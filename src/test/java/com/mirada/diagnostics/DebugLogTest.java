package com.mirada.diagnostics;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Debug Log: bounded in memory, mirrored to the session file, the last one kept (DIAG-02, DIAG-04). */
class DebugLogTest {

    @TempDir
    Path folder;

    @BeforeEach
    void empty() {
        DebugLog.detachFile();
        DebugLog.clear();
    }

    @AfterEach
    void release() {
        DebugLog.detachFile();
        DebugLog.clear();
        DebugLog.onUncaught(null);
        DebugLog.writeOn(Runnable::run);
    }

    @Test
    void aLineIsKeptAtOnceAndWrittenToTheFileWhereTheApplicationSays() throws IOException {
        java.util.List<Runnable> queued = new java.util.ArrayList<>();
        DebugLog.attachFile(folder);
        DebugLog.writeOn(queued::add); // the files' thread, held
        DebugLog.append("logged on the FX thread");
        assertTrue(DebugLog.text().contains("logged on the FX thread"), "in memory at once");
        Path file = folder.resolve(DebugLog.FILE);
        assertFalse(
                Files.readString(file, StandardCharsets.UTF_8).contains("logged on the FX thread"), "not written here");
        queued.forEach(Runnable::run);
        assertTrue(Files.readString(file, StandardCharsets.UTF_8).contains("logged on the FX thread"));
    }

    @Test
    void theBufferIsBounded() {
        for (int i = 0; i < DebugLog.MAX_ENTRIES + 10; i++) DebugLog.append("line " + i);
        assertEquals(10, DebugLog.dropped());
        String text = DebugLog.text();
        assertTrue(
                text.startsWith("… 10 earlier lines are not kept here"),
                text.lines().findFirst().orElse(""));
        assertFalse(text.contains("line 9" + System.lineSeparator()));
        assertTrue(text.endsWith("line " + (DebugLog.MAX_ENTRIES + 9)));
    }

    @Test
    void javaUtilLoggingIsCaptured() {
        DebugLog.captureLogging();
        Logger.getLogger("com.mirada.test.Captured").warning("a warning from the test");
        assertTrue(DebugLog.text().contains("WARNING Captured — a warning from the test"), DebugLog.text());
    }

    @Test
    void anUncaughtExceptionIsLoggedWithItsStackAndPassedOn() {
        List<Throwable> heard = new ArrayList<>();
        DebugLog.onUncaught((thread, error) -> heard.add(error));
        IllegalStateException error = new IllegalStateException("broken on purpose");
        DebugLog.uncaught(Thread.currentThread(), error);
        String text = DebugLog.text();
        assertTrue(text.contains("UNCAUGHT on " + Thread.currentThread().getName()), text);
        assertTrue(text.contains("java.lang.IllegalStateException: broken on purpose"), text);
        assertTrue(text.contains("at com.mirada.diagnostics.DebugLogTest"), text);
        assertSame(error, heard.getFirst());
    }

    @Test
    void installPutsTheHandlerInPlace() {
        Thread.UncaughtExceptionHandler before = Thread.getDefaultUncaughtExceptionHandler();
        try {
            DebugLog.install();
            assertNotNull(Thread.getDefaultUncaughtExceptionHandler());
            List<Throwable> heard = new ArrayList<>();
            DebugLog.onUncaught((thread, error) -> heard.add(error));
            Thread.getDefaultUncaughtExceptionHandler()
                    .uncaughtException(Thread.currentThread(), new RuntimeException("x"));
            assertEquals(1, heard.size());
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(before);
        }
    }

    @Test
    void theSessionFileIsWrittenWithWhatCameBefore() throws IOException {
        DebugLog.append("before the file");
        DebugLog.attachFile(folder);
        DebugLog.append("after the file");
        Path file = folder.resolve(DebugLog.FILE);
        assertEquals(file, DebugLog.file());
        String written = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(written.contains("before the file"), written);
        assertTrue(written.contains("after the file"), written);
    }

    @Test
    void theLastSessionIsKeptAsThePreviousFile() throws IOException {
        Files.writeString(folder.resolve(DebugLog.FILE), "last session's crash\n", StandardCharsets.UTF_8);
        DebugLog.attachFile(folder);
        DebugLog.append("this session");
        assertEquals(
                "last session's crash\n",
                Files.readString(folder.resolve(DebugLog.PREVIOUS_FILE), StandardCharsets.UTF_8));
        String now = Files.readString(folder.resolve(DebugLog.FILE), StandardCharsets.UTF_8);
        assertFalse(now.contains("last session's crash"));
        assertTrue(now.contains("this session"));
    }

    @Test
    void anEmptyLastSessionLeavesThePreviousFileAlone() throws IOException {
        Files.writeString(folder.resolve(DebugLog.PREVIOUS_FILE), "older\n", StandardCharsets.UTF_8);
        DebugLog.attachFile(folder);
        assertEquals("older\n", Files.readString(folder.resolve(DebugLog.PREVIOUS_FILE), StandardCharsets.UTF_8));
    }

    @Test
    void clearingEmptiesTheBufferButNotTheFile() throws IOException {
        DebugLog.attachFile(folder);
        DebugLog.append("kept on disk");
        DebugLog.clear();
        assertEquals("", DebugLog.text());
        assertTrue(Files.readString(folder.resolve(DebugLog.FILE), StandardCharsets.UTF_8)
                .contains("kept on disk"));
    }

    @Test
    void pastItsCapTheSessionFileStartsOverWithANoteAndTheNewestLines() throws IOException {
        DebugLog.attachFile(folder);
        // Lines of 5 KB: the in-memory cap (2000) holds 10 MB of them, past the file's 8 MB.
        String filler = "x".repeat(5_000);
        for (int i = 0; i < DebugLog.MAX_ENTRIES; i++) DebugLog.append(i + " " + filler);
        Path file = folder.resolve(DebugLog.FILE);
        long size = Files.size(file);
        assertTrue(size <= DebugLog.MAX_FILE_BYTES, "held under the cap: " + size);
        String written = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(written.startsWith("… earlier lines were cut") || written.contains("earlier lines were cut"));
        assertTrue(written.contains((DebugLog.MAX_ENTRIES - 1) + " " + filler), "the newest is kept");
        assertFalse(written.contains(System.lineSeparator() + "0 " + filler), "the oldest was dropped");
    }

    @Test
    void aSessionFileHeldByAnotherMiradaLeavesItAloneAndWritesItsOwn() throws IOException {
        Path theirs = folder.resolve(DebugLog.FILE);
        Files.writeString(theirs, "the other window's session\n", StandardCharsets.UTF_8);
        try (java.nio.channels.FileChannel other =
                        java.nio.channels.FileChannel.open(theirs, java.nio.file.StandardOpenOption.WRITE);
                java.nio.channels.FileLock held = other.lock(DebugLog.LOCK_POSITION, 1, false)) {
            assertTrue(held.isValid());
            DebugLog.attachFile(folder);
            DebugLog.append("this one's");
            Path mine = DebugLog.file();
            assertNotNull(mine);
            assertEquals(
                    "mirada-session-" + ProcessHandle.current().pid() + ".log",
                    mine.getFileName().toString());
            assertTrue(Files.readString(mine, StandardCharsets.UTF_8).contains("this one's"));
            assertEquals("the other window's session\n", Files.readString(theirs, StandardCharsets.UTF_8));
        }
    }
}
