package com.mirada.ui;

import java.awt.Color;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

import com.mirada.AppInfo;
import com.mirada.diagnostics.DebugLog;
import com.mirada.folder.FolderImages;
import com.mirada.folder.Sorting;
import com.mirada.settings.Bookmarks;
import org.jspecify.annotations.Nullable;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The viewer window as a user drives it, on JavaFX's headless platform: commands run as the keys,
 * the palette and the menus run them, and what is checked is what the user would see or find
 * afterwards (the title, the image, the panels, what the status bar said, the files, the
 * clipboard). The rules behind each are tested on their own elsewhere; this is that they are wired.
 */
@Tag("fx")
class ViewerWindowTest {

    @TempDir
    Path dir;

    private Path photos = Path.of("unset");

    @AfterEach
    void cleanUp() throws Exception {
        FileDialogs.answeredBy(null);
        FxToolkit.cleanupStages();
        fx(() -> Command.setVertical(false));
    }

    private WindowFixture open(String file) throws Exception {
        photos = WindowFixture.threeImages(dir.resolve("photos"));
        WindowFixture window = WindowFixture.open(dir.resolve("config"), photos.resolve(file));
        window.waitForImage(file);
        return window;
    }

    private static FolderImages folder(WindowFixture window) throws Exception {
        return call(() -> window.field("folder"));
    }

    private static ImageViewport viewport(WindowFixture window) {
        return window.field("viewport");
    }

    private static BorderPane root(WindowFixture window) {
        return window.field("root");
    }

    /** Every label's text under {@code node}, for what a panel shows. */
    private static List<String> texts(Node node) {
        List<String> found = new ArrayList<>();
        collect(node, found);
        return found;
    }

    private static void collect(Node node, List<String> found) {
        if (node instanceof Label label && label.getText() != null) found.add(label.getText());
        if (node instanceof javafx.scene.control.ScrollPane scroll && scroll.getContent() != null)
            collect(scroll.getContent(), found);
        if (node instanceof Parent parent) for (Node child : parent.getChildrenUnmodifiable()) collect(child, found);
    }

    // ---- Stepping through the folder ----

    @Test
    void theArrowKeysHomeAndEndWalkTheFolderAndTheTitleFollows() throws Exception {
        try (WindowFixture window = open("a.png")) {
            assertEquals("a.png — " + AppInfo.NAME, call(window.stage::getTitle));
            window.press(KeyCode.RIGHT);
            window.waitForImage("b.png");
            window.press(KeyCode.END);
            window.waitForImage("c.jpg");
            window.run(Command.NEXT);
            assertEquals(2, folder(window).index(), "the last image is the last: no wrapping");
            window.press(KeyCode.LEFT);
            window.waitForImage("b.png");
            window.press(KeyCode.HOME);
            window.waitForImage("a.png");
            window.run(Command.LAST);
            window.waitForImage("c.jpg");
            window.run(Command.PREVIOUS);
            window.waitForImage("b.png");
            window.run(Command.FIRST);
            window.waitForImage("a.png");
        }
    }

    @Test
    void aFolderOpensAtItsFirstImageAndAnImageWithItsFolderBehindIt() throws Exception {
        photos = WindowFixture.threeImages(dir.resolve("photos"));
        try (WindowFixture window = WindowFixture.open(dir.resolve("config"), photos)) {
            window.waitForImage("a.png");
            assertEquals(3, folder(window).size());
            fx(() -> window.window.open(photos.resolve("c.jpg")));
            window.waitForImage("c.jpg");
            assertEquals(2, folder(window).index());
        }
    }

    @Test
    void whatCannotBeOpenedIsSaidInPlaceOfTheImage() throws Exception {
        photos = WindowFixture.threeImages(dir.resolve("photos"));
        Files.writeString(photos.resolve("broken.png"), "not a picture");
        Files.createDirectories(dir.resolve("empty"));
        try (WindowFixture window = WindowFixture.open(dir.resolve("config"), photos.resolve("broken.png"))) {
            window.waitToSay("Couldn't open broken.png");
            Label placeholder = window.field("placeholder");
            assertTrue(call(placeholder::isVisible));
            assertTrue(call(placeholder::getText).startsWith("Couldn't open broken.png"));
            assertNull(window.shownImage());

            fx(() -> window.window.open(dir.resolve("empty")));
            assertEquals("No images in " + dir.resolve("empty"), call(placeholder::getText));
            assertEquals(AppInfo.NAME, call(window.stage::getTitle), "no folder, no file name");

            fx(() -> window.window.open(dir.resolve("not-there")));
            assertTrue(call(placeholder::getText).contains("not-there"), "said what could not be opened");

            Path text = Files.writeString(dir.resolve("empty/notes.txt"), "words");
            fx(() -> window.window.open(text));
            assertEquals("Mirada can't open TXT files yet", call(placeholder::getText), "opened by name, it is named");
            // The empty window offers to open something, and Open Image's own dialog answers.
            assertNotNull(window.field("openButtons"));
            FileDialogs.answeredBy(
                    (title, folder, name) -> photos.resolve("b.png").toFile());
            window.run(Command.OPEN);
            window.waitForImage("b.png");
            assertFalse(call(() -> ((Node) window.field("openButtons")).isVisible()), "hidden once a folder is open");
        }
    }

    @Test
    void openAndOpenFolderOpenWhatTheirDialogsChooseAndNothingWhenCancelled() throws Exception {
        try (WindowFixture window = open("a.png")) {
            List<String> titles = new ArrayList<>();
            List<File> folders = new ArrayList<>();
            Path other = WindowFixture.png(dir.resolve("other"), "x.png", 10, 10, Color.CYAN);
            FileDialogs.answeredBy((title, folder, name) -> {
                titles.add(title);
                folders.add(folder);
                return title.equals("Open Folder") ? other.getParent().toFile() : null;
            });
            window.run(Command.OPEN);
            assertEquals("a.png — " + AppInfo.NAME, call(window.stage::getTitle), "cancelled: nothing changes");
            window.run(Command.OPEN_FOLDER);
            window.waitForImage("x.png");
            assertEquals(List.of("Open Image", "Open Folder"), titles);
            assertEquals(photos.toFile(), folders.getFirst(), "the dialog starts in the folder on screen");
        }
    }

    // ---- Where the window opens, and what is dropped on it ----

    @Test
    void theWindowOpensWhereItClosedMaximisedIfItWasAndOnScreenIfThatPlaceIsGone() throws Exception {
        photos = WindowFixture.threeImages(dir.resolve("photos"));
        try (WindowFixture window = WindowFixture.open(dir.resolve("moved"), photos, s -> {})) {
            fx(() -> {
                window.stage.setX(20);
                window.stage.setY(30);
                window.stage.setWidth(700);
            });
        }
        com.mirada.settings.Settings saved = new com.mirada.settings.Settings(dir.resolve("moved/settings.properties"));
        saved.load();
        assertEquals(20, saved.window().x(), "its place is saved as it closes");
        assertEquals(700, saved.window().width());

        try (WindowFixture window = WindowFixture.open(
                dir.resolve("maximised"),
                photos,
                s -> s.setWindow(new com.mirada.settings.Settings.WindowPlace(0, 0, 800, 600, true)))) {
            assertTrue(call(window.stage::isMaximized), "maximised, as it closed");
        }
        try (WindowFixture window = WindowFixture.open(
                dir.resolve("gone"),
                photos,
                s -> s.setWindow(new com.mirada.settings.Settings.WindowPlace(5000, 5000, 800, 600, false)))) {
            // Left where the system puts a new window (the headless glass: nowhere in particular).
            assertFalse(call(window.stage::getX) == 5000, "a place no longer on a screen is not used");
        }
    }

    @Test
    void aFileDroppedOnTheWindowOpensWithItsFolder() throws Exception {
        try (WindowFixture window = open("a.png")) {
            Path other = WindowFixture.png(dir.resolve("dropped"), "x.png", 10, 10, Color.CYAN);
            javafx.scene.input.DragEvent dropped =
                    call(() -> drag(javafx.scene.input.DragEvent.DRAG_DROPPED, List.of(other.toFile())));
            fx(() -> window.stage.getScene().getOnDragDropped().handle(dropped));
            window.waitForImage("x.png");
        }
    }

    /** A drag carrying {@code files}, from outside the window. */
    private static javafx.scene.input.DragEvent drag(
            javafx.event.EventType<javafx.scene.input.DragEvent> type, List<File> files) throws Exception {
        // A dragboard is the toolkit's to make; the tests run on the classpath, where its internals
        // are reachable by reflection (the compile is modular, hence the names as strings).
        Class<?> clipboard = Class.forName("com.sun.javafx.tk.TKClipboard");
        Object toolkit = Class.forName("com.sun.javafx.tk.Toolkit")
                .getMethod("getToolkit")
                .invoke(null);
        Object local = toolkit.getClass().getMethod("createLocalClipboard").invoke(toolkit);
        java.lang.reflect.Constructor<javafx.scene.input.Dragboard> make =
                javafx.scene.input.Dragboard.class.getDeclaredConstructor(clipboard);
        make.setAccessible(true);
        javafx.scene.input.Dragboard board = make.newInstance(local);
        javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
        content.putFiles(files);
        board.setContent(content);
        return new javafx.scene.input.DragEvent(
                type, board, 10, 10, 10, 10, javafx.scene.input.TransferMode.COPY, null, null, null);
    }

    // ---- The image ----

    @Test
    void anAnimationPlaysOnceItsFramesAreDecodedAndStopsWhenSteppedAway() throws Exception {
        Path folder = Files.createDirectories(dir.resolve("moving"));
        for (String name : List.of("webp-animated.webp", "jxl-animated.jxl")) {
            try (var in = ViewerWindowTest.class.getResourceAsStream("/com/mirada/matrix/" + name)) {
                Files.copy(Objects.requireNonNull(in), folder.resolve(name));
            }
        }
        try (WindowFixture window = WindowFixture.open(dir.resolve("config"), folder.resolve("jxl-animated.jxl"))) {
            window.waitForImage("jxl-animated.jxl");
            waitUntil("the animation", () -> window.field("playing") != null);
            waitUntil("a later frame", () -> FxTestSupport.<Integer>field(window.window, "frame") > 0);
            window.run(Command.NEXT);
            window.waitForImage("webp-animated.webp");
            waitUntil("the next animation", () -> window.field("playing") != null);
            window.run(Command.EDIT); // the editor takes a still
            assertNull(window.field("playing"), "stopped for the editor");
        }
    }

    @Test
    void rotatingZoomingAndPanningMoveTheViewNotThePixels() throws Exception {
        try (WindowFixture window = open("a.png")) {
            ImageViewport viewport = viewport(window);
            window.press(KeyCode.R);
            assertEquals(1, call(viewport::turns));
            assertEquals(48.0, call(viewport::contentWidth), "the turned width");
            window.run(Command.ROTATE_COUNTERCLOCKWISE);
            window.run(Command.ROTATE_COUNTERCLOCKWISE);
            assertEquals(3, call(viewport::turns));
            assertTrue(call(() -> viewport.fittedProperty().get()));
            window.run(Command.ACTUAL_SIZE);
            assertEquals(1.0, call(() -> viewport.scaleProperty().get()));
            window.run(Command.ZOOM_IN);
            double zoomed = call(() -> viewport.scaleProperty().get());
            assertTrue(zoomed > 1, "zoomed in from actual size");
            assertFalse(call(() -> viewport.fittedProperty().get()));
            for (Command pan : List.of(Command.PAN_LEFT, Command.PAN_RIGHT, Command.PAN_UP, Command.PAN_DOWN)) {
                window.run(pan);
            }
            window.run(Command.ZOOM_OUT);
            assertTrue(call(() -> viewport.scaleProperty().get()) < zoomed);
            window.run(Command.FIT);
            assertTrue(call(() -> viewport.fittedProperty().get()));
            // Stepping fits the next image unless the zoom is kept.
            window.run(Command.KEEP_ZOOM);
            assertTrue(window.settings.keepZoom());
            assertEquals("Keeping the zoom when stepping", window.lastSaid());
            window.run(Command.KEEP_ZOOM);
            assertEquals("Fitting each image", window.lastSaid());
        }
    }

    @Test
    void copyPutsThePictureAsItStandsAndItsFileOnTheClipboard() throws Exception {
        try (WindowFixture window = open("a.png")) {
            window.run(Command.COPY);
            window.waitToSay("Copied a.png");
            assertEquals(
                    64.0, call(() -> Clipboard.getSystemClipboard().getImage().getWidth()));
            assertEquals(
                    List.of(photos.resolve("a.png").toFile()),
                    call(() -> Clipboard.getSystemClipboard().getFiles()));
            // Turned on screen, it is copied turned (off the FX thread).
            window.run(Command.ROTATE_CLOCKWISE);
            fx(() -> Clipboard.getSystemClipboard().clear());
            window.run(Command.COPY);
            waitUntil("the turned copy", () -> Clipboard.getSystemClipboard().hasImage());
            assertEquals(
                    48.0, call(() -> Clipboard.getSystemClipboard().getImage().getWidth()));
        }
    }

    @Test
    void sortingReordersTheOpenFolderAndKeepsTheImageOnScreen() throws Exception {
        try (WindowFixture window = open("a.png")) {
            // c.jpg is the largest file, b.png the smallest picture.
            window.run(Command.SORT_BY_SIZE);
            assertEquals(Sorting.By.SIZE, window.settings.sorting().by());
            assertEquals("Sorted by size", window.lastSaid());
            assertEquals(photos.resolve("a.png"), folder(window).current(), "the same image stays");
            window.run(Command.REVERSE_ORDER);
            assertEquals("Sorted by size, reversed", window.lastSaid());
            window.run(Command.SORT_BY_DATE);
            assertEquals("Sorted by date, reversed", window.lastSaid());
            window.run(Command.SORT_BY_NAME);
            window.run(Command.REVERSE_ORDER);
            assertEquals("Sorted by name", window.lastSaid());
            assertEquals(
                    List.of("a.png", "b.png", "c.jpg"),
                    folder(window).images().stream()
                            .map(p -> p.getFileName().toString())
                            .toList());
        }
    }

    // ---- Panels ----

    @Test
    void thePropertiesSidebarShowsTheImageOnScreenAndFollowsIt() throws Exception {
        try (WindowFixture window = open("a.png")) {
            window.press(KeyCode.I);
            BorderPane root = root(window);
            assertNotNull(call(root::getRight), "the sidebar is open");
            waitUntil("the details of a.png", () -> texts(root.getRight()).contains("a.png"));
            assertTrue(call(() -> texts(root.getRight())).contains("64 × 48"), "the size, as shown");
            window.run(Command.ROTATE_CLOCKWISE);
            waitUntil("the turned size", () -> texts(root.getRight()).contains("48 × 64"));
            window.run(Command.NEXT);
            waitUntil("the details of b.png", () -> texts(root.getRight()).contains("b.png"));
            window.run(Command.INFO);
            assertNull(call(root::getRight), "closed again");
        }
    }

    @Test
    void theThumbnailStripStandsAlongTheBottomOrDownTheSideAndStepsTheWayItRuns() throws Exception {
        try (WindowFixture window = open("a.png")) {
            window.run(Command.THUMBNAILS);
            ThumbnailStrip strip = window.field("strip");
            VBox bottom = window.field("bottom");
            assertTrue(call(() -> bottom.getChildren().contains(strip.node())));
            assertEquals(0, call(() -> strip.node().getSelectionModel().getSelectedIndex()));
            waitUntil(
                    "the strip's cells",
                    () -> !strip.node().lookupAll(".list-cell").isEmpty());

            window.run(Command.THUMBNAILS_AT_SIDE);
            assertTrue(window.settings.thumbnailsAtSide());
            assertSame(strip.node(), call(() -> root(window).getLeft()));
            assertTrue(call(Command::vertical), "down the side, Up and Down step");
            window.press(KeyCode.DOWN);
            window.waitForImage("b.png");
            assertEquals(1, call(() -> strip.node().getSelectionModel().getSelectedIndex()));

            window.run(Command.THUMBNAIL_NAMES);
            assertTrue(window.settings.thumbnailNames());
            window.run(Command.THUMBNAIL_CAPTIONS);
            window.run(Command.THUMBNAILS_AT_SIDE);
            assertFalse(call(Command::vertical));
            assertNull(call(() -> root(window).getLeft()));

            // A click on a thumbnail steps the window there.
            waitUntil("the third thumbnail", () -> thumbnail(strip, 2) != null);
            fx(() -> Objects.requireNonNull(thumbnail(strip, 2))
                    .fireEvent(click(javafx.scene.input.MouseButton.PRIMARY)));
            window.waitForImage("c.jpg");

            ContextMenu menu =
                    call(() -> (ContextMenu) FxTestSupport.invoke(window.window, "stripMenu", new Class<?>[0]));
            assertEquals("Hide Thumbnails", menu.getItems().getFirst().getText());
            fx(() -> menu.getItems().getFirst().fire());
            assertFalse(call(() -> bottom.getChildren().contains(strip.node())), "hidden from its own menu");
        }
    }

    @Test
    void theStatusBarSaysWhatHappenedAndWithoutItTheImageDoes() throws Exception {
        try (WindowFixture window = open("a.png")) {
            Label toast = window.field("toast");
            window.run(Command.KEEP_ZOOM);
            assertFalse(call(toast::isVisible), "the status bar says it");
            window.run(Command.STATUS_BAR);
            assertFalse(window.settings.statusBar());
            window.run(Command.KEEP_ZOOM);
            assertTrue(call(toast::isVisible), "with the bar off, said over the image");
            assertEquals("Fitting each image", call(toast::getText));
            fx(() -> window.window.sayLater(
                    "Something went wrong: boom", com.mirada.diagnostics.MessageLog.Severity.ERROR));
            assertTrue(call(() -> toast.getPseudoClassStates().stream()
                    .anyMatch(p -> p.getPseudoClassName().equals("error"))));
            window.run(Command.STATUS_BAR);
            assertTrue(window.settings.statusBar());

            window.run(Command.COPY_MESSAGES);
            assertTrue(call(() -> Clipboard.getSystemClipboard().getString()).contains("Fitting each image"));
            window.run(Command.MESSAGE_LOG);
            MessageLogView log = window.field("messageLog");
            assertTrue(call(log::isShowing));
            window.press(KeyCode.ESCAPE);
            assertFalse(call(log::isShowing), "Escape closes the log, not the window");
            assertTrue(call(window.stage::isShowing));
            window.run(Command.CLEAR_MESSAGES);
            assertEquals(List.of(), window.said());
            assertEquals("Nothing has been said yet.", unavailable(window, Command.COPY_MESSAGES));
        }
    }

    @Test
    void theDebugLogIsShownCopiedClearedAndExported() throws Exception {
        try (WindowFixture window = open("a.png")) {
            // As App.main does: the Debug Log takes java.util.logging's records.
            java.lang.reflect.Method capture = DebugLog.class.getDeclaredMethod("captureLogging");
            capture.setAccessible(true);
            capture.invoke(null);
            java.util.logging.Logger.getLogger("com.mirada.test").warning("a line for the log");
            window.run(Command.DEBUG_LOG);
            DebugLogWindow log = window.field("debugLog");
            assertTrue(call(() -> log.stage().isShowing()));
            window.run(Command.COPY_DEBUG_LOG);
            assertEquals("Copied the Debug Log", window.lastSaid());
            assertTrue(call(() -> Clipboard.getSystemClipboard().getString()).contains("a line for the log"));
            Path exported = dir.resolve("exported.txt");
            FileDialogs.answeredBy((title, folder, name) -> {
                assertEquals("mirada-debug-log.txt", name);
                return exported.toFile();
            });
            window.run(Command.EXPORT_DEBUG_LOG);
            waitUntil("the export", () -> Files.exists(exported));
            assertTrue(Files.readString(exported).contains("a line for the log"));
            window.run(Command.CLEAR_DEBUG_LOG);
            assertFalse(DebugLog.text().contains("a line for the log"));
        }
    }

    // ---- Bookmarks and notes ----

    @Test
    void bookmarksAndNotesAreKeptStepToAndFoundAgain() throws Exception {
        try (WindowFixture window = open("a.png")) {
            window.waitForFirstPaint();
            Bookmarks bookmarks = window.field("bookmarks");
            assertEquals("Nothing is bookmarked or noted yet.", unavailable(window, Command.GO_TO_BOOKMARK));
            window.run(Command.BOOKMARK);
            assertEquals("Bookmarked a.png", window.lastSaid());
            assertTrue(call(() -> bookmarks.isBookmarked(photos.resolve("a.png").toAbsolutePath())));
            MarksBadge badge = window.field("marksBadge");
            assertNotNull(badge, "the badge over the image");

            window.run(Command.LAST);
            window.waitForImage("c.jpg");
            window.run(Command.BOOKMARK);
            window.run(Command.FIRST);
            window.waitForImage("a.png");
            window.run(Command.NEXT_BOOKMARK);
            window.waitForImage("c.jpg");
            window.run(Command.NEXT_BOOKMARK);
            assertEquals("No bookmarked image after this one", window.lastSaid());
            assertEquals("No bookmarked image after this one.", unavailable(window, Command.NEXT_BOOKMARK));
            window.run(Command.PREVIOUS_BOOKMARK);
            window.waitForImage("a.png");
            window.run(Command.PREVIOUS_BOOKMARK);
            assertEquals("No bookmarked image before this one", window.lastSaid());

            WindowFixture.answerNextDialog(pane -> {
                WindowFixture.type(pane, "Taken on the pier");
                WindowFixture.press(pane, "Save");
            });
            window.run(Command.NOTE);
            assertEquals("Kept the note on a.png", window.lastSaid());
            assertEquals(
                    "Taken on the pier",
                    call(() -> bookmarks.note(photos.resolve("a.png").toAbsolutePath())));

            WindowFixture.answerNextDialog(pane -> {
                WindowFixture.type(pane, "Summer");
                WindowFixture.press(pane, "Save");
            });
            window.run(Command.FOLDER_NOTE);
            assertEquals("Kept the note on the folder photos", window.lastSaid());
            window.run(Command.BOOKMARK_FOLDER);
            assertEquals("Bookmarked the folder photos", window.lastSaid());

            // Cancelled, the note stays as it was.
            WindowFixture.answerNextDialog(pane -> WindowFixture.press(pane, "Cancel"));
            window.run(Command.NOTE);
            assertEquals(
                    "Taken on the pier",
                    call(() -> bookmarks.note(photos.resolve("a.png").toAbsolutePath())));

            // Go to Bookmark: the palette over every mark; the note's words find it.
            window.run(Command.LAST);
            window.waitForImage("c.jpg");
            window.run(Command.GO_TO_BOOKMARK);
            CommandPalette palette = window.field("palette");
            assertTrue(call(palette::isShowing));
            typeInPalette(window, "pier");
            window.press(KeyCode.ENTER);
            window.waitForImage("a.png");
            assertFalse(call(palette::isShowing));

            // Emptied, the note goes; taken off, the bookmark too.
            WindowFixture.answerNextDialog(pane -> {
                WindowFixture.type(pane, "");
                WindowFixture.press(pane, "Save");
            });
            window.run(Command.NOTE);
            assertEquals("Took the note off a.png", window.lastSaid());
            window.run(Command.BOOKMARK);
            assertEquals("Took the bookmark off a.png", window.lastSaid());
            assertFalse(
                    call(() -> bookmarks.isBookmarked(photos.resolve("a.png").toAbsolutePath())));
        }
    }

    @Test
    void theBookmarksWindowListsTheMarksAndOpensOne() throws Exception {
        try (WindowFixture window = open("a.png")) {
            window.waitForFirstPaint();
            window.run(Command.LAST);
            window.waitForImage("c.jpg");
            window.run(Command.BOOKMARK);
            window.run(Command.FIRST);
            window.waitForImage("a.png");
            window.run(Command.BOOKMARKS);
            BookmarksWindow marks = window.field("bookmarksWindow");
            Stage list = marks.stage();
            assertTrue(call(list::isShowing));
            assertTrue(call(() -> texts(list.getScene().getRoot())).stream().anyMatch(t -> t.contains("c.jpg")));
            fx(() -> FxTestSupport.invoke(
                    window.window,
                    "openFromBookmarks",
                    new Class<?>[] {Path.class, boolean.class},
                    photos.resolve("c.jpg").toAbsolutePath(),
                    false));
            window.waitForImage("c.jpg");
            window.run(Command.BOOKMARKS);
            assertTrue(call(list::isShowing), "asked again, brought forward");
        }
    }

    // ---- Renaming ----

    @Test
    void renameRenamesTheFileInPlaceAndRefusesToReplaceAnother() throws Exception {
        try (WindowFixture window = open("a.png")) {
            WindowFixture.answerNextDialog(pane -> {
                assertEquals("a.png", ((TextField) pane.lookup(".text-field")).getText());
                WindowFixture.type(pane, "harbour");
                WindowFixture.press(pane, "OK");
            });
            window.run(Command.RENAME);
            assertEquals("Renamed to harbour.png", window.lastSaid(), "the extension kept");
            window.waitForImage("harbour.png");
            assertTrue(Files.exists(photos.resolve("harbour.png")));
            assertFalse(Files.exists(photos.resolve("a.png")));

            WindowFixture.answerNextDialog(pane -> {
                WindowFixture.type(pane, "b.png");
                WindowFixture.press(pane, "OK");
            });
            window.run(Command.RENAME);
            assertEquals("Not renamed: b.png already exists", window.lastSaid());

            WindowFixture.answerNextDialog(pane -> {
                WindowFixture.type(pane, "a/b");
                WindowFixture.press(pane, "OK");
            });
            window.run(Command.RENAME);
            assertTrue(window.lastSaid().startsWith("Not renamed: "), window.lastSaid());

            int said = window.said().size();
            WindowFixture.answerNextDialog(pane -> WindowFixture.press(pane, "Cancel"));
            window.run(Command.RENAME);
            assertEquals(said, window.said().size(), "cancelled: nothing said, nothing renamed");
            assertTrue(Files.exists(photos.resolve("harbour.png")));
        }
    }

    // ---- The palette ----

    @Test
    void thePaletteRunsACommandFoundByNameAndGoesToAnImageOrARecentFolder() throws Exception {
        try (WindowFixture window = open("a.png")) {
            CommandPalette palette = window.field("palette");
            window.press(KeyCode.P, true, true);
            assertTrue(call(palette::isShowing));
            window.press(KeyCode.R);
            assertEquals(0, call(() -> viewport(window).turns()), "typing in the palette does not rotate");
            typeInPalette(window, "rotate right");
            window.press(KeyCode.ENTER);
            assertFalse(call(palette::isShowing));
            assertEquals(1, call(() -> viewport(window).turns()), "the command ran");

            window.press(KeyCode.P, true, true);
            window.press(KeyCode.P, true, true);
            assertFalse(call(palette::isShowing), "its own key closes it");

            window.run(Command.GO_TO_IMAGE);
            typeInPalette(window, "c.j");
            window.press(KeyCode.ENTER);
            window.waitForImage("c.jpg");

            window.waitForFirstPaint();
            window.run(Command.OPEN_RECENT);
            assertEquals("No other folder has been opened lately", window.lastSaid());
            Path other = WindowFixture.png(dir.resolve("holiday"), "x.png", 10, 10, Color.CYAN);
            fx(() -> window.window.open(other));
            window.waitForImage("x.png");
            window.run(Command.OPEN_RECENT);
            typeInPalette(window, "photos");
            window.press(KeyCode.ENTER);
            window.waitForImage("a.png");

            window.run(Command.PALETTE);
            window.press(KeyCode.ESCAPE);
            assertFalse(call(palette::isShowing));
            assertTrue(call(window.stage::isShowing), "Escape closed the palette, not the window");
        }
    }

    private static void typeInPalette(WindowFixture window, String text) throws Exception {
        CommandPalette palette = window.field("palette");
        TextField input = FxTestSupport.field(palette, "input");
        fx(() -> input.setText(text));
    }

    // ---- Full screen and the slideshow ----

    @Test
    void theSlideshowGoesFullScreenStepsOnItsOwnAndEscapeStopsIt() throws Exception {
        try (WindowFixture window = open("b.png")) {
            window.settings.setSlideshowSeconds(1);
            window.run(Command.SLIDESHOW);
            assertTrue(call(window.stage::isFullScreen));
            assertTrue(window.lastSaid().startsWith("Slideshow: every 1 s"));
            StatusBar bar = window.field("statusBar");
            assertFalse(call(bar::isShowing), "no status bar over a slideshow");
            window.waitForImage("c.jpg");
            window.waitForImage("a.png"); // round again after the last
            window.press(KeyCode.ESCAPE);
            assertEquals("Slideshow stopped", window.lastSaid());
            assertFalse(call(window.stage::isFullScreen));
            assertTrue(call(bar::isShowing));
            assertTrue(call(window.stage::isShowing));

            window.press(KeyCode.F);
            assertTrue(call(window.stage::isFullScreen));
            assertEquals(
                    "Leave Full Screen",
                    FxTestSupport.invoke(window.window, "label", new Class<?>[] {Command.class}, Command.FULL_SCREEN));
            window.press(KeyCode.ESCAPE);
            assertFalse(call(window.stage::isFullScreen));
        }
    }

    @Test
    void escapeClosesTheWindowOnLinuxOnly() throws Exception {
        try (WindowFixture window = open("a.png")) {
            window.press(KeyCode.ESCAPE);
            assertEquals(Command.escapeCloses(System.getProperty("os.name")), !call(window.stage::isShowing));
        }
    }

    // ---- Help, settings and the other windows ----

    @Test
    void helpCommandsOpenTheirWindowsAndLinks() throws Exception {
        try (WindowFixture window = open("a.png")) {
            window.run(Command.DOCUMENTATION);
            assertEquals(List.of(AppInfo.DOCS_URL), window.links);
            window.run(Command.ABOUT);
            window.run(Command.SHORTCUTS);
            window.run(Command.SETTINGS);
            window.run(Command.REPORT_PROBLEM);
            List<String> titles = call(() -> Window.getWindows().stream()
                    .filter(w -> w instanceof Stage s && s != window.stage && s.isShowing())
                    .map(w -> ((Stage) w).getTitle())
                    .toList());
            assertEquals(4, titles.size(), titles.toString());
            assertNotNull(window.field("about"));
            assertNotNull(window.field("shortcutsWindow"));
            assertNotNull(window.field("settingsWindow"));
            // Asked again, each is brought forward rather than made twice.
            Object about = window.field("about");
            window.run(Command.ABOUT);
            assertSame(about, window.field("about"));

            assertEquals(
                    "No newer release is known: Check for Updates first.", unavailable(window, Command.OPEN_RELEASE));
            window.run(Command.CHECK_FOR_UPDATES);
            window.waitToSay("Couldn't check for updates");
            window.run(Command.OPEN_RELEASE);
            assertEquals(List.of(AppInfo.DOCS_URL), window.links, "no release known, nothing opened");
        }
    }

    // ---- The context menu ----

    @Test
    void theContextMenuNamesTheCommandsTicksTheSettingsAndGreysWhatCannotRun() throws Exception {
        try (WindowFixture window = open("a.png")) {
            window.run(Command.INFO);
            ContextMenu menu =
                    call(() -> (ContextMenu) FxTestSupport.invoke(window.window, "contextMenu", new Class<?>[0]));
            List<MenuItem> items = menu.getItems();
            MenuItem info = items.stream()
                    .filter(i -> Command.INFO.label().equals(i.getText()))
                    .findFirst()
                    .orElseThrow();
            assertTrue(((CheckMenuItem) info).isSelected(), "the sidebar is open: ticked");
            Menu sort = (Menu) items.stream()
                    .filter(i -> "Sort".equals(i.getText()))
                    .findFirst()
                    .orElseThrow();
            assertFalse(sort.isDisable());
            fx(() -> sort.getItems().get(2).fire());
            assertEquals(Sorting.By.SIZE, window.settings.sorting().by(), "the menu's item runs its command");
            MenuItem copy = items.stream()
                    .filter(i -> Command.COPY.label().equals(i.getText()))
                    .findFirst()
                    .orElseThrow();
            assertFalse(copy.isDisable());
            fx(copy::fire);
            window.waitToSay("Copied a.png");
            for (Command command : Command.values()) {
                FxTestSupport.invoke(window.window, "ticked", new Class<?>[] {Command.class}, command);
            }
        }
    }

    @Test
    void theFloatingControlsAreBuiltOnTheFirstPointerMovementAndStepTheFolder() throws Exception {
        try (WindowFixture window = open("a.png")) {
            assertNull(window.field("controls"));
            Node canvas = window.field("canvas");
            fx(() -> canvas.fireEvent(new MouseEvent(
                    MouseEvent.MOUSE_MOVED,
                    300,
                    300,
                    300,
                    300,
                    javafx.scene.input.MouseButton.NONE,
                    0,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    null)));
            OnScreenControls controls = window.field("controls");
            assertNotNull(controls);
            javafx.scene.control.Button next = FxTestSupport.field(controls, "next");
            fx(next::fire);
            window.waitForImage("b.png");
            fx(() -> controls.setVertical(true));
            fx(() -> controls.setFullScreen(true));
            fx(() -> controls.setFullScreen(false));
        }
    }

    // ---- The editor ----

    @Test
    void theEditorTurnsFlipsAndCropsAndSavesANewFileBesideTheOld() throws Exception {
        try (WindowFixture window = open("a.png")) {
            window.press(KeyCode.E);
            Editor editor = window.field("editor");
            assertTrue(call(editor::isShowing));
            window.run(Command.NEXT);
            assertEquals("Finish editing first: Save or Cancel", window.lastSaid());
            assertEquals("a.png — " + AppInfo.NAME, call(window.stage::getTitle));

            window.press(KeyCode.ENTER);
            assertEquals("Nothing to save: crop, turn or flip first", window.lastSaid());
            // S, Z and Y alone are not the editor's (Ctrl with them is); nor is Q at all.
            int said = window.said().size();
            for (KeyCode alone : List.of(KeyCode.S, KeyCode.Z, KeyCode.Y, KeyCode.Q)) window.press(alone);
            assertEquals(said, window.said().size());
            assertTrue(call(editor::isShowing));
            window.press(KeyCode.R);
            window.press(KeyCode.H);
            window.press(KeyCode.V);
            window.press(KeyCode.Z, true, false); // undo the flip
            window.press(KeyCode.Z, true, true); // and redo it
            window.press(KeyCode.Y, true, false);
            window.press(KeyCode.A); // a shape for the crop
            window.press(KeyCode.RIGHT);
            window.press(KeyCode.DOWN, true, false);
            window.press(KeyCode.LEFT, false, true);
            window.press(KeyCode.UP);

            Path saved = photos.resolve("a (edited).png");
            List<String> names = new ArrayList<>();
            FileDialogs.answeredBy((title, folder, name) -> {
                names.add(name);
                return saved.toFile();
            });
            window.press(KeyCode.S, true, false);
            window.waitToSay("Saved a (edited).png");
            assertEquals(List.of("a (edited).png"), names, "the dialog suggests a new name");
            assertFalse(call(editor::isShowing), "closed once saved");
            window.waitForImage("a (edited).png");
            javafx.scene.image.Image written =
                    call(() -> new javafx.scene.image.Image(saved.toUri().toString()));
            assertTrue(written.getWidth() <= 48 && written.getHeight() <= 64, "turned a quarter, then cropped");
            assertEquals(4, folder(window).size(), "the new file joins the folder");
        }
    }

    @Test
    void theEditorsToolbarDoesWhatItsKeysDo() throws Exception {
        try (WindowFixture window = open("a.png")) {
            window.run(Command.EDIT);
            Editor editor = window.field("editor");
            javafx.scene.layout.BorderPane node = FxTestSupport.field(editor, "node");
            CropView crop = FxTestSupport.field(editor, "crop");
            for (String tool : List.of("Rotate Right", "Rotate Left", "Flip Horizontally", "Flip Vertically")) {
                fx(() -> toolbarButton(node, tool).fire());
            }
            fx(() -> toolbarButton(node, "Undo").fire());
            fx(() -> toolbarButton(node, "Redo").fire());
            // A crop, then a shape: the crop is held to the shape where it stands.
            window.press(KeyCode.LEFT, true, false);
            window.press(KeyCode.UP, true, false);
            javafx.scene.control.ChoiceBox<com.mirada.edit.AspectRatio> shape = FxTestSupport.field(editor, "shape");
            fx(() -> shape.setValue(com.mirada.edit.AspectRatio.values()[2]));
            com.mirada.edit.CropBox box = call(crop::box);
            assertTrue(box.width() < call(crop::pictureWidth), "still cropped");
            assertEquals(com.mirada.edit.AspectRatio.FREE, shape.getConverter().fromString("anything"));
            fx(() -> toolbarButton(node, "Reset").fire());
            assertEquals(com.mirada.edit.AspectRatio.FREE, call(shape::getValue));
            // Save with nothing to save, then Cancel with nothing to lose.
            fx(() -> toolbarButton(node, "Save…").fire());
            assertEquals("Nothing to save: crop, turn or flip first", window.lastSaid());
            fx(() -> toolbarButton(node, "Cancel").fire());
            assertFalse(call(editor::isShowing));
        }
    }

    @Test
    void aSaveThatFailsSaysWhyAndKeepsTheEditorOpen() throws Exception {
        try (WindowFixture window = open("a.png")) {
            window.run(Command.EDIT);
            window.press(KeyCode.R);
            Editor editor = window.field("editor");
            // A folder that is not there cannot take the file.
            FileDialogs.answeredBy(
                    (title, folder, name) -> dir.resolve("no-such-folder/a.png").toFile());
            window.press(KeyCode.ENTER);
            window.waitToSay("Couldn't save a.png");
            assertTrue(call(editor::isShowing));
            // Cancelled, the dialog saves nothing.
            FileDialogs.answeredBy((title, folder, name) -> null);
            int said = window.said().size();
            window.press(KeyCode.ENTER);
            assertEquals(said, window.said().size());
            // A name without an extension gets the file's; one that would replace a file asks.
            Files.writeString(photos.resolve("copy.png"), "already here");
            FileDialogs.answeredBy(
                    (title, folder, name) -> photos.resolve("copy").toFile());
            WindowFixture.answerNextDialog(pane -> WindowFixture.press(pane, "Cancel"));
            window.press(KeyCode.ENTER);
            assertEquals("already here", Files.readString(photos.resolve("copy.png")), "not replaced");
        }
    }

    private static javafx.scene.control.Button toolbarButton(Parent root, String text) {
        for (Node node : root.lookupAll(".button")) {
            if (node instanceof javafx.scene.control.Button button && text.equals(button.getText())) return button;
        }
        throw new AssertionError("no button " + text);
    }

    @Test
    void cancellingAnEditAsksBeforeDiscardingIt() throws Exception {
        try (WindowFixture window = open("a.png")) {
            window.run(Command.EDIT);
            Editor editor = window.field("editor");
            window.press(KeyCode.ESCAPE);
            assertFalse(call(editor::isShowing), "nothing changed, nothing asked");

            window.run(Command.EDIT);
            window.press(KeyCode.R);
            window.press(KeyCode.BACK_SPACE); // reset: nothing to lose again
            window.press(KeyCode.R);
            WindowFixture.answerNextDialog(pane -> {
                assertTrue(pane.getHeaderText().startsWith("Discard the edit to a.png"));
                WindowFixture.press(pane, "Cancel");
            });
            window.press(KeyCode.ESCAPE);
            assertTrue(call(editor::isShowing), "kept: the user said Cancel");
            WindowFixture.answerNextDialog(pane -> WindowFixture.press(pane, "Discard"));
            window.run(Command.CLOSE);
            assertFalse(call(window.stage::isShowing), "discarded, and the window closed");
        }
    }

    private static javafx.scene.control.@Nullable ListCell<?> thumbnail(ThumbnailStrip strip, int index) {
        strip.node().layout();
        for (Node cell : strip.node().lookupAll(".list-cell")) {
            if (cell instanceof javafx.scene.control.ListCell<?> listCell
                    && listCell.getIndex() == index
                    && !listCell.isEmpty()) return listCell;
        }
        return null;
    }

    static MouseEvent click(javafx.scene.input.MouseButton button) {
        return new MouseEvent(
                MouseEvent.MOUSE_CLICKED,
                5,
                5,
                5,
                5,
                button,
                1,
                false,
                false,
                false,
                false,
                button == javafx.scene.input.MouseButton.PRIMARY,
                false,
                button == javafx.scene.input.MouseButton.SECONDARY,
                false,
                false,
                true,
                null);
    }

    private static String unavailable(WindowFixture window, Command command) throws Exception {
        return String.valueOf(call(
                () -> FxTestSupport.invoke(window.window, "unavailable", new Class<?>[] {Command.class}, command)));
    }
}
