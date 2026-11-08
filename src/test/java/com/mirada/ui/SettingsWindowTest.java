package com.mirada.ui;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import com.mirada.folder.Sorting;
import com.mirada.settings.Settings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.api.FxToolkit;

import static com.mirada.ui.FxTestSupport.call;
import static com.mirada.ui.FxTestSupport.fx;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every control of the Settings window writes its setting at once, and a setting changed elsewhere
 * (the menu, a key) shows in the window: the two never disagree.
 */
@Tag("fx")
class SettingsWindowTest {

    @TempDir
    Path dir;

    private Settings settings = new Settings(Path.of("unset"));
    private SettingsWindow window = null;
    private Stage stage = null;

    @BeforeEach
    void open() throws Exception {
        FxTestSupport.start();
        settings = new Settings(dir.resolve("settings.properties"));
        settings.load();
        window = call(() -> new SettingsWindow(settings, ViewerWindow.stylesheet()));
        Stage owner = call(Stage::new);
        fx(() -> {
            owner.setX(0);
            owner.setY(0);
            owner.setWidth(800);
            owner.setHeight(600);
            window.show(owner);
        });
        stage = window.stage();
    }

    @AfterEach
    void close() throws Exception {
        FxToolkit.cleanupStages();
        fx(() -> Keybindings.use(Settings.DEFAULT_KEYMAP, Map.of(), Settings.defaultPaletteKeys(Command.MAC)));
    }

    /** The control in the row titled {@code title}. */
    @SuppressWarnings("unchecked")
    private <T extends Node> T control(String title) throws Exception {
        return call(() -> {
            for (Node node : Objects.requireNonNull(stage.getScene()).getRoot().lookupAll(".info-row")) {
                HBox row = (HBox) node;
                Label name =
                        (Label) ((javafx.scene.layout.VBox) row.getChildren().get(0))
                                .getChildren()
                                .get(0);
                if (name.getText().equals(title)) return (T) row.getChildren().get(1);
            }
            throw new AssertionError("no row " + title);
        });
    }

    @Test
    void everyCheckBoxWritesItsSetting() throws Exception {
        assertTrue(call(stage::isShowing));
        record Box(String title, java.util.function.BooleanSupplier setting) {}
        for (Box box : java.util.List.of(
                new Box("Thumbnails at start", settings::startWithThumbnails),
                new Box("Thumbnails at the side", settings::thumbnailsAtSide),
                new Box("File names under thumbnails", settings::thumbnailCaptions),
                new Box("File names instead of pictures", settings::thumbnailNames),
                new Box("Properties at start", settings::startWithInfo),
                new Box("Status bar", settings::statusBar),
                new Box("Keep zoom when stepping", settings::keepZoom),
                new Box("Check for updates", settings::checkForUpdates))) {
            CheckBox check = control(box.title());
            boolean was = box.setting().getAsBoolean();
            assertEquals(was, call(check::isSelected), box.title() + " shows the setting");
            fx(check::fire);
            assertEquals(!was, box.setting().getAsBoolean(), box.title() + " writes the setting");
            fx(check::fire);
            assertEquals(was, box.setting().getAsBoolean());
        }
    }

    @Test
    void theChoicesAndSpinnersWriteTheirSettings() throws Exception {
        ChoiceBox<Settings.ThemeChoice> theme = control("Theme");
        fx(() -> theme.setValue(Settings.ThemeChoice.DARK));
        assertEquals(Settings.ThemeChoice.DARK, settings.theme());
        assertEquals("Dark", theme.getConverter().toString(Settings.ThemeChoice.DARK));
        assertEquals("Follow the system", theme.getConverter().toString(Settings.ThemeChoice.SYSTEM));
        assertEquals("Light", theme.getConverter().toString(Settings.ThemeChoice.LIGHT));
        assertEquals(Settings.ThemeChoice.SYSTEM, theme.getConverter().fromString("anything"));
        fx(() -> theme.setValue(Settings.ThemeChoice.SYSTEM));

        ChoiceBox<Sorting.By> sortBy = control("Sort by");
        fx(() -> sortBy.setValue(Sorting.By.DATE));
        assertEquals(Sorting.By.DATE, settings.sorting().by());
        assertEquals("Date modified", sortBy.getConverter().toString(Sorting.By.DATE));
        assertEquals("Size", sortBy.getConverter().toString(Sorting.By.SIZE));
        assertEquals(Sorting.By.NAME, sortBy.getConverter().fromString("anything"));
        CheckBox reversed = control("Reverse order");
        fx(reversed::fire);
        assertTrue(settings.sorting().reversed());

        Spinner<Integer> hide = control("Hide controls after");
        fx(hide::increment);
        assertEquals(Settings.MIN_HIDE_SECONDS + 2, settings.controlsHideSeconds(), "from the default, 2, one more");
        Spinner<Integer> slideshow = control("Slideshow speed");
        int seconds = settings.slideshowSeconds();
        fx(slideshow::increment);
        assertEquals(seconds + 1, settings.slideshowSeconds());
    }

    @Test
    void aSettingChangedElsewhereShowsInTheWindow() throws Exception {
        fx(() -> settings.setSorting(new Sorting(Sorting.By.SIZE, true)));
        assertEquals(
                Sorting.By.SIZE,
                call(() -> this.<ChoiceBox<Sorting.By>>control("Sort by").getValue()));
        assertTrue(call(() -> this.<CheckBox>control("Reverse order").isSelected()));
        fx(() -> settings.setKeepZoom(true));
        assertTrue(call(() -> this.<CheckBox>control("Keep zoom when stepping").isSelected()));
        fx(() -> settings.setStatusBar(false));
        assertFalse(call(() -> this.<CheckBox>control("Status bar").isSelected()));
    }

    @Test
    void aPaletteKeyIsAddedByPressingItAndTakenOffByPressingItAgain() throws Exception {
        javafx.scene.layout.VBox keys = control("Command palette");
        Label shown = (Label) keys.getChildren().get(0);
        HBox buttons = (HBox) keys.getChildren().get(1);
        Button add = (Button) buttons.getChildren().get(0);
        Button defaults = (Button) buttons.getChildren().get(1);
        String before = settings.paletteKeys();

        fx(add::fire);
        assertEquals("Press a key, or Esc", call(shown::getText));
        press(KeyCode.F2);
        assertEquals(before + " f2", settings.paletteKeys());
        assertTrue(call(shown::getText).contains("F2"));

        fx(add::fire);
        press(KeyCode.F2);
        assertEquals(before, settings.paletteKeys(), "pressed again, taken off");

        fx(add::fire);
        press(KeyCode.ESCAPE);
        assertEquals(before, settings.paletteKeys(), "Escape adds nothing");
        assertTrue(call(stage::isShowing), "and, recording, does not close the window");

        fx(add::fire);
        fx(stage::hide); // closed while recording: it stops
        assertFalse(call(shown::getText).startsWith("Press"));

        fx(() -> settings.setPaletteKeys("f3"));
        fx(defaults::fire);
        assertEquals(Settings.defaultPaletteKeys(Command.MAC), settings.paletteKeys());
    }

    @Test
    void everyControlSetInTheWindowSurvivesARestart() throws Exception {
        // Every control away from its default, through the window.
        fx(() -> this.<ChoiceBox<Settings.ThemeChoice>>control("Theme").setValue(Settings.ThemeChoice.DARK));
        for (String box : List.of(
                "Thumbnails at start",
                "Thumbnails at the side",
                "File names under thumbnails",
                "File names instead of pictures",
                "Properties at start",
                "Status bar",
                "Keep zoom when stepping",
                "Check for updates",
                "Reverse order")) {
            CheckBox check = control(box);
            fx(check::fire);
        }
        fx(() -> this.<ChoiceBox<Sorting.By>>control("Sort by").setValue(Sorting.By.SIZE));
        fx(() -> this.<Spinner<Integer>>control("Hide controls after").increment(3));
        fx(() -> this.<Spinner<Integer>>control("Slideshow speed").increment(4));
        fx(() -> settings.setPaletteKeys(settings.paletteKeys() + " f2"));
        Settings was = settings;

        // A restart: the file read again, and a new window over it.
        Settings again = new Settings(dir.resolve("settings.properties"));
        again.load();
        assertEquals(Settings.ThemeChoice.DARK, again.theme());
        assertEquals(!new Settings(dir.resolve("none.properties")).startWithThumbnails(), again.startWithThumbnails());
        assertEquals(was.thumbnailsAtSide(), again.thumbnailsAtSide());
        assertEquals(was.thumbnailCaptions(), again.thumbnailCaptions());
        assertEquals(was.thumbnailNames(), again.thumbnailNames());
        assertEquals(was.startWithInfo(), again.startWithInfo());
        assertEquals(was.statusBar(), again.statusBar());
        assertEquals(was.keepZoom(), again.keepZoom());
        assertEquals(was.checkForUpdates(), again.checkForUpdates());
        assertEquals(new Sorting(Sorting.By.SIZE, true), again.sorting());
        assertEquals(was.controlsHideSeconds(), again.controlsHideSeconds());
        assertEquals(was.slideshowSeconds(), again.slideshowSeconds());
        assertEquals(was.paletteKeys(), again.paletteKeys());

        settings = again;
        SettingsWindow reopened = call(() -> new SettingsWindow(again, ViewerWindow.stylesheet()));
        fx(() -> reopened.show(stage));
        stage = reopened.stage();
        assertEquals(
                Settings.ThemeChoice.DARK,
                call(() ->
                        this.<ChoiceBox<Settings.ThemeChoice>>control("Theme").getValue()));
        assertEquals(
                Sorting.By.SIZE,
                call(() -> this.<ChoiceBox<Sorting.By>>control("Sort by").getValue()));
        assertEquals(
                again.statusBar(),
                call(() -> this.<CheckBox>control("Status bar").isSelected()));
        assertEquals(
                again.slideshowSeconds(),
                call(() -> this.<Spinner<Integer>>control("Slideshow speed").getValue()));
        assertTrue(call(() -> this.<CheckBox>control("Reverse order").isSelected()));
    }

    @Test
    void escapeClosesTheWindowAndShowingItAgainBringsItForward() throws Exception {
        press(KeyCode.ESCAPE);
        assertFalse(call(stage::isShowing));
        Stage owner = call(Stage::new);
        fx(() -> window.show(owner));
        fx(() -> window.show(owner));
        assertTrue(call(stage::isShowing));
    }

    private void press(KeyCode code) throws Exception {
        fx(() -> {
            Scene scene = Objects.requireNonNull(stage.getScene());
            Node target = scene.getFocusOwner() != null ? scene.getFocusOwner() : scene.getRoot();
            Event.fireEvent(target, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false));
        });
    }
}
