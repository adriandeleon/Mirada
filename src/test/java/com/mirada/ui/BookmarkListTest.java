package com.mirada.ui;

import java.nio.file.Path;
import java.util.List;

import com.mirada.settings.Bookmarks;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What the Bookmarks and Notes window lists: by folder, the latest folder first, images by name. */
class BookmarkListTest {

    private static Bookmarks.Mark image(Path file, boolean bookmarked, String note) {
        return new Bookmarks.Mark(file, false, bookmarked, note);
    }

    private static Bookmarks.Mark folder(Path folder, boolean bookmarked, String note) {
        return new Bookmarks.Mark(folder, true, bookmarked, note);
    }

    private static List<Path> paths(List<BookmarkList.Row> rows) {
        return rows.stream().map(BookmarkList.Row::path).toList();
    }

    @Test
    void foldersComeByTheirLatestMarkAndImagesByName(@TempDir Path dir) {
        Path lake = dir.resolve("Lake");
        Path city = dir.resolve("City");
        // Latest first, as Bookmarks.all gives them.
        List<Bookmarks.Mark> marks = List.of(
                image(city.resolve("IMG_10.jpg"), true, ""),
                image(lake.resolve("IMG_9.jpg"), true, ""),
                folder(lake, false, "Holiday"),
                image(city.resolve("IMG_9.jpg"), false, "dusk"),
                image(lake.resolve("IMG_10.jpg"), true, ""));
        List<BookmarkList.Row> rows = BookmarkList.rows(marks, "", BookmarkList.Show.ALL);
        assertEquals(
                List.of(
                        city,
                        city.resolve("IMG_9.jpg"),
                        city.resolve("IMG_10.jpg"),
                        lake,
                        lake.resolve("IMG_9.jpg"),
                        lake.resolve("IMG_10.jpg")),
                paths(rows));
        // A folder heads its group with its own mark when it has one, with its name alone when not.
        assertNull(((BookmarkList.FolderRow) rows.get(0)).mark());
        Bookmarks.Mark lakeMark = ((BookmarkList.FolderRow) rows.get(3)).mark();
        assertTrue(lakeMark != null && lakeMark.note().equals("Holiday"));
    }

    @Test
    void bookmarksOrNotesAlone(@TempDir Path dir) {
        Path a = dir.resolve("a.jpg");
        Path b = dir.resolve("b.jpg");
        List<Bookmarks.Mark> marks = List.of(image(a, true, ""), image(b, false, "a note"), folder(dir, true, ""));
        assertEquals(List.of(dir, a), paths(BookmarkList.rows(marks, "", BookmarkList.Show.BOOKMARKS)));
        assertEquals(List.of(dir, b), paths(BookmarkList.rows(marks, "", BookmarkList.Show.NOTES)));
        // The folder heads the notes only as a heading: its bookmark is not a note.
        assertNull(((BookmarkList.FolderRow)
                        BookmarkList.rows(marks, "", BookmarkList.Show.NOTES).getFirst())
                .mark());
    }

    @Test
    void everyWordIsFoundInTheNameTheFolderOrTheNote(@TempDir Path dir) {
        Bookmarks.Mark mark = image(dir.resolve("Holiday/IMG_0412.jpg"), true, "The lake at DAWN");
        assertTrue(BookmarkList.matches(mark, BookmarkList.words("")));
        assertTrue(BookmarkList.matches(mark, BookmarkList.words("0412")));
        assertTrue(BookmarkList.matches(mark, BookmarkList.words("  dawn  holi ")));
        assertFalse(BookmarkList.matches(mark, BookmarkList.words("dawn city")));
        // A folder is found by its own name, not by its parent's.
        Bookmarks.Mark holiday = folder(dir.resolve("Holiday"), true, "");
        assertTrue(BookmarkList.matches(holiday, BookmarkList.words("holiday")));
        assertFalse(BookmarkList.matches(
                holiday, BookmarkList.words(dir.getFileName().toString())));
    }

    @Test
    void theSelectionStaysOnItsRowOrWhatTookItsPlace(@TempDir Path dir) {
        Path a = dir.resolve("a.jpg");
        Path b = dir.resolve("b.jpg");
        List<BookmarkList.Row> rows =
                BookmarkList.rows(List.of(image(a, true, ""), image(b, true, "")), "", BookmarkList.Show.ALL);
        assertEquals(2, BookmarkList.keep(rows, b, 0));
        // Removed: the row that took its place, else the last.
        assertEquals(1, BookmarkList.keep(rows, dir.resolve("gone.jpg"), 1));
        assertEquals(2, BookmarkList.keep(rows, dir.resolve("gone.jpg"), 7));
        assertEquals(-1, BookmarkList.keep(List.of(), a, 0));
    }

    @Test
    void theCountSaysOneOrMany() {
        assertEquals("1 image", BookmarksWindow.counted(1, "image"));
        assertEquals("0 folders", BookmarksWindow.counted(0, "folder"));
    }
}
