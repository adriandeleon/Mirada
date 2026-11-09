package com.mirada.diagnostics;

import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * The session's status-bar messages, newest first, bounded (DIAG-01, MLOG-01, Pauta 4.0.1). In memory
 * only: the Debug Log is the record that outlives a session. No JavaFX here; {@code
 * ui.MessageLogView} is the view and {@code ui.StatusBar} the echo area.
 *
 * <p>A message's severity is said where it is raised (MLOG-05's direct channel): its words are
 * translated, so they cannot tell an error from a notice. FX thread only.
 */
public final class MessageLog {

    public enum Severity {
        INFO,
        WARNING,
        ERROR
    }

    public record Entry(LocalTime time, String text, Severity severity) {}

    public static final int MAX_ENTRIES = 200;

    static final long REPEAT_MILLIS = 2000;

    private final Deque<Entry> entries = new ArrayDeque<>();
    private final List<Runnable> listeners = new ArrayList<>();
    private int unreadErrors;
    private int dropped;

    /**
     * Records a message. A blank one is not. The same text again within {@link #REPEAT_MILLIS} moves
     * to the top as one entry, and an error counts as unread once.
     */
    public void add(String text, Severity severity, LocalTime now) {
        if (text.isBlank()) return;
        Entry newest = entries.peekFirst();
        boolean repeat = newest != null
                && newest.text().equals(text)
                && Duration.between(newest.time(), now).toMillis() < REPEAT_MILLIS;
        if (repeat) entries.removeFirst();
        entries.addFirst(new Entry(now, text, severity));
        while (entries.size() > MAX_ENTRIES) {
            entries.removeLast();
            dropped++;
        }
        if (severity == Severity.ERROR && !repeat) unreadErrors++;
        for (Runnable listener : List.copyOf(listeners)) listener.run();
    }

    /** Newest first. */
    public List<Entry> entries() {
        return List.copyOf(entries);
    }

    /** The newest entry, or null when there is none. */
    public @Nullable Entry newest() {
        return entries.peekFirst();
    }

    /** Errors since the log was last opened; kept even when the entry itself falls off the end (MLOG-07). */
    public int unreadErrors() {
        return unreadErrors;
    }

    /** How many of the oldest entries were cut to keep the log within {@link #MAX_ENTRIES}. */
    public int dropped() {
        return dropped;
    }

    /** The log was opened: its errors have been seen (MLOG-06). */
    public void markRead() {
        if (unreadErrors == 0) return;
        unreadErrors = 0;
        for (Runnable listener : List.copyOf(listeners)) listener.run();
    }

    public void clear() {
        entries.clear();
        unreadErrors = 0;
        dropped = 0;
        for (Runnable listener : List.copyOf(listeners)) listener.run();
    }

    /** Runs after every change: a message added, the errors read, the log cleared. */
    public void addListener(Runnable listener) {
        listeners.add(listener);
    }
}
