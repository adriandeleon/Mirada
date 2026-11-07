package com.mirada.ui;

import java.util.List;

import javafx.animation.PauseTransition;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.input.MouseEvent;
import javafx.stage.Window;
import javafx.util.Duration;

/**
 * Closes a window's menus on any press outside them, and when the window loses focus.
 *
 * <p>JavaFX's auto-hide does this through a GTK pointer grab, which XWayland honours only some of
 * the time: a click on the image could leave the menu up, and each right-click then added another.
 * Mirada's own menus leave it off ({@link #closedByWindow}) and the window closes them instead. A
 * press in the window's scene is outside every menu (a menu is a window of its own, so a press on
 * one never reaches this scene), so it closes them and is consumed, as auto-hide consumes the press
 * that closes a menu; a right-click still opens a fresh menu where it was pressed, the request being
 * its own event. The menu buttons' menus keep auto-hide, and are closed here too when it fails.
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
        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            if (closeMenus(window)) e.consume();
        });
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
}
