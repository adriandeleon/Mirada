package com.mirada.ui;

import java.util.ArrayList;
import java.util.List;

import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testfx.api.FxToolkit;

import static com.mirada.ui.FxTestSupport.call;
import static com.mirada.ui.FxTestSupport.fx;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The palette's keyboard, as Termina's and Editora's: the arrows and the Emacs chords move over the
 * rows (stepping over greyed ones), Return runs, Escape and C-g cancel; in its query field the
 * Emacs editing chords move the caret, kill and yank.
 */
@Tag("fx")
class PaletteKeyboardTest {

    @BeforeAll
    static void toolkit() throws Exception {
        FxTestSupport.start();
    }

    @AfterEach
    void close() throws Exception {
        FxToolkit.cleanupStages();
    }

    /** A key press: {@code control}, {@code alt} and {@code shift} as named, on {@code target}. */
    private static void press(Node target, KeyCode code, boolean control, boolean alt, boolean shift) throws Exception {
        fx(() -> Event.fireEvent(target, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, shift, control, alt, false)));
    }

    private static void press(Node target, KeyCode code) throws Exception {
        press(target, code, false, false, false);
    }

    private static void type(Node target, String character, boolean control) throws Exception {
        fx(() -> Event.fireEvent(
                target,
                new KeyEvent(KeyEvent.KEY_TYPED, character, "", KeyCode.UNDEFINED, false, control, false, false)));
    }

    // ---- The list ----

    @Test
    void theRowsAreWalkedByArrowsPagesAndEmacsChordsSteppingOverGreyedOnes() throws Exception {
        StackPane host = new StackPane();
        fx(() -> {
            Stage stage = new Stage();
            stage.setScene(new Scene(host, 700, 600));
            stage.show();
        });
        List<Command> ran = new ArrayList<>();
        CommandPalette palette = call(() -> new CommandPalette(host, ran::add));
        List<Command> commands = Command.inPalette();
        fx(() -> palette.show(commands, command -> command == commands.get(1) ? "Not now." : null));
        assertTrue(call(palette::isShowing));
        TextField input = FxTestSupport.field(palette, "input");
        ListView<?> list = FxTestSupport.field(palette, "list");
        assertEquals(0, call(() -> list.getSelectionModel().getSelectedIndex()));

        press(input, KeyCode.DOWN);
        assertEquals(2, call(() -> list.getSelectionModel().getSelectedIndex()), "the greyed row is stepped over");
        press(input, KeyCode.UP);
        assertEquals(0, call(() -> list.getSelectionModel().getSelectedIndex()));
        press(input, KeyCode.UP);
        assertEquals(commands.size() - 1, call(() -> list.getSelectionModel().getSelectedIndex()), "up wraps");
        press(input, KeyCode.N, true, false, false);
        assertEquals(0, call(() -> list.getSelectionModel().getSelectedIndex()), "C-n is down, and wraps");
        press(input, KeyCode.P, true, false, false);
        assertEquals(commands.size() - 1, call(() -> list.getSelectionModel().getSelectedIndex()), "C-p is up");
        press(input, KeyCode.PAGE_UP);
        int paged = call(() -> list.getSelectionModel().getSelectedIndex());
        assertTrue(paged < commands.size() - 1);
        press(input, KeyCode.PAGE_DOWN);
        press(input, KeyCode.V, false, true, false); // M-v: a page up
        press(input, KeyCode.V, true, false, false); // C-v: a page down
        press(input, KeyCode.COMMA, false, true, true); // M-<: the first
        assertEquals(0, call(() -> list.getSelectionModel().getSelectedIndex()));
        press(input, KeyCode.PERIOD, false, true, true); // M->: the last
        assertEquals(commands.size() - 1, call(() -> list.getSelectionModel().getSelectedIndex()));
        fx(() -> list.getSelectionModel().select(1));
        press(input, KeyCode.ENTER);
        assertEquals(List.of(), ran, "a greyed row does not run");
        fx(() -> list.getSelectionModel().select(0));
        press(input, KeyCode.ENTER);
        assertEquals(List.of(commands.getFirst()), ran);
        assertFalse(call(palette::isShowing));

        fx(() -> palette.show(commands, command -> null));
        press(input, KeyCode.G, true, false, false);
        assertFalse(call(palette::isShowing), "C-g cancels");
        fx(() -> palette.show(commands, command -> null));
        Node backdrop = FxTestSupport.field(palette, "backdrop");
        fx(() -> backdrop.fireEvent(new MouseEvent(
                MouseEvent.MOUSE_PRESSED,
                1,
                1,
                1,
                1,
                MouseButton.PRIMARY,
                1,
                false,
                false,
                false,
                false,
                true,
                false,
                false,
                false,
                false,
                true,
                null)));
        assertFalse(call(palette::isShowing), "a press outside the card closes it");

        // Typed, the rows narrow to the matches, and a query that matches nothing says so.
        fx(() -> palette.show(commands, command -> null));
        fx(() -> input.setText("zzzzqqq"));
        assertEquals(0, call(() -> list.getItems().size()));
        press(input, KeyCode.ENTER);
        assertEquals(1, ran.size(), "nothing to run");
        press(input, KeyCode.ESCAPE);
        assertFalse(call(palette::isShowing));
    }

    // ---- The query field ----

    @Test
    void theEmacsChordsEditTheQuery() throws Exception {
        TextField field = new TextField();
        fx(() -> {
            Stage stage = new Stage();
            stage.setScene(new Scene(new StackPane(field), 300, 100));
            stage.show();
            TextInputKeys.install(field, false);
            field.requestFocus();
        });
        // Gaining the focus selects the whole field: let that happen before the caret is placed.
        FxTestSupport.waitUntil("the field's focus", field::isFocused);
        FxTestSupport.drain();
        fx(() -> {
            field.setText("rotate left now");
            field.positionCaret(7);
        });
        press(field, KeyCode.A, true, false, false);
        assertEquals(0, call(field::getCaretPosition), "C-a: the start");
        press(field, KeyCode.E, true, false, false);
        assertEquals(15, call(field::getCaretPosition), "C-e: the end");
        press(field, KeyCode.B, true, false, false);
        assertEquals(14, call(field::getCaretPosition), "C-b: back one");
        press(field, KeyCode.F, true, false, false);
        assertEquals(15, call(field::getCaretPosition), "C-f: forward one");
        press(field, KeyCode.B, false, true, false);
        assertEquals(12, call(field::getCaretPosition), "M-b: back a word");
        press(field, KeyCode.D, true, false, false);
        assertEquals("rotate left ow", call(field::getText), "C-d: the character after");
        press(field, KeyCode.K, true, false, false);
        assertEquals("rotate left ", call(field::getText), "C-k: to the end");
        press(field, KeyCode.K, true, false, false);
        assertEquals("rotate left ", call(field::getText), "at the end, nothing more");
        fx(() -> field.positionCaret(0));
        press(field, KeyCode.D, false, true, false);
        assertEquals(" left ", call(field::getText), "M-d: the word after");
        press(field, KeyCode.F, false, true, false);
        assertEquals(5, call(field::getCaretPosition), "M-f: to the end of the next word");
        press(field, KeyCode.SLASH, true, false, false);
        assertEquals("rotate left ", call(field::getText), "C-/: undo");
        press(field, KeyCode.SLASH, true, false, true);
        assertEquals(" left ", call(field::getText), "C-S-/: redo");
        press(field, KeyCode.MINUS, true, false, true);
        assertNotEquals(" left ", call(field::getText), "C-_: undo too");

        fx(() -> {
            field.setText("abc");
            field.selectRange(0, 2);
        });
        press(field, KeyCode.D, true, false, false);
        assertEquals("c", call(field::getText), "C-d on a selection deletes it");
        fx(() -> field.selectAll());
        press(field, KeyCode.W, false, true, false);
        press(field, KeyCode.W, true, false, false);
        assertEquals("", call(field::getText), "C-w: cut");
        press(field, KeyCode.Y, true, false, false);
        assertEquals("c", call(field::getText), "C-y: paste it back");

        // The character a handled chord would type is dropped, an ordinary one is not.
        press(field, KeyCode.A, true, false, false);
        type(field, "a", true);
        assertEquals("c", call(field::getText));
        fx(() -> Event.fireEvent(
                field, new KeyEvent(KeyEvent.KEY_RELEASED, "", "", KeyCode.A, false, true, false, false)));
        press(field, KeyCode.CONTROL, true, false, false); // a bare Ctrl has no chord
        fx(() -> {
            KeyEvent claimed = new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.Q, false, true, false, false);
            claimed.consume();
            Event.fireEvent(field, claimed);
        });
    }
}
