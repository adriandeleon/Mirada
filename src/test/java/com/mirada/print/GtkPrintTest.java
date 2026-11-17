package com.mirada.print;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.mirada.edit.Raster;
import com.mirada.os.Os;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_DOUBLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * GTK's printed page, drawn as {@code draw-page} draws it but into a cairo PDF: the pixels reach
 * cairo and the page is written, with no dialog and no printer. Linux only, where GTK prints.
 */
class GtkPrintTest {

    @TempDir
    Path dir;

    @Test
    void theOperationDrawsTheImageIntoThePage() throws Exception {
        assumeTrue(Os.host() == Os.Kind.LINUX, "GTK prints on Linux only");
        // Wide, so the page is set landscape too; exported, so no dialog and no printer.
        Path file = dir.resolve("export.pdf");
        Printing.Outcome outcome = GtkPrint.run(new Raster(30, 20, new int[30 * 20]), "test", file.toString());
        assertEquals(Printing.Outcome.SENT, outcome);
        String text = new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
        assertTrue(text.startsWith("%PDF"), "a PDF");
        assertTrue(text.contains("/Subtype /Image"), "with the image in it");
    }

    @Test
    void thePageIsDrawnWithCairo() throws Throwable {
        assumeTrue(Os.host() == Os.Kind.LINUX, "GTK prints on Linux only");
        GtkPrint.Cairo cairo;
        try {
            cairo = GtkPrint.Cairo.load();
        } catch (IllegalArgumentException noCairo) {
            assumeTrue(false, "no libcairo here");
            return;
        }
        Linker linker = Linker.nativeLinker();
        MethodHandle pdf = linker.downcallHandle(
                cairo.library.find("cairo_pdf_surface_create").orElseThrow(),
                FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_DOUBLE, JAVA_DOUBLE));
        MethodHandle create = linker.downcallHandle(
                cairo.library.find("cairo_create").orElseThrow(), FunctionDescriptor.of(ADDRESS, ADDRESS));
        MethodHandle destroy = linker.downcallHandle(
                cairo.library.find("cairo_destroy").orElseThrow(), FunctionDescriptor.ofVoid(ADDRESS));

        // A red half and a transparent half, premultiplied ARGB as the viewer holds them.
        int[] pixels = new int[40 * 20];
        for (int i = 0; i < pixels.length; i++) pixels[i] = (i % 40) < 20 ? 0xFFFF0000 : 0;
        Path file = dir.resolve("page.pdf");
        try (Arena arena = Arena.ofConfined()) {
            GtkPrint.Job job = GtkPrint.Job.of(new Raster(40, 20, pixels), arena);
            MemorySegment surface = (MemorySegment) pdf.invokeExact(arena.allocateFrom(file.toString()), 595.0, 842.0);
            MemorySegment cr = (MemorySegment) create.invokeExact(surface);
            GtkPrint.draw(cairo, cr, job, 595.0, 842.0);
            destroy.invokeExact(cr);
            cairo.surfaceDestroy.invokeExact(surface);
        }
        byte[] written = Files.readAllBytes(file);
        String text = new String(written, StandardCharsets.ISO_8859_1);
        assertTrue(text.startsWith("%PDF"), "a PDF");
        assertTrue(text.contains("/Subtype /Image"), "with the image in it");
    }
}
