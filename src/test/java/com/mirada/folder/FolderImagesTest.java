package com.mirada.folder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FolderImagesTest {

    @TempDir
    Path dir;

    private Path touch(String name) throws IOException {
        return Files.createFile(dir.resolve(name));
    }

    private static List<String> names(FolderImages folder) {
        return folder.images().stream().map(FolderImages::name).toList();
    }

    @Test
    void aFileOpensWithItsFolderInNameOrder() throws IOException {
        touch("IMG_10.jpg");
        Path opened = touch("IMG_9.JPG");
        touch("IMG_2.png");
        touch("notes.txt");
        Files.createDirectory(dir.resolve("sub.jpg"));

        FolderImages folder = FolderImages.open(opened);

        assertEquals(List.of("IMG_2.png", "IMG_9.JPG", "IMG_10.jpg"), names(folder));
        assertEquals(1, folder.index());
        assertEquals(opened, folder.current());
    }

    @Test
    void aFolderOpensAtItsFirstImage() throws IOException {
        touch("b.gif");
        touch("a.bmp");

        FolderImages folder = FolderImages.open(dir);

        assertEquals(List.of("a.bmp", "b.gif"), names(folder));
        assertEquals(0, folder.index());
    }

    @Test
    void hiddenFilesAreLeftOutUnlessOpenedByName() throws IOException {
        touch("photo.jpg");
        Path hidden = touch(".thumb.jpg");

        assertEquals(List.of("photo.jpg"), names(FolderImages.open(dir)));
        FolderImages opened = FolderImages.open(hidden);
        assertEquals(List.of(".thumb.jpg", "photo.jpg"), names(opened));
        assertEquals(hidden, opened.current());
    }

    @Test
    void anUnsupportedFileOpenedByNameIsKeptSoTheWindowCanSaySo() throws IOException {
        touch("a.jpg");
        Path webp = touch("b.webp");

        FolderImages folder = FolderImages.open(webp);

        assertEquals(List.of("a.jpg", "b.webp"), names(folder));
        assertEquals(webp, folder.current());
    }

    @Test
    void steppingStopsAtTheEnds() {
        FolderImages folder = FolderImages.select(List.of(Path.of("a.jpg"), Path.of("b.jpg"), Path.of("c.jpg")), null);

        assertEquals(0, folder.step(-1).index());
        assertEquals(2, folder.step(5).index());
        assertEquals(2, folder.last().index());
        assertEquals(0, folder.last().first().index());
    }

    @Test
    void neighboursAreNextThenPrevious() {
        Path a = Path.of("a.jpg");
        Path b = Path.of("b.jpg");
        Path c = Path.of("c.jpg");
        FolderImages folder = FolderImages.select(List.of(a, b, c), b);

        assertEquals(List.of(c, a), folder.neighbours());
        assertEquals(List.of(b), folder.first().neighbours());
        assertEquals(List.of(b), folder.last().neighbours());
    }

    @Test
    void emptyHasNoCurrent() {
        assertTrue(FolderImages.EMPTY.isEmpty());
        assertNull(FolderImages.EMPTY.current());
        assertEquals(FolderImages.EMPTY, FolderImages.EMPTY.step(1));
    }

    @Test
    void extensionsAreCaseBlind() {
        assertTrue(ImageFormats.isImage(Path.of("A.JPEG")));
        assertEquals("", ImageFormats.extension(Path.of(".jpg")));
        assertEquals("", ImageFormats.extension(Path.of("README")));
        assertEquals("webp", ImageFormats.extension(Path.of("x.WebP")));
    }

    @Test
    void aTrashedImageGivesWayToTheNextOrThePrevious() {
        Path a = Path.of("a.jpg");
        Path b = Path.of("b.jpg");
        Path c = Path.of("c.jpg");
        FolderImages onB = new FolderImages(List.of(a, b, c), 1);
        assertEquals(new FolderImages(List.of(a, c), 1), onB.without(b));
        assertEquals(new FolderImages(List.of(b, c), 0), onB.without(a));
        assertEquals(new FolderImages(List.of(a, b), 1), onB.without(c));
        FolderImages onC = new FolderImages(List.of(a, b, c), 2);
        assertEquals(new FolderImages(List.of(a, b), 1), onC.without(c));
        assertTrue(new FolderImages(List.of(a), 0).without(a).isEmpty());
        assertEquals(onB, onB.without(Path.of("elsewhere.jpg")));
    }

    @Test
    void aRenamedImageKeepsItsPlaceUntilTheFolderIsSortedAgain() {
        Path a = Path.of("a.jpg");
        Path b = Path.of("b.jpg");
        Path c = Path.of("c.jpg");
        Path z = Path.of("z.jpg");
        FolderImages onA = new FolderImages(List.of(a, b, c), 0);
        FolderImages renamed = onA.renamed(a, z);
        assertEquals(List.of(z, b, c), renamed.images());
        assertEquals(z, renamed.current());
        // The sort, made off the FX thread, puts it in its place and the window stays on it.
        FolderImages sorted = renamed.reordered(Sorting.DEFAULT.sort(renamed.images()));
        assertEquals(List.of(b, c, z), sorted.images());
        assertEquals(z, sorted.current());
        assertEquals(a, onA.renamed(b, Path.of("0.jpg")).current());
        assertEquals(onA, onA.renamed(Path.of("elsewhere.jpg"), z));
    }

    @Test
    void aSortMadeOffTheFxThreadKeepsWhatChangedMeanwhile() {
        Path a = Path.of("a.jpg");
        Path b = Path.of("b.jpg");
        Path c = Path.of("c.jpg");
        Path d = Path.of("d.jpg");
        // Sorted from a, b, c; meanwhile c was trashed and d came, and the window stepped to b.
        FolderImages now = new FolderImages(List.of(a, b, d), 1);
        FolderImages sorted = now.reordered(List.of(c, b, a));
        assertEquals(List.of(b, a, d), sorted.images(), "gone left out, new last");
        assertEquals(b, sorted.current());
        assertEquals(FolderImages.EMPTY, FolderImages.EMPTY.reordered(List.of(a)));
    }

    @Test
    void reSortingKeepsTheImageShowing() {
        Path a = Path.of("a.jpg");
        Path b = Path.of("b.jpg");
        FolderImages onA = new FolderImages(List.of(a, b), 0);
        FolderImages reversed = onA.sorted(Sorting.DEFAULT.reverse());
        assertEquals(List.of(b, a), reversed.images());
        assertEquals(a, reversed.current());
    }
}
