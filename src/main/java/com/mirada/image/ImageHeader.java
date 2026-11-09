package com.mirada.image;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.jspecify.annotations.Nullable;

/**
 * The size a PNG, GIF or BMP says it is, from its first bytes: what {@link DecodeGuard} needs before
 * JavaFX's own decoder, which allocates the whole picture first, is let at the file (PERF-12). The other
 * formats' decoders read their headers themselves.
 */
final class ImageHeader {

    /** As many bytes as the three formats need to give their size. */
    static final int LENGTH = 26;

    record Size(long width, long height) {}

    private ImageHeader() {}

    /** The size {@code file}'s header gives, or null when it is none of the three or is cut short. */
    static @Nullable Size read(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            return of(in.readNBytes(LENGTH));
        }
    }

    /** The size in a file's first {@link #LENGTH} bytes, or null when they are none of PNG, GIF or BMP. Pure. */
    static @Nullable Size of(byte[] head) {
        if (head.length >= 24 && startsWith(head, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return new Size(bigEndian(head, 16), bigEndian(head, 20));
        }
        if (head.length >= 10 && startsWith(head, 'G', 'I', 'F', '8')) {
            return new Size(littleEndian(head, 6, 2), littleEndian(head, 8, 2));
        }
        if (head.length >= 26 && startsWith(head, 'B', 'M')) {
            // The 12-byte header of OS/2's bitmaps has 16-bit sizes; every later one 32-bit, the
            // height negative for rows stored top first.
            if (littleEndian(head, 14, 4) == 12) return new Size(littleEndian(head, 18, 2), littleEndian(head, 20, 2));
            return new Size(
                    Math.abs((long) (int) littleEndian(head, 18, 4)), Math.abs((long) (int) littleEndian(head, 22, 4)));
        }
        return null;
    }

    private static boolean startsWith(byte[] head, int... expected) {
        for (int i = 0; i < expected.length; i++) {
            if ((head[i] & 0xFF) != expected[i]) return false;
        }
        return true;
    }

    private static long bigEndian(byte[] bytes, int at) {
        long value = 0;
        for (int i = 0; i < 4; i++) value = value << 8 | (bytes[at + i] & 0xFF);
        return value;
    }

    private static long littleEndian(byte[] bytes, int at, int length) {
        long value = 0;
        for (int i = length - 1; i >= 0; i--) value = value << 8 | (bytes[at + i] & 0xFF);
        return value;
    }
}
