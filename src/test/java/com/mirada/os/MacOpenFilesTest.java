package com.mirada.os;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.sun.glass.ui.Application;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MacOpenFilesTest {

    @Test
    void onlyMacOsNeedsAwt() {
        assertFalse(Trash.headless("Mac OS X"));
        assertFalse(Trash.headless("Darwin"));
        assertTrue(Trash.headless("Linux"));
        assertTrue(Trash.headless("Windows 11"));
        assertTrue(Trash.headless(null));
    }

    @Test
    void ofSeveralFilesTheFirstIsShown() {
        assertEquals(
                Path.of("a.jpg"),
                MacOpenFiles.first(new String[] {"a.jpg", "b.jpg"}).orElseThrow());
        assertEquals(
                Path.of("b.jpg"),
                MacOpenFiles.first(new String[] {null, "b.jpg"}).orElseThrow());
        assertTrue(MacOpenFiles.first(new String[0]).isEmpty());
        assertTrue(MacOpenFiles.first(null).isEmpty());
    }

    @Test
    void theHandlerOpensTheFileAndLeavesTheRestToJavaFx() {
        List<String> javafx = new ArrayList<>();
        List<Path> opened = new ArrayList<>();
        Application.EventHandler quantum = new Application.EventHandler() {
            @Override
            public void handleQuitAction(Application app, long time) {
                javafx.add("quit");
            }

            @Override
            public void handleOpenFilesAction(Application app, long time, String[] files) {
                javafx.add("open");
            }
        };
        MacOpenFiles.Handler handler = new MacOpenFiles.Handler(quantum, opened::add, false);

        handler.handleOpenFilesAction(null, 0, new String[] {"/photos/a.jpg", "/photos/b.jpg"});
        handler.handleQuitAction(null, 0);

        assertEquals(List.of(Path.of("/photos/a.jpg")), opened);
        // Quit is how JavaFX closes its windows on Cmd+Q from the Dock: it must still get there.
        assertEquals(List.of("open", "quit"), javafx);
    }

    @Test
    void withoutJavaFxsHandlerTheFilesStillOpen() {
        List<Path> opened = new ArrayList<>();
        new MacOpenFiles.Handler(null, opened::add, false).handleOpenFilesAction(null, 0, new String[] {"a.jpg"});
        assertEquals(List.of(Path.of("a.jpg")), opened);
    }

    @Test
    void theEventThatRepeatsTheArgumentIsDroppedOnce(@TempDir Path folder) throws Exception {
        Path photo = Files.createFile(folder.resolve("photo.jpg"));
        Path other = Files.createFile(folder.resolve("other.jpg"));
        List<Path> opened = new ArrayList<>();
        // The argument as typed, relative or not; AppKit's event names it absolutely.
        Consumer<Path> open = MacOpenFiles.besides(folder.resolve(".").resolve("photo.jpg"), opened::add);

        open.accept(photo.toAbsolutePath());
        open.accept(other);
        open.accept(photo);

        assertEquals(List.of(other, photo), opened);
    }

    @Test
    void aFirstEventForAnotherFileStillOpens(@TempDir Path folder) throws Exception {
        Path other = Files.createFile(folder.resolve("other.jpg"));
        List<Path> opened = new ArrayList<>();
        MacOpenFiles.besides(folder.resolve("photo.jpg"), opened::add).accept(other);
        MacOpenFiles.besides(null, opened::add).accept(other);
        assertEquals(List.of(other, other), opened);
    }
}
