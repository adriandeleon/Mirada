package com.mirada.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javafx.scene.input.KeyCombination;

import com.mirada.i18n.Messages;
import com.mirada.os.Os;
import org.jspecify.annotations.Nullable;

import static com.mirada.i18n.Messages.tr;

/**
 * Everything Mirada can do, the registry (KBD-01): each command's id, how the command palette names
 * and describes it, and where its keys work. The key filter, the palette, the menus and the
 * buttons all run commands from here, so a command has one name wherever it appears. Its keys are
 * the keymap's ({@link Keymap}, {@code keymaps/*.json}) with the user's own on top ({@link
 * Keybindings}).
 */
enum Command {
    NEXT("go.next", "go navigate forward"),
    PREVIOUS("go.previous", "go navigate back"),
    FIRST("go.first", "go navigate start"),
    LAST("go.last", "go navigate end"),
    GO_TO_IMAGE("go.toImage", "find search name jump navigate"),
    SLIDESHOW("view.slideshow", "play presentation"),
    ZOOM_IN("view.zoomIn", "view magnify"),
    ZOOM_OUT("view.zoomOut", "view"),
    FIT("view.zoomToFit", "view"),
    KEEP_ZOOM("view.keepZoom", "lock fit compare"),
    PAN_LEFT("view.panLeft", "scroll"),
    PAN_RIGHT("view.panRight", "scroll"),
    PAN_UP("view.panUp", "scroll"),
    PAN_DOWN("view.panDown", "scroll"),
    ACTUAL_SIZE("view.actualSize", "view zoom 100"),
    ROTATE_CLOCKWISE("image.rotateRight", "turn"),
    ROTATE_COUNTERCLOCKWISE("image.rotateLeft", "turn"),
    FULL_SCREEN("view.fullScreen", "view"),
    /**
     * The interface's zoom (WIN-12): the whole chrome, menus and status bar included, never the
     * image, whose zoom is {@link #ZOOM_IN} and the rest on Ctrl+=, Ctrl+- and Ctrl+0 (WIN-09).
     */
    UI_ZOOM_IN("view.uiZoomIn", "interface larger bigger text font size menus chrome accessibility scale"),
    UI_ZOOM_OUT("view.uiZoomOut", "interface smaller text font size menus chrome scale"),
    UI_ZOOM_RESET("view.uiZoomReset", "interface default normal 100 text font size menus chrome scale"),
    EDIT("image.edit", "crop rotate flip edit save"),
    RENAME("file.rename", "file name"),
    UNDO_TRASH("file.undoMoveToTrash", "restore put back undelete recover"),
    COPY("image.copy", "clipboard"),
    SHOW_IN_FOLDER("file.showInFolder", "reveal finder explorer files locate open containing"),
    MOVE_TO_TRASH("file.moveToTrash", "delete remove bin recycle trash"),
    BOOKMARK("bookmark.image", "favourite favorite star flag mark pick keep"),
    BOOKMARK_FOLDER("bookmark.folder", "favourite favorite star flag mark directory"),
    NOTE("bookmark.noteOnImage", "comment annotate memo personal caption text"),
    FOLDER_NOTE("bookmark.noteOnFolder", "comment annotate memo personal directory text"),
    NEXT_BOOKMARK("bookmark.next", "go forward mark favourite"),
    PREVIOUS_BOOKMARK("bookmark.previous", "go back mark favourite"),
    GO_TO_BOOKMARK("bookmark.goTo", "bookmarks notes find search jump favourites favorites"),
    BOOKMARKS("bookmark.list", "bookmarks notes list all manage window panel favourites favorites organise organize"),
    SORT_BY_NAME("folder.sortByName", "order"),
    SORT_BY_DATE("folder.sortByDate", "order modified time"),
    SORT_BY_SIZE("folder.sortBySize", "order"),
    REVERSE_ORDER("folder.reverseOrder", "sort descending ascending"),
    INFO("tool.properties", "info details exif metadata sidebar"),
    /**
     * The properties sidebar with All Metadata unfolded, or folded again: the keyboard's way to that
     * card, which stays out of the Tab order so Space keeps stepping the folder (KBD-15).
     */
    ALL_METADATA("view.allMetadata", "exif xmp iptc tags details info properties sidebar unfold expand"),
    THUMBNAILS("tool.thumbnails", "filmstrip strip gallery browse folder"),
    THUMBNAILS_AT_SIDE("view.thumbnailsAtSide", "vertical column filmstrip strip left layout"),
    THUMBNAIL_NAMES("view.thumbnailNames", "names list text filmstrip strip filenames"),
    THUMBNAIL_CAPTIONS("view.thumbnailCaptions", "captions labels titles filmstrip strip filenames"),
    /**
     * Stops the slideshow or leaves full screen; with nothing to leave it does nothing, on every OS
     * (OS-04): a stray press never loses the user's place.
     */
    ESCAPE("window.escape", ""),
    SETTINGS("app.settings", "preferences options configure"),
    // One command for each setting that has no other (SET-06, Pauta 4.0.1): it flips the setting, or
    // asks for its value in the palette.
    THEME("settings.theme", "appearance dark light mode colour color look"),
    LANGUAGE("settings.language", "locale translation english español português français italiano deutsch"),
    KEYMAP("settings.keymap", "keys shortcuts bindings emacs cua"),
    THUMBNAILS_AT_START("settings.thumbnailsAtStart", "startup launch filmstrip strip open"),
    PROPERTIES_AT_START("settings.propertiesAtStart", "startup launch info details sidebar open"),
    HIDE_CONTROLS_AFTER("settings.hideControlsAfter", "delay fade timeout overlay buttons seconds"),
    SLIDESHOW_SPEED("settings.slideshowSpeed", "interval delay seconds play pace"),
    UI_ZOOM("settings.uiZoom", "interface size scale larger smaller text font menus chrome accessibility zoom"),
    PALETTE_KEYS("settings.paletteKeys", "shortcut keys bindings palette"),
    /** Settings' Default button beside the palette's keys (KBD-01): the keys it opens with at first. */
    RESET_PALETTE_KEYS("settings.resetPaletteKeys", "default shortcut keys bindings palette restore"),
    CHECK_DAILY("settings.checkForUpdatesDaily", "automatic update version network release"),
    /** The menu bar in the title bar, or under the system's (WIN-03): takes effect at the next start. */
    TITLE_BAR_MENUS("settings.titleBarMenus", "menu bar header title frame decorated window chrome"),
    /** Export Configuration (SET-12): the config folder as a timestamped zip. */
    EXPORT_CONFIG("config.export", "settings preferences backup save zip copy move"),
    /** Import Configuration (SET-12): the parts of an exported zip the user ticks. */
    IMPORT_CONFIG("config.import", "settings preferences restore backup load zip"),
    SHORTCUTS("help.keyboardShortcuts", "keys help"),
    ABOUT("help.about", "version help credits"),
    STATUS_BAR("view.statusBar", "echo messages bottom bar footer"),
    MESSAGE_LOG("view.messageLog", "messages history echo status notifications errors"),
    COPY_MESSAGES("messages.copy", "message log clipboard"),
    CLEAR_MESSAGES("messages.clear", "message log empty"),
    DEBUG_LOG("help.debugLog", "diagnostics logging errors exceptions trace console problems"),
    COPY_DEBUG_LOG("help.copyDebugLog", "diagnostics clipboard logging"),
    EXPORT_DEBUG_LOG("help.exportDebugLog", "diagnostics save file logging"),
    CLEAR_DEBUG_LOG("help.clearDebugLog", "diagnostics logging empty"),
    /**
     * The Debug Log's find bar (FIND-04): opens it over the log, and run while it is open finds the
     * next match. Its keys work in the Debug Log window ({@link Context#DEBUG_LOG}).
     */
    DEBUG_LOG_FIND("log.find", "search look text diagnostics logging next"),
    REPORT_PROBLEM("help.reportProblem", "bug issue feedback support crash help"),
    CHECK_FOR_UPDATES("help.checkForUpdates", "update version release new upgrade"),
    OPEN_RELEASE("help.openRelease", "update download upgrade version"),
    OPEN("file.open", "file"),
    OPEN_FOLDER("file.openFolder", "directory browse"),
    OPEN_RECENT("file.openRecent", "recent history folders previous last"),
    CLOSE("window.close", "exit"),
    /** File → Quit (WIN-14): the one quit method, which the window's own close and macOS's Quit run too. */
    QUIT("app.quit", "exit close leave"),
    /** Its keys are the user's ({@code palette.keys}, {@link PaletteChords}), on top of the keymap's. */
    PALETTE("palette.show", ""),
    /** Help → Documentation (DOCS-06): the user guide on the website, in the browser. */
    DOCUMENTATION("help.documentation", "help manual guide docs website"),
    /** About's Copy details, which needs no About window: what a bug report needs (ABOUT-03). */
    COPY_DETAILS("help.copyDetails", "about version clipboard bug report environment"),
    // About's links, each a command so the palette reaches it too (KBD-01, ABOUT-02).
    HOME_PAGE("help.homePage", "website web site home about"),
    RELEASE_NOTES("help.releaseNotes", "changelog changes new version about"),
    RELEASES("help.releases", "source code repository github download versions about"),
    /** The Pauta window (PWIN-01): the edition, the conformance and every exception. */
    PAUTA("help.pauta", "guidelines conformance rules standard exceptions about"),
    /** The edition's own page, never latest/ (PWIN-04, ABOUT-06). */
    PAUTA_RULES("help.pautaRules", "guidelines edition rules standard"),
    /** The website's page on Mirada's conformance (PWIN-07). */
    PAUTA_DOCS("help.pautaDocs", "guidelines conformance website exceptions"),
    /** The Pauta window's Copy as text (PWIN-06), which needs no window. */
    COPY_PAUTA("help.copyPauta", "guidelines conformance clipboard markdown exceptions"),
    // The editor's tools: they run while the editor is open (Command.Context.EDITOR).
    EDITOR_ROTATE_LEFT("editor.rotateLeft", "turn crop"),
    EDITOR_ROTATE_RIGHT("editor.rotateRight", "turn crop"),
    EDITOR_FLIP_HORIZONTALLY("editor.flipHorizontally", "mirror"),
    EDITOR_FLIP_VERTICALLY("editor.flipVertically", "mirror upside down"),
    EDITOR_NEXT_SHAPE("editor.nextShape", "aspect ratio crop square"),
    EDITOR_RESET("editor.reset", "revert original clear"),
    EDITOR_UNDO("editor.undo", "revert"),
    EDITOR_REDO("editor.redo", "repeat"),
    EDITOR_SAVE("editor.save", "write export"),
    EDITOR_CANCEL("editor.cancel", "close discard quit"),
    // The Bookmarks and Notes window's row actions: they run on its selected row while it is up.
    BOOKMARKS_OPEN("bookmarks.open", "show go"),
    BOOKMARKS_EDIT_NOTE("bookmarks.editNote", "comment annotate"),
    BOOKMARKS_TOGGLE("bookmarks.toggleBookmark", "mark favourite favorite star"),
    BOOKMARKS_SHOW_IN_FOLDER("bookmarks.showInFolder", "reveal finder explorer files locate"),
    BOOKMARKS_REMOVE("bookmarks.remove", "delete forget"),
    BOOKMARKS_UNDO_REMOVE("bookmarks.undoRemove", "restore put back undelete recover"),
    BOOKMARKS_LIST_ALL("bookmarks.listAll", "filter show everything"),
    BOOKMARKS_LIST_BOOKMARKED("bookmarks.listBookmarked", "filter show marked"),
    BOOKMARKS_LIST_NOTES("bookmarks.listNotes", "filter show comments");

    /**
     * Where a command's keys work: the viewer, the editor while it is open, the Bookmarks and Notes
     * window, or the Debug Log window. One key can mean a different command in each (R turns the image in the viewer
     * and the picture being edited in the editor); the palette lists every command in all of them.
     */
    enum Context {
        VIEWER,
        EDITOR,
        BOOKMARKS,
        /** The Debug Log window, whose find command takes the keys a find takes. */
        DEBUG_LOG
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
    private final String keywords;

    /**
     * @param id the command's id; its title is {@code command.<id>} and its palette line {@code
     *     command.<id>.desc} in the message catalogs (KBD-03, PAL-02)
     * @param keywords English words the palette also finds it by, whatever the language
     */
    Command(String id, String keywords) {
        this.id = id;
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
                    BOOKMARKS_UNDO_REMOVE,
                    BOOKMARKS_LIST_ALL,
                    BOOKMARKS_LIST_BOOKMARKED,
                    BOOKMARKS_LIST_NOTES -> Context.BOOKMARKS;
            case DEBUG_LOG_FIND -> Context.DEBUG_LOG;
            default -> Context.VIEWER;
        };
    }

    String label() {
        return label(Os.isMac(), Os.isWindows());
    }

    /**
     * The label on macOS, Windows or Linux: Windows calls its trash the Recycle Bin, and the file
     * manager is named where it has one name. Testable.
     */
    String label(boolean mac, boolean windows) {
        return tr(platformKey("command." + id, mac, windows));
    }

    /**
     * {@code key}, or its {@code .mac} or {@code .windows} form where the catalog has one: Windows
     * calls its trash the Recycle Bin, and the file manager is named where it has one name. Those
     * forms live under {@code commandOn.}, so a {@code command.} key is always a title or its
     * {@code .desc}.
     */
    private static String platformKey(String key, boolean mac, boolean windows) {
        String platform = mac ? ".mac" : windows ? ".windows" : null;
        if (platform == null) return key;
        String variant = "commandOn" + key.substring("command".length()) + platform;
        return Messages.has(variant) ? variant : key;
    }

    /** What the desktop calls the place a deleted file can be put back from. */
    static String trash(boolean windows) {
        return tr(windows ? "trash.recycleBin" : "trash.trash");
    }

    /** One line, shown under the palette's list for the highlighted command. */
    String description() {
        return tr(platformKey("command." + id + ".desc", Os.isMac(), Os.isWindows()));
    }

    /**
     * Words the palette also finds the command by, besides its label: English ones, and in another
     * language the English title's words too, so a name read in a guide or known from before is found.
     */
    String keywords() {
        if (Messages.current().equals("en")) return keywords;
        String english = Messages.english("command." + id).replace("…", "").replace(":", "");
        return (keywords + " " + english).strip();
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
            case NEXT, LAST -> size == 0 ? tr("reason.noFolder") : index >= size - 1 ? tr("reason.lastImage") : null;
            case PREVIOUS, FIRST -> size == 0 ? tr("reason.noFolder") : index <= 0 ? tr("reason.firstImage") : null;
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
                    SHOW_IN_FOLDER -> imageShowing ? null : tr("reason.noImage");
            case BOOKMARK, BOOKMARK_FOLDER, NOTE, FOLDER_NOTE, NEXT_BOOKMARK, PREVIOUS_BOOKMARK ->
                size == 0 ? tr("reason.noFolder") : null;
            case SLIDESHOW -> size < 2 ? tr("reason.slideshowNeedsImages") : null;
            case SORT_BY_NAME, SORT_BY_DATE, SORT_BY_SIZE, REVERSE_ORDER, GO_TO_IMAGE ->
                size == 0 ? tr("reason.noFolder") : null;
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
            case NEXT, PREVIOUS, FIRST, LAST, GO_TO_IMAGE, SLIDESHOW -> tr("group.folder");
            case ZOOM_IN,
                    ZOOM_OUT,
                    FIT,
                    ACTUAL_SIZE,
                    KEEP_ZOOM,
                    PAN_LEFT,
                    PAN_RIGHT,
                    PAN_UP,
                    PAN_DOWN,
                    FULL_SCREEN,
                    UI_ZOOM_IN,
                    UI_ZOOM_OUT,
                    UI_ZOOM_RESET -> tr("group.view");
            case ROTATE_CLOCKWISE,
                    ROTATE_COUNTERCLOCKWISE,
                    EDIT,
                    RENAME,
                    MOVE_TO_TRASH,
                    UNDO_TRASH,
                    COPY,
                    SHOW_IN_FOLDER -> tr("group.image");
            case BOOKMARK,
                    BOOKMARK_FOLDER,
                    NOTE,
                    FOLDER_NOTE,
                    NEXT_BOOKMARK,
                    PREVIOUS_BOOKMARK,
                    GO_TO_BOOKMARK,
                    BOOKMARKS -> tr("group.bookmarks");
            case SORT_BY_NAME, SORT_BY_DATE, SORT_BY_SIZE, REVERSE_ORDER -> tr("group.order");
            case INFO, ALL_METADATA, THUMBNAILS, THUMBNAILS_AT_SIDE, THUMBNAIL_NAMES, THUMBNAIL_CAPTIONS, STATUS_BAR ->
                tr("group.panels");
            case MESSAGE_LOG,
                    COPY_MESSAGES,
                    CLEAR_MESSAGES,
                    DEBUG_LOG,
                    COPY_DEBUG_LOG,
                    EXPORT_DEBUG_LOG,
                    CLEAR_DEBUG_LOG,
                    DEBUG_LOG_FIND,
                    REPORT_PROBLEM,
                    CHECK_FOR_UPDATES,
                    OPEN_RELEASE,
                    COPY_DETAILS,
                    HOME_PAGE,
                    RELEASE_NOTES,
                    RELEASES,
                    PAUTA,
                    PAUTA_RULES,
                    PAUTA_DOCS,
                    COPY_PAUTA -> tr("group.help");
            case ESCAPE, SETTINGS, SHORTCUTS, ABOUT, OPEN, OPEN_FOLDER, OPEN_RECENT, CLOSE, QUIT, PALETTE ->
                tr("group.mirada");
            case THEME,
                    LANGUAGE,
                    KEYMAP,
                    THUMBNAILS_AT_START,
                    PROPERTIES_AT_START,
                    HIDE_CONTROLS_AFTER,
                    SLIDESHOW_SPEED,
                    UI_ZOOM,
                    PALETTE_KEYS,
                    RESET_PALETTE_KEYS,
                    CHECK_DAILY,
                    TITLE_BAR_MENUS,
                    EXPORT_CONFIG,
                    IMPORT_CONFIG -> tr("group.settings");
            case DOCUMENTATION -> tr("group.help");
            case EDITOR_ROTATE_LEFT,
                    EDITOR_ROTATE_RIGHT,
                    EDITOR_FLIP_HORIZONTALLY,
                    EDITOR_FLIP_VERTICALLY,
                    EDITOR_NEXT_SHAPE,
                    EDITOR_RESET,
                    EDITOR_UNDO,
                    EDITOR_REDO,
                    EDITOR_SAVE,
                    EDITOR_CANCEL -> tr("group.editor");
            case BOOKMARKS_OPEN,
                    BOOKMARKS_EDIT_NOTE,
                    BOOKMARKS_TOGGLE,
                    BOOKMARKS_SHOW_IN_FOLDER,
                    BOOKMARKS_REMOVE,
                    BOOKMARKS_UNDO_REMOVE,
                    BOOKMARKS_LIST_ALL,
                    BOOKMARKS_LIST_BOOKMARKED,
                    BOOKMARKS_LIST_NOTES -> tr("group.bookmarksWindow");
        };
    }
}
