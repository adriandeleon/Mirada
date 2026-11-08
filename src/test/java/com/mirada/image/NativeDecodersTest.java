package com.mirada.image;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import javafx.scene.image.WritableImage;

import com.mirada.edit.ExifBlock;
import com.mirada.edit.IccProfile;
import com.mirada.folder.ImageFormats;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * libheif and libjxl, where the machine has them. Elsewhere the tests skip themselves — except
 * where {@code MIRADA_REQUIRE_CODECS=1} is set, as CI sets it on Linux and macOS, so a library that
 * stopped loading fails the build instead of quietly skipping. The fixtures are left half red, right half
 * blue; HEIC and AVIF are lossy, so their colours are compared loosely.
 */
@Tag("fx")
class NativeDecodersTest {

    @BeforeAll
    static void toolkit() throws InterruptedException {
        FxToolkit.start();
    }

    private static Path fixture(String name) throws URISyntaxException {
        return Path.of(Objects.requireNonNull(NativeDecodersTest.class.getResource(name), name)
                .toURI());
    }

    private static void assertRedThenBlue(WritableImage image, int tolerance) {
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        assertNear(0xFFFF0000, image.getPixelReader().getArgb(1, height / 2), tolerance);
        assertNear(0xFF0000FF, image.getPixelReader().getArgb(width - 2, height / 2), tolerance);
    }

    private static void assertNear(int expected, int actual, int tolerance) {
        for (int shift = 0; shift <= 24; shift += 8) {
            int want = (expected >>> shift) & 0xFF;
            int got = (actual >>> shift) & 0xFF;
            assertTrue(Math.abs(want - got) <= tolerance, String.format("expected %08X, got %08X", expected, actual));
        }
    }

    private static void require(boolean available, String library) {
        if (!available && "1".equals(System.getenv("MIRADA_REQUIRE_CODECS"))) {
            fail(library + " did not load, and MIRADA_REQUIRE_CODECS=1");
        }
        assumeTrue(available, library + " is not installed");
    }

    @Test
    void decodesHeic() throws Exception {
        require(HeifDecoder.available(), "libheif");
        WritableImage image = HeifDecoder.decode(fixture("red-blue.heic"));
        assertEquals(32, image.getWidth());
        assertEquals(8, image.getHeight());
        assertRedThenBlue(image, 16);
    }

    /**
     * An iPhone's HEIC: a grid of tiles whose last column and row are cut off, turned upright by irot
     * and tagged with EXIF orientation 6 as well, its profile on the tiles rather than the grid
     * (scripts/write-heic-grid.py). Upright it is 48 × 32, red on the left and blue on the right.
     */
    @Test
    void decodesAnIphoneStyleGridTurnedOnce() throws Exception {
        require(HeifDecoder.available(), "libheif");
        Path grid = fixture("/com/mirada/matrix/heic-grid.heic");
        WritableImage image = HeifDecoder.decode(grid);
        assertEquals(48, image.getWidth(), "cropped to the grid's size, and turned by irot");
        assertEquals(32, image.getHeight());
        // Each corner, so every tile is where it belongs, the cut-off ones included.
        for (int y : new int[] {1, 30}) {
            assertNear(0xFFD0302A, image.getPixelReader().getArgb(1, y), 12);
            assertNear(0xFFD0302A, image.getPixelReader().getArgb(22, y), 12);
            assertNear(0xFF2A5FD0, image.getPixelReader().getArgb(25, y), 12);
            assertNear(0xFF2A5FD0, image.getPixelReader().getArgb(46, y), 12);
        }
        // The EXIF says turn it too; libheif already has, so the viewer must not again.
        assertEquals(Orientation.ROTATE_90, Exif.read(grid).orientation());
        assertTrue(ImageFormats.Decoder.LIBHEIF.turnsUpright());
        assertEquals(Orientation.NORMAL, Exif.read(grid).upright().orientation());
        // What a saved edit carries: the EXIF, and the tiles' profile.
        assertTrue(ExifBlock.read(grid) != null);
        assertArrayEquals(Files.readAllBytes(fixture("/com/mirada/edit/display-p3.icc")), IccProfile.read(grid));
    }

    @Test
    void decodesAvif() throws Exception {
        require(HeifDecoder.available(), "libheif");
        WritableImage image = HeifDecoder.decode(fixture("red-blue.avif"));
        assertEquals(32, image.getWidth());
        assertEquals(8, image.getHeight());
        assertRedThenBlue(image, 16);
    }

    @Test
    void decodesJpegXl() throws Exception {
        require(JxlDecoder.available(), "libjxl");
        WritableImage image = JxlDecoder.decode(fixture("red-blue.jxl"));
        assertEquals(4, image.getWidth());
        assertEquals(2, image.getHeight());
        assertRedThenBlue(image, 0);
    }

    @Test
    void decodesWebp() throws Exception {
        require(WebpDecoder.available(), "libwebp");
        WritableImage image = WebpDecoder.decode(fixture("red-blue.webp"));
        assertRedThenBlue(image, 24);
    }

    @Test
    void decodesJpeg() throws Exception {
        require(TurboJpegDecoder.available(), "libjpeg-turbo");
        WritableImage image = TurboJpegDecoder.decode(fixture("red-blue.jpg"));
        assertRedThenBlue(image, 24);
    }

    /** At an eighth of the size when that still covers what was asked for, in the DCT. */
    @Test
    void decodesJpegSmaller() throws Exception {
        require(TurboJpegDecoder.available(), "libjpeg-turbo");
        Path jpeg = fixture("red-blue.jpg");
        int fullWidth = (int) TurboJpegDecoder.decode(jpeg).getWidth();
        ArgbPixels half = TurboJpegDecoder.pixels(jpeg, (fullWidth + 1) / 2);
        assertEquals((fullWidth + 1) / 2, half.width());
    }

    @Test
    void denominatorIsTheLargestThatStillCovers() {
        assertEquals(8, TurboJpegDecoder.denominator(6000, 256));
        assertEquals(4, TurboJpegDecoder.denominator(6000, 1000));
        assertEquals(2, TurboJpegDecoder.denominator(6000, 2000));
        assertEquals(1, TurboJpegDecoder.denominator(6000, 4000));
        assertEquals(1, TurboJpegDecoder.denominator(6000, 0));
        assertEquals(8, TurboJpegDecoder.denominator(7, 1)); // rounded up, as TJSCALED is
    }

    /** CMYK is left to JavaFX: an IOException, which ImageCache answers with JavaFX's decoder. */
    @Test
    void leavesCmykToJavaFx() throws Exception {
        require(TurboJpegDecoder.available(), "libjpeg-turbo");
        Path cmyk = Path.of(NativeDecodersTest.class
                        .getResource("/com/mirada/matrix")
                        .toURI())
                .resolve("jpeg-cmyk.jpg");
        assumeTrue(Files.exists(cmyk), "no CMYK fixture");
        assertThrows(IOException.class, () -> TurboJpegDecoder.pixels(cmyk));
    }

    /** With a bundle present (scripts/build-codecs.sh, or the downloaded release), it is what loads. */
    @Test
    void theBundleWinsOverTheSystem() {
        String bundle = System.getProperty(NativeLibraries.BUNDLE_PROPERTY);
        assumeTrue(bundle != null && Files.isDirectory(Path.of(bundle)), "no codec bundle in " + bundle);
        require(HeifDecoder.available(), "libheif");
        require(JxlDecoder.available(), "libjxl");
        String heif = NativeLibraries.fileNames("heif", List.of("1")).getFirst();
        String jxl = NativeLibraries.fileNames("jxl", JxlDecoder.VERSIONS).getFirst();
        assertEquals("bundle", NativeLibraries.origin(heif).orElse("not loaded"), heif);
        assertEquals("bundle", NativeLibraries.origin(jxl).orElse("not loaded"), jxl);
    }

    @Test
    void brokenFilesAreIoExceptions(@TempDir Path dir) throws IOException {
        Path heic = Files.writeString(dir.resolve("broken.heic"), "not a heic");
        Path jxl = Files.writeString(dir.resolve("broken.jxl"), "not a jxl");
        if (HeifDecoder.available()) assertThrows(IOException.class, () -> HeifDecoder.decode(heic));
        if (JxlDecoder.available()) assertThrows(IOException.class, () -> JxlDecoder.decode(jxl));
    }

    @Test
    void libraryFileNamesPerPlatform() {
        assertEquals(List.of("libheif.so.1"), NativeLibraries.fileNames("Linux", "heif", List.of("1")));
        assertEquals(
                List.of("libjxl.0.11.dylib", "libjxl.0.7.dylib"),
                NativeLibraries.fileNames("Mac OS X", "jxl", List.of("0.11", "0.7")));
        assertEquals(List.of("heif.dll", "libheif.dll"), NativeLibraries.fileNames("Windows 11", "heif", List.of("1")));
    }

    @Test
    void theBundleIsFoundBesideANativeImage() {
        assertEquals(Path.of("/opt/x/codecs"), NativeLibraries.bundleFolder("/opt/x/codecs", "runtime", "/bin/mirada"));
        // Absolute as the platform makes it: Windows adds the drive letter.
        assertEquals(
                Path.of("/opt/mirada").toAbsolutePath().resolve("codecs"),
                NativeLibraries.bundleFolder(null, "runtime", "/opt/mirada/mirada"));
        // On the JVM the property is the only source: the executable is java itself.
        assertEquals(null, NativeLibraries.bundleFolder(null, null, "/usr/bin/java"));
        assertEquals(null, NativeLibraries.bundleFolder(" ", "runtime", null));
    }

    /**
     * The edit fixtures are rgb(200, 100, 50) tagged Display P3. A decoder that keeps the file's
     * colours hands those values back, so a saved edit can carry the profile; ImageIO's WebP reader
     * converts them to sRGB (about 215, 93, 31), which is why {@code keepsColours} is false for it.
     */
    @Test
    void theDecodersThatKeepColoursLeaveTheProfileUnapplied() throws Exception {
        require(TurboJpegDecoder.available(), "libjpeg-turbo");
        require(WebpDecoder.available(), "libwebp");
        require(HeifDecoder.available(), "libheif");
        int tone = 0xFFC86432;
        assertNear(tone, centre(TurboJpegDecoder.pixels(fixture("/com/mirada/edit/profile.jpg"))), 4);
        assertNear(tone, centre(WebpDecoder.pixels(fixture("/com/mirada/edit/profile.webp"))), 4);
        assertNear(tone, centre(HeifDecoder.pixels(fixture("/com/mirada/edit/profile.heic"))), 4);
        assertNear(tone, centre(HeifDecoder.pixels(fixture("/com/mirada/edit/profile.avif"))), 4);
        assertNear(tone, centre(ImageIoDecoder.pixels(fixture("/com/mirada/edit/profile.png"))), 4);
        int converted = centre(ImageIoDecoder.pixels(fixture("/com/mirada/edit/profile.webp")));
        assertTrue((converted >> 16 & 0xFF) > 0xC8 + 8, String.format("ImageIO converts: %08X", converted));
        assertTrue(ImageCache.keepsColours(Path.of("a.webp")));
        assertTrue(ImageCache.keepsColours(Path.of("a.HEIC")));
        assertFalse(ImageCache.keepsColours(Path.of("a.jxl")));
        assertFalse(ImageCache.keepsColours(Path.of("a.tif")));
    }

    private static int centre(ArgbPixels image) {
        return image.pixels()[image.height() / 2 * image.width() + image.width() / 2];
    }
}
