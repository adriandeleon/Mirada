package com.mirada.ui;

import java.nio.file.Path;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javafx.animation.PauseTransition;
import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import com.mirada.diagnostics.MessageLog;
import com.mirada.settings.Settings;
import com.mirada.update.UpdateCoordinator;
import com.mirada.update.UpdateService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
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
 * The chrome over and around the image: the floating controls run their commands and fade when the
 * pointer leaves, the status bar's parts open the Message Log or the release, a press outside a
 * menu closes it without reaching what is under it, the shortcuts window follows the palette's keys.
 */
@Tag("fx")
class ChromeTest {

    @TempDir
    Path dir;

    private final List<Command> ran = new ArrayList<>();

    @BeforeAll
    static void toolkit() throws Exception {
        FxTestSupport.start();
    }

    @AfterEach
    void close() throws Exception {
        FxToolkit.cleanupStages();
    }

    private static Stage shown(javafx.scene.Parent root) throws Exception {
        return call(() -> {
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 600, 400));
            stage.setX(0);
            stage.setY(0);
            stage.show();
            return stage;
        });
    }

    private static MouseEvent mouse(javafx.event.EventType<MouseEvent> type) {
        return new MouseEvent(
                type,
                50,
                50,
                50,
                50,
                MouseButton.PRIMARY,
                1,
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

    @Test
    void theFloatingControlsRunTheirCommandsAndFadeWhenThePointerLeaves() throws Exception {
        StackPane canvas = new StackPane();
        shown(canvas);
        OnScreenControls controls = call(() -> new OnScreenControls(canvas, ran::add));
        fx(() -> controls.update(1, 3, true, true));
        for (String field : List.of("previous", "next", "zoom", "fullScreen")) {
            Button button = FxTestSupport.field(controls, field);
            fx(button::fire);
        }
        fx(() -> controls.update(1, 3, true, false));
        fx(((Button) FxTestSupport.field(controls, "zoom"))::fire);
        assertEquals(
                List.of(Command.PREVIOUS, Command.NEXT, Command.ACTUAL_SIZE, Command.FULL_SCREEN, Command.FIT),
                ran,
                "the zoom button: actual size when fitted, fitted otherwise");
        MenuButton zoomMenu = FxTestSupport.field(controls, "zoomMenu");
        ran.clear();
        for (MenuItem item : zoomMenu.getItems()) fx(item::fire);
        assertEquals(List.of(Command.FIT, Command.ACTUAL_SIZE, Command.ZOOM_IN, Command.ZOOM_OUT), ran);

        Node navigation = FxTestSupport.field(controls, "navigation");
        fx(() -> canvas.fireEvent(mouse(MouseEvent.MOUSE_MOVED)));
        assertTrue(call(navigation::isVisible), "woken by the pointer");
        fx(() -> canvas.fireEvent(mouse(MouseEvent.MOUSE_DRAGGED)));
        PauseTransition idle = FxTestSupport.field(controls, "idle");
        fx(() -> idle.getOnFinished().handle(new javafx.event.ActionEvent()));
        fx(() -> canvas.fireEvent(mouse(MouseEvent.MOUSE_EXITED)));
        fx(() -> {
            zoomMenu.show();
            zoomMenu.hide();
        });
        fx(() -> controls.update(0, 0, false, true));
    }

    @Test
    void theStatusBarsPartsOpenTheMessageLogAndTheRelease() throws Exception {
        MessageLog messages = new MessageLog();
        UpdateCoordinator updates = new UpdateCoordinator(
                new Settings(dir.resolve("settings.properties")),
                version -> new UpdateService.Outcome(null, null),
                (delay, task) -> {},
                Runnable::run,
                Runnable::run,
                (text, severity) -> {},
                () -> 0L);
        StatusBar bar = call(() -> new StatusBar(messages, updates, ran::add));
        shown(new StackPane(bar.node()));
        fx(bar::finish);
        fx(() -> messages.add("Couldn't open a.png", MessageLog.Severity.ERROR, LocalTime.NOON));
        for (Node node : call(() -> List.copyOf(((javafx.scene.Parent) bar.node()).getChildrenUnmodifiable()))) {
            if (node instanceof Label label)
                fx(() -> label.getOnMouseClicked().handle(mouse(MouseEvent.MOUSE_CLICKED)));
            if (node instanceof Hyperlink link) fx(link::fire);
        }
        assertEquals(List.of(Command.MESSAGE_LOG, Command.MESSAGE_LOG, Command.OPEN_RELEASE), ran);
    }

    /** MENU-07 (Pauta 4.0.1): the press closes the menu and is not consumed. */
    @Test
    void aPressOutsideAMenuClosesItAndStillLands() throws Exception {
        StackPane root = new StackPane();
        List<MouseEvent> reached = new ArrayList<>();
        root.setOnMousePressed(reached::add);
        Stage stage = shown(root);
        fx(() -> Popups.closeOnPressOutside(stage, stage.getScene()));
        ContextMenu menu = new ContextMenu(new MenuItem("One"));
        fx(() -> {
            Popups.closedByWindow(menu);
            menu.show(root, 10, 10);
        });
        assertTrue(call(menu::isShowing));
        fx(() -> Event.fireEvent(root, mouse(MouseEvent.MOUSE_PRESSED)));
        assertFalse(call(menu::isShowing), "closed by the press");
        assertEquals(1, reached.size(), "and still lands");
        fx(() -> Event.fireEvent(root, mouse(MouseEvent.MOUSE_PRESSED)));
        assertEquals(2, reached.size(), "with no menu up, a press is a press");
        // Closing the window's menus says whether any was up.
        fx(() -> menu.show(root, 10, 10));
        assertTrue(call(() -> Popups.closeMenus(stage)));
        assertFalse(call(() -> Popups.closeMenus(stage)), "none left");
    }

    @Test
    void theShortcutsWindowFollowsThePalettesKeysAndEscapeClosesIt() throws Exception {
        Stage owner = shown(new StackPane());
        ShortcutsWindow window = call(() -> new ShortcutsWindow(ViewerWindow.stylesheet()));
        fx(() -> window.show(owner));
        fx(() -> Keybindings.use(Settings.DEFAULT_KEYMAP, Map.of(), "f9"));
        fx(window::refresh);
        assertTrue(call(() -> window.stage().getScene().getRoot().lookupAll(".label").stream()
                .anyMatch(n -> n instanceof Label l
                        && l.getText() != null
                        && l.getText().contains("F9"))));
        fx(() -> Keybindings.use(Settings.DEFAULT_KEYMAP, Map.of(), Settings.defaultPaletteKeys(Command.MAC)));
        fx(() -> window.show(owner));
        fx(() -> Event.fireEvent(
                window.stage().getScene().getRoot(),
                new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE, false, false, false, false)));
        assertFalse(call(() -> window.stage().isShowing()));
    }

    @Test
    void theStripsMenuOpensOnARightClickAndItsCellsIgnoreOtherButtons() throws Exception {
        List<Integer> picked = new ArrayList<>();
        ThumbnailStrip strip = call(() -> new ThumbnailStrip(picked::add, (source, file, picture) -> {}));
        Stage stage = shown(new StackPane(strip.node()));
        ContextMenu menu = new ContextMenu(new MenuItem("Hide Thumbnails"));
        fx(() -> strip.setMenu(() -> menu));
        fx(() -> strip.setLayout(true, true, false));
        assertTrue(call(strip::atSide));
        fx(() -> strip.show(List.of(dir.resolve("a.png"), dir.resolve("b.png")), 0));
        fx(() -> strip.node()
                .fireEvent(new ContextMenuEvent(ContextMenuEvent.CONTEXT_MENU_REQUESTED, 5, 5, 5, 5, false, null)));
        assertTrue(call(menu::isShowing));
        fx(menu::hide);
        fx(() -> strip.node().layout());
        for (Node cell : call(() -> List.copyOf(strip.node().lookupAll(".list-cell")))) {
            fx(() -> cell.fireEvent(new MouseEvent(
                    MouseEvent.MOUSE_PRESSED,
                    5,
                    5,
                    5,
                    5,
                    MouseButton.SECONDARY,
                    1,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    true,
                    false,
                    false,
                    true,
                    null)));
        }
        assertEquals(List.of(), picked, "a right click picks nothing");
        assertTrue(call(stage::isShowing));
    }
}
