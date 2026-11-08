package com.mirada.ui;

import java.util.Set;

import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class IconsTest {

    /** The order's choices are ticks in a submenu, which has the icon; everything else has its own. */
    private static final Set<Command> UNICONED =
            Set.of(Command.SORT_BY_NAME, Command.SORT_BY_DATE, Command.SORT_BY_SIZE, Command.REVERSE_ORDER);

    /** Keys only, never a menu's item: Escape and panning. */
    private static final Set<Command> KEYS_ONLY =
            Set.of(Command.ESCAPE, Command.PAN_LEFT, Command.PAN_RIGHT, Command.PAN_UP, Command.PAN_DOWN);

    @Test
    void everyCommandInTheMenusHasAnIcon() {
        for (Command command : Command.values()) {
            if (command.context() != Command.Context.VIEWER || KEYS_ONLY.contains(command)) continue;
            if (UNICONED.contains(command)) assertNull(Icons.forCommand(command), command.name());
            else assertNotNull(Icons.forCommand(command), command.name());
        }
    }

    /** TBAR-03: a tooltip is the title and the chord, built, and the title alone without one. */
    @Test
    void aTooltipIsTheTitleAndItsChord() {
        assertEquals("Rotate Right (R)", Icons.tooltip("Rotate Right", new KeyCodeCombination(KeyCode.R)));
        assertEquals("Main Menu", Icons.tooltip("Main Menu", KeyCombination.NO_MATCH));
        assertEquals(
                "Rename (" + Command.RENAME.chord().getDisplayText() + ")",
                Icons.tooltip(Command.RENAME),
                "the menu's … is not a tooltip's");
    }

    /** MENU-14: these were borrowed from other commands, and have glyphs of their own now. */
    @Test
    void noCommandBorrowsAnothersGlyph() {
        assertNotEquals(Icons.forCommand(Command.NOTE), Icons.forCommand(Command.BOOKMARKS));
        assertNotEquals(Icons.forCommand(Command.INFO), Icons.forCommand(Command.ABOUT));
        assertNotEquals(Icons.forCommand(Command.ZOOM_OUT), Icons.forCommand(Command.FIT));
    }

    /** ICON-20: full screen's glyph is the way out while in it, in the menus as on the button. */
    @Test
    void fullScreensGlyphFollowsTheWindow() {
        try {
            Icons.setFullScreen(true);
            assertEquals(Icons.LEAVE_FULL_SCREEN, Icons.forCommand(Command.FULL_SCREEN));
            assertEquals("Leave Full Screen", Icons.title(Command.FULL_SCREEN));
        } finally {
            Icons.setFullScreen(false);
        }
        assertEquals(Icons.FULL_SCREEN, Icons.forCommand(Command.FULL_SCREEN));
    }
}
