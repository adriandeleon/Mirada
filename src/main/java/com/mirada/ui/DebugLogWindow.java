package com.mirada.ui;

import java.nio.file.Path;
import java.util.function.Consumer;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import javafx.stage.Window;

import com.mirada.AppInfo;
import com.mirada.diagnostics.DebugLog;

/**
 * The Debug Log in a window of its own, beside the viewer: modeless, with Refresh, Copy, Clear,
 * Export and Report a Problem (DIAG-03, Pauta 4.0.1). The only way to read Mirada's log in a packaged
 * build, which has no console. Every button runs a {@link Command}, so the palette reaches the same
 * work. Built the first time it is asked for.
 */
final class DebugLogWindow {

    private static final double WIDTH = 820;

    private final Stage stage = new Stage();
    private final TextArea text = new TextArea();
    private final Label where = new Label();

    DebugLogWindow(Consumer<Command> run, String stylesheet) {
        stage.setTitle("Debug Log — " + AppInfo.NAME);
        WindowIcon.applyTo(stage);
        text.setEditable(false);
        // A long path or a stack frame wraps, rather than scroll the start of every line out of sight.
        text.setWrapText(true);
        text.getStyleClass().add("debug-log-text");

        Button refresh = new Button("Refresh");
        refresh.setOnAction(e -> refresh());
        Button copy = button("Copy", Command.COPY_DEBUG_LOG, run);
        Button clear = button("Clear", Command.CLEAR_DEBUG_LOG, run);
        Button export = button("Export…", Command.EXPORT_DEBUG_LOG, run);
        Button report = button("Report a Problem…", Command.REPORT_PROBLEM, run);
        where.getStyleClass().add("debug-log-file");
        where.setMinWidth(0);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox buttons = new HBox(6, refresh, copy, clear, export, report, spacer, where);
        buttons.setAlignment(Pos.CENTER_LEFT);
        buttons.setPadding(new Insets(8));

        BorderPane root = new BorderPane(text);
        root.setBottom(buttons);
        root.getStyleClass().add("debug-log");
        Scene scene = new Scene(root, WIDTH, 480);
        scene.getStylesheets().add(stylesheet);
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) stage.hide();
            else if (e.getCode() == KeyCode.F5) refresh();
        });
        stage.setScene(scene);
    }

    private static Button button(String label, Command command, Consumer<Command> run) {
        Button button = new Button(label);
        button.setOnAction(e -> run.accept(command));
        return button;
    }

    /** Shows the window beside {@code from}, or brings it forward; either way with what is logged now. */
    void show(Window from) {
        refresh();
        if (stage.isShowing()) {
            stage.toFront();
            return;
        }
        stage.setX(from.getX() + Math.max(0, (from.getWidth() - WIDTH) / 2));
        stage.setY(from.getY() + 60);
        stage.show();
    }

    /** What is logged now, scrolled to its end. */
    void refresh() {
        text.setText(DebugLog.text());
        text.positionCaret(text.getLength());
        Path file = DebugLog.file();
        where.setText(file == null ? "Not written to a file" : "Also in " + AppInfo.tildeHome(file.toString()));
    }

    boolean isShowing() {
        return stage.isShowing();
    }

    Stage stage() {
        return stage;
    }
}
