package com.mirada.ui;

import java.util.Objects;
import java.util.function.Consumer;

import javafx.geometry.Pos;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

import com.mirada.diagnostics.MessageLog;
import com.mirada.update.ReleaseInfo;
import com.mirada.update.UpdateCoordinator;
import org.jspecify.annotations.Nullable;

/**
 * The status bar along the bottom of the window (WIN-01, Pauta 4.0.1). Its left side is the echo
 * area: one line saying what the last command did, errors in red and warnings in amber; a click on it,
 * or on the count of unread errors beside it, opens the Message Log. On the right, a newer release's
 * notice, which opens its page.
 *
 * <p>The bar takes its height when the window is made, so the image is fitted once; its labels are
 * added after the first paint ({@link #finish}), like the rest of the chrome nobody reads before the
 * image (AGENTS.md).
 */
final class StatusBar {

    /** The bar's height, held before its labels exist so the first image is fitted to the final space. */
    static final double HEIGHT = 26;

    static final String ERROR = "status-echo-error";
    static final String WARNING = "status-echo-warn";

    private final MessageLog messages;
    private final UpdateCoordinator updates;
    private final Consumer<Command> run;
    private final HBox bar = new HBox(10);

    /** The bar's contents, made after the first paint. */
    private record Parts(Label echo, Label unread, Hyperlink update) {}

    private @Nullable Parts parts;

    StatusBar(MessageLog messages, UpdateCoordinator updates, Consumer<Command> run) {
        this.messages = messages;
        this.updates = updates;
        this.run = run;
        bar.getStyleClass().add("status-bar");
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setMinHeight(HEIGHT);
        bar.setPrefHeight(HEIGHT);
        bar.setMaxHeight(HEIGHT);
    }

    /** Adds the echo area, the unread count and the update notice: after the first paint. */
    void finish() {
        if (parts != null) return;
        Label echo = new Label();
        Label unread = new Label();
        Hyperlink update = new Hyperlink();
        parts = new Parts(echo, unread, update);
        echo.getStyleClass().add("status-echo");
        echo.setMaxWidth(Double.MAX_VALUE);
        echo.setMinWidth(0);
        // A click runs the Message Log command, as its menu item and the palette do (WIN-01, MLOG-02).
        echo.setOnMouseClicked(e -> run.accept(Command.MESSAGE_LOG));
        echo.setTooltip(new Tooltip("Message Log: everything said this session"));
        unread.getStyleClass().add("status-unread");
        unread.setOnMouseClicked(e -> run.accept(Command.MESSAGE_LOG));
        unread.setTooltip(new Tooltip("Errors since the Message Log was last opened"));
        update.getStyleClass().add("status-update");
        update.setFocusTraversable(false);
        update.setOnAction(e -> run.accept(Command.OPEN_RELEASE));
        Region spacer = new Region();
        HBox.setHgrow(echo, Priority.SOMETIMES);
        HBox.setHgrow(spacer, Priority.ALWAYS);
        bar.getChildren().setAll(echo, unread, spacer, update);
        messages.addListener(this::refresh);
        updates.addListener(this::refreshUpdate);
        refresh();
        refreshUpdate();
    }

    /** The newest message in the echo area, coloured by its severity, and the unread count. */
    private void refresh() {
        Parts built = parts;
        if (built == null) return;
        Label echo = built.echo();
        Label unread = built.unread();
        MessageLog.@Nullable Entry newest = messages.newest();
        echo.setText(newest == null ? "" : newest.text());
        echo.getStyleClass().removeAll(ERROR, WARNING);
        String style = newest == null ? null : styleOf(newest.severity());
        if (style != null) echo.getStyleClass().add(style);
        int count = messages.unreadErrors();
        unread.setText(String.valueOf(count));
        unread.setVisible(count > 0);
        unread.setManaged(count > 0);
    }

    /** The echo area's style for a severity: red for an error, amber for a warning, else none. */
    static @Nullable String styleOf(MessageLog.Severity severity) {
        return switch (severity) {
            case ERROR -> ERROR;
            case WARNING -> WARNING;
            case INFO -> null;
        };
    }

    private void refreshUpdate() {
        Parts built = parts;
        if (built == null) return;
        Hyperlink update = built.update();
        ReleaseInfo latest = updates.latest();
        boolean show = latest != null && updates.state() == UpdateCoordinator.State.AVAILABLE;
        update.setText(latest == null ? "" : "Mirada " + latest.version() + " is available");
        update.setVisible(show);
        update.setManaged(show);
    }

    /** Shows or hides the bar, taking its space with it. */
    void setShowing(boolean showing) {
        bar.setVisible(showing);
        bar.setManaged(showing);
    }

    boolean isShowing() {
        return bar.isVisible();
    }

    Region node() {
        return bar;
    }

    /** The echo area; only after {@link #finish}. */
    Label echo() {
        return Objects.requireNonNull(parts, "not finished").echo();
    }

    Label unread() {
        return Objects.requireNonNull(parts, "not finished").unread();
    }
}
