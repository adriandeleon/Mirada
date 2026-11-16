package com.mirada.ui;

import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

import com.mirada.AppInfo;
import com.mirada.diagnostics.DebugLog;

import static com.mirada.i18n.Messages.tr;

/**
 * The Debug Log in a window of its own, beside the viewer: modeless, with Refresh, Copy, Clear,
 * Export and Report a Problem (DIAG-03). The only way to read Mirada's log in a packaged build,
 * which has no console. Every button runs a {@link Command}, so the palette reaches the same work.
 * A find bar over the log ({@link FindBar}, FIND-01) opens on {@code log.find}, whose keys
 * ({@link Command.Context#DEBUG_LOG}) work anywhere in the window, the bar's field included.
 * Built the first time it is asked for.
 */
final class DebugLogWindow {

    private static final double WIDTH = 820;

    private final Stage stage = new Stage();
    private final TextArea text = new TextArea();
    private final Label where = new Label();
    private final FindBar findBar = new FindBar(text);

    DebugLogWindow(Consumer<Command> run, String stylesheet) {
        stage.setTitle(tr("debugLog.title"));
        WindowIcon.applyTo(stage);
        text.setEditable(false);
        // A long path or a stack frame wraps, rather than scroll the start of every line out of sight.
        text.setWrapText(true);
        text.getStyleClass().add("debug-log-text");

        Button refresh = new Button(tr("debugLog.refresh"));
        refresh.setOnAction(e -> refresh());
        Button copy = button(tr("debugLog.copy"), Command.COPY_DEBUG_LOG, run);
        Button clear = button(tr("debugLog.clear"), Command.CLEAR_DEBUG_LOG, run);
        Button export = button(tr("debugLog.export"), Command.EXPORT_DEBUG_LOG, run);
        Button report = button(tr("debugLog.reportProblem"), Command.REPORT_PROBLEM, run);
        where.getStyleClass().add("debug-log-file");
        where.setMinWidth(0);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox buttons = UiZoom.hbox(6, refresh, copy, clear, export, report, spacer, where);
        buttons.setAlignment(Pos.CENTER_LEFT);
        UiZoom.pad(buttons, 8);

        UiZoom.pad(findBar, 6, 8, 0, 8);
        BorderPane root = new BorderPane(text);
        root.setBottom(new VBox(findBar, buttons));
        root.getStyleClass().add("debug-log");
        Scene scene = new Scene(root, UiZoom.px(WIDTH), UiZoom.px(480));
        scene.getStylesheets().add(stylesheet);
        // The window's own commands, from the keymap (FIND-04): before the field or the log take the key.
        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            Command command = Keybindings.current().commandFor(Command.Context.DEBUG_LOG, e);
            if (command != null) {
                run.accept(command);
                e.consume();
            }
        });
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

    /**
     * The log's right-click menu (MENU-05): its commands, Copy, Export, Clear and Report a Problem,
     * made as it opens so each row is greyed or not as it is then. A read-only console's menu.
     */
    void setMenu(Supplier<ContextMenu> menu) {
        text.setOnContextMenuRequested(e -> {
            ContextMenu made = menu.get();
            made.show(text, e.getScreenX(), e.getScreenY());
            e.consume();
        });
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
        where.setText(
                file == null ? tr("debugLog.notWritten") : tr("debugLog.alsoIn", AppInfo.tildeHome(file.toString())));
    }

    boolean isShowing() {
        return stage.isShowing();
    }

    /** Opens the find bar over the log, or finds the next match when it is open ({@code log.find}). */
    void find() {
        findBar.openOrNext();
    }

    FindBar findBar() {
        return findBar;
    }

    TextArea text() {
        return text;
    }

    Stage stage() {
        return stage;
    }
}
