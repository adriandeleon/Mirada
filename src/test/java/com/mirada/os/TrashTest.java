package com.mirada.os;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrashTest {

    /** The freedesktop trash is Linux's, and these paths are Unix ones. */
    @Test
    @DisabledOnOs(OS.WINDOWS)
    void theHomeTrashFollowsXdgDataHome() {
        assertEquals(Path.of("/data/Trash"), Trash.homeTrash("/data", "/home/me"));
        assertEquals(Path.of("/home/me/.local/share/Trash"), Trash.homeTrash(null, "/home/me"));
        // The spec ignores a relative XDG_DATA_HOME.
        assertEquals(Path.of("/home/me/.local/share/Trash"), Trash.homeTrash("data", "/home/me"));
    }

    @Test
    void aTakenNameIsNumberedBeforeTheExtension() {
        assertEquals("photo.jpg", Trash.numbered("photo.jpg", 0));
        assertEquals("photo.2.jpg", Trash.numbered("photo.jpg", 1));
        assertEquals("README.3", Trash.numbered("README", 2));
        assertEquals(".hidden.2", Trash.numbered(".hidden", 1));
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void theInfoFileSaysWhereItCameFromAndWhen() {
        String info =
                Trash.trashInfo(Path.of("/home/me/My Photos/été.jpg"), LocalDateTime.of(2026, 11, 1, 12, 30, 5, 999));
        assertEquals(
                "[Trash Info]\nPath=/home/me/My%20Photos/%C3%A9t%C3%A9.jpg\nDeletionDate=2026-11-01T12:30:05\n", info);
    }

    @Test
    void aFileGoesIntoFilesWithItsInfoBeside(@TempDir Path dir) throws IOException {
        Path trash = dir.resolve("Trash");
        Path photo = Files.writeString(dir.resolve("photo.jpg"), "first");
        Trash.freedesktop(photo, trash);
        Path again = Files.writeString(dir.resolve("photo.jpg"), "second");
        Trash.freedesktop(again, trash);

        assertFalse(Files.exists(photo));
        assertEquals("first", Files.readString(trash.resolve("files/photo.jpg")));
        assertEquals("second", Files.readString(trash.resolve("files/photo.2.jpg")));
        assertTrue(Files.readString(trash.resolve("info/photo.2.jpg.trashinfo"))
                .contains("Path=" + Trash.encodePath(photo.toAbsolutePath().toString())));
    }

    @Test
    void aTrashedFileIsPutBackWhereItWasAndItsInfoRemoved(@TempDir Path dir) throws IOException {
        Path trash = dir.resolve("Trash");
        Path photo = Files.writeString(dir.resolve("photo.jpg"), "pixels");
        Trash.Trashed trashed = Trash.freedesktop(photo, trash);
        assertTrue(trashed.restorable());

        Trash.restore(trashed);

        assertEquals("pixels", Files.readString(photo));
        assertFalse(Files.exists(trash.resolve("files/photo.jpg")));
        assertFalse(Files.exists(trash.resolve("info/photo.jpg.trashinfo")));
    }

    @Test
    void aFileThatCameSinceIsNeverOverwritten(@TempDir Path dir) throws IOException {
        Path trash = dir.resolve("Trash");
        Path photo = Files.writeString(dir.resolve("photo.jpg"), "old");
        Trash.Trashed trashed = Trash.freedesktop(photo, trash);
        Files.writeString(photo, "new");

        assertThrows(FileAlreadyExistsException.class, () -> Trash.restore(trashed));
        assertEquals("new", Files.readString(photo));
        assertEquals("old", Files.readString(trash.resolve("files/photo.jpg")));
    }

    @Test
    void whatTheDesktopTrashedCannotBePutBackFromHere() {
        assertFalse(Trash.Trashed.gone(Path.of("photo.jpg")).restorable());
        assertThrows(IOException.class, () -> Trash.restore(Trash.Trashed.gone(Path.of("photo.jpg"))));
    }
}
