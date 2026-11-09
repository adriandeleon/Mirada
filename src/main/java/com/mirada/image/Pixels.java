package com.mirada.image;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/** Converts an interleaved 8-bit RGB or RGBA buffer, as native decoders hand it out, to {@link ArgbPixels}. */
final class Pixels {

    private Pixels() {}

    /**
     * @param channels 3 for RGB, 4 for RGBA (not premultiplied)
     * @param stride bytes from the start of one row to the next, at least {@code width * channels}
     */
    static ArgbPixels fromInterleaved(MemorySegment pixels, int width, int height, int stride, int channels)
            throws java.io.IOException {
        int[] out = ArgbPixels.allocate(width, height);
        // A row at a time into a Java array: reading the segment byte by byte is several times slower.
        byte[] row = new byte[width * channels];
        int at = 0;
        for (int y = 0; y < height; y++) {
            MemorySegment.copy(pixels, ValueLayout.JAVA_BYTE, (long) y * stride, row, 0, row.length);
            if (channels == 3) {
                for (int i = 0; i < row.length; i += 3) {
                    out[at++] = ArgbPixels.opaque(row[i] & 0xFF, row[i + 1] & 0xFF, row[i + 2] & 0xFF);
                }
            } else {
                for (int i = 0; i < row.length; i += 4) {
                    int argb = (row[i + 3] & 0xFF) << 24
                            | (row[i] & 0xFF) << 16
                            | (row[i + 1] & 0xFF) << 8
                            | row[i + 2] & 0xFF;
                    out[at++] = ArgbPixels.premultiply(argb);
                }
            }
        }
        return new ArgbPixels(width, height, out);
    }
}
