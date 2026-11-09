package com.mirada.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;

import com.mirada.settings.Bookmarks;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.api.FxToolkit;

import static com.mirada.ui.FxTestSupport.call;
import static com.mirada.ui.FxTestSupport.fx;
import static com.mirada.ui.FxTestSupport.waitUntil;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Bookmarks and Notes window, driven from its filter field as a user does: typing filters,
 * the arrows move, Return opens, F2 edits the note, Ctrl+B toggles the bookmark, Ctrl+Delete removes
 * (asking first when a note would go with it), and the right-click menu does the same.
 */
@Tag("fx")
class BookmarksWindowTest {

    /** With the shortcut key, removes a mark: Delete, or a Mac's Backspace. */
    private static final KeyCode REMOVE = Command.MAC ? KeyCode.BACK_SPACE : KeyCode.DELETE;

    @TempDir
    Path dir;

    private Bookmarks bookmarks = new Bookmarks(Path.of("unset"));
    private BookmarksWindow window = null;
    private Stage stage = null;
    private final List<String> opened = new ArrayList<>();
    private final List<Path> revealed = new ArrayList<>();
    private Path photos = Path.of("unset");

    @BeforeEach
    void open() throws Exception {
        FxTestSupport.start();
        photos = WindowFixture.threeImages(dir.resolve("photos"));
        bookmarks = new Bookmarks(dir.resolve("bookmarks.properties"));
        bookmarks.toggle(photos.resolve("a.png").toAbsolutePath(), false);
        bookmarks.setNote(photos.resolve("b.png").toAbsolutePath(), false, "The lighthouse at dusk");
        bookmarks.toggle(photos.toAbsolutePath(), true);
        bookmarks.toggle(dir.resolve("gone.png").toAbsolutePath(), false);
        window = call(() -> new BookmarksWindow(
                bookmarks,
                (path, folder) -> opened.add((folder ? "folder " : "") + path.getFileName()),
                revealed::add,
                command -> {},
                command -> null,
                ViewerWindow.stylesheet()));
        Stage owner = call(Stage::new);
        fx(() -> window.show(owner));
        stage = window.stage();
        lookedFor();
    }

    /**
     * Types {@code text} into the filter as a user would, then makes the list at once instead of after
     * the filter's pause (PERF-06), which the tests would otherwise have to wait out.
     */
    private void filterBy(String text) throws Exception {
        fx(() -> {
            filter().setText(text);
            FxTestSupport.<FxThrottle>field(window, "filtering").flush();
        });
    }

    /** Waits for the window to have looked for every mark's file, off the FX thread. */
    private void lookedFor() throws Exception {
        waitUntil(
                "the marks looked for",
                () -> !FxTestSupport.<LatestOnly>field(window, "looking").pending());
    }

    @AfterEach
    void close() throws Exception {
        fx(window::close);
        FxToolkit.cleanupStages();
    }

    private ListView<?> list() {
        return FxTestSupport.field(window, "list");
    }

    private TextField filter() {
        return FxTestSupport.field(window, "filter");
    }

    private String selectedName() throws Exception {
        return call(() -> {
            Object row = list().getSelectionModel().getSelectedItem();
            if (row == null) return "";
            Path path = (Path) row.getClass().getMethod("path").invoke(row);
            return String.valueOf(path.getFileName());
        });
    }

    private void press(KeyCode code, boolean shortcut) throws Exception {
        fx(() -> {
            Scene scene = Objects.requireNonNull(stage.getScene());
            Node target = scene.getFocusOwner() != null ? scene.getFocusOwner() : scene.getRoot();
            boolean mac = Command.MAC;
            Event.fireEvent(
                    target,
                    new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, shortcut && !mac, false, shortcut && mac));
        });
    }

    private void press(KeyCode code) throws Exception {
        press(code, false);
    }

    private String count() throws Exception {
        return call(() -> ((javafx.scene.control.Label) FxTestSupport.field(window, "count")).getText());
    }

    @Test
    void theListShowsEveryMarkAndTheFilterFindsAWordOfANote() throws Exception {
        assertTrue(call(stage::isShowing));
        assertEquals("3 images, 1 folder", count(), "the gone file is listed too, marked missing");
        filterBy("lighthouse");
        assertEquals("1 image", count());
        assertEquals("photos", selectedName(), "its folder heads it");
        press(KeyCode.DOWN);
        assertEquals("b.png", selectedName());
        press(KeyCode.ENTER);
        assertEquals(List.of("b.png"), opened);
        press(KeyCode.ESCAPE);
        assertEquals("", call(() -> filter().getText()), "Escape clears the filter first");
        assertTrue(call(stage::isShowing));
        filterBy("nothing like this");
        assertEquals("0 images", count());
        assertEquals(
                "Nothing matches.",
                call(() -> ((javafx.scene.control.Label) FxTestSupport.field(window, "empty")).getText()));
        press(KeyCode.ESCAPE);
        press(KeyCode.ESCAPE);
        assertFalse(call(stage::isShowing), "then closes the window");
    }

    @Test
    void theArrowsMoveAndTheKindsNarrowTheList() throws Exception {
        String first = selectedName();
        press(KeyCode.DOWN);
        assertFalse(first.equals(selectedName()), "down moves");
        press(KeyCode.UP);
        assertEquals(first, selectedName());
        press(KeyCode.PAGE_DOWN);
        press(KeyCode.PAGE_UP);
        press(KeyCode.N, false);
        fx(() -> {
            Scene scene = Objects.requireNonNull(stage.getScene());
            Event.fireEvent(
                    scene.getRoot(), new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.P, false, true, false, false));
            Event.fireEvent(
                    scene.getRoot(), new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.N, false, true, false, false));
        });
        press(KeyCode.DIGIT3, true);
        assertEquals("1 image", count(), "notes only");
        press(KeyCode.DIGIT2, true);
        assertEquals("2 images, 1 folder", count(), "bookmarks only");
        // A click on the kind already chosen leaves it chosen.
        ToggleButton chosen = call(() -> (ToggleButton)
                ((javafx.scene.control.ToggleGroup) FxTestSupport.field(window, "showing")).getSelectedToggle());
        fx(chosen::fire);
        assertEquals("2 images, 1 folder", count());
        press(KeyCode.DIGIT1, true);
        assertEquals("3 images, 1 folder", count());
        press(KeyCode.F, true);
        press(KeyCode.W, true);
        assertFalse(call(stage::isShowing), "Ctrl+W closes");
    }

    @Test
    void aBookmarkIsToggledANoteEditedAndAMarkRemovedAskingWhenANoteGoes() throws Exception {
        filterBy("a.png");
        press(KeyCode.DOWN);
        assertEquals("a.png", selectedName());
        press(KeyCode.B, true);
        assertFalse(bookmarks.isBookmarked(photos.resolve("a.png").toAbsolutePath()), "Ctrl+B takes it off");
        assertEquals("0 images", count(), "neither bookmark nor note: gone from the list");

        filterBy("lighthouse");
        press(KeyCode.DOWN);
        WindowFixture.answerNextDialog(pane -> {
            WindowFixture.type(pane, "The lighthouse, at dawn");
            WindowFixture.press(pane, "Save");
        });
        press(KeyCode.F2);
        assertEquals(
                "The lighthouse, at dawn",
                bookmarks.note(photos.resolve("b.png").toAbsolutePath()));

        WindowFixture.answerNextDialog(pane -> WindowFixture.press(pane, "Cancel"));
        press(REMOVE, true);
        assertEquals(
                "The lighthouse, at dawn",
                bookmarks.note(photos.resolve("b.png").toAbsolutePath()),
                "kept");
        WindowFixture.answerNextDialog(pane -> {
            assertTrue(pane.getHeaderText().startsWith("Remove the note on b.png"));
            WindowFixture.press(pane, "Remove");
        });
        press(REMOVE, true);
        assertEquals("", bookmarks.note(photos.resolve("b.png").toAbsolutePath()), "removed once confirmed");

        // A bare bookmark goes without a question.
        filterBy("");
        filterBy("photos");
        press(KeyCode.DOWN);
        assertEquals("photos", selectedName(), "the folder, bookmarked with no image marked in it");
        press(REMOVE, true);
        assertFalse(bookmarks.isBookmarked(photos.toAbsolutePath()));
    }

    @Test
    void theRightClickMenuAndADoubleClickActOnTheRow() throws Exception {
        filterBy("gone");
        waitUntil("the row's cell", () -> cell(1) != null);
        ListCell<?> gone = call(() -> cell(1));
        fx(() ->
                gone.fireEvent(new ContextMenuEvent(ContextMenuEvent.CONTEXT_MENU_REQUESTED, 5, 5, 5, 5, false, null)));
        ContextMenu menu = call(() -> (ContextMenu) FxTestSupport.invoke(
                window,
                "menu",
                new Class<?>[] {BookmarkList.Row.class},
                list().getItems().get(1)));
        List<MenuItem> items = menu.getItems();
        assertEquals(
                "gone.png",
                call(() -> String.valueOf(
                        ((BookmarkList.Row) list().getItems().get(1)).path().getFileName())));
        assertTrue(items.get(0).isDisable(), "a missing file cannot be opened");
        assertTrue(items.get(1).isDisable(), "nor shown in its folder");
        fx(() -> items.getLast().fire());
        assertEquals("0 images", count(), "removed from the menu");

        filterBy("a.png");
        waitUntil("a.png's cell", () -> cell(1) != null);
        ContextMenu aMenu = call(() -> (ContextMenu) FxTestSupport.invoke(
                window,
                "menu",
                new Class<?>[] {BookmarkList.Row.class},
                list().getItems().get(1)));
        fx(() -> aMenu.getItems().get(1).fire());
        assertEquals(List.of(photos.resolve("a.png").toAbsolutePath()), revealed);
        fx(() -> aMenu.getItems().get(0).fire());
        assertEquals(List.of("a.png"), opened);
        WindowFixture.answerNextDialog(pane -> WindowFixture.press(pane, "Cancel"));
        fx(() -> aMenu.getItems().get(3).fire());
        fx(() -> aMenu.getItems().get(4).fire());
        assertFalse(bookmarks.isBookmarked(photos.resolve("a.png").toAbsolutePath()));

        filterBy("photos");
        waitUntil("the folder's cell", () -> cell(0) != null);
        fx(() -> Objects.requireNonNull(cell(0)).fireEvent(doubleClick()));
        assertEquals(List.of("a.png", "folder photos"), opened);
        filterBy("b.png");
        waitUntil("b.png's picture", () -> {
            ListCell<?> cell = cell(1);
            return cell != null
                    && cell.lookup(".image-view") instanceof javafx.scene.image.ImageView view
                    && view.getImage() != null;
        });
    }

    @Test
    void theOtherKeysOfTheListDoWhatTheHintSays() throws Exception {
        press(KeyCode.NUMPAD3, true);
        assertEquals("1 image", count(), "the keypad's 3: notes");
        press(KeyCode.NUMPAD2, true);
        press(KeyCode.NUMPAD1, true);
        assertEquals("3 images, 1 folder", count());
        press(KeyCode.DIGIT9, true); // no kind of its own
        press(KeyCode.L, true);
        assertTrue(call(() -> filter().isFocused()) || call(stage::isShowing));
        // Return on a file that has gone does nothing; Delete while filtering edits the filter.
        filterBy("gone");
        press(KeyCode.DOWN);
        assertEquals("gone.png", selectedName());
        press(KeyCode.ENTER);
        assertEquals(List.of(), opened, "a missing file is not opened");
        press(KeyCode.DELETE);
        assertTrue(bookmarks.isBookmarked(dir.resolve("gone.png").toAbsolutePath()), "Delete edited the filter");
        // Ctrl+E edits the note too; cancelled, nothing changes.
        WindowFixture.answerNextDialog(pane -> WindowFixture.press(pane, "Cancel"));
        press(KeyCode.E, true);
        assertEquals("", bookmarks.note(dir.resolve("gone.png").toAbsolutePath()));
        // With nothing listed, the keys have nothing to act on.
        filterBy("nothing like this at all");
        for (KeyCode code : List.of(KeyCode.DOWN, KeyCode.UP, KeyCode.ENTER, KeyCode.F2)) press(code);
        press(KeyCode.B, true);
        press(REMOVE, true);
        assertEquals("0 images", count());
        press(KeyCode.Q); // not the list's
        assertTrue(call(stage::isShowing));
    }

    @Test
    void anEmptyListSaysHowToMarkSomething() throws Exception {
        fx(() -> {
            for (Bookmarks.Mark mark : bookmarks.all()) bookmarks.remove(mark.path());
        });
        fx(() -> window.show(stage));
        assertTrue(call(() -> ((javafx.scene.control.Label) FxTestSupport.field(window, "empty")).getText())
                .startsWith("Nothing is bookmarked or noted yet."));
        assertTrue(Files.exists(photos.resolve("a.png")));
    }

    private @Nullable ListCell<?> cell(int index) {
        list().layout();
        for (Node node : list().lookupAll(".list-cell")) {
            if (node instanceof ListCell<?> cell && cell.getIndex() == index && !cell.isEmpty()) return cell;
        }
        return null;
    }

    private static MouseEvent doubleClick() {
        return new MouseEvent(
                MouseEvent.MOUSE_CLICKED,
                5,
                5,
                5,
                5,
                MouseButton.PRIMARY,
                2,
                false,
                false,
                false,
                false,
                true,
                false,
                false,
                false,
                false,
                true,
                null);
    }
}
