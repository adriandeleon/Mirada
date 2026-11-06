package com.mirada.ui;

import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class MenuChordTest {

    @Test
    void showsTheCommandsChord() {
        for (Command command : Command.values()) {
            KeyCombination chord = command.chord();
            KeyCombination shown = MenuChord.of(command);
            if (chord == KeyCombination.NO_MATCH) {
                assertNull(shown, command.name());
            } else {
                assertNotNull(shown, command.name());
                assertEquals(chord.getDisplayText(), shown.getDisplayText(), command.name());
            }
        }
    }

    /** The scene runs a menu's accelerators: one that matched would run a key twice, or under the palette. */
    @Test
    void neverMatchesAKey() {
        KeyCombination rotate = MenuChord.of(Command.ROTATE_CLOCKWISE);
        assertNotNull(rotate);
        assertFalse(rotate.match(press(KeyCode.R, false)));
        KeyCombination open = MenuChord.of(Command.OPEN);
        assertNotNull(open);
        assertFalse(open.match(press(KeyCode.O, true)));
    }

    /** KeyCombination's own equality is by modifiers, and the scene keeps its accelerators in a map. */
    @Test
    void isEqualOnlyToItself() {
        KeyCombination a = MenuChord.of(Command.ROTATE_CLOCKWISE);
        KeyCombination b = MenuChord.of(Command.INFO);
        assertNotNull(a);
        assertNotEquals(a, b);
        assertNotEquals(a, MenuChord.of(Command.ROTATE_CLOCKWISE));
        assertNotEquals(a, Command.INFO.chord());
    }

    private static KeyEvent press(KeyCode code, boolean shortcut) {
        boolean mac = System.getProperty("os.name", "").startsWith("Mac");
        return new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, shortcut && !mac, false, shortcut && mac);
    }
}
