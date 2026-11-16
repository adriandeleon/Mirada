package com.mirada.ui;

import javafx.animation.Animation;
import javafx.animation.PauseTransition;
import javafx.util.Duration;

/**
 * Text-driven work that leaves the FX thread waits for typing to pause (PERF-23): every request
 * restarts the wait, and the action runs once, when it ends (the app template's {@code Debounce},
 * Editora's 150 ms before a search). A filter over a list in memory needs none and keeps {@link
 * FxThrottle}. FX thread only.
 */
final class Debounce {

    /** How long typing has to pause before a search runs. */
    static final Duration TYPING_PAUSE = Duration.millis(150);

    private final PauseTransition pause;
    private final Runnable action;

    Debounce(Runnable action) {
        this(TYPING_PAUSE, action);
    }

    Debounce(Duration wait, Runnable action) {
        this.action = action;
        this.pause = new PauseTransition(wait);
        pause.setOnFinished(e -> action.run());
    }

    /** Asks for the action: it runs once the requests stop for the pause. */
    void request() {
        pause.playFromStart();
    }

    /** Whether a run is waiting for the pause to end. */
    boolean pending() {
        return pause.getStatus() == Animation.Status.RUNNING;
    }

    /** Drops a waiting action: nothing runs. */
    void cancel() {
        pause.stop();
    }

    /** Runs a waiting action now (Enter in a find field acts on what was typed). */
    void flush() {
        if (!pending()) return;
        pause.stop();
        action.run();
    }
}
