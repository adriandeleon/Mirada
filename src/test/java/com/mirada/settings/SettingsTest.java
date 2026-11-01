package com.mirada.settings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.mirada.folder.Sorting;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsTest {

    @Test
    void theDefaultsWithoutAFile(@TempDir Path dir) {
        Settings settings = new Settings(dir.resolve("settings.properties"));
        settings.load();
        assertEquals(Settings.ThemeChoice.SYSTEM, settings.theme());
        assertFalse(settings.startWithThumbnails());
        assertFalse(settings.startWithInfo());
        assertEquals(2, settings.controlsHideSeconds());
        assertEquals(Sorting.DEFAULT, settings.sorting());
        assertFalse(settings.keepZoom());
        assertEquals(5, settings.slideshowSeconds());
    }

    @Test
    void theFolderOrderZoomAndSlideshowAreKept(@TempDir Path dir) {
        Path file = dir.resolve("settings.properties");
        Settings settings = new Settings(file);
        settings.setSorting(new Sorting(Sorting.By.DATE, true));
        settings.setKeepZoom(true);
        settings.setSlideshowSeconds(12);

        Settings again = new Settings(file);
        again.load();
        assertEquals(new Sorting(Sorting.By.DATE, true), again.sorting());
        assertTrue(again.keepZoom());
        assertEquals(12, again.slideshowSeconds());
        assertEquals(Settings.MAX_SLIDESHOW_SECONDS, Settings.slideshowSecondsOf("600"));
        assertEquals(5, Settings.slideshowSecondsOf("fast"));
    }

    @Test
    void everyChangeIsWrittenThroughAndReadBack(@TempDir Path dir) {
        Path file = dir.resolve("nested/settings.properties");
        Settings settings = new Settings(file);
        int[] notified = {0};
        settings.addListener(() -> notified[0]++);
        settings.setTheme(Settings.ThemeChoice.DARK);
        settings.setStartWithThumbnails(true);
        settings.setStartWithInfo(true);
        settings.setControlsHideSeconds(5);
        assertEquals(4, notified[0]);
        assertTrue(Files.isRegularFile(file));

        Settings again = new Settings(file);
        again.load();
        assertEquals(Settings.ThemeChoice.DARK, again.theme());
        assertTrue(again.startWithThumbnails());
        assertTrue(again.startWithInfo());
        assertEquals(5, again.controlsHideSeconds());
    }

    /** The file is meant to be edited by hand, so nonsense in it is the defaults, not a failed start. */
    @Test
    void badValuesAreClampedOnRead(@TempDir Path dir) throws IOException {
        Path file = Files.writeString(
                dir.resolve("settings.properties"), "theme=purple\ncontrols.hideSeconds=999\nstart.info= TRUE \n");
        Settings settings = new Settings(file);
        settings.load();
        assertEquals(Settings.ThemeChoice.SYSTEM, settings.theme());
        assertEquals(Settings.MAX_HIDE_SECONDS, settings.controlsHideSeconds());
        assertTrue(settings.startWithInfo());
        assertEquals(2, Settings.hideSecondsOf("soon"));
        assertEquals(Settings.MIN_HIDE_SECONDS, Settings.hideSecondsOf("-3"));
    }

    @Test
    void theFileLivesInDotMiradaUnlessMoved() {
        assertEquals(Path.of("/home/a/.mirada/settings.properties"), Settings.defaultFile(null, "/home/a"));
        assertEquals(Path.of("/tmp/x/settings.properties"), Settings.defaultFile("/tmp/x", "/home/a"));
        assertEquals(Path.of("/home/a/.mirada/settings.properties"), Settings.defaultFile("  ", "/home/a"));
    }
}
