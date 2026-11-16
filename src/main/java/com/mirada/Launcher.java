package com.mirada;

/**
 * Mirada's entry point, for every launch: the native image (the {@code native} profile, StaticFX),
 * the JVM app image (jpackage), {@code javafx:run}, the AOT training run and {@code
 * scripts/dev-run.sh}. A main class that extends {@code Application} has the JDK's launcher start
 * the toolkit before its {@code main} runs, so the settings read {@link App#main} starts would
 * follow the toolkit instead of overlapping it (PERF-16); a plain class that calls {@link App#main}
 * lets {@code main} start the read first. In a native image built from the class path it also skips
 * the launcher's check that JavaFX is on the module path.
 */
public final class Launcher {

    private Launcher() {}

    public static void main(String[] args) {
        App.main(args);
    }
}
