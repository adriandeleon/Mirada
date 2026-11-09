package com.mirada.ui;

import java.awt.Color;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;

import com.mirada.settings.Bookmarks;
import com.mirada.settings.ConfigImport;
import com.mirada.settings.Settings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.api.FxToolkit;

import static com.mirada.ui.FxTestSupport.call;
import static com.mirada.ui.FxTestSupport.fx;
import static com.mirada.ui.FxTestSupport.waitUntil;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every setting changes from the palette as its Settings row would change it (SET-06), and Export
 * and Import Configuration run from it (SET-12), in a real headless window.
 */
@Tag("fx")
class SettingsCommandsTest {

    @TempDir
    Path dir;

    @AfterEach
    void cleanUp() throws Exception {
        FileDialogs.answeredBy(null);
        FxToolkit.cleanupStages();
        fx(() -> Keybindings.use(Settings.DEFAULT_KEYMAP, Map.of(), Settings.defaultPaletteKeys(Command.MAC)));
    }

    private WindowFixture open() throws Exception {
        Path image = WindowFixture.png(dir.resolve("photos"), "a.png", 20, 20, Color.ORANGE);
        WindowFixture window = WindowFixture.open(dir.resolve("config"), image);
        window.waitForImage("a.png");
        return window;
    }

    @Test
    void theTogglesFlipTheirSettingAndSaySo() throws Exception {
        try (WindowFixture window = open()) {
            Settings settings = window.settings;
            window.run(Command.THUMBNAILS_AT_START);
            assertTrue(settings.startWithThumbnails());
            assertEquals("The thumbnails will open at start", window.lastSaid());
            window.run(Command.THUMBNAILS_AT_START);
            assertFalse(settings.startWithThumbnails());
            window.run(Command.PROPERTIES_AT_START);
            assertTrue(settings.startWithInfo());
            assertEquals("The properties will open at start", window.lastSaid());
            window.run(Command.PROPERTIES_AT_START);
            assertEquals("The properties will stay closed at start", window.lastSaid());
            boolean daily = settings.checkForUpdates();
            window.run(Command.CHECK_DAILY);
            assertEquals(!daily, settings.checkForUpdates());
            window.run(Command.CHECK_DAILY);
            assertEquals(daily, settings.checkForUpdates());
            assertEquals("Mirada will check for updates once a day", window.lastSaid());
        }
    }

    @Test
    void theChoicesAreAskedInThePaletteWithTheOneInUseUnderTheCursor() throws Exception {
        try (WindowFixture window = open()) {
            Settings settings = window.settings;
            CommandPalette palette = window.field("palette");

            window.run(Command.THEME);
            assertTrue(call(palette::isShowing));
            type(window, "dark");
            window.press(KeyCode.ENTER);
            assertEquals(Settings.ThemeChoice.DARK, settings.theme());
            assertEquals("Theme: Dark", window.lastSaid());

            window.run(Command.HIDE_CONTROLS_AFTER);
            type(window, "7 sec");
            window.press(KeyCode.ENTER);
            assertEquals(7, settings.controlsHideSeconds());

            window.run(Command.SLIDESHOW_SPEED);
            window.press(KeyCode.ENTER); // the one in use: nothing changes
            assertEquals(5, settings.slideshowSeconds());
            window.run(Command.SLIDESHOW_SPEED);
            type(window, "12 sec");
            window.press(KeyCode.ENTER);
            assertEquals(12, settings.slideshowSeconds());

            window.run(Command.KEYMAP);
            type(window, "emacs");
            window.press(KeyCode.ENTER);
            assertEquals("emacs", settings.keymap());
            fx(() -> settings.setTheme(Settings.ThemeChoice.SYSTEM));
        }
    }

    @Test
    void paletteKeysOpenSettingsOnTheKeysPageWaitingForAKey() throws Exception {
        try (WindowFixture window = open()) {
            window.run(Command.PALETTE_KEYS);
            SettingsWindow settingsWindow = window.field("settingsWindow");
            assertTrue(call(() -> settingsWindow.stage().isShowing()));
            assertEquals(SettingsPages.Page.KEYS, call(settingsWindow::page));
            fx(() -> javafx.event.Event.fireEvent(
                    settingsWindow.stage().getScene().getRoot(),
                    new javafx.scene.input.KeyEvent(
                            javafx.scene.input.KeyEvent.KEY_PRESSED, "", "", KeyCode.F4, false, false, false, false)));
            assertTrue(window.settings.paletteKeys().endsWith("f4"));
        }
    }

    @Test
    void exportThenImportPutsTheTickedPartsBack() throws Exception {
        try (WindowFixture window = open()) {
            Settings settings = window.settings;
            window.waitForFirstPaint();
            fx(() -> settings.setTheme(Settings.ThemeChoice.DARK));
            fx(() -> settings.setKeymap("emacs"));
            Bookmarks bookmarks = window.field("bookmarks");
            Path photo = dir.resolve("photos").resolve("a.png");
            fx(() -> bookmarks.toggle(photo, false));

            Path zip = dir.resolve("out").resolve("mine.zip");
            Files.createDirectories(zip.getParent());
            AtomicReference<String> suggested = new AtomicReference<>();
            FileDialogs.answeredBy((title, folder, name) -> {
                suggested.set(name);
                return zip.toFile();
            });
            window.run(Command.EXPORT_CONFIG);
            window.waitToSay("Exported 2 files of configuration to mine.zip");
            assertTrue(suggested.get().matches("mirada-config-\\d{8}-\\d{6}\\.zip"), suggested.get());

            // Changed since, then the keys and the bookmarks imported back; the theme left as it is.
            fx(() -> settings.setTheme(Settings.ThemeChoice.LIGHT));
            fx(() -> settings.setKeymap("cua"));
            fx(() -> bookmarks.toggle(photo, false));
            assertFalse(bookmarks.isBookmarked(photo));

            Thread answer = WindowFixture.answerNextDialog(pane -> {
                for (javafx.scene.Node node : pane.lookupAll(".check-box")) {
                    CheckBox box = (CheckBox) node;
                    if (box.getUserData() == ConfigImport.Part.PREFERENCES) box.setSelected(false);
                }
                WindowFixture.press(pane, "Import");
            });
            window.run(Command.IMPORT_CONFIG);
            answer.join();
            window.waitToSay("Imported the keys and the bookmarks and notes");
            assertEquals("emacs", settings.keymap());
            assertEquals(Settings.ThemeChoice.LIGHT, settings.theme(), "unticked: kept");
            waitUntil("the bookmark back", () -> bookmarks.isBookmarked(photo));
            fx(() -> settings.setTheme(Settings.ThemeChoice.SYSTEM));
        }
    }

    @Test
    void anImportCancelledOrRefusedChangesNothing() throws Exception {
        try (WindowFixture window = open()) {
            Path notAZip = Files.writeString(dir.resolve("x.zip"), "hello");
            FileDialogs.answeredBy((title, folder, name) -> notAZip.toFile());
            window.run(Command.IMPORT_CONFIG);
            window.waitToSay("That file is not a zip");

            Path zip = dir.resolve("theirs.zip");
            FileDialogs.answeredBy((title, folder, name) -> zip.toFile());
            fx(() -> window.settings.setTheme(Settings.ThemeChoice.DARK));
            window.run(Command.EXPORT_CONFIG);
            window.waitToSay("Exported");
            fx(() -> window.settings.setTheme(Settings.ThemeChoice.LIGHT));
            Thread answer = WindowFixture.answerNextDialog(pane -> WindowFixture.press(pane, "Cancel"));
            window.run(Command.IMPORT_CONFIG);
            answer.join();
            Thread.sleep(200);
            assertEquals(Settings.ThemeChoice.LIGHT, window.settings.theme());
            fx(() -> window.settings.setTheme(Settings.ThemeChoice.SYSTEM));

            FileDialogs.answeredBy((title, folder, name) -> null);
            window.run(Command.EXPORT_CONFIG);
            window.run(Command.IMPORT_CONFIG);
        }
    }

    @Test
    void theWordsAfterwards() {
        assertEquals("1 second", SettingsCommands.seconds(1));
        assertEquals("10 seconds", SettingsCommands.seconds(10));
        assertEquals(List.of(1, 2, 3), SettingsCommands.range(1, 3));
        assertEquals("Exported 1 file of configuration to a.zip", SettingsCommands.exported(Path.of("a.zip"), 1));
        assertEquals("Nothing was imported", SettingsCommands.imported(EnumSet.noneOf(ConfigImport.Part.class)));
        assertEquals("Imported the keys", SettingsCommands.imported(EnumSet.of(ConfigImport.Part.KEYS)));
        assertEquals(
                "Imported the preferences, the keys and the bookmarks and notes",
                SettingsCommands.imported(EnumSet.allOf(ConfigImport.Part.class)));
        assertNull(SettingsCommands.setAside("settings", null));
        assertEquals(
                "The settings are from a newer Mirada: kept as settings.properties.v3.bak, and the defaults are in use",
                SettingsCommands.setAside("settings", Path.of("/c/settings.properties.v3.bak")));
        assertEquals(
                "The bookmarks could not be read: kept as bookmarks.properties.corrupt.bak, and the defaults are in"
                        + " use",
                SettingsCommands.setAside("bookmarks", Path.of("bookmarks.properties.corrupt.bak")));
    }

    @Test
    void aNewerSettingsFileIsKeptAndTheWindowSaysSo() throws Exception {
        Path config = dir.resolve("config");
        Files.createDirectories(config);
        Files.writeString(config.resolve("settings.properties"), "schemaVersion=99\ntheme=dark\n");
        Path image = WindowFixture.png(dir.resolve("photos"), "a.png", 20, 20, Color.ORANGE);
        try (WindowFixture window = WindowFixture.open(config, image)) {
            window.waitForImage("a.png");
            window.waitForFirstPaint();
            window.waitToSay("The settings are from a newer Mirada: kept as settings.properties.v99.bak");
            assertTrue(Files.exists(config.resolve("settings.properties.v99.bak")));
        }
    }

    /** SET-05: two windows, as two Mirada processes, each with its own settings over one file. */
    @Test
    void aSettingChangedInOneWindowIsAppliedInTheOtherAsItComesForward() throws Exception {
        Path image = WindowFixture.png(dir.resolve("photos"), "a.png", 20, 20, Color.ORANGE);
        try (WindowFixture one = WindowFixture.open(dir.resolve("config"), image);
                WindowFixture two = WindowFixture.open(dir.resolve("config"), image)) {
            one.waitForImage("a.png");
            two.waitForImage("a.png");
            assertFalse(two.settings.keepZoom());
            one.run(Command.KEEP_ZOOM);
            assertTrue(one.settings.keepZoom());
            // The headless platform leaves every window focused: the other one goes back and comes
            // forward as the desktop would make it when clicked.
            fx(() -> focus(two, false));
            fx(() -> focus(two, true));
            waitUntil("the other window's settings read again", two.settings::keepZoom);
            ImageViewport viewport = two.field("viewport");
            assertTrue(call(() -> FxTestSupport.<Boolean>field(viewport, "keepZoom")), "and applied there");
            fx(() -> one.settings.setKeepZoom(false));
        }
    }

    private static void focus(WindowFixture window, boolean focused) {
        FxTestSupport.invoke(window.stage, "setFocused", new Class<?>[] {boolean.class}, focused);
    }

    private static void type(WindowFixture window, String text) throws Exception {
        CommandPalette palette = window.field("palette");
        TextField input = FxTestSupport.field(palette, "input");
        fx(() -> input.setText(text));
    }
}
