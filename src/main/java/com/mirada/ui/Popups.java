package com.mirada.ui;

import java.util.List;

import javafx.animation.PauseTransition;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.IndexedCell;
import javafx.scene.control.ListView;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.stage.Window;
import javafx.util.Duration;

import org.jspecify.annotations.Nullable;

/**
 * Closes a window's menus on any press outside them, and when the window loses focus.
 *
 * <p>JavaFX's auto-hide does this through a GTK pointer grab, which XWayland honours only some of
 * the time: a click on the image could leave the menu up, and each right-click then added another.
 * Mirada's own menus leave it off ({@link #closedByWindow}) and the window closes them instead. A
 * press in the window's scene is outside every menu (a menu is a window of its own, so a press on
 * one never reaches this scene), so it closes them and goes on to do what it does (Pauta MENU-07):
 * a click on a thumbnail still shows that image, and a right-click opens a fresh menu where it was
 * pressed, the request being its own event. The menu buttons' menus keep auto-hide, and are closed
 * here too when it fails. One menu at a time (MENU-06): {@link #show} closes the others first, for a
 * menu the keyboard opens too.
 *
 * <p>The keyboard's right-click is the Menu key or Shift+F10 (MENU-09, {@link #isMenuKey}): the
 * window's key filter opens the menu of what is selected, anchored to it. The platform's own request
 * for those keys (GTK and Windows send one, the Headless platform none) is dropped ({@link
 * #menusByKey}), so a menu never opens twice or at the focused text field.
 *
 * <p>A press on the header's empty part never reaches the scene (the window manager takes it, to
 * move the window), nor does one in another application; both take the focus, and losing it closes
 * the menus. Focus is checked again a moment after it goes: a menu button's grab can take it and
 * give it back as the menu opens, which would close the menu as it appears.
 */
final class Popups {

    private static final Duration FOCUS_SETTLES = Duration.millis(150);

    private Popups() {}

    static void closeOnPressOutside(Window window, Scene scene) {
        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> closeMenus(window));
        PauseTransition settle = new PauseTransition(FOCUS_SETTLES);
        settle.setOnFinished(e -> {
            if (!window.isFocused()) closeMenus(window);
        });
        window.focusedProperty().addListener((o, was, now) -> {
            if (!now) settle.playFromStart();
        });
    }

    /**
     * Leaves {@code menu}'s closing to the window ({@link #closeOnPressOutside}), without auto-hide
     * and its pointer grab. Escape still closes it (JavaFX's hide-on-escape does not need auto-hide),
     * and so does choosing an item.
     */
    static void closedByWindow(ContextMenu menu) {
        menu.setAutoHide(false);
    }

    /**
     * Hides every menu showing over {@code owner}: its context menus and the ones its menu buttons
     * drop down. A submenu is owned by its parent menu and goes with it. Whether any was showing.
     */
    static boolean closeMenus(Window owner) {
        List<ContextMenu> showing = Window.getWindows().stream()
                .filter(w -> w instanceof ContextMenu m && m.isShowing() && m.getOwnerWindow() == owner)
                .map(w -> (ContextMenu) w)
                .toList();
        showing.forEach(ContextMenu::hide);
        return !showing.isEmpty();
    }

    /** Shows {@code menu} at a point on the screen, after closing every other menu over the window. */
    static void show(ContextMenu menu, Node anchor, double screenX, double screenY) {
        Scene scene = anchor.getScene();
        Window window = scene == null ? null : scene.getWindow();
        if (window != null) closeMenus(window);
        menu.show(anchor, screenX, screenY);
    }

    /** The Menu key, or Shift+F10 with nothing else held: the keyboard's right-click. */
    static boolean isMenuKey(KeyCode code, boolean shift, boolean shortcut, boolean alt) {
        if (shortcut || alt) return false;
        return code == KeyCode.CONTEXT_MENU && !shift || code == KeyCode.F10 && shift;
    }

    static boolean isMenuKey(KeyEvent e) {
        return isMenuKey(e.getCode(), e.isShiftDown(), e.isShortcutDown() || e.isControlDown(), e.isAltDown());
    }

    /**
     * Drops the platform's keyboard requests for a menu in {@code scene}: its key filter opens them
     * from {@link #isMenuKey} itself. A right-click's request is left alone.
     */
    static void menusByKey(Scene scene) {
        scene.addEventFilter(ContextMenuEvent.CONTEXT_MENU_REQUESTED, e -> {
            if (e.isKeyboardTrigger()) e.consume();
        });
    }

    /**
     * Where a menu opened from the keyboard on {@code row} stands: below the row, a little in from
     * its left edge, never past a narrow one. Editora's {@code RowContextMenu}.
     */
    static Point2D under(Bounds row) {
        return new Point2D(row.getMinX() + Math.max(0, Math.min(row.getWidth() / 4, 48)), row.getMaxY());
    }

    /**
     * The bounds on screen of {@code list}'s selected row, scrolled into view first, or null when no
     * row is selected or it has no cell.
     */
    static @Nullable Bounds selectedRow(ListView<?> list) {
        int index = list.getSelectionModel().getSelectedIndex();
        if (index < 0) return null;
        IndexedCell<?> cell = cell(list, index);
        if (cell == null) {
            list.scrollTo(index);
            list.applyCss();
            list.layout();
            cell = cell(list, index);
        }
        return cell == null ? null : cell.localToScreen(cell.getBoundsInLocal());
    }

    private static @Nullable IndexedCell<?> cell(ListView<?> list, int index) {
        for (Node node : list.lookupAll(".list-cell")) {
            if (node instanceof IndexedCell<?> cell && cell.getIndex() == index && !cell.isEmpty() && cell.isVisible())
                return cell;
        }
        return null;
    }
}
