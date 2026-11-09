package com.mirada.settings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two Mirada processes over one settings file, each with its own {@link Settings}: a change made in
 * one reaches the other, and neither writes over the other's (SET-05).
 */
class CrossProcessSettingsTest {

    @TempDir
    Path dir;

    @Test
    void theMergeTakesTheirFileWithOurChangeOnTop() {
        Properties base = properties("theme", "system", "view.keepZoom", "false", "recent.1", "/a");
        Properties ours = properties("theme", "system", "view.keepZoom", "true");
        Properties theirs =
                properties("theme", "dark", "view.keepZoom", "false", "recent.1", "/a", "keys.go.next", "n");
        Properties merged = ConfigFile.merged(base, ours, theirs);
        assertEquals("dark", merged.getProperty("theme"), "their change stays");
        assertEquals("true", merged.getProperty("view.keepZoom"), "ours goes on top");
        assertNull(merged.getProperty("recent.1"), "what ours removed goes");
        assertEquals("n", merged.getProperty("keys.go.next"));
        assertEquals(ConfigFile.merged(base, base, theirs), theirs, "with no change of ours, theirs as it is");
    }

    @Test
    void aChangeInOneReachesTheOtherWhenItLooksAgain() throws IOException {
        Path file = dir.resolve("settings.properties");
        Settings one = new Settings(file);
        one.load();
        Settings two = new Settings(file);
        two.load();
        int[] heard = {0};
        two.addListener(() -> heard[0]++);
        two.reloadIfChanged();
        assertEquals(0, heard[0], "nothing changed yet");

        one.setTheme(Settings.ThemeChoice.DARK);
        two.reloadIfChanged();
        assertEquals(Settings.ThemeChoice.DARK, two.theme());
        assertEquals(1, heard[0]);
        two.reloadIfChanged();
        assertEquals(1, heard[0], "read once");

        two.setKeepZoom(true);
        assertFalse(one.keepZoom());
        one.reloadIfChanged();
        assertTrue(one.keepZoom());
    }

    @Test
    void aChangeMadeWithoutLookingFirstKeepsTheOthers() throws IOException {
        Path file = dir.resolve("settings.properties");
        Settings one = new Settings(file);
        one.load();
        Settings two = new Settings(file);
        two.load();

        one.setTheme(Settings.ThemeChoice.DARK);
        one.setSlideshowSeconds(9);
        two.setKeepZoom(true); // two never looked: it must not put the theme back

        Settings fresh = new Settings(file);
        fresh.load();
        assertEquals(Settings.ThemeChoice.DARK, fresh.theme());
        assertEquals(9, fresh.slideshowSeconds());
        assertTrue(fresh.keepZoom());
        assertEquals(Settings.ThemeChoice.DARK, two.theme(), "and two has the others' now");
    }

    /**
     * In the application a change is written on the files' thread (PERF-01): there it goes on top of
     * what another Mirada wrote, this run takes theirs back on the FX thread, and a change made here
     * while that write was on its way stays.
     */
    @Test
    void aChangeWrittenOnTheFilesThreadKeepsTheOthersAndTakesThem() {
        Path file = dir.resolve("settings.properties");
        Settings one = new Settings(file);
        one.load();
        Settings two = new Settings(file);
        two.load();
        List<Runnable> files = new ArrayList<>();
        List<Runnable> fx = new ArrayList<>();
        two.writeOn(new FileWrites(files::add), fx::add);

        one.setTheme(Settings.ThemeChoice.DARK);
        two.setKeepZoom(true);
        two.setSlideshowSeconds(9);
        assertEquals(2, files.size(), "nothing written on the FX thread");
        run(files);
        two.setKeepZoom(false); // while theirs is on its way back
        run(fx);
        assertEquals(Settings.ThemeChoice.DARK, two.theme(), "theirs taken");
        assertFalse(two.keepZoom(), "the change made since stays");
        assertEquals(9, two.slideshowSeconds());
        run(files);

        Settings fresh = new Settings(file);
        fresh.load();
        assertEquals(Settings.ThemeChoice.DARK, fresh.theme());
        assertFalse(fresh.keepZoom());
        assertEquals(9, fresh.slideshowSeconds());
    }

    private static void run(List<Runnable> queue) {
        while (!queue.isEmpty()) queue.removeFirst().run();
    }

    @Test
    void aFileMendedByHandIsReadWhenTheWindowLooksAgain() throws IOException {
        Path file = dir.resolve("settings.properties");
        Files.write(file, new byte[] {'m', '=', '\\', 'u', '1'});
        Settings settings = new Settings(file);
        settings.load(); // set aside: writable, the defaults
        int[] heard = {0};
        settings.addListener(() -> heard[0]++);
        settings.reloadIfChanged();
        assertEquals(0, heard[0], "a missing file is as it was");
        Files.writeString(file, "theme=light\n");
        settings.reloadIfChanged();
        assertEquals(1, heard[0]);
        assertEquals(Settings.ThemeChoice.LIGHT, settings.theme());
    }

    private static Properties properties(String... pairs) {
        Properties p = new Properties();
        for (int i = 0; i < pairs.length; i += 2) p.setProperty(pairs[i], pairs[i + 1]);
        return p;
    }
}
