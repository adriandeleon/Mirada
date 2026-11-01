package com.mirada.edit;

/**
 * Pixels as premultiplied ARGB ints, row after row, and the turns, mirrors and crops the editor
 * applies to them. Each operation returns a new raster; none needs the JavaFX toolkit.
 */
public record Raster(int width, int height, int[] pixels) {

    public Raster {
        if (width <= 0 || height <= 0 || pixels.length != width * height) {
            throw new IllegalArgumentException(width + " × " + height + " in " + pixels.length + " pixels");
        }
    }

    /** Left to right. */
    public Raster mirrored() {
        int[] out = new int[pixels.length];
        for (int y = 0; y < height; y++) {
            int row = y * width;
            for (int x = 0; x < width; x++) out[row + x] = pixels[row + width - 1 - x];
        }
        return new Raster(width, height, out);
    }

    /** Top to bottom. */
    public Raster flippedVertically() {
        int[] out = new int[pixels.length];
        for (int y = 0; y < height; y++) System.arraycopy(pixels, (height - 1 - y) * width, out, y * width, width);
        return new Raster(width, height, out);
    }

    /** Turned clockwise by {@code quarterTurns} quarters (any integer; taken modulo 4). */
    public Raster turned(int quarterTurns) {
        int turns = Math.floorMod(quarterTurns, 4);
        if (turns == 0) return this;
        if (turns == 2) {
            int[] out = new int[pixels.length];
            for (int i = 0; i < pixels.length; i++) out[i] = pixels[pixels.length - 1 - i];
            return new Raster(width, height, out);
        }
        int[] out = new int[pixels.length];
        int newWidth = height;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                // Clockwise: (x, y) goes to (height - 1 - y, x); counterclockwise to (y, width - 1 - x).
                int nx = turns == 1 ? height - 1 - y : y;
                int ny = turns == 1 ? x : width - 1 - x;
                out[ny * newWidth + nx] = pixels[y * width + x];
            }
        }
        return new Raster(height, width, out);
    }

    /** The rectangle at ({@code x}, {@code y}), {@code w} × {@code h}, which must lie inside. */
    public Raster cropped(int x, int y, int w, int h) {
        if (x < 0 || y < 0 || w <= 0 || h <= 0 || x + w > width || y + h > height) {
            throw new IllegalArgumentException(
                    "crop " + x + "," + y + " " + w + "×" + h + " outside " + width + "×" + height);
        }
        if (x == 0 && y == 0 && w == width && h == height) return this;
        int[] out = new int[w * h];
        for (int row = 0; row < h; row++) System.arraycopy(pixels, (y + row) * width + x, out, row * w, w);
        return new Raster(w, h, out);
    }

    /** Whether every pixel is fully opaque: what decides whether a JPEG can carry it as it is. */
    public boolean opaque() {
        for (int argb : pixels) {
            if (argb >>> 24 != 0xFF) return false;
        }
        return true;
    }
}
