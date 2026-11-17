package com.mirada.print;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.mirada.edit.Raster;
import org.jspecify.annotations.Nullable;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_DOUBLE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

/**
 * GTK's print dialog on Linux, through FFM: a {@code GtkPrintOperation} of one page, run with its
 * dialog, whose {@code draw-page} signal comes back here ({@link #drawPage}) to paint the image with
 * cairo into whatever the user chose: a printer, Print to File, the preview.
 *
 * <p>JavaFX's glass already runs GTK 3 on the FX thread, so the operation runs there too, and
 * {@code gtk_print_operation_run} spins GTK's main loop until the dialog closes and the page is
 * drawn: JavaFX's windows keep painting meanwhile, as under any modal dialog. The job is never
 * asynchronous ({@code allow-async} stays off), so the pixels, in this call's arena, outlive every
 * draw.
 *
 * <p>The native image lists this upcall and these downcalls in its metadata by hand: the tracing
 * agent never sees a dialog nobody answers.
 */
final class GtkPrint {

    private static Logger log() {
        return Logger.getLogger(GtkPrint.class.getName());
    }

    private GtkPrint() {}

    private static final int ACTION_PRINT_DIALOG = 0;
    /** {@code GTK_PRINT_OPERATION_ACTION_EXPORT}: the page into a PDF, no dialog; a test's. */
    private static final int ACTION_EXPORT = 3;

    private static final int RESULT_ERROR = 0;
    private static final int RESULT_APPLY = 1;
    private static final int UNIT_POINTS = 1;
    private static final int ORIENTATION_LANDSCAPE = 1;
    /** {@code CAIRO_FORMAT_ARGB32}: premultiplied, alpha in the high byte of a native-order int. */
    static final int FORMAT_ARGB32 = 0;

    /** The image being printed, in native memory as cairo reads it. */
    record Job(MemorySegment pixels, int width, int height) {

        /** {@code raster}'s pixels copied into {@code arena}: Java's ARGB ints are cairo's ARGB32. */
        static Job of(Raster raster, Arena arena) {
            int[] source = raster.pixels();
            MemorySegment pixels = arena.allocate(JAVA_INT, source.length);
            MemorySegment.copy(source, 0, pixels, JAVA_INT, 0, source.length);
            return new Job(pixels, raster.width(), raster.height());
        }
    }

    /** The job {@code draw-page} paints, while {@link #print} runs; FX thread only. */
    private static @Nullable Job current;
    /** What went wrong inside {@link #drawPage}, which must not throw into GTK. */
    private static @Nullable Throwable drawFailure;

    static Printing.Outcome print(Raster raster, String jobName) throws IOException {
        return run(raster, jobName, null);
    }

    /**
     * The operation: with {@code exportTo} null, through the print dialog; otherwise the same page
     * written to that PDF with no dialog, the whole path but the user's choices, for a test.
     */
    static Printing.Outcome run(Raster raster, String jobName, @Nullable String exportTo) throws IOException {
        Gtk gtk = Gtk.load();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment operation = (MemorySegment) gtk.operationNew.invokeExact();
            try {
                gtk.setNPages.invokeExact(operation, 1);
                gtk.setUnit.invokeExact(operation, UNIT_POINTS);
                gtk.setEmbedPageSetup.invokeExact(operation, 1);
                gtk.setJobName.invokeExact(operation, arena.allocateFrom(jobName));
                if (exportTo != null) gtk.setExportFilename.invokeExact(operation, arena.allocateFrom(exportTo));
                if (PrintLayout.landscape(raster.width(), raster.height())) {
                    MemorySegment setup = (MemorySegment) gtk.pageSetupNew.invokeExact();
                    gtk.setOrientation.invokeExact(setup, ORIENTATION_LANDSCAPE);
                    gtk.setDefaultPageSetup.invokeExact(operation, setup);
                    gtk.unref.invokeExact(setup);
                }
                MemorySegment drawPage = Linker.nativeLinker()
                        .upcallStub(
                                MethodHandles.lookup()
                                        .findStatic(
                                                GtkPrint.class,
                                                "drawPage",
                                                MethodType.methodType(
                                                        void.class,
                                                        MemorySegment.class,
                                                        MemorySegment.class,
                                                        int.class,
                                                        MemorySegment.class)),
                                FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, JAVA_INT, ADDRESS),
                                arena);
                long connected = (long) gtk.signalConnect.invokeExact(
                        operation,
                        arena.allocateFrom("draw-page"),
                        drawPage,
                        MemorySegment.NULL,
                        MemorySegment.NULL,
                        0);
                if (connected == 0) throw new IOException("draw-page");
                MemorySegment error = arena.allocate(ADDRESS);
                current = Job.of(raster, arena);
                drawFailure = null;
                int result;
                try {
                    result = (int) gtk.run.invokeExact(
                            operation,
                            exportTo == null ? ACTION_PRINT_DIALOG : ACTION_EXPORT,
                            MemorySegment.NULL,
                            error);
                } finally {
                    current = null;
                }
                Throwable failed = drawFailure;
                drawFailure = null;
                if (failed != null) throw new IOException(failed.getMessage(), failed);
                if (result == RESULT_ERROR) throw new IOException(message(gtk, error.get(ADDRESS, 0)));
                return result == RESULT_APPLY ? Printing.Outcome.SENT : Printing.Outcome.CANCELLED;
            } finally {
                gtk.unref.invokeExact(operation);
            }
        } catch (IOException e) {
            throw e;
        } catch (Throwable e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    /** A {@code GError}'s message, and the error freed: {@code {GQuark domain; gint code; gchar *message}}. */
    private static String message(Gtk gtk, MemorySegment error) throws Throwable {
        if (error.equals(MemorySegment.NULL)) return "GTK";
        MemorySegment fields = error.reinterpret(16);
        String message = fields.get(ADDRESS, 8).reinterpret(Long.MAX_VALUE).getString(0);
        gtk.errorFree.invokeExact(error);
        return message;
    }

    /**
     * GTK's {@code draw-page}: paints the job into the page's cairo context, fitted and centred in
     * its printable area. An upcall must not throw, so a failure is kept for {@link #print} to report.
     */
    static void drawPage(MemorySegment operation, MemorySegment context, int page, MemorySegment data) {
        Job job = current;
        if (job == null) return;
        try {
            Gtk gtk = Gtk.load();
            MemorySegment cairo = (MemorySegment) gtk.contextCairo.invokeExact(context);
            double width = (double) gtk.contextWidth.invokeExact(context);
            double height = (double) gtk.contextHeight.invokeExact(context);
            draw(gtk.cairo, cairo, job, width, height);
        } catch (Throwable e) {
            log().log(Level.WARNING, "Could not draw the printed page", e);
            drawFailure = e;
        }
    }

    /** Paints {@code job} into the cairo context {@code cr}, fitted to {@code width} × {@code height}. */
    static void draw(Cairo cairo, MemorySegment cr, Job job, double width, double height) throws Throwable {
        PrintLayout.Placement placed = PrintLayout.fit(job.width(), job.height(), width, height);
        MemorySegment surface = (MemorySegment) cairo.surfaceForData.invokeExact(
                job.pixels(), FORMAT_ARGB32, job.width(), job.height(), job.width() * 4);
        try {
            cairo.save.invokeExact(cr);
            cairo.translate.invokeExact(cr, placed.x(), placed.y());
            cairo.scale.invokeExact(cr, placed.width() / job.width(), placed.height() / job.height());
            cairo.setSourceSurface.invokeExact(cr, surface, 0.0, 0.0);
            cairo.paint.invokeExact(cr);
            cairo.restore.invokeExact(cr);
        } finally {
            cairo.surfaceDestroy.invokeExact(surface);
        }
    }

    private static MethodHandle handle(SymbolLookup library, String name, FunctionDescriptor descriptor) {
        MemorySegment symbol =
                library.find(name).orElseThrow(() -> new IllegalStateException(name + " is not in the library"));
        return Linker.nativeLinker().downcallHandle(symbol, descriptor);
    }

    /** The cairo calls that paint the page; a test draws through them into a PDF. */
    static final class Cairo {
        final SymbolLookup library;
        final MethodHandle surfaceForData;
        final MethodHandle surfaceDestroy;
        final MethodHandle save;
        final MethodHandle restore;
        final MethodHandle translate;
        final MethodHandle scale;
        final MethodHandle setSourceSurface;
        final MethodHandle paint;

        private Cairo(SymbolLookup cairo) {
            library = cairo;
            surfaceForData = handle(
                    cairo,
                    "cairo_image_surface_create_for_data",
                    FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT));
            surfaceDestroy = handle(cairo, "cairo_surface_destroy", FunctionDescriptor.ofVoid(ADDRESS));
            save = handle(cairo, "cairo_save", FunctionDescriptor.ofVoid(ADDRESS));
            restore = handle(cairo, "cairo_restore", FunctionDescriptor.ofVoid(ADDRESS));
            translate = handle(cairo, "cairo_translate", FunctionDescriptor.ofVoid(ADDRESS, JAVA_DOUBLE, JAVA_DOUBLE));
            scale = handle(cairo, "cairo_scale", FunctionDescriptor.ofVoid(ADDRESS, JAVA_DOUBLE, JAVA_DOUBLE));
            setSourceSurface = handle(
                    cairo,
                    "cairo_set_source_surface",
                    FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, JAVA_DOUBLE, JAVA_DOUBLE));
            paint = handle(cairo, "cairo_paint", FunctionDescriptor.ofVoid(ADDRESS));
        }

        /** libcairo, as GTK itself loaded it. */
        static Cairo load() {
            return new Cairo(SymbolLookup.libraryLookup("libcairo.so.2", Arena.global()));
        }
    }

    /** GTK's print calls, looked up once, on the first print. */
    private static final class Gtk {
        private static @Nullable Gtk loaded;

        final Cairo cairo;
        final MethodHandle operationNew;
        final MethodHandle setNPages;
        final MethodHandle setUnit;
        final MethodHandle setEmbedPageSetup;
        final MethodHandle setJobName;
        final MethodHandle setExportFilename;
        final MethodHandle pageSetupNew;
        final MethodHandle setOrientation;
        final MethodHandle setDefaultPageSetup;
        final MethodHandle signalConnect;
        final MethodHandle run;
        final MethodHandle contextCairo;
        final MethodHandle contextWidth;
        final MethodHandle contextHeight;
        final MethodHandle unref;
        final MethodHandle errorFree;

        private Gtk(SymbolLookup gtk, SymbolLookup gobject, SymbolLookup glib, Cairo cairo) {
            this.cairo = cairo;
            operationNew = handle(gtk, "gtk_print_operation_new", FunctionDescriptor.of(ADDRESS));
            setNPages = handle(gtk, "gtk_print_operation_set_n_pages", FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT));
            setUnit = handle(gtk, "gtk_print_operation_set_unit", FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT));
            setEmbedPageSetup = handle(
                    gtk, "gtk_print_operation_set_embed_page_setup", FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT));
            setJobName = handle(gtk, "gtk_print_operation_set_job_name", FunctionDescriptor.ofVoid(ADDRESS, ADDRESS));
            setExportFilename =
                    handle(gtk, "gtk_print_operation_set_export_filename", FunctionDescriptor.ofVoid(ADDRESS, ADDRESS));
            pageSetupNew = handle(gtk, "gtk_page_setup_new", FunctionDescriptor.of(ADDRESS));
            setOrientation =
                    handle(gtk, "gtk_page_setup_set_orientation", FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT));
            setDefaultPageSetup = handle(
                    gtk, "gtk_print_operation_set_default_page_setup", FunctionDescriptor.ofVoid(ADDRESS, ADDRESS));
            signalConnect = handle(
                    gobject,
                    "g_signal_connect_data",
                    FunctionDescriptor.of(JAVA_LONG, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS, JAVA_INT));
            run = handle(
                    gtk,
                    "gtk_print_operation_run",
                    FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, ADDRESS));
            contextCairo = handle(gtk, "gtk_print_context_get_cairo_context", FunctionDescriptor.of(ADDRESS, ADDRESS));
            contextWidth = handle(gtk, "gtk_print_context_get_width", FunctionDescriptor.of(JAVA_DOUBLE, ADDRESS));
            contextHeight = handle(gtk, "gtk_print_context_get_height", FunctionDescriptor.of(JAVA_DOUBLE, ADDRESS));
            unref = handle(gobject, "g_object_unref", FunctionDescriptor.ofVoid(ADDRESS));
            errorFree = handle(glib, "g_error_free", FunctionDescriptor.ofVoid(ADDRESS));
        }

        /** GTK 3, already loaded by JavaFX's glass; FX thread. */
        static Gtk load() throws IOException {
            Gtk gtk = loaded;
            if (gtk != null) return gtk;
            try {
                gtk = new Gtk(
                        SymbolLookup.libraryLookup("libgtk-3.so.0", Arena.global()),
                        SymbolLookup.libraryLookup("libgobject-2.0.so.0", Arena.global()),
                        SymbolLookup.libraryLookup("libglib-2.0.so.0", Arena.global()),
                        Cairo.load());
            } catch (IllegalArgumentException | IllegalStateException e) {
                throw new IOException(e.getMessage(), e);
            }
            loaded = gtk;
            return gtk;
        }
    }
}
