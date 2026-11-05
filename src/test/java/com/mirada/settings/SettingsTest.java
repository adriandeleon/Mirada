package com.mirada.settings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.mirada.folder.Sorting;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    void recentFoldersAreKeptLatestFirstWithoutCopies(@TempDir Path dir) {
        Path file = dir.resolve("settings.properties");
        Settings settings = new Settings(file);
        settings.load();
        settings.addRecent(Path.of("/photos/a"));
        settings.addRecent(Path.of("/photos/b"));
        settings.addRecent(Path.of("/photos/a"));

        Settings again = new Settings(file);
        again.load();
        assertEquals(List.of(Path.of("/photos/a"), Path.of("/photos/b")), again.recent());
    }

    @Test
    void onlyTheLatestTenAreKept() {
        List<Path> folders = List.of();
        for (int i = 0; i < 15; i++) folders = Settings.withRecent(folders, Path.of("/f" + i));
        assertEquals(Settings.MAX_RECENT, folders.size());
        assertEquals(Path.of("/f14"), folders.getFirst());
        assertEquals(Path.of("/f5"), folders.getLast());
    }

    @Test
    void theWindowsPlaceIsKept(@TempDir Path dir) {
        Path file = dir.resolve("settings.properties");
        Settings settings = new Settings(file);
        settings.load();
        assertNull(settings.window());
        settings.setWindow(new Settings.WindowPlace(-1200, 40, 1400, 900, true));

        Settings again = new Settings(file);
        again.load();
        assertEquals(new Settings.WindowPlace(-1200, 40, 1400, 900, true), again.window());
    }

    @Test
    void aBadWindowPlaceIsNone() {
        assertNull(Settings.windowOf("10,20,800", null));
        assertNull(Settings.windowOf("10,20,800,x", null));
        assertNull(Settings.windowOf("10,20,30,40", null)); // too small to be a window
        assertNull(Settings.windowOf("NaN,20,800,600", null));
        assertEquals(new Settings.WindowPlace(10, 20, 800, 600, false), Settings.windowOf(" 10, 20,800 ,600", "nope"));
    }

    @Test
    void theStripsSideAndNamesAreKept(@TempDir Path dir) {
        Path file = dir.resolve("settings.properties");
        Settings settings = new Settings(file);
        settings.load();
        assertFalse(settings.thumbnailsAtSide());
        assertFalse(settings.thumbnailNames());
        settings.setThumbnailsAtSide(true);
        assertFalse(settings.thumbnailCaptions());
        settings.setThumbnailNames(true);
        settings.setThumbnailCaptions(true);

        Settings again = new Settings(file);
        again.load();
        assertTrue(again.thumbnailsAtSide());
        assertTrue(again.thumbnailNames());
        assertTrue(again.thumbnailCaptions());
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
    void eachPlatformKeepsTheSettingsInItsOwnFolder() {
        assertEquals(Path.of("/home/a/.mirada"), Settings.folder(null, "/home/a", "Linux", null));
        assertEquals(
                Path.of("/Users/a/Library/Application Support/Mirada"),
                Settings.folder(null, "/Users/a", "Mac OS X", null));
        assertEquals(
                Path.of("C:/Users/a/AppData/Roaming", "Mirada"),
                Settings.folder(null, "C:/Users/a", "Windows 11", "C:/Users/a/AppData/Roaming"));
        // Without APPDATA, where it would be.
        assertEquals(
                Path.of("C:/Users/a", "AppData", "Roaming", "Mirada"),
                Settings.folder(null, "C:/Users/a", "Windows 11", null));
    }

    @Test
    void theOverrideWinsEverywhere() {
        assertEquals(Path.of("/tmp/x"), Settings.folder("/tmp/x", "/home/a", "Mac OS X", null));
        assertEquals(Path.of("/home/a/.mirada"), Settings.folder("  ", "/home/a", "Linux", null));
    }

    @Test
    void settingsLeftInDotMiradaMoveToTheNewFolderOnce(@TempDir Path home) throws IOException {
        Path old = home.resolve(".mirada").resolve(Settings.FILE_NAME);
        Files.createDirectories(old.getParent());
        Files.writeString(old, "theme=dark\n");
        Path now = home.resolve("Library/Application Support/Mirada").resolve(Settings.FILE_NAME);

        assertEquals(now, Settings.movedFrom(old, now));
        assertEquals("theme=dark\n", Files.readString(now));
        assertFalse(Files.exists(old.getParent()), "the emptied folder goes");
        // Done once: the new file is not replaced by a later old one.
        Files.createDirectories(old.getParent());
        Files.writeString(old, "theme=light\n");
        assertEquals(now, Settings.movedFrom(old, now));
        assertEquals("theme=dark\n", Files.readString(now));
    }

    @Test
    void nothingToMoveIsTheNewFile(@TempDir Path home) {
        Path old = home.resolve(".mirada").resolve(Settings.FILE_NAME);
        Path now = home.resolve("Mirada").resolve(Settings.FILE_NAME);
        assertEquals(now, Settings.movedFrom(old, now));
        assertEquals(old, Settings.movedFrom(old, old));
    }
}
