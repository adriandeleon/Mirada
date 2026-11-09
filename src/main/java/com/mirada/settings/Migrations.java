package com.mirada.settings;

import java.util.Map;
import java.util.Properties;
import java.util.function.UnaryOperator;

/**
 * The versions of Mirada's config files and the steps between them (SET-09, Pauta 4.0.1). Each file
 * says its version in {@code schemaVersion}; step {@code v} turns a version-{@code v} file into
 * version {@code v + 1}. A purely additive change still gets a step, the identity, so every version
 * has one and the chain never has a gap ({@code MigrationsTest} checks). A file newer than the
 * running build is not migrated but kept aside ({@link ConfigFile}, SET-10).
 *
 * <p>Pure: properties in, properties out.
 */
final class Migrations {

    /** The key every versioned file holds its version under. */
    static final String VERSION_KEY = "schemaVersion";

    /** A file with no {@link #VERSION_KEY} predates versioning (0.1 to 0.3) and is version 1. */
    static final int UNVERSIONED = 1;

    /** One file's current version and its steps up to it, by the version each starts from. */
    record Schema(String file, int version, Map<Integer, UnaryOperator<Properties>> steps) {

        /** Whether every version from {@link #UNVERSIONED} up to the current one has a step. */
        boolean complete() {
            for (int v = UNVERSIONED; v < version; v++) {
                if (!steps.containsKey(v)) return false;
            }
            return true;
        }
    }

    /**
     * {@code settings.properties}. Version 2 only adds {@code schemaVersion} itself: the identity
     * step.
     */
    static final Schema SETTINGS =
            new Schema(Settings.FILE_NAME, Settings.SCHEMA_VERSION, Map.of(1, UnaryOperator.identity()));

    /**
     * {@code bookmarks.properties}. Version 2 only adds {@code schemaVersion} itself: the identity
     * step.
     */
    static final Schema BOOKMARKS =
            new Schema(Bookmarks.FILE_NAME, Bookmarks.SCHEMA_VERSION, Map.of(1, UnaryOperator.identity()));

    private Migrations() {}

    /**
     * The version a file says it is: {@link #UNVERSIONED} without one, and for one that is not a
     * whole number of at least 1 (a hand edit), which the steps then bring forward.
     */
    static int versionOf(Properties properties) {
        String value = properties.getProperty(VERSION_KEY);
        if (value == null) return UNVERSIONED;
        try {
            return Math.max(UNVERSIONED, Integer.parseInt(value.trim()));
        } catch (NumberFormatException e) {
            return UNVERSIONED;
        }
    }

    /**
     * Runs every step from the file's version up to the schema's, and returns the properties without
     * {@link #VERSION_KEY}: what a file holds, not which version it was. A file at or past the
     * current version is returned as it is, less the key.
     *
     * @throws IllegalStateException when a step is missing, which {@link Schema#complete} rules out
     */
    static Properties migrate(Properties properties, Schema schema) {
        Properties current = properties;
        for (int v = versionOf(properties); v < schema.version(); v++) {
            UnaryOperator<Properties> step = schema.steps().get(v);
            if (step == null) throw new IllegalStateException("no migration of " + schema.file() + " from " + v);
            current = step.apply(current);
        }
        current.remove(VERSION_KEY);
        return current;
    }
}
