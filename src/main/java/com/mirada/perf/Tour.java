package com.mirada.perf;

import java.nio.file.Path;

import javafx.animation.AnimationTimer;
import javafx.animation.PauseTransition;
import javafx.util.Duration;

import org.jspecify.annotations.Nullable;

/**
 * Steps through the whole folder on its own, timing each step from the request to the image on
 * screen, then exits.
 *
 * <p>Two uses, one path. With {@code MIRADA_PERF_TOUR=1} it measures stepping, the other number
 * that matters: a step onto a prefetched neighbour should be a frame or two, a step onto an
 * uncached image is a decode. With {@code -Dmirada.aotTrain} it is the AOT training run: stepping
 * through one file of every format loads every decoder's classes, ImageIO's service lookup and the
 * native bindings, so the cache holds them all, not only what showing the first image needed.
 *
 * <p>Off unless asked for: {@link #enabled} is a static boolean.
 */
public final class Tour {

    private static final boolean TRAINING = System.getProperty("mirada.aotTrain") != null;
    private static final boolean ENABLED =
            TRAINING || "1".equals(System.getenv("MIRADA_PERF_TOUR")) || Boolean.getBoolean("mirada.tour");

    /**
     * How long it looks at each image before the next, like a person stepping briskly. Without a
     * pause every step outruns the neighbour prefetch and measures a decode no user would wait for.
     * {@code MIRADA_PERF_TOUR_PAUSE=0} measures exactly that worst case; training needs no pause.
     */
    private static final long PAUSE_MS = TRAINING ? 0 : pause(System.getenv("MIRADA_PERF_TOUR_PAUSE"));

    private final Runnable next;
    private long requestedAt;
    private boolean prefetched;
    private int steps;

    /** @param next steps to the next image in the folder */
    public Tour(Runnable next) {
        this.next = next;
    }

    public static boolean enabled() {
        return ENABLED;
    }

    /** Creates a tour when one was asked for. */
    public static @Nullable Tour start(Runnable next) {
        return ENABLED ? new Tour(next) : null;
    }

    /** An image was asked for; {@code ready} is whether its decode had already finished. */
    public void requested(boolean ready) {
        requestedAt = System.nanoTime();
        prefetched = ready;
    }

    /**
     * The requested image is in the scene. Reports once the frame that shows it has completed —
     * the second animation tick, as {@link Startup#markFirstPaint} explains — then asks for the next,
     * or exits when {@code last}.
     */
    public void shown(Path file, boolean last) {
        new AnimationTimer() {
            private int ticks;

            @Override
            public void handle(long now) {
                if (++ticks < 2) return;
                stop();
                if (steps++ > 0) {
                    long millis = (System.nanoTime() - requestedAt) / 1_000_000L;
                    Path name = file.getFileName();
                    System.out.printf(
                            "[perf] step  %-24s %5d ms  (%s)%n",
                            name == null ? file : name, millis, prefetched ? "prefetched" : "decoded");
                }
                if (last) {
                    System.out.flush();
                    System.exit(0);
                }
                if (PAUSE_MS == 0) {
                    next.run();
                } else {
                    PauseTransition look = new PauseTransition(Duration.millis(PAUSE_MS));
                    look.setOnFinished(e -> next.run());
                    look.play();
                }
            }
        }.start();
    }

    static long pause(@Nullable String setting) {
        if (setting == null || setting.isBlank()) return 300;
        try {
            return Math.max(0, Long.parseLong(setting.trim()));
        } catch (NumberFormatException e) {
            return 300;
        }
    }

    /**
     * The requested image could not be shown. Says so, as {@code [perf] fail name: reason}, and goes
     * on to the next, so one run of the smoke test names every file that fails rather than stopping
     * at the first.
     */
    public void failed(Path file, String reason, boolean last) {
        Path name = file.getFileName();
        System.out.printf("[perf] fail  %s: %s%n", name == null ? file : name, reason);
        steps++;
        if (last) {
            System.out.flush();
            System.exit(0);
        }
        next.run();
    }

    /** Whether this run is recording the AOT cache. */
    public static boolean training() {
        return TRAINING;
    }
}
