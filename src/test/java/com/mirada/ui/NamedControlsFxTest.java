package com.mirada.ui;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Cell;
import javafx.scene.control.ComboBoxBase;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuBar;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Spinner;
import javafx.scene.control.SplitPane;
import javafx.scene.input.KeyCode;
import javafx.stage.Window;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.api.FxToolkit;

import static com.mirada.ui.FxTestSupport.fx;
import static com.mirada.ui.FxTestSupport.waitUntil;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ACC-01: every control a person can reach with Tab or the mouse has a name a screen reader says: its
 * own text, a {@code Label} whose {@code labelFor} is the control, or its accessible text. The test
 * opens every window and overlay Mirada has (the viewer with its sidebar and strip, Bookmarks,
 * Settings page by page, About, Pauta, Shortcuts, the Debug Log, the Message Log, Report a Problem,
 * the palette, a picker and the editor), walks each scene, and fails on a control or a row that reads
 * as nothing.
 */
@Tag("fx")
class NamedControlsFxTest {

    @TempDir
    Path dir;

    /** What holds controls but is no control a person uses: text, scrolling, separators, layout. */
    private static final Set<Class<?>> NOT_A_CONTROL = Set.of(
            Label.class,
            ScrollBar.class,
            ScrollPane.class,
            Separator.class,
            SplitPane.class,
            MenuBar.class,
            javafx.scene.control.ButtonBar.class);

    @AfterEach
    void cleanUp() throws Exception {
        FxToolkit.cleanupStages();
    }

    @Test
    void everyControlInEveryWindowHasAName() throws Exception {
        Path photos = WindowFixture.threeImages(dir.resolve("photos"));
        WindowFixture fixture = WindowFixture.open(dir.resolve("config"), photos);
        fixture.waitForImage("a.png");
        fixture.waitForFirstPaint();
        List<String> unnamed = new ArrayList<>();

        // The viewer with its sidebar, all its metadata, the strip and a bookmark's badge.
        fixture.run(Command.INFO);
        fixture.run(Command.ALL_METADATA);
        fixture.run(Command.THUMBNAILS);
        fixture.run(Command.BOOKMARK);
        sweep(fixture, unnamed, "viewer");

        // Overlays over the image, one at a time.
        fixture.run(Command.MESSAGE_LOG);
        sweep(fixture, unnamed, "message log");
        fixture.press(KeyCode.ESCAPE);
        fixture.run(Command.PALETTE);
        sweep(fixture, unnamed, "palette");
        fixture.press(KeyCode.ESCAPE);
        fixture.run(Command.GO_TO_IMAGE);
        sweep(fixture, unnamed, "picker");
        fixture.press(KeyCode.ESCAPE);

        // The editor takes the window's centre.
        fixture.run(Command.EDIT);
        waitUntil("the editor", () -> {
            Editor editor = fixture.field("editor");
            return editor != null && editor.isShowing();
        });
        sweep(fixture, unnamed, "editor");
        fixture.run(Command.EDITOR_CANCEL);

        // The windows of their own.
        for (Command command : List.of(
                Command.BOOKMARKS,
                Command.ABOUT,
                Command.PAUTA,
                Command.SHORTCUTS,
                Command.DEBUG_LOG,
                Command.REPORT_PROBLEM,
                Command.SETTINGS)) {
            fixture.run(command);
        }
        sweep(fixture, unnamed, "windows");
        assertTrue(FxTestSupport.call(() -> WindowFixture.others(fixture.stage).size()) >= 7, "the windows are up");

        // Settings, page by page.
        SettingsWindow settings = fixture.field("settingsWindow");
        ListView<?> pages = FxTestSupport.field(settings, "sidebar");
        int count = FxTestSupport.call(() -> pages.getItems().size());
        for (int i = 0; i < count; i++) {
            int page = i;
            fx(() -> pages.getSelectionModel().select(page));
            sweep(fixture, unnamed, "settings page " + page);
        }

        // The dialogs, built as they are asked and walked without showing them modally.
        fx(() -> {
            List<javafx.scene.control.Dialog<?>> dialogs = List.of(
                    Confirm.dialog(Confirm.discardEdit("a.png"), fixture.stage, null, true),
                    Confirm.dialog(
                            Confirm.importConfiguration("mirada.zip"),
                            fixture.stage,
                            new javafx.scene.layout.VBox(new javafx.scene.control.CheckBox("Settings")),
                            false),
                    Confirm.dialog(Confirm.resetPaletteKeys("F3"), fixture.stage, null, false),
                    ViewerWindow.renameDialog("a.png", fixture.stage),
                    NoteDialog.dialog(fixture.stage, "Note", "a.png", ""));
            for (javafx.scene.control.Dialog<?> dialog : dialogs) {
                javafx.scene.control.DialogPane pane = dialog.getDialogPane();
                pane.applyCss();
                pane.layout();
                walk(pane, labelled(pane), unnamed, "dialog " + dialog.getTitle());
            }
        });

        // The menus: the menu bar's, the image's, the Message Log's, the Debug Log's, the
        // Bookmarks window's rows and the zoom drop-down; each row reads as its title (ACC-01).
        fx(() -> {
            List<javafx.scene.control.MenuItem> rows = new ArrayList<>();
            TitleBar titleBar = FxTestSupport.field(fixture.window, "titleBar");
            MenuBar bar = titleBar.menuBar();
            if (bar != null) for (javafx.scene.control.Menu menu : bar.getMenus()) rows.add(menu);
            rows.addAll(((javafx.scene.control.ContextMenu)
                            FxTestSupport.invoke(fixture.window, "contextMenu", new Class<?>[0]))
                    .getItems());
            OnScreenControls controls = new OnScreenControls(new javafx.scene.layout.StackPane(), c -> {});
            rows.addAll(controls.zoomMenu().getItems());
            List<String> blank = new ArrayList<>();
            unread(rows, blank);
            unnamed.addAll(blank);
        });
        for (Command command : List.of(Command.MESSAGE_LOG, Command.DEBUG_LOG)) {
            fixture.run(command);
            FxTestSupport.drain();
        }
        fx(() -> {
            MessageLogView log = fixture.field("messageLog");
            ListView<?> list = (ListView<?>) fixture.stage.getScene().lookup(".message-log-list");
            DebugLogWindow debug = fixture.field("debugLog");
            List<String> blank = new ArrayList<>();
            // The Bookmarks window's row menu, from the keyboard on its selected row.
            for (Window window : List.copyOf(Window.getWindows())) {
                Scene scene = window.getScene();
                if (scene == null || !window.isShowing()) continue;
                if (scene.lookup(".bookmarks-list") instanceof ListView<?> rows
                        && !rows.getItems().isEmpty()) {
                    rows.getSelectionModel().select(rows.getItems().size() - 1);
                    rows.fireEvent(new javafx.scene.input.KeyEvent(
                            javafx.scene.input.KeyEvent.KEY_PRESSED, "", "", KeyCode.F10, true, false, false, false));
                    collectMenus(blank, "bookmark row");
                }
            }
            for (Node owner : List.of(list, debug.text())) {
                owner.fireEvent(new javafx.scene.input.ContextMenuEvent(
                        javafx.scene.input.ContextMenuEvent.CONTEXT_MENU_REQUESTED, 1, 1, 1, 1, false, null));
                collectMenus(blank, owner.getStyleClass().toString());
            }
            assertEquals(
                    3, blank.stream().filter(b -> b.startsWith("menu opened")).count(), blank.toString());
            blank.removeIf(b -> b.startsWith("menu opened"));
            unnamed.addAll(blank);
            log.hide();
        });

        fixture.close();
        assertEquals(List.of(), unnamed, "controls with no name");
    }

    /** The rows of the menus showing now that read as nothing; each menu is closed after. */
    private static void collectMenus(List<String> blank, String where) {
        for (Window window : List.copyOf(Window.getWindows())) {
            if (window instanceof javafx.scene.control.ContextMenu menu && menu.isShowing()) {
                blank.add("menu opened: " + where);
                unread(menu.getItems(), blank);
                menu.hide();
            }
        }
    }

    /**
     * The rows that read as nothing: a row of Mirada's own drawing ({@code CustomMenuItem}) reads its
     * content's accessible text, a stock row its text; a submenu's rows are walked too.
     */
    private static void unread(List<javafx.scene.control.MenuItem> rows, List<String> blank) {
        for (javafx.scene.control.MenuItem row : rows) {
            if (row instanceof javafx.scene.control.SeparatorMenuItem) continue;
            // A submenu Mirada draws reads as its graphic, as JavaFX reads a row's graphic before its text.
            String read = row instanceof javafx.scene.control.CustomMenuItem custom && custom.getContent() != null
                    ? custom.getContent().getAccessibleText()
                    : has(row.getText())
                            ? row.getText()
                            : row.getGraphic() == null ? null : row.getGraphic().getAccessibleText();
            if (!has(read))
                blank.add("menu row " + row.getClass().getSimpleName() + " " + row.getStyleClass() + " "
                        + row.getUserData() + " in "
                        + (row.getParentMenu() == null
                                ? "-"
                                : row.getParentMenu().getText()));
            if (row instanceof javafx.scene.control.Menu menu) unread(menu.getItems(), blank);
        }
    }

    /** Walks every showing window's scene. */
    private static void sweep(WindowFixture fixture, List<String> unnamed, String where) throws Exception {
        FxTestSupport.drain();
        fx(() -> {
            for (Window window : List.copyOf(Window.getWindows())) {
                Scene scene = window.getScene();
                if (scene == null || !window.isShowing()) continue;
                scene.getRoot().applyCss();
                scene.getRoot().layout();
                String name = where + " / " + window.getClass().getSimpleName()
                        + (window == fixture.stage ? " (viewer)" : "");
                walk(scene.getRoot(), labelled(scene.getRoot()), unnamed, name);
            }
        });
    }

    /** The controls some label in this scene names (its {@code labelFor}). */
    private static Set<Node> labelled(Parent root) {
        Set<Node> named = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Node node : root.lookupAll(".label")) {
            if (node instanceof Label label && label.getLabelFor() != null) named.add(label.getLabelFor());
        }
        return named;
    }

    private static void walk(Node node, Set<Node> labelled, List<String> unnamed, String where) {
        if (!node.isVisible()) return;
        if (node instanceof Control control
                && NOT_A_CONTROL.stream().noneMatch(c -> c.isInstance(control))
                && !control.isDisabled()
                && !named(control, labelled)) {
            String entry = describe(control) + " in " + where;
            if (unnamed.stream().noneMatch(seen -> seen.startsWith(describe(control) + " in "))) unnamed.add(entry);
        }
        // A combo box's or a spinner's own parts are its skin's: the box carries the name.
        if (node instanceof Parent parent && !(node instanceof ComboBoxBase<?>) && !(node instanceof Spinner<?>)) {
            for (Node child : parent.getChildrenUnmodifiable()) walk(child, labelled, unnamed, where);
        }
    }

    private static boolean named(Control control, Set<Node> labelled) {
        if (has(control.getAccessibleText()) || labelled.contains(control)) return true;
        if (control instanceof Cell<?> cell) {
            // An empty cell is no row; a filled one reads its text or its graphic's.
            if (cell.isEmpty()) return true;
            if (has(cell.getText())) return true;
            Node graphic = cell.getGraphic();
            return graphic != null && has(graphic.getAccessibleText());
        }
        return control instanceof Labeled labeled && has(labeled.getText());
    }

    private static boolean has(String text) {
        return text != null && !text.isBlank();
    }

    private static String describe(Control control) {
        StringBuilder path = new StringBuilder();
        for (Parent p = control.getParent(); p != null && path.length() < 160; p = p.getParent()) {
            path.append(" < ").append(p.getStyleClass());
        }
        return control.getClass().getSimpleName() + " " + control.getStyleClass() + path;
    }
}
