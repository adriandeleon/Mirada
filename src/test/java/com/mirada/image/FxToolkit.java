package com.mirada.image;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javafx.application.Platform;

/**
 * Starts the JavaFX toolkit once per test run, for tests that make images. Headless with software
 * rendering: surefire sets {@code glass.platform} and {@code prism.order} before any class loads.
 */
final class FxToolkit {

    private static boolean started;

    private FxToolkit() {}

    static synchronized void start() throws InterruptedException {
        if (started) return;
        CountDownLatch up = new CountDownLatch(1);
        try {
            Platform.startup(up::countDown);
        } catch (IllegalStateException alreadyRunning) {
            up.countDown();
        }
        if (!up.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("JavaFX did not start");
        Platform.setImplicitExit(false);
        started = true;
    }
}
