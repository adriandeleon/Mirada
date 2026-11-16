package com.mirada.ui;

import javafx.application.Platform;

/**
 * Coalesces a list's refreshes (PERF-06): any number of requests before the FX thread is next free
 * run the refresh once, through a {@code pending} flag. A burst of messages, or of bookmark changes,
 * makes the list once, not once a change. FX thread only.
 */
final class Redraw {

    private final Runnable action;
    private boolean pending;

    Redraw(Runnable action) {
        this.action = action;
    }

    /** Runs the refresh on the next free turn of the FX thread, unless one is already queued. */
    void request() {
        if (pending) return;
        pending = true;
        Platform.runLater(() -> {
            pending = false;
            action.run();
        });
    }

    boolean pending() {
        return pending;
    }
}
