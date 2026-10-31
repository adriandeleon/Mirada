package com.mirada.ui;

import javafx.scene.input.KeyCode;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void otherKeysAreNotOurs() {
        assertNull(Command.forKey(KeyCode.A, false, false));
    }
}
