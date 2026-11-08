package com.mirada.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javafx.scene.input.KeyCombination;

import org.jspecify.annotations.Nullable;

/**
 * Everything Mirada can do, the registry (KBD-01): each command's id, how the command palette names
 * and describes it, and where its keys work. The key filter, the palette, the menus and the
 * buttons all run commands from here, so a command has one name wherever it appears. Its keys are
 * the keymap's ({@link Keymap}, {@code keymaps/*.json}) with the user's own on top ({@link
 * Keybindings}).
 */
enum Command {
    NEXT("go.next", "Next Image", "Shows the next image in the folder.", "go navigate forward"),
    PREVIOUS("go.previous", "Previous Image", "Shows the previous image in the folder.", "go navigate back"),
    FIRST("go.first", "First Image", "Jumps to the first image in the folder.", "go navigate start"),
    LAST("go.last", "Last Image", "Jumps to the last image in the folder.", "go navigate end"),
    GO_TO_IMAGE(
            "go.toImage",
            "Go to Image…",
            "Finds an image in the folder by its name, and shows it.",
            "find search name jump navigate"),
    SLIDESHOW(
            "view.slideshow",
            "Slideshow",
            "Shows the folder's images one after another, full screen. Escape stops it.",
            "play presentation"),
    ZOOM_IN("view.zoomIn", "Zoom In", "Zooms to the next step, about the centre of the window.", "view magnify"),
    ZOOM_OUT("view.zoomOut", "Zoom Out", "Zooms to the previous step, about the centre of the window.", "view"),
    FIT("view.zoomToFit", "Zoom to Fit", "Shows the whole image, fitted to the window.", "view"),
    KEEP_ZOOM(
            "view.keepZoom",
            "Keep Zoom When Stepping",
            "Turns on or off keeping the zoom and position when moving to another image.",
            "lock fit compare"),
    PAN_LEFT("view.panLeft", "Pan Left", "Moves the view of a zoomed-in image left.", "scroll"),
    PAN_RIGHT("view.panRight", "Pan Right", "Moves the view of a zoomed-in image right.", "scroll"),
    PAN_UP("view.panUp", "Pan Up", "Moves the view of a zoomed-in image up.", "scroll"),
    PAN_DOWN("view.panDown", "Pan Down", "Moves the view of a zoomed-in image down.", "scroll"),
    ACTUAL_SIZE(
            "view.actualSize",
            "Actual Size",
            "Shows the image at 100 %: one image pixel per screen pixel.",
            "view zoom 100"),
    ROTATE_CLOCKWISE(
            "image.rotateRight",
            "Rotate Right",
            "Turns the image a quarter clockwise. The file is not changed.",
            "turn"),
    ROTATE_COUNTERCLOCKWISE(
            "image.rotateLeft",
            "Rotate Left",
            "Turns the image a quarter counterclockwise. The file is not changed.",
            "turn"),
    FULL_SCREEN("view.fullScreen", "Full Screen", "Fills the screen with the image. Escape leaves.", "view"),
    EDIT(
            "image.edit",
            "Edit…",
            "Crops, turns and flips the image, and saves the result as a new file.",
            "crop rotate flip edit save"),
    RENAME("file.rename", "Rename…", "Renames the image's file.", "file name"),
    UNDO_TRASH(
            "file.undoMoveToTrash",
            "Undo Move to Trash",
            "Puts the image last moved to the Trash back where it was.",
            "restore put back undelete recover"),
    COPY(
            "image.copy",
            "Copy",
            "Copies the image, as it stands on screen, and its file, to paste into another app or a folder.",
            "clipboard"),
    SHOW_IN_FOLDER(
            "file.showInFolder",
            "Show in Folder",
            "Opens the image's folder in the file manager, with the image selected.",
            "reveal finder explorer files locate open containing"),
    MOVE_TO_TRASH(
            "file.moveToTrash",
            "Move to Trash",
            "Moves the image to the Trash, from where it can be put back, and shows the next.",
            "delete remove bin recycle trash"),
    BOOKMARK(
            "bookmark.image",
            "Bookmark Image",
            "Marks the image, to find again with Go to Bookmark or step to in its folder. Again takes the mark off.",
            "favourite favorite star flag mark pick keep"),
    BOOKMARK_FOLDER(
            "bookmark.folder",
            "Bookmark Folder",
            "Marks the image's folder, to open again with Go to Bookmark. Again takes the mark off.",
            "favourite favorite star flag mark directory"),
    NOTE(
            "bookmark.noteOnImage",
            "Note on Image…",
            "Writes a note of your own on the image. Mirada keeps it; the file is not changed.",
            "comment annotate memo personal caption text"),
    FOLDER_NOTE(
            "bookmark.noteOnFolder",
            "Note on Folder…",
            "Writes a note of your own on the image's folder. Mirada keeps it; the folder is not changed.",
            "comment annotate memo personal directory text"),
    NEXT_BOOKMARK(
            "bookmark.next",
            "Next Bookmarked Image",
            "Steps to the next bookmarked image in the folder.",
            "go forward mark favourite"),
    PREVIOUS_BOOKMARK(
            "bookmark.previous",
            "Previous Bookmarked Image",
            "Steps to the previous bookmarked image in the folder.",
            "go back mark favourite"),
    GO_TO_BOOKMARK(
            "bookmark.goTo",
            "Go to Bookmark…",
            "Finds a bookmarked image or folder, or one with a note, by its name or its note, and opens it.",
            "bookmarks notes find search jump favourites favorites"),
    BOOKMARKS(
            "bookmark.list",
            "Bookmarks and Notes",
            "Lists every bookmark and note, by folder, with each image's thumbnail and note, to find, open,"
                    + " edit or remove.",
            "bookmarks notes list all manage window panel favourites favorites organise organize"),
    SORT_BY_NAME("folder.sortByName", "Sort by Name", "Orders the folder by file name, as a person counts.", "order"),
    SORT_BY_DATE(
            "folder.sortByDate",
            "Sort by Date",
            "Orders the folder by when each file was last changed.",
            "order modified time"),
    SORT_BY_SIZE("folder.sortBySize", "Sort by Size", "Orders the folder by file size.", "order"),
    REVERSE_ORDER(
            "folder.reverseOrder",
            "Reverse Order",
            "Turns the folder's order the other way round.",
            "sort descending ascending"),
    INFO(
            "view.properties",
            "Image Properties",
            "Shows the folder, size, format, file dates and camera beside the image.",
            "info details exif metadata sidebar"),
    THUMBNAILS(
            "view.thumbnails",
            "Thumbnails",
            "Shows the folder's images in a strip along the bottom.",
            "filmstrip strip gallery browse folder"),
    THUMBNAILS_AT_SIDE(
            "view.thumbnailsAtSide",
            "Thumbnails at the Side",
            "Stands the thumbnails in a column down the left instead of a row along the bottom.",
            "vertical column filmstrip strip left layout"),
    THUMBNAIL_NAMES(
            "view.thumbnailNames",
            "Names Instead of Thumbnails",
            "Lists the folder by file name in the strip, instead of pictures.",
            "names list text filmstrip strip filenames"),
    THUMBNAIL_CAPTIONS(
            "view.thumbnailCaptions",
            "Names Under Thumbnails",
            "Writes each image's file name under its thumbnail in the strip.",
            "captions labels titles filmstrip strip filenames"),
    /** Leaves full screen, or on Linux closes the window: look, then go ({@link #escapeCloses}). */
    ESCAPE(
            "window.escape",
            "Escape",
            "Stops the slideshow or leaves full screen; on Linux, otherwise closes the window.",
            ""),
    SETTINGS(
            "app.settings",
            "Settings",
            "Theme, what opens at start, and how long the floating controls stay.",
            "preferences options configure"),
    SHORTCUTS("help.keyboardShortcuts", "Keyboard Shortcuts", "Lists every key Mirada answers to.", "keys help"),
    ABOUT(
            "help.about",
            "About Mirada",
            "Version, licence, and the runtime and codec versions.",
            "version help credits"),
    STATUS_BAR(
            "view.statusBar",
            "Status Bar",
            "Shows the line along the bottom of the window that says what the last command did.",
            "echo messages bottom bar footer"),
    MESSAGE_LOG(
            "view.messageLog",
            "Message Log",
            "Lists everything the status bar has said this session, newest first.",
            "messages history echo status notifications errors"),
    COPY_MESSAGES(
            "messages.copy",
            "Copy Messages",
            "Copies the messages as text: those the Message Log shows, or all of this session's.",
            "message log clipboard"),
    CLEAR_MESSAGES(
            "messages.clear",
            "Clear Messages",
            "Empties the Message Log and its count of unread errors.",
            "message log empty"),
    DEBUG_LOG(
            "help.debugLog",
            "Debug Log",
            "Shows what Mirada has logged this session, errors with their details, in a window of its own.",
            "diagnostics logging errors exceptions trace console problems"),
    COPY_DEBUG_LOG(
            "help.copyDebugLog", "Copy Debug Log", "Copies the Debug Log as text.", "diagnostics clipboard logging"),
    EXPORT_DEBUG_LOG(
            "help.exportDebugLog",
            "Export Debug Log…",
            "Saves the Debug Log to a text file.",
            "diagnostics save file logging"),
    CLEAR_DEBUG_LOG(
            "help.clearDebugLog",
            "Clear Debug Log",
            "Empties the Debug Log here; the session file in the settings folder keeps what it has.",
            "diagnostics logging empty"),
    REPORT_PROBLEM(
            "help.reportProblem",
            "Report a Problem…",
            "Writes a report with private details taken out, for you to read and edit, then opens a GitHub"
                    + " issue with it. Nothing is sent by itself.",
            "bug issue feedback support crash help"),
    CHECK_FOR_UPDATES(
            "help.checkForUpdates",
            "Check for Updates",
            "Asks GitHub whether a newer Mirada is out. Nothing is downloaded.",
            "update version release new upgrade"),
    OPEN_RELEASE(
            "help.openRelease",
            "Open the New Release",
            "Opens the newer release's page in the browser, to download it from there.",
            "update download upgrade version"),
    OPEN("file.open", "Open Image…", "Opens an image, with the rest of its folder.", "file"),
    OPEN_FOLDER("file.openFolder", "Open Folder…", "Opens a folder at its first image.", "directory browse"),
    OPEN_RECENT(
            "file.openRecent",
            "Open Recent…",
            "Finds a folder opened lately by its name, and opens it.",
            "recent history folders previous last"),
    CLOSE("window.close", "Close Window", "Closes Mirada.", "quit exit"),
    /** Its keys are the user's ({@code palette.keys}, {@link PaletteChords}), on top of the keymap's. */
    PALETTE("palette.show", "Command Palette", "Finds and runs any command by name.", ""),
    /** Help → Documentation (DOCS-06, Pauta 4.0.1): the user guide on the website, in the browser. */
    DOCUMENTATION(
            "help.documentation",
            "Documentation",
            "Opens Mirada's user guide in the browser.",
            "help manual guide docs website"),
    /** About's Copy details, which needs no About window: what a bug report needs (ABOUT-03). */
    COPY_DETAILS(
            "help.copyDetails",
            "Copy Details",
            "Copies the version, build, runtime, system and settings file, as About lists them, for a bug report.",
            "about version clipboard bug report environment"),
    // The editor's tools: they run while the editor is open (Command.Context.EDITOR).
    EDITOR_ROTATE_LEFT(
            "editor.rotateLeft",
            "Edit: Rotate Left",
            "Turns the picture being edited a quarter counterclockwise.",
            "turn crop"),
    EDITOR_ROTATE_RIGHT(
            "editor.rotateRight",
            "Edit: Rotate Right",
            "Turns the picture being edited a quarter clockwise.",
            "turn crop"),
    EDITOR_FLIP_HORIZONTALLY(
            "editor.flipHorizontally",
            "Edit: Flip Horizontally",
            "Mirrors the picture being edited left to right.",
            "mirror"),
    EDITOR_FLIP_VERTICALLY(
            "editor.flipVertically",
            "Edit: Flip Vertically",
            "Mirrors the picture being edited top to bottom.",
            "mirror upside down"),
    EDITOR_NEXT_SHAPE(
            "editor.nextShape",
            "Edit: Next Crop Shape",
            "Holds the crop to the next shape: free, the original's, square, 5:4, 4:3, 3:2 or 16:9.",
            "aspect ratio crop square"),
    EDITOR_RESET(
            "editor.reset",
            "Edit: Reset",
            "Takes back every turn, flip and crop; Undo brings them back.",
            "revert original clear"),
    EDITOR_UNDO("editor.undo", "Edit: Undo", "Takes back the last turn, flip, shape or move of the crop.", "revert"),
    EDITOR_REDO("editor.redo", "Edit: Redo", "Does again what Undo took back.", "repeat"),
    EDITOR_SAVE(
            "editor.save",
            "Edit: Save…",
            "Asks where, then writes the edited picture as a new file; the original stays as it is.",
            "write export"),
    EDITOR_CANCEL(
            "editor.cancel",
            "Edit: Cancel",
            "Closes the editor without saving, after asking when there is an edit to lose.",
            "close discard quit"),
    // The Bookmarks and Notes window's row actions: they run on its selected row while it is up.
    BOOKMARKS_OPEN(
            "bookmarks.open",
            "Bookmarks: Open",
            "Opens the selected bookmark in the viewer: the image, or the folder at its first image.",
            "show go"),
    BOOKMARKS_EDIT_NOTE(
            "bookmarks.editNote",
            "Bookmarks: Edit Note…",
            "Writes or changes the note on the selected image or folder.",
            "comment annotate"),
    BOOKMARKS_TOGGLE(
            "bookmarks.toggleBookmark",
            "Bookmarks: Bookmark or Unbookmark",
            "Puts the bookmark on the selected image or folder, or takes it off; its note stays.",
            "mark favourite favorite star"),
    BOOKMARKS_SHOW_IN_FOLDER(
            "bookmarks.showInFolder",
            "Bookmarks: Show in Folder",
            "Opens the selected image's folder in the file manager, with the image selected.",
            "reveal finder explorer files locate"),
    BOOKMARKS_REMOVE(
            "bookmarks.remove",
            "Bookmarks: Remove",
            "Takes the bookmark and the note off the selected image or folder, after asking when it has a note.",
            "delete forget"),
    BOOKMARKS_LIST_ALL(
            "bookmarks.listAll",
            "Bookmarks: List All",
            "Lists every bookmark and every note.",
            "filter show everything"),
    BOOKMARKS_LIST_BOOKMARKED(
            "bookmarks.listBookmarked",
            "Bookmarks: List Bookmarks Only",
            "Lists only what is bookmarked.",
            "filter show marked"),
    BOOKMARKS_LIST_NOTES(
            "bookmarks.listNotes", "Bookmarks: List Notes Only", "Lists only what has a note.", "filter show comments");

    /** Whether this is macOS, read once: its keymap is the {@code .mac.json} one ({@link Keymap#load}). */
    static final boolean MAC = isMac(System.getProperty("os.name", ""));

    /** Whether this is Windows, read once: its trash is the Recycle Bin ({@link #label(boolean, boolean)}). */
    static final boolean WINDOWS =
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");

    /**
     * Where a command's keys work: the viewer, the editor while it is open, or the Bookmarks and
     * Notes window. One key can mean a different command in each (R turns the image in the viewer
     * and the picture being edited in the editor); the palette lists every command in all of them.
     */
    enum Context {
        VIEWER,
        EDITOR,
        BOOKMARKS
    }

    /** Every command by its id, built once when first asked. */
    private static final class ById {
        static final Map<String, Command> MAP = byId();

        private static Map<String, Command> byId() {
            Map<String, Command> map = new HashMap<>();
            for (Command command : values()) map.put(command.id, command);
            return Map.copyOf(map);
        }
    }

    /**
     * Whether the thumbnails stand down the side, so the folder steps along Up and Down and the
     * arrows across pan (the keymap's {@code vertical} bindings, {@link Keybindings#current}). The
     * window sets it as the strip moves or closes; on the FX thread, like everything that reads it.
     */
    private static boolean vertical;

    private final String id;
    private final String label;
    private final String description;
    private final String keywords;

    Command(String id, String label, String description, String keywords) {
        this.id = id;
        this.label = label;
        this.description = description;
        this.keywords = keywords;
    }

    /**
     * The command's id, {@code <area>.<camelCaseName>} (KBD-02): what the keymaps bind keys to, what
     * {@code keys.<id>} in the settings rebinds, and what translations will key its strings by.
     */
    String id() {
        return id;
    }

    /** The command with this id, or null for one that is not Mirada's. */
    static @Nullable Command byId(String id) {
        return ById.MAP.get(id);
    }

    /** Where this command's keys work: the editor's tools in the editor, the Bookmarks window's there. */
    Context context() {
        return switch (this) {
            case EDITOR_ROTATE_LEFT,
                    EDITOR_ROTATE_RIGHT,
                    EDITOR_FLIP_HORIZONTALLY,
                    EDITOR_FLIP_VERTICALLY,
                    EDITOR_NEXT_SHAPE,
                    EDITOR_RESET,
                    EDITOR_UNDO,
                    EDITOR_REDO,
                    EDITOR_SAVE,
                    EDITOR_CANCEL -> Context.EDITOR;
            case BOOKMARKS_OPEN,
                    BOOKMARKS_EDIT_NOTE,
                    BOOKMARKS_TOGGLE,
                    BOOKMARKS_SHOW_IN_FOLDER,
                    BOOKMARKS_REMOVE,
                    BOOKMARKS_LIST_ALL,
                    BOOKMARKS_LIST_BOOKMARKED,
                    BOOKMARKS_LIST_NOTES -> Context.BOOKMARKS;
            default -> Context.VIEWER;
        };
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
            case BOOKMARKS_SHOW_IN_FOLDER ->
                "Bookmarks: " + (mac ? "Show in Finder" : windows ? "Show in File Explorer" : "Show in Folder");
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
     * The chord shown beside the command: the first of its keys in the keymap in use, with the
     * user's own on top ({@link Keybindings#current}), or {@link KeyCombination#NO_MATCH} for none.
     */
    KeyCombination chord() {
        return Keybindings.current().chord(this);
    }

    /** Whether the folder steps along Up and Down: the thumbnails stand down the side. */
    static boolean vertical() {
        return vertical;
    }

    static void setVertical(boolean value) {
        vertical = value;
    }

    /**
     * Why this command cannot run right now, or null when it can: stepping needs somewhere to step
     * to, and zooming or turning needs an image on screen. Shown as the greyed row's tooltip in the
     * palette. The editor's and the Bookmarks window's are the window's to say ({@code
     * ViewerWindow.unavailable}).
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
            case BOOKMARK, BOOKMARK_FOLDER, NOTE, FOLDER_NOTE, NEXT_BOOKMARK, PREVIOUS_BOOKMARK ->
                size == 0 ? "No folder is open." : null;
            case SLIDESHOW -> size < 2 ? "A slideshow needs a folder with more than one image." : null;
            case SORT_BY_NAME, SORT_BY_DATE, SORT_BY_SIZE, REVERSE_ORDER, GO_TO_IMAGE ->
                size == 0 ? "No folder is open." : null;
            default -> null;
        };
    }

    /**
     * Every command, as the palette lists them before anything is typed (PAL-02): those whose keys
     * work in {@code where} first, so the editor's tools lead while it is open and the Bookmarks
     * window's row actions in that window; then the rest in their order here. Nothing is left out:
     * one that cannot run is greyed, never hidden.
     */
    static List<Command> inPalette(Context where) {
        List<Command> first = new ArrayList<>();
        List<Command> rest = new ArrayList<>();
        for (Command command : values()) (command.context() == where ? first : rest).add(command);
        first.addAll(rest);
        return List.copyOf(first);
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
            case BOOKMARK,
                    BOOKMARK_FOLDER,
                    NOTE,
                    FOLDER_NOTE,
                    NEXT_BOOKMARK,
                    PREVIOUS_BOOKMARK,
                    GO_TO_BOOKMARK,
                    BOOKMARKS -> "Bookmarks";
            case SORT_BY_NAME, SORT_BY_DATE, SORT_BY_SIZE, REVERSE_ORDER -> "Order";
            case INFO, THUMBNAILS, THUMBNAILS_AT_SIDE, THUMBNAIL_NAMES, THUMBNAIL_CAPTIONS, STATUS_BAR -> "Panels";
            case MESSAGE_LOG,
                    COPY_MESSAGES,
                    CLEAR_MESSAGES,
                    DEBUG_LOG,
                    COPY_DEBUG_LOG,
                    EXPORT_DEBUG_LOG,
                    CLEAR_DEBUG_LOG,
                    REPORT_PROBLEM,
                    CHECK_FOR_UPDATES,
                    OPEN_RELEASE,
                    COPY_DETAILS -> "Help";
            case ESCAPE, SETTINGS, SHORTCUTS, ABOUT, OPEN, OPEN_FOLDER, OPEN_RECENT, CLOSE, PALETTE -> "Mirada";
            case DOCUMENTATION -> "Help";
            case EDITOR_ROTATE_LEFT,
                    EDITOR_ROTATE_RIGHT,
                    EDITOR_FLIP_HORIZONTALLY,
                    EDITOR_FLIP_VERTICALLY,
                    EDITOR_NEXT_SHAPE,
                    EDITOR_RESET,
                    EDITOR_UNDO,
                    EDITOR_REDO,
                    EDITOR_SAVE,
                    EDITOR_CANCEL -> "Editor";
            case BOOKMARKS_OPEN,
                    BOOKMARKS_EDIT_NOTE,
                    BOOKMARKS_TOGGLE,
                    BOOKMARKS_SHOW_IN_FOLDER,
                    BOOKMARKS_REMOVE,
                    BOOKMARKS_LIST_ALL,
                    BOOKMARKS_LIST_BOOKMARKED,
                    BOOKMARKS_LIST_NOTES -> "Bookmarks and Notes Window";
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
}
