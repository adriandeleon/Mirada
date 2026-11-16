package com.mirada.ui;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The find bar's matches: a growing query is narrowed from the last search, exactly as a full one finds (PERF-08). */
class LogSearchTest {

    private static List<String> spans(LogSearch search) {
        return search.matches().stream().map(m -> m[0] + "-" + m[1]).toList();
    }

    @Test
    void aGrowingQueryNarrowsToWhatAFullSearchFinds() {
        String text = "aaab Alpha one\nbeta\nAlpha two aaaa alphabet";
        for (boolean matchCase : new boolean[] {false, true}) {
            for (String[] pair :
                    new String[][] {{"a", "aa"}, {"aa", "aab"}, {"aa", "aaa"}, {"al", "alpha"}, {"Al", "Alpha t"}}) {
                LogSearch shorter = LogSearch.of(text, 1, pair[0], matchCase);
                LogSearch narrowed = shorter.narrow(1, pair[1], matchCase);
                assertNotNull(narrowed, pair[1]);
                assertEquals(
                        spans(LogSearch.of(text, 1, pair[1], matchCase)), spans(narrowed), pair[0] + " → " + pair[1]);
            }
        }
        // "aa" in "aaab" is found once, at 0, but "aab" is at 1: the overlapping occurrences keep it.
        assertEquals(List.of("1-4"), spans(LogSearch.of("aaab", 1, "aa", true).narrow(1, "aab", true)));
        assertEquals(List.of("0-2", "2-4"), spans(LogSearch.of("aaaa", 1, "aa", true)));
    }

    @Test
    void anythingButAGrowingQueryOnTheSameTextSearchesAgain() {
        LogSearch search = LogSearch.of("alpha beta", 1, "al", false);
        assertNull(search.narrow(2, "alp", false), "the text changed");
        assertNull(search.narrow(1, "alp", true), "the case setting changed");
        assertNull(search.narrow(1, "a", false), "the query shrank");
        assertNull(search.narrow(1, "be", false), "another query");
        assertNull(LogSearch.of("x", 1, "", false).narrow(1, "x", false), "nothing was searched");
        assertSame(search, search.narrow(1, "al", false));
    }

    @Test
    void aSearchStopsAtTheCapAndThenIsNotNarrowed() {
        LogSearch search = LogSearch.of("a".repeat(LogSearch.MAX_OCCURRENCES + 5), 1, "a", true);
        assertTrue(search.capped());
        assertEquals(LogSearch.MAX_OCCURRENCES, search.occurrences().size());
        assertNull(search.narrow(1, "aa", true));
    }

    @Test
    void anInterruptedSearchStopsEarly() {
        Thread.currentThread().interrupt();
        try {
            assertTrue(LogSearch.of("a".repeat(100), 1, "a", true).occurrences().isEmpty());
        } finally {
            Thread.interrupted();
        }
    }
}
