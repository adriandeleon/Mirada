package com.mirada.ui;

import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class CommandTest {

    @Test
    void arrowsAndPagesWalkTheFolder() {
        assertEquals(Command.NEXT, Command.forKey(KeyCode.RIGHT, false, false));
        assertEquals(Command.NEXT, Command.forKey(KeyCode.PAGE_DOWN, false, false));
        assertEquals(Command.PREVIOUS, Command.forKey(KeyCode.LEFT, false, false));
        assertEquals(Command.PREVIOUS, Command.forKey(KeyCode.BACK_SPACE, false, false));
        assertEquals(Command.FIRST, Command.forKey(KeyCode.HOME, false, false));
        assertEquals(Command.LAST, Command.forKey(KeyCode.END, false, false));
    }

    @Test
    void spaceGoesForwardAndShiftSpaceBack() {
        assertEquals(Command.NEXT, Command.forKey(KeyCode.SPACE, false, false));
        assertEquals(Command.PREVIOUS, Command.forKey(KeyCode.SPACE, true, false));
    }

    @Test
    void zoomWorksBareAndWithTheShortcut() {
        for (boolean shortcut : new boolean[] {false, true}) {
            assertEquals(Command.ZOOM_IN, Command.forKey(KeyCode.EQUALS, false, shortcut));
            assertEquals(Command.ZOOM_OUT, Command.forKey(KeyCode.MINUS, false, shortcut));
            assertEquals(Command.FIT, Command.forKey(KeyCode.DIGIT0, false, shortcut));
            assertEquals(Command.ACTUAL_SIZE, Command.forKey(KeyCode.DIGIT1, false, shortcut));
        }
    }

    @Test
    void shiftReversesTheTurn() {
        assertEquals(Command.ROTATE_CLOCKWISE, Command.forKey(KeyCode.R, false, false));
        assertEquals(Command.ROTATE_COUNTERCLOCKWISE, Command.forKey(KeyCode.R, true, false));
    }

    @Test
    void windowCommandsNeedTheShortcut() {
        assertEquals(Command.OPEN, Command.forKey(KeyCode.O, false, true));
        assertEquals(Command.CLOSE, Command.forKey(KeyCode.W, false, true));
        assertNull(Command.forKey(KeyCode.O, false, false));
        assertNull(Command.forKey(KeyCode.W, false, false));
        // The shortcut does not turn a navigation key into something else.
        assertNull(Command.forKey(KeyCode.RIGHT, false, true));
    }

    @Test
    void thePaletteOpensOnShortcutShiftPAndF1() {
        assertEquals(Command.PALETTE, Command.forKey(KeyCode.P, true, true));
        assertEquals(Command.PALETTE, Command.forKey(KeyCode.F1, false, false));
        assertNull(Command.forKey(KeyCode.P, false, true));
    }

    /** The chord a command shows, in the palette and the context menu, must be one that runs it. */
    @Test
    void everyShownChordRunsItsCommand() {
        for (Command command : Command.values()) {
            if (!(command.chord() instanceof KeyCodeCombination chord)) continue; // + and -, by character
            boolean shift = chord.getShift() == KeyCombination.ModifierValue.DOWN;
            boolean shortcut = chord.getShortcut() == KeyCombination.ModifierValue.DOWN;
            assertEquals(command, Command.forKey(chord.getCode(), shift, shortcut), command.name());
        }
    }

    @Test
    void thePaletteListsEveryCommandButItselfAndEscape() {
        assertFalse(Command.inPalette().contains(Command.PALETTE));
        assertFalse(Command.inPalette().contains(Command.ESCAPE));
        assertEquals(Command.values().length - 2, Command.inPalette().size());
        for (Command command : Command.inPalette()) {
            assertFalse(command.label().isBlank(), command.name());
            assertFalse(command.description().isBlank(), command.name());
        }
    }

    @Test
    void steppingNeedsSomewhereToGo() {
        assertEquals("No folder is open.", Command.NEXT.unavailable(0, 0, false));
        assertEquals("This is the last image.", Command.NEXT.unavailable(2, 3, true));
        assertEquals("This is the last image.", Command.LAST.unavailable(2, 3, true));
        assertEquals("This is the first image.", Command.PREVIOUS.unavailable(0, 3, true));
        assertNull(Command.NEXT.unavailable(1, 3, true));
        assertNull(Command.FIRST.unavailable(1, 3, true));
    }

    @Test
    void zoomingAndTurningNeedAnImage() {
        assertEquals("No image is showing.", Command.ZOOM_IN.unavailable(0, 1, false));
        assertEquals("No image is showing.", Command.ROTATE_CLOCKWISE.unavailable(0, 1, false));
        assertNull(Command.FIT.unavailable(0, 1, true));
        assertNull(Command.OPEN.unavailable(0, 0, false));
    }

    @Test
    void otherKeysAreNotOurs() {
        assertNull(Command.forKey(KeyCode.A, false, false));
    }
}
