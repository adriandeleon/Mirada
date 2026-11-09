package com.mirada.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.KeyCombination;

import org.jspecify.annotations.Nullable;

/**
 * The macOS menu bar: File, Edit, Image, View, Go and Help across the top of the screen, every item a
 * {@link Command}. A Mac app without one looks unfinished, and the Help menu's search finds every
 * item by name, as the palette does. About and Settings go in the application menu instead
 * ({@code os.MacAppMenu}).
 *
 * <p>No item has a key equivalent (Pauta MENU-02): the native menu would take its key before the
 * window's key filter, the one way into a command ({@link Keybindings#commandFor}), and a bare one while the
 * palette's field has focus, so an "r" typed there would rotate the image. The chord is written into
 * the item's title instead (MENU-15), "Rotate Right   R", as the native menu draws text only, and
 * so no icons. Items are greyed only while their menu is open, by the same rule as the palette's,
 * and their titles are written again then, so a changed key or the strip's axis shows at once.
 */
final class MacMenus {

    /** One menu: its title, and its items in groups that separators divide. */
    record Heading(String title, List<List<Command>> groups) {}

    static final List<Heading> HEADINGS = List.of(
            new Heading(
                    "File",
                    List.of(
                            List.of(Command.OPEN, Command.OPEN_FOLDER, Command.OPEN_RECENT),
                            List.of(Command.SHOW_IN_FOLDER),
                            List.of(Command.EXPORT_CONFIG, Command.IMPORT_CONFIG),
                            List.of(Command.CLOSE))),
            new Heading("Edit", List.of(List.of(Command.UNDO_TRASH), List.of(Command.COPY))),
            new Heading(
                    "Image",
                    List.of(
                            List.of(Command.EDIT),
                            List.of(Command.ROTATE_CLOCKWISE, Command.ROTATE_COUNTERCLOCKWISE),
                            List.of(Command.RENAME, Command.MOVE_TO_TRASH))),
            new Heading(
                    "View",
                    List.of(
                            List.of(Command.ZOOM_IN, Command.ZOOM_OUT, Command.FIT, Command.ACTUAL_SIZE),
                            List.of(Command.KEEP_ZOOM),
                            List.of(Command.INFO, Command.THUMBNAILS, Command.STATUS_BAR),
                            List.of(Command.THUMBNAILS_AT_SIDE, Command.THUMBNAIL_CAPTIONS, Command.THUMBNAIL_NAMES),
                            List.of(Command.FULL_SCREEN))),
            new Heading(
                    "Go",
                    List.of(
                            List.of(Command.NEXT, Command.PREVIOUS, Command.FIRST, Command.LAST),
                            List.of(Command.GO_TO_IMAGE),
                            List.of(Command.SLIDESHOW),
                            List.of(
                                    Command.SORT_BY_NAME,
                                    Command.SORT_BY_DATE,
                                    Command.SORT_BY_SIZE,
                                    Command.REVERSE_ORDER))),
            new Heading(
                    "Bookmarks",
                    List.of(
                            List.of(Command.BOOKMARK, Command.BOOKMARK_FOLDER),
                            List.of(Command.NOTE, Command.FOLDER_NOTE),
                            List.of(Command.NEXT_BOOKMARK, Command.PREVIOUS_BOOKMARK, Command.GO_TO_BOOKMARK),
                            List.of(Command.BOOKMARKS))),
            new Heading(
                    "Help",
                    List.of(
                            List.of(Command.PALETTE, Command.DOCUMENTATION, Command.SHORTCUTS),
                            List.of(Command.CHECK_FOR_UPDATES, Command.OPEN_RELEASE),
                            List.of(Command.MESSAGE_LOG, Command.COPY_MESSAGES, Command.CLEAR_MESSAGES),
                            List.of(
                                    Command.DEBUG_LOG,
                                    Command.COPY_DEBUG_LOG,
                                    Command.EXPORT_DEBUG_LOG,
                                    Command.CLEAR_DEBUG_LOG),
                            List.of(Command.REPORT_PROBLEM))));

    /** The commands that are a setting or a panel, shown with a tick when on. */
    static final Set<Command> TICKED = Set.of(
            Command.BOOKMARK,
            Command.BOOKMARK_FOLDER,
            Command.KEEP_ZOOM,
            Command.INFO,
            Command.THUMBNAILS,
            Command.STATUS_BAR,
            Command.THUMBNAILS_AT_SIDE,
            Command.THUMBNAIL_NAMES,
            Command.THUMBNAIL_CAPTIONS,
            Command.SORT_BY_NAME,
            Command.SORT_BY_DATE,
            Command.SORT_BY_SIZE,
            Command.REVERSE_ORDER);

    private MacMenus() {}

    /** What the menu calls {@code command}: {@code label}, then its chord now, if it has one. */
    static String title(String label, Command command) {
        KeyCombination chord = command.chord();
        return chord == KeyCombination.NO_MATCH ? label : label + "   " + chord.getDisplayText();
    }

    /**
     * The menu bar, on the system's menu bar. It must be in the window's scene to take effect; it
     * draws nothing there.
     *
     * @param run runs a command, as its key would
     * @param unavailable why a command cannot run now, or null: greys it while its menu is open
     * @param ticked whether a {@link #TICKED} command is on now
     * @param label a command's label now ("Leave Full Screen" while in it)
     */
    static MenuBar build(
            Consumer<Command> run,
            Function<Command, @Nullable String> unavailable,
            Predicate<Command> ticked,
            Function<Command, String> label) {
        MenuBar bar = new MenuBar();
        bar.setUseSystemMenuBar(true);
        for (Heading heading : HEADINGS) {
            Menu menu = new Menu(heading.title());
            List<MenuItem> items = new ArrayList<>();
            for (List<Command> group : heading.groups()) {
                if (!menu.getItems().isEmpty()) menu.getItems().add(new SeparatorMenuItem());
                for (Command command : group) {
                    String title = title(command.label(), command);
                    MenuItem item = TICKED.contains(command) ? new CheckMenuItem(title) : new MenuItem(title);
                    item.setUserData(command);
                    item.setOnAction(e -> run.accept(command));
                    items.add(item);
                    menu.getItems().add(item);
                }
            }
            menu.setOnShowing(e -> {
                for (MenuItem item : items) {
                    Command command = (Command) item.getUserData();
                    item.setText(title(label.apply(command), command));
                    item.setDisable(unavailable.apply(command) != null);
                    if (item instanceof CheckMenuItem check) check.setSelected(ticked.test(command));
                }
            });
            menu.setOnHidden(e -> items.forEach(item -> item.setDisable(false)));
            bar.getMenus().add(menu);
        }
        return bar;
    }
}
