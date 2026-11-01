package com.mirada.edit;

/**
 * A crop rectangle as fractions of the image it is drawn on (0 to 1 each way), so it survives the
 * difference between the preview the user drags it on and the full image it is applied to.
 */
public record Crop(double x, double y, double width, double height) {

    public static final Crop ALL = new Crop(0, 0, 1, 1);

    public Crop {
        x = clamp01(x);
        y = clamp01(y);
        width = Math.clamp(width, 0, 1 - x);
        height = Math.clamp(height, 0, 1 - y);
    }

    public boolean isAll() {
        return x <= 0 && y <= 0 && width >= 1 && height >= 1;
    }

    /** Mirrored left to right, as the image under it is. */
    public Crop mirrored() {
        return new Crop(1 - x - width, y, width, height);
    }

    public Crop flippedVertically() {
        return new Crop(x, 1 - y - height, width, height);
    }

    /**
     * In whole pixels of a {@code imageWidth} × {@code imageHeight} image: {x, y, width, height},
     * rounded, at least one pixel, and inside the image.
     */
    public int[] pixels(int imageWidth, int imageHeight) {
        int left = (int) Math.round(x * imageWidth);
        int top = (int) Math.round(y * imageHeight);
        int right = (int) Math.round((x + width) * imageWidth);
        int bottom = (int) Math.round((y + height) * imageHeight);
        left = Math.clamp(left, 0, imageWidth - 1);
        top = Math.clamp(top, 0, imageHeight - 1);
        right = Math.clamp(right, left + 1, imageWidth);
        bottom = Math.clamp(bottom, top + 1, imageHeight);
        return new int[] {left, top, right - left, bottom - top};
    }

    private static double clamp01(double value) {
        return Math.clamp(value, 0, 1);
    }
}
