package com.mirada.ui;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Control;
import javafx.scene.control.Labeled;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.SVGPath;

import org.jspecify.annotations.Nullable;

/**
 * The window's symbolic icons, drawn here as stroked paths on a 24-unit grid: two-pixel strokes,
 * round caps, no fill, in the style of GNOME's symbolic icons. Their colour is CSS's ({@code
 * .icon} in mirada.css: {@code -fx-stroke}), so they follow the theme.
 *
 * <p>One action, one glyph, wherever it is reached (Pauta ICON-20): a menu row and a button that run
 * a command both take {@link #forCommand}'s, and a command with no fitting glyph gets one of its own
 * here rather than another's (MENU-14). Every icon-only button is named by {@link #name}, which sets
 * its tooltip and what a screen reader says together, the tooltip built from the command's title
 * and its chord now, never typed (ICON-21, KBD-17, TBAR-01, TBAR-03).
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
    /** A question mark in a circle: the Help submenu, which runs no command of its own (MENU-14). */
    static final String HELP =
            "M12 3 A9 9 0 1 1 11.99 3 Z M9.5 9.5 Q9.5 7 12 7 Q14.5 7 14.5 9.3 Q14.5 11 12 12 V13.5 M12 16.5 V16.6";

    static final String MENU = "M4 6.5 H20 M4 12 H20 M4 17.5 H20";
    static final String PREVIOUS = "M15 18 L9 12 L15 6";
    static final String NEXT = "M9 18 L15 12 L9 6";
    /** Previous and next with the thumbnails down the side, where the folder runs top to bottom. */
    static final String UP = "M18 15 L12 9 L6 15";

    static final String DOWN = "M6 9 L12 15 L18 9";
    static final String CHEVRON_DOWN = "M7 10 L12 15 L17 10";
    static final String ZOOM_IN =
            "M10.5 3.5 A7 7 0 1 1 10.49 3.5 Z M20.5 20.5 L15.5 15.5 M10.5 7.5 V13.5 M7.5 10.5 H13.5";
    static final String FOLDER_OPEN = "M3 18 V6 H9 L11 8 H18 V10.5 M3 18 L6 10.5 H21.5 L18.5 18 Z";
    static final String ROTATE_LEFT = "M4 4 V9 H9 M4 9 A8.5 8.5 0 1 1 3.5 14";
    static final String ZOOM_OUT = "M10.5 3.5 A7 7 0 1 1 10.49 3.5 Z M20.5 20.5 L15.5 15.5 M7.5 10.5 H13.5";
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
    static final String PICTURE =
            "M3 5 H21 V19 H3 Z M3 16 L8.5 11 L13 15.5 M12 14.5 L15.5 11.5 L21 16.5 M15.5 8.5 H15.6";
    static final String FOLDER = "M3 6 H9 L11 8 H21 V19 H3 Z";
    static final String LOCK = "M6 11 H18 V20 H6 Z M8.5 11 V8 A3.5 3.5 0 0 1 15.5 8 V11";
    static final String SORT = "M7 4 V20 M4 17 L7 20 L10 17 M13 6 H20 M13 12 H18 M13 18 H16";
    static final String SIDEBAR = "M3 5 H21 V19 H3 Z M9 5 V19";
    static final String CAPTION = "M4 4 H20 V15 H4 Z M7 19.5 H17";
    static final String CLOCK = "M12 3 A9 9 0 1 1 11.99 3 Z M12 7 V12 L15.5 14";
    static final String LIST = "M8 6 H20 M8 12 H20 M8 18 H20 M4 6 H4.1 M4 12 H4.1 M4 18 H4.1";
    static final String BOOK =
            "M12 6.5 Q8 4 3.5 5 V18.5 Q8 17.5 12 20 Q16 17.5 20.5 18.5 V5 Q16 4 12 6.5 Z M12 6.5 V20";

    static final String STATUS_BAR = "M3 5 H21 V19 H3 Z M3 15.5 H21 M6 17.5 H11";
    static final String MESSAGES = "M4 5 H20 V16 H10 L6 20 V16 H4 Z M8 9 H16 M8 12 H13";
    static final String LOG = "M6 3 H18 V21 H6 Z M9 8 H15 M9 12 H15 M9 16 H13";
    static final String DOWNLOAD = "M12 4 V15 M7 10 L12 15 L17 10 M5 20 H19";
    static final String UPLOAD = "M12 15 V4 M7 9 L12 4 L17 9 M5 20 H19";
    /** A circle half filled: light and dark. */
    static final String THEME = "M12 3 A9 9 0 1 1 11.99 3 Z M12 3 V21 M12 7 H15 M12 11 H17 M12 15 H16";

    static final String WARNING = "M12 3.5 L21.5 20 H2.5 Z M12 9.5 V14 M12 17 V17.1";
    static final String UPDATE = "M12 3 A9 9 0 1 1 11.99 3 Z M12 16.5 V8 M8.5 11.5 L12 8 L15.5 11.5";

    static final String BOOKMARK = "M6.5 3.5 H17.5 V20.5 L12 16 L6.5 20.5 Z";
    static final String NOTE = "M5 4 H19 V14 L14 20 H5 Z M14 20 V14 H19 M8.5 8.5 H15.5 M8.5 12 H12.5";
    static final String NEXT_BOOKMARK = "M4 3.5 H12 V17.5 L8 14 L4 17.5 Z M15 18 L20 12 L15 6";
    static final String PREVIOUS_BOOKMARK = "M12 3.5 H20 V17.5 L16 14 L12 17.5 Z M9 18 L4 12 L9 6";
    /** Every bookmark and note: a bookmark beside the lines of a list. */
    static final String BOOKMARKS = "M4 3.5 H10.5 V14 L7.25 11.5 L4 14 Z M14 6 H20 M14 10.5 H20 M4 18.5 H20";
    /** A mark taken off: the bookmark with a stroke through it, not the trash a file goes to. */
    static final String REMOVE_MARK = "M6.5 3.5 H17.5 V20.5 L12 16 L6.5 20.5 Z M9.5 10 H14.5";
    /** About Mirada: an eye, the app's name ("a look"). */
    static final String ABOUT = "M2.5 12 Q12 3.5 21.5 12 Q12 20.5 2.5 12 Z M12 9.25 A2.75 2.75 0 1 1 11.99 9.25 Z";

    /** Whether the window fills the screen: full screen's glyph is then the way out. */
    private static boolean fullScreen;

    private Icons() {}

    /** Full screen's glyph from now on: the way in, or out while {@code value}. */
    static void setFullScreen(boolean value) {
        fullScreen = value;
    }

    /** The icon a menu shows beside {@code command}, or null for none (the order's choices, a tick's). */
    static @Nullable String forCommand(Command command) {
        return switch (command) {
            case NEXT -> Command.vertical() ? DOWN : NEXT;
            case PREVIOUS -> Command.vertical() ? UP : PREVIOUS;
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
            case FULL_SCREEN -> fullScreen ? LEAVE_FULL_SCREEN : FULL_SCREEN;
            case EDIT -> CROP;
            case RENAME -> RENAME;
            case MOVE_TO_TRASH -> TRASH;
            case UNDO_TRASH -> UNDO;
            case COPY -> COPY;
            case SHOW_IN_FOLDER -> FOLDER;
            case BOOKMARK, BOOKMARK_FOLDER, GO_TO_BOOKMARK -> BOOKMARK;
            case BOOKMARKS -> BOOKMARKS;
            case NOTE, FOLDER_NOTE -> NOTE;
            case NEXT_BOOKMARK -> NEXT_BOOKMARK;
            case PREVIOUS_BOOKMARK -> PREVIOUS_BOOKMARK;
            case INFO -> INFO;
            case ABOUT -> ABOUT;
            case THUMBNAILS -> STRIP;
            case THUMBNAILS_AT_SIDE -> SIDEBAR;
            case THUMBNAIL_NAMES -> LIST;
            case THUMBNAIL_CAPTIONS -> CAPTION;
            case SETTINGS -> SLIDERS;
            case THEME -> THEME;
            case KEYMAP -> KEYBOARD;
            case THUMBNAILS_AT_START -> STRIP;
            case PROPERTIES_AT_START -> INFO;
            case HIDE_CONTROLS_AFTER -> CLOCK;
            case SLIDESHOW_SPEED -> PLAY;
            case PALETTE_KEYS -> PROMPT;
            case CHECK_DAILY -> UPDATE;
            case EXPORT_CONFIG -> UPLOAD;
            case IMPORT_CONFIG -> DOWNLOAD;
            case SHORTCUTS -> KEYBOARD;
            case OPEN -> PICTURE;
            case OPEN_FOLDER -> FOLDER_OPEN;
            case OPEN_RECENT -> CLOCK;
            case CLOSE -> CLOSE;
            case PALETTE -> PROMPT;
            case DOCUMENTATION -> BOOK;
            case STATUS_BAR -> STATUS_BAR;
            case MESSAGE_LOG -> MESSAGES;
            case DEBUG_LOG -> LOG;
            case COPY_MESSAGES, COPY_DEBUG_LOG, COPY_DETAILS -> COPY;
            case CLEAR_MESSAGES, CLEAR_DEBUG_LOG -> TRASH;
            case EXPORT_DEBUG_LOG, OPEN_RELEASE -> DOWNLOAD;
            case REPORT_PROBLEM -> WARNING;
            case CHECK_FOR_UPDATES -> UPDATE;
            default -> null;
        };
    }

    /** {@link #forCommand}'s icon as a menu item's graphic, or null for none. */
    static @Nullable Node menu(Command command) {
        String path = forCommand(command);
        return path == null ? null : of(path);
    }

    /**
     * An icon-only button that runs {@code command}: its glyph, its name ({@link #name}), flat, in
     * the given style class, and out of the Tab order, as the keyboard has the command already
     * (TBAR-02).
     */
    static Button button(Command command, String styleClass, Runnable action) {
        String path = forCommand(command);
        Button button = new Button();
        button.setGraphic(of(path == null ? MENU : path));
        button.getStyleClass().addAll(styleClass, "flat");
        button.setFocusTraversable(false);
        button.setOnAction(e -> action.run());
        return name(button, command);
    }

    /**
     * Gives an icon-only button {@code command}'s glyph again, and its name: after the strip's axis
     * turned the arrows, or full screen changed its way.
     */
    static <T extends Control> T relabel(T control, Command command) {
        String path = forCommand(command);
        if (path != null && control instanceof Labeled labeled) labeled.setGraphic(of(path));
        return name(control, command);
    }

    /** {@link #name} by {@code command}'s title and chord now. */
    static <T extends Control> T name(T control, Command command) {
        return name(control, title(command), command.chord());
    }

    /**
     * Names an icon-only control: {@code title} is what a screen reader says, and with {@code
     * chord}, if any, its tooltip, "Rotate Right (R)". Called again, it renames it in place.
     */
    static <T extends Control> T name(T control, String title, KeyCombination chord) {
        String tip = tooltip(title, chord);
        Tooltip tooltip = control.getTooltip();
        if (tooltip == null) control.setTooltip(new Tooltip(tip));
        else tooltip.setText(tip);
        control.setAccessibleText(title);
        return control;
    }

    /** "Title (chord)", or the title alone when there is no chord: what a tooltip says (TBAR-03). */
    static String tooltip(String title, KeyCombination chord) {
        return chord == KeyCombination.NO_MATCH ? title : title + " (" + chord.getDisplayText() + ")";
    }

    /** {@code command}'s tooltip, by its title and chord now. */
    static String tooltip(Command command) {
        return tooltip(title(command), command.chord());
    }

    /** A command's title now, without the "…" a menu row gives one that asks something first. */
    static String title(Command command) {
        String label = command == Command.FULL_SCREEN && fullScreen ? "Leave Full Screen" : command.label();
        return label.endsWith("…") ? label.substring(0, label.length() - 1) : label;
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
