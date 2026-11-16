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
    private static final Set<Class<?>> NOT_A_CONTROL =
            Set.of(Label.class, ScrollBar.class, ScrollPane.class, Separator.class, SplitPane.class, MenuBar.class);

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

        fixture.close();
        assertEquals(List.of(), unnamed, "controls with no name");
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
