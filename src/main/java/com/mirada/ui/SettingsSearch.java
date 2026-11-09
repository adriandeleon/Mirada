package com.mirada.ui;

import java.util.Locale;

/**
 * Settings search scoring (SET-03). A row is found by its title, description, visible text (its
 * controls' labels), card heading, page name and English keywords. Title hits rank above description
 * and visible-text hits, which rank above heading, page and keyword hits; whole words above word starts
 * above anywhere. Every word of the query has to match somewhere. Pure, so the ranking is unit-tested.
 *
 * <p>The javafx-app-template's, verbatim but for the package and visibility: a ranking change belongs
 * in both.
 */
final class SettingsSearch {

    /** What a row can be found by. */
    record Entry(String title, String description, String visibleText, String context, String keywords) {

        Entry(String title, String description, String context, String keywords) {
            this(title, description, "", context, keywords);
        }
    }

    private SettingsSearch() {}

    /** The row's score for {@code query}; 0 means no match. A blank query matches everything with 1. */
    static int score(String query, Entry entry) {
        String[] words = query.toLowerCase(Locale.ROOT).trim().split("\\s+");
        if (words.length == 0 || words[0].isEmpty()) return 1;
        int total = 0;
        for (String word : words) {
            int best = Math.max(
                    field(word, entry.title()) * 4,
                    Math.max(
                            Math.max(field(word, entry.description()), field(word, entry.visibleText())) * 2,
                            field(word, entry.context() + " " + entry.keywords())));
            if (best == 0) return 0;
            total += best;
        }
        // A query that is a row's whole title names that row: it wins any tie.
        return entry.title().equalsIgnoreCase(query.trim()) ? total + 100 : total;
    }

    /** 3 for a whole word, 2 for a word start, 1 anywhere, 0 for no match. */
    static int field(String word, String text) {
        String t = text.toLowerCase(Locale.ROOT);
        int best = 0;
        for (int at = t.indexOf(word); at >= 0; at = t.indexOf(word, at + 1)) {
            boolean start = at == 0 || !Character.isLetterOrDigit(t.charAt(at - 1));
            int end = at + word.length();
            boolean whole = start && (end == t.length() || !Character.isLetterOrDigit(t.charAt(end)));
            best = Math.max(best, whole ? 3 : start ? 2 : 1);
        }
        return best;
    }
}
