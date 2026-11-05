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
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;

import org.jspecify.annotations.Nullable;

/**
 * The macOS menu bar: File, Edit, Image, View, Go and Help across the top of the screen, every item a
 * {@link Command}. A Mac app without one looks unfinished, and the Help menu's search finds every
 * item by name, as the palette does. About and Settings go in the application menu instead
 * ({@code os.MacAppMenu}).
 *
 * <p>Only chords with Cmd are shown, as Preview shows them. A bare key is never a menu's key
 * equivalent: the menu could take it while the palette's field has focus, and an "r" typed there
 * would rotate the image. Items are greyed only while their menu is open, by the same rule as the
 * palette's; the rest of the time they stay enabled, so a key equivalent is never swallowed by a
 * stale state, and the command decides for itself, as it does from the keyboard.
 */
final class MacMenus {

    /** One menu: its title, and its items in groups that separators divide. */
    record Heading(String title, List<List<Command>> groups) {}

    static final List<Heading> HEADINGS = List.of(
            new Heading(
                    "File", List.of(List.of(Command.OPEN), List.of(Command.SHOW_IN_FOLDER), List.of(Command.CLOSE))),
            new Heading("Edit", List.of(List.of(Command.COPY))),
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
                            List.of(Command.INFO, Command.THUMBNAILS),
                            List.of(Command.FULL_SCREEN))),
            new Heading(
                    "Go",
                    List.of(
                            List.of(Command.NEXT, Command.PREVIOUS, Command.FIRST, Command.LAST),
                            List.of(Command.SLIDESHOW),
                            List.of(
                                    Command.SORT_BY_NAME,
                                    Command.SORT_BY_DATE,
                                    Command.SORT_BY_SIZE,
                                    Command.REVERSE_ORDER))),
            new Heading("Help", List.of(List.of(Command.PALETTE, Command.SHORTCUTS))));

    /** The commands that are a setting or a panel, shown with a tick when on. */
    static final Set<Command> TICKED = Set.of(
            Command.KEEP_ZOOM,
            Command.INFO,
            Command.THUMBNAILS,
            Command.SORT_BY_NAME,
            Command.SORT_BY_DATE,
            Command.SORT_BY_SIZE,
            Command.REVERSE_ORDER);

    private MacMenus() {}

    /**
     * The chord a menu shows beside {@code command}, or null for none: the command's own when it
     * has Cmd, and Preview's for zooming, turning, the properties and full screen, which Mirada also
     * answers to on a Mac ({@link Command#forKey}).
     */
    static @Nullable KeyCombination accelerator(Command command) {
        return switch (command) {
            case ZOOM_IN -> cmd(KeyCode.EQUALS);
            case ZOOM_OUT -> cmd(KeyCode.MINUS);
            case FIT -> cmd(KeyCode.DIGIT0);
            case ACTUAL_SIZE -> cmd(KeyCode.DIGIT1);
            case ROTATE_CLOCKWISE -> cmd(KeyCode.R);
            case ROTATE_COUNTERCLOCKWISE -> cmd(KeyCode.L);
            case INFO -> cmd(KeyCode.I);
            case FULL_SCREEN ->
                new KeyCodeCombination(KeyCode.F, KeyCombination.SHORTCUT_DOWN, KeyCombination.CONTROL_DOWN);
            default -> {
                KeyCombination chord = command.chord(true);
                yield chord.getShortcut() == KeyCombination.ModifierValue.DOWN ? chord : null;
            }
        };
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
                    MenuItem item = TICKED.contains(command)
                            ? new CheckMenuItem(command.label())
                            : new MenuItem(command.label());
                    item.setUserData(command);
                    item.setAccelerator(accelerator(command));
                    item.setOnAction(e -> run.accept(command));
                    items.add(item);
                    menu.getItems().add(item);
                }
            }
            menu.setOnShowing(e -> {
                for (MenuItem item : items) {
                    Command command = (Command) item.getUserData();
                    item.setText(label.apply(command));
                    item.setDisable(unavailable.apply(command) != null);
                    if (item instanceof CheckMenuItem check) check.setSelected(ticked.test(command));
                }
            });
            menu.setOnHidden(e -> items.forEach(item -> item.setDisable(false)));
            bar.getMenus().add(menu);
        }
        return bar;
    }

    private static KeyCombination cmd(KeyCode code) {
        return new KeyCodeCombination(code, KeyCombination.SHORTCUT_DOWN);
    }
}
