package com.mirada.settings;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The settings and the bookmarks are written off the FX thread (PERF-01): in order, the latest of a burst
 * only, and waited for at quit.
 */
class FileWritesTest {

    /** An executor that runs nothing until told to: the files' thread, held. */
    static final class Held implements Executor {
        final Deque<Runnable> queue = new ArrayDeque<>();

        @Override
        public void execute(Runnable task) {
            queue.add(task);
        }

        void runAll() {
            while (!queue.isEmpty()) queue.poll().run();
        }
    }

    @Test
    void aWriteOvertakenByANewerOneOfTheSameFileIsSkipped() {
        Held thread = new Held();
        FileWrites writes = new FileWrites(thread);
        List<String> written = new ArrayList<>();
        writes.latest("settings", () -> written.add("first"));
        writes.latest("settings", () -> written.add("second"));
        writes.latest("bookmarks", () -> written.add("other file"));
        assertTrue(written.isEmpty(), "nothing is written on the caller's thread");
        thread.runAll();
        assertEquals(List.of("second", "other file"), written);
    }

    @Test
    void whatIsExecutedAlwaysRunsInOrder() {
        Held thread = new Held();
        FileWrites writes = new FileWrites(thread);
        List<Integer> ran = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            int n = i;
            writes.execute(() -> ran.add(n));
        }
        thread.runAll();
        assertEquals(List.of(0, 1, 2), ran);
    }

    @Test
    void supersededIsAnyEarlierNumber() {
        assertFalse(FileWrites.superseded(3, 3));
        assertTrue(FileWrites.superseded(2, 3));
    }

    @Test
    void directRunsAtOnceAndFlushesAtOnce() {
        List<String> ran = new ArrayList<>();
        FileWrites.direct().latest("x", () -> ran.add("written"));
        assertEquals(List.of("written"), ran);
        assertTrue(FileWrites.direct().isDirect());
        assertFalse(FileWrites.background().isDirect());
        assertTrue(FileWrites.direct().flush(Duration.ZERO));
    }

    @Test
    void flushWaitsForWhatIsQueuedAndGivesUpAfterItsTime() throws Exception {
        var thread = Executors.newSingleThreadExecutor();
        try {
            FileWrites writes = new FileWrites(thread);
            CountDownLatch release = new CountDownLatch(1);
            writes.execute(() -> {
                try {
                    release.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            assertFalse(writes.flush(Duration.ofMillis(20)), "still writing");
            release.countDown();
            assertTrue(writes.flush(Duration.ofSeconds(10)));
        } finally {
            thread.shutdownNow();
        }
    }

    @Test
    void settingsWrittenInTheBackgroundAreWrittenOnceTheThreadRuns(@TempDir Path dir) {
        Held thread = new Held();
        Path file = dir.resolve("settings.properties");
        Settings settings = new Settings(file);
        settings.writeOn(new FileWrites(thread));
        settings.setKeepZoom(true);
        settings.setSlideshowSeconds(9);
        assertFalse(Files.exists(file), "not on the caller's thread");
        assertEquals(2, thread.queue.size());
        thread.runAll();
        Settings again = new Settings(file);
        again.load();
        assertTrue(again.keepZoom());
        assertEquals(9, again.slideshowSeconds(), "the latest write, which holds both");
    }
}
