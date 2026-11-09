package com.mirada.settings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BookmarksTest {

    @Test
    void aBookmarkAndANoteAreKeptAcrossStarts(@TempDir Path dir) {
        Path file = dir.resolve("bookmarks.properties");
        Bookmarks bookmarks = new Bookmarks(file);
        Path photo = dir.resolve("photos/IMG_0001.jpg");
        assertTrue(bookmarks.toggle(photo, false));
        bookmarks.setNote(photo, false, "  The lake at dawn\nprint this one \n");
        bookmarks.setNote(dir.resolve("photos"), true, "Holiday");

        Bookmarks again = new Bookmarks(file);
        assertTrue(again.isBookmarked(photo));
        assertEquals("The lake at dawn\nprint this one", again.note(photo));
        Bookmarks.Mark folder = again.get(dir.resolve("photos"));
        assertTrue(folder != null && folder.folder() && !folder.bookmarked());
        assertEquals("Holiday", again.note(dir.resolve("photos/./")));
    }

    @Test
    void toggleTakesTheBookmarkOffButKeepsTheNote(@TempDir Path dir) {
        Bookmarks bookmarks = new Bookmarks(dir.resolve("bookmarks.properties"));
        Path photo = dir.resolve("a.jpg");
        bookmarks.toggle(photo, false);
        bookmarks.setNote(photo, false, "keep");
        assertFalse(bookmarks.toggle(photo, false));
        assertFalse(bookmarks.isBookmarked(photo));
        assertEquals("keep", bookmarks.note(photo));
        // With neither, nothing is kept.
        bookmarks.setNote(photo, false, "   ");
        assertNull(bookmarks.get(photo));
        assertTrue(bookmarks.all().isEmpty());
    }

    @Test
    void theLatestBookmarkComesFirst(@TempDir Path dir) {
        Bookmarks bookmarks = new Bookmarks(dir.resolve("bookmarks.properties"));
        Path a = dir.resolve("a.jpg");
        Path b = dir.resolve("b.jpg");
        bookmarks.toggle(a, false);
        bookmarks.toggle(b, false);
        assertEquals(List.of(b, a), paths(bookmarks.all()));
        // Bookmarked again, it is the latest again; a note leaves its place alone.
        bookmarks.toggle(a, false);
        bookmarks.toggle(a, false);
        bookmarks.setNote(b, false, "note");
        assertEquals(List.of(a, b), paths(bookmarks.all()));
    }

    @Test
    void aRenameCarriesTheMarkInItsPlace(@TempDir Path dir) {
        Bookmarks bookmarks = new Bookmarks(dir.resolve("bookmarks.properties"));
        Path a = dir.resolve("a.jpg");
        Path b = dir.resolve("b.jpg");
        bookmarks.toggle(a, false);
        bookmarks.setNote(a, false, "first");
        bookmarks.toggle(b, false);
        Path renamed = dir.resolve("lake.jpg");
        bookmarks.moved(a, renamed);
        assertNull(bookmarks.get(a));
        assertEquals("first", bookmarks.note(renamed));
        assertTrue(bookmarks.isBookmarked(renamed));
        assertEquals(List.of(b, renamed), paths(bookmarks.all()));
    }

    @Test
    void twoWindowsAddToEachOthersMarks(@TempDir Path dir) {
        Path file = dir.resolve("bookmarks.properties");
        Bookmarks one = new Bookmarks(file);
        Bookmarks two = new Bookmarks(file);
        one.ensureLoaded();
        two.ensureLoaded();
        one.toggle(dir.resolve("a.jpg"), false);
        two.toggle(dir.resolve("b.jpg"), false);
        one.load();
        assertEquals(Set.of(dir.resolve("a.jpg"), dir.resolve("b.jpg")), Set.copyOf(paths(one.all())));
    }

    @Test
    void aHandEditedFileIsReadAsWellAsItCanBe(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("bookmarks.properties");
        // Forward slashes: a backslash is an escape in a properties file, and Windows reads either.
        String photos = dir.toString().replace('\\', '/') + "/photos";
        Files.writeString(file, """
                mark.7.path=%1$s/later.jpg
                mark.7.bookmark=TRUE
                mark.2.path=%1$s/first.jpg
                mark.2.note=gaps and order are fine
                mark.3.bookmark=true
                mark.4.path=%1$s/nothing.jpg
                mark.4.bookmark=no
                mark.x.path=%1$s/not-a-number.jpg
                mark.5.path=%1$s/later.jpg
                mark.5.note=an older copy
                mark.9.path=
                mark.9.bookmark=true
                """.formatted(photos));
        Bookmarks bookmarks = new Bookmarks(file);
        Path later = dir.resolve("photos/later.jpg");
        Path first = dir.resolve("photos/first.jpg");
        // Numbered order, the oldest first: the copy at 7 wins over 5, in 7's place.
        assertEquals(List.of(later, first), paths(bookmarks.all()));
        assertTrue(bookmarks.isBookmarked(later));
        assertEquals("", bookmarks.note(later));
        assertEquals("gaps and order are fine", bookmarks.note(first));
    }

    @Test
    void anUnreadableFileIsNoMarks(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("bookmarks.properties");
        Files.write(file, new byte[] {'m', '=', '\\', 'u', '1'}); // a malformed \\uxxxx escape
        assertTrue(new Bookmarks(file).all().isEmpty());
        assertTrue(new Bookmarks(dir.resolve("missing.properties")).all().isEmpty());
    }

    @Test
    void theFileIsNumberedFromZeroTheOldestFirst(@TempDir Path dir) {
        Properties properties = Bookmarks.propertiesOf(List.of(
                new Bookmarks.Mark(dir, true, false, "folder note"),
                new Bookmarks.Mark(dir.resolve("b.jpg"), false, true, "")));
        assertEquals(dir.toAbsolutePath().toString(), properties.getProperty("mark.0.path"));
        assertEquals("true", properties.getProperty("mark.0.folder"));
        assertNull(properties.getProperty("mark.0.bookmark"));
        assertEquals("folder note", properties.getProperty("mark.0.note"));
        assertEquals("true", properties.getProperty("mark.1.bookmark"));
        assertNull(properties.getProperty("mark.1.note"));
        assertEquals(2, Bookmarks.marksOf(properties).size());
    }

    @Test
    void stepFindsTheNextMarkedImageEitherWayWithoutWrapping() {
        List<Path> images = List.of(Path.of("0"), Path.of("1"), Path.of("2"), Path.of("3"), Path.of("4"));
        Set<Path> marked = Set.of(Path.of("1"), Path.of("3"));
        assertEquals(1, Bookmarks.step(images, -1, 1, marked::contains));
        assertEquals(3, Bookmarks.step(images, 1, 1, marked::contains));
        assertEquals(-1, Bookmarks.step(images, 3, 1, marked::contains));
        assertEquals(1, Bookmarks.step(images, 3, -1, marked::contains));
        assertEquals(-1, Bookmarks.step(images, 1, -1, marked::contains));
        assertEquals(-1, Bookmarks.step(List.of(), 0, 1, marked::contains));
    }

    @Test
    void theFileSitsBesideTheSettings() {
        assertEquals(
                Path.of("/config/Mirada/bookmarks.properties"),
                Bookmarks.fileBeside(Path.of("/config/Mirada/settings.properties")));
    }

    private static List<Path> paths(List<Bookmarks.Mark> marks) {
        return marks.stream().map(Bookmarks.Mark::path).toList();
    }

    @Test
    void removeTakesTheBookmarkAndTheNoteTogether(@TempDir Path dir) {
        Path file = dir.resolve("bookmarks.properties");
        Bookmarks bookmarks = new Bookmarks(file);
        Path photo = dir.resolve("a.jpg");
        bookmarks.toggle(photo, false);
        bookmarks.setNote(photo, false, "keep?");
        int[] changes = {0};
        bookmarks.addListener(() -> changes[0]++);
        assertTrue(bookmarks.remove(photo));
        assertNull(new Bookmarks(file).get(photo));
        assertFalse(bookmarks.remove(photo));
        assertEquals(1, changes[0]);
    }

    // ---- Off the FX thread (PERF-01), under a generation guard (PERF-03) ----

    /** Marks read and written on a held thread, delivered on another: the application's shape. */
    private static Bookmarks held(Path file, FileWritesTest.Held io, FileWritesTest.Held fx) {
        return new Bookmarks(file, io, fx);
    }

    @Test
    void aChangeShowsAtOnceAndIsWrittenOnTheFilesThread(@TempDir Path dir) {
        FileWritesTest.Held io = new FileWritesTest.Held();
        FileWritesTest.Held fx = new FileWritesTest.Held();
        Path file = dir.resolve("bookmarks.properties");
        Bookmarks bookmarks = held(file, io, fx);
        int[] changes = {0};
        bookmarks.addListener(() -> changes[0]++);
        Path photo = dir.resolve("a.jpg");

        assertTrue(bookmarks.toggle(photo, false), "bookmarked at once");
        assertTrue(bookmarks.isBookmarked(photo));
        assertEquals(1, changes[0]);
        assertFalse(Files.exists(file), "nothing read or written on the caller's thread");
        assertFalse(bookmarks.loaded());

        io.runAll();
        assertTrue(new Bookmarks(file).isBookmarked(photo), "written on the files' thread");
        fx.runAll();
        assertTrue(bookmarks.loaded());
        assertTrue(bookmarks.isBookmarked(photo));
    }

    @Test
    void aReadOvertakenByAChangeIsDroppedAndTheChangesReadIsKept(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("bookmarks.properties");
        Path other = dir.resolve("other.jpg");
        new Bookmarks(file).toggle(other, false); // another window's mark, already in the file
        FileWritesTest.Held io = new FileWritesTest.Held();
        FileWritesTest.Held fx = new FileWritesTest.Held();
        Bookmarks bookmarks = held(file, io, fx);
        Path photo = dir.resolve("a.jpg");

        bookmarks.ensureLoaded();
        io.runAll(); // the read is done, its result on its way
        bookmarks.setNote(photo, false, "mine"); // asked before that result arrives
        io.runAll();
        fx.runAll();

        assertTrue(bookmarks.loaded());
        assertEquals("mine", bookmarks.note(photo), "the stale read did not undo the note");
        assertTrue(bookmarks.isBookmarked(other), "the other window's mark, from the change's own read");
        assertEquals(Set.of(photo, other), Set.copyOf(paths(bookmarks.all())));
    }

    @Test
    void onlyTheLatestRequestsResultIsTaken() {
        assertTrue(Bookmarks.current(4, 4));
        assertFalse(Bookmarks.current(3, 4));
    }
}
