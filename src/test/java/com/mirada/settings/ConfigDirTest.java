package com.mirada.settings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.mirada.Launch;
import com.mirada.os.Os;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** SET-07: one resolver, the flags over the variable over {@code ~/.mirada}, the same on every OS. */
class ConfigDirTest {

    private static final String HOME = "/home/u";

    private static Path resolve(String env, String... args) {
        Launch launch = Launch.parse(java.util.List.of(args));
        return ConfigDir.resolve(launch.configDir(), launch.dev(), env, HOME);
    }

    @Test
    void configDirThenDevThenTheVariableThenTheDefault() {
        assertEquals(Path.of("/tmp/c"), resolve("/env", "--config-dir", "/tmp/c", "--dev"));
        assertEquals(Path.of("/tmp/c"), resolve("/env", "--dev", "--config-dir", "/tmp/c"));
        assertEquals(Path.of("/tmp/c"), resolve("/env", "--dev", "--config-dir=/tmp/c"));
        assertEquals(Path.of(HOME, ".mirada-dev"), resolve("/env", "--dev"));
        assertEquals(Path.of("/env"), resolve(" /env ", "a.jpg"));
        assertEquals(Path.of(HOME, ".mirada"), resolve("  ", "a.jpg"));
        assertEquals(Path.of(HOME, ".mirada"), resolve(null));
    }

    @Test
    void theDefaultIsTheSameOnEveryOs() {
        try {
            for (Os.Kind kind : Os.Kind.values()) {
                Os.override(kind);
                assertEquals(Path.of(HOME, ".mirada"), ConfigDir.resolve(null, false, null, HOME), kind.name());
            }
        } finally {
            Os.override(null);
        }
    }

    @Test
    void theOldFoldersWereTheOsesOwn() {
        assertEquals(
                Path.of("/Users/a/Library/Application Support/Mirada"),
                ConfigDir.oldFolder(Os.Kind.MAC, "/Users/a", null));
        assertEquals(
                Path.of("C:/Users/a/AppData/Roaming", "Mirada"),
                ConfigDir.oldFolder(Os.Kind.WINDOWS, "C:/Users/a", "C:/Users/a/AppData/Roaming"));
        assertEquals(
                Path.of("C:/Users/a", "AppData", "Roaming", "Mirada"),
                ConfigDir.oldFolder(Os.Kind.WINDOWS, "C:/Users/a", null));
        assertNull(ConfigDir.oldFolder(Os.Kind.LINUX, "/home/a", null));
    }

    @Test
    void anOldFolderMovesToDotMiradaOnce(@TempDir Path home) throws IOException {
        Path old = home.resolve("Library/Application Support/Mirada");
        Files.createDirectories(old);
        Files.writeString(old.resolve(Settings.FILE_NAME), "theme=dark\n");
        Files.writeString(old.resolve("bookmarks.properties"), "x\n");
        Path now = home.resolve(".mirada");

        assertEquals("moved", ConfigDir.adopt(old, now));
        assertEquals("theme=dark\n", Files.readString(now.resolve(Settings.FILE_NAME)));
        assertTrue(Files.isRegularFile(now.resolve("bookmarks.properties")));
        assertFalse(Files.exists(old));
        // Done once: a later old folder never replaces ~/.mirada.
        Files.createDirectories(old);
        Files.writeString(old.resolve(Settings.FILE_NAME), "theme=light\n");
        assertEquals("none", ConfigDir.adopt(old, now));
        assertEquals("theme=dark\n", Files.readString(now.resolve(Settings.FILE_NAME)));
    }

    @Test
    void nothingToMoveIsNothingDone(@TempDir Path home) {
        assertEquals("none", ConfigDir.adopt(null, home.resolve(".mirada")));
        assertEquals("none", ConfigDir.adopt(home.resolve("absent"), home.resolve(".mirada")));
        assertFalse(Files.exists(home.resolve(".mirada")));
    }
}
