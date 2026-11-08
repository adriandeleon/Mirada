package com.mirada.image;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.imageio.ImageIO;

import com.drew.metadata.Metadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ThumbnailsTest {

    @TempDir
    Path folder;

    @Test
    void pastItsCapTheOldestThumbnailIsDroppedAndTheNewestKept() {
        Thumbnails thumbnails = new Thumbnails();
        try {
            // Files that are not there: each is a request, made or failed, and counts the same.
            List<Path> files = new ArrayList<>();
            for (int i = 0; i <= Thumbnails.CAPACITY; i++) files.add(folder.resolve("missing-" + i + ".jpg"));
            CompletableFuture<Thumbnails.Thumbnail> first = thumbnails.get(files.getFirst());
            CompletableFuture<Thumbnails.Thumbnail> second = thumbnails.get(files.get(1));
            for (Path file : files.subList(2, files.size())) thumbnails.get(file);
            assertSame(second, thumbnails.get(files.get(1)), "kept: the cap is not reached until the last");
            assertSame(thumbnails.get(files.getLast()), thumbnails.get(files.getLast()), "the newest is kept");
            assertNotSame(first, thumbnails.get(files.getFirst()), "the oldest was dropped and is asked for again");
        } finally {
            thumbnails.close();
        }
    }

    @Test
    void aPhonesThumbnailFitsItsPhoto() {
        assertTrue(Thumbnails.fits(512, 384, 4032, 3024));
        assertTrue(Thumbnails.fits(504, 376, 4032, 3024), "rounded to a multiple of 8");
        assertTrue(Thumbnails.fits(512, 288, 3840, 2160));
    }

    @Test
    void aThumbnailTooSmallOrOfAnotherShapeDoesNot() {
        assertFalse(Thumbnails.fits(120, 90, 4032, 3024), "smaller than the strip draws it");
        assertFalse(Thumbnails.fits(160, 120, 3840, 2160), "a 16:9 photo letterboxed into 4:3");
        assertFalse(Thumbnails.fits(384, 512, 4032, 3024), "turned the other way");
        assertFalse(Thumbnails.fits(512, 384, 3000, 3000), "left from before a crop");
        assertFalse(Thumbnails.fits(512, 384, 0, 0));
    }

    @Test
    void aQuarterOfTheCoresOneToFour() {
        assertEquals(1, Thumbnails.threads(1));
        assertEquals(1, Thumbnails.threads(4));
        assertEquals(2, Thumbnails.threads(8));
        assertEquals(4, Thumbnails.threads(24));
        assertEquals(4, Thumbnails.threads(64));
    }

    @Test
    void theCamerasThumbnailIsTakenWhenItFits() throws IOException {
        requireTurboJpeg();
        Path photo = photoWithThumbnail(800, 600, 200, 150);
        Metadata m = metadata(photo);
        for (var d : m.getDirectories()) {
            System.out.println("DBG " + d.getName() + " " + d.getTags() + " " + d.getErrors());
        }
        var t = m.getFirstDirectoryOfType(com.drew.metadata.exif.ExifThumbnailDirectory.class);
        if (t != null)
            System.out.println("DBG offset " + t.getAdjustedThumbnailOffset() + " start " + t.getExifStartOffset());
        ArgbPixels thumbnail = Thumbnails.embedded(photo, metadata(photo));
        assertNotNull(thumbnail);
        assertEquals(200, thumbnail.width());
        assertEquals(150, thumbnail.height());
        // Blue, the thumbnail's colour: the photo itself is red.
        int centre = thumbnail.pixels()[75 * 200 + 100];
        assertTrue((centre & 0xFF) > 200 && ((centre >> 16) & 0xFF) < 60, String.format("%08X", centre));
    }

    @Test
    void aThumbnailOfAnotherShapeIsPassedOver() throws IOException {
        requireTurboJpeg();
        Path photo = photoWithThumbnail(1600, 900, 200, 150);
        assertNull(Thumbnails.embedded(photo, metadata(photo)));
    }

    @Test
    void theExifBlockIsFoundBehindTheJfifSegment() throws IOException {
        Path photo = photoWithThumbnail(80, 60, 40, 30);
        // SOI (2), the APP1's marker and length (4), "Exif\0\0" (6).
        assertEquals(12, Thumbnails.exifTiff(photo));
        Path plain = folder.resolve("plain.jpg");
        Files.write(plain, jpeg(80, 60, Color.RED));
        assertEquals(-1, Thumbnails.exifTiff(plain));
        Path notJpeg = folder.resolve("not.jpg");
        Files.writeString(notJpeg, "not a jpeg");
        assertEquals(-1, Thumbnails.exifTiff(notJpeg));
    }

    private static Metadata metadata(Path photo) {
        Metadata metadata = Exif.metadata(photo);
        assertNotNull(metadata);
        return metadata;
    }

    private static void requireTurboJpeg() {
        boolean available = TurboJpegDecoder.available();
        if (!available && "1".equals(System.getenv("MIRADA_REQUIRE_CODECS"))) {
            fail("libjpeg-turbo did not load, and MIRADA_REQUIRE_CODECS=1");
        }
        assumeTrue(available, "libjpeg-turbo is not installed");
    }

    /**
     * A red JPEG with a blue thumbnail in its EXIF, as cameras write it: an APP1 after the SOI whose
     * TIFF block has IFD0 (the orientation) and IFD1 (compression 6, then where the thumbnail's JPEG
     * starts within the block and how long it is), the thumbnail's bytes after them.
     */
    private Path photoWithThumbnail(int width, int height, int thumbWidth, int thumbHeight) throws IOException {
        byte[] main = jpeg(width, height, Color.RED);
        byte[] thumbnail = jpeg(thumbWidth, thumbHeight, Color.BLUE);
        int ifd1 = 8 + 2 + 12 + 4;
        int data = ifd1 + 2 + 3 * 12 + 4;
        ByteBuffer tiff = ByteBuffer.allocate(data + thumbnail.length); // big-endian, as "MM" says
        tiff.put((byte) 'M').put((byte) 'M').putShort((short) 42).putInt(8);
        tiff.putShort((short) 1);
        entry(tiff, 0x0112, 3, 1 << 16); // orientation 1, a SHORT in the value's first half
        tiff.putInt(ifd1);
        tiff.putShort((short) 3);
        entry(tiff, 0x0103, 3, 6 << 16); // compression: JPEG
        entry(tiff, 0x0201, 4, data);
        entry(tiff, 0x0202, 4, thumbnail.length);
        tiff.putInt(0);
        tiff.put(thumbnail);
        byte[] exif = "Exif\0\0".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        int length = 2 + exif.length + tiff.capacity();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(main, 0, 2);
        out.write(0xFF);
        out.write(0xE1);
        out.write(length >> 8);
        out.write(length & 0xFF);
        out.write(exif);
        out.write(tiff.array());
        out.write(main, 2, main.length - 2);
        Path photo = folder.resolve(width + "x" + height + ".jpg");
        Files.write(photo, out.toByteArray());
        return photo;
    }

    private static void entry(ByteBuffer tiff, int tag, int type, int value) {
        tiff.putShort((short) tag).putShort((short) type).putInt(1).putInt(value);
    }

    private static byte[] jpeg(int width, int height, Color colour) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(colour);
        g.fillRect(0, 0, width, height);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return out.toByteArray();
    }
}
