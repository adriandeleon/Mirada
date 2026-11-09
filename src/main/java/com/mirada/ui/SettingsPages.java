package com.mirada.ui;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import static com.mirada.i18n.Messages.tr;

/**
 * What the Settings window holds, without the toolkit: its pages in sidebar order, each row's page,
 * card, words and command (SET-02, SET-06, Pauta 4.0.1), and what a search shows (SET-03). The
 * window ({@link SettingsWindow}) builds a control for each row; the rules are here, so they test
 * without it.
 */
final class SettingsPages {

    /** The pages, in sidebar order, each with its title's key and its icon. */
    enum Page {
        GENERAL("settings.page.general", Icons.SLIDERS),
        VIEWER("settings.page.viewer", Icons.PICTURE),
        THUMBNAILS("settings.page.thumbnails", Icons.STRIP),
        FOLDER("settings.page.folder", Icons.SORT),
        KEYS("settings.page.keys", Icons.KEYBOARD),
        BACKUP("settings.page.backup", Icons.DOWNLOAD);

        private final String titleKey;
        private final String icon;

        Page(String titleKey, String icon) {
            this.titleKey = titleKey;
            this.icon = icon;
        }

        /** The page's name, in the interface's language. */
        String title() {
            return tr(titleKey);
        }

        /** The icon's SVG path ({@link Icons#of}). */
        String icon() {
            return icon;
        }
    }

    /**
     * One row: where it stands, the keys of what it says (its card's heading, its title and its
     * description, translated by {@link #card()}, {@link #title()} and {@link #description()}), the
     * English words it is also found by, and the palette command that changes the same setting
     * (SET-06), or opens the same action.
     */
    record Row(Page page, String cardKey, String titleKey, String descriptionKey, String keywords, Command command) {

        /** The heading of the card the row is on, in the interface's language. */
        String card() {
            return tr(cardKey);
        }

        String title() {
            return tr(titleKey);
        }

        String description() {
            return tr(descriptionKey);
        }

        /** What the search finds this row by, with the text its control shows: the translated words. */
        SettingsSearch.Entry entry(String visibleText) {
            return new SettingsSearch.Entry(title(), description(), visibleText, page.title() + " " + card(), keywords);
        }
    }

    /** Every row, by page and card, in the order the window shows them. */
    static final List<Row> ROWS = List.of(
            new Row(
                    Page.GENERAL,
                    "settings.card.appearance",
                    "settings.theme.title",
                    "settings.theme.description",
                    "dark light system mode colour color look",
                    Command.THEME),
            new Row(
                    Page.GENERAL,
                    "settings.card.appearance",
                    "settings.language.title",
                    "settings.language.description",
                    "locale translation english español português français italiano deutsch",
                    Command.LANGUAGE),
            new Row(
                    Page.GENERAL,
                    "settings.card.atStart",
                    "settings.thumbnailsAtStart.title",
                    "settings.thumbnailsAtStart.description",
                    "startup launch filmstrip strip",
                    Command.THUMBNAILS_AT_START),
            new Row(
                    Page.GENERAL,
                    "settings.card.atStart",
                    "settings.propertiesAtStart.title",
                    "settings.propertiesAtStart.description",
                    "startup launch info details exif sidebar",
                    Command.PROPERTIES_AT_START),
            new Row(
                    Page.GENERAL,
                    "settings.card.updates",
                    "settings.checkForUpdates.title",
                    "settings.checkForUpdates.description",
                    "version release network automatic",
                    Command.CHECK_DAILY),
            new Row(
                    Page.VIEWER,
                    "settings.card.viewer",
                    "settings.statusBar.title",
                    "settings.statusBar.description",
                    "messages echo footer",
                    Command.STATUS_BAR),
            new Row(
                    Page.VIEWER,
                    "settings.card.viewer",
                    "settings.hideControlsAfter.title",
                    "settings.hideControlsAfter.description",
                    "delay fade timeout overlay buttons seconds",
                    Command.HIDE_CONTROLS_AFTER),
            new Row(
                    Page.VIEWER,
                    "settings.card.viewer",
                    "settings.keepZoom.title",
                    "settings.keepZoom.description",
                    "lock fit compare",
                    Command.KEEP_ZOOM),
            new Row(
                    Page.THUMBNAILS,
                    "settings.card.thumbnailStrip",
                    "settings.thumbnailsAtSide.title",
                    "settings.thumbnailsAtSide.description",
                    "vertical column filmstrip layout left",
                    Command.THUMBNAILS_AT_SIDE),
            new Row(
                    Page.THUMBNAILS,
                    "settings.card.thumbnailStrip",
                    "settings.thumbnailCaptions.title",
                    "settings.thumbnailCaptions.description",
                    "captions labels filmstrip",
                    Command.THUMBNAIL_CAPTIONS),
            new Row(
                    Page.THUMBNAILS,
                    "settings.card.thumbnailStrip",
                    "settings.thumbnailNames.title",
                    "settings.thumbnailNames.description",
                    "list text filmstrip",
                    Command.THUMBNAIL_NAMES),
            new Row(
                    Page.FOLDER,
                    "settings.card.order",
                    "settings.sortBy.title",
                    "settings.sortBy.description",
                    "name date modified size sorting",
                    Command.SORT_BY_NAME),
            new Row(
                    Page.FOLDER,
                    "settings.card.order",
                    "settings.reverseOrder.title",
                    "settings.reverseOrder.description",
                    "descending ascending sorting",
                    Command.REVERSE_ORDER),
            new Row(
                    Page.FOLDER,
                    "settings.card.slideshow",
                    "settings.slideshowSpeed.title",
                    "settings.slideshowSpeed.description",
                    "interval delay play seconds",
                    Command.SLIDESHOW_SPEED),
            new Row(
                    Page.KEYS,
                    "settings.card.keys",
                    "settings.keymap.title",
                    "settings.keymap.description",
                    "shortcuts bindings emacs cua",
                    Command.KEYMAP),
            new Row(
                    Page.KEYS,
                    "settings.card.keys",
                    "settings.paletteKeys.title",
                    "settings.paletteKeys.description",
                    "shortcuts bindings palette",
                    Command.PALETTE_KEYS),
            new Row(
                    Page.BACKUP,
                    "settings.card.configuration",
                    "settings.exportImport.title",
                    "settings.exportImport.description",
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
