package com.mirada.ui;

import java.awt.Color;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.testfx.api.FxToolkit;

import static com.mirada.ui.FxTestSupport.call;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Move to Trash and its undo, through the window, on Linux's own trash (the only one Mirada can put
 * back from). The undo list is bounded (PERF-11): past {@link ViewerWindow#UNDO_DEPTH} files the
 * oldest can no longer be put back, the newest can.
 *
 * <p>The trash is surefire's, under {@code target/} ({@code XDG_DATA_HOME}), and the folder sits on
 * the same drive, so no file reaches the desktop's trash; without that set up the test is skipped.
 */
@Tag("fx")
@EnabledOnOs(OS.LINUX)
class TrashUndoTest {

    private Path folder = Path.of("unset");
    private Path trash = Path.of("unset");

    @AfterEach
    void cleanUp() throws Exception {
        FxToolkit.cleanupStages();
    }

    private void setUp() throws Exception {
        String dataHome = System.getenv("XDG_DATA_HOME");
        assumeTrue(dataHome != null && dataHome.contains("target"), "the trash is surefire's, under target/");
        Path data = Path.of(dataHome);
        Files.createDirectories(data);
        trash = data.resolve("Trash");
        folder = data.resolveSibling("trash-test-" + UUID.randomUUID());
        Files.createDirectories(folder);
        assumeTrue(
                Files.getFileStore(folder).equals(Files.getFileStore(data)), "the folder and the trash share a drive");
    }

    @Test
    void aTrashedImageIsPutBackAndShownAgain() throws Exception {
        setUp();
        WindowFixture.threeImages(folder);
        try (WindowFixture window = WindowFixture.open(folder.resolve("config"), folder.resolve("b.png"))) {
            window.waitForImage("b.png");
            assertEquals(
                    "Nothing has been moved to the Trash yet.", call(() -> unavailable(window, Command.UNDO_TRASH)));
            window.run(Command.MOVE_TO_TRASH);
            window.waitToSay("Moved b.png to the Trash");
            window.waitForImage("c.jpg");
            assertFalse(Files.exists(folder.resolve("b.png")));
            assertTrue(Files.exists(trash.resolve("files/b.png")), "in the trash under target/");
            assertTrue(window.lastSaid().endsWith("puts it back"), "the message says how to undo");

            window.run(Command.UNDO_TRASH);
            window.waitToSay("Put back b.png");
            window.waitForImage("b.png");
            assertTrue(Files.exists(folder.resolve("b.png")));
            assertFalse(Files.exists(trash.resolve("info/b.png.trashinfo")), "its info file is gone");
        }
    }

    @Test
    void pastItsDepthTheOldestTrashedFileCanNoLongerBePutBack() throws Exception {
        setUp();
        int count = ViewerWindow.UNDO_DEPTH + 1;
        List<String> names = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String name = String.format("%02d.png", i);
            WindowFixture.png(folder, name, 8 + i, 8, Color.ORANGE);
            names.add(name);
        }
        WindowFixture.png(folder, "zz.png", 8, 8, Color.BLACK);
        try (WindowFixture window = WindowFixture.open(folder.resolve("config"), folder)) {
            for (String name : names) {
                window.waitForImage(name);
                window.run(Command.MOVE_TO_TRASH);
                window.waitToSay("Moved " + name);
            }
            window.waitForImage("zz.png");
            for (int i = count - 1; i >= 1; i--) {
                window.run(Command.UNDO_TRASH);
                window.waitToSay("Put back " + names.get(i));
            }
            // The newest twenty came back; the first, past the depth, stays in the trash.
            for (String name : names.subList(1, count)) assertTrue(Files.exists(folder.resolve(name)), name);
            assertFalse(Files.exists(folder.resolve(names.getFirst())), "the oldest was not kept to undo");
            assertEquals(
                    "Nothing has been moved to the Trash yet.", call(() -> unavailable(window, Command.UNDO_TRASH)));
            int said = window.said().size();
            window.run(Command.UNDO_TRASH);
            FxTestSupport.drain();
            assertEquals(said, window.said().size(), "nothing left to put back, nothing done");
        }
    }

    private static String unavailable(WindowFixture window, Command command) {
        return String.valueOf(
                FxTestSupport.invoke(window.window, "unavailable", new Class<?>[] {Command.class}, command));
    }
}
