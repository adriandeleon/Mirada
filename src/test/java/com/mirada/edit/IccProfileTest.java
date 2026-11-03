package com.mirada.edit;

import java.awt.color.ColorSpace;
import java.awt.color.ICC_Profile;
import java.awt.image.BufferedImage;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;
import javax.imageio.ImageIO;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.icc.IccDirectory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The fixtures carry display-p3.icc, a Display P3 profile built by hand for these tests (ICC 4.3,
 * matrix and sRGB curve, 560 bytes), attached with ImageMagick's {@code -profile} to a red and green
 * picture in each format.
 */
class IccProfileTest {

    private static Path fixture(String name) throws URISyntaxException {
        return Path.of(Objects.requireNonNull(IccProfileTest.class.getResource(name), name)
                .toURI());
    }

    private static byte[] p3() throws Exception {
        return Files.readAllBytes(fixture("display-p3.icc"));
    }

    @Test
    void readsTheProfileFromEveryFormatThatCarriesOne() throws Exception {
        byte[] p3 = p3();
        for (String name :
                new String[] {"profile.jpg", "profile.png", "profile.webp", "profile.heic", "profile.avif"}) {
            assertArrayEquals(p3, IccProfile.read(fixture(name)), name);
        }
    }

    @Test
    void aFileWithoutOneHasNone() throws Exception {
        assertNull(IccProfile.read(fixture("/com/mirada/image/exif.jpg")));
        assertNull(IccProfile.read(fixture("exif.png")));
        assertNull(IccProfile.read(fixture("exif.webp")));
        // libheif writes colour as nclx codes when there is no profile: nothing to carry.
        assertNull(IccProfile.read(fixture("exif.heic")));
        assertNull(IccProfile.carried(fixture("/com/mirada/matrix/orient-6.jpg")));
    }

    @Test
    void onlyAnRgbProfileIsCarried() throws Exception {
        byte[] rgb = p3();
        assertArrayEquals(rgb, IccProfile.rgb(rgb));
        byte[] cmyk = rgb.clone();
        System.arraycopy("CMYK".getBytes(), 0, cmyk, 16, 4);
        assertNull(IccProfile.rgb(cmyk), "a CMYK JPEG's pixels are converted to RGB on the way in");
        byte[] grey = ICC_Profile.getInstance(ColorSpace.CS_GRAY).getData();
        assertNull(IccProfile.rgb(grey));
        byte[] notAProfile = rgb.clone();
        notAProfile[36] = 'x';
        assertNull(IccProfile.rgb(notAProfile));
        assertNull(IccProfile.rgb(Arrays.copyOf(rgb, rgb.length - 1)), "shorter than its header says");
        byte[] padded = Arrays.copyOf(rgb, rgb.length + 3);
        assertArrayEquals(rgb, IccProfile.rgb(padded), "trimmed to the size its header gives");
    }

    @Test
    void theProfileGoesIntoJpegAndPngAndReadsBack(@TempDir Path dir) throws Exception {
        byte[] p3 = p3();
        byte[] exif = ExifBlock.forEdited(
                Objects.requireNonNull(ExifBlock.read(fixture("/com/mirada/matrix/orient-6.jpg"))), 32, 16);
        assertNotNull(exif);
        int[] pixels = new int[32 * 16];
        for (int i = 0; i < pixels.length; i++) pixels[i] = i % 32 < 16 ? 0xFFFF0000 : 0xFF00FF00;
        Raster image = new Raster(32, 16, pixels);
        for (String extension : new String[] {"jpg", "png"}) {
            Path file = dir.resolve("with-profile." + extension);
            Encoder.write(image, file, exif, p3);
            assertArrayEquals(p3, IccProfile.read(file), extension);
            assertArrayEquals(exif, ExifBlock.read(file), extension + " keeps the EXIF beside it");
            Metadata metadata = ImageMetadataReader.readMetadata(file.toFile());
            IccDirectory icc = metadata.getFirstDirectoryOfType(IccDirectory.class);
            assertNotNull(icc, extension);
            String description = icc.getDescription(IccDirectory.TAG_TAG_desc);
            assertTrue(description != null && description.contains("Display P3 (Mirada test)"), extension);
            assertNotNull(metadata.getFirstDirectoryOfType(ExifIFD0Directory.class), extension);
            BufferedImage back = ImageIO.read(file.toFile());
            assertEquals(32, back.getWidth(), extension + " still decodes");
        }
        Path bmp = dir.resolve("plain.bmp");
        Encoder.write(image, bmp, exif, p3);
        assertNull(IccProfile.read(bmp));
        assertEquals(32, ImageIO.read(bmp.toFile()).getWidth());
    }

    @Test
    void aLargeProfileTakesSeveralSegmentsAndComesBackWhole(@TempDir Path dir) throws Exception {
        // A profile with tables in it can pass 64 KB; this one is padded to three segments' worth.
        byte[] large = Arrays.copyOf(p3(), 2 * Encoder.ICC_CHUNK + 1000);
        ByteBuffer.wrap(large).putInt(0, large.length);
        Path file = dir.resolve("large.jpg");
        Encoder.write(new Raster(2, 2, new int[] {-1, -1, -1, -1}), file, null, large);
        assertArrayEquals(large, IccProfile.read(file));
        int segments = 0;
        byte[] jpeg = Files.readAllBytes(file);
        for (int at = 2; (jpeg[at + 1] & 0xFF) != 0xDA; at += 2 + ((jpeg[at + 2] & 0xFF) << 8 | jpeg[at + 3] & 0xFF)) {
            if ((jpeg[at + 1] & 0xFF) == 0xE2) segments++;
        }
        assertEquals(3, segments);
    }

    @Test
    void aJpegMissingOneOfItsSegmentsHasNone(@TempDir Path dir) throws Exception {
        byte[] large = Arrays.copyOf(p3(), Encoder.ICC_CHUNK + 1000);
        ByteBuffer.wrap(large).putInt(0, large.length);
        byte[] jpeg = Encoder.withSegments(Files.readAllBytes(fixture("/com/mirada/image/exif.jpg")), null, large);
        // Turn the second APP2 into an APP3, which no reader takes for a profile.
        int found = 0;
        for (int at = 2; (jpeg[at + 1] & 0xFF) != 0xDA; at += 2 + ((jpeg[at + 2] & 0xFF) << 8 | jpeg[at + 3] & 0xFF)) {
            if ((jpeg[at + 1] & 0xFF) == 0xE2 && ++found == 2) jpeg[at + 1] = (byte) 0xE3;
        }
        assertEquals(2, found);
        Path file = dir.resolve("half.jpg");
        Files.write(file, jpeg);
        assertNull(IccProfile.read(file));
        assertTrue(Files.size(file) > Encoder.ICC_CHUNK);
    }
}
