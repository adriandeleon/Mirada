package com.mirada.ui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.DialogPane;
import javafx.scene.control.MenuItem;
import javafx.stage.Window;

import com.mirada.settings.Settings;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.api.FxToolkit;

import static com.mirada.ui.FxTestSupport.call;
import static com.mirada.ui.FxTestSupport.fx;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every registered command, run in a real headless window in each state a user meets it in: an
 * empty window, an image on screen, one turned and zoomed with the panels open, the editor open,
 * full screen (QUAL-11). Each either runs or says why it cannot, the context menu greys it as the
 * palette would, and nothing is written outside the test's folders (QUAL-08): Mirada's own settings
 * folder and the desktop's trash are as they were.
 *
 * <p>Not run here: Show in Folder (it asks the desktop's file manager) and Move to Trash, which
 * {@code TrashUndoTest} runs against a trash of its own. Dialogs are cancelled and file dialogs
 * chosen nothing, as a user who changed their mind.
 */
@Tag("fx")
class CommandSweepTest {

    private static final Set<Command> NOT_HERE = Set.of(Command.SHOW_IN_FOLDER, Command.MOVE_TO_TRASH);

    @TempDir
    Path dir;

    private final AtomicBoolean answering = new AtomicBoolean(true);

    @AfterEach
    void cleanUp() throws Exception {
        answering.set(false);
        FileDialogs.answeredBy(null);
        FxToolkit.cleanupStages();
        fx(() -> {
            Command.setVertical(false);
            PaletteChords.use(Settings.defaultPaletteKeys(Command.MAC));
        });
    }

    /** What is in Mirada's real settings folder and the desktop's trash, by name and time. */
    private static Map<String, Long> outside() throws IOException {
        Map<String, Long> found = new TreeMap<>();
        Path home = Path.of(System.getProperty("user.home"));
        List<Path> watched = new ArrayList<>(List.of(
                home.resolve(".mirada"),
                home.resolve(".mirada-dev"),
                home.resolve(".local/share/Trash/files"),
                home.resolve(".local/share/Trash/info")));
        Path config = Settings.defaultFile(null).getParent();
        if (config != null) watched.add(config);
        for (Path folder : watched) {
            if (!Files.isDirectory(folder)) continue;
            try (Stream<Path> files = Files.list(folder)) {
                for (Path file : files.toList())
                    found.put(file.toString(), Files.getLastModifiedTime(file).toMillis());
            }
        }
        return found;
    }

    /** Cancels every dialog that shows, as long as the test runs. */
    private void cancelDialogs() {
        Thread canceller = new Thread(
                () -> {
                    while (answering.get()) {
                        try {
                            fx(() -> {
                                for (Window window : List.copyOf(Window.getWindows())) {
                                    if (window.isShowing()
                                            && window.getScene() != null
                                            && window.getScene().getRoot() instanceof DialogPane pane) {
                                        cancel(pane);
                                    }
                                }
                            });
                            Thread.sleep(15);
                        } catch (Exception e) {
                            return;
                        }
                    }
                },
                "dialog-canceller");
        canceller.setDaemon(true);
        canceller.start();
    }

    private static void cancel(DialogPane pane) {
        for (ButtonType type : pane.getButtonTypes()) {
            if (type.getButtonData().isCancelButton()) {
                ((javafx.scene.control.Button) java.util.Objects.requireNonNull(pane.lookupButton(type))).fire();
                return;
            }
        }
    }

    private static @Nullable String unavailable(WindowFixture window, Command command) throws Exception {
        return call(() ->
                (String) FxTestSupport.invoke(window.window, "unavailable", new Class<?>[] {Command.class}, command));
    }

    /** Back to the state the sweep is in: the palette, the log, the editor and full screen as they were. */
    private static void settle(WindowFixture window, boolean editing, boolean fullScreen) throws Exception {
        fx(() -> {
            CommandPalette palette = window.field("palette");
            palette.hide();
            MessageLogView log = window.field("messageLog");
            if (log != null) log.hide();
            Editor editor = window.field("editor");
            if (!editing && editor != null && editor.isShowing()) editor.close();
            if (FxTestSupport.<Boolean>field(window.window, "slideshowRunning")) {
                FxTestSupport.invoke(window.window, "stopSlideshow", new Class<?>[0]);
            }
            if (window.stage.isFullScreen() != fullScreen) window.stage.setFullScreen(fullScreen);
            if (!window.stage.isShowing()) window.stage.show();
        });
    }

    private enum State {
        EMPTY,
        IMAGE,
        TURNED_AND_ZOOMED,
        EDITING,
        FULL_SCREEN
    }

    @Test
    void everyCommandRunsOrSaysWhyNotInEveryStateAndNothingIsWrittenOutside() throws Exception {
        Map<String, Long> before = outside();
        cancelDialogs();
        FileDialogs.answeredBy((title, folder, name) -> null);
        Path photos = WindowFixture.threeImages(dir.resolve("photos"));
        Map<State, List<Command>> ran = new TreeMap<>();
        for (State state : State.values()) {
            WindowFixture window = WindowFixture.open(
                    dir.resolve("config-" + state), state == State.EMPTY ? null : photos.resolve("b.png"));
            if (state != State.EMPTY) window.waitForImage("b.png");
            window.waitForFirstPaint();
            boolean editing = state == State.EDITING;
            boolean fullScreen = state == State.FULL_SCREEN;
            switch (state) {
                case TURNED_AND_ZOOMED -> {
                    window.run(Command.ROTATE_CLOCKWISE);
                    window.run(Command.ZOOM_IN);
                    window.run(Command.INFO);
                    window.run(Command.THUMBNAILS);
                    window.run(Command.BOOKMARK);
                }
                case EDITING -> window.run(Command.EDIT);
                case FULL_SCREEN -> window.run(Command.FULL_SCREEN);
                default -> {}
            }
            List<Command> done = new ArrayList<>();
            for (Command command : Command.values()) {
                if (NOT_HERE.contains(command) || command == Command.CLOSE || command == Command.ESCAPE) continue;
                String why = unavailable(window, command);
                if (why != null && !editing) {
                    assertTrue(why.endsWith("."), command + ": a reason is a sentence: " + why);
                    continue;
                }
                window.run(command);
                done.add(command);
                settle(window, editing, fullScreen);
            }
            // The context menu greys exactly what cannot run.
            ContextMenu menu =
                    call(() -> (ContextMenu) FxTestSupport.invoke(window.window, "contextMenu", new Class<?>[0]));
            for (MenuItem item : menu.getItems()) {
                if (item.getText() == null || item instanceof javafx.scene.control.Menu) continue;
                Command command = Command.inPalette().stream()
                        .filter(c -> item.getText().equals(c.label())
                                || item.getText().equals("Leave Full Screen"))
                        .findFirst()
                        .orElse(null);
                if (command != null && command != Command.FULL_SCREEN) {
                    assertEquals(unavailable(window, command) != null, item.isDisable(), state + " " + command);
                }
            }
            ran.put(state, done);
            // Escape and Close last: they may close the window.
            window.run(Command.ESCAPE);
            window.run(Command.CLOSE);
            window.close();
        }
        assertTrue(ran.get(State.IMAGE).size() > 40, "most commands run on an image: " + ran.get(State.IMAGE));
        assertTrue(ran.get(State.EMPTY).size() < ran.get(State.IMAGE).size(), "an empty window greys some");
        answering.set(false);
        assertEquals(before, outside(), "the sweep wrote outside its folders");
    }
}
