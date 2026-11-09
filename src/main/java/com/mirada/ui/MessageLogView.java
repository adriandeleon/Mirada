package com.mirada.ui;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
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

/**
 * The Message Log: this session's status-bar messages, newest first, as a card over the window just
 * above the status bar (DIAG-01, MLOG-01, Pauta 4.0.1). The filter has the keyboard when it opens;
 * Escape, {@code C-g} or a click outside closes it; Copy copies what it shows, Clear empties it.
 * Built the first time it is opened.
 */
final class MessageLogView {

    static final double WIDTH = 773;
    static final double LIST_HEIGHT = 336;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    /** Makes the list again as the filter is typed, coalesced as the Bookmarks window's is. */
    private final FxThrottle filtering = new FxThrottle(BookmarksWindow.FILTER_PAUSE_MS, this::refresh);

    private final StackPane host;
    private final MessageLog messages;
    private final Consumer<Command> run;
    private final Region backdrop = new Region();
    private final TextField filter = new TextField();
    private final ListView<MessageLog.Entry> list = new ListView<>();
    /** Says how many older messages the bounded log no longer holds. */
    private final Label cut = new Label();

    private final VBox card;

    MessageLogView(StackPane host, MessageLog messages, Consumer<Command> run) {
        this.host = host;
        this.messages = messages;
        this.run = run;

        // Transparent: the image stays as it is, but a press outside the card closes it (MLOG-03).
        backdrop.getStyleClass().add("message-log-backdrop");
        backdrop.setOnMousePressed(e -> {
            hide();
            e.consume();
        });

        Label title = new Label("Messages");
        title.getStyleClass().add("message-log-title");
        filter.setPromptText("Filter messages…");
        filter.getStyleClass().add("message-log-filter");
        // As the Bookmarks window's filter: the first key at once, then at most one list a pause (PERF-06).
        filter.textProperty().addListener((o, was, now) -> filtering.request());
        TextInputKeys.install(filter);

        list.getStyleClass().add("message-log-list");
        list.setPrefHeight(LIST_HEIGHT);
        list.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        list.setPlaceholder(new Label("No messages yet"));
        list.setCellFactory(v -> new EntryCell());

        Button copy = new Button("Copy");
        copy.getStyleClass().add("flat");
        copy.setOnAction(e -> run.accept(Command.COPY_MESSAGES));
        Button clear = new Button("Clear");
        clear.getStyleClass().add("flat");
        clear.setOnAction(e -> run.accept(Command.CLEAR_MESSAGES));
        cut.getStyleClass().add("message-log-hint");
        Label hint = new Label("Esc to close");
        hint.getStyleClass().add("message-log-hint");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox footer = new HBox(6, copy, clear, cut, spacer, hint);
        footer.setAlignment(Pos.CENTER_LEFT);

        card = new VBox(8, title, filter, list, footer);
        card.getStyleClass().add("message-log");
        card.setPrefWidth(WIDTH);
        card.setMaxWidth(WIDTH);
        card.setMaxHeight(Region.USE_PREF_SIZE);
        StackPane.setAlignment(card, Pos.BOTTOM_LEFT);
        // On the card, before the field and the list take the keys.
        card.addEventFilter(KeyEvent.KEY_PRESSED, this::onKey);
        messages.addListener(() -> {
            if (isShowing()) refresh();
        });
    }

    private void onKey(KeyEvent e) {
        if (closes(e.getCode(), e.isControlDown(), e.isShiftDown() || e.isAltDown() || e.isMetaDown())) {
            hide();
            e.consume();
        } else if (e.isShortcutDown() && !e.isShiftDown() && e.getCode() == KeyCode.C && list.isFocused()) {
            run.accept(Command.COPY_MESSAGES);
            e.consume();
        } else if ((e.getCode() == KeyCode.DOWN || e.getCode() == KeyCode.ENTER)
                && filter.isFocused()
                && !list.getItems().isEmpty()) {
            list.requestFocus();
            if (list.getSelectionModel().isEmpty()) list.getSelectionModel().selectFirst();
            e.consume();
        }
    }

    /** Escape, or Emacs's {@code C-g}, closes the log (MLOG-03). */
    static boolean closes(KeyCode code, boolean control, boolean otherModifiers) {
        if (otherModifiers) return false;
        return code == KeyCode.ESCAPE && !control || code == KeyCode.G && control;
    }

    /** Opens the log above the status bar, {@code bottom} pixels up, and marks its errors read (MLOG-06). */
    void show(double bottom) {
        messages.markRead();
        filter.clear();
        refresh();
        list.getSelectionModel().clearSelection();
        list.scrollTo(0);
        StackPane.setMargin(card, new Insets(8, 8, bottom + 6, 8));
        if (!isShowing()) host.getChildren().addAll(backdrop, card);
        filter.requestFocus();
    }

    void hide() {
        host.getChildren().removeAll(backdrop, card);
    }

    boolean isShowing() {
        return host.getChildren().contains(card);
    }

    /** What Copy copies: while the log is open, the selected entries or else those it lists; else every entry. */
    List<MessageLog.Entry> listed() {
        if (!isShowing()) return messages.entries();
        List<MessageLog.Entry> selected = list.getSelectionModel().getSelectedItems();
        return List.copyOf(selected.isEmpty() ? list.getItems() : selected);
    }

    private void refresh() {
        int dropped = messages.dropped();
        cut.setText(dropped == 0 ? "" : dropped + " older not kept");
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
                return;
            }
            Label time = new Label(TIME.format(entry.time()));
            time.getStyleClass().add("message-log-time");
            Label text = new Label(entry.text());
            text.setWrapText(true);
            text.setMinWidth(0);
            String style = StatusBar.styleOf(entry.severity());
            if (style != null) text.getStyleClass().add(style);
            HBox row = new HBox(10, time, text);
            row.setAlignment(Pos.TOP_LEFT);
            setText(null);
            setGraphic(row);
        }
    }
}
