package com.mirada.image;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import javafx.scene.image.WritableImage;

/**
 * Decodes the formats JavaFX cannot — WebP, TIFF, PSD — through ImageIO, into {@link ArgbPixels}.
 * Blocking and memory-hungry (the {@code BufferedImage} and the copy exist together for a moment),
 * so it runs on {@link ImageCache}'s decoder threads, never the FX thread.
 *
 * <p>{@code BufferedImage.getRGB} converts from the decoded image's colour space to sRGB on the way
 * out. Whether that space carries a file's embedded ICC profile is up to each reader; unchecked.
 *
 */
final class ImageIoDecoder {

    static {
        // ImageIO otherwise buffers input streams through temporary files.
        ImageIO.setUseCache(false);
    }

    private ImageIoDecoder() {}

    static WritableImage decode(Path file) throws IOException {
        return pixels(file).toImage();
    }

    /** The image, decoded without the JavaFX toolkit. */
    static ArgbPixels pixels(Path file) throws IOException {
        // ImageIO.read's own steps, with the size read from the header and checked between them
        // (PERF-12): ImageIO.read would allocate the whole picture before anyone could ask.
        try (ImageInputStream in = ImageIO.createImageInputStream(file.toFile())) {
            if (in == null) throw new IOException("the file could not be read");
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) throw new IOException("no decoder accepts this file");
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                int width;
                int height;
                try {
                    width = reader.getWidth(0);
                    height = reader.getHeight(0);
                } catch (RuntimeException e) {
                    // A reader that cannot find the first image's header throws what it likes.
                    throw new IOException("the file is damaged or cut short", e);
                }
                DecodeGuard.check(width, height);
                BufferedImage decoded;
                try {
                    decoded = reader.read(0, reader.getDefaultReadParam());
                } catch (RuntimeException e) {
                    // TwelveMonkeys' TIFF reader, on a JPEG-in-TIFF cut short, throws a ClassCastException.
                    throw new IOException("the file is damaged or cut short", e);
                }
                if (decoded == null) throw new IOException("no decoder accepts this file");
                return toArgb(decoded);
            } finally {
                reader.dispose();
            }
        }
    }

    /** Fast for plain 8-bit RGB and grey; through {@code getRGB}, a row at a time, for the rest. */
    static ArgbPixels toArgb(BufferedImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        int[] out = new int[Math.multiplyExact(width, height)];
        int type = source.getType();
        if ((type == BufferedImage.TYPE_3BYTE_BGR || type == BufferedImage.TYPE_BYTE_GRAY)
                && source.getRaster().getDataBuffer() instanceof DataBufferByte buffer
                && buffer.getNumBanks() == 1
                && source.getRaster().getParent() == null) {
            byte[] bytes = buffer.getData();
            if (type == BufferedImage.TYPE_3BYTE_BGR) {
                for (int i = 0, b = 0; i < out.length; i++, b += 3) {
                    out[i] = ArgbPixels.opaque(bytes[b + 2] & 0xFF, bytes[b + 1] & 0xFF, bytes[b] & 0xFF);
                }
            } else {
                for (int i = 0; i < out.length; i++) {
                    int grey = bytes[i] & 0xFF;
                    out[i] = ArgbPixels.opaque(grey, grey, grey);
                }
            }
            return new ArgbPixels(width, height, out);
        }
        for (int y = 0; y < height; y++) {
            int at = y * width;
            source.getRGB(0, y, width, 1, out, at, width);
            for (int i = at; i < at + width; i++) out[i] = ArgbPixels.premultiply(out[i]);
        }
        return new ArgbPixels(width, height, out);
    }

    static WritableImage toFx(BufferedImage source) {
        return toArgb(source).toImage();
    }
}
