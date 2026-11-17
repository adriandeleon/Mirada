package com.mirada.os;

/**
 * The one rule for {@code java.awt.headless}, set in {@code App.main} before any AWT class loads.
 * ImageIO and {@code BufferedImage} are {@code java.desktop} and need no display, so Linux runs AWT
 * headless; the Mac and Windows run it headful, for what only AWT reaches there: printing through
 * the system's dialog ({@code javafx.print}, {@code com.mirada.print.FxPrint}), and on the Mac Move
 * to Trash ({@link Trash}). Headful, AWT's toolkit still loads only when one of those is first used.
 */
public final class Awt {

    private Awt() {}

    /** Whether AWT must be headless on {@code os}: only on Linux, which prints through GTK. */
    public static boolean headless(Os.Kind os) {
        return os == Os.Kind.LINUX;
    }
}
