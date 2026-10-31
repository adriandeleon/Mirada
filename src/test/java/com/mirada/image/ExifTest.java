package com.mirada.image;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExifTest {

    @Test
    void orientationFollowsTheTagsOrder() {
        assertEquals(Orientation.NORMAL, Orientation.fromExif(1));
        assertEquals(Orientation.ROTATE_90, Orientation.fromExif(6));
        assertEquals(Orientation.ROTATE_270, Orientation.fromExif(8));
        assertEquals(Orientation.NORMAL, Orientation.fromExif(0));
        assertEquals(Orientation.NORMAL, Orientation.fromExif(9));
    }

    /**
     * Each orientation as "mirror, then turn clockwise", checked against the tag's definition by
     * where the stored image's top-left corner ends up (x right, y down, a unit square).
     */
    @Test
    void mirrorThenTurnPutsTheCornerWhereTheTagSays() {
        // Tag value -> where the stored top-left pixel is displayed.
        int[][] expected = {
            {0, 0}, // 1: top-left
            {1, 0}, // 2: mirrored, top-right
            {1, 1}, // 3: turned half, bottom-right
            {0, 1}, // 4: flipped vertically, bottom-left
            {0, 0}, // 5: transposed, top-left
            {1, 0}, // 6: turned clockwise, top-right
            {1, 1}, // 7: transverse, bottom-right
            {0, 1}, // 8: turned counterclockwise, bottom-left
        };
        for (int tag = 1; tag <= 8; tag++) {
            Orientation o = Orientation.fromExif(tag);
            double x = -0.5;
            double y = -0.5;
            if (o.mirrored()) x = -x;
            for (int t = 0; t < o.degrees() / 90; t++) {
                double turnedX = -y;
                y = x;
                x = turnedX;
            }
            assertEquals(expected[tag - 1][0], (int) Math.round(x + 0.5), "x for tag " + tag);
            assertEquals(expected[tag - 1][1], (int) Math.round(y + 0.5), "y for tag " + tag);
        }
    }

    @Test
    void summaryDropsAMakeTheModelRepeats() {
        assertEquals(
                "Canon EOS R6 · 1/250 s · f/4.0 · ISO 400 · 50 mm",
                Exif.summary("Canon", "Canon EOS R6", "1/250 sec", "f/4.0", "400", "50 mm"));
        assertEquals("Apple iPhone 15", Exif.summary("Apple", "iPhone 15", null, null, null, null));
    }

    @Test
    void summaryLeavesOutWhatIsMissing() {
        assertEquals("f/1.8 · ISO 80", Exif.summary(null, " ", null, "f/1.8", "80", ""));
        assertEquals("NIKON", Exif.summary("NIKON", null, null, null, null, null));
        assertEquals("", Exif.summary(null, null, null, null, null, null));
    }

    @Test
    void aFileWithoutMetadataIsUpright(@TempDir Path dir) throws IOException {
        Path junk = Files.writeString(dir.resolve("junk.jpg"), "not an image");
        assertEquals(Exif.NONE, Exif.read(junk));
        assertEquals(Exif.NONE, Exif.read(dir.resolve("missing.jpg")));
    }

    @Test
    void sidewaysTurnsAreTheOddQuarters() {
        assertTrue(Orientation.ROTATE_90.degrees() % 180 != 0);
        assertFalse(Orientation.FLIP_VERTICAL.degrees() % 180 != 0);
    }
}
