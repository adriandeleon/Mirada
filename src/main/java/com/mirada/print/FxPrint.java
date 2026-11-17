package com.mirada.print;

import java.io.IOException;
import java.nio.IntBuffer;

import javafx.print.PageLayout;
import javafx.print.PageOrientation;
import javafx.print.Printer;
import javafx.print.PrinterJob;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelBuffer;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Pane;
import javafx.stage.Window;

import com.mirada.edit.Raster;

/**
 * The Mac's and Windows' print dialog, through {@code javafx.print}: JavaFX asks AWT's printer job,
 * which shows the system's own panel there (NSPrintPanel, the Win32 print dialog). AWT must be
 * headful for that; under a headless AWT the dialog is skipped and the page goes straight to the
 * default printer, which is why Linux, headless, prints through {@link GtkPrint} instead.
 */
final class FxPrint {

    private FxPrint() {}

    static Printing.Outcome print(Raster raster, String jobName, Window owner) throws IOException {
        PrinterJob job;
        try {
            job = PrinterJob.createPrinterJob();
        } catch (RuntimeException e) {
            throw new IOException(e.getMessage(), e);
        }
        if (job == null) return Printing.Outcome.NO_PRINTER;
        try {
            job.getJobSettings().setJobName(jobName);
            Printer printer = job.getPrinter();
            if (printer != null && PrintLayout.landscape(raster.width(), raster.height())) {
                PageLayout portrait = job.getJobSettings().getPageLayout();
                job.getJobSettings()
                        .setPageLayout(printer.createPageLayout(
                                portrait.getPaper(), PageOrientation.LANDSCAPE, Printer.MarginType.DEFAULT));
            }
            if (!job.showPrintDialog(owner)) {
                job.cancelJob();
                return Printing.Outcome.CANCELLED;
            }
            PageLayout layout = job.getJobSettings().getPageLayout();
            if (!job.printPage(layout, page(raster, layout)) || !job.endJob()) {
                throw new IOException(String.valueOf(job.getJobStatus()));
            }
            return Printing.Outcome.SENT;
        } catch (RuntimeException e) {
            job.cancelJob();
            throw new IOException(e.getMessage(), e);
        }
    }

    /** The page: the image fitted to the printable area and centred in it ({@link PrintLayout#fit}). */
    static Pane page(Raster raster, PageLayout layout) {
        WritableImage image = new WritableImage(new PixelBuffer<>(
                raster.width(), raster.height(), IntBuffer.wrap(raster.pixels()), PixelFormat.getIntArgbPreInstance()));
        PrintLayout.Placement placed = PrintLayout.fit(
                raster.width(), raster.height(), layout.getPrintableWidth(), layout.getPrintableHeight());
        ImageView view = new ImageView(image);
        view.setSmooth(true);
        view.setFitWidth(placed.width());
        view.setFitHeight(placed.height());
        view.relocate(placed.x(), placed.y());
        Pane page = new Pane(view);
        page.setPrefSize(layout.getPrintableWidth(), layout.getPrintableHeight());
        return page;
    }
}
