package com.mirada.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import javafx.application.Platform;
import javafx.event.Event;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.SVGPath;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.stage.Window;

import com.mirada.image.FxToolkit;
import com.mirada.settings.Bookmarks;
import com.mirada.settings.Settings;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The menus and icon buttons as a user meets them, in a real window on the Headless platform (Pauta
 * MENU-06, MENU-07, MENU-09, MENU-14, MENU-16, ICON-20, ICON-21, TBAR-01, TBAR-03, TBAR-08).
 */
@Tag("fx")
class MenusFxTest {

    @TempDir
    static Path config;

    private static Stage stage;
    private static ViewerWindow window;

    @BeforeAll
    static void open() throws Exception {
        FxToolkit.start();
        Fonts.load();
        fx(() -> {
            Fonts.ready();
            Theme.apply(Settings.ThemeChoice.LIGHT);
            stage = new Stage();
            Settings settings = new Settings(config.resolve("settings.properties"));
            window = new ViewerWindow(stage, settings, link -> {});
            window.show();
            // The Headless platform's screen is 1000 pixels square and draws nothing past it.
            stage.setX(0);
            stage.setY(0);
            stage.setWidth(900);
            stage.setHeight(700);
            return null;
        });
        // The header's menu is filled after the first paint, or half a second after start without one.
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (fx(() -> headerMenu().getItems().isEmpty())) {
            if (System.nanoTime() > deadline) throw new AssertionError("the header's menu was never filled");
            Thread.sleep(50);
        }
    }

    @AfterAll
    static void close() throws Exception {
        fx(() -> {
            closeMenus();
            stage.hide();
            return null;
        });
    }

    @Test
    void everyRowOfEveryMenuHasAGlyph() throws Exception {
        List<String> blank = new ArrayList<>();
        fx(() -> {
            unglyphed("header menu", headerMenu().getItems(), blank);
            OnScreenControls controls = new OnScreenControls(new StackPane(), c -> {});
            unglyphed("zoom menu", controls.zoomMenu().getItems(), blank);
            unglyphed("image menu", rightClick(viewport()).getItems(), blank);
            closeMenus();
            return null;
        });
        assertTrue(blank.isEmpty(), "rows without a glyph: " + blank);
    }

    @Test
    void previousAndNextAreInTheHeadersMenu() throws Exception {
        // TBAR-08: the floating arrows are not the only way to them on Linux and Windows.
        List<Object> commands = fx(() ->
                headerMenu().getItems().stream().map(MenuItem::getUserData).toList());
        assertTrue(commands.contains(Command.PREVIOUS) && commands.contains(Command.NEXT), commands.toString());
    }

    @Test
    void menusAreInterFourteenWhateverTheyOpenFrom() throws Exception {
        assertTrue(fx(Fonts::registered).contains("Inter Bold"), "Inter's faces are registered");
        fx(() -> {
            headerMenu().show();
            assertInter(showingMenu(), "header menu");
            closeMenus();
            assertInter(rightClick(viewport()), "image menu");
            closeMenus();
            return null;
        });
    }

    @Test
    void oneRightClickMenuAtATimeAndThePressThatClosesItStillLands() throws Exception {
        fx(() -> {
            rightClick(viewport());
            rightClick(viewport());
            assertEquals(1, showingMenus().size(), "a second right-click replaces the first menu");
            int[] pressed = {0};
            var count = (javafx.event.EventHandler<MouseEvent>) e -> pressed[0]++;
            viewport().addEventHandler(MouseEvent.MOUSE_PRESSED, count);
            Event.fireEvent(viewport(), press(viewport(), MouseButton.PRIMARY));
            viewport().removeEventHandler(MouseEvent.MOUSE_PRESSED, count);
            assertTrue(showingMenus().isEmpty(), "a press inside the menu's own control closes it");
            assertEquals(1, pressed[0], "and is not consumed");
            return null;
        });
    }

    @Test
    void theMenuKeyAndShiftF10OpenTheImagesMenu() throws Exception {
        fx(() -> {
            for (KeyEvent key : List.of(key(KeyCode.CONTEXT_MENU, false), key(KeyCode.F10, true))) {
                Event.fireEvent(stage.getScene().getRoot(), key);
                List<ContextMenu> menus = showingMenus();
                assertEquals(1, menus.size(), key.getCode().toString());
                Bounds image = viewport().localToScreen(viewport().getBoundsInLocal());
                assertEquals(image.getCenterX(), menus.getFirst().getAnchorX(), 1);
                // At the middle of the image; a menu too tall for the screen below it is moved up.
                double y = menus.getFirst().getAnchorY();
                assertTrue(y <= image.getCenterY() + 1 && y > image.getMinY(), "anchored at " + y);
                closeMenus();
            }
            return null;
        });
    }

    @Test
    void theMenuKeyOpensTheSelectedBookmarksMenuUnderItsRow() throws Exception {
        Path picture = Files.createFile(config.resolve("photo.jpg"));
        Bookmarks bookmarks = new Bookmarks(config.resolve("bookmarks-menu-key.properties"));
        bookmarks.load();
        bookmarks.toggle(picture, false);
        fx(() -> {
            BookmarksWindow marks = new BookmarksWindow(
                    bookmarks, (p, f) -> {}, p -> {}, c -> {}, c -> null, ViewerWindow.stylesheet());
            marks.show(stage);
            Window shown = Window.getWindows().stream()
                    .filter(w -> w.getScene() != null && w.getScene().lookup(".bookmarks-window") != null)
                    .findFirst()
                    .orElseThrow();
            shown.setX(0);
            shown.setY(0);
            Scene scene = shown.getScene();
            scene.getRoot().applyCss();
            scene.getRoot().layout();
            ListView<?> list = (ListView<?>) scene.lookup(".bookmarks-list");
            list.getSelectionModel().select(list.getItems().size() - 1);
            Event.fireEvent(
                    scene.getFocusOwner() == null ? scene.getRoot() : scene.getFocusOwner(), key(KeyCode.F10, true));
            List<ContextMenu> menus = showingMenus();
            assertEquals(1, menus.size());
            Bounds row = Popups.selectedRow(list);
            assertNotNull(row);
            assertEquals(row.getMaxY(), menus.getFirst().getAnchorY(), 1, "under the selected row");
            List<String> blank = new ArrayList<>();
            unglyphed("bookmark's menu", menus.getFirst().getItems(), blank);
            assertTrue(blank.isEmpty(), blank.toString());
            assertInter(menus.getFirst(), "bookmark's menu");
            closeMenus();
            shown.hide();
            return null;
        });
    }

    @Test
    void everyIconButtonIsNamedAndWearsItsCommandsGlyph() throws Exception {
        fx(() -> {
            List<Control> buttons = new ArrayList<>();
            OnScreenControls controls = new OnScreenControls(new StackPane(), c -> {});
            buttons.addAll(controls.buttons());
            for (Node node : stage.getScene().getRoot().lookupAll(".header-button")) buttons.add((Control) node);
            assertTrue(buttons.size() >= 7, "found " + buttons.size());
            for (Control button : buttons) {
                String name = button.getAccessibleText();
                assertNotNull(name, button.toString());
                assertNotNull(button.getTooltip(), name);
                Command command = byTitle(name);
                if (command == null) {
                    assertEquals(name, button.getTooltip().getText());
                    continue;
                }
                // TBAR-03: built from the title and the chord, never typed.
                assertEquals(Icons.tooltip(command), button.getTooltip().getText(), name);
                // ICON-20: the glyph the menus give the command.
                if (button instanceof ButtonBase labeled && !(button instanceof MenuButton)) {
                    SVGPath glyph = (SVGPath) labeled.getGraphic().lookup(".icon");
                    assertEquals(Icons.forCommand(command), glyph.getContent(), name);
                }
            }
            return null;
        });
    }

    private static Command byTitle(String title) {
        for (Command command : Command.values()) if (Icons.title(command).equals(title)) return command;
        return null;
    }

    private static void unglyphed(String where, List<MenuItem> items, List<String> blank) {
        for (MenuItem item : items) {
            if (item instanceof SeparatorMenuItem || item instanceof RadioMenuItem) continue;
            if (item.getGraphic() == null && !(item instanceof CheckMenuItem)) blank.add(where + ": " + item.getText());
            if (item instanceof Menu menu) unglyphed(where + " > " + menu.getText(), menu.getItems(), blank);
        }
    }

    private static void assertInter(ContextMenu menu, String where) {
        Node content = menu.getSkin().getNode();
        content.applyCss();
        int rows = 0;
        for (Node node : content.lookupAll(".label")) {
            if (!(node instanceof Label label)
                    || label.getText() == null
                    || label.getText().isEmpty()) continue;
            Font font = label.getFont();
            assertEquals(Fonts.FAMILY, font.getFamily(), where + ": " + label.getText());
            double size = label.getStyleClass().contains("accelerator-text") ? 11 : 14;
            assertEquals(size, font.getSize(), where + ": " + label.getText());
            rows++;
        }
        assertTrue(rows > 3, where + " has rows");
    }

    private static MenuButton headerMenu() {
        for (Node node : stage.getScene().getRoot().lookupAll(".header-button")) {
            if (node instanceof MenuButton menu) return menu;
        }
        throw new AssertionError("no header menu");
    }

    private static Node viewport() {
        return stage.getScene().getRoot().lookup(".viewport");
    }

    private static ContextMenu rightClick(Node target) {
        Bounds b = target.localToScreen(target.getBoundsInLocal());
        Event.fireEvent(target, press(target, MouseButton.SECONDARY));
        Event.fireEvent(
                target,
                new ContextMenuEvent(
                        ContextMenuEvent.CONTEXT_MENU_REQUESTED,
                        b.getWidth() / 2,
                        b.getHeight() / 2,
                        b.getCenterX(),
                        b.getCenterY(),
                        false,
                        null));
        return showingMenu();
    }

    private static MouseEvent press(Node target, MouseButton button) {
        Bounds b = target.localToScreen(target.getBoundsInLocal());
        return new MouseEvent(
                MouseEvent.MOUSE_PRESSED,
                b.getWidth() / 3,
                b.getHeight() / 3,
                b.getMinX() + b.getWidth() / 3,
                b.getMinY() + b.getHeight() / 3,
                button,
                1,
                false,
                false,
                false,
                false,
                button == MouseButton.PRIMARY,
                false,
                button == MouseButton.SECONDARY,
                true,
                button == MouseButton.SECONDARY,
                true,
                null);
    }

    private static KeyEvent key(KeyCode code, boolean shift) {
        return new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, shift, false, false, false);
    }

    private static List<ContextMenu> showingMenus() {
        return Window.getWindows().stream()
                .filter(w -> w instanceof ContextMenu m && m.isShowing())
                .map(w -> (ContextMenu) w)
                .toList();
    }

    private static ContextMenu showingMenu() {
        List<ContextMenu> menus = showingMenus();
        assertEquals(
                1,
                menus.size(),
                () -> "menus showing: "
                        + menus.stream()
                                .map(m -> m.getOwnerNode() + " " + m.getItems().size() + " items, first "
                                        + (m.getItems().isEmpty()
                                                ? "-"
                                                : m.getItems().getFirst().getText()))
                                .toList());
        return menus.getFirst();
    }

    private static void closeMenus() {
        showingMenus().forEach(ContextMenu::hide);
        assertFalse(showingMenus().size() > 0);
    }

    private static <T> T fx(Callable<T> task) throws Exception {
        CompletableFuture<T> done = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                done.complete(task.call());
            } catch (Throwable e) {
                done.completeExceptionally(e);
            }
        });
        try {
            return done.get(20, TimeUnit.SECONDS);
        } catch (java.util.concurrent.ExecutionException e) {
            if (e.getCause() instanceof Exception cause) throw cause;
            if (e.getCause() instanceof Error error) throw error;
            throw e;
        }
    }
}
