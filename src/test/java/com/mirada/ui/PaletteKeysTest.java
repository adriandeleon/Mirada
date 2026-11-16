package com.mirada.ui;

import java.util.Map;

import com.mirada.ui.PaletteKeys.Action;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The palette moves on the fixed keys and on the active keymap's list moves and cancel chord (PAL-06,
 * PAL-07), and its legend names only keys that reach it.
 */
class PaletteKeysTest {

    private static final Keybindings CUA = Keybindings.layered(Keymap.load("cua", false), false, Map.of());
    private static final Keybindings EMACS = Keybindings.layered(Keymap.load("emacs", false), false, Map.of());

    private static Action resolve(@org.jspecify.annotations.Nullable String token, boolean queryField) {
        return PaletteKeys.resolve(token, queryField, EMACS);
    }

    @Test
    void theFixedKeysAlwaysWork() {
        assertEquals(Action.DOWN, resolve("down", true));
        assertEquals(Action.UP, resolve("up", true));
        assertEquals(Action.PAGE_DOWN, resolve("pagedown", true));
        assertEquals(Action.PAGE_UP, resolve("pageup", true));
        assertEquals(Action.ACCEPT, resolve("enter", true));
        assertEquals(Action.CANCEL, resolve("escape", true));
        assertEquals(Action.FIRST, resolve("C-home", true));
        assertEquals(Action.LAST, resolve("C-end", true));
        assertEquals(Action.FIRST, resolve("Cmd-up", true));
        assertEquals(Action.LAST, resolve("Cmd-down", true));
    }

    @Test
    void plainHomeAndEndBelongToTheQueryFieldWhenThereIsOne() {
        assertEquals(Action.NONE, resolve("home", true));
        assertEquals(Action.NONE, resolve("end", true));
        assertEquals(Action.FIRST, resolve("home", false));
        assertEquals(Action.LAST, resolve("end", false));
    }

    @Test
    void theEmacsKeymapsListMovesNavigate() {
        // Editora's Emacs keymap, token for token, read from the keymap's lists section.
        assertEquals(Action.DOWN, resolve("C-n", true));
        assertEquals(Action.UP, resolve("C-p", true));
        assertEquals(Action.PAGE_DOWN, resolve("C-v", true));
        assertEquals(Action.PAGE_UP, resolve("M-v", true));
        assertEquals(Action.FIRST, resolve("M-S-,", true));
        assertEquals(Action.LAST, resolve("M-S-.", true));
        assertEquals(Action.CANCEL, resolve("C-g", true));
    }

    /** CUA binds no list moves and Escape alone cancels: C-n and C-g are nothing there, not a fixed table's. */
    @Test
    void cuaMovesOnTheFixedKeysAlone() {
        assertEquals(Action.NONE, PaletteKeys.resolve("C-n", true, CUA));
        assertEquals(Action.NONE, PaletteKeys.resolve("C-p", true, CUA));
        assertEquals(Action.NONE, PaletteKeys.resolve("C-g", true, CUA));
        assertEquals(Action.DOWN, PaletteKeys.resolve("down", true, CUA));
        assertEquals(Action.CANCEL, PaletteKeys.resolve("escape", true, CUA));
    }

    /** The user's own {@code keys.nav.*} and {@code keys.window.escape} move and cancel the list too. */
    @Test
    void theUsersKeysMoveAndCancel() {
        Keybindings mine = Keybindings.layered(
                Keymap.load("emacs", false), false, Map.of("nav.lineDown", "C-j", "window.escape", "escape C-q"));
        assertEquals(Action.DOWN, PaletteKeys.resolve("C-j", true, mine));
        assertEquals(Action.NONE, PaletteKeys.resolve("C-n", true, mine), "the user's keys replace the keymap's");
        assertEquals(Action.CANCEL, PaletteKeys.resolve("C-q", true, mine));
        assertEquals(Action.NONE, PaletteKeys.resolve("C-g", true, mine));
    }

    @Test
    void anythingElseIsTyping() {
        assertEquals(Action.NONE, resolve("a", true));
        assertEquals(Action.NONE, resolve("n", true), "a bare n is a letter, not next");
        assertEquals(Action.NONE, resolve("C-a", true), "C-a is the query field's line start");
        assertEquals(Action.NONE, resolve("C-S-n", true), "the Emacs chords are unshifted");
        assertEquals(Action.NONE, resolve(null, true), "a modifier on its own");
    }

    @Test
    void upAndDownWrap() {
        assertEquals(1, PaletteKeys.target(Action.DOWN, 0, 3, 8));
        assertEquals(0, PaletteKeys.target(Action.DOWN, 2, 3, 8));
        assertEquals(2, PaletteKeys.target(Action.UP, 0, 3, 8));
        assertEquals(0, PaletteKeys.target(Action.DOWN, -1, 3, 8), "nothing selected: Down starts at the top");
        assertEquals(2, PaletteKeys.target(Action.UP, -1, 3, 8), "nothing selected: Up starts at the bottom");
    }

    @Test
    void pagingAndEndsClampInsteadOfWrapping() {
        assertEquals(8, PaletteKeys.target(Action.PAGE_DOWN, 0, 20, 8));
        assertEquals(19, PaletteKeys.target(Action.PAGE_DOWN, 15, 20, 8));
        assertEquals(0, PaletteKeys.target(Action.PAGE_UP, 3, 20, 8));
        assertEquals(7, PaletteKeys.target(Action.PAGE_UP, 15, 20, 8));
        assertEquals(0, PaletteKeys.target(Action.FIRST, 15, 20, 8));
        assertEquals(19, PaletteKeys.target(Action.LAST, 0, 20, 8));
        assertEquals(1, PaletteKeys.target(Action.PAGE_DOWN, 0, 20, 0), "a page is never less than one row");
    }

    @Test
    void nonNavigationAndEmptyListsHaveNoTarget() {
        assertEquals(-1, PaletteKeys.target(Action.ACCEPT, 0, 3, 8));
        assertEquals(-1, PaletteKeys.target(Action.NONE, 0, 3, 8));
        assertEquals(-1, PaletteKeys.target(Action.DOWN, 0, 0, 8));
        assertTrue(PaletteKeys.isNavigation(Action.PAGE_UP));
        assertFalse(PaletteKeys.isNavigation(Action.CANCEL));
    }

    @Test
    void theLegendNamesTheKeymapsChordsBesideTheFixedKeys() {
        // In the platform's notation — the same the menus use — not the raw tokens.
        String move = "↑↓ / " + KeyChord.display("C-n") + " " + KeyChord.display("C-p");
        String cancel = "Esc / " + KeyChord.display("C-g");
        assertEquals(
                move + " move  ·  ↵ run  ·  " + cancel + " cancel",
                PaletteKeys.legend(EMACS, PaletteKeys.hint("run", "↵")));
        assertTrue(
                PaletteKeys.legend(EMACS).contains("Ctrl")
                        || PaletteKeys.legend(EMACS).contains("⌃"),
                "a real key name");
        // PAL-09: another keymap, another legend.
        assertEquals("↑↓ move  ·  ↵ run  ·  Esc cancel", PaletteKeys.legend(CUA, PaletteKeys.hint("run", "↵")));
    }

    @Test
    void anEntryWithNoKeyIsLeftOutNotShownKeyless() {
        assertNull(PaletteKeys.hint("run", ""));
        assertNull(PaletteKeys.hint("run", null));
        String legend = PaletteKeys.legend(EMACS, (String) null);
        assertEquals(2, legend.split("  ·  ").length, legend);
    }
}
