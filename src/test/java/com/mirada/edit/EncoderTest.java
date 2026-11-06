package com.mirada.edit;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import javax.imageio.ImageIO;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.icc.IccDirectory;
import com.drew.metadata.xmp.XmpDirectory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EncoderTest {

    @Test
    void writesWhatTheNameSaysAndReadsBack(@TempDir Path dir) throws IOException {
        Raster redBlue = new Raster(4, 2, new int[] {
            0xFFFF0000, 0xFFFF0000, 0xFF0000FF, 0xFF0000FF,
            0xFFFF0000, 0xFFFF0000, 0xFF0000FF, 0xFF0000FF,
        });
        for (String extension : new String[] {"png", "tif", "bmp"}) {
            Path file = dir.resolve("out." + extension);
            Encoder.write(redBlue, file);
            BufferedImage back = ImageIO.read(file.toFile());
            assertEquals(4, back.getWidth(), extension);
            assertEquals(0xFFFF0000, back.getRGB(0, 0), extension);
            assertEquals(0xFF0000FF, back.getRGB(3, 1), extension);
        }
        Path jpeg = dir.resolve("out.jpg");
        Encoder.write(redBlue, jpeg);
        assertEquals(4, ImageIO.read(jpeg.toFile()).getWidth());
    }

    @Test
    void transparencyStaysInPngAndGoesOverWhiteInJpeg(@TempDir Path dir) throws IOException {
        // Half-transparent pure blue, premultiplied: 0x80 alpha, 0x80 blue.
        Raster half = new Raster(1, 1, new int[] {0x80000080});
        Path png = dir.resolve("half.png");
        Encoder.write(half, png);
        assertEquals(0x80, ImageIO.read(png.toFile()).getRGB(0, 0) >>> 24);
        assertEquals(0xFF7F7FFF, Encoder.toBuffered(half, false).getRGB(0, 0));
    }

    @Test
    void formatsItCannotWriteAreRefusedAndReplaced(@TempDir Path dir) {
        assertNull(Encoder.formatFor(Path.of("a.heic")));
        assertThrows(IOException.class, () -> Encoder.write(new Raster(1, 1, new int[] {-1}), dir.resolve("a.webp")));
        assertEquals("jpg", Encoder.extensionFor(Path.of("IMG_1.heic"), true));
        assertEquals("png", Encoder.extensionFor(Path.of("sticker.webp"), false));
        assertEquals("PNG", Encoder.extensionFor(Path.of("shot.PNG"), true));
    }

    @Test
    void exifGoesIntoJpegAndPngAndReadsBack(@TempDir Path dir) throws Exception {
        byte[] block = ExifBlock.read(
                Path.of(Objects.requireNonNull(EncoderTest.class.getResource("/com/mirada/matrix/orient-6.jpg"))
                        .toURI()));
        assertNotNull(block);
        byte[] edited = ExifBlock.forEdited(block, 32, 16);
        assertNotNull(edited);
        // Red on the left, blue on the right: large enough that JPEG's colour blocks keep them apart.
        int[] pixels = new int[32 * 16];
        for (int i = 0; i < pixels.length; i++) pixels[i] = i % 32 < 16 ? 0xFFFF0000 : 0xFF0000FF;
        Raster image = new Raster(32, 16, pixels);
        for (String extension : new String[] {"jpg", "png"}) {
            Path file = dir.resolve("with-exif." + extension);
            Encoder.write(image, file, edited, null);
            Metadata metadata = ImageMetadataReader.readMetadata(file.toFile());
            ExifIFD0Directory ifd0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            assertNotNull(ifd0, extension);
            assertEquals(1, ifd0.getInt(ExifIFD0Directory.TAG_ORIENTATION), extension);
            BufferedImage back = ImageIO.read(file.toFile());
            assertEquals(32, back.getWidth(), extension);
            int right = back.getRGB(31, 15);
            assertTrue((right & 0xFF) > (right >> 16 & 0xFF), extension + " still decodes, blue on the right");
            assertArrayEquals(edited, ExifBlock.read(file), extension + " carries the block whole");
        }
        // BMP is written without it, and still written.
        Path bmp = dir.resolve("plain.bmp");
        Encoder.write(image, bmp, edited, null);
        assertEquals(32, ImageIO.read(bmp.toFile()).getWidth());
    }

    @Test
    void aTiffStartsWithTheExifBlockAndCarriesTheProfileAndXmp(@TempDir Path dir) throws Exception {
        byte[] block = ExifBlock.read(resource("/com/mirada/image/exif.jpg"));
        assertNotNull(block);
        assertEquals('M', block[0], "big-endian, so the TIFF is too");
        byte[] edited = ExifBlock.forEdited(block, 32, 16);
        assertNotNull(edited);
        byte[] p3 = Files.readAllBytes(resource("/com/mirada/edit/display-p3.icc"));
        byte[] xmp = Xmp.forEdited(XmpTest.packet(), 32, 16);
        int[] pixels = new int[32 * 16];
        for (int i = 0; i < pixels.length; i++) pixels[i] = i % 32 < 16 ? 0xFFFF0000 : 0xFF0000FF;
        Path file = dir.resolve("carried.tif");
        Encoder.write(new Raster(32, 16, pixels), file, edited, p3, xmp);

        BufferedImage back = ImageIO.read(file.toFile());
        assertEquals(32, back.getWidth());
        assertEquals(16, back.getHeight());
        assertEquals(0xFFFF0000, back.getRGB(0, 15));
        assertEquals(0xFF0000FF, back.getRGB(31, 0));

        Metadata metadata = ImageMetadataReader.readMetadata(file.toFile());
        ExifIFD0Directory ifd0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
        assertNotNull(ifd0);
        assertEquals("Canon EOS R6", ifd0.getString(ExifIFD0Directory.TAG_MODEL));
        assertEquals(32, ifd0.getInt(ExifIFD0Directory.TAG_IMAGE_WIDTH), "its own size, not the camera's");
        assertFalse(ifd0.containsTag(ExifIFD0Directory.TAG_ORIENTATION), "upright, TIFF's default");
        ExifSubIFDDirectory exif = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
        assertNotNull(exif, "the EXIF directory, reached through the block's own offset");
        assertTrue(exif.containsTag(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL));
        IccDirectory icc = metadata.getFirstDirectoryOfType(IccDirectory.class);
        assertNotNull(icc);
        assertEquals(p3.length, icc.getInt(IccDirectory.TAG_PROFILE_BYTE_COUNT));
        XmpDirectory found = metadata.getFirstDirectoryOfType(XmpDirectory.class);
        assertNotNull(found);
        assertEquals("1", found.getXmpProperties().get("tiff:Orientation"));
    }

    @Test
    void aTiffWithoutExifIsLittleEndianAndKeepsItsTransparency(@TempDir Path dir) throws IOException {
        // Wide enough for several strips: 3000 × 4 bytes a row, 64 KB a strip, 40 rows.
        int w = 3000;
        int h = 40;
        int[] pixels = new int[w * h];
        for (int i = 0; i < pixels.length; i++) pixels[i] = i / w < 20 ? 0xFF00FF00 : 0x80000080;
        Path file = dir.resolve("alpha.tif");
        Encoder.write(new Raster(w, h, pixels), file);
        byte[] bytes = Files.readAllBytes(file);
        assertEquals('I', bytes[0]);
        BufferedImage back = ImageIO.read(file.toFile());
        assertEquals(w, back.getWidth());
        assertEquals(0xFF00FF00, back.getRGB(w - 1, 0));
        int half = back.getRGB(0, h - 1);
        assertEquals(0x80, half >>> 24);
        assertEquals(0xFF, half & 0xFF, "straight, not premultiplied, blue");
    }

    private static Path resource(String name) throws Exception {
        return Path.of(Objects.requireNonNull(EncoderTest.class.getResource(name), name)
                .toURI());
    }
}
