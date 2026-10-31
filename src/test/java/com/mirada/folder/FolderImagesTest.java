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
}
