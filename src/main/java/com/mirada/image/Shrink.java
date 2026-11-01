package com.mirada.image;

import java.nio.IntBuffer;

import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritablePixelFormat;

/**
 * Scales an image down so its longer side fits, each output pixel the average of the source pixels
 * it covers: a box filter, with no aliasing, in one pass over the source a row at a time.
 *
 * <p>Premultiplied pixels are averaged as they are, which is what keeps a transparent edge from
 * turning dark. Any thread, for an image that has finished loading.
 */
final class Shrink {

    private Shrink() {}

    /** {@code source} with its longer side at most {@code longSide}; {@code source} itself if it already is. */
    static Image toFit(Image source, int longSide) {
        int width = (int) source.getWidth();
        int height = (int) source.getHeight();
        int[] size = fittedSize(width, height, longSide);
        if (size[0] == width && size[1] == height) return source;
        return box(source.getPixelReader(), width, height, size[0], size[1]).toImage();
    }

    /** The size that fits {@code longSide}, never larger than the source and never below 1 × 1. */
    static int[] fittedSize(int width, int height, int longSide) {
        double scale = Math.min(1, (double) longSide / Math.max(width, height));
        if (scale >= 1) return new int[] {width, height};
        return new int[] {Math.max(1, (int) Math.round(width * scale)), Math.max(1, (int) Math.round(height * scale))};
    }

    static ArgbPixels box(PixelReader reader, int width, int height, int outWidth, int outHeight) {
        WritablePixelFormat<IntBuffer> format = PixelFormat.getIntArgbPreInstance();
        int[] row = new int[width];
        // Sums for the output row being built, one per output column and channel.
        long[] alpha = new long[outWidth];
        long[] red = new long[outWidth];
        long[] green = new long[outWidth];
        long[] blue = new long[outWidth];
        int[] firstColumn = new int[outWidth + 1];
        for (int x = 0; x <= outWidth; x++) firstColumn[x] = (int) ((long) x * width / outWidth);
        int[] out = new int[outWidth * outHeight];
        int sourceY = 0;
        for (int y = 0; y < outHeight; y++) {
            int lastY = Math.max(sourceY + 1, (int) ((long) (y + 1) * height / outHeight));
            for (; sourceY < lastY; sourceY++) {
                reader.getPixels(0, sourceY, width, 1, format, row, 0, width);
                for (int x = 0; x < outWidth; x++) {
                    int to = Math.max(firstColumn[x] + 1, firstColumn[x + 1]);
                    for (int sx = firstColumn[x]; sx < to; sx++) {
                        int argb = row[sx];
                        alpha[x] += argb >>> 24;
                        red[x] += argb >> 16 & 0xFF;
                        green[x] += argb >> 8 & 0xFF;
                        blue[x] += argb & 0xFF;
                    }
                }
            }
            int rows = lastY - (int) ((long) y * height / outHeight);
            for (int x = 0; x < outWidth; x++) {
                long n = (long) rows * Math.max(1, firstColumn[x + 1] - firstColumn[x]);
                out[y * outWidth + x] = (int) (alpha[x] / n) << 24
                        | (int) (red[x] / n) << 16
                        | (int) (green[x] / n) << 8
                        | (int) (blue[x] / n);
                alpha[x] = red[x] = green[x] = blue[x] = 0;
            }
        }
        return new ArgbPixels(outWidth, outHeight, out);
    }
}
