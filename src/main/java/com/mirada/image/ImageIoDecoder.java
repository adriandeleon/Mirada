package com.mirada.image;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.IntBuffer;
import java.nio.file.Path;
import javax.imageio.ImageIO;

import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;

/**
 * Decodes the formats JavaFX cannot — WebP, TIFF, PSD — through ImageIO, and copies the result into
 * a JavaFX image. Blocking and memory-hungry (the {@code BufferedImage} and the copy exist together
 * for a moment), so it runs on {@link ImageCache}'s decoder threads, never the FX thread.
 *
 * <p>{@code BufferedImage.getRGB} converts from the decoded image's colour space to sRGB on the way
 * out. Whether that space carries a file's embedded ICC profile is up to each reader; unchecked.
 */
final class ImageIoDecoder {

    static {
        // ImageIO otherwise buffers input streams through temporary files.
        ImageIO.setUseCache(false);
    }

    private ImageIoDecoder() {}

    static WritableImage decode(Path file) throws IOException {
        BufferedImage decoded = ImageIO.read(file.toFile());
        if (decoded == null) throw new IOException("no decoder accepts this file");
        return toFx(decoded);
    }

    /** Row by row, so the extra memory is one row of ints rather than a second full image. */
    static WritableImage toFx(BufferedImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        WritableImage out = new WritableImage(width, height);
        PixelWriter writer = out.getPixelWriter();
        PixelFormat<IntBuffer> argb = PixelFormat.getIntArgbInstance();
        int[] row = new int[width];
        for (int y = 0; y < height; y++) {
            source.getRGB(0, y, width, 1, row, 0, width);
            writer.setPixels(0, y, width, 1, argb, row, 0, width);
        }
        return out;
    }
}
