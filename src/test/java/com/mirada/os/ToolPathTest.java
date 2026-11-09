package com.mirada.os;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** CLI-10: every program Mirada starts gets a PATH that includes the login shell's. */
class ToolPathTest {

    @Test
    void ownFoldersComeFirstThenTheLoginShellsNotAlreadyThere() {
        assertEquals(
                "/stubs:/usr/bin:/bin:/opt/homebrew/bin:/home/u/.local/bin",
                ToolPath.merged("/stubs:/usr/bin::/bin", "/opt/homebrew/bin:/usr/bin:/home/u/.local/bin", ":"));
        assertEquals("/usr/bin", ToolPath.merged("/usr/bin", null, ":"));
        assertEquals("/opt/homebrew/bin", ToolPath.merged(null, "/opt/homebrew/bin", ":"));
        assertEquals("", ToolPath.merged(null, null, ":"));
    }

    @Test
    void aProgramIsFoundOnTheMergedPathAsJavaWouldNotOnItsOwn() {
        String path = String.join(File.pathSeparator, "/usr/bin", "/opt/homebrew/bin");
        Set<String> there = Set.of("/opt/homebrew/bin/gio", "/usr/bin/xdg-open", "/opt/homebrew/bin/xdg-open");
        assertEquals(
                List.of("/opt/homebrew/bin/gio", "trash", "--", "/p/a.jpg"),
                ToolPath.resolved(List.of("gio", "trash", "--", "/p/a.jpg"), path, "/", there::contains));
        // The first on the PATH wins, as a shell would choose.
        assertEquals(
                List.of("/usr/bin/xdg-open", "/p"),
                ToolPath.resolved(List.of("xdg-open", "/p"), path, "/", there::contains));
        // A path, or a program found nowhere, is left alone: its start fails as it did before.
        assertEquals(List.of("/bin/open", "-R"), ToolPath.resolved(List.of("/bin/open", "-R"), path, "/", f -> true));
        assertEquals(List.of("nowhere"), ToolPath.resolved(List.of("nowhere"), path, "/", there::contains));
        assertEquals(List.of(), ToolPath.resolved(List.of(), path, "/", f -> true));
        assertEquals(
                List.of("/usr/bin/x"), ToolPath.resolved(List.of("x"), "/usr/bin/", "/", f -> f.equals("/usr/bin/x")));
    }

    @Test
    void thePathIsReadBetweenItsMarkersWhateverTheProfilePrints() {
        assertEquals(
                "/usr/bin:/bin",
                ToolPath.between("Welcome!\n" + ToolPath.BEGIN + "/usr/bin:/bin" + ToolPath.END + "\nbye"));
        assertNull(ToolPath.between("no markers"));
        assertNull(ToolPath.between(ToolPath.BEGIN + "/usr/bin"));
        assertNull(ToolPath.between(ToolPath.BEGIN + "  " + ToolPath.END));
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void aLoginShellIsAskedForItsPath() {
        String path = ToolPath.ask("/bin/sh");
        assertNotNull(path);
        assertTrue(path.contains("/bin"), path);
    }

    @Test
    void noShellOrOneThatSaysNothingIsNoPath(@TempDir Path dir) throws Exception {
        assertNull(ToolPath.ask(dir.resolve("no-such-shell").toString()));
        assertNull(ToolPath.ask("\0"));
        if (!System.getProperty("os.name", "").toLowerCase().startsWith("windows")) {
            Path quiet = Files.writeString(dir.resolve("quiet"), "#!/bin/sh\nexit 0\n");
            assertTrue(quiet.toFile().setExecutable(true));
            assertNull(ToolPath.ask(quiet.toString()));
        }
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void aStartedProgramHasItsFullPathAndTheMergedPath() {
        ProcessBuilder builder = ToolPath.process(List.of("sh", "-c", "true"));
        assertTrue(
                builder.command().getFirst().endsWith("/sh"), builder.command().getFirst());
        String path = builder.environment().get("PATH");
        String own = System.getenv("PATH");
        if (own != null) assertTrue(path.startsWith(own.split(":")[0]), path);
    }
}
