package com.mirada.diagnostics;

import java.time.LocalTime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Message Log's model: newest first, bounded, cleared; repeats and unread errors (DIAG-01, MLOG). */
class MessageLogTest {

    private static final LocalTime NOON = LocalTime.NOON;

    @Test
    void messagesAreKeptNewestFirst() {
        MessageLog log = new MessageLog();
        log.add("Opened a.jpg", MessageLog.Severity.INFO, NOON);
        log.add("Copied a.jpg", MessageLog.Severity.INFO, NOON.plusSeconds(5));
        assertEquals("Copied a.jpg", log.entries().getFirst().text());
        assertEquals("Opened a.jpg", log.entries().get(1).text());
        assertEquals("Copied a.jpg", log.newest().text());
    }

    @Test
    void theOldestIsEvictedPastTheCapAndCounted() {
        MessageLog log = new MessageLog();
        for (int i = 0; i < MessageLog.MAX_ENTRIES + 5; i++)
            log.add("message " + i, MessageLog.Severity.INFO, NOON.plusSeconds(i * 3L));
        assertEquals(MessageLog.MAX_ENTRIES, log.entries().size());
        assertEquals(5, log.dropped());
        assertEquals("message 5", log.entries().getLast().text());
    }

    @Test
    void clearEmptiesTheLogAndItsCounts() {
        MessageLog log = new MessageLog();
        log.add("Couldn't copy a.jpg", MessageLog.Severity.ERROR, NOON);
        log.clear();
        assertTrue(log.entries().isEmpty());
        assertNull(log.newest());
        assertEquals(0, log.unreadErrors());
        assertEquals(0, log.dropped());
    }

    @Test
    void blankMessagesAreNotKept() {
        MessageLog log = new MessageLog();
        log.add("  ", MessageLog.Severity.INFO, NOON);
        assertTrue(log.entries().isEmpty());
    }

    @Test
    void aRepeatWithinTwoSecondsIsOneEntryAndOneUnreadError() {
        MessageLog log = new MessageLog();
        log.add("Couldn't copy a.jpg", MessageLog.Severity.ERROR, NOON);
        log.add("Copied b.jpg", MessageLog.Severity.INFO, NOON.plusSeconds(1));
        log.add("Copied b.jpg", MessageLog.Severity.INFO, NOON.plusSeconds(2));
        assertEquals(2, log.entries().size());
        log.add("Copied b.jpg", MessageLog.Severity.INFO, NOON.plusSeconds(10));
        assertEquals(3, log.entries().size());
        log.add("Couldn't copy c.jpg", MessageLog.Severity.ERROR, NOON.plusSeconds(11));
        log.add("Couldn't copy c.jpg", MessageLog.Severity.ERROR, NOON.plusSeconds(12));
        assertEquals(2, log.unreadErrors());
    }

    @Test
    void openingTheLogReadsItsErrors() {
        MessageLog log = new MessageLog();
        log.add("Couldn't copy a.jpg", MessageLog.Severity.ERROR, NOON);
        assertEquals(1, log.unreadErrors());
        log.markRead();
        assertEquals(0, log.unreadErrors());
        assertEquals(1, log.entries().size());
    }

    @Test
    void anEvictedErrorStillCounts() {
        MessageLog log = new MessageLog();
        log.add("Couldn't copy a.jpg", MessageLog.Severity.ERROR, NOON);
        for (int i = 0; i < MessageLog.MAX_ENTRIES; i++)
            log.add("message " + i, MessageLog.Severity.INFO, NOON.plusSeconds(3L + i * 3L));
        assertTrue(log.entries().stream().noneMatch(e -> e.severity() == MessageLog.Severity.ERROR));
        assertEquals(1, log.unreadErrors());
    }

    @Test
    void listenersHearEveryChange() {
        MessageLog log = new MessageLog();
        int[] heard = {0};
        log.addListener(() -> heard[0]++);
        log.add("Copied a.jpg", MessageLog.Severity.INFO, NOON);
        log.add("Couldn't copy b.jpg", MessageLog.Severity.ERROR, NOON);
        log.markRead();
        log.clear();
        assertEquals(4, heard[0]);
    }
}
