package com.mirada.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

import com.mirada.image.Thumbnails;
import com.mirada.settings.Bookmarks;
import org.jspecify.annotations.Nullable;

/**
 * Every bookmark and note in one list, Editora's Bookmarks and Personal Notes tool windows moved to
 * pictures: grouped by folder ({@link BookmarkList}), each image with its thumbnail and its note,
 * found by typing a word of its name, its folder's or its note.
 *
 * <p>The keys stay in the filter field, as in the palette: Up and Down (Ctrl+P and Ctrl+N) move,
 * Escape clears the filter and then closes. What can be done to a row is a command ({@link
 * Command.Context#BOOKMARKS}, {@link #run}), on its key from the keymap (Return opens in the viewer,
 * F2 edits the note, Ctrl+B bookmarks, Ctrl+Delete removes the mark, Ctrl+1–3 list all, the
 * bookmarks or the notes), from the row's menu, and from the window's own command palette, which
 * the palette's keys open here as in the viewer and which lists every command. Delete also removes
 * while the filter is empty. A mark whose file has gone stays listed, dimmed, so it can be removed.
 * Built the first time it is opened; it follows every change to the marks while it is up.
 */
final class BookmarksWindow {

    private static final double WIDTH = 580;
    private static final double PICTURE = 56;
    private static final PseudoClass MISSING = PseudoClass.getPseudoClass("missing");
    private static final PseudoClass FOLDER = PseudoClass.getPseudoClass("folder");

    private final Stage stage = new Stage();
    private final Bookmarks bookmarks;
    /** Opens a mark in the viewer: the path, and whether it is a folder. */
    private final BiConsumer<Path, Boolean> opener;

    private final Consumer<Path> reveal;
    /** Runs a command the window's palette chose: the viewer's run, which hands this window's back to {@link #run}. */
    private final Consumer<Command> runner;

    private final Function<Command, @Nullable String> unavailable;
    private final String stylesheet;
    private final CommandPalette palette;
    private final Thumbnails thumbnails = new Thumbnails();

    private final TextField filter = new TextField();
    private final ToggleGroup showing = new ToggleGroup();
    private final ListView<BookmarkList.Row> list = new ListView<>();
    private final Label empty = new Label();
    private final Label count = new Label();
    private final Label hint = new Label();
    /** The marks whose file or folder was not there when they were last looked for. */
    private Set<Path> missing = Set.of();

    /**
     * How long the filter waits while typing goes on: the list is made again at most once in this
     * time, the first key at once (PERF-06).
     */
    static final int FILTER_PAUSE_MS = 150;

    /** Makes the list again as the filter is typed, coalesced ({@link #FILTER_PAUSE_MS}). */
    private final FxThrottle filtering = new FxThrottle(FILTER_PAUSE_MS, this::refresh);
    /** Looks for each mark's file or folder off the FX thread; only the latest look is taken. */
    private final LatestOnly looking = new LatestOnly(ViewerWindow.FILES, Platform::runLater);

    private BookmarkList.Show show = BookmarkList.Show.ALL;

    /**
     * @param runner runs any command, as the viewer's keys would: what the window's palette runs
     * @param unavailable why a command cannot run now, or null: the window's palette greys it
     */
    BookmarksWindow(
            Bookmarks bookmarks,
            BiConsumer<Path, Boolean> opener,
            Consumer<Path> reveal,
            Consumer<Command> runner,
            Function<Command, @Nullable String> unavailable,
            String stylesheet) {
        this.bookmarks = bookmarks;
        this.opener = opener;
        this.reveal = reveal;
        this.runner = runner;
        this.unavailable = unavailable;
        this.stylesheet = stylesheet;
        stage.setTitle("Bookmarks and Notes");
        WindowIcon.applyTo(stage);

        filter.setPromptText("Filter by name, folder or note");
        filter.getStyleClass().add("bookmarks-filter");
        filter.textProperty().addListener((o, was, now) -> filtering.request());
        HBox.setHgrow(filter, Priority.ALWAYS);

        HBox kinds = new HBox();
        kinds.getStyleClass().add("bookmarks-kinds");
        BookmarkList.Show[] all = BookmarkList.Show.values();
        for (int i = 0; i < all.length; i++) {
            BookmarkList.Show kind = all[i];
            ToggleButton button = new ToggleButton(kind.label);
            button.setUserData(kind);
            button.setToggleGroup(showing);
            button.setFocusTraversable(false);
            button.getStyleClass().add(i == 0 ? "left-pill" : i == all.length - 1 ? "right-pill" : "center-pill");
            kinds.getChildren().add(button);
        }
        showing.selectToggle(showing.getToggles().getFirst());
        showing.selectedToggleProperty().addListener((o, was, now) -> {
            // Always one of the three: a click on the one already chosen leaves it chosen.
            if (now == null) showing.selectToggle(was);
            else if (now.getUserData() instanceof BookmarkList.Show kind && kind != show) {
                show = kind;
                refresh();
            }
        });

        HBox top = new HBox(8, filter, kinds);
        top.setAlignment(Pos.CENTER_LEFT);
        top.setPadding(new Insets(12, 12, 8, 12));

        list.getStyleClass().add("bookmarks-list");
        // The filter keeps the keys; a click selects without taking them from it.
        list.setFocusTraversable(false);
        list.setCellFactory(view -> new Cell());
        empty.getStyleClass().add("info-empty");
        empty.setWrapText(true);
        list.setPlaceholder(empty);

        hint.setText(hintText());
        hint.getStyleClass().add("bookmarks-hint");
        hint.setTextOverrun(OverrunStyle.ELLIPSIS);
        hint.setMinWidth(0);
        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        count.getStyleClass().add("bookmarks-hint");
        count.setMinWidth(Region.USE_PREF_SIZE);
        HBox bottom = new HBox(8, hint, gap, count);
        bottom.setAlignment(Pos.CENTER_LEFT);
        bottom.setPadding(new Insets(6, 12, 8, 12));

        BorderPane content = new BorderPane(list, top, null, bottom, null);
        content.getStyleClass().add("bookmarks-window");
        // A root the palette can lie over, as the viewer's does.
        StackPane root = new StackPane(content);
        palette = new CommandPalette(root, runner);
        Scene scene = new Scene(root, WIDTH, 640);
        scene.getStylesheets().add(stylesheet);
        scene.addEventFilter(KeyEvent.KEY_PRESSED, this::onKey);
        Popups.closeOnPressOutside(stage, scene);
        Popups.menusByKey(scene);
        stage.setScene(scene);

        bookmarks.addListener(() -> {
            if (stage.isShowing()) marksChanged();
        });
    }

    /**
     * Shows the window beside {@code from}, or brings it forward if it is already up, with the
     * marks read again: another Mirada window may have added to them.
     */
    void show(Window from) {
        bookmarks.load();
        marksChanged();
        if (stage.isShowing()) {
            stage.toFront();
        } else {
            stage.setX(from.getX() + Math.max(0, from.getWidth() - WIDTH - 40));
            stage.setY(from.getY() + 60);
            stage.show();
        }
        filter.requestFocus();
        filter.selectAll();
    }

    boolean isShowing() {
        return stage.isShowing();
    }

    /** The list made again from the marks, the selection kept on the same row where it can be. */
    private void refresh() {
        BookmarkList.Row was = list.getSelectionModel().getSelectedItem();
        int index = list.getSelectionModel().getSelectedIndex();
        List<Bookmarks.Mark> marks = bookmarks.all();
        List<BookmarkList.Row> rows = BookmarkList.rows(marks, filter.getText(), show);
        list.getItems().setAll(rows);
        int keep = BookmarkList.keep(rows, was == null ? null : was.path(), Math.max(index, 0));
        if (keep >= 0) {
            list.getSelectionModel().select(keep);
            list.scrollTo(Math.max(0, keep - 2));
        }
        int images = (int) rows.stream()
                .filter(row -> row instanceof BookmarkList.ImageRow)
                .count();
        int folders = (int) rows.stream()
                .filter(row -> row instanceof BookmarkList.FolderRow folder && folder.mark() != null)
                .count();
        count.setText(counted(images, "image") + (folders == 0 ? "" : ", " + counted(folders, "folder")));
        empty.setText(
                marks.isEmpty()
                        ? "Nothing is bookmarked or noted yet. In the viewer, B bookmarks the image and N writes a"
                                + " note on it; Shift+B and Shift+N do the same for its folder."
                        : "Nothing matches.");
    }

    /**
     * The list made again for marks that changed, and each one's file or folder looked for off the FX
     * thread (PERF-01): the list is made once more when the answer comes. Typing in the filter looks
     * for nothing.
     */
    private void marksChanged() {
        refresh();
        List<Bookmarks.Mark> marks = bookmarks.all();
        looking.submit(() -> gone(marks), gone -> {
            missing = gone;
            refresh();
        });
    }

    /** The paths of {@code marks} whose file or folder is not there. Reads the disk: never on the FX thread. */
    static Set<Path> gone(List<Bookmarks.Mark> marks) {
        Set<Path> gone = new HashSet<>();
        for (Bookmarks.Mark mark : marks) {
            boolean there = mark.folder() ? Files.isDirectory(mark.path()) : Files.isRegularFile(mark.path());
            if (!there) gone.add(mark.path());
        }
        return Set.copyOf(gone);
    }

    static String counted(int n, String what) {
        return n + " " + what + (n == 1 ? "" : "s");
    }

    private void onKey(KeyEvent e) {
        // While the palette is up, keys are its own; its chord closes it, unless it moves by that key.
        boolean paletteKey = PaletteChords.opens(e);
        if (palette.isShowing()) {
            if (paletteKey && !PaletteChords.keptByPalette(KeyChord.of(e))) {
                palette.hide();
                e.consume();
            }
            return;
        }
        if (paletteKey) {
            palette.show(Command.inPalette(Command.Context.BOOKMARKS), unavailable);
            e.consume();
            return;
        }
        Command command = Keybindings.current().commandFor(Command.Context.BOOKMARKS, e);
        if (command != null) {
            run(command);
            e.consume();
            return;
        }
        KeyCode code = e.getCode();
        boolean control = e.isControlDown() && !e.isAltDown();
        if (code == KeyCode.ESCAPE) {
            if (filter.getText().isEmpty()) stage.hide();
            else filter.clear();
        } else if (code == KeyCode.UP || (control && code == KeyCode.P)) {
            move(-1);
        } else if (code == KeyCode.DOWN || (control && code == KeyCode.N)) {
            move(1);
        } else if (code == KeyCode.PAGE_UP) {
            move(-10);
        } else if (code == KeyCode.PAGE_DOWN) {
            move(10);
        } else if (Popups.isMenuKey(e)) {
            showMenuFromKeyboard();
        } else if (code == KeyCode.DELETE
                && !e.isShortcutDown()
                && filter.getText().isEmpty()) {
            // The field has nothing to delete, so Delete is the row's.
            run(Command.BOOKMARKS_REMOVE);
        } else if (e.isShortcutDown() && (code == KeyCode.F || code == KeyCode.L)) {
            filter.requestFocus();
            filter.selectAll();
        } else if (e.isShortcutDown() && code == KeyCode.W) {
            stage.hide();
        } else {
            return;
        }
        e.consume();
    }

    /** Shows the keys in force again after the settings changed them: the hint and the palette's chips. */
    void refreshKeys() {
        hint.setText(hintText());
        palette.refreshKeys();
    }

    private static String hintText() {
        return key(Command.BOOKMARKS_OPEN) + " opens · " + key(Command.BOOKMARKS_EDIT_NOTE) + " note · "
                + key(Command.BOOKMARKS_TOGGLE) + " bookmark · " + key(Command.BOOKMARKS_REMOVE) + " removes";
    }

    /** Runs one of the window's commands on the selected row, from its key, its menu or a palette. */
    void run(Command command) {
        BookmarkList.Row row = selected();
        switch (command) {
            case BOOKMARKS_OPEN -> open(row);
            case BOOKMARKS_EDIT_NOTE -> editNote(row);
            case BOOKMARKS_TOGGLE -> toggle(row);
            case BOOKMARKS_SHOW_IN_FOLDER -> showInFolder(row);
            case BOOKMARKS_REMOVE -> remove(row);
            case BOOKMARKS_LIST_ALL -> list(BookmarkList.Show.ALL);
            case BOOKMARKS_LIST_BOOKMARKED -> list(BookmarkList.Show.BOOKMARKS);
            case BOOKMARKS_LIST_NOTES -> list(BookmarkList.Show.NOTES);
            default -> runner.accept(command);
        }
    }

    /** Why one of the window's commands cannot run now, or null when it can. */
    @Nullable
    String unavailable(Command command) {
        BookmarkList.Row row = selected();
        return switch (command) {
            case BOOKMARKS_OPEN, BOOKMARKS_SHOW_IN_FOLDER ->
                row == null
                        ? "Nothing is selected."
                        : missing.contains(row.path())
                                ? (row instanceof BookmarkList.FolderRow ? "The folder" : "The file") + " is not there."
                                : null;
            case BOOKMARKS_EDIT_NOTE, BOOKMARKS_TOGGLE -> row == null ? "Nothing is selected." : null;
            case BOOKMARKS_REMOVE ->
                row == null ? "Nothing is selected." : markOf(row) == null ? "The folder itself has no mark." : null;
            default -> null;
        };
    }

    /** Lists all marks, the bookmarks or the notes, as the buttons at the top do. */
    private void list(BookmarkList.Show kind) {
        showing.getToggles().stream()
                .filter(toggle -> toggle.getUserData() == kind)
                .findFirst()
                .ifPresent(showing::selectToggle);
    }

    /** The key shown for {@code command}, or its label for none. */
    private static String key(Command command) {
        KeyCombination chord = command.chord();
        return chord == KeyCombination.NO_MATCH ? command.label() : chord.getDisplayText();
    }

    /** Moves the selection by {@code delta} rows, stopping at either end. */
    private void move(int delta) {
        int size = list.getItems().size();
        if (size == 0) return;
        int at = list.getSelectionModel().getSelectedIndex();
        int next = at < 0 ? (delta > 0 ? 0 : size - 1) : Math.clamp(at + delta, 0, size - 1);
        list.getSelectionModel().select(next);
        list.scrollTo(Math.max(0, next - 2));
    }

    private BookmarkList.@Nullable Row selected() {
        return list.getSelectionModel().getSelectedItem();
    }

    /** The mark on the row: an image's, or a folder's when the folder itself is marked. */
    private static Bookmarks.@Nullable Mark markOf(BookmarkList.@Nullable Row row) {
        return switch (row) {
            case null -> null;
            case BookmarkList.ImageRow image -> image.mark();
            case BookmarkList.FolderRow folder -> folder.mark();
        };
    }

    private void open(BookmarkList.@Nullable Row row) {
        if (row == null) return;
        if (missing.contains(row.path())) return;
        opener.accept(row.path(), row instanceof BookmarkList.FolderRow);
    }

    private void editNote(BookmarkList.@Nullable Row row) {
        if (row == null) return;
        boolean folder = row instanceof BookmarkList.FolderRow;
        String about = folder ? "the folder " + BookmarkList.name(row.path()) : BookmarkList.name(row.path());
        Optional<String> answer =
                NoteDialog.ask(stage, folder ? "Folder Note" : "Note", about, bookmarks.note(row.path()), stylesheet);
        answer.ifPresent(note -> bookmarks.setNote(row.path(), folder, note));
        filter.requestFocus();
    }

    private void showInFolder(BookmarkList.@Nullable Row row) {
        if (row != null && !missing.contains(row.path())) reveal.accept(row.path());
    }

    private void toggle(BookmarkList.@Nullable Row row) {
        if (row != null) bookmarks.toggle(row.path(), row instanceof BookmarkList.FolderRow);
    }

    /** Takes the mark off the row; a note, which cannot be typed again from memory, asks first. */
    private void remove(BookmarkList.@Nullable Row row) {
        Bookmarks.Mark mark = markOf(row);
        if (row == null || mark == null) return;
        if (mark.hasNote()
                && !Confirm.ask(Confirm.removeMark(BookmarkList.name(mark.path()), mark.bookmarked()), stage)) {
            return;
        }
        bookmarks.remove(mark.path());
        filter.requestFocus();
    }

    /**
     * The selected row's menu, opened by the Menu key or Shift+F10 under the row, scrolled into view
     * first (MENU-09): the keys stay in the filter field, so they never reach a row of their own.
     */
    private void showMenuFromKeyboard() {
        BookmarkList.Row row = list.getSelectionModel().getSelectedItem();
        Bounds bounds = Popups.selectedRow(list);
        if (row == null || bounds == null) return;
        Point2D at = Popups.under(bounds);
        Popups.show(menu(row), list, at.getX(), at.getY());
    }

    private ContextMenu menu(BookmarkList.Row row) {
        Bookmarks.Mark mark = markOf(row);
        boolean folder = row instanceof BookmarkList.FolderRow;
        MenuItem open =
                new MenuItem(folder ? "Open Folder" : "Open", Icons.of(folder ? Icons.FOLDER_OPEN : Icons.PICTURE));
        MenuChord.on(open, Command.BOOKMARKS_OPEN);
        open.setOnAction(e -> open(row));
        open.setDisable(missing.contains(row.path()));
        MenuItem note = new MenuItem(mark != null && mark.hasNote() ? "Edit Note…" : "Add Note…", Icons.of(Icons.NOTE));
        MenuChord.on(note, Command.BOOKMARKS_EDIT_NOTE);
        note.setOnAction(e -> editNote(row));
        boolean bookmarked = mark != null && mark.bookmarked();
        MenuItem bookmark = new MenuItem(
                bookmarked ? "Take the Bookmark Off" : folder ? "Bookmark Folder" : "Bookmark Image",
                Icons.of(Icons.BOOKMARK));
        MenuChord.on(bookmark, Command.BOOKMARKS_TOGGLE);
        bookmark.setOnAction(e -> toggle(row));
        MenuItem show = new MenuItem("Show in Folder", Icons.of(Icons.FOLDER));
        MenuChord.on(show, Command.BOOKMARKS_SHOW_IN_FOLDER);
        show.setOnAction(e -> showInFolder(row));
        show.setDisable(missing.contains(row.path()));
        MenuItem remove = new MenuItem("Remove", Icons.of(Icons.REMOVE_MARK));
        MenuChord.on(remove, Command.BOOKMARKS_REMOVE);
        remove.setOnAction(e -> remove(row));
        remove.setDisable(mark == null);
        ContextMenu menu =
                new ContextMenu(open, show, new SeparatorMenuItem(), note, bookmark, new SeparatorMenuItem(), remove);
        Popups.closedByWindow(menu);
        return menu;
    }

    private final class Cell extends ListCell<BookmarkList.Row> {
        private final ImageView picture = new ImageView();
        private final StackPane frame = new StackPane(picture);
        private final Node folderIcon = Icons.of(Icons.FOLDER);
        private final Label name = new Label();
        private final Label where = new Label();
        private final Label note = new Label();
        private final Node bookmarkIcon = Icons.of(Icons.BOOKMARK);
        private final HBox title = new HBox(6);
        private final VBox text = new VBox(2);
        private final HBox row = new HBox(10);
        /** The file whose thumbnail this cell asked for, until it shows another. */
        private @Nullable Path asked;

        Cell() {
            picture.setPreserveRatio(true);
            picture.setSmooth(true);
            picture.setFitWidth(PICTURE);
            picture.setFitHeight(PICTURE);
            frame.getStyleClass().add("bookmarks-picture");
            frame.setMinSize(PICTURE, PICTURE);
            frame.setPrefSize(PICTURE, PICTURE);
            frame.setMaxSize(PICTURE, PICTURE);
            name.getStyleClass().add("bookmarks-name");
            name.setTextOverrun(OverrunStyle.CENTER_ELLIPSIS);
            name.setMinWidth(0);
            where.getStyleClass().add("bookmarks-where");
            where.setTextOverrun(OverrunStyle.LEADING_ELLIPSIS);
            where.setMinWidth(0);
            note.getStyleClass().add("bookmarks-note");
            note.setWrapText(true);
            note.setMinWidth(0);
            // A long note shows its start; the note window shows all of it.
            note.setMaxHeight(60);
            bookmarkIcon.getStyleClass().add("thumbnail-mark");
            title.setAlignment(Pos.CENTER_LEFT);
            text.setMinWidth(0);
            HBox.setHgrow(text, Priority.ALWAYS);
            row.setAlignment(Pos.CENTER_LEFT);
            getStyleClass().add("bookmarks-cell");
            // As wide as the list, never as the longest path or note: those cut or wrap instead.
            setPrefWidth(0);
            setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2 && !isEmpty()) open(getItem());
            });
            setOnContextMenuRequested(e -> {
                BookmarkList.Row item = getItem();
                if (isEmpty() || item == null) return;
                getListView().getSelectionModel().select(getIndex());
                Popups.show(menu(item), this, e.getScreenX(), e.getScreenY());
                e.consume();
            });
        }

        @Override
        protected void updateItem(BookmarkList.@Nullable Row item, boolean empty) {
            super.updateItem(item, empty);
            picture.setImage(null);
            Path previous = asked;
            if (previous != null && (empty || item == null || !previous.equals(item.path()))) {
                thumbnails.release(previous);
                asked = null;
            }
            if (empty || item == null) {
                setGraphic(null);
                setText(null);
                pseudoClassStateChanged(MISSING, false);
                pseudoClassStateChanged(FOLDER, false);
                return;
            }
            boolean gone = missing.contains(item.path());
            pseudoClassStateChanged(MISSING, gone);
            pseudoClassStateChanged(FOLDER, item instanceof BookmarkList.FolderRow);
            Bookmarks.Mark mark = markOf(item);
            name.setText(BookmarkList.name(item.path()));
            title.getChildren().setAll(name);
            if (mark != null && mark.bookmarked()) title.getChildren().add(bookmarkIcon);
            String said = mark == null ? "" : mark.note();
            if (gone)
                said = (item instanceof BookmarkList.FolderRow ? "Folder" : "File")
                        + " not found: moved, deleted, or on a drive that is not there."
                        + (said.isEmpty() ? "" : "\n" + said);
            note.setText(said);
            text.getChildren().setAll(title);
            if (item instanceof BookmarkList.FolderRow) {
                Path parent = item.path().getParent();
                where.setText(parent == null ? "" : parent.toString());
                if (!where.getText().isEmpty()) text.getChildren().add(where);
                if (!said.isEmpty()) text.getChildren().add(note);
                row.getChildren().setAll(folderIcon, text);
                row.setPadding(new Insets(6, 0, 2, 0));
            } else {
                if (!said.isEmpty()) text.getChildren().add(note);
                row.getChildren().setAll(frame, text);
                row.setPadding(new Insets(0, 0, 0, 22));
                if (!gone) {
                    Path file = item.path();
                    asked = file;
                    thumbnails
                            .get(file)
                            .thenAcceptAsync(
                                    thumbnail -> {
                                        BookmarkList.Row now = getItem();
                                        if (now == null || !file.equals(now.path())) return;
                                        picture.setRotate(
                                                thumbnail.orientation().degrees());
                                        picture.setScaleX(
                                                thumbnail.orientation().mirrored() ? -1 : 1);
                                        picture.setImage(thumbnail.image());
                                    },
                                    Platform::runLater);
                }
            }
            setText(null);
            setGraphic(row);
        }
    }

    /** Its stage, closed with the viewer's. */
    Stage stage() {
        return stage;
    }

    /** Closes it for good, with the viewer: its thumbnail workers stop. */
    void close() {
        filtering.cancel();
        looking.cancel();
        stage.close();
        thumbnails.close();
    }
}
