package com.mirada.settings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** A config file that will not read never stops a start and is never lost to one (SET-11). */
class ConfigFileTest {

    private static final byte[] MALFORMED_ESCAPE = {'m', '=', '\\', 'u', '1'};

    @Test
    void aFileThatWillNotDecodeIsKeptAsABackup(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("settings.properties");
        Files.write(file, MALFORMED_ESCAPE);
        ConfigFile.Result read = ConfigFile.read(file);
        assertEquals(ConfigFile.Outcome.SET_ASIDE, read.outcome());
        assertTrue(read.properties().isEmpty());
        assertTrue(read.writable());
        assertFalse(Files.exists(file));
        assertArrayEquals(MALFORMED_ESCAPE, Files.readAllBytes(dir.resolve("settings.properties.corrupt.bak")));
    }

    @Test
    void bytesThatAreNotUtf8AreSetAsideToo(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("settings.properties");
        Files.write(file, new byte[] {'t', '=', (byte) 0xC3, (byte) 0x28});
        assertEquals(ConfigFile.Outcome.SET_ASIDE, ConfigFile.read(file).outcome());
    }

    @Test
    void aMissingFileIsTheDefaults(@TempDir Path dir) {
        ConfigFile.Result read = ConfigFile.read(dir.resolve("none.properties"));
        assertEquals(ConfigFile.Outcome.MISSING, read.outcome());
        assertTrue(read.writable());
    }

    @Test
    void settingsStartFromTheDefaultsAndWriteAFreshFile(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("settings.properties");
        Files.write(file, MALFORMED_ESCAPE);
        Settings settings = new Settings(file);
        settings.load();
        assertEquals(Settings.ThemeChoice.SYSTEM, settings.theme());
        settings.setTheme(Settings.ThemeChoice.DARK);
        assertTrue(Files.readString(file).contains("theme=dark"));
        assertTrue(Files.exists(ConfigFile.backupOf(file)));
    }

    @Test
    void aBookmarksFileThatCannotBeReadIsNeverWrittenOver(@TempDir Path dir) throws IOException {
        assumeTrue(dir.getFileSystem().supportedFileAttributeViews().contains("posix"));
        Path file = dir.resolve("bookmarks.properties");
        Files.writeString(file, "mark.0.path=/a.jpg\nmark.0.bookmark=true\n");
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("-w-------"));
        assumeTrue(!Files.isReadable(file)); // root reads anything
        try {
            new Bookmarks(file).toggle(dir.resolve("b.jpg"), false);
        } finally {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        }
        assertEquals("mark.0.path=/a.jpg\nmark.0.bookmark=true\n", Files.readString(file));
    }
}
