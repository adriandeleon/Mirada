package com.mirada.ui;

import java.util.List;

import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;

import com.mirada.settings.Settings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The palette opens on the user's keys: by default its own chord, M-x and F1. */
class PaletteChordsTest {

    private static final KeyCombination CTRL_SHIFT_P =
            new KeyCodeCombination(KeyCode.P, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN);
    private static final KeyCombination CMD_SHIFT_P =
            new KeyCodeCombination(KeyCode.P, KeyCombination.META_DOWN, KeyCombination.SHIFT_DOWN);
    private static final KeyCombination ALT_X = new KeyCodeCombination(KeyCode.X, KeyCombination.ALT_DOWN);
    private static final KeyCombination F1 = new KeyCodeCombination(KeyCode.F1);

    @Test
    void theDefaultsAreThePalettesChordMxAndF1() {
        assertEquals(List.of(CTRL_SHIFT_P, ALT_X, F1), PaletteChords.parse(Settings.defaultPaletteKeys(false), false));
        assertEquals(List.of(CMD_SHIFT_P, ALT_X, F1), PaletteChords.parse(Settings.defaultPaletteKeys(true), true));
    }

    @Test
    void mxMatchesAltXAndNothingElse() {
        List<KeyCombination> chords = PaletteChords.parse(Settings.defaultPaletteKeys(false), false);
        assertTrue(matches(chords, press(KeyCode.X, false, false, true, false)));
        assertTrue(matches(chords, press(KeyCode.P, true, true, false, false)));
        assertTrue(matches(chords, press(KeyCode.F1, false, false, false, false)));
        assertFalse(matches(chords, press(KeyCode.X, false, false, false, false)), "bare x");
        assertFalse(matches(chords, press(KeyCode.X, true, false, true, false)), "M-S-x");
        assertFalse(matches(chords, press(KeyCode.P, false, true, false, false)), "C-p is Go to Image");
    }

    @Test
    void unreadableKeysAreSkippedAndNoneLeftIsTheDefault() {
        assertEquals(List.of(ALT_X), PaletteChords.parse("M-x, nonsense M-x", false));
        assertEquals(List.of(F1, ALT_X), PaletteChords.parse("  f1,M-x  ", false));
        List<KeyCombination> defaults = PaletteChords.parse(Settings.defaultPaletteKeys(false), false);
        assertEquals(defaults, PaletteChords.parse("nonsense", false));
        assertEquals(defaults, PaletteChords.parse("", false));
        assertEquals(defaults, PaletteChords.parse(null, false));
    }

    @Test
    void addingAKeyAppendsItAndAddingItAgainTakesItOff() {
        assertEquals("C-S-p M-x f1 C-k", PaletteChords.toggled("C-S-p M-x f1", "C-k"));
        assertEquals("C-S-p f1", PaletteChords.toggled("C-S-p M-x f1", "M-x"));
        assertEquals("M-x", PaletteChords.toggled("M-x", "M-x"), "the last key stays");
        assertEquals("M-x f1", PaletteChords.toggled("M-x junk f1", "junk"), "unreadable ones go");
    }

    @Test
    void thePaletteKeepsTheKeysItMovesAndEditsBy() {
        assertTrue(PaletteChords.keptByPalette("C-p"));
        assertTrue(PaletteChords.keptByPalette("C-n"));
        assertTrue(PaletteChords.keptByPalette("M-f"));
        assertTrue(PaletteChords.keptByPalette("escape"));
        assertFalse(PaletteChords.keptByPalette("M-x"));
        assertFalse(PaletteChords.keptByPalette("C-S-p"));
        assertFalse(PaletteChords.keptByPalette("f1"));
        assertFalse(PaletteChords.keptByPalette(null));
    }

    @Test
    void theShortcutsWindowNamesTheOtherKeys() {
        String also = ShortcutsWindow.also(false, List.of(CTRL_SHIFT_P, ALT_X, F1));
        assertTrue(
                also.contains(ALT_X.getDisplayText() + " and " + F1.getDisplayText() + " for the command palette"),
                also);
        assertFalse(ShortcutsWindow.also(false, List.of(CTRL_SHIFT_P)).contains("palette"));
        assertEquals("A, B and C", ShortcutsWindow.inWords(List.of("A", "B", "C")));
        assertEquals("A", ShortcutsWindow.inWords(List.of("A")));
    }

    @Test
    void theMacMenuShowsTheFirstKeyWithCommand() {
        assertEquals(
                PaletteChords.current().stream()
                        .filter(c -> c.getMeta() == KeyCombination.ModifierValue.DOWN
                                || c.getShortcut() == KeyCombination.ModifierValue.DOWN)
                        .findFirst()
                        .orElse(null),
                MacMenus.accelerator(Command.PALETTE));
    }

    private static boolean matches(List<KeyCombination> chords, KeyEvent e) {
        return chords.stream().anyMatch(chord -> chord.match(e));
    }

    private static KeyEvent press(KeyCode code, boolean shift, boolean control, boolean alt, boolean meta) {
        return new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, shift, control, alt, meta);
    }
}
