package com.mirada.image;

import java.io.IOException;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

/**
 * Whether an image of a given size may be decoded into the heap that is left, asked from its header
 * before its pixels are (PERF-12; Editora's {@code LoadHeapGuard} moved to pictures). A decoded image is
 * four bytes a pixel and is briefly held twice while it is decoded, so a 300-megapixel scan needs
 * 2.4 GB: past what is free, it is refused with a message naming both, instead of an {@code
 * OutOfMemoryError} on a decoder thread taking the cache and the window with it.
 *
 * <p>The thresholds, smallest first: an animation's frames stop at {@link Animation#MAX_PIXELS} (it shows
 * as a still); an image needing more than the heap leaves (less {@link #MARGIN_BYTES}) is refused; and no
 * image passes {@link #MAX_PIXELS}, the most a Java array holds, whatever the heap.
 */
final class DecodeGuard {

    /** Live heap a pixel costs while it is decoded: the ARGB int, and the decoder's buffer beside it. */
    static final long BYTES_PER_PIXEL = 8;

    /** Heap left for the rest of Mirada after the decode is counted: the scene, the previews, the strip. */
    static final long MARGIN_BYTES = 64L << 20;

    /** The most pixels one image may have: an {@code int[]}'s limit, whatever the heap. */
    static final long MAX_PIXELS = Integer.MAX_VALUE - 8;

    private DecodeGuard() {}

    /** An image refused for its size: a file that is fine, only too large for the memory left. */
    static final class TooLarge extends IOException {
        TooLarge(String reason) {
            super(reason);
        }
    }

    /** Whether {@code pixels} decode, with margin, in what {@code usedHeap} leaves of {@code maxHeap}. Pure. */
    static boolean fits(long pixels, long maxHeap, long usedHeap) {
        if (pixels > MAX_PIXELS) return false;
        return pixels * BYTES_PER_PIXEL <= Math.max(0, maxHeap - usedHeap - MARGIN_BYTES);
    }

    /**
     * Why a {@code width} × {@code height} image may not be decoded with this heap, or null when it may.
     * Pure: the heap's figures are given.
     */
    static @Nullable String refusal(long width, long height, long maxHeap, long usedHeap) {
        if (width <= 0 || height <= 0) return null; // the decoder says what is wrong with it
        long pixels = Math.multiplyHigh(width, height) != 0 ? Long.MAX_VALUE : width * height;
        if (fits(pixels, maxHeap, usedHeap)) return null;
        String size = width + " × " + height + " pixels";
        if (pixels > MAX_PIXELS) return "the image is too large to open (" + size + ")";
        return "the image is too large to open with the memory left (" + size + " need "
                + bytes(pixels * BYTES_PER_PIXEL) + ", "
                + bytes(Math.max(0, maxHeap - usedHeap)) + " is free)";
    }

    /**
     * Throws, naming the size and the memory, when a {@code width} × {@code height} image will not fit
     * the heap now. Asked before the pixels are decoded or copied; a heap that looks full is collected
     * once and asked again, as what looks used may be images already let go.
     */
    static void check(long width, long height) throws TooLarge {
        Runtime runtime = Runtime.getRuntime();
        if (refusal(width, height, runtime.maxMemory(), used(runtime)) == null) return;
        System.gc();
        String reason = refusal(width, height, runtime.maxMemory(), used(runtime));
        if (reason != null) throw new TooLarge(reason);
    }

    private static long used(Runtime runtime) {
        return runtime.totalMemory() - runtime.freeMemory();
    }

    /** {@code 2.4 GB}, {@code 310 MB}: a size as a person reads it. */
    static String bytes(long bytes) {
        double megabytes = bytes / (double) (1L << 20);
        if (megabytes >= 1024) return String.format(Locale.ROOT, "%.1f GB", megabytes / 1024);
        return String.format(Locale.ROOT, "%.0f MB", megabytes);
    }
}
