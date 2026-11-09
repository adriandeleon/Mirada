package com.mirada.edit;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A damaged file never stops an edit from being saved: what an edit carries over from its source
 * (the EXIF, the colour profile, the XMP) is read from files cut short anywhere and with bytes
 * overwritten, and each reader either finds the block, finds none, or says why with an
 * IOException. Nothing else may escape, and {@code carried}, which the save calls, never throws.
 *
 * <p>The files are the fixtures of every container the readers walk: JPEG segments, PNG chunks,
 * WebP's RIFF, HEIF's boxes (iinf, iloc, ipma), JPEG XL's boxes.
 */
class DamagedFilesTest {

    @TempDir
    Path dir;

    private static final List<String> FIXTURES = List.of(
            "/com/mirada/edit/exif.png",
            "/com/mirada/edit/exif.webp",
            "/com/mirada/edit/exif.heic",
            "/com/mirada/edit/exif.avif",
            "/com/mirada/edit/profile.jpg",
            "/com/mirada/edit/profile.png",
            "/com/mirada/edit/profile.webp",
            "/com/mirada/edit/profile.heic",
            "/com/mirada/edit/profile.avif",
            "/com/mirada/image/exif.jpg",
            "/com/mirada/matrix/exif-heavy.jpg",
            "/com/mirada/matrix/orient-6.jpg",
            "/com/mirada/matrix/heic-grid.heic",
            "/com/mirada/matrix/jxl-rgb.jxl",
            "/com/mirada/matrix/png-rgba.png");

    private static byte[] fixture(String name) throws IOException, URISyntaxException {
        return Files.readAllBytes(Path.of(
                Objects.requireNonNull(DamagedFilesTest.class.getResource(name)).toURI()));
    }

    /** Reads {@code file} with every reader; returns what escaped that should not have. */
    private static List<String> escaped(Path file, String what) {
        List<String> escaped = new ArrayList<>();
        try {
            byte[] exif = ExifBlock.read(file);
            if (exif != null) ExifBlock.forEdited(exif, 10, 10);
        } catch (IOException fine) {
            // said why
        } catch (RuntimeException e) {
            escaped.add("ExifBlock " + what + ": " + e);
        }
        try {
            IccProfile.read(file);
        } catch (IOException fine) {
            // said why
        } catch (RuntimeException e) {
            escaped.add("IccProfile " + what + ": " + e);
        }
        try {
            byte[] xmp = Xmp.read(file);
            if (xmp != null) Xmp.forEdited(xmp, 10, 10);
        } catch (IOException fine) {
            // said why
        } catch (RuntimeException e) {
            escaped.add("Xmp " + what + ": " + e);
        }
        // What the save calls: never throws, whatever the file.
        ExifBlock.carried(file, 10, 10);
        IccProfile.carried(file);
        Xmp.carried(file, 10, 10);
        return escaped;
    }

    @Test
    void aFileCutShortAnywhereIsReadOrRefusedNeverThrownAt() throws Exception {
        List<String> escaped = new ArrayList<>();
        for (String name : FIXTURES) {
            byte[] whole = fixture(name);
            Path file = dir.resolve("cut");
            // Every length near the start, where the headers are, then every 1/97th of the rest.
            List<Integer> lengths = new ArrayList<>();
            for (int n = 0; n < Math.min(whole.length, 160); n++) lengths.add(n);
            for (int n = 160; n < whole.length; n += Math.max(1, whole.length / 97)) lengths.add(n);
            for (int length : lengths) {
                Files.write(file, java.util.Arrays.copyOf(whole, length));
                escaped.addAll(escaped(file, name + " cut at " + length));
            }
        }
        assertEquals(List.of(), escaped);
    }

    @Test
    void aFileWithBytesOverwrittenIsReadOrRefusedNeverThrownAt() throws Exception {
        List<String> escaped = new ArrayList<>();
        Random random = new Random(20261108);
        for (String name : FIXTURES) {
            byte[] whole = fixture(name);
            Path file = dir.resolve("damaged");
            for (int round = 0; round < 400; round++) {
                byte[] damaged = whole.clone();
                // Mostly in the headers and boxes, where a wrong length or offset does harm: a byte
                // set at random, to 0 or to 0xFF, or four set to 0xFF, as a length gone huge.
                int span = Math.min(damaged.length, round % 2 == 0 ? 512 : damaged.length);
                for (int hit = 0; hit < 1 + round % 4; hit++) {
                    int at = random.nextInt(span);
                    switch (round % 4) {
                        case 0 -> damaged[at] = (byte) 0xFF;
                        case 1 -> damaged[at] = 0;
                        case 2 -> {
                            for (int i = at; i < Math.min(damaged.length, at + 4); i++) damaged[i] = (byte) 0xFF;
                        }
                        default -> damaged[at] = (byte) random.nextInt(256);
                    }
                }
                Files.write(file, damaged);
                escaped.addAll(escaped(file, name + " round " + round));
            }
        }
        assertEquals(List.of(), escaped);
    }

    @Test
    void theWholeFixturesStillCarryWhatTheyHave() throws Exception {
        int carried = 0;
        for (String name : FIXTURES) {
            Path file = dir.resolve("whole");
            Files.write(file, fixture(name));
            if (ExifBlock.carried(file, 10, 10) != null) carried++;
            if (IccProfile.carried(file) != null) carried++;
            if (Xmp.carried(file, 10, 10) != null) carried++;
        }
        assertTrue(carried >= 10, "the fixtures carry their blocks: " + carried);
    }
}
