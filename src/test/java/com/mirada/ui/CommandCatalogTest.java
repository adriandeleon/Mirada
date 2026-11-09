package com.mirada.ui;

import java.util.Set;
import java.util.TreeSet;

import com.mirada.i18n.Messages;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every command's title is {@code command.<id>} and its palette line {@code command.<id>.desc} in the
 * catalogs (Pauta KBD-03, PAL-02), and the catalog has no command Mirada does not.
 */
class CommandCatalogTest {

    @Test
    void everyCommandHasItsTitleAndDescriptionInTheCatalog() {
        Set<String> missing = new TreeSet<>();
        for (Command command : Command.values()) {
            for (String key : new String[] {"command." + command.id(), "command." + command.id() + ".desc"})
                if (!Messages.has(key)) missing.add(key);
        }
        assertTrue(missing.isEmpty(), "missing from messages.properties: " + missing);
    }

    @Test
    void theCatalogNamesNoCommandMiradaLacks() {
        Set<String> ids = new TreeSet<>();
        for (Command command : Command.values()) ids.add(command.id());
        Set<String> stray = new TreeSet<>();
        for (String key : Messages.keys()) {
            if (!key.startsWith("command.")) continue;
            String id = key.substring("command.".length()).replaceFirst("\\.desc$", "");
            if (!ids.contains(id)) stray.add(key);
        }
        assertTrue(stray.isEmpty(), "titles for no command: " + stray);
    }

    /** In another language the palette still finds a command by its English title. */
    @Test
    void inAnotherLanguageTheEnglishTitleIsAKeyword() {
        try {
            Messages.init("de");
            assertTrue(
                    Command.ROTATE_CLOCKWISE.keywords().contains("Rotate Right"), Command.ROTATE_CLOCKWISE.keywords());
            assertTrue(CommandPalette.keywordScore(Command.ROTATE_CLOCKWISE, "rotate right") != FuzzyMatch.NO_SCORE);
        } finally {
            Messages.init("en");
        }
    }

    @Test
    void aPlatformsOwnWordIsUsedWhereItHasOne() {
        assertEquals("Show in Finder", Command.SHOW_IN_FOLDER.label(true, false));
        assertEquals("Show in File Explorer", Command.SHOW_IN_FOLDER.label(false, true));
        assertEquals("Show in Folder", Command.SHOW_IN_FOLDER.label(false, false));
        assertEquals("Move to Recycle Bin", Command.MOVE_TO_TRASH.label(false, true));
        assertEquals("Move to Trash", Command.MOVE_TO_TRASH.label(true, false));
    }
}
