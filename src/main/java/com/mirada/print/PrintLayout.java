package com.mirada.print;

/**
 * Where the image goes on the page: as large as the printable area allows, its proportions kept,
 * centred; and how many pixels are worth sending to the printer at all.
 */
public final class PrintLayout {

    private PrintLayout() {}

    /**
     * The longest side, in pixels, an image is printed at: 400 dots per inch down the long side of
     * A4 or Letter paper. A 50-megapixel photo is shrunk to this first, so the job the printer is
     * sent is tens of megabytes, not hundreds, and looks the same on paper.
     */
    public static final int LONGEST_SIDE = 4800;

    /** A rectangle on the page, in the printable area's own units, from its top-left corner. */
    public record Placement(double x, double y, double width, double height) {}

    /**
     * The image, {@code imageWidth} × {@code imageHeight}, fitted to an area {@code areaWidth} ×
     * {@code areaHeight}: scaled up or down until it meets two opposite edges, and centred.
     */
    public static Placement fit(double imageWidth, double imageHeight, double areaWidth, double areaHeight) {
        if (imageWidth <= 0 || imageHeight <= 0 || areaWidth <= 0 || areaHeight <= 0) {
            return new Placement(0, 0, 0, 0);
        }
        double scale = Math.min(areaWidth / imageWidth, areaHeight / imageHeight);
        double width = imageWidth * scale;
        double height = imageHeight * scale;
        return new Placement((areaWidth - width) / 2, (areaHeight - height) / 2, width, height);
    }

    /** Whether the page should start out landscape: the image is wider than it is tall. */
    public static boolean landscape(int imageWidth, int imageHeight) {
        return imageWidth > imageHeight;
    }
}
