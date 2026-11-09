package com.mirada.ui;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.mirada.ui.SettingsPages.Page;
import com.mirada.ui.SettingsPages.Row;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Settings window's pages and search, without the toolkit: a title hit ranks above a keyword
 * hit and a whole word above part of one (SET-03); every row has a palette command that changes it
 * (SET-06); every page has rows (SET-02).
 */
class SettingsPagesTest {

    private static final SettingsSearch.Entry TITLED =
            new SettingsSearch.Entry("Theme", "Light or dark.", "General", "");
    private static final SettingsSearch.Entry KEYWORDED =
            new SettingsSearch.Entry("Appearance", "How it looks.", "General", "theme colour");

    @Test
    void aTitleHitRanksAboveADescriptionHitAboveAKeywordHit() {
        SettingsSearch.Entry described = new SettingsSearch.Entry("Look", "The theme of the controls.", "General", "");
        assertTrue(SettingsSearch.score("theme", TITLED) > SettingsSearch.score("theme", described));
        assertTrue(SettingsSearch.score("theme", described) > SettingsSearch.score("theme", KEYWORDED));
        assertTrue(SettingsSearch.score("theme", KEYWORDED) > 0);
    }

    @Test
    void aWholeWordRanksAboveAWordStartAbovePartOfAWord() {
        assertEquals(3, SettingsSearch.field("zoom", "keep zoom when stepping"));
        assertEquals(2, SettingsSearch.field("zo", "keep zoom when stepping"));
        assertEquals(1, SettingsSearch.field("oom", "keep zoom when stepping"));
        assertEquals(0, SettingsSearch.field("x", "keep zoom"));
        SettingsSearch.Entry whole = new SettingsSearch.Entry("Sort by", "", "", "");
        SettingsSearch.Entry start = new SettingsSearch.Entry("Sorting", "", "", "");
        assertTrue(SettingsSearch.score("sort", whole) > SettingsSearch.score("sort", start));
    }

    @Test
    void everyWordMustMatchAndABlankQueryMatchesEverything() {
        assertEquals(0, SettingsSearch.score("theme zebra", TITLED));
        assertEquals(1, SettingsSearch.score("  ", TITLED));
        assertTrue(
                SettingsSearch.score("Theme", TITLED) > SettingsSearch.score("theme light", TITLED),
                "a query that is the whole title names the row");
    }

    @Test
    void theBestMatchOpensItsPageAndOnlyTheMatchingRowsShow() {
        SettingsPages.Found dark = SettingsPages.search("dark", Map.of());
        assertEquals(Page.GENERAL, dark.best());
        assertEquals(Set.of("Theme"), titles(dark.rows()));
        assertEquals(EnumSet.of(Page.GENERAL), dark.pages());

        SettingsPages.Found slideshow = SettingsPages.search("slideshow", Map.of());
        assertEquals(Page.FOLDER, slideshow.best());
        assertEquals(Set.of("Slideshow speed"), titles(slideshow.rows()));

        SettingsPages.Found keymap = SettingsPages.search("emacs", Map.of());
        assertEquals(Page.KEYS, keymap.best());

        SettingsPages.Found zip = SettingsPages.search("zip", Map.of());
        assertEquals(Page.BACKUP, zip.best());

        SettingsPages.Found nothing = SettingsPages.search("zebra", Map.of());
        assertTrue(nothing.rows().isEmpty());
        assertNull(nothing.best());
    }

    @Test
    void aWordOfTitlesOnALaterPageOpensThatPage() {
        SettingsPages.Found found = SettingsPages.search("names", Map.of());
        assertEquals(Page.THUMBNAILS, found.best());
        assertTrue(titles(found.rows()).contains("File names instead of pictures"));
    }

    @Test
    void aControlsTextFindsItsRow() {
        Row folder = SettingsPages.ROWS.stream()
                .filter(row -> row.command() == Command.EXPORT_CONFIG)
                .findFirst()
                .orElseThrow();
        assertTrue(SettingsPages.search("import", Map.of()).rows().contains(folder), "its description says Import");
        assertTrue(SettingsPages.search("mirada-config", Map.of(folder, "/home/me/mirada-config"))
                .rows()
                .contains(folder));
    }

    @Test
    void aBlankSearchShowsEveryRowAndMovesNoPage() {
        SettingsPages.Found all = SettingsPages.search("", Map.of());
        assertEquals(SettingsPages.ROWS.size(), all.rows().size());
        assertEquals(EnumSet.allOf(Page.class), all.pages());
        assertNull(all.best());
    }

    @Test
    void everyPageHasRowsAndEveryRowAPaletteCommand() {
        Set<Page> pages = EnumSet.noneOf(Page.class);
        Set<String> titles = new HashSet<>();
        for (Row row : SettingsPages.ROWS) {
            pages.add(row.page());
            assertTrue(titles.add(row.title()), row.title() + " twice");
            assertTrue(Command.inPalette(Command.Context.VIEWER).contains(row.command()), row.title());
            assertTrue(row.description().endsWith("."), row.title());
        }
        assertEquals(EnumSet.allOf(Page.class), pages);
        for (Page page : Page.values())
            assertTrue(!page.title().isBlank() && !page.icon().isBlank());
        assertEquals(
                SettingsPages.ROWS.size(),
                SettingsPages.cards().values().stream()
                        .flatMap(cards -> cards.values().stream())
                        .mapToInt(java.util.List::size)
                        .sum());
    }

    /** SET-06: each setting's own command is a row's, so none is only a Settings row. */
    @Test
    void everySettingsCommandIsARowsCommand() {
        Set<Command> rows = new HashSet<>();
        for (Row row : SettingsPages.ROWS) rows.add(row.command());
        for (Command command : Command.values()) {
            if (command.group().equals("Settings") && command != Command.IMPORT_CONFIG)
                assertTrue(rows.contains(command), command.id() + " has no row");
        }
    }

    private static Set<String> titles(Set<Row> rows) {
        Set<String> out = new HashSet<>();
        for (Row row : rows) out.add(row.title());
        return out;
    }
}
