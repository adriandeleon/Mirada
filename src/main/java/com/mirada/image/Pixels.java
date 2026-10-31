package com.mirada.image;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;

import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;

/** Copies an interleaved 8-bit RGB or RGBA buffer, as native decoders hand it out, into a JavaFX image. */
final class Pixels {

    private Pixels() {}

    /**
     * @param channels 3 for RGB, 4 for RGBA (not premultiplied)
     * @param stride bytes from the start of one row to the next, at least {@code width * channels}
     */
    static WritableImage fromInterleaved(MemorySegment pixels, int width, int height, int stride, int channels) {
        WritableImage out = new WritableImage(width, height);
        if (channels == 3) {
            // JavaFX takes RGB bytes as they are: one call, no copy on our side.
            out.getPixelWriter()
                    .setPixels(0, 0, width, height, PixelFormat.getByteRgbInstance(), pixels.asByteBuffer(), stride);
            return out;
        }
        // RGBA has no JavaFX format; BGRA does. Swap red and blue a row at a time.
        byte[] row = new byte[width * 4];
        ByteBuffer rowBuffer = ByteBuffer.wrap(row);
        for (int y = 0; y < height; y++) {
            MemorySegment.copy(pixels, ValueLayout.JAVA_BYTE, (long) y * stride, row, 0, row.length);
            for (int i = 0; i < row.length; i += 4) {
                byte red = row[i];
                row[i] = row[i + 2];
                row[i + 2] = red;
            }
            out.getPixelWriter().setPixels(0, y, width, 1, PixelFormat.getByteBgraInstance(), rowBuffer, row.length);
        }
        return out;
    }
}
