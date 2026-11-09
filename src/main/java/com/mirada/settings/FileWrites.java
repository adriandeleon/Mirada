package com.mirada.settings;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Where the settings and the bookmarks are read and written: off the FX thread, on one thread of their
 * own, in the order asked (PERF-01). A {@link #latest} write that a newer one of the same file has
 * superseded before it ran is skipped, as the newer one holds all it would have written; an {@link
 * #execute}d task always runs. {@link #flush} waits for what is queued, at quit and in tests.
 *
 * <p>{@link #direct()} runs everything on the caller's thread, as tests and {@code --report} want.
 */
public final class FileWrites implements Executor {

    private static final class Background {
        static final FileWrites INSTANCE = new FileWrites(Executors.newSingleThreadExecutor(
                Thread.ofPlatform().daemon().name("config-files").factory()));
    }

    private static final FileWrites DIRECT = new FileWrites(Runnable::run);

    private final Executor thread;
    private final Map<Object, AtomicLong> generations = new ConcurrentHashMap<>();

    FileWrites(Executor thread) {
        this.thread = thread;
    }

    /** The application's: one daemon thread, started with the first write. */
    public static FileWrites background() {
        return Background.INSTANCE;
    }

    /** Everything on the calling thread, at once. */
    public static FileWrites direct() {
        return DIRECT;
    }

    /** Whether this runs on the caller's thread. */
    public boolean isDirect() {
        return this == DIRECT;
    }

    /** Runs {@code task} on the files' thread, after everything asked before it. */
    @Override
    public void execute(Runnable task) {
        thread.execute(task);
    }

    /**
     * Writes {@code key}'s file with {@code write}, unless a later call for the same key comes before
     * this one runs: that one writes everything this one would have.
     */
    public void latest(Object key, Runnable write) {
        AtomicLong generation = generations.computeIfAbsent(key, k -> new AtomicLong());
        long mine = generation.incrementAndGet();
        thread.execute(() -> {
            if (superseded(mine, generation.get())) return;
            write.run();
        });
    }

    /** Whether the write numbered {@code mine} has been overtaken by {@code latest}. Pure. */
    static boolean superseded(long mine, long latest) {
        return mine != latest;
    }

    /**
     * Waits, at most {@code timeout}, for every write asked so far: at quit, as the thread is a daemon
     * and would be stopped mid-write. Whether they all finished.
     */
    public boolean flush(Duration timeout) {
        if (!(thread instanceof ExecutorService service)) return true;
        try {
            service.submit(() -> {}).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException | TimeoutException e) {
            return false;
        }
    }
}
