package com.mirada.ui;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.stage.Stage;

import com.mirada.os.Os;
import com.mirada.settings.Settings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static com.mirada.ui.FxTestSupport.call;
import static com.mirada.ui.FxTestSupport.fx;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OS-03, OS-01 (Pauta 6.1.0): the window runs as each OS in turn, through {@link Os#override}, and has
 * the same commands, the same menu rows and the same Settings pages and rows on Windows, macOS and
 * Linux. Where macOS's own way wins (the system menu bar draws the rows) the rows are the same
 * commands all the same.
 */
@Tag("fx")
class SameOnEveryOsFxTest {

    @TempDir
    Path dir;

    @BeforeAll
    static void boot() throws Exception {
        FxTestSupport.start();
    }

    @AfterEach
    void reset() throws Exception {
        Os.override(null);
        // The keys in force are global: back to this OS's for the tests that follow.
        fx(() -> {
            Keybindings.use(Settings.DEFAULT_KEYMAP, Map.of(), Settings.defaultPaletteKeys(Os.isMac()));
        });
    }

    @Test
    void theCommandsTheMenusAndSettingsAreTheSameOnEveryOs() throws Exception {
        Map<Os.Kind, List<String>> commands = new LinkedHashMap<>();
        Map<Os.Kind, List<String>> menus = new LinkedHashMap<>();
        Map<Os.Kind, List<String>> settingsRows = new LinkedHashMap<>();
        for (Os.Kind kind : Os.Kind.values()) {
            Os.override(kind);
            Stage stage = call(Stage::new);
            Settings settings = new Settings(dir.resolve(kind.name()).resolve("settings.properties"));
            ViewerWindow window = call(() -> {
                ViewerWindow made = new ViewerWindow(stage, settings, link -> {});
                made.show();
                return made;
            });
            try {
                // The menu bar is built after the first paint, or half a second after start without one.
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                while (call(() -> FxTestSupport.field(window, "afterFirstPaint") != null)) {
                    if (System.nanoTime() > deadline) throw new AssertionError("the menu bar was never built");
                    Thread.sleep(50);
                }
                commands.put(
                        kind,
                        Arrays.stream(Command.values())
                                .map(Command::id)
                                .sorted()
                                .toList());
                menus.put(kind, call(() -> rows(menuBar(window, kind))));
                settingsRows.put(kind, call(() -> {
                    SettingsWindow shown = new SettingsWindow(settings, ViewerWindow.stylesheet(), c -> {});
                    List<String> titles = new ArrayList<>();
                    javafx.scene.control.ScrollPane scroll = FxTestSupport.field(shown, "scroll");
                    labels(scroll.getContent(), titles);
                    shown.stage().close();
                    return titles;
                }));
            } finally {
                fx(() -> {
                    if (stage.isShowing()) stage.close();
                });
            }
        }
        assertTrue(commands.get(Os.Kind.LINUX).contains(Command.SETTINGS.id()));
        assertTrue(
                menus.get(Os.Kind.LINUX).size() > 20, menus.get(Os.Kind.LINUX).toString());
        assertTrue(
                settingsRows.get(Os.Kind.LINUX).size() > 10,
                settingsRows.get(Os.Kind.LINUX).toString());
        for (Os.Kind kind : List.of(Os.Kind.WINDOWS, Os.Kind.MAC)) {
            assertEquals(commands.get(Os.Kind.LINUX), commands.get(kind), "commands on " + kind);
            assertEquals(menus.get(Os.Kind.LINUX), menus.get(kind), "menu rows on " + kind);
            assertEquals(settingsRows.get(Os.Kind.LINUX), settingsRows.get(kind), "Settings on " + kind);
        }
    }

    /** Every page's title and every row's name under {@code node}, in order. */
    private static void labels(Node node, List<String> out) {
        if (node instanceof Label label
                && (label.getStyleClass().contains("settings-page-title")
                        || label.getStyleClass().contains("info-value"))) out.add(label.getText());
        if (node instanceof javafx.scene.Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) labels(child, out);
        }
    }

    /** The menu bar: in the window's title bar, or as macOS the system's. */
    private static MenuBar menuBar(ViewerWindow window, Os.Kind kind) {
        if (kind == Os.Kind.MAC) return FxTestSupport.field(window, "macMenus");
        TitleBar titleBar = FxTestSupport.field(window, "titleBar");
        MenuBar bar = titleBar.menuBar();
        if (bar == null) throw new AssertionError("no in-window menu bar as " + kind);
        return bar;
    }

    /** Each menu's heading key and its rows' command ids, separators as "-". */
    private static List<String> rows(MenuBar bar) {
        List<String> rows = new ArrayList<>();
        for (Menu menu : bar.getMenus()) {
            rows.add("[" + menu.getText() + "]");
            for (MenuItem item : menu.getItems()) {
                rows.add(item instanceof SeparatorMenuItem ? "-" : String.valueOf(((Command) item.getUserData()).id()));
            }
        }
        return rows;
    }
}
