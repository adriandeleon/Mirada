package com.mirada.ui;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.StringConverter;

import com.mirada.folder.Sorting;
import com.mirada.settings.Settings;
import com.mirada.ui.SettingsPages.Page;
import com.mirada.ui.SettingsPages.Row;
import org.jspecify.annotations.Nullable;

/**
 * Settings (SET-02, SET-03, Pauta 4.0.1): a search field on top, a sidebar of pages with icons on
 * the left, the page on the right. The search hides every row and card that does not match and opens
 * the page with the best match; a page with none is greyed in the sidebar. What the pages hold and
 * how a search ranks is {@link SettingsPages}'s.
 *
 * <p>Termina's way otherwise: every control writes through at once, so there is no OK button, and
 * each row says what it does. A setting changed elsewhere (a command, the menu) shows here at once.
 * Built the first time it is opened, never before the first paint.
 */
final class SettingsWindow {

    static final double WIDTH = 780;
    static final double HEIGHT = 560;

    private final Stage stage = new Stage();
    private final Settings settings;
    private final TextField search = new TextField();
    private final ListView<Page> sidebar = new ListView<>();
    private final ScrollPane scroll = new ScrollPane();
    private final Map<Page, VBox> pages = new EnumMap<>(Page.class);
    /** Each row's node, by row; and each card, by page and heading. */
    private final Map<Row, HBox> rowNodes = new HashMap<>();

    private final Map<Row, String> visibleText = new HashMap<>();
    private final List<VBox> cards = new ArrayList<>();
    private SettingsPages.Found found = SettingsPages.search("", Map.of());

    /** Takes the next key pressed while "Add a key…" waits for one; null otherwise. */
    private @Nullable EventHandler<KeyEvent> recording;

    private final Label paletteKeys = new Label();
    private final Button addKey = new Button("Add a key…");

    SettingsWindow(Settings settings, String stylesheet, Consumer<Command> run) {
        this.settings = settings;
        stage.setTitle("Settings");
        WindowIcon.applyTo(stage);

        ChoiceBox<Settings.ThemeChoice> theme = new ChoiceBox<>();
        theme.getItems().addAll(Settings.ThemeChoice.values());
        theme.setConverter(converter(SettingsWindow::themeName, Settings.ThemeChoice.SYSTEM));
        theme.setValue(settings.theme());
        theme.valueProperty().addListener((o, was, now) -> {
            if (now != null && now != settings.theme()) settings.setTheme(now);
        });

        CheckBox thumbnails = checkBox(
                settings.startWithThumbnails(), settings::startWithThumbnails, settings::setStartWithThumbnails);
        CheckBox info = checkBox(settings.startWithInfo(), settings::startWithInfo, settings::setStartWithInfo);
        CheckBox updates =
                checkBox(settings.checkForUpdates(), settings::checkForUpdates, settings::setCheckForUpdates);
        CheckBox statusBar = checkBox(settings.statusBar(), settings::statusBar, settings::setStatusBar);
        CheckBox keepZoom = checkBox(settings.keepZoom(), settings::keepZoom, settings::setKeepZoom);
        CheckBox atSide =
                checkBox(settings.thumbnailsAtSide(), settings::thumbnailsAtSide, settings::setThumbnailsAtSide);
        CheckBox captions =
                checkBox(settings.thumbnailCaptions(), settings::thumbnailCaptions, settings::setThumbnailCaptions);
        CheckBox names = checkBox(settings.thumbnailNames(), settings::thumbnailNames, settings::setThumbnailNames);

        Spinner<Integer> hide =
                new Spinner<>(Settings.MIN_HIDE_SECONDS, Settings.MAX_HIDE_SECONDS, settings.controlsHideSeconds());
        hide.setPrefWidth(110);
        hide.valueProperty().addListener((o, was, now) -> {
            if (now != null && now != settings.controlsHideSeconds()) settings.setControlsHideSeconds(now);
        });

        ChoiceBox<Sorting.By> sortBy = new ChoiceBox<>();
        sortBy.getItems().addAll(Sorting.By.values());
        sortBy.setConverter(converter(SettingsWindow::sortName, Sorting.By.NAME));
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

        Spinner<Integer> slideshow = new Spinner<>(
                Settings.MIN_SLIDESHOW_SECONDS, Settings.MAX_SLIDESHOW_SECONDS, settings.slideshowSeconds());
        slideshow.setPrefWidth(110);
        slideshow.valueProperty().addListener((o, was, now) -> {
            if (now != null && now != settings.slideshowSeconds()) settings.setSlideshowSeconds(now);
        });

        // The bundled keymaps by name; one the file names that is not bundled shows as the default.
        ChoiceBox<String> keymap = new ChoiceBox<>();
        keymap.getItems().addAll(Keymap.PRESETS);
        keymap.setConverter(converter(id -> Keymap.load(id, Command.MAC).name(), Keymap.DEFAULT));
        keymap.setValue(Keymap.resolve(settings.keymap()));
        keymap.valueProperty().addListener((o, was, now) -> {
            if (now != null && !now.equals(Keymap.resolve(settings.keymap()))) settings.setKeymap(now);
        });

        paletteKeys.setText(shownKeys(settings));
        paletteKeys.getStyleClass().add("shortcut-key");
        Button defaultKeys = new Button("Default");
        defaultKeys.setOnAction(e -> settings.setPaletteKeys(Settings.defaultPaletteKeys(Command.MAC)));
        addKey.setOnAction(e -> recordKey());
        HBox keyButtons = new HBox(6, addKey, defaultKeys);
        keyButtons.setAlignment(Pos.CENTER_RIGHT);
        VBox paletteControl = new VBox(6, paletteKeys, keyButtons);
        paletteControl.setAlignment(Pos.CENTER_RIGHT);

        Label folder = new Label(String.valueOf(settings.file().toAbsolutePath().getParent()));
        folder.getStyleClass().add("info-label");
        Button export = new Button("Export…");
        export.setOnAction(e -> run.accept(Command.EXPORT_CONFIG));
        Button importButton = new Button("Import…");
        importButton.setOnAction(e -> run.accept(Command.IMPORT_CONFIG));
        HBox backupButtons = new HBox(6, export, importButton);
        backupButtons.setAlignment(Pos.CENTER_RIGHT);
        VBox backup = new VBox(6, folder, backupButtons);
        backup.setAlignment(Pos.CENTER_RIGHT);

        Map<Command, Node> controls = Map.ofEntries(
                Map.entry(Command.THEME, theme),
                Map.entry(Command.THUMBNAILS_AT_START, thumbnails),
                Map.entry(Command.PROPERTIES_AT_START, info),
                Map.entry(Command.CHECK_DAILY, updates),
                Map.entry(Command.STATUS_BAR, statusBar),
                Map.entry(Command.HIDE_CONTROLS_AFTER, hide),
                Map.entry(Command.KEEP_ZOOM, keepZoom),
                Map.entry(Command.THUMBNAILS_AT_SIDE, atSide),
                Map.entry(Command.THUMBNAIL_CAPTIONS, captions),
                Map.entry(Command.THUMBNAIL_NAMES, names),
                Map.entry(Command.SORT_BY_NAME, sortBy),
                Map.entry(Command.REVERSE_ORDER, reversed),
                Map.entry(Command.SLIDESHOW_SPEED, slideshow),
                Map.entry(Command.KEYMAP, keymap),
                Map.entry(Command.PALETTE_KEYS, paletteControl),
                Map.entry(Command.EXPORT_CONFIG, backup));

        // The same settings change from the palette, the keyboard and the menus; follow them.
        settings.addListener(() -> {
            if (recording == null) paletteKeys.setText(shownKeys(settings));
            theme.setValue(settings.theme());
            thumbnails.setSelected(settings.startWithThumbnails());
            info.setSelected(settings.startWithInfo());
            updates.setSelected(settings.checkForUpdates());
            hide.getValueFactory().setValue(settings.controlsHideSeconds());
            slideshow.getValueFactory().setValue(settings.slideshowSeconds());
            sortBy.setValue(settings.sorting().by());
            reversed.setSelected(settings.sorting().reversed());
            keepZoom.setSelected(settings.keepZoom());
            atSide.setSelected(settings.thumbnailsAtSide());
            names.setSelected(settings.thumbnailNames());
            captions.setSelected(settings.thumbnailCaptions());
            statusBar.setSelected(settings.statusBar());
            keymap.setValue(Keymap.resolve(settings.keymap()));
        });

        Map<Page, Map<String, List<Row>>> layout = SettingsPages.cards();
        VBox content = new VBox();
        for (Page page : Page.values()) {
            Label title = new Label(page.title());
            title.getStyleClass().add("settings-page-title");
            VBox body = new VBox(18, title);
            body.getStyleClass().add("settings-page");
            body.setPadding(new Insets(18, 22, 22, 22));
            for (Map.Entry<String, List<Row>> card :
                    layout.getOrDefault(page, Map.of()).entrySet()) {
                List<Node> rows = new ArrayList<>();
                for (Row row : card.getValue()) {
                    Node control = Objects.requireNonNull(controls.get(row.command()), row.title());
                    HBox node = row(row.title(), row.description(), control);
                    rowNodes.put(row, node);
                    visibleText.put(row, visibleText(control));
                    rows.add(node);
                }
                body.getChildren().add(group(card.getKey(), rows));
            }
            pages.put(page, body);
            content.getChildren().add(body);
        }
        scroll.setContent(content);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("settings-scroll");

        sidebar.getItems().setAll(Page.values());
        sidebar.setCellFactory(list -> new PageCell());
        sidebar.getStyleClass().add("settings-sidebar");
        sidebar.setPrefWidth(190);
        sidebar.getSelectionModel().selectedItemProperty().addListener((o, was, page) -> {
            if (page != null) showPage(page);
        });

        search.setPromptText("Search settings");
        search.getStyleClass().add("settings-search");
        search.setAccessibleText("Search settings");
        search.textProperty().addListener((o, was, query) -> applySearch(query));
        HBox top = new HBox(search);
        HBox.setHgrow(search, Priority.ALWAYS);
        top.setPadding(new Insets(12, 14, 10, 14));
        top.getStyleClass().add("settings-top");

        BorderPane root = new BorderPane(scroll, top, null, null, sidebar);
        root.getStyleClass().add("settings");
        Scene scene = new Scene(root, WIDTH, HEIGHT);
        scene.getStylesheets().add(stylesheet);
        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (recording != null) return;
            if (e.getCode() == KeyCode.F && e.isShortcutDown()) {
                search.requestFocus();
                search.selectAll();
                e.consume();
            }
        });
        scene.setOnKeyPressed(e -> {
            if (e.getCode() != KeyCode.ESCAPE) return;
            // Escape empties the search first, then closes.
            if (!search.getText().isEmpty()) search.clear();
            else stage.hide();
        });
        stage.setScene(scene);
        stage.setOnHidden(e -> {
            if (recording != null) {
                stopRecording();
                paletteKeys.setText(shownKeys(settings));
            }
        });
        sidebar.getSelectionModel().select(Page.GENERAL);
    }

    /** Shows the window beside {@code from} on the page it was last on, or brings it forward. */
    void show(Window from) {
        if (stage.isShowing()) {
            stage.toFront();
            return;
        }
        stage.setX(from.getX() + Math.max(0, (from.getWidth() - WIDTH) / 2));
        stage.setY(from.getY() + 60);
        stage.show();
        search.requestFocus();
    }

    /** Shows the window on {@code page}, the search emptied. */
    void show(Window from, Page page) {
        search.clear();
        sidebar.getSelectionModel().select(page);
        show(from);
    }

    /** The palette's Command Palette Keys…: the Keys page, waiting for the key to add or take off. */
    void recordPaletteKey(Window from) {
        show(from, Page.KEYS);
        recordKey();
    }

    private void showPage(Page shown) {
        for (Map.Entry<Page, VBox> page : pages.entrySet()) {
            boolean on = page.getKey() == shown;
            page.getValue().setVisible(on);
            page.getValue().setManaged(on);
        }
        scroll.setVvalue(0);
    }

    /** Hides every row and card the query does not match, and opens the page with the best match. */
    private void applySearch(String query) {
        found = SettingsPages.search(query, visibleText);
        rowNodes.forEach((row, node) -> {
            boolean on = found.rows().contains(row);
            node.setVisible(on);
            node.setManaged(on);
        });
        for (VBox card : cards) {
            VBox list = (VBox) card.getChildren().get(1);
            boolean first = true;
            for (Node row : list.getChildren()) {
                row.getStyleClass().remove("info-row-after");
                if (!row.isVisible()) continue;
                if (!first) row.getStyleClass().add("info-row-after");
                first = false;
            }
            card.setVisible(!first);
            card.setManaged(!first);
        }
        Page best = found.best();
        if (best != null) sidebar.getSelectionModel().select(best);
        sidebar.refresh();
    }

    private void recordKey() {
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
    }

    private void stopRecording() {
        EventHandler<KeyEvent> take = recording;
        if (take != null) stage.getScene().removeEventFilter(KeyEvent.KEY_PRESSED, take);
        recording = null;
    }

    /** The palette's keys as the platform writes keys. */
    private static String shownKeys(Settings settings) {
        return PaletteChords.display(PaletteChords.parse(settings.paletteKeys(), Command.MAC));
    }

    static String themeName(Settings.ThemeChoice choice) {
        return switch (choice) {
            case SYSTEM -> "Follow the system";
            case LIGHT -> "Light";
            case DARK -> "Dark";
        };
    }

    static String sortName(Sorting.By by) {
        return switch (by) {
            case NAME -> "Name";
            case DATE -> "Date modified";
            case SIZE -> "Size";
        };
    }

    private static <T> StringConverter<T> converter(java.util.function.Function<T, String> name, T fallback) {
        return new StringConverter<>() {
            @Override
            public String toString(@Nullable T value) {
                return value == null ? "" : name.apply(value);
            }

            @Override
            public T fromString(String text) {
                return fallback;
            }
        };
    }

    private static CheckBox checkBox(boolean value, java.util.function.BooleanSupplier current, Consumer<Boolean> set) {
        CheckBox box = new CheckBox();
        box.setSelected(value);
        box.selectedProperty().addListener((o, was, now) -> {
            if (now != current.getAsBoolean()) set.accept(now);
        });
        return box;
    }

    /** The text a row's control shows (button and label text), so a search finds the row by it. */
    static String visibleText(Node control) {
        StringBuilder text = new StringBuilder();
        if (control instanceof Labeled labeled && labeled.getText() != null)
            text.append(labeled.getText()).append(' ');
        if (control instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) text.append(visibleText(child));
        }
        return text.toString();
    }

    private VBox group(String title, List<Node> rows) {
        Label heading = new Label(title);
        heading.getStyleClass().add("settings-group");
        VBox list = new VBox();
        list.getChildren().addAll(rows);
        list.getStyleClass().add("info-card");
        for (int i = 1; i < rows.size(); i++) rows.get(i).getStyleClass().add("info-row-after");
        VBox card = new VBox(8, heading, list);
        cards.add(card);
        return card;
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

    // ---- for tests ----

    TextField search() {
        return search;
    }

    ListView<Page> sidebar() {
        return sidebar;
    }

    /** The page showing. */
    @Nullable
    Page page() {
        return sidebar.getSelectionModel().getSelectedItem();
    }

    /** Whether a search found nothing on {@code page}, which the sidebar greys. */
    boolean greyed(Page page) {
        return !search.getText().isBlank() && !found.pages().contains(page);
    }

    /** A sidebar row: the page's icon and name, greyed when a search finds nothing on it. */
    private final class PageCell extends ListCell<Page> {
        @Override
        protected void updateItem(@Nullable Page page, boolean empty) {
            super.updateItem(page, empty);
            getStyleClass().remove("no-match");
            if (empty || page == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            setText(page.title());
            setGraphic(Icons.of(page.icon()));
            if (greyed(page)) getStyleClass().add("no-match");
        }
    }
}
