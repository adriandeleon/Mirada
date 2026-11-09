package com.mirada.diagnostics;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.jspecify.annotations.Nullable;

/**
 * Every {@code java.util.logging} record and uncaught exception, kept where a user can reach it: a
 * packaged app has no console (DIAG-02, Pauta 4.0.1). The template's, which is Editora's.
 *
 * <p>A bounded buffer in memory backs the Debug Log window ("it is misbehaving now"); a session file
 * in the config folder covers "it died". The previous session's file is kept as {@link
 * #PREVIOUS_FILE}, so a crash can still be read on the next launch. The file is locked for the life
 * of the process: a second Mirada on the same folder writes to a sibling named by its pid rather than
 * truncating the first one's log.
 *
 * <p>Thread-safe: records arrive from any thread.
 */
public final class DebugLog {

    public static final String FILE = "mirada-session.log";
    public static final String PREVIOUS_FILE = "mirada-session.prev.log";

    static final int MAX_ENTRIES = 2000;

    /**
     * The session file never grows past this: when the next line would cross it, the file is
     * rewritten with a note of the cut and the newest lines, so a runaway logger cannot fill the disk
     * and the lines just before a crash are still there.
     */
    static final long MAX_FILE_BYTES = 8L * 1024 * 1024;

    /**
     * The lock is one byte far past the end of the file, not the whole file: another Mirada still sees
     * it, but on Windows a whole-file lock also stops anyone (an editor, {@code mirada --report}) from
     * reading the log while the app runs.
     */
    static final long LOCK_POSITION = Long.MAX_VALUE - 1;

    /** Made at the first line logged, not as the class loads in {@code main}: java.time costs milliseconds. */
    private static final class Time {
        static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    }

    private static final Deque<String> ENTRIES = new ArrayDeque<>();

    private static @Nullable FileChannel channel;
    private static @Nullable FileLock lock;
    private static @Nullable Path file;
    private static @Nullable Handler handler;
    private static int dropped;
    private static long fileBytes;

    private static volatile @Nullable BiConsumer<Thread, Throwable> onUncaught;

    /** Writes the session file: the caller's thread until {@link #writeOn}, as tests want. */
    private static volatile Executor writes = Runnable::run;

    /**
     * Writes each line to the session file on {@code executor} from now on, in order: a line logged on
     * the FX thread is then never a file write there (PERF-01). The line is in memory at once.
     */
    public static void writeOn(Executor executor) {
        writes = executor;
    }

    private DebugLog() {}

    /**
     * Installs the uncaught exception handler, then captures every record the loggers emit. The first
     * thing {@code App.main} does, so a failure during start-up is caught too (DIAG-04).
     *
     * <p>The handler is in place at once; the logging hook-up follows on a thread of its own, as
     * starting {@code java.util.logging} took 4 to 6 ms of a cold start on the main thread. Nothing
     * Mirada does logs in those milliseconds, and a record that did would still reach the console.
     */
    public static void install() {
        Thread.setDefaultUncaughtExceptionHandler(DebugLog::uncaught);
        Thread capture = new Thread(DebugLog::captureLogging, "debug-log");
        capture.setDaemon(true);
        capture.start();
    }

    /** Adds the handler to the root logger, once. */
    static synchronized void captureLogging() {
        if (handler != null) return;
        Handler h = new Handler() {
            @Override
            public void publish(LogRecord record) {
                // Everything the (already level-gated) loggers emit, FINE included.
                append(format(record));
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        };
        h.setLevel(Level.ALL);
        Logger.getLogger("").addHandler(h);
        handler = h;
    }

    /**
     * An exception no code caught, on any thread: into the log with its stack, then to whoever
     * listens (the window, which says it in the status bar). Never a dialog.
     */
    static void uncaught(Thread thread, Throwable error) {
        append(stamp("UNCAUGHT on " + thread.getName() + ": " + stackTrace(error)));
        BiConsumer<Thread, Throwable> listener = onUncaught;
        if (listener != null) listener.accept(thread, error);
    }

    /** Who hears about uncaught exceptions after the log has them, or null for nobody. */
    public static void onUncaught(@Nullable BiConsumer<Thread, Throwable> listener) {
        onUncaught = listener;
    }

    /**
     * Mirrors the log to {@link #FILE} in {@code configDir}, keeping the last session's as {@link
     * #PREVIOUS_FILE}; whatever was logged before this is written first. A folder that cannot be
     * written leaves the log in memory only: no reason to fail a launch.
     */
    public static synchronized void attachFile(Path configDir) {
        detachFile();
        try {
            Files.createDirectories(configDir);
            if (!claim(configDir.resolve(FILE), true)) {
                claim(
                        configDir.resolve(
                                "mirada-session-" + ProcessHandle.current().pid() + ".log"),
                        false);
            }
        } catch (IOException e) {
            detachFile();
        }
        for (String line : ENTRIES) write(line);
    }

    private static boolean claim(Path candidate, boolean rotate) throws IOException {
        FileChannel opened = FileChannel.open(
                candidate, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.READ);
        FileLock held;
        try {
            held = opened.tryLock(LOCK_POSITION, 1, false);
        } catch (OverlappingFileLockException e) {
            held = null;
        }
        if (held == null) {
            opened.close();
            return false;
        }
        Path parent = candidate.getParent();
        if (rotate && opened.size() > 0 && parent != null) {
            // Through the channel that holds the lock: Windows refuses any other reader of a locked
            // file. The stream is not closed, which would close the channel and release the lock.
            opened.position(0);
            Files.copy(
                    Channels.newInputStream(opened),
                    parent.resolve(PREVIOUS_FILE),
                    StandardCopyOption.REPLACE_EXISTING);
        }
        opened.truncate(0);
        fileBytes = 0;
        channel = opened;
        lock = held;
        file = candidate;
        return true;
    }

    /** Releases the session file; the log goes on in memory. */
    public static synchronized void detachFile() {
        try {
            FileLock held = lock;
            if (held != null) held.release();
            FileChannel open = channel;
            if (open != null) open.close();
        } catch (IOException e) {
            // Nothing left to do with a channel that will not close.
        }
        lock = null;
        channel = null;
        file = null;
    }

    /** The session file, or null when there is none. */
    public static synchronized @Nullable Path file() {
        return file;
    }

    static synchronized void append(String line) {
        ENTRIES.addLast(line);
        while (ENTRIES.size() > MAX_ENTRIES) {
            ENTRIES.removeFirst();
            dropped++;
        }
        Executor writer = writes;
        writer.execute(() -> {
            synchronized (DebugLog.class) {
                write(line);
            }
        });
    }

    private static void write(String line) {
        FileChannel out = channel;
        if (out == null) return;
        byte[] encoded = (line + System.lineSeparator()).getBytes(StandardCharsets.UTF_8);
        try {
            if (fileBytes + encoded.length > MAX_FILE_BYTES) {
                rewriteWithNewest(out);
                return; // the newest lines, this one included, are in the file again
            }
            fileBytes += encoded.length;
            ByteBuffer bytes = ByteBuffer.wrap(encoded);
            out.position(out.size());
            while (bytes.hasRemaining()) out.write(bytes);
        } catch (IOException e) {
            // The copy in memory is still there.
        }
    }

    /** Starts the file over: a note of the cut, then as many of the newest lines as fit in half the cap. */
    private static void rewriteWithNewest(FileChannel out) throws IOException {
        List<byte[]> chunks = new ArrayList<>();
        long size = 0;
        for (Iterator<String> it = ENTRIES.descendingIterator(); it.hasNext(); ) {
            byte[] bytes = (it.next() + System.lineSeparator()).getBytes(StandardCharsets.UTF_8);
            if (size + bytes.length > MAX_FILE_BYTES / 2) break;
            chunks.addFirst(bytes);
            size += bytes.length;
        }
        chunks.addFirst((stamp("… earlier lines were cut to keep this file under " + MAX_FILE_BYTES / (1024 * 1024)
                                + " MB; the newest follow.")
                        + System.lineSeparator())
                .getBytes(StandardCharsets.UTF_8));
        out.truncate(0);
        out.position(0);
        fileBytes = 0;
        for (byte[] chunk : chunks) {
            ByteBuffer bytes = ByteBuffer.wrap(chunk);
            while (bytes.hasRemaining()) out.write(bytes);
            fileBytes += chunk.length;
        }
    }

    /** Everything kept this session, oldest first, after a note of how many older lines were cut. */
    public static synchronized String text() {
        String kept = String.join(System.lineSeparator(), ENTRIES);
        if (dropped == 0) return kept;
        return "… " + dropped + " earlier lines are not kept here; the session file has them." + System.lineSeparator()
                + kept;
    }

    /** How many of the oldest lines were cut to keep the log within {@link #MAX_ENTRIES}. */
    static synchronized int dropped() {
        return dropped;
    }

    /** Empties the log in memory; the session file keeps what it has. */
    public static synchronized void clear() {
        ENTRIES.clear();
        dropped = 0;
    }

    static String format(LogRecord record) {
        String logger = record.getLoggerName() == null ? "?" : record.getLoggerName();
        String message = record.getMessage();
        Object[] parameters = record.getParameters();
        if (message != null && parameters != null && parameters.length > 0) {
            try {
                message = java.text.MessageFormat.format(message, parameters);
            } catch (IllegalArgumentException e) {
                // A pattern that is not one: shown as written.
            }
        }
        String line = stamp(
                record.getLevel().getName() + " " + logger.substring(logger.lastIndexOf('.') + 1) + " — " + message);
        Throwable thrown = record.getThrown();
        return thrown == null ? line : line + System.lineSeparator() + stackTrace(thrown);
    }

    private static String stamp(String message) {
        return LocalTime.now().format(Time.FORMAT) + "  " + message;
    }

    static String stackTrace(Throwable error) {
        StringWriter out = new StringWriter();
        error.printStackTrace(new PrintWriter(out));
        return out.toString().stripTrailing();
    }
}
