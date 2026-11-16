package com.mirada.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

/**
 * The Debug Log find bar's matches in one text (the app template's {@code LogSearch}, verbatim but for the package): every place the query occurs, overlapping ones included,
 * so a longer query is answered from them (PERF-08). A query that grows by a letter is only ever found
 * where the shorter one was, so {@link #narrow} checks those places, never the whole log again; any
 * other change is a {@link #of full search}, which the bar runs off the FX thread (PERF-01). Pure.
 *
 * @param text the text searched
 * @param version which text it was, as its owner counts changes: a narrowing is only for the same one
 * @param occurrences each {@code [start, end)} where the query occurs, in order
 * @param capped whether the search stopped at {@link #MAX_OCCURRENCES}, so a narrowing cannot be trusted
 */
record LogSearch(String text, long version, String needle, boolean matchCase, List<int[]> occurrences, boolean capped) {

    /** More occurrences than this are not looked for: the count says "{@value}+". */
    static final int MAX_OCCURRENCES = 10_000;

    /**
     * Searches {@code text} for {@code needle}. Stops early, with what it has, when its thread is
     * interrupted: a superseded search's result is dropped anyway ({@link LatestOnly}).
     */
    static LogSearch of(String text, long version, String needle, boolean matchCase) {
        List<int[]> found = new ArrayList<>();
        boolean capped = false;
        if (!needle.isEmpty() && !text.isEmpty()) {
            Matcher m = pattern(needle, matchCase).matcher(text);
            int from = 0;
            while (from <= text.length() && m.find(from)) {
                if (found.size() == MAX_OCCURRENCES) {
                    capped = true;
                    break;
                }
                if (Thread.currentThread().isInterrupted()) break;
                found.add(new int[] {m.start(), m.end()});
                from = m.start() + 1;
            }
        }
        return new LogSearch(text, version, needle, matchCase, List.copyOf(found), capped);
    }

    /**
     * The matches for a longer query, from this search's own occurrences: O(occurrences), not O(text).
     * Null when that cannot answer it exactly: another text, another case setting, a query that does not
     * extend this one, or a search that stopped at the cap.
     */
    @Nullable
    LogSearch narrow(long version, String needle, boolean matchCase) {
        if (version != this.version || matchCase != this.matchCase || capped) return null;
        if (this.needle.isEmpty() || !needle.startsWith(this.needle)) return null;
        if (needle.equals(this.needle)) return this;
        Matcher m = pattern(needle, matchCase).matcher(text);
        List<int[]> kept = new ArrayList<>();
        for (int[] occurrence : occurrences) {
            m.region(occurrence[0], text.length());
            if (m.lookingAt()) kept.add(new int[] {m.start(), m.end()});
        }
        return new LogSearch(text, version, needle, matchCase, List.copyOf(kept), false);
    }

    /** The matches the bar steps through: the occurrences that do not overlap, as a find from the start sees them. */
    List<int[]> matches() {
        List<int[]> out = new ArrayList<>();
        int end = -1;
        for (int[] occurrence : occurrences) {
            if (occurrence[0] < end) continue;
            out.add(occurrence);
            end = Math.max(occurrence[1], occurrence[0] + 1);
        }
        return out;
    }

    /** A quoted pattern, not a lower-cased indexOf: case folding can change a string's length. */
    private static Pattern pattern(String needle, boolean matchCase) {
        int flags = matchCase ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
        return Pattern.compile(Pattern.quote(needle), flags);
    }
}
