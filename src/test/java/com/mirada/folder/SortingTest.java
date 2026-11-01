package com.mirada.folder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SortingTest {

    @Test
    void byNameCountsAsAPersonDoes() {
        List<Path> images = List.of(Path.of("IMG_10.jpg"), Path.of("IMG_9.jpg"), Path.of("a.png"));
        assertEquals(
                List.of(Path.of("a.png"), Path.of("IMG_9.jpg"), Path.of("IMG_10.jpg")), Sorting.DEFAULT.sort(images));
        assertEquals(
                List.of(Path.of("IMG_10.jpg"), Path.of("IMG_9.jpg"), Path.of("a.png")),
                Sorting.DEFAULT.reverse().sort(images));
    }

    @Test
    void byDateAndSizeWithTheNameBreakingTies(@TempDir Path dir) throws IOException {
        Path old = write(dir, "b.jpg", 30, 1_000);
        Path recent = write(dir, "a.jpg", 10, 3_000);
        Path tie = write(dir, "c.jpg", 20, 3_000);
        List<Path> images = List.of(recent, tie, old);
        assertEquals(List.of(old, recent, tie), new Sorting(Sorting.By.DATE, false).sort(images));
        assertEquals(List.of(recent, tie, old), new Sorting(Sorting.By.SIZE, false).sort(images));
        assertEquals(List.of(old, tie, recent), new Sorting(Sorting.By.SIZE, true).sort(images));
    }

    @Test
    void anyOtherWordIsByName() {
        assertEquals(Sorting.By.DATE, Sorting.byOf(" Date "));
        assertEquals(Sorting.By.NAME, Sorting.byOf("colour"));
        assertEquals(Sorting.By.NAME, Sorting.byOf(null));
    }

    private static Path write(Path dir, String name, int bytes, long modifiedSeconds) throws IOException {
        Path file = Files.write(dir.resolve(name), new byte[bytes]);
        Files.setLastModifiedTime(file, FileTime.fromMillis(modifiedSeconds * 1000));
        return file;
    }
}
