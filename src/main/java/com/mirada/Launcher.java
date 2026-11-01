package com.mirada;

/**
 * The native image's entry point (the {@code native} profile, StaticFX). A main class that extends
 * {@code Application} makes the JDK's launcher check that JavaFX is on the module path, which it is
 * not in a native image built from the class path; a plain class that calls {@link App#main} skips
 * that check. The jlink image and the dev runs keep using App directly.
 */
public final class Launcher {

    private Launcher() {}

    public static void main(String[] args) {
        App.main(args);
    }
}
