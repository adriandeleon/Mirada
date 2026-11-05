package com.mirada.ui;

import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.SVGPath;

import org.jspecify.annotations.Nullable;

/**
 * The window's symbolic icons, drawn here as stroked paths on a 24-unit grid: two-pixel strokes,
 * round caps, no fill, in the style of GNOME's symbolic icons. Their colour is CSS's ({@code
 * .icon} in mirada.css: {@code -fx-stroke}), so they follow the theme.
 *
 * <p>Hand-drawn rather than copied from an icon set, so nothing here carries a licence of its own.
 * Path data is never wrapped across source lines: Termina's notes record that a whitespace-aware
 * wrap once joined two numbers and the glyph drew distorted, with no error anywhere.
 */
final class Icons {

    static final String FULL_SCREEN = "M15 3 H21 V9 M9 21 H3 V15 M21 3 L14 10 M3 21 L10 14";
    static final String LEAVE_FULL_SCREEN = "M4 14 H10 V20 M20 10 H14 V4 M14 10 L21 3 M3 21 L10 14";
    static final String ROTATE = "M20 4 V9 H15 M20 9 A8.5 8.5 0 1 0 20.5 14";
    static final String INFO = "M12 3 A9 9 0 1 1 11.99 3 Z M12 11 V16.5 M12 7.5 V7.6";
    static final String MENU = "M4 6.5 H20 M4 12 H20 M4 17.5 H20";
    static final String PREVIOUS = "M15 18 L9 12 L15 6";
    static final String NEXT = "M9 18 L15 12 L9 6";
    static final String CHEVRON_DOWN = "M7 10 L12 15 L17 10";
    static final String ZOOM_IN =
            "M10.5 3.5 A7 7 0 1 1 10.49 3.5 Z M20.5 20.5 L15.5 15.5 M10.5 7.5 V13.5 M7.5 10.5 H13.5";
    static final String ZOOM_FIT = "M10.5 3.5 A7 7 0 1 1 10.49 3.5 Z M20.5 20.5 L15.5 15.5 M7.5 10.5 H13.5";
    static final String FOLDER_OPEN = "M3 18 V6 H9 L11 8 H18 V10.5 M3 18 L6 10.5 H21.5 L18.5 18 Z";
    static final String ROTATE_LEFT = "M4 4 V9 H9 M4 9 A8.5 8.5 0 1 1 3.5 14";
    static final String ZOOM_OUT = ZOOM_FIT;
    static final String FIT = "M4 9 V4 H9 M15 4 H20 V9 M20 15 V20 H15 M9 20 H4 V15";
    static final String ACTUAL_SIZE = "M5 8 L7.5 6 V18 M15 8 L17.5 6 V18 M11.5 10 V10.1 M11.5 14 V14.1";
    static final String FIRST = "M17 18 L11 12 L17 6 M7 6 V18";
    static final String LAST = "M7 18 L13 12 L7 6 M17 6 V18";
    static final String TRASH = "M4 7 H20 M9 7 V4 H15 V7 M6 7 L7 20 H17 L18 7 M10 11 V16 M14 11 V16";
    static final String UNDO = "M9 14 L4 9 L9 4 M4 9 H15 A5 5 0 0 1 15 19 H11";
    static final String RENAME = "M4 20 L4.5 16 L16 4.5 L19.5 8 L8 19.5 Z M13.5 7 L17 10.5";
    static final String COPY = "M8 8 H20 V20 H8 Z M16 8 V4 H4 V16 H8";
    static final String CROP = "M7 3 V17 H21 M3 7 H17 V21";
    static final String PLAY = "M7 4 L19 12 L7 20 Z";
    static final String STRIP = "M3 6 H21 V18 H3 Z M9 6 V18 M15 6 V18";
    static final String SLIDERS = "M4 7 H20 M4 17 H20 M9 4 V10 M15 14 V20";
    static final String KEYBOARD = "M3 6 H21 V18 H3 Z M7 10 H7.1 M11 10 H11.1 M15 10 H15.1 M8 14.5 H16";
    static final String CLOSE = "M6 6 L18 18 M18 6 L6 18";
    static final String SEARCH = "M10.5 3.5 A7 7 0 1 1 10.49 3.5 Z M20.5 20.5 L15.5 15.5";
    static final String PROMPT = "M4 7 L9 12 L4 17 M12 17 H20";
    static final String FOLDER = "M3 6 H9 L11 8 H21 V19 H3 Z";
    static final String LOCK = "M6 11 H18 V20 H6 Z M8.5 11 V8 A3.5 3.5 0 0 1 15.5 8 V11";
    static final String SORT = "M7 4 V20 M4 17 L7 20 L10 17 M13 6 H20 M13 12 H18 M13 18 H16";
    static final String SIDEBAR = "M3 5 H21 V19 H3 Z M9 5 V19";
    static final String CAPTION = "M4 4 H20 V15 H4 Z M7 19.5 H17";
    static final String CLOCK = "M12 3 A9 9 0 1 1 11.99 3 Z M12 7 V12 L15.5 14";
    static final String LIST = "M8 6 H20 M8 12 H20 M8 18 H20 M4 6 H4.1 M4 12 H4.1 M4 18 H4.1";

    private Icons() {}

    /** The icon a menu shows beside {@code command}, or null for none (the order's choices, a tick's). */
    static @Nullable String forCommand(Command command) {
        return switch (command) {
            case NEXT -> NEXT;
            case PREVIOUS -> PREVIOUS;
            case FIRST -> FIRST;
            case LAST -> LAST;
            case GO_TO_IMAGE -> SEARCH;
            case SLIDESHOW -> PLAY;
            case ZOOM_IN -> ZOOM_IN;
            case ZOOM_OUT -> ZOOM_OUT;
            case FIT -> FIT;
            case ACTUAL_SIZE -> ACTUAL_SIZE;
            case KEEP_ZOOM -> LOCK;
            case ROTATE_CLOCKWISE -> ROTATE;
            case ROTATE_COUNTERCLOCKWISE -> ROTATE_LEFT;
            case FULL_SCREEN -> FULL_SCREEN;
            case EDIT -> CROP;
            case RENAME -> RENAME;
            case MOVE_TO_TRASH -> TRASH;
            case UNDO_TRASH -> UNDO;
            case COPY -> COPY;
            case SHOW_IN_FOLDER -> FOLDER;
            case INFO, ABOUT -> INFO;
            case THUMBNAILS -> STRIP;
            case THUMBNAILS_AT_SIDE -> SIDEBAR;
            case THUMBNAIL_NAMES -> LIST;
            case THUMBNAIL_CAPTIONS -> CAPTION;
            case SETTINGS -> SLIDERS;
            case SHORTCUTS -> KEYBOARD;
            case OPEN -> FOLDER_OPEN;
            case OPEN_RECENT -> CLOCK;
            case CLOSE -> CLOSE;
            case PALETTE -> PROMPT;
            default -> null;
        };
    }

    /** {@link #forCommand}'s icon as a menu item's graphic, or null for none. */
    static @Nullable Node menu(Command command) {
        String path = forCommand(command);
        return path == null ? null : of(path);
    }

    /** A 16-pixel icon from one of the paths above, centred in a box of its own size. */
    static Node of(String path) {
        SVGPath glyph = new SVGPath();
        glyph.setContent(path);
        glyph.getStyleClass().add("icon");
        // The grid is 24 units; scaled to 16 px it matches GNOME's symbolic icon size.
        glyph.setScaleX(16 / 24.0);
        glyph.setScaleY(16 / 24.0);
        StackPane box = new StackPane(glyph);
        box.setMinSize(16, 16);
        box.setPrefSize(16, 16);
        box.setMaxSize(16, 16);
        return box;
    }
}
