package com.mirada.os;

import java.io.IOException;
import java.io.InputStream;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jspecify.annotations.Nullable;

/**
 * One running Mirada per config folder (CLI-07, Pauta 4.0.1; the javafx-app-template's
 * {@code instance/SingleInstance}). The first launch listens on a Unix-domain socket in the config
 * folder (Windows 10 and later have them too); a second launch hands it the files it was given, the
 * first opens them and comes to the front, and the second exits. On macOS a launch from Finder or
 * {@code open} never makes a second process: its Apple Event reaches the running app
 * ({@link MacOpenFiles}). A launch from a terminal there uses the socket, as everywhere else.
 *
 * <p>Fast first: a launch with no Mirada running pays one file check ({@link #handOff}). Becoming the
 * one that listens ({@link #claimInBackground}) runs on a thread of its own, so binding the socket and
 * taking the {@link InstanceLock} cost the first image nothing; a launch that arrives in that instant
 * starts a window of its own, as every launch did before. Which process listens is the lock's to
 * settle, so two launches in the same instant never take the socket from each other.
 *
 * <p>Every failure means no hand-off, never no Mirada: a stale socket from a crash is a closed door
 * the next primary removes, and a folder that cannot hold a socket leaves each launch on its own.
 */
public final class SingleInstance implements AutoCloseable {

    /** In the config folder, so {@code --dev} and each {@code --config-dir} are instances apart. */
    public static final String SOCKET = "instance.sock";

    /** Below every platform's limit for a Unix-domain socket's path (104 on macOS, 108 on Linux). */
    static final int MAX_PATH = 100;

    /** How long a second launch waits for the running one's answer before starting on its own. */
    static final long ANSWER_MILLIS = 2000;

    /** Larger than any launch sends: a request claiming more is not Mirada's. */
    static final int MAX_REQUEST = 1 << 20;

    private final Path configDir;
    private final Path socket;
    /** Launches that came before {@link #receive}: the window was not up yet. */
    private final List<List<String>> waiting = new ArrayList<>();

    private @Nullable Consumer<List<String>> listener;
    private @Nullable InstanceLock lock;
    private volatile @Nullable ServerSocketChannel server;
    private volatile boolean closed;

    private SingleInstance(Path configDir) {
        this.configDir = configDir;
        this.socket = socketPath(configDir);
    }

    /**
     * Hands {@code args} (absolute paths, or none to bring the window forward) to the Mirada running
     * on {@code configDir}. True when it took them, and this launch should exit; false when none is
     * running, or it did not answer in time.
     */
    public static boolean handOff(Path configDir, List<String> args) {
        Path socket = socketPath(configDir);
        // The one check every launch pays: no socket, no Mirada, and no networking classes loaded.
        if (!Files.exists(socket)) return false;
        return send(socket, args);
    }

    private static boolean send(Path socket, List<String> args) {
        try (SocketChannel channel = SocketChannel.open(UnixDomainSocketAddress.of(socket))) {
            // A Mirada that took the connection and never answers (stopped, hung) must not hold this
            // launch: closing the channel wakes the read below.
            Thread timeout = Thread.ofPlatform()
                    .daemon()
                    .name("hand-off-timeout")
                    .start(() -> {
                        try {
                            Thread.sleep(ANSWER_MILLIS);
                            channel.close();
                        } catch (InterruptedException | IOException answered) {
                            // answered in time, or already closed
                        }
                    });
            try {
                byte[] payload = encode(args);
                channel.write(ByteBuffer.allocate(4).putInt(payload.length).flip());
                channel.write(ByteBuffer.wrap(payload));
                return channel.read(ByteBuffer.allocate(1)) == 1; // the acknowledgement
            } finally {
                timeout.interrupt();
            }
        } catch (IOException | RuntimeException e) {
            return false; // a stale socket from a crash, or one that does not answer
        }
    }

    /** The arguments as one message: each in UTF-8, separated by NUL, which no path contains. */
    static byte[] encode(List<String> args) {
        return String.join("\0", args).getBytes(StandardCharsets.UTF_8);
    }

    /** {@link #encode}'s reverse; an empty message is no arguments. */
    static List<String> decode(byte[] payload) {
        String text = new String(payload, StandardCharsets.UTF_8);
        return text.isEmpty() ? List.of() : List.of(text.split("\0", -1));
    }

    /**
     * The socket for a config folder: inside it, or, when that path is longer than a Unix-domain
     * socket's may be, in the temp folder under a name made from the folder's path.
     */
    static Path socketPath(Path configDir) {
        Path inside = configDir.toAbsolutePath().resolve(SOCKET);
        if (inside.toString().getBytes(StandardCharsets.UTF_8).length <= MAX_PATH) return inside;
        String hash = Integer.toHexString(
                configDir.toAbsolutePath().normalize().toString().hashCode());
        return Path.of(System.getProperty("java.io.tmpdir"), "mirada-" + hash + ".sock");
    }

    /**
     * Claims {@code configDir} on a thread of its own and, if this is the first Mirada there, listens
     * for later launches. Returns at once; launches are held until {@link #receive}.
     */
    public static SingleInstance claimInBackground(Path configDir) {
        SingleInstance instance = new SingleInstance(configDir);
        Thread thread = new Thread(instance::claim, "single-instance-claim");
        thread.setDaemon(true);
        thread.start();
        return instance;
    }

    /** As {@link #claimInBackground}, on this thread: for the tests. */
    static SingleInstance claimNow(Path configDir) {
        SingleInstance instance = new SingleInstance(configDir);
        instance.claim();
        return instance;
    }

    private void claim() {
        InstanceLock claimed = InstanceLock.claim(configDir);
        synchronized (this) {
            if (closed) {
                claimed.close();
                return;
            }
            lock = claimed;
        }
        if (!claimed.primary()) {
            log().info("Another Mirada uses " + configDir + "; this one takes no launches from others");
            return;
        }
        try {
            // Ours to replace: the lock says no other Mirada on this folder is alive to own it.
            Files.deleteIfExists(socket);
            ServerSocketChannel listening = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
            listening.bind(UnixDomainSocketAddress.of(socket));
            server = listening;
            if (closed) {
                close();
                return;
            }
            Thread thread = new Thread(() -> accept(listening), "single-instance");
            thread.setDaemon(true);
            thread.start();
            // However the process ends. A thread still waiting in accept() held the JVM's exit back by
            // about 300 ms here; closed first, the exit takes no longer than without it.
            Runtime.getRuntime().addShutdownHook(new Thread(this::close, "single-instance-close"));
        } catch (IOException | RuntimeException e) {
            log().log(Level.INFO, "No hand-off from later launches: " + e.getMessage(), e);
        }
    }

    private void accept(ServerSocketChannel listening) {
        while (listening.isOpen()) {
            try (SocketChannel client = listening.accept()) {
                InputStream in = Channels.newInputStream(client);
                byte[] size = in.readNBytes(4);
                if (size.length < 4) continue;
                int length = ByteBuffer.wrap(size).getInt();
                if (length < 0 || length > MAX_REQUEST) continue;
                byte[] payload = in.readNBytes(length);
                if (payload.length < length) continue;
                // Held or delivered before the acknowledgement: once handOff returns, this Mirada has them.
                deliver(decode(payload));
                client.write(ByteBuffer.wrap(new byte[] {1}));
            } catch (IOException | RuntimeException e) {
                if (listening.isOpen()) log().log(Level.FINE, "A hand-off failed", e);
            }
        }
    }

    private void deliver(List<String> args) {
        Consumer<List<String>> target;
        synchronized (this) {
            target = listener;
            if (target == null) {
                waiting.add(args);
                return;
            }
        }
        target.accept(args);
    }

    /**
     * Where later launches' arguments go, on the listening thread; those that came before it are
     * given to it now, in order.
     */
    public void receive(Consumer<List<String>> onArgs) {
        List<List<String>> early;
        synchronized (this) {
            listener = onArgs;
            early = List.copyOf(waiting);
            waiting.clear();
        }
        early.forEach(onArgs);
    }

    /** Whether this process is listening for later launches. */
    public boolean listening() {
        ServerSocketChannel open = server;
        return open != null && open.isOpen();
    }

    /** Whether this process took the folder's lock first; false until the claim has run. */
    public synchronized boolean primary() {
        InstanceLock held = lock;
        return held != null && held.primary();
    }

    /** Stops listening and removes the socket, so the next launch starts its own window at once. */
    @Override
    public void close() {
        InstanceLock held;
        synchronized (this) {
            closed = true;
            held = lock;
        }
        ServerSocketChannel open = server;
        // Once only: closed twice (App.stop, then the shutdown hook), the second must not remove the
        // socket of a Mirada that started in between.
        if (open != null && open.isOpen()) {
            try {
                open.close();
                Files.deleteIfExists(socket);
            } catch (IOException e) {
                // a leftover socket is a closed door the next primary removes
            }
        }
        // After the socket: a launch that takes the lock next finds no socket of ours to remove.
        if (held != null) held.close();
    }

    private static Logger log() {
        return Logger.getLogger(SingleInstance.class.getName());
    }
}
