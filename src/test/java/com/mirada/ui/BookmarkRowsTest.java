package com.mirada.ui;

import java.nio.file.Path;

import com.mirada.settings.Bookmarks;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** How Go to Bookmark lists a mark: by name, a folder's with a slash, and its note's first line. */
class BookmarkRowsTest {

    @Test
    void aRowIsTheNameAndTheNotesFirstLine(@TempDir Path dir) {
        Path photo = dir.resolve("2026/IMG_0412.jpg");
        assertEquals("IMG_0412.jpg", ViewerWindow.bookmarkName(new Bookmarks.Mark(photo, false, true, "")));
        assertEquals(
                "IMG_0412.jpg — The lake at dawn",
                ViewerWindow.bookmarkName(new Bookmarks.Mark(photo, false, true, "The lake at dawn\nprint it")));
        assertEquals(
                "2026/ — Holiday",
                ViewerWindow.bookmarkName(new Bookmarks.Mark(dir.resolve("2026"), true, false, "Holiday")));
        String longNote = "word ".repeat(40);
        String row = ViewerWindow.bookmarkName(new Bookmarks.Mark(photo, false, true, longNote));
        assertEquals(true, row.endsWith("…") && row.length() < "IMG_0412.jpg — ".length() + 73, row);
    }

    @Test
    void theDetailIsTheFolderAndWhetherItIsOnlyANote(@TempDir Path dir) {
        Path photo = dir.resolve("2026/IMG_0412.jpg");
        assertEquals("2026", ViewerWindow.bookmarkDetail(new Bookmarks.Mark(photo, false, true, "")));
        assertEquals("Note · 2026", ViewerWindow.bookmarkDetail(new Bookmarks.Mark(photo, false, false, "a note")));
        assertEquals("Folder", ViewerWindow.bookmarkDetail(new Bookmarks.Mark(dir, true, true, "")));
    }
}
