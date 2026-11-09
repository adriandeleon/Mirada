package com.mirada.os;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** CLI-08: two processes on one config folder know about each other through its lock. */
class InstanceLockTest {

    /** Another JVM claiming {@code dir}; the first line it prints is the role it got. */
    private static final class OtherProcess implements AutoCloseable {
        private final Process process;
        private final BufferedReader out;
        private final BufferedWriter in;
        final String role;

        OtherProcess(Path dir) throws Exception {
            process = new ProcessBuilder(
                            Path.of(System.getProperty("java.home"), "bin", "java")
                                    .toString(),
                            "-cp",
                            System.getProperty("java.class.path"),
                            InstanceLockProbe.class.getName(),
                            dir.toString())
                    .redirectError(ProcessBuilder.Redirect.INHERIT)
                    .start();
            out = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            in = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
            role = out.readLine();
        }

        boolean seesOthers() throws Exception {
            in.write("probe\n");
            in.flush();
            return "others=true".equals(out.readLine());
        }

        /** Returns once the process is gone and the system has dropped its locks. */
        void exit() throws Exception {
            in.close();
            assertTrue(process.waitFor(30, TimeUnit.SECONDS));
        }

        @Override
        public void close() {
            process.destroyForcibly();
        }
    }

    @Test
    void theFirstProcessIsPrimaryAndSeesALaterOneComeAndGo(@TempDir Path dir) throws Exception {
        try (InstanceLock first = InstanceLock.claim(dir)) {
            assertTrue(first.primary());
            assertTrue(first.held());
            assertFalse(first.othersPresent());
            try (OtherProcess second = new OtherProcess(dir)) {
                assertEquals("secondary", second.role);
                assertTrue(second.seesOthers());
                assertTrue(first.othersPresent());
                second.exit();
            }
            assertFalse(first.othersPresent());
        }
    }

    @Test
    void aLaterProcessIsSecondaryAndADeadPrimaryLeavesNoStaleClaim(@TempDir Path dir) throws Exception {
        try (OtherProcess primary = new OtherProcess(dir)) {
            assertEquals("primary", primary.role);
            assertFalse(primary.seesOthers());
            try (InstanceLock second = InstanceLock.claim(dir)) {
                assertFalse(second.primary());
                assertTrue(second.othersPresent());
                assertTrue(primary.seesOthers());
            }
            primary.close(); // killed, as a crash would
            primary.process.waitFor(30, TimeUnit.SECONDS);
        }
        try (InstanceLock next = InstanceLock.claim(dir)) {
            assertTrue(next.primary());
        }
    }

    @Test
    void closingLetsTheNextProcessBePrimary(@TempDir Path dir) {
        InstanceLock first = InstanceLock.claim(dir);
        assertTrue(first.primary());
        first.close();
        assertFalse(first.held());
        assertFalse(first.othersPresent());
        try (InstanceLock next = InstanceLock.claim(dir)) {
            assertTrue(next.primary());
        }
    }

    @Test
    void aFolderThatCannotHoldTheLockMeansTheOnlyMirada(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("file");
        Files.writeString(file, "x");
        try (InstanceLock alone = InstanceLock.claim(file.resolve("sub"))) {
            assertTrue(alone.primary());
            assertFalse(alone.held());
            assertFalse(alone.othersPresent());
        }
    }

    @Test
    void theLockIsMadeInAConfigFolderThatDoesNotExistYet(@TempDir Path dir) {
        Path fresh = dir.resolve("not").resolve("yet");
        try (InstanceLock lock = InstanceLock.claim(fresh)) {
            assertTrue(lock.primary());
            assertTrue(Files.exists(fresh.resolve(InstanceLock.FILE_NAME)));
        }
    }
}
