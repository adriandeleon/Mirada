package com.mirada.edit;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
}
