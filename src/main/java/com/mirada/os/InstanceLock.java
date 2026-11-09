package com.mirada.os;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import org.jspecify.annotations.Nullable;

/**
 * An advisory claim on a config folder, held for the life of the process, so two Mirada processes on
 * one folder know about each other (CLI-08, Pauta 4.0.1; Editora's {@code config/InstanceLock}). The
 * first takes byte 0 of {@code instance.lock} exclusively and is the primary: it alone serves the
 * hand-off socket ({@link SingleInstance}). A later one (started with {@code --new-instance}, or in
 * the instant before the primary was listening) finds byte 0 taken and is a secondary; it holds byte 1
 * shared for its life, so the primary can ask whether anyone else is there ({@link #othersPresent}).
 *
 * <p>The operating system drops both locks when their holder dies, so a crash leaves no stale claim,
 * as a pid in a file would. On a file system that refuses locks the claim is "primary, alone": the
 * behaviour before there was a lock.
 */
public final class InstanceLock implements AutoCloseable {

    /** Under the config folder; its content is never read or written. */
    public static final String FILE_NAME = "instance.lock";

    private static final long PRIMARY_BYTE = 0;
    private static final long PRESENCE_BYTE = 1;

    private final @Nullable FileChannel channel;
    private final boolean primary;
    /** Kept so the claim lives as long as this object; released by closing the channel. */
    private final @Nullable FileLock held;

    private InstanceLock(@Nullable FileChannel channel, boolean primary, @Nullable FileLock held) {
        this.channel = channel;
        this.primary = primary;
        this.held = held;
    }

    /** Claims {@code configDir} for this process. Never throws and never waits for another process. */
    public static InstanceLock claim(Path configDir) {
        FileChannel channel;
        try {
            Files.createDirectories(configDir);
            channel = FileChannel.open(
                    configDir.resolve(FILE_NAME),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.READ,
                    StandardOpenOption.WRITE);
        } catch (IOException | RuntimeException e) {
            return new InstanceLock(null, true, null);
        }
        try {
            FileLock primary = channel.tryLock(PRIMARY_BYTE, 1, false);
            if (primary != null) return new InstanceLock(channel, true, primary);
        } catch (OverlappingFileLockException inThisJvm) {
            // Another channel of this JVM holds it: what a second process would be told. Tests use this.
        } catch (IOException | RuntimeException refused) {
            closeQuietly(channel);
            return new InstanceLock(null, true, null);
        }
        return new InstanceLock(channel, false, tryLock(channel, PRESENCE_BYTE, true));
    }

    /** Whether this process took the folder first. Fixed for the life of the process. */
    public boolean primary() {
        return primary;
    }

    /**
     * Whether another Mirada uses the folder now. A secondary always knows the primary is there; the
     * primary tries the presence byte, which every secondary holds shared, and lets it go at once.
     * Not knowing counts as yes.
     */
    public synchronized boolean othersPresent() {
        FileChannel open = channel;
        if (!primary) return true;
        if (open == null || !open.isOpen()) return false;
        FileLock probe = tryLock(open, PRESENCE_BYTE, false);
        if (probe == null) return true;
        try {
            probe.release();
        } catch (IOException e) {
            // closed with the channel
        }
        return false;
    }

    /** The lock, or null when it is held elsewhere or the file system refused it. */
    private static @Nullable FileLock tryLock(FileChannel channel, long position, boolean shared) {
        try {
            return channel.tryLock(position, 1, shared);
        } catch (IOException | OverlappingFileLockException e) {
            return null;
        }
    }

    /** Releases the claim. */
    @Override
    public synchronized void close() {
        closeQuietly(channel);
    }

    /** Whether the claim is still held: for the tests. */
    boolean held() {
        FileLock lock = held;
        return lock != null && lock.isValid();
    }

    private static void closeQuietly(@Nullable FileChannel channel) {
        if (channel == null) return;
        try {
            channel.close();
        } catch (IOException e) {
            // the locks go with the process anyway
        }
    }
}
