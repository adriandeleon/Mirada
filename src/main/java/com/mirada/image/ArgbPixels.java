package com.mirada.image;

import java.nio.IntBuffer;

import javafx.scene.image.PixelBuffer;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;

/**
 * A decoded image as premultiplied ARGB ints, one per pixel, row after row: what every decoder but
 * JavaFX's own produces.
 *
 * <p>It needs no JavaFX toolkit, which is the point: {@link HeadStart} decodes the first image into
 * one while the toolkit is still starting. {@link #toImage} then wraps the same array as a JavaFX
 * image without copying it.
 */
record ArgbPixels(int width, int height, int[] pixels) {

    ArgbPixels {
        if (width <= 0 || height <= 0 || pixels.length < width * height) {
            throw new IllegalArgumentException(width + " × " + height + " in " + pixels.length + " pixels");
        }
    }

    /** The pixels as a JavaFX image, sharing this array. Needs the toolkit; any thread. */
    WritableImage toImage() {
        PixelBuffer<IntBuffer> buffer =
                new PixelBuffer<>(width, height, IntBuffer.wrap(pixels), PixelFormat.getIntArgbPreInstance());
        return new WritableImage(buffer);
    }

    /** Opaque red, green and blue. */
    static int opaque(int red, int green, int blue) {
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    /** Straight ARGB to premultiplied, rounding as JavaFX's own conversion does. */
    static int premultiply(int argb) {
        int alpha = argb >>> 24;
        if (alpha == 0xFF) return argb;
        if (alpha == 0) return 0;
        int red = ((argb >> 16 & 0xFF) * alpha + 0x7F) / 0xFF;
        int green = ((argb >> 8 & 0xFF) * alpha + 0x7F) / 0xFF;
        int blue = ((argb & 0xFF) * alpha + 0x7F) / 0xFF;
        return alpha << 24 | red << 16 | green << 8 | blue;
    }
}
