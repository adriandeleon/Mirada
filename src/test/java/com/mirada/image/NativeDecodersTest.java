package com.mirada.image;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import javafx.scene.image.WritableImage;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
