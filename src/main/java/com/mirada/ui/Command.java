package com.mirada.ui;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import javafx.scene.input.KeyCharacterCombination;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;

import org.jspecify.annotations.Nullable;

/**
 * Everything Mirada can do: which keys do it, and how the command palette names and describes it.
 * The key filter, the palette and the context menu all read from here, so a command has one name
 * and one chord wherever it appears.
 */
enum Command {
    NEXT("Next Image", "Shows the next image in the folder.", "go navigate forward", key(KeyCode.RIGHT)),
    PREVIOUS("Previous Image", "Shows the previous image in the folder.", "go navigate back", key(KeyCode.LEFT)),
    FIRST("First Image", "Jumps to the first image in the folder.", "go navigate start", key(KeyCode.HOME)),
    LAST("Last Image", "Jumps to the last image in the folder.", "go navigate end", key(KeyCode.END)),
    GO_TO_IMAGE(
            "Go to Image…",
            "Finds an image in the folder by its name, and shows it.",
            "find search name jump navigate",
            shortcut(KeyCode.P)),
    SLIDESHOW(
            "Slideshow",
            "Shows the folder's images one after another, full screen. Escape stops it.",
            "play presentation",
            key(KeyCode.F5)),
    ZOOM_IN("Zoom In", "Zooms to the next step, about the centre of the window.", "view magnify", text("+")),
    ZOOM_OUT("Zoom Out", "Zooms to the previous step, about the centre of the window.", "view", text("-")),
    FIT("Zoom to Fit", "Shows the whole image, fitted to the window.", "view", key(KeyCode.DIGIT0)),
    KEEP_ZOOM(
            "Keep Zoom When Stepping",
            "Turns on or off keeping the zoom and position when moving to another image.",
            "lock fit compare",
            key(KeyCode.Z)),
    PAN_LEFT("Pan Left", "Moves the view of a zoomed-in image left.", "scroll", shift(KeyCode.LEFT)),
    PAN_RIGHT("Pan Right", "Moves the view of a zoomed-in image right.", "scroll", shift(KeyCode.RIGHT)),
    PAN_UP("Pan Up", "Moves the view of a zoomed-in image up.", "scroll", key(KeyCode.UP)),
    PAN_DOWN("Pan Down", "Moves the view of a zoomed-in image down.", "scroll", key(KeyCode.DOWN)),
    ACTUAL_SIZE(
            "Actual Size",
            "Shows the image at 100 %: one image pixel per screen pixel.",
            "view zoom 100",
            key(KeyCode.DIGIT1)),
    ROTATE_CLOCKWISE(
            "Rotate Right", "Turns the image a quarter clockwise. The file is not changed.", "turn", key(KeyCode.R)),
    ROTATE_COUNTERCLOCKWISE(
            "Rotate Left",
            "Turns the image a quarter counterclockwise. The file is not changed.",
            "turn",
            new KeyCodeCombination(KeyCode.R, KeyCombination.SHIFT_DOWN)),
    FULL_SCREEN("Full Screen", "Fills the screen with the image. Escape leaves.", "view", key(KeyCode.F)),
    EDIT(
            "Edit…",
            "Crops, turns and flips the image, and saves the result as a new file.",
            "crop rotate flip edit save",
            key(KeyCode.E)),
    RENAME("Rename…", "Renames the image's file.", "file name", key(KeyCode.F2)),
    UNDO_TRASH(
            "Undo Move to Trash",
            "Puts the image last moved to the Trash back where it was.",
            "restore put back undelete recover",
            shortcut(KeyCode.Z)),
    COPY(
            "Copy",
            "Copies the image, as it stands on screen, and its file, to paste into another app or a folder.",
            "clipboard",
            shortcut(KeyCode.C)),
    SHOW_IN_FOLDER(
            "Show in Folder",
            "Opens the image's folder in the file manager, with the image selected.",
            "reveal finder explorer files locate open containing",
            none()),
    MOVE_TO_TRASH(
            "Move to Trash",
            "Moves the image to the Trash, from where it can be put back, and shows the next.",
            "delete remove bin recycle trash",
            key(KeyCode.DELETE)),
    SORT_BY_NAME("Sort by Name", "Orders the folder by file name, as a person counts.", "order", none()),
    SORT_BY_DATE(
            "Sort by Date", "Orders the folder by when each file was last changed.", "order modified time", none()),
    SORT_BY_SIZE("Sort by Size", "Orders the folder by file size.", "order", none()),
    REVERSE_ORDER(
            "Reverse Order", "Turns the folder's order the other way round.", "sort descending ascending", none()),
    INFO(
            "Image Properties",
            "Shows the folder, size, format, file dates and camera beside the image.",
            "info details exif metadata sidebar",
            key(KeyCode.I)),
    THUMBNAILS(
            "Thumbnails",
            "Shows the folder's images in a strip along the bottom.",
            "filmstrip strip gallery browse folder",
            key(KeyCode.T)),
    THUMBNAILS_AT_SIDE(
            "Thumbnails at the Side",
            "Stands the thumbnails in a column down the left instead of a row along the bottom.",
            "vertical column filmstrip strip left layout",
            none()),
    THUMBNAIL_NAMES(
            "Names Instead of Thumbnails",
            "Lists the folder by file name in the strip, instead of pictures.",
            "names list text filmstrip strip filenames",
            none()),
    THUMBNAIL_CAPTIONS(
            "Names Under Thumbnails",
            "Writes each image's file name under its thumbnail in the strip.",
            "captions labels titles filmstrip strip filenames",
            none()),
    /** Leaves full screen, or on Linux closes the window: look, then go ({@link #escapeCloses}). */
    ESCAPE(
            "Escape",
            "Stops the slideshow or leaves full screen; on Linux, otherwise closes the window.",
            "",
            key(KeyCode.ESCAPE)),
    SETTINGS(
            "Settings",
            "Theme, what opens at start, and how long the floating controls stay.",
            "preferences options configure",
            shortcut(KeyCode.COMMA)),
    SHORTCUTS(
            "Keyboard Shortcuts",
            "Lists every key Mirada answers to.",
            "keys help",
            new KeyCharacterCombination("?", KeyCombination.SHORTCUT_DOWN)),
    ABOUT(
            "About Mirada",
            "Version, licence, and the runtime and codec versions.",
            "version help credits",
            key(KeyCode.F12)),
    OPEN("Open…", "Opens an image, with its folder, or a folder.", "file", shortcut(KeyCode.O)),
    OPEN_RECENT(
            "Open Recent…",
            "Finds a folder opened lately by its name, and opens it.",
            "recent history folders previous last",
            new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN)),
    CLOSE("Close Window", "Closes Mirada.", "quit exit", shortcut(KeyCode.W)),
    /** Its keys are the user's ({@link PaletteChords}), so {@link #forKey} never answers it. */
    PALETTE("Command Palette", "Finds and runs any command by name.", "", none());

    /** Whether this is macOS, read once: its keys differ ({@link #forKey}, {@link #chord(boolean)}). */
    static final boolean MAC = isMac(System.getProperty("os.name", ""));

    /** Whether this is Windows, read once: its trash is the Recycle Bin ({@link #label(boolean, boolean)}). */
    static final boolean WINDOWS =
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");

    private final String label;
    private final String description;
    private final String keywords;
    private final KeyCombination chord;

    Command(String label, String description, String keywords, KeyCombination chord) {
        this.label = label;
        this.description = description;
        this.keywords = keywords;
        this.chord = chord;
    }

    String label() {
        return label(MAC, WINDOWS);
    }

    /**
     * The label on macOS, Windows or Linux: Windows calls its trash the Recycle Bin, and the file
     * manager is named where it has one name. Testable.
     */
    String label(boolean mac, boolean windows) {
        return switch (this) {
            case MOVE_TO_TRASH -> "Move to " + trash(windows);
            case UNDO_TRASH -> "Undo Move to " + trash(windows);
            case SHOW_IN_FOLDER -> mac ? "Show in Finder" : windows ? "Show in File Explorer" : label;
            default -> label;
        };
    }

    /** What the desktop calls the place a deleted file can be put back from. */
    static String trash(boolean windows) {
        return windows ? "Recycle Bin" : "Trash";
    }

    /** One line, shown under the palette's list for the highlighted command. */
    String description() {
        return this == MOVE_TO_TRASH ? description.replace("Trash", trash(WINDOWS)) : description;
    }

    /** Words the palette also finds the command by, besides its label. */
    String keywords() {
        return keywords;
    }

    /**
     * The chord shown beside the command: its main key, one of those {@link #forKey} accepts, or for
     * the palette the first of the user's ({@link PaletteChords}).
     */
    KeyCombination chord() {
        return chord(MAC);
    }

    /**
     * The chord shown on macOS or elsewhere. A Mac keyboard has no forward Delete, and F2 is a
     * brightness key there, so trashing and renaming show Finder's keys instead: Cmd-Backspace and
     * Return. Package-visible so both platforms are testable.
     */
    KeyCombination chord(boolean mac) {
        if (this == PALETTE) return PaletteChords.current().getFirst();
        if (!mac) return chord;
        return switch (this) {
            case MOVE_TO_TRASH -> shortcut(KeyCode.BACK_SPACE);
            case RENAME -> key(KeyCode.ENTER);
            default -> chord;
        };
    }

    /**
     * Why this command cannot run right now, or null when it can: stepping needs somewhere to step
     * to, and zooming or turning needs an image on screen. Shown as the greyed row's tooltip in the
     * palette.
     */
    @Nullable
    String unavailable(int index, int size, boolean imageShowing) {
        return switch (this) {
            case NEXT, LAST -> size == 0 ? "No folder is open." : index >= size - 1 ? "This is the last image." : null;
            case PREVIOUS, FIRST -> size == 0 ? "No folder is open." : index <= 0 ? "This is the first image." : null;
            case ZOOM_IN,
                    ZOOM_OUT,
                    FIT,
                    ACTUAL_SIZE,
                    ROTATE_CLOCKWISE,
                    ROTATE_COUNTERCLOCKWISE,
                    PAN_LEFT,
                    PAN_RIGHT,
                    PAN_UP,
                    PAN_DOWN,
                    EDIT,
                    RENAME,
                    MOVE_TO_TRASH,
                    COPY,
                    SHOW_IN_FOLDER -> imageShowing ? null : "No image is showing.";
            case SLIDESHOW -> size < 2 ? "A slideshow needs a folder with more than one image." : null;
            case SORT_BY_NAME, SORT_BY_DATE, SORT_BY_SIZE, REVERSE_ORDER, GO_TO_IMAGE ->
                size == 0 ? "No folder is open." : null;
            default -> null;
        };
    }

    /** The commands the palette lists, in the order it lists them before anything is typed. */
    static List<Command> inPalette() {
        return Arrays.stream(values()).filter(Command::listed).toList();
    }

    /** Escape, the palette itself and the panning keys: things nobody looks up by name. */
    private boolean listed() {
        return switch (this) {
            case ESCAPE, PALETTE, PAN_LEFT, PAN_RIGHT, PAN_UP, PAN_DOWN -> false;
            default -> true;
        };
    }

    /** The heading this command comes under in the shortcuts window. */
    String group() {
        return switch (this) {
            case NEXT, PREVIOUS, FIRST, LAST, GO_TO_IMAGE, SLIDESHOW -> "Folder";
            case ZOOM_IN, ZOOM_OUT, FIT, ACTUAL_SIZE, KEEP_ZOOM, PAN_LEFT, PAN_RIGHT, PAN_UP, PAN_DOWN, FULL_SCREEN ->
                "View";
            case ROTATE_CLOCKWISE,
                    ROTATE_COUNTERCLOCKWISE,
                    EDIT,
                    RENAME,
                    MOVE_TO_TRASH,
                    UNDO_TRASH,
                    COPY,
                    SHOW_IN_FOLDER -> "Image";
            case SORT_BY_NAME, SORT_BY_DATE, SORT_BY_SIZE, REVERSE_ORDER -> "Order";
            case INFO, THUMBNAILS, THUMBNAILS_AT_SIDE, THUMBNAIL_NAMES, THUMBNAIL_CAPTIONS -> "Panels";
            case ESCAPE, SETTINGS, SHORTCUTS, ABOUT, OPEN, OPEN_RECENT, CLOSE, PALETTE -> "Mirada";
        };
    }

    /**
     * The command for a key press, or null when the key is not one of Mirada's. {@code shortcut}
     * is Ctrl, or Cmd on macOS; {@code control} is the Ctrl key itself, the same as {@code shortcut}
     * except on a Mac. Navigation and zoom take bare keys: there is no text to type into.
     *
     * <p>macOS adds Preview's and Finder's keys, because its laptops' function keys are media keys
     * and its keyboards' Delete is Backspace: Cmd-Backspace trashes (Backspace alone still steps
     * back, so it never deletes anything), Return renames, Cmd-I shows the properties, Cmd-L turns
     * left and Ctrl-Cmd-F is full screen. Ctrl-R and Ctrl-Shift-R turn on every platform, as in
     * GNOME's viewer and Windows' Photos.
     */
    static @Nullable Command forKey(KeyCode code, boolean shift, boolean shortcut, boolean control, boolean mac) {
        if (mac && shortcut && control) return code == KeyCode.F ? FULL_SCREEN : null;
        if (shortcut) {
            return switch (code) {
                case O -> shift ? OPEN_RECENT : OPEN;
                case C -> shift ? null : COPY;
                case Z -> shift ? null : UNDO_TRASH;
                case W, Q -> CLOSE;
                case P -> shift ? null : GO_TO_IMAGE;
                case R -> shift ? ROTATE_COUNTERCLOCKWISE : ROTATE_CLOCKWISE;
                case L -> mac && !shift ? ROTATE_COUNTERCLOCKWISE : null;
                case I -> mac && !shift ? INFO : null;
                case BACK_SPACE, DELETE -> mac && !shift ? MOVE_TO_TRASH : null;
                case COMMA -> SETTINGS;
                case SLASH -> SHORTCUTS;
                case EQUALS, PLUS, ADD -> ZOOM_IN;
                case MINUS, SUBTRACT -> ZOOM_OUT;
                case DIGIT0, NUMPAD0 -> FIT;
                case DIGIT1, NUMPAD1 -> ACTUAL_SIZE;
                default -> null;
            };
        }
        return switch (code) {
            case RIGHT -> shift ? PAN_RIGHT : NEXT;
            case LEFT -> shift ? PAN_LEFT : PREVIOUS;
            case UP -> PAN_UP;
            case DOWN -> PAN_DOWN;
            case PAGE_DOWN -> NEXT;
            case PAGE_UP, BACK_SPACE -> PREVIOUS;
            case SPACE -> shift ? PREVIOUS : NEXT;
            case HOME -> FIRST;
            case END -> LAST;
            case EQUALS, PLUS, ADD -> ZOOM_IN;
            case MINUS, SUBTRACT -> ZOOM_OUT;
            case DIGIT0, NUMPAD0 -> FIT;
            case DIGIT1, NUMPAD1 -> ACTUAL_SIZE;
            case R -> shift ? ROTATE_COUNTERCLOCKWISE : ROTATE_CLOCKWISE;
            case F, F11 -> FULL_SCREEN;
            case I -> INFO;
            case T -> THUMBNAILS;
            case F2 -> RENAME;
            case ENTER -> mac && !shift ? RENAME : null;
            case F5 -> SLIDESHOW;
            case Z -> KEEP_ZOOM;
            case E -> EDIT;
            case DELETE -> MOVE_TO_TRASH;
            case F12 -> ABOUT;
            case ESCAPE -> ESCAPE;
            default -> null;
        };
    }

    /**
     * Whether Escape, with nothing to leave, closes the window. GNOME's viewer does, and Linux keeps
     * it; on macOS and Windows no viewer does, and a stray press would lose the user's place.
     */
    static boolean escapeCloses(String osName) {
        String os = osName.toLowerCase(Locale.ROOT);
        return !os.startsWith("mac") && !os.startsWith("windows");
    }

    static boolean isMac(String osName) {
        return osName.toLowerCase(Locale.ROOT).startsWith("mac");
    }

    private static KeyCombination key(KeyCode code) {
        return new KeyCodeCombination(code);
    }

    private static KeyCombination shift(KeyCode code) {
        return new KeyCodeCombination(code, KeyCombination.SHIFT_DOWN);
    }

    /** No key: found through the palette and the context menu. */
    private static KeyCombination none() {
        return KeyCombination.NO_MATCH;
    }

    private static KeyCombination shortcut(KeyCode code) {
        return new KeyCodeCombination(code, KeyCombination.SHORTCUT_DOWN);
    }

    /** For keys named by the character they type, whose key code differs by keyboard layout. */
    private static KeyCombination text(String character) {
        return new KeyCharacterCombination(character);
    }
}
