package com.mirada.os;

import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** CLI-07: the first launch on a config folder listens, a second hands it its files and exits. */
class SingleInstanceTest {

    @Test
    void theFirstLaunchIsPrimaryAndASecondHandsItItsFiles(@TempDir Path dir) {
        List<List<String>> received = new CopyOnWriteArrayList<>();
        try (SingleInstance first = SingleInstance.claimNow(dir)) {
            assertTrue(first.primary());
            assertTrue(first.listening());
            first.receive(received::add);
            assertTrue(SingleInstance.handOff(dir, List.of("/p/a.jpg", "/p/b c.jpg")));
            assertTrue(SingleInstance.handOff(dir, List.of()));
            assertEquals(List.of(List.of("/p/a.jpg", "/p/b c.jpg"), List.of()), received);
        }
        // Closed, it leaves no socket: the next launch starts its own window at once.
        assertFalse(Files.exists(SingleInstance.socketPath(dir)));
        assertFalse(SingleInstance.handOff(dir, List.of("/p/a.jpg")));
    }

    @Test
    void launchesBeforeTheWindowAreHeldUntilItAsks(@TempDir Path dir) {
        try (SingleInstance first = SingleInstance.claimNow(dir)) {
            assertTrue(SingleInstance.handOff(dir, List.of("/p/early.jpg")));
            List<List<String>> received = new CopyOnWriteArrayList<>();
            first.receive(received::add);
            assertEquals(List.of(List.of("/p/early.jpg")), received);
        }
    }

    @Test
    void aSecondProcessOnTheFolderDoesNotListen(@TempDir Path dir) {
        try (SingleInstance first = SingleInstance.claimNow(dir);
                SingleInstance second = SingleInstance.claimNow(dir)) {
            assertTrue(first.listening());
            assertFalse(second.primary());
            assertFalse(second.listening());
            // The second did not take the socket from the first.
            List<List<String>> received = new CopyOnWriteArrayList<>();
            first.receive(received::add);
            assertTrue(SingleInstance.handOff(dir, List.of("/p/a.jpg")));
            assertEquals(List.of(List.of("/p/a.jpg")), received);
        }
    }

    @Test
    void configFoldersAreInstancesApart(@TempDir Path dir) {
        Path mine = dir.resolve("mine");
        Path dev = dir.resolve("dev");
        try (SingleInstance first = SingleInstance.claimNow(mine)) {
            assertTrue(first.listening());
            assertFalse(SingleInstance.handOff(dev, List.of("/p/a.jpg")));
        }
    }

    @Test
    void aStaleSocketFromACrashIsNoMiradaAndTheNextPrimaryReplacesIt(@TempDir Path dir) throws Exception {
        // A socket no one listens on, as a crash leaves it.
        Path socket = SingleInstance.socketPath(dir);
        try (ServerSocketChannel crashed = ServerSocketChannel.open(StandardProtocolFamily.UNIX)) {
            crashed.bind(UnixDomainSocketAddress.of(socket));
        }
        assertTrue(Files.exists(socket));
        assertFalse(SingleInstance.handOff(dir, List.of()));
        try (SingleInstance next = SingleInstance.claimNow(dir)) {
            assertTrue(next.listening());
            assertTrue(SingleInstance.handOff(dir, List.of()));
        }
    }

    @Test
    void aBrokenOrBogusHandOffIsDroppedAndTheNextIsServed(@TempDir Path dir) throws Exception {
        List<List<String>> received = new CopyOnWriteArrayList<>();
        try (SingleInstance first = SingleInstance.claimNow(dir)) {
            first.receive(received::add);
            UnixDomainSocketAddress address = UnixDomainSocketAddress.of(SingleInstance.socketPath(dir));
            try (SocketChannel dropped = SocketChannel.open(address)) {
                dropped.write(ByteBuffer.wrap(new byte[] {0, 0})); // half a length, then gone
            }
            try (SocketChannel bogus = SocketChannel.open(address)) {
                bogus.write(ByteBuffer.allocate(4).putInt(-1).flip()); // a length no launch sends
            }
            try (SocketChannel huge = SocketChannel.open(address)) {
                huge.write(ByteBuffer.allocate(4)
                        .putInt(SingleInstance.MAX_REQUEST + 1)
                        .flip());
            }
            try (SocketChannel short_ = SocketChannel.open(address)) {
                short_.write(ByteBuffer.allocate(6)
                        .putInt(10)
                        .put((byte) 'a')
                        .put((byte) 'b')
                        .flip());
            }
            assertTrue(SingleInstance.handOff(dir, List.of("/p/after.jpg")));
            assertEquals(List.of(List.of("/p/after.jpg")), received);
        }
    }

    @Test
    void aMiradaThatNeverAnswersDoesNotHoldTheLaunch(@TempDir Path dir) throws Exception {
        Path socket = SingleInstance.socketPath(dir);
        try (ServerSocketChannel hung = ServerSocketChannel.open(StandardProtocolFamily.UNIX)) {
            hung.bind(UnixDomainSocketAddress.of(socket));
            long start = System.nanoTime();
            // The connection waits in the backlog; nothing ever reads it or answers.
            assertFalse(SingleInstance.handOff(dir, List.of("/p/a.jpg")));
            long millis = (System.nanoTime() - start) / 1_000_000;
            assertTrue(millis >= SingleInstance.ANSWER_MILLIS - 100 && millis < 10_000, millis + " ms");
        }
    }

    @Test
    void argumentsSurviveTheTripWhateverTheyHoldButNul() {
        for (List<String> args : List.of(
                List.<String>of(), List.of(""), List.of("/a b/ç ü 写真.jpg", "/x\ny.jpg"), List.of("a", "", "b"))) {
            assertEquals(
                    args.isEmpty() || args.equals(List.of("")) ? List.of() : args,
                    SingleInstance.decode(SingleInstance.encode(args)));
        }
    }

    @Test
    void aLongFolderGetsASocketInTheTempFolder(@TempDir Path dir) {
        Path deep = dir.resolve("a".repeat(60)).resolve("b".repeat(60));
        Path socket = SingleInstance.socketPath(deep);
        assertTrue(socket.startsWith(Path.of(System.getProperty("java.io.tmpdir"))));
        assertEquals(dir.resolve(SingleInstance.SOCKET).toAbsolutePath(), SingleInstance.socketPath(dir));
        try (SingleInstance first = SingleInstance.claimNow(deep)) {
            assertTrue(first.listening());
            assertTrue(SingleInstance.handOff(deep, List.of("/p/z.jpg")));
        }
    }

    @Test
    void aFolderThatCannotBeMadeLeavesTheLaunchOnItsOwn(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("file");
        Files.writeString(file, "x");
        try (SingleInstance alone = SingleInstance.claimNow(file.resolve("sub"))) {
            assertFalse(alone.listening());
            // The lock could not be made either: the only Mirada there is, as before there was one.
            assertTrue(alone.primary());
        }
    }

    @Test
    void theBackgroundClaimListensSoon(@TempDir Path dir) throws Exception {
        try (SingleInstance first = SingleInstance.claimInBackground(dir)) {
            long deadline = System.nanoTime() + 5_000_000_000L;
            while (!first.listening() && System.nanoTime() < deadline) Thread.sleep(5);
            assertTrue(first.listening());
            assertTrue(first.primary());
        }
    }

    @Test
    void closingBeforeTheClaimRanLeavesNothingBehind(@TempDir Path dir) throws Exception {
        SingleInstance first = SingleInstance.claimInBackground(dir);
        first.close();
        Thread.sleep(200);
        assertFalse(first.listening());
        assertFalse(Files.exists(SingleInstance.socketPath(dir)));
        // And the lock went with it: the next is primary.
        try (SingleInstance next = SingleInstance.claimNow(dir)) {
            assertTrue(next.primary());
        }
    }
}
