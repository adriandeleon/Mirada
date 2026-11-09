package com.mirada.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import com.mirada.folder.Sorting;
import com.mirada.settings.Bookmarks;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the window reads from the disk, as the plain methods its background threads run (PERF-01): which
 * marks and recent folders are still there, and a folder listed and sorted.
 */
class OffTheFxThreadTest {

    @Test
    void theMarksStillThereAreTheOnesGoToBookmarkOffers(@TempDir Path dir) throws Exception {
        Path photo = Files.writeString(dir.resolve("a.jpg"), "");
        Bookmarks.Mark image = new Bookmarks.Mark(photo, false, true, "");
        Bookmarks.Mark folder = new Bookmarks.Mark(dir, true, true, "");
        Bookmarks.Mark gone = new Bookmarks.Mark(dir.resolve("gone.jpg"), false, true, "");
        Bookmarks.Mark notAFolder = new Bookmarks.Mark(photo, true, false, "a note");
        List<Bookmarks.Mark> marks = List.of(image, folder, gone, notAFolder);
        assertEquals(List.of(image, folder), ViewerWindow.stillThere(marks));
        assertEquals(
                Set.of(gone.path(), notAFolder.path()),
                BookmarksWindow.gone(List.of(gone, notAFolder)),
                "the Bookmarks window dims the same ones");
        assertEquals(Set.of(), BookmarksWindow.gone(List.of(image, folder)));
    }

    @Test
    void theRecentFoldersStillThereButTheOneOnScreen(@TempDir Path dir) throws Exception {
        Path here = Files.createDirectories(dir.resolve("here"));
        Path other = Files.createDirectories(dir.resolve("other"));
        Path gone = dir.resolve("gone");
        assertEquals(List.of(other), ViewerWindow.recentFolders(List.of(here, gone, other), here));
        assertEquals(List.of(here, other), ViewerWindow.recentFolders(List.of(here, other), null));
    }

    @Test
    void aFolderIsListedSortedOrToldWhyNot(@TempDir Path dir) throws Exception {
        Path b = Files.writeString(dir.resolve("b.jpg"), "bb");
        Path a = Files.writeString(dir.resolve("a.jpg"), "a");
        ViewerWindow.Listed listed = ViewerWindow.Listed.of(b, Sorting.DEFAULT);
        assertNull(listed.error());
        assertEquals(List.of(a, b), listed.images().images());
        assertEquals(b, listed.images().current());
        ViewerWindow.Listed bySize =
                ViewerWindow.Listed.of(dir, Sorting.DEFAULT.by(Sorting.By.SIZE).reverse());
        assertEquals(List.of(b, a), bySize.images().images(), "the stats read where it is listed");
        ViewerWindow.Listed missing = ViewerWindow.Listed.of(dir.resolve("none/x.jpg"), Sorting.DEFAULT);
        assertTrue(missing.images().isEmpty());
        assertTrue(missing.error() != null && missing.error().contains("none"), String.valueOf(missing.error()));
    }
}
