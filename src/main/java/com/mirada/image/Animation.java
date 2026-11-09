package com.mirada.image;

import java.util.ArrayList;
import java.util.List;

import javafx.scene.image.Image;

import org.jspecify.annotations.Nullable;

/**
 * An animated image: every frame a whole picture (the decoders compose partial frames), and how
 * long each stays up. GIF is not here: JavaFX animates those itself.
 *
 * @param millis how long each frame shows, one per frame
 */
public record Animation(List<Image> frames, int[] millis) {

    /**
     * The most pixels, over all frames, an animation may hold: 64 million, 256 MB. A longer or
     * larger one shows its first frame only, like a still.
     */
    static final long MAX_PIXELS = 64L * 1024 * 1024;

    /**
     * Whether {@code frames} frames of {@code width} × {@code height} stay within {@link #MAX_PIXELS}:
     * asked before the second frame is decoded, where the format says how many there are (WebP).
     */
    static boolean fits(int width, int height, int frames) {
        return (long) width * height * Math.max(0, frames) <= MAX_PIXELS;
    }

    /** Frames shorter than this are shown for {@link #DEFAULT_MILLIS}, as browsers do. */
    static final int SHORTEST_MILLIS = 20;

    static final int DEFAULT_MILLIS = 100;

    /** A duration as written in the file, made one a person can see. */
    static int sane(long millis) {
        return millis < SHORTEST_MILLIS ? DEFAULT_MILLIS : (int) Math.min(millis, 60_000);
    }

    /** Frames decoded off the toolkit, before they become images. */
    record Frames(List<ArgbPixels> pixels, int[] millis) {

        /** Wrapped as JavaFX images, sharing the arrays. Needs the toolkit; any thread. */
        Animation toAnimation() {
            List<Image> images = new ArrayList<>(pixels.size());
            for (ArgbPixels frame : pixels) images.add(frame.toImage());
            return new Animation(List.copyOf(images), millis.clone());
        }
    }

    /** Collects frames until they would pass {@link #MAX_PIXELS}. */
    static final class Collector {
        private final List<ArgbPixels> frames = new ArrayList<>();
        private final List<Integer> millis = new ArrayList<>();
        private long pixels;

        /** False once the budget is spent: stop decoding, and show the still. */
        boolean add(ArgbPixels frame, long durationMillis) {
            pixels += (long) frame.width() * frame.height();
            if (pixels > MAX_PIXELS) return false;
            frames.add(frame);
            millis.add(sane(durationMillis));
            return true;
        }

        /** The frames, or null for fewer than two (nothing to animate). */
        @Nullable
        Frames frames() {
            if (frames.size() < 2) return null;
            return new Frames(
                    List.copyOf(frames),
                    millis.stream().mapToInt(Integer::intValue).toArray());
        }
    }
}
