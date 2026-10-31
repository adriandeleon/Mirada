package com.mirada.image;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CompletionException;

import javafx.scene.image.WritableImage;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ImageIoDecoderTest {

    private static final int RED = 0xFFFF0000;
    private static final int BLUE = 0xFF0000FF;

    @BeforeAll
    static void toolkit() throws InterruptedException {
        FxToolkit.start();
    }

    private static Path fixture(String name) throws URISyntaxException {
        return Path.of(Objects.requireNonNull(ImageIoDecoderTest.class.getResource(name), name)
                .toURI());
    }

    /** Each fixture is 4 × 2: the left half red, the right half blue. */
    @ParameterizedTest
    @ValueSource(strings = {"red-blue.webp", "red-blue.tif", "red-blue.psd"})
    void decodesTheFormatsJavaFxCannot(String name) throws Exception {
        WritableImage image = ImageIoDecoder.decode(fixture(name));

        assertEquals(4, image.getWidth());
        assertEquals(2, image.getHeight());
        assertEquals(RED, image.getPixelReader().getArgb(0, 0), name);
        assertEquals(RED, image.getPixelReader().getArgb(1, 1), name);
        assertEquals(BLUE, image.getPixelReader().getArgb(2, 0), name);
        assertEquals(BLUE, image.getPixelReader().getArgb(3, 1), name);
    }

    @Test
    void aBrokenFileIsAnIoException(@TempDir Path dir) throws IOException {
        Path broken = Files.writeString(dir.resolve("broken.webp"), "not a webp");
        assertThrows(IOException.class, () -> ImageIoDecoder.decode(broken));
    }

    @Test
    void transparencySurvivesTheCopy() {
        BufferedImage source = new BufferedImage(2, 1, BufferedImage.TYPE_INT_ARGB);
        source.setRGB(0, 0, 0x80123456);
        source.setRGB(1, 0, 0x00000000);

        WritableImage copy = ImageIoDecoder.toFx(source);

        assertEquals(0x80123456, copy.getPixelReader().getArgb(0, 0));
        assertEquals(0, copy.getPixelReader().getArgb(1, 0) >>> 24);
    }

    @Test
    void failuresReportTheInnermostMessage() {
        Throwable wrapped = new CompletionException(new UncheckedIOException(new IOException("truncated file")));
        assertEquals("truncated file", ImageCache.message(wrapped));
        assertEquals("IllegalStateException", ImageCache.message(new IllegalStateException()));
    }
}
