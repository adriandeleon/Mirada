package com.mirada.ui;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import javafx.animation.Animation;
import javafx.application.Platform;

/**
 * Motion follows the OS (Pauta ACC-04): every animation that moves, fades or flashes part of the
 * interface starts here, and while the system asks for reduced motion ({@code
 * Platform.Preferences.reducedMotionProperty}) it jumps to its end instead of playing. A change to
 * the setting applies at once: the animations under way finish there and then. Timers that move
 * nothing (a {@code PauseTransition} waiting to hide the controls) are not motion and do not come
 * here. FX thread.
 */
final class Motion {

    /** The animations started here and still running, held weakly: one that ends is let go. */
    private static final Set<Animation> RUNNING = Collections.newSetFromMap(new WeakHashMap<>());

    private static boolean listening;

    private Motion() {}

    /** Plays {@code animation}, or puts it at its end at once while motion is reduced. */
    static void play(Animation animation) {
        listen();
        animation.play();
        if (reduced()) finish(animation);
        else RUNNING.add(animation);
    }

    /** Whether the system asks for reduced motion now. */
    static boolean reduced() {
        return Platform.getPreferences().isReducedMotion();
    }

    private static void finish(Animation animation) {
        // To its last frame now; its next pulse sees it done and runs its onFinished.
        animation.jumpTo(animation.getTotalDuration());
    }

    private static void listen() {
        if (listening) return;
        listening = true;
        Platform.getPreferences().reducedMotionProperty().addListener((o, was, now) -> {
            if (!now) return;
            for (Animation animation : Set.copyOf(RUNNING)) {
                if (animation.getStatus() == Animation.Status.RUNNING) finish(animation);
            }
            RUNNING.clear();
        });
    }
}
