package com.mirada.ui;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import com.mirada.diagnostics.MessageLog;
import org.jspecify.annotations.Nullable;

import static com.mirada.i18n.Messages.tr;

/**
 * The Message Log: this session's status-bar messages, newest first, as a card over the window just
 * above the status bar (DIAG-01, MLOG-01), a card in the window's {@link OverlayHost}. The filter has
 * the keyboard when it opens; Escape, the keymap's cancel chord ({@code C-g} in Emacs) or a press
 * outside closes it (MLOG-03), and the focus goes back to where it was. The list moves on the
 * palette's keys ({@link PaletteKeys}, QOPEN-12). Copy, {@code Ctrl+C} ({@code Cmd+C}) and the
 * keymap's copy chord ({@code M-w} in Emacs, MLOG-04) copy what it shows or the selected entries;
 * Clear empties it. Built the first time it is opened.
 */
final class MessageLogView {

    static final double WIDTH = 773;
    static final double LIST_HEIGHT = 336;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    /** Makes the list again as the filter is typed, coalesced as the Bookmarks window's is. */
    private final FxThrottle filtering = new FxThrottle(BookmarksWindow.FILTER_PAUSE_MS, this::refresh);
    /** Makes the list again as messages come, once a pulse however many came (PERF-06). */
    private final Redraw redraw = new Redraw(this::refreshIfShowing);

    private final OverlayHost host;
    private final MessageLog messages;
    private final Consumer<Command> run;
    private final TextField filter = new TextField();
    private final ListView<MessageLog.Entry> list = new ListView<>();
    /** Says how many older messages the bounded log no longer holds. */
    private final Label cut = new Label();

    private final VBox card;
    /** The list's right-click menu, made as it opens; null until the window hands one over. */
    private @Nullable Supplier<ContextMenu> menu;

    MessageLogView(OverlayHost host, MessageLog messages, Consumer<Command> run) {
        this.host = host;
        this.messages = messages;
        this.run = run;

        Label title = new Label(tr("messageLog.title"));
        title.getStyleClass().add("message-log-title");
        filter.setPromptText(tr("messageLog.filterPrompt"));
        filter.getStyleClass().add("message-log-filter");
        filter.setAccessibleText(tr("messageLog.filterPrompt")); // the prompt goes when typing starts (ACC-01)
        // As the Bookmarks window's filter: the first key at once, then at most one list a pause (PERF-06).
        filter.textProperty().addListener((o, was, now) -> filtering.request());
        TextInputKeys.install(filter);

        list.getStyleClass().add("message-log-list");
        list.setAccessibleText(tr("messageLog.title"));
        UiZoom.bind(list.prefHeightProperty(), LIST_HEIGHT);
        list.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        list.setPlaceholder(new Label(tr("messageLog.empty")));
        list.setCellFactory(v -> new EntryCell());

        Button copy = new Button(tr("messageLog.copy"));
        copy.getStyleClass().add("flat");
        copy.setOnAction(e -> run.accept(Command.COPY_MESSAGES));
        Button clear = new Button(tr("messageLog.clear"));
        clear.getStyleClass().add("flat");
        clear.setOnAction(e -> run.accept(Command.CLEAR_MESSAGES));
        cut.getStyleClass().add("message-log-hint");
        Label hint = new Label(tr("messageLog.escToClose"));
        hint.getStyleClass().add("message-log-hint");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox footer = UiZoom.hbox(6, copy, clear, cut, spacer, hint);
        footer.setAlignment(Pos.CENTER_LEFT);

        card = UiZoom.vbox(8, title, filter, list, footer);
        card.getStyleClass().add("message-log");
        UiZoom.bind(card.prefWidthProperty(), WIDTH);
        UiZoom.bind(card.maxWidthProperty(), WIDTH);
        card.setMaxHeight(Region.USE_PREF_SIZE);
        StackPane.setAlignment(card, Pos.BOTTOM_LEFT);
        // On the card, before the field and the list take the keys.
        card.addEventFilter(KeyEvent.KEY_PRESSED, this::onKey);
        messages.addListener(() -> {
            if (isShowing()) redraw.request();
        });
    }

    /**
     * The list's right-click menu (MENU-05): Copy and Clear, the log's commands. A right-click on a
     * row selects it first (ListView's own), so Copy copies what was clicked (MENU-10).
     */
    void setMenu(Supplier<ContextMenu> menu) {
        this.menu = menu;
        list.setOnContextMenuRequested(e -> {
            Popups.show(menu.get(), list, e.getScreenX(), e.getScreenY());
            e.consume();
        });
    }

    /**
     * The list's menu from the Menu key or Shift+F10 (MENU-09): under the selected row, scrolled into
     * view first; with none selected, the first row is selected for it, as a right-click on it would.
     * An empty list has no row to open a menu for.
     */
    private void showMenuFromKeyboard() {
        Supplier<ContextMenu> making = menu;
        if (making == null || list.getItems().isEmpty()) return;
        if (list.getSelectionModel().isEmpty()) PaletteKeys.select(list, 0);
        list.applyCss();
        list.layout();
        javafx.geometry.Bounds row = Popups.selectedRow(list);
        if (row == null) return;
        javafx.geometry.Point2D at = Popups.under(row);
        Popups.show(making.get(), list, at.getX(), at.getY());
    }

    private void onKey(KeyEvent e) {
        if (Popups.isMenuKey(e)) {
            // The keyboard's right-click opens the selected row's menu (MENU-09), from the filter too.
            showMenuFromKeyboard();
            e.consume();
            return;
        }
        PaletteKeys.Action action = PaletteKeys.action(e, filter.isFocused());
        if (action == PaletteKeys.Action.CANCEL) {
            hide();
            e.consume();
        } else if (list.isFocused() && copies(e, Keybindings.current())) {
            run.accept(Command.COPY_MESSAGES);
            e.consume();
        } else if (PaletteKeys.isNavigation(action) || action == PaletteKeys.Action.ACCEPT && filter.isFocused()) {
            // The palette's moves (QOPEN-12); from the filter they carry the keyboard into the list.
            if (list.getItems().isEmpty()) return;
            if (!list.isFocused()) list.requestFocus();
            if (action == PaletteKeys.Action.ACCEPT) {
                if (list.getSelectionModel().isEmpty()) PaletteKeys.select(list, 0);
            } else {
                PaletteKeys.navigate(list, action, entry -> true);
            }
            e.consume();
        }
    }

    /**
     * Whether {@code e} copies the selected entries: {@code Ctrl+C} ({@code Cmd+C}) in every keymap,
     * and the keymap's own copy chord, {@code M-w} in Emacs (MLOG-04).
     */
    static boolean copies(KeyEvent e, Keybindings keys) {
        boolean platform = e.isShortcutDown() && !e.isShiftDown() && !e.isAltDown() && e.getCode() == KeyCode.C;
        return platform || keys.copies(e);
    }

    /** Opens the log above the status bar, {@code bottom} pixels up, and marks its errors read (MLOG-06). */
    void show(double bottom) {
        messages.markRead();
        filter.clear();
        refresh();
        list.getSelectionModel().clearSelection();
        list.scrollTo(0);
        UiZoom.stackMargin(card, 8, 8, bottom + 6, 8);
        // Transparent: the image stays as it is, but a press outside the card closes it (MLOG-03).
        host.show(card, "message-log-backdrop", null);
        filter.requestFocus();
    }

    void hide() {
        if (isShowing()) host.hide();
    }

    boolean isShowing() {
        return host.isShowing(card);
    }

    /** What Copy copies: while the log is open, the selected entries or else those it lists; else every entry. */
    List<MessageLog.Entry> listed() {
        if (!isShowing()) return messages.entries();
        List<MessageLog.Entry> selected = list.getSelectionModel().getSelectedItems();
        return List.copyOf(selected.isEmpty() ? list.getItems() : selected);
    }

    private void refreshIfShowing() {
        if (isShowing()) refresh();
    }

    private void refresh() {
        int dropped = messages.dropped();
        cut.setText(dropped == 0 ? "" : tr("messageLog.olderNotKept", dropped));
        list.getItems().setAll(matching(messages.entries(), filter.getText()));
    }

    /** The entries whose text holds {@code query}, ignoring case and the spaces around it. */
    static List<MessageLog.Entry> matching(List<MessageLog.Entry> entries, @Nullable String query) {
        String q = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return entries;
        return entries.stream()
                .filter(e -> e.text().toLowerCase(Locale.ROOT).contains(q))
                .toList();
    }

    /** Entries as text, one a line: the time as {@code HH:mm:ss}, then the message. */
    static String asText(List<MessageLog.Entry> entries) {
        return entries.stream()
                .map(e -> TIME.format(e.time()) + "  " + e.text())
                .collect(Collectors.joining("\n"));
    }

    private static final class EntryCell extends ListCell<MessageLog.Entry> {
        @Override
        protected void updateItem(MessageLog.@Nullable Entry entry, boolean empty) {
            super.updateItem(entry, empty);
            if (empty || entry == null) {
                setGraphic(null);
                setText(null);
                setAccessibleText(null);
                return;
            }
            Label time = new Label(TIME.format(entry.time()));
            time.getStyleClass().add("message-log-time");
            Label text = new Label(entry.text());
            text.setWrapText(true);
            text.setMinWidth(0);
            String style = StatusBar.styleOf(entry.severity());
            if (style != null) text.getStyleClass().add(style);
            HBox row = UiZoom.hbox(10, time, text);
            row.setAlignment(Pos.TOP_LEFT);
            setText(null);
            setGraphic(row);
            setAccessibleText(TIME.format(entry.time()) + "  " + entry.text()); // the row as it reads (ACC-01)
        }
    }
}
