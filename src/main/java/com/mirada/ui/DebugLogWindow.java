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
import org.jspecify.annotations.Nullable;

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
    private final FindBar findBar;
    /** The log's right-click menu, made as it opens; null until the window is handed one. */
    private @Nullable Supplier<ContextMenu> menu;

    DebugLogWindow(Consumer<Command> run, String stylesheet) {
        findBar = new FindBar(text, run);
        stage.setTitle(tr("debugLog.title"));
        WindowIcon.applyTo(stage);
        text.setEditable(false);
        // A long path or a stack frame wraps, rather than scroll the start of every line out of sight.
        text.setWrapText(true);
        text.getStyleClass().add("debug-log-text");
        text.setAccessibleText(Icons.title(Command.DEBUG_LOG));

        Button refresh = button(tr("debugLog.refresh"), Command.DEBUG_LOG_REFRESH, run);
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
        // The log's menu closes on a press anywhere in the window, the log included (MENU-07), and the
        // keyboard's right-click opens it (MENU-09), never the platform's request as well.
        Popups.closeOnPressOutside(stage, scene);
        Popups.menusByKey(scene);
        // The window's own commands, from the keymap (FIND-04): before the field or the log take the key.
        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (Popups.isMenuKey(e) && !findBar.field().isFocused()) {
                showMenuFromKeyboard();
                e.consume();
                return;
            }
            Command command = Keybindings.current().commandFor(Command.Context.DEBUG_LOG, e);
            if (command != null) {
                run.accept(command);
                e.consume();
            }
        });
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) stage.hide();
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
        this.menu = menu;
        // One menu at a time (MENU-06), closed by the window's press filter (MENU-07).
        text.setOnContextMenuRequested(e -> {
            showMenu(e.getScreenX(), e.getScreenY());
            e.consume();
        });
    }

    /** The log's menu at a point on the screen, after any other menu over the window has closed. */
    private void showMenu(double screenX, double screenY) {
        Supplier<ContextMenu> making = menu;
        if (making == null) return;
        ContextMenu made = making.get();
        Popups.closedByWindow(made);
        Popups.show(made, text, screenX, screenY);
    }

    /**
     * The log's menu from the Menu key or Shift+F10 (MENU-09): the log has no rows, so it opens over
     * the view, a little in from its top left corner.
     */
    void showMenuFromKeyboard() {
        javafx.geometry.Bounds view = text.localToScreen(text.getBoundsInLocal());
        if (view == null) return;
        javafx.geometry.Point2D at = Popups.under(new javafx.geometry.BoundingBox(
                view.getMinX(), view.getMinY(), view.getWidth(), Math.min(view.getHeight(), 24)));
        showMenu(at.getX(), at.getY());
    }

    /**
     * Runs one of the window's own commands, the {@code log.*} ones; whether {@code command} was
     * one. The window shows itself first when it is closed.
     */
    boolean run(Command command, Window from) {
        switch (command) {
            case DEBUG_LOG_FIND, DEBUG_LOG_FIND_PREVIOUS, DEBUG_LOG_MATCH_CASE, DEBUG_LOG_REFRESH -> {}
            default -> {
                return false;
            }
        }
        if (!isShowing()) show(from);
        switch (command) {
            case DEBUG_LOG_FIND -> findBar.openOrNext();
            case DEBUG_LOG_FIND_PREVIOUS -> findBar.openOrPrevious();
            case DEBUG_LOG_MATCH_CASE -> findBar.toggleMatchCase();
            default -> refresh();
        }
        return true;
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
