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
}
