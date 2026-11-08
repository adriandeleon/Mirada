package com.mirada.ui;

import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
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
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.StringConverter;

import com.mirada.folder.Sorting;
import com.mirada.settings.Settings;
import org.jspecify.annotations.Nullable;

/**
 * Settings, Termina's way: every control writes through at once, so there is no OK button, and each
 * row says what it does — including the two that take effect at the next start. Built the first time
 * it is opened.
 */
final class SettingsWindow {

    private final Stage stage = new Stage();

    /** Takes the next key pressed while "Add a key…" waits for one; null otherwise. */
    private @Nullable EventHandler<KeyEvent> recording;

    SettingsWindow(Settings settings, String stylesheet) {
        stage.setTitle("Settings");
        stage.setResizable(false);
        WindowIcon.applyTo(stage);

        ChoiceBox<Settings.ThemeChoice> theme = new ChoiceBox<>();
        theme.getItems().addAll(Settings.ThemeChoice.values());
        theme.setConverter(new StringConverter<>() {
            @Override
            public String toString(Settings.ThemeChoice choice) {
                return choice == null
                        ? ""
                        : switch (choice) {
                            case SYSTEM -> "Follow the system";
                            case LIGHT -> "Light";
                            case DARK -> "Dark";
                        };
            }

            @Override
            public Settings.ThemeChoice fromString(String text) {
                return Settings.ThemeChoice.SYSTEM;
            }
        });
        theme.setValue(settings.theme());
        theme.valueProperty().addListener((o, was, now) -> {
            if (now != null) settings.setTheme(now);
        });

        CheckBox thumbnails = new CheckBox();
        thumbnails.setSelected(settings.startWithThumbnails());
        thumbnails.selectedProperty().addListener((o, was, now) -> settings.setStartWithThumbnails(now));

        CheckBox info = new CheckBox();
        info.setSelected(settings.startWithInfo());
        info.selectedProperty().addListener((o, was, now) -> settings.setStartWithInfo(now));

        Spinner<Integer> hide =
                new Spinner<>(Settings.MIN_HIDE_SECONDS, Settings.MAX_HIDE_SECONDS, settings.controlsHideSeconds());
        hide.setPrefWidth(110);
        hide.valueProperty().addListener((o, was, now) -> {
            if (now != null) settings.setControlsHideSeconds(now);
        });

        ChoiceBox<Sorting.By> sortBy = new ChoiceBox<>();
        sortBy.getItems().addAll(Sorting.By.values());
        sortBy.setConverter(new StringConverter<>() {
            @Override
            public String toString(Sorting.By by) {
                return by == null
                        ? ""
                        : switch (by) {
                            case NAME -> "Name";
                            case DATE -> "Date modified";
                            case SIZE -> "Size";
                        };
            }

            @Override
            public Sorting.By fromString(String text) {
                return Sorting.By.NAME;
            }
        });
        sortBy.setValue(settings.sorting().by());
        sortBy.valueProperty().addListener((o, was, now) -> {
            if (now != null && now != settings.sorting().by())
                settings.setSorting(settings.sorting().by(now));
        });

        CheckBox reversed = new CheckBox();
        reversed.setSelected(settings.sorting().reversed());
        reversed.selectedProperty().addListener((o, was, now) -> {
            if (now != settings.sorting().reversed())
                settings.setSorting(settings.sorting().reverse());
        });

        CheckBox atSide = new CheckBox();
        atSide.setSelected(settings.thumbnailsAtSide());
        atSide.selectedProperty().addListener((o, was, now) -> {
            if (now != settings.thumbnailsAtSide()) settings.setThumbnailsAtSide(now);
        });
        CheckBox captions = new CheckBox();
        captions.setSelected(settings.thumbnailCaptions());
        captions.selectedProperty().addListener((o, was, now) -> {
            if (now != settings.thumbnailCaptions()) settings.setThumbnailCaptions(now);
        });
        CheckBox names = new CheckBox();
        names.setSelected(settings.thumbnailNames());
        names.selectedProperty().addListener((o, was, now) -> {
            if (now != settings.thumbnailNames()) settings.setThumbnailNames(now);
        });

        CheckBox statusBar = new CheckBox();
        statusBar.setSelected(settings.statusBar());
        statusBar.selectedProperty().addListener((o, was, now) -> {
            if (now != settings.statusBar()) settings.setStatusBar(now);
        });

        CheckBox updates = new CheckBox();
        updates.setSelected(settings.checkForUpdates());
        updates.selectedProperty().addListener((o, was, now) -> settings.setCheckForUpdates(now));

        CheckBox keepZoom = new CheckBox();
        keepZoom.setSelected(settings.keepZoom());
        keepZoom.selectedProperty().addListener((o, was, now) -> settings.setKeepZoom(now));

        Spinner<Integer> slideshow = new Spinner<>(
                Settings.MIN_SLIDESHOW_SECONDS, Settings.MAX_SLIDESHOW_SECONDS, settings.slideshowSeconds());
        slideshow.setPrefWidth(110);
        slideshow.valueProperty().addListener((o, was, now) -> {
            if (now != null) settings.setSlideshowSeconds(now);
        });

        Label paletteKeys = new Label(shownKeys(settings));
        paletteKeys.getStyleClass().add("shortcut-key");
        Button addKey = new Button("Add a key…");
        Button defaultKeys = new Button("Default");
        defaultKeys.setOnAction(e -> settings.setPaletteKeys(Settings.defaultPaletteKeys(Command.MAC)));
        addKey.setOnAction(e -> {
            if (recording != null) return;
            paletteKeys.setText("Press a key, or Esc");
            EventHandler<KeyEvent> take = key -> {
                String token = KeyChord.of(key);
                if (token == null) return; // Ctrl or Shift on its own: wait for the key it goes with
                key.consume();
                stopRecording();
                if (!token.equals("escape")) {
                    settings.setPaletteKeys(PaletteChords.toggled(settings.paletteKeys(), token));
                }
                paletteKeys.setText(shownKeys(settings));
            };
            recording = take;
            stage.getScene().addEventFilter(KeyEvent.KEY_PRESSED, take);
        });
        HBox keyButtons = new HBox(6, addKey, defaultKeys);
        keyButtons.setAlignment(Pos.CENTER_RIGHT);
        VBox paletteControl = new VBox(6, paletteKeys, keyButtons);
        paletteControl.setAlignment(Pos.CENTER_RIGHT);

        // The same settings change from the keyboard and the context menu; follow them.
        settings.addListener(() -> {
            if (recording == null) paletteKeys.setText(shownKeys(settings));
            sortBy.setValue(settings.sorting().by());
            reversed.setSelected(settings.sorting().reversed());
            keepZoom.setSelected(settings.keepZoom());
            atSide.setSelected(settings.thumbnailsAtSide());
            names.setSelected(settings.thumbnailNames());
            captions.setSelected(settings.thumbnailCaptions());
            statusBar.setSelected(settings.statusBar());
        });

        VBox root = new VBox(
                18,
                group("Appearance", row("Theme", "Light or dark controls and backgrounds.", theme)),
                group(
                        "Viewer",
                        row(
                                "Thumbnails at start",
                                "Opens the thumbnail strip when Mirada starts. T toggles it.",
                                thumbnails),
                        row(
                                "Thumbnails at the side",
                                "A column down the left of the window instead of a row along the bottom.",
                                atSide),
                        row(
                                "File names under thumbnails",
                                "Each thumbnail has its file name written under it.",
                                captions),
                        row(
                                "File names instead of pictures",
                                "The strip lists the folder by name, and loads no thumbnails.",
                                names),
                        row(
                                "Properties at start",
                                "Opens the properties sidebar when Mirada starts. I toggles it.",
                                info),
                        row(
                                "Status bar",
                                "A line along the bottom saying what the last command did; a click on it"
                                        + " lists every message.",
                                statusBar),
                        row(
                                "Hide controls after",
                                "Seconds without moving the pointer before the floating controls fade.",
                                hide),
                        row(
                                "Keep zoom when stepping",
                                "Moving to another image keeps the zoom and position, to compare the same"
                                        + " detail across shots. Z toggles it.",
                                keepZoom)),
                group(
                        "Folder",
                        row("Sort by", "The order images are stepped through.", sortBy),
                        row("Reverse order", "Last first: newest, largest, or Z to A.", reversed),
                        row("Slideshow speed", "Seconds each image shows in a slideshow. F5 starts it.", slideshow)),
                group(
                        "Updates",
                        row(
                                "Check for updates",
                                "Once a day, a few seconds after start, asks GitHub whether a newer Mirada"
                                        + " is out, and says so in the status bar. Nothing is downloaded.",
                                updates)),
                group(
                        "Keys",
                        row(
                                "Command palette",
                                "The keys that open the palette. Add a key, then press it; pressing one"
                                        + " already listed takes it off.",
                                paletteControl)));
        root.setPadding(new Insets(20));
        root.getStyleClass().add("settings");
        root.setPrefWidth(540);
        Scene scene = new Scene(root);
        scene.getStylesheets().add(stylesheet);
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) stage.hide();
        });
        stage.setScene(scene);
        stage.setOnHidden(e -> {
            if (recording != null) {
                stopRecording();
                paletteKeys.setText(shownKeys(settings));
            }
        });
    }

    /** The palette's keys as the platform writes keys. */
    private static String shownKeys(Settings settings) {
        return PaletteChords.display(PaletteChords.parse(settings.paletteKeys(), Command.MAC));
    }

    private void stopRecording() {
        EventHandler<KeyEvent> take = recording;
        if (take != null) stage.getScene().removeEventFilter(KeyEvent.KEY_PRESSED, take);
        recording = null;
    }

    void show(Window from) {
        if (stage.isShowing()) {
            stage.toFront();
            return;
        }
        stage.setX(from.getX() + Math.max(0, (from.getWidth() - 520) / 2));
        stage.setY(from.getY() + 80);
        stage.show();
    }

    private static VBox group(String title, Node... rows) {
        Label heading = new Label(title);
        heading.getStyleClass().add("settings-group");
        VBox card = new VBox(rows);
        card.getStyleClass().add("info-card");
        for (int i = 1; i < rows.length; i++) rows[i].getStyleClass().add("info-row-after");
        return new VBox(8, heading, card);
    }

    private static HBox row(String name, String description, Node control) {
        Label label = new Label(name);
        label.getStyleClass().add("info-value");
        Label detail = new Label(description);
        detail.getStyleClass().add("info-label");
        detail.setWrapText(true);
        // A wrapping label is laid out at its one-line height unless told otherwise, and its column
        // has to be allowed to shrink for the wrap to happen: without both, the text ends in "...".
        detail.setMinHeight(Region.USE_PREF_SIZE);
        VBox words = new VBox(2, label, detail);
        words.setMinWidth(0);
        HBox.setHgrow(words, Priority.ALWAYS);
        // The control keeps its own width; the words give way.
        if (control instanceof Region region) region.setMinWidth(Region.USE_PREF_SIZE);
        HBox row = new HBox(12, words, control);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("info-row");
        return row;
    }

    /** Its stage, closed with the viewer's. */
    Stage stage() {
        return stage;
    }
}
