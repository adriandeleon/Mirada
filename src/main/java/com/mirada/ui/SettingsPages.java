package com.mirada.ui;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

/**
 * What the Settings window holds, without the toolkit: its pages in sidebar order, each row's page,
 * card, words and command (SET-02, SET-06, Pauta 4.0.1), and what a search shows (SET-03). The
 * window ({@link SettingsWindow}) builds a control for each row; the rules are here, so they test
 * without it.
 */
final class SettingsPages {

    /** The pages, in sidebar order, each with its icon. */
    enum Page {
        GENERAL("General", Icons.SLIDERS),
        VIEWER("Viewer", Icons.PICTURE),
        THUMBNAILS("Thumbnails", Icons.STRIP),
        FOLDER("Folder", Icons.SORT),
        KEYS("Keys", Icons.KEYBOARD),
        BACKUP("Backup", Icons.DOWNLOAD);

        private final String title;
        private final String icon;

        Page(String title, String icon) {
            this.title = title;
            this.icon = icon;
        }

        String title() {
            return title;
        }

        /** The icon's SVG path ({@link Icons#of}). */
        String icon() {
            return icon;
        }
    }

    /**
     * One row: where it stands, what it says, the words it is also found by, and the palette command
     * that changes the same setting (SET-06), or opens the same action.
     */
    record Row(Page page, String card, String title, String description, String keywords, Command command) {

        /** What the search finds this row by, with the text its control shows. */
        SettingsSearch.Entry entry(String visibleText) {
            return new SettingsSearch.Entry(title, description, visibleText, page.title() + " " + card, keywords);
        }
    }

    /** Every row, by page and card, in the order the window shows them. */
    static final List<Row> ROWS = List.of(
            new Row(
                    Page.GENERAL,
                    "Appearance",
                    "Theme",
                    "Light or dark controls and backgrounds.",
                    "dark light system mode colour color look",
                    Command.THEME),
            new Row(
                    Page.GENERAL,
                    "At start",
                    "Thumbnails at start",
                    "Opens the thumbnail strip when Mirada starts. T toggles it.",
                    "startup launch filmstrip strip",
                    Command.THUMBNAILS_AT_START),
            new Row(
                    Page.GENERAL,
                    "At start",
                    "Properties at start",
                    "Opens the properties sidebar when Mirada starts. I toggles it.",
                    "startup launch info details exif sidebar",
                    Command.PROPERTIES_AT_START),
            new Row(
                    Page.GENERAL,
                    "Updates",
                    "Check for updates",
                    "Once a day, a few seconds after start, asks GitHub whether a newer Mirada is out, and says so"
                            + " in the status bar. Nothing is downloaded.",
                    "version release network automatic",
                    Command.CHECK_DAILY),
            new Row(
                    Page.VIEWER,
                    "Viewer",
                    "Status bar",
                    "A line along the bottom saying what the last command did; a click on it lists every message.",
                    "messages echo footer",
                    Command.STATUS_BAR),
            new Row(
                    Page.VIEWER,
                    "Viewer",
                    "Hide controls after",
                    "Seconds without moving the pointer before the floating controls fade.",
                    "delay fade timeout overlay buttons seconds",
                    Command.HIDE_CONTROLS_AFTER),
            new Row(
                    Page.VIEWER,
                    "Viewer",
                    "Keep zoom when stepping",
                    "Moving to another image keeps the zoom and position, to compare the same detail across shots."
                            + " Z toggles it.",
                    "lock fit compare",
                    Command.KEEP_ZOOM),
            new Row(
                    Page.THUMBNAILS,
                    "Thumbnail strip",
                    "Thumbnails at the side",
                    "A column down the left of the window instead of a row along the bottom.",
                    "vertical column filmstrip layout left",
                    Command.THUMBNAILS_AT_SIDE),
            new Row(
                    Page.THUMBNAILS,
                    "Thumbnail strip",
                    "File names under thumbnails",
                    "Each thumbnail has its file name written under it.",
                    "captions labels filmstrip",
                    Command.THUMBNAIL_CAPTIONS),
            new Row(
                    Page.THUMBNAILS,
                    "Thumbnail strip",
                    "File names instead of pictures",
                    "The strip lists the folder by name, and loads no thumbnails.",
                    "list text filmstrip",
                    Command.THUMBNAIL_NAMES),
            new Row(
                    Page.FOLDER,
                    "Order",
                    "Sort by",
                    "The order images are stepped through.",
                    "name date modified size sorting",
                    Command.SORT_BY_NAME),
            new Row(
                    Page.FOLDER,
                    "Order",
                    "Reverse order",
                    "Last first: newest, largest, or Z to A.",
                    "descending ascending sorting",
                    Command.REVERSE_ORDER),
            new Row(
                    Page.FOLDER,
                    "Slideshow",
                    "Slideshow speed",
                    "Seconds each image shows in a slideshow. F5 starts it.",
                    "interval delay play seconds",
                    Command.SLIDESHOW_SPEED),
            new Row(
                    Page.KEYS,
                    "Keys",
                    "Keymap",
                    "Mirada's own keys, or Emacs's. Your own go on top of it: keys.<command id> in the settings"
                            + " file.",
                    "shortcuts bindings emacs cua",
                    Command.KEYMAP),
            new Row(
                    Page.KEYS,
                    "Keys",
                    "Command palette",
                    "The keys that open the palette. Add a key, then press it; pressing one already listed takes"
                            + " it off.",
                    "shortcuts bindings palette",
                    Command.PALETTE_KEYS),
            new Row(
                    Page.BACKUP,
                    "Configuration",
                    "Export and import",
                    "Export saves the settings, keys, bookmarks and notes in a zip, to keep or to take to another"
                            + " computer; Import puts back the parts you choose.",
                    "backup restore zip move copy settings folder",
                    Command.EXPORT_CONFIG));

    /** What a search shows: the rows that match, the pages they are on, and the page to open. */
    record Found(Set<Row> rows, Set<Page> pages, @Nullable Page best) {}

    private SettingsPages() {}

    /**
     * The rows {@code query} matches, scored by {@link SettingsSearch} over each row's entry (its
     * control's text from {@code visibleText}), and the page holding the best of them, the first in
     * order on a tie; null for a blank query, which matches every row and moves no page.
     */
    static Found search(String query, Map<Row, String> visibleText) {
        Set<Row> rows = new java.util.LinkedHashSet<>();
        Set<Page> pages = EnumSet.noneOf(Page.class);
        Page best = null;
        int bestScore = 0;
        for (Row row : ROWS) {
            int score = SettingsSearch.score(query, row.entry(visibleText.getOrDefault(row, "")));
            if (score == 0) continue;
            rows.add(row);
            pages.add(row.page());
            if (score > bestScore) {
                bestScore = score;
                best = row.page();
            }
        }
        return new Found(rows, pages, query.isBlank() ? null : best);
    }

    /** The cards of each page, in order, with their rows. */
    static Map<Page, Map<String, List<Row>>> cards() {
        Map<Page, Map<String, List<Row>>> out = new EnumMap<>(Page.class);
        for (Row row : ROWS) {
            out.computeIfAbsent(row.page(), p -> new java.util.LinkedHashMap<>())
                    .computeIfAbsent(row.card(), c -> new java.util.ArrayList<>())
                    .add(row);
        }
        return out;
    }
}
