package com.mirada.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;

import com.mirada.os.Os;
import com.mirada.settings.Settings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testfx.api.FxToolkit;

import static com.mirada.ui.FxTestSupport.call;
import static com.mirada.ui.FxTestSupport.fx;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Debug Log's find bar (FIND-01, 02, 04, 05, 06, 07, 09, 10, 16). */
@Tag("fx")
class FindBarTest {

    private static final String LOG = "alpha one\nbeta\nAlpha two\nbeta again\n";

    @BeforeAll
    static void toolkit() throws Exception {
        FxTestSupport.start();
    }

    @AfterEach
    void close() throws Exception {
        FxToolkit.cleanupStages();
        fx(() -> Keybindings.use(Settings.DEFAULT_KEYMAP, Map.of(), Settings.defaultPaletteKeys(Os.isMac())));
    }

    /**
     * The Debug Log, shown, with {@link #LOG} as its text; its commands land in {@code ran}, and its
     * own ({@code log.*}) run there as the viewer runs them.
     */
    private static DebugLogWindow log(List<Command> ran) throws Exception {
        Stage[] owners = new Stage[1];
        DebugLogWindow[] made = new DebugLogWindow[1];
        DebugLogWindow window = call(() -> new DebugLogWindow(
                command -> {
                    ran.add(command);
                    made[0].run(command, owners[0]);
                },
                ViewerWindow.stylesheet()));
        made[0] = window;
        fx(() -> {
            Stage owner = new Stage();
            owners[0] = owner;
            owner.setScene(new Scene(new javafx.scene.layout.StackPane(), 200, 200));
            owner.show();
            window.show(owner);
            window.text().setText(LOG);
            window.text().positionCaret(0);
        });
        return window;
    }

    private static void press(Node target, KeyCode code, boolean shift) throws Exception {
        fx(() -> Event.fireEvent(target, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, shift, false, false, false)));
    }

    private static void settle(FindBar bar) throws Exception {
        fx(() -> FxTestSupport.<Debounce>field(bar, "searching").flush());
        FxTestSupport.waitUntil("the search", () -> {
            try {
                return !call(bar::searching);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        FxTestSupport.drain();
    }

    @Test
    void theBarIsInTheWindowSearchesAsYouTypeStepsAndCloses() throws Exception {
        List<Command> ran = new ArrayList<>();
        DebugLogWindow window = log(ran);
        FindBar bar = window.findBar();
        TextArea text = window.text();
        fx(window::find);
        assertTrue(call(bar::isOpen));
        // FIND-01: a node in the Debug Log's own scene, not a popup or a stage of its own.
        assertSame(call(() -> window.stage().getScene()), call(bar::getScene));
        FxTestSupport.waitUntil("the field's focus", () -> bar.field().isFocused());

        // FIND-02, FIND-05: typing searches, counts and selects the first match from the caret.
        fx(() -> bar.field().setText("alpha"));
        settle(bar);
        assertEquals("1 of 2", call(bar::countText));
        assertEquals("alpha", call(text::getSelectedText));

        // FIND-06: Enter forward, Shift+Enter back, wrapping at the ends.
        press(bar.field(), KeyCode.ENTER, false);
        assertEquals("2 of 2", call(bar::countText));
        assertEquals("Alpha", call(text::getSelectedText));
        press(bar.field(), KeyCode.ENTER, false);
        assertEquals("1 of 2", call(bar::countText), "past the last: the first");
        press(bar.field(), KeyCode.ENTER, true);
        assertEquals("2 of 2", call(bar::countText), "before the first: the last");

        // FIND-04: the command, run while the bar is open, finds the next match.
        fx(window::find);
        assertEquals("1 of 2", call(bar::countText));

        // Match case narrows it; nothing found says so in the count, and nothing else opens (FIND-10).
        fx(() -> bar.matchCase().setSelected(true));
        settle(bar);
        assertEquals("1 of 1", call(bar::countText));
        fx(() -> bar.field().setText("zzz-not-there"));
        settle(bar);
        assertEquals("No matches", call(bar::countText));

        // FIND-07: Escape closes it, drops what is pending, clears the match and gives the log the keyboard.
        fx(() -> bar.field().setText("beta"));
        press(bar.field(), KeyCode.ESCAPE, false);
        assertFalse(call(bar::isOpen));
        assertFalse(call(bar::searching), "nothing runs after the bar closed");
        assertEquals(0, call(() -> text.getSelection().getLength()));
        FxTestSupport.waitUntil("the log's focus", text::isFocused);
        assertTrue(call(() -> window.stage().isShowing()), "the Escape that closes the bar keeps the window");

        // The Close button does the same; it and the steps are out of the Tab order, Match case in it (FIND-16).
        fx(window::find);
        List<Button> buttons = call(() -> bar.getChildren().stream()
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .toList());
        assertEquals(3, buttons.size());
        for (Button button : buttons) assertFalse(button.isFocusTraversable(), button.getText());
        assertTrue(call(() -> bar.matchCase().isFocusTraversable()));
        fx(() -> buttons.getLast().fire());
        assertFalse(call(bar::isOpen));
    }

    /** PERF-23: typing waits for a pause before the log is searched off the FX thread. */
    @Test
    void typingWaitsForAPauseBeforeTheSearch() throws Exception {
        DebugLogWindow window = log(new ArrayList<>());
        FindBar bar = window.findBar();
        fx(window::find);
        fx(() -> bar.field().setText("a"));
        fx(() -> bar.field().setText("al"));
        LatestOnly searches = call(() -> FxTestSupport.<LatestOnly>field(bar, "searches"));
        assertTrue(call(() -> FxTestSupport.<Debounce>field(bar, "searching").pending()), "waiting for the pause");
        assertFalse(call(searches::pending), "no search starts on the first key");
        assertEquals("", call(bar::countText));
        FxTestSupport.waitUntil("the pause", () -> {
            try {
                return !call(bar::searching);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        FxTestSupport.drain();
        assertEquals("1 of 2", call(bar::countText), "searched once typing paused");
    }

    /** KBD-01: Previous, Shift+Enter, Match case and Refresh run commands, which the keymap reaches too. */
    @Test
    void theBarsButtonsAndKeysRunCommands() throws Exception {
        List<Command> ran = new ArrayList<>();
        DebugLogWindow window = log(ran);
        FindBar bar = window.findBar();
        fx(window::find);
        fx(() -> bar.field().setText("alpha"));
        settle(bar);
        assertEquals("1 of 2", call(bar::countText));
        Button previous = call(() -> (Button) bar.getChildren().stream()
                .filter(n -> n instanceof Button b && b.getText().equals(com.mirada.i18n.Messages.tr("find.previous")))
                .findFirst()
                .orElseThrow());
        fx(previous::fire);
        assertEquals(List.of(Command.DEBUG_LOG_FIND_PREVIOUS), ran);
        assertEquals("2 of 2", call(bar::countText), "the match before, wrapping");
        press(bar.field(), KeyCode.ENTER, true);
        assertEquals(List.of(Command.DEBUG_LOG_FIND_PREVIOUS, Command.DEBUG_LOG_FIND_PREVIOUS), ran);
        assertEquals("1 of 2", call(bar::countText));
        ran.clear();
        // Match case from its command: the box follows, and the search narrows.
        fx(() -> window.run(Command.DEBUG_LOG_MATCH_CASE, window.stage()));
        assertTrue(call(() -> bar.matchCase().isSelected()));
        settle(bar);
        assertEquals("1 of 1", call(bar::countText));
        // The keymap's chords reach all three in the window (Context.DEBUG_LOG).
        for (Command command :
                List.of(Command.DEBUG_LOG_FIND_PREVIOUS, Command.DEBUG_LOG_MATCH_CASE, Command.DEBUG_LOG_REFRESH)) {
            assertEquals(Command.Context.DEBUG_LOG, command.context());
            assertTrue(
                    Keybindings.current().chord(command) != javafx.scene.input.KeyCombination.NO_MATCH, command.id());
        }
        Button refresh = call(() -> (Button) window.stage().getScene().getRoot().lookupAll(".button").stream()
                .filter(n ->
                        n instanceof Button b && b.getText().equals(com.mirada.i18n.Messages.tr("debugLog.refresh")))
                .findFirst()
                .orElseThrow());
        fx(refresh::fire);
        assertEquals(List.of(Command.DEBUG_LOG_REFRESH), ran);
    }

    /** FIND-04: the window's own keys, from the keymap, run its find command, the field's focus or not. */
    @Test
    void theKeymapsFindChordRunsTheFindCommand() throws Exception {
        List<Command> ran = new ArrayList<>();
        DebugLogWindow window = log(ran);
        boolean mac = Os.isMac();
        fx(() -> Event.fireEvent(
                window.text(), new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.F, false, !mac, false, mac)));
        assertEquals(List.of(Command.DEBUG_LOG_FIND), ran);
        fx(() -> Keybindings.use("emacs", Map.of(), Settings.defaultPaletteKeys(mac)));
        fx(window::find);
        fx(() -> Event.fireEvent(
                window.findBar().field(),
                new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.S, false, true, false, false)));
        assertEquals(List.of(Command.DEBUG_LOG_FIND, Command.DEBUG_LOG_FIND), ran, "C-s in Emacs, from the field");
    }

    /** FIND-09: the field edits by Emacs's chords under the Emacs keymap, and by the platform's otherwise. */
    @Test
    void theFieldFollowsTheKeymapsEditingChords() throws Exception {
        DebugLogWindow window = log(new ArrayList<>());
        fx(window::find);
        var field = window.findBar().field();
        fx(() -> {
            field.setText("alpha");
            field.positionCaret(5);
        });
        KeyEvent controlA = new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.A, false, true, false, false);
        fx(() -> Keybindings.use("emacs", Map.of(), Settings.defaultPaletteKeys(Os.isMac())));
        fx(() -> Event.fireEvent(field, controlA.copyFor(field, field)));
        assertEquals(0, call(field::getCaretPosition), "C-a: the line's start, in Emacs");
        fx(() -> Keybindings.use("cua", Map.of(), Settings.defaultPaletteKeys(Os.isMac())));
        fx(() -> field.positionCaret(5));
        fx(() -> Event.fireEvent(field, controlA.copyFor(field, field)));
        if (!Os.isMac()) assertEquals("alpha", call(field::getSelectedText), "Ctrl+A: select all, in CUA");
    }

    @Test
    void aStepWrapsAroundTheEnds() {
        List<int[]> matches = List.of(new int[] {2, 4}, new int[] {10, 12}, new int[] {20, 22});
        assertEquals(1, FindBar.stepFrom(matches, 2, true));
        assertEquals(0, FindBar.stepFrom(matches, 20, true));
        assertEquals(0, FindBar.stepFrom(matches, 10, false));
        assertEquals(2, FindBar.stepFrom(matches, 2, false));
    }
}
