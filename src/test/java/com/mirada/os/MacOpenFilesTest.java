package com.mirada.os;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

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
    void listensOnlyOnMacOs() {
        if (MacOpenFiles.isMac(System.getProperty("os.name"))) return;
        assertEquals("none (not macOS)", MacOpenFiles.listen());
    }

    @Test
    void ofSeveralFilesTheFirstIsShown() {
        assertEquals(
                Path.of("a.jpg"),
                MacOpenFiles.first(List.of(Path.of("a.jpg"), Path.of("b.jpg"))).orElseThrow());
        assertTrue(MacOpenFiles.first(List.of()).isEmpty());
    }

    @Test
    void aFileUrlIsItsPath(@TempDir Path folder) {
        Path photo = folder.resolve("a photo.jpg");
        assertEquals(Optional.of(photo), MacOpenFiles.fileOf(photo.toUri().toString()));
        // AppKit's absoluteString escapes what a URI must. A Unix path, which Windows reads otherwise.
        if (!System.getProperty("os.name", "").startsWith("Windows")) {
            assertEquals(
                    Optional.of(Path.of("/Users/me/Pictures/a b.jpg")),
                    MacOpenFiles.fileOf("file:///Users/me/Pictures/a%20b.jpg"));
        }
    }

    @Test
    void anythingButAFileUrlIsNothing() {
        assertTrue(MacOpenFiles.fileOf(null).isEmpty());
        assertTrue(MacOpenFiles.fileOf("https://example.com/a.jpg").isEmpty());
        assertTrue(MacOpenFiles.fileOf("not a url").isEmpty());
        assertTrue(MacOpenFiles.fileOf("file:relative.jpg").isEmpty());
    }

    @Test
    void whatArrivesBeforeTheWindowWaitsForIt() {
        List<Path> opened = new ArrayList<>();
        MacOpenFiles.arrived(Path.of("/photos/a.jpg"));
        MacOpenFiles.arrived(Path.of("/photos/b.jpg"));
        assertTrue(opened.isEmpty());

        MacOpenFiles.receive(null, opened::add);

        // One event, two files: one window, the first.
        assertEquals(List.of(Path.of("/photos/a.jpg")), opened);
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
