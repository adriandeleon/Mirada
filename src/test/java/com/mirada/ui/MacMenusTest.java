package com.mirada.ui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MacMenusTest {

    /**
     * Every viewer command is in a menu once, but About and Settings (the app menu's), Copy Details
     * (About's button), and Escape and panning, which are keys. The editor's tools and the Bookmarks
     * window's are their own windows'.
     */
    @Test
    void theMenusHoldTheViewersCommandsOnce() {
        List<Command> inMenus = new ArrayList<>();
        for (MacMenus.Heading heading : MacMenus.HEADINGS) heading.groups().forEach(inMenus::addAll);
        Set<Command> expected = new HashSet<>();
        for (Command command : Command.values()) {
            if (command.context() == Command.Context.VIEWER) expected.add(command);
        }
        expected.removeAll(Set.of(
                Command.ABOUT,
                Command.SETTINGS,
                Command.COPY_DETAILS,
                Command.ESCAPE,
                Command.PAN_LEFT,
                Command.PAN_RIGHT,
                Command.PAN_UP,
                Command.PAN_DOWN));
        assertEquals(expected, new HashSet<>(inMenus));
        assertEquals(inMenus.size(), new HashSet<>(inMenus).size(), "a command in two menus");
    }

    /** A chord a menu shows must run its command on a Mac, as the keyboard would. */
    @Test
    void everyMenuChordRunsItsCommandOnAMac() {
        Keybindings mac = Keybindings.layered(Keymap.load("cua", true), false, Map.of());
        int shown = 0;
        for (Command command : Command.values()) {
            if (!(MacMenus.accelerator(command, mac) instanceof KeyCodeCombination chord)) continue;
            shown++;
            assertEquals(KeyCombination.ModifierValue.DOWN, chord.getMeta(), command.name());
            String token = KeyChord.token(
                    chord.getCode(),
                    chord.getControl() == KeyCombination.ModifierValue.DOWN,
                    false,
                    true,
                    chord.getShift() == KeyCombination.ModifierValue.DOWN);
            assertEquals(command, mac.commandFor(command.context(), java.util.Objects.requireNonNull(token)));
        }
        assertTrue(shown > 20, "the Mac's Cmd keys are in its keymap");
        // Preview's keys, among the bare ones.
        assertEquals(
                new KeyCodeCombination(javafx.scene.input.KeyCode.EQUALS, KeyCombination.META_DOWN),
                MacMenus.accelerator(Command.ZOOM_IN, mac));
        assertEquals(
                new KeyCodeCombination(
                        javafx.scene.input.KeyCode.F, KeyCombination.CONTROL_DOWN, KeyCombination.META_DOWN),
                MacMenus.accelerator(Command.FULL_SCREEN, mac));
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
