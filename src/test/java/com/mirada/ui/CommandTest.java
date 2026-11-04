package com.mirada.ui;

import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandTest {

    @Test
    void arrowsAndPagesWalkTheFolder() {
        assertEquals(Command.NEXT, key(KeyCode.RIGHT, false, false));
        assertEquals(Command.NEXT, key(KeyCode.PAGE_DOWN, false, false));
        assertEquals(Command.PREVIOUS, key(KeyCode.LEFT, false, false));
        assertEquals(Command.PREVIOUS, key(KeyCode.BACK_SPACE, false, false));
        assertEquals(Command.FIRST, key(KeyCode.HOME, false, false));
        assertEquals(Command.LAST, key(KeyCode.END, false, false));
    }

    @Test
    void spaceGoesForwardAndShiftSpaceBack() {
        assertEquals(Command.NEXT, key(KeyCode.SPACE, false, false));
        assertEquals(Command.PREVIOUS, key(KeyCode.SPACE, true, false));
    }

    @Test
    void zoomWorksBareAndWithTheShortcut() {
        for (boolean shortcut : new boolean[] {false, true}) {
            assertEquals(Command.ZOOM_IN, key(KeyCode.EQUALS, false, shortcut));
            assertEquals(Command.ZOOM_OUT, key(KeyCode.MINUS, false, shortcut));
            assertEquals(Command.FIT, key(KeyCode.DIGIT0, false, shortcut));
            assertEquals(Command.ACTUAL_SIZE, key(KeyCode.DIGIT1, false, shortcut));
        }
    }

    @Test
    void shiftReversesTheTurn() {
        assertEquals(Command.ROTATE_CLOCKWISE, key(KeyCode.R, false, false));
        assertEquals(Command.ROTATE_COUNTERCLOCKWISE, key(KeyCode.R, true, false));
    }

    @Test
    void windowCommandsNeedTheShortcut() {
        assertEquals(Command.OPEN, key(KeyCode.O, false, true));
        assertEquals(Command.CLOSE, key(KeyCode.W, false, true));
        assertNull(key(KeyCode.O, false, false));
        assertNull(key(KeyCode.W, false, false));
        // The shortcut does not turn a navigation key into something else.
        assertNull(key(KeyCode.RIGHT, false, true));
    }

    @Test
    void thePaletteOpensOnShortcutShiftPAndF1() {
        assertEquals(Command.PALETTE, key(KeyCode.P, true, true));
        assertEquals(Command.PALETTE, key(KeyCode.F1, false, false));
        assertNull(key(KeyCode.P, false, true));
    }

    /** The chord a command shows, in the palette and the context menu, must be one that runs it. */
    @Test
    void everyShownChordRunsItsCommand() {
        for (boolean mac : new boolean[] {false, true}) {
            for (Command command : Command.values()) {
                if (!(command.chord(mac) instanceof KeyCodeCombination chord)) continue; // + and -, by character
                boolean shift = chord.getShift() == KeyCombination.ModifierValue.DOWN;
                boolean shortcut = chord.getShortcut() == KeyCombination.ModifierValue.DOWN;
                boolean control = mac ? chord.getControl() == KeyCombination.ModifierValue.DOWN : shortcut;
                assertEquals(
                        command,
                        Command.forKey(chord.getCode(), shift, shortcut, control, mac),
                        command.name() + (mac ? " on macOS" : ""));
            }
        }
    }

    @Test
    void aMacTrashesWithCommandBackspaceAndBackspaceAloneStillStepsBack() {
        assertEquals(Command.MOVE_TO_TRASH, mac(KeyCode.BACK_SPACE, false, true, false));
        assertEquals(Command.PREVIOUS, mac(KeyCode.BACK_SPACE, false, false, false));
        assertEquals(Command.MOVE_TO_TRASH, mac(KeyCode.DELETE, false, false, false));
        assertEquals(
                new KeyCodeCombination(KeyCode.BACK_SPACE, KeyCombination.SHORTCUT_DOWN),
                Command.MOVE_TO_TRASH.chord(true));
        // Elsewhere Ctrl-Backspace is a text key, never a delete.
        assertNull(key(KeyCode.BACK_SPACE, false, true));
    }

    @Test
    void aMacHasPreviewsAndFindersKeys() {
        assertEquals(Command.RENAME, mac(KeyCode.ENTER, false, false, false));
        assertEquals(Command.INFO, mac(KeyCode.I, false, true, false));
        assertEquals(Command.ROTATE_CLOCKWISE, mac(KeyCode.R, false, true, false));
        assertEquals(Command.ROTATE_COUNTERCLOCKWISE, mac(KeyCode.L, false, true, false));
        assertEquals(Command.FULL_SCREEN, mac(KeyCode.F, false, true, true));
        // Ctrl-Cmd takes nothing else.
        assertNull(mac(KeyCode.O, false, true, true));
        // The function keys still work for those who have them.
        assertEquals(Command.RENAME, mac(KeyCode.F2, false, false, false));
    }

    @Test
    void returnAndTheMacChordsAreNotOursElsewhere() {
        assertNull(key(KeyCode.ENTER, false, false));
        assertNull(key(KeyCode.L, false, true));
        assertNull(key(KeyCode.I, false, true));
        assertEquals(new KeyCodeCombination(KeyCode.F2), Command.RENAME.chord(false));
        assertEquals(new KeyCodeCombination(KeyCode.DELETE), Command.MOVE_TO_TRASH.chord(false));
    }

    @Test
    void ctrlRTurnsEverywhere() {
        assertEquals(Command.ROTATE_CLOCKWISE, key(KeyCode.R, false, true));
        assertEquals(Command.ROTATE_COUNTERCLOCKWISE, key(KeyCode.R, true, true));
    }

    @Test
    void escapeClosesTheWindowOnlyOnLinux() {
        assertTrue(Command.escapeCloses("Linux"));
        assertFalse(Command.escapeCloses("Mac OS X"));
        assertFalse(Command.escapeCloses("Windows 11"));
    }

    @Test
    void thePaletteListsEveryCommandButItselfEscapeAndPanning() {
        assertFalse(Command.inPalette().contains(Command.PALETTE));
        assertFalse(Command.inPalette().contains(Command.ESCAPE));
        assertFalse(Command.inPalette().contains(Command.PAN_LEFT));
        assertEquals(Command.values().length - 6, Command.inPalette().size());
        for (Command command : Command.inPalette()) {
            assertFalse(command.label().isBlank(), command.name());
            assertFalse(command.description().isBlank(), command.name());
        }
    }

    @Test
    void shiftArrowsPanAndBareArrowsStep() {
        assertEquals(Command.PAN_LEFT, key(KeyCode.LEFT, true, false));
        assertEquals(Command.PAN_RIGHT, key(KeyCode.RIGHT, true, false));
        assertEquals(Command.PAN_UP, key(KeyCode.UP, false, false));
        assertEquals(Command.PAN_DOWN, key(KeyCode.DOWN, true, false));
        assertEquals(Command.NEXT, key(KeyCode.RIGHT, false, false));
    }

    @Test
    void fileKeysAreTheDesktopsOwn() {
        assertEquals(Command.MOVE_TO_TRASH, key(KeyCode.DELETE, false, false));
        assertEquals(Command.RENAME, key(KeyCode.F2, false, false));
        assertEquals(Command.SLIDESHOW, key(KeyCode.F5, false, false));
        assertEquals(Command.SHORTCUTS, key(KeyCode.SLASH, true, true));
        assertEquals(Command.KEEP_ZOOM, key(KeyCode.Z, false, false));
    }

    @Test
    void everyCommandHasAGroupAndTheShortcutsWindowListsEveryKey() {
        int keyed = 0;
        for (Command command : Command.values()) {
            assertFalse(command.group().isBlank(), command.name());
            if (command.chord() != javafx.scene.input.KeyCombination.NO_MATCH) keyed++;
        }
        assertEquals(
                keyed,
                ShortcutsWindow.grouped().values().stream()
                        .mapToInt(java.util.List::size)
                        .sum());
    }

    @Test
    void fileCommandsNeedAnImageAndASlideshowTwo() {
        assertEquals("No image is showing.", Command.MOVE_TO_TRASH.unavailable(0, 1, false));
        assertNull(Command.RENAME.unavailable(0, 1, true));
        assertEquals("A slideshow needs a folder with more than one image.", Command.SLIDESHOW.unavailable(0, 1, true));
        assertNull(Command.SLIDESHOW.unavailable(0, 2, true));
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
        assertNull(key(KeyCode.A, false, false));
    }

    /** A press on Linux or Windows, where the shortcut is the Ctrl key. */
    private static @Nullable Command key(KeyCode code, boolean shift, boolean shortcut) {
        return Command.forKey(code, shift, shortcut, shortcut, false);
    }

    /** A press on macOS: {@code shortcut} is Cmd, {@code control} the Ctrl key. */
    private static @Nullable Command mac(KeyCode code, boolean shift, boolean shortcut, boolean control) {
        return Command.forKey(code, shift, shortcut, control, true);
    }
}
