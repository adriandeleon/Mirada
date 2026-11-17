package com.mirada.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

import org.jspecify.annotations.Nullable;

import static com.mirada.i18n.Messages.tr;

/**
 * The menu bar, the same menus on every OS (OS-01, OS-04): File, Edit, Image, View, Go, Bookmarks and
 * Help, every row a {@link Command}. On Windows and Linux it is drawn in the window, in the title bar
 * by default ({@link TitleBar}, WIN-03); on macOS it is the system's, across the top of the screen,
 * where the Help menu's search finds every row by name. About and Settings are also in the macOS
 * application menu ({@code os.MacAppMenu}).
 *
 * <p>No row has an accelerator (MENU-02): a native menu would take its key before the window's key
 * filter, the one way into a command ({@link Keybindings#commandFor}), and a bare one while the
 * palette's field has focus, so an "r" typed there would rotate the image. The chord is drawn
 * instead, in one of two ways:
 *
 * <ul>
 *   <li><b>In the window</b>: each row a {@link CustomMenuItem} of the command's glyph in a 22 px
 *       column, its title, a spacer and its chord, every title in a menu as wide as the widest so the
 *       chords form one column ({@link #inWindowRow}, Editora's {@code MainMenuBar.rowContent}). A row
 *       that cannot run now stays, greyed, its tooltip saying why, and choosing it says why in the
 *       status bar (KBD-04).
 *   <li><b>The macOS system menu bar</b>: AppKit draws the menus and no JavaFX node, so a row is plain
 *       text with the chord appended, "Rotate Right   R" ({@link #title}), and no icon; it is greyed
 *       while its menu is open, by the same rule as the palette's.
 * </ul>
 *
 * <p>Either way the titles and chords are written again as a menu opens, so a changed key, the
 * strip's axis or full screen shows at once.
 */
final class AppMenus {

    /**
     * One menu: the catalog key of its title, and its items in groups that separators divide. Nothing
     * matches a menu by its title, so the Help menu stays the Help menu in any language.
     */
    record Heading(String titleKey, List<List<Command>> groups) {

        /** Its title in the language in use: "File", "Help". */
        String title() {
            return tr(titleKey);
        }
    }

    static final List<Heading> HEADINGS = List.of(
            new Heading(
                    "menu.file",
                    List.of(
                            List.of(Command.OPEN, Command.OPEN_FOLDER, Command.OPEN_RECENT),
                            List.of(Command.SHOW_IN_FOLDER),
                            List.of(Command.PRINT),
                            List.of(Command.EXPORT_CONFIG, Command.IMPORT_CONFIG),
                            List.of(Command.SETTINGS),
                            List.of(Command.CLOSE, Command.QUIT))),
            new Heading("menu.edit", List.of(List.of(Command.UNDO_TRASH), List.of(Command.COPY))),
            new Heading(
                    "menu.image",
                    List.of(
                            List.of(Command.EDIT),
                            List.of(Command.ROTATE_CLOCKWISE, Command.ROTATE_COUNTERCLOCKWISE),
                            List.of(Command.RENAME, Command.MOVE_TO_TRASH))),
            new Heading(
                    "menu.view",
                    List.of(
                            List.of(Command.ZOOM_IN, Command.ZOOM_OUT, Command.FIT, Command.ACTUAL_SIZE),
                            List.of(Command.KEEP_ZOOM),
                            List.of(Command.INFO, Command.ALL_METADATA, Command.THUMBNAILS, Command.STATUS_BAR),
                            List.of(Command.THUMBNAILS_AT_SIDE, Command.THUMBNAIL_CAPTIONS, Command.THUMBNAIL_NAMES),
                            List.of(Command.UI_ZOOM_IN, Command.UI_ZOOM_OUT, Command.UI_ZOOM_RESET),
                            List.of(Command.FULL_SCREEN))),
            new Heading(
                    "menu.go",
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
                    "menu.bookmarks",
                    List.of(
                            List.of(Command.BOOKMARK, Command.BOOKMARK_FOLDER),
                            List.of(Command.NOTE, Command.FOLDER_NOTE),
                            List.of(Command.NEXT_BOOKMARK, Command.PREVIOUS_BOOKMARK, Command.GO_TO_BOOKMARK),
                            List.of(Command.BOOKMARKS))),
            // Pauta's Help order (DOCS-07): Command Palette, Documentation, the keyboard reference,
            // Check for Updates, Message Log, Debug Log, Report a Problem, Pauta Conformance, About.
            // Mirada has no Welcome Page; each log's own commands follow it.
            new Heading(
                    "menu.help",
                    List.of(
                            List.of(Command.PALETTE, Command.DOCUMENTATION, Command.SHORTCUTS),
                            List.of(Command.CHECK_FOR_UPDATES, Command.OPEN_RELEASE),
                            List.of(Command.MESSAGE_LOG, Command.COPY_MESSAGES, Command.CLEAR_MESSAGES),
                            List.of(
                                    Command.DEBUG_LOG,
                                    Command.COPY_DEBUG_LOG,
                                    Command.EXPORT_DEBUG_LOG,
                                    Command.CLEAR_DEBUG_LOG),
                            List.of(Command.REPORT_PROBLEM),
                            List.of(Command.PAUTA, Command.ABOUT))));

    /** The commands that are a setting or a panel, shown with a tick when on. */
    static final Set<Command> TICKED = Set.of(
            Command.BOOKMARK,
            Command.BOOKMARK_FOLDER,
            Command.KEEP_ZOOM,
            Command.INFO,
            Command.ALL_METADATA,
            Command.THUMBNAILS,
            Command.STATUS_BAR,
            Command.THUMBNAILS_AT_SIDE,
            Command.THUMBNAIL_NAMES,
            Command.THUMBNAIL_CAPTIONS,
            Command.SORT_BY_NAME,
            Command.SORT_BY_DATE,
            Command.SORT_BY_SIZE,
            Command.REVERSE_ORDER);

    /**
     * The rows that delete, discard or reset (MENU-12), their title in the danger colour; whether each
     * asks first is the Confirm dialog's business, not the row's.
     */
    static final Set<Command> DESTRUCTIVE = Set.of(
            Command.MOVE_TO_TRASH,
            Command.CLEAR_MESSAGES,
            Command.CLEAR_DEBUG_LOG,
            Command.IMPORT_CONFIG,
            Command.BOOKMARKS_REMOVE);

    /** The width of a row's icon column, so a row without a glyph keeps its title in line (MENU-18). */
    static final double ICON_COLUMN = 22;

    /** The gap between the icon column and the title (MENU-18). */
    static final double TITLE_GAP = 8;

    /** The least room between the widest title and the chord column (MENU-18). */
    static final double CHORD_GAP = 18;

    /**
     * The mark a ticked row shows in its icon column, in place of its glyph while it is on: in the
     * title it pushed a toggle's words right of the rows around it.
     */
    static final String TICK = "\u2713";

    private static final String REASON_TIP = AppMenus.class.getName() + ".reason";

    private AppMenus() {}

    /** One in-window row: its item, the command it runs, and the labels it draws. */
    record Row(CustomMenuItem item, Command command, StackPane icon, Label title, Label chord) {}

    /** What the menu calls {@code command}: {@code label}, then its chord now, if it has one. */
    static String title(String label, Command command) {
        KeyCombination chord = command.chord();
        return chord == KeyCombination.NO_MATCH ? label : label + "   " + chord.getDisplayText();
    }

    /**
     * The menu bar drawn in the window (Windows and Linux): its rows {@link #inWindowRow}s, refreshed
     * as each menu opens.
     *
     * @param run runs a command, as its key would
     * @param unavailable why a command cannot run now, or null
     * @param ticked whether a {@link #TICKED} command is on now
     * @param label a command's label now ("Leave Full Screen" while in it)
     * @param say tells the user why a row they chose cannot run
     */
    static MenuBar inWindow(
            Consumer<Command> run,
            Function<Command, @Nullable String> unavailable,
            Predicate<Command> ticked,
            Function<Command, String> label,
            Consumer<String> say) {
        MenuBar bar = new MenuBar();
        bar.getStyleClass().add("app-menu-bar");
        for (Heading heading : HEADINGS) {
            Menu menu = new Menu(heading.title());
            List<Row> rows = new ArrayList<>();
            for (List<Command> group : heading.groups()) {
                if (!menu.getItems().isEmpty()) menu.getItems().add(new SeparatorMenuItem());
                for (Command command : group) {
                    Row row = inWindowRow(command);
                    row.item().setOnAction(e -> {
                        String reason = unavailable.apply(command);
                        if (reason == null) run.accept(command);
                        else say.accept(reason);
                    });
                    rows.add(row);
                    menu.getItems().add(row.item());
                }
            }
            menu.setOnShowing(e -> {
                for (Row row : rows) {
                    refresh(row, label.apply(row.command()), ticked.test(row.command()));
                    showAvailability(row, unavailable.apply(row.command()));
                }
            });
            // Measured once the popup has its CSS, when the labels know their real widths.
            menu.setOnShown(e -> alignChords(rows));
            bar.getMenus().add(menu);
        }
        return bar;
    }

    /**
     * An in-window row (MENU-02, MENU-18): the command's glyph in a column 22 px wide, the title 8 px
     * after it, a spacer, and the chord at least 18 px after the widest title; the chord in the
     * keyboard's colour at 11 px (MENU-15, MENU-16, the stylesheet's {@code .menu-item-chord}).
     */
    static Row inWindowRow(Command command) {
        StackPane icon = new StackPane();
        icon.getStyleClass().add("menu-item-icon");
        UiZoom.bind(icon.minWidthProperty(), ICON_COLUMN);
        UiZoom.bind(icon.prefWidthProperty(), ICON_COLUMN);
        Label title = new Label();
        title.getStyleClass().add("menu-item-title");
        Label chord = new Label();
        chord.getStyleClass().add("menu-item-chord");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox box = new HBox(icon, title, spacer, chord);
        UiZoom.hboxMargin(title, 0, 0, 0, TITLE_GAP);
        UiZoom.hboxMargin(chord, 0, 0, 0, CHORD_GAP);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setMaxWidth(Double.MAX_VALUE);
        // A row drawn from nodes reads as its title and chord to a screen reader (ACC-01).
        // A ticked row's tick, in the icon column, is heard before its title.
        box.accessibleTextProperty()
                .bind(Bindings.createStringBinding(
                        () -> {
                            boolean ticked = icon.getChildren().stream()
                                    .anyMatch(n -> n.getStyleClass().contains("menu-item-tick"));
                            String words =
                                    (ticked ? TICK + " " : "") + title.getText().strip();
                            return chord.getText().isEmpty() ? words : words + ", " + chord.getText();
                        },
                        title.textProperty(),
                        chord.textProperty(),
                        icon.getChildren()));
        CustomMenuItem item = new CustomMenuItem(box);
        item.setHideOnClick(true);
        item.setUserData(command);
        if (DESTRUCTIVE.contains(command)) item.getStyleClass().add("danger");
        Row row = new Row(item, command, icon, title, chord);
        item.getProperties().put(ROW, row);
        refresh(row, command.label(), false);
        return row;
    }

    /**
     * A right-click menu's row, drawn as the menu bar's (MENU-15, MENU-18): written as it is now, and
     * greyed with its reason when it cannot run (KBD-04), when choosing it says why instead.
     */
    static CustomMenuItem commandRow(
            Command command,
            Consumer<Command> run,
            Function<Command, @Nullable String> unavailable,
            Predicate<Command> ticked,
            Function<Command, String> label,
            Consumer<String> say) {
        Row row = inWindowRow(command);
        refresh(row, label.apply(command), ticked.test(command));
        String reason = unavailable.apply(command);
        showAvailability(row, reason);
        row.item().setOnAction(e -> {
            if (reason == null) run.accept(command);
            else say.accept(reason);
        });
        return row.item();
    }

    /**
     * A submenu drawn as a row: the glyph in the icon column and the title, so it lines up with the
     * {@link CustomMenuItem} rows around it.
     */
    static Menu submenuRow(String title, @Nullable Node glyph) {
        StackPane icon = new StackPane();
        icon.getStyleClass().add("menu-item-icon");
        UiZoom.bind(icon.minWidthProperty(), ICON_COLUMN);
        UiZoom.bind(icon.prefWidthProperty(), ICON_COLUMN);
        if (glyph != null) icon.getChildren().add(glyph);
        Label text = new Label(title);
        text.getStyleClass().add("menu-item-title");
        HBox box = new HBox(icon, text);
        UiZoom.hboxMargin(text, 0, 0, 0, TITLE_GAP);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setAccessibleText(title);
        Menu menu = new Menu("", box);
        menu.getStyleClass().add("menu-submenu");
        return menu;
    }

    /** Lines up the chords of {@code menu}'s rows each time it is shown ({@link #alignChords}). */
    static <T extends javafx.scene.control.ContextMenu> T aligned(T menu) {
        menu.setOnShown(e -> alignChords(menu.getItems().stream()
                .map(item -> item.getProperties().get(ROW))
                .filter(Row.class::isInstance)
                .map(Row.class::cast)
                .toList()));
        return menu;
    }

    static final String ROW = AppMenus.class.getName() + ".row";

    /**
     * Writes a row's glyph, title and chord as they are now: the tick in the glyph's place while its
     * setting or panel is on, the keymap's chord.
     */
    static void refresh(Row row, String label, boolean ticked) {
        Command command = row.command();
        boolean on = ticked && TICKED.contains(command);
        row.title().setText(label);
        // Not drawn (the content is), but what the row is called to code that asks it.
        row.item().setText(label);
        KeyCombination chord = command.chord();
        row.chord().setText(chord == KeyCombination.NO_MATCH ? "" : chord.getDisplayText());
        if (on) {
            Label tick = new Label(TICK);
            tick.getStyleClass().add("menu-item-tick");
            row.icon().getChildren().setAll(tick);
            return;
        }
        Node glyph = Icons.menu(command);
        if (glyph == null) row.icon().getChildren().clear();
        else row.icon().getChildren().setAll(glyph);
    }

    /**
     * A command that can't run now stays in its menu, greyed, and says why (KBD-04): the row keeps
     * working as a row, so its tooltip shows the reason and a screen reader hears it.
     */
    static void showAvailability(Row row, @Nullable String reason) {
        CustomMenuItem item = row.item();
        item.getStyleClass().remove("unavailable");
        Node content = item.getContent();
        if (content.getProperties().remove(REASON_TIP) instanceof Tooltip tip) Tooltip.uninstall(content, tip);
        content.setAccessibleHelp(reason);
        if (reason == null) return;
        item.getStyleClass().add("unavailable");
        Tooltip tip = Icons.tip(reason);
        Tooltip.install(content, tip);
        content.getProperties().put(REASON_TIP, tip);
    }

    /**
     * A submenu whose rows cannot run now stays, greyed, and says why on its own row (KBD-04): a
     * disabled row shows no tooltip, so the submenu keeps working as a row and its rows are greyed.
     */
    static void showAvailability(Menu submenu, @Nullable String reason) {
        submenu.getStyleClass().remove("unavailable");
        Node content = submenu.getGraphic();
        if (content == null) return;
        if (content.getProperties().remove(REASON_TIP) instanceof Tooltip tip) Tooltip.uninstall(content, tip);
        content.setAccessibleHelp(reason);
        for (MenuItem item : submenu.getItems()) item.setDisable(reason != null);
        if (reason == null) return;
        submenu.getStyleClass().add("unavailable");
        Tooltip tip = Icons.tip(reason);
        Tooltip.install(content, tip);
        content.getProperties().put(REASON_TIP, tip);
    }

    /** Gives a menu's titles one width, so its chords start in the same column. */
    static void alignChords(List<Row> rows) {
        double widest = 0;
        for (Row row : rows) {
            row.title().setMinWidth(Region.USE_PREF_SIZE);
            widest = Math.max(widest, row.title().prefWidth(-1));
        }
        for (Row row : rows) row.title().setMinWidth(widest);
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
    static MenuBar system(
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
