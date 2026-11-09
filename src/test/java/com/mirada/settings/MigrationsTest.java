package com.mirada.settings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every config file has a version and a step from each older one (SET-09); a file a newer Mirada
 * wrote is kept as {@code <name>.v<n>.bak} and the defaults load (SET-10).
 */
class MigrationsTest {

    @TempDir
    Path dir;

    @Test
    void everyStoreHasAStepForEveryVersionBelowItsOwn() {
        for (Migrations.Schema schema : new Migrations.Schema[] {Migrations.SETTINGS, Migrations.BOOKMARKS}) {
            assertTrue(schema.complete(), schema.file());
            assertEquals(
                    schema.version() - Migrations.UNVERSIONED, schema.steps().size(), schema.file());
        }
        assertEquals(Settings.SCHEMA_VERSION, Migrations.SETTINGS.version());
        assertEquals(Bookmarks.SCHEMA_VERSION, Migrations.BOOKMARKS.version());
        assertFalse(new Migrations.Schema("x", 3, Map.of(1, UnaryOperator.identity())).complete());
    }

    @Test
    void aFileWithoutAVersionIsTheFirstAndAHandEditedOneIsReadAsWellAsItCanBe() {
        assertEquals(1, Migrations.versionOf(properties()));
        assertEquals(2, Migrations.versionOf(properties("schemaVersion", " 2 ")));
        assertEquals(1, Migrations.versionOf(properties("schemaVersion", "two")));
        assertEquals(1, Migrations.versionOf(properties("schemaVersion", "-4")));
    }

    @Test
    void eachStepRunsOnceInOrderAndTheVersionKeyIsTakenOut() {
        Migrations.Schema schema = new Migrations.Schema(
                "test",
                3,
                Map.of(
                        1, p -> rename(p, "dark", "theme"),
                        2, p -> rename(p, "theme", "look")));
        Properties migrated = Migrations.migrate(properties("dark", "true"), schema);
        assertEquals("true", migrated.getProperty("look"));
        assertNull(migrated.getProperty("dark"));
        Properties from2 = Migrations.migrate(properties("schemaVersion", "2", "theme", "x"), schema);
        assertEquals("x", from2.getProperty("look"));
        assertNull(from2.getProperty("schemaVersion"));
        Properties current = Migrations.migrate(properties("schemaVersion", "3", "theme", "x"), schema);
        assertEquals("x", current.getProperty("theme"), "a current file is left as it is");
        Migrations.Schema gap = new Migrations.Schema("gap", 3, Map.of(1, UnaryOperator.identity()));
        assertThrows(IllegalStateException.class, () -> Migrations.migrate(properties(), gap));
    }

    @Test
    void anUnversionedSettingsFileLoadsAndIsWrittenAtTheCurrentVersion() throws IOException {
        Path file = dir.resolve("settings.properties");
        Files.writeString(file, "theme=dark\nslideshow.seconds=9\n");
        Settings settings = new Settings(file);
        settings.load();
        assertEquals(Settings.ThemeChoice.DARK, settings.theme());
        assertEquals(9, settings.slideshowSeconds());
        assertNull(settings.setAside());
        settings.setKeepZoom(true);
        assertTrue(Files.readString(file).contains("schemaVersion=" + Settings.SCHEMA_VERSION));
    }

    @Test
    void tooNewSettingsAreBackedUpAndDefaultsLoaded() throws IOException {
        Path file = dir.resolve("settings.properties");
        int newer = Settings.SCHEMA_VERSION + 1;
        String text = "schemaVersion=" + newer + "\ntheme=dark\nlook.new=1\n";
        Files.writeString(file, text);
        Settings settings = new Settings(file);
        settings.load();
        assertEquals(Settings.ThemeChoice.SYSTEM, settings.theme(), "the defaults");
        Path backup = dir.resolve("settings.properties.v" + newer + ".bak");
        assertEquals(backup, settings.setAside());
        assertEquals(text, Files.readString(backup));
        assertEquals(text, Files.readString(file), "left where it was until something changes");
        settings.setTheme(Settings.ThemeChoice.LIGHT);
        assertEquals(text, Files.readString(backup), "the newer file survives the first write");
    }

    @Test
    void tooNewBookmarksAreBackedUpAndNoMarksLoad() throws IOException {
        Path file = dir.resolve("bookmarks.properties");
        int newer = Bookmarks.SCHEMA_VERSION + 4;
        String text = "schemaVersion=" + newer + "\nmark.0.path=/a.jpg\nmark.0.bookmark=true\n";
        Files.writeString(file, text);
        Bookmarks bookmarks = new Bookmarks(file);
        bookmarks.load();
        assertTrue(bookmarks.all().isEmpty());
        assertEquals(dir.resolve("bookmarks.properties.v" + newer + ".bak"), bookmarks.setAside());
        assertEquals(text, Files.readString(dir.resolve("bookmarks.properties.v" + newer + ".bak")));
    }

    @Test
    void bookmarksAreWrittenAtTheCurrentVersionAndReadBack() throws IOException {
        Path file = dir.resolve("bookmarks.properties");
        Files.writeString(file, "mark.0.path=/a.jpg\nmark.0.bookmark=true\n");
        Bookmarks bookmarks = new Bookmarks(file);
        bookmarks.toggle(dir.resolve("b.jpg"), false);
        assertTrue(Files.readString(file).contains("schemaVersion=" + Bookmarks.SCHEMA_VERSION));
        Bookmarks again = new Bookmarks(file);
        again.load();
        assertEquals(2, again.all().size());
        assertNull(again.setAside());
    }

    @Test
    void aNewerFileThatCannotBeCopiedIsNotWrittenOver() throws IOException {
        Path file = dir.resolve("settings.properties");
        Files.writeString(file, "schemaVersion=99\n");
        Files.createDirectory(dir.resolve("settings.properties.v99.bak"));
        Files.writeString(dir.resolve("settings.properties.v99.bak").resolve("in-the-way"), "x");
        ConfigFile.Result read = ConfigFile.read(file, Migrations.SETTINGS);
        assertEquals(ConfigFile.Outcome.NEWER, read.outcome());
        assertNull(read.backup());
        assertFalse(read.writable());
    }

    private static Properties rename(Properties p, String from, String to) {
        Object value = p.remove(from);
        if (value != null) p.put(to, value);
        return p;
    }

    private static Properties properties(String... pairs) {
        Properties p = new Properties();
        for (int i = 0; i < pairs.length; i += 2) p.setProperty(pairs[i], pairs[i + 1]);
        return p;
    }
}
