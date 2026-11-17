package com.mirada.print;

import java.io.IOException;

import javafx.stage.Window;

import com.mirada.edit.Raster;
import com.mirada.os.Os;

/**
 * Prints an image through the desktop's own print dialog (Print, in the File menu): the user picks
 * the printer, the paper and the copies there, and the image fills the printable area, centred,
 * the page landscape when the image is wide.
 *
 * <ul>
 *   <li><b>Linux</b>: GTK's print dialog ({@link GtkPrint}), the one every GTK app shows, with
 *       Print to File and the page setup tab. JavaFX's own would be AWT's, which Mirada keeps
 *       headless there.
 *   <li><b>macOS and Windows</b>: {@code javafx.print} ({@link FxPrint}), whose dialog is the
 *       system's, AWT being headful on both for it ({@code com.mirada.os.Awt}).
 * </ul>
 */
public final class Printing {

    private Printing() {}

    /** How a print ended: sent to the printer, called off in the dialog, or no printer to ask. */
    public enum Outcome {
        SENT,
        CANCELLED,
        NO_PRINTER
    }

    /**
     * Shows the print dialog over {@code owner} and prints {@code image}, upright and premultiplied,
     * as the job {@code jobName}; returns once the dialog is closed and the page handed on. FX thread:
     * the dialog runs a nested event loop, so the window keeps painting under it.
     *
     * @throws IOException when the desktop could not show the dialog or print, with its reason
     */
    public static Outcome print(Raster image, String jobName, Window owner) throws IOException {
        return Os.host() == Os.Kind.LINUX ? GtkPrint.print(image, jobName) : FxPrint.print(image, jobName, owner);
    }
}
