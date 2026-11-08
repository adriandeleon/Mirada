package com.mirada.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
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
import javafx.scene.input.KeyCodeCombination;
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
 * Return opens in the viewer, F2 edits the note, Ctrl+B bookmarks, Ctrl+Delete removes the mark
 * (Delete too while the filter is empty), Ctrl+1–3 list all, the bookmarks or the notes, Escape
 * clears the filter and then closes. A mark whose file has gone stays listed, dimmed, so it can be
 * removed. Built the first time it is opened; it follows every change to the marks while it is up.
 */
final class BookmarksWindow {

    private static final double WIDTH = 580;
    private static final double PICTURE = 56;
    private static final PseudoClass MISSING = PseudoClass.getPseudoClass("missing");
    private static final PseudoClass FOLDER = PseudoClass.getPseudoClass("folder");

    private static final KeyCombination EDIT_NOTE = new KeyCodeCombination(KeyCode.F2);
    private static final KeyCombination TOGGLE = new KeyCodeCombination(KeyCode.B, KeyCombination.SHORTCUT_DOWN);
    private static final KeyCombination REMOVE =
            new KeyCodeCombination(Command.MAC ? KeyCode.BACK_SPACE : KeyCode.DELETE, KeyCombination.SHORTCUT_DOWN);

    private final Stage stage = new Stage();
    private final Bookmarks bookmarks;
    /** Opens a mark in the viewer: the path, and whether it is a folder. */
    private final BiConsumer<Path, Boolean> opener;

    private final Consumer<Path> reveal;
    private final String stylesheet;
    private final Thumbnails thumbnails = new Thumbnails();

    private final TextField filter = new TextField();
    private final ToggleGroup showing = new ToggleGroup();
    private final ListView<BookmarkList.Row> list = new ListView<>();
    private final Label empty = new Label();
    private final Label count = new Label();
    /** The marks whose file or folder was not there when the list was last made. */
    private final Set<Path> missing = new HashSet<>();

    private BookmarkList.Show show = BookmarkList.Show.ALL;

    BookmarksWindow(Bookmarks bookmarks, BiConsumer<Path, Boolean> opener, Consumer<Path> reveal, String stylesheet) {
        this.bookmarks = bookmarks;
        this.opener = opener;
        this.reveal = reveal;
        this.stylesheet = stylesheet;
        stage.setTitle("Bookmarks and Notes");
        WindowIcon.applyTo(stage);

        filter.setPromptText("Filter by name, folder or note");
        filter.getStyleClass().add("bookmarks-filter");
        filter.textProperty().addListener((o, was, now) -> refresh());
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

        Label hint = new Label("Return opens · " + EDIT_NOTE.getDisplayText() + " note · " + TOGGLE.getDisplayText()
                + " bookmark · " + REMOVE.getDisplayText() + " removes");
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

        BorderPane root = new BorderPane(list, top, null, bottom, null);
        root.getStyleClass().add("bookmarks-window");
        Scene scene = new Scene(root, WIDTH, 640);
        scene.getStylesheets().add(stylesheet);
        scene.addEventFilter(KeyEvent.KEY_PRESSED, this::onKey);
        Popups.closeOnPressOutside(stage, scene);
        stage.setScene(scene);

        bookmarks.addListener(() -> {
            if (stage.isShowing()) refresh();
        });
    }

    /**
     * Shows the window beside {@code from}, or brings it forward if it is already up, with the
     * marks read again: another Mirada window may have added to them.
     */
    void show(Window from) {
        bookmarks.load();
        refresh();
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
        missing.clear();
        for (Bookmarks.Mark mark : marks) {
            boolean there = mark.folder() ? Files.isDirectory(mark.path()) : Files.isRegularFile(mark.path());
            if (!there) missing.add(mark.path());
        }
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

    static String counted(int n, String what) {
        return n + " " + what + (n == 1 ? "" : "s");
    }

    private void onKey(KeyEvent e) {
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
        } else if (code == KeyCode.ENTER) {
            open(selected());
        } else if (EDIT_NOTE.match(e) || (e.isShortcutDown() && code == KeyCode.E)) {
            editNote(selected());
        } else if (TOGGLE.match(e)) {
            toggle(selected());
        } else if (REMOVE.match(e)
                || (code == KeyCode.DELETE
                        && !e.isShortcutDown()
                        && filter.getText().isEmpty())) {
            remove(selected());
        } else if (e.isShortcutDown() && (code == KeyCode.F || code == KeyCode.L)) {
            filter.requestFocus();
            filter.selectAll();
        } else if (e.isShortcutDown() && code == KeyCode.W) {
            stage.hide();
        } else if (e.isShortcutDown() && code.isDigitKey() && kind(code) != null) {
            BookmarkList.Show kind = kind(code);
            showing.getToggles().stream()
                    .filter(toggle -> toggle.getUserData() == kind)
                    .findFirst()
                    .ifPresent(showing::selectToggle);
        } else {
            return;
        }
        e.consume();
    }

    private static BookmarkList.@Nullable Show kind(KeyCode code) {
        return switch (code) {
            case DIGIT1, NUMPAD1 -> BookmarkList.Show.ALL;
            case DIGIT2, NUMPAD2 -> BookmarkList.Show.BOOKMARKS;
            case DIGIT3, NUMPAD3 -> BookmarkList.Show.NOTES;
            default -> null;
        };
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

    private ContextMenu menu(BookmarkList.Row row) {
        Bookmarks.Mark mark = markOf(row);
        boolean folder = row instanceof BookmarkList.FolderRow;
        MenuItem open =
                new MenuItem(folder ? "Open Folder" : "Open", Icons.of(folder ? Icons.FOLDER_OPEN : Icons.PICTURE));
        open.setAccelerator(MenuChord.shown(new KeyCodeCombination(KeyCode.ENTER)));
        open.setOnAction(e -> open(row));
        open.setDisable(missing.contains(row.path()));
        MenuItem note = new MenuItem(mark != null && mark.hasNote() ? "Edit Note…" : "Add Note…", Icons.of(Icons.NOTE));
        note.setAccelerator(MenuChord.shown(EDIT_NOTE));
        note.setOnAction(e -> editNote(row));
        boolean bookmarked = mark != null && mark.bookmarked();
        MenuItem bookmark = new MenuItem(
                bookmarked ? "Take the Bookmark Off" : folder ? "Bookmark Folder" : "Bookmark Image",
                Icons.of(Icons.BOOKMARK));
        bookmark.setAccelerator(MenuChord.shown(TOGGLE));
        bookmark.setOnAction(e -> toggle(row));
        MenuItem show = new MenuItem("Show in Folder", Icons.of(Icons.FOLDER));
        show.setOnAction(e -> reveal.accept(row.path()));
        show.setDisable(missing.contains(row.path()));
        MenuItem remove = new MenuItem("Remove", Icons.of(Icons.TRASH));
        remove.setAccelerator(MenuChord.shown(REMOVE));
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
                menu(item).show(this, e.getScreenX(), e.getScreenY());
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
}
