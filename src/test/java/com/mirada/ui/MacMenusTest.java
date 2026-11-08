package com.mirada.ui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MacMenusTest {

    /** Every command the palette lists is in a menu once, but About and Settings: the app menu's. */
    @Test
    void theMenusHoldThePalettesCommandsOnce() {
        List<Command> inMenus = new ArrayList<>();
        for (MacMenus.Heading heading : MacMenus.HEADINGS) heading.groups().forEach(inMenus::addAll);
        Set<Command> expected = new HashSet<>(Command.inPalette());
        expected.remove(Command.ABOUT);
        expected.remove(Command.SETTINGS);
        expected.add(Command.PALETTE);
        assertEquals(expected, new HashSet<>(inMenus));
        assertEquals(inMenus.size(), new HashSet<>(inMenus).size(), "a command in two menus");
    }

    /** A chord a menu shows must run its command on a Mac, as the keyboard would. */
    @Test
    void everyMenuChordRunsItsCommandOnAMac() {
        for (Command command : Command.values()) {
            if (command == Command.PALETTE) continue; // the user's keys: PaletteChordsTest
            if (!(MacMenus.accelerator(command) instanceof KeyCodeCombination chord)) continue;
            assertEquals(KeyCombination.ModifierValue.DOWN, chord.getShortcut(), command.name());
            boolean shift = chord.getShift() == KeyCombination.ModifierValue.DOWN;
            boolean control = chord.getControl() == KeyCombination.ModifierValue.DOWN;
            assertEquals(command, Command.forKey(chord.getCode(), shift, true, control, true), command.name());
        }
    }

    /** Help opens with the palette, then the documentation (DOCS-06, DOCS-07, Pauta 4.0.1). */
    @Test
    void helpPutsTheDocumentationRightAfterThePalette() {
        MacMenus.Heading help = MacMenus.HEADINGS.getLast();
        assertEquals("Help", help.title());
        assertEquals(
                List.of(Command.PALETTE, Command.DOCUMENTATION),
                help.groups().getFirst().subList(0, 2));
    }

    @Test
    void bareKeysAreNotKeyEquivalents() {
        assertNull(MacMenus.accelerator(Command.NEXT));
        assertNull(MacMenus.accelerator(Command.RENAME));
        assertNull(MacMenus.accelerator(Command.EDIT));
    }
}
