package com.mirada.ui;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The palette's ranking over Mirada's own commands. */
class CommandPaletteTest {

    private static List<Command> find(String query) {
        return CommandPalette.orderedMatches(Command.inPalette(), query);
    }

    @Test
    void anEmptyQueryKeepsThePaletteOrder() {
        assertEquals(Command.inPalette(), find(""));
        assertEquals(Command.inPalette(), find("   "));
    }

    @Test
    void labelsMatchFuzzily() {
        assertEquals(Command.NEXT, find("next").getFirst());
        assertEquals(Command.ROTATE_COUNTERCLOCKWISE, find("rot le").getFirst());
        assertEquals(Command.FULL_SCREEN, find("fs").getFirst());
        assertTrue(find("zoom").containsAll(List.of(Command.ZOOM_IN, Command.ZOOM_OUT, Command.FIT)));
    }

    @Test
    void keywordsFindWhatTheLabelDoesNot() {
        assertEquals(Command.CLOSE, find("quit").getFirst());
        assertEquals(Command.ACTUAL_SIZE, find("100").getFirst());
        assertEquals(Command.OPEN, find("file").getFirst());
    }

    @Test
    void aLabelMatchBeatsAKeywordMatch() {
        // "turn" is a keyword of both rotations, but "Full Screen" contains nothing of it, and any
        // label match outranks a keyword match.
        assertTrue(CommandPalette.keywordScore(Command.ROTATE_CLOCKWISE, "turn") < -CommandPalette.KEYWORD_PENALTY / 2);
    }

    @Test
    void nonsenseMatchesNothing() {
        assertTrue(find("qqqq").isEmpty());
    }

    @Test
    void theListShowsWholeRows() {
        assertEquals(CommandPalette.VISIBLE_ROWS * CommandPalette.ROW_HEIGHT + 4, CommandPalette.listHeight(4));
    }
}
