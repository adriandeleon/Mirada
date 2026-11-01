package com.mirada.image;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
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
 * <p>It also decodes the first JPEG while the JavaFX toolkit is still starting ({@link HeadStart}),
 * which JavaFX's own decoder cannot do — but only a plain one ({@link #plainJpeg}).
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
        BufferedImage decoded = ImageIO.read(file.toFile());
        if (decoded == null) throw new IOException("no decoder accepts this file");
        return toArgb(decoded);
    }

    /**
     * A JPEG as JavaFX would show it, or an {@link IOException} when it is not one JavaFX and the
     * JDK's reader agree on: YCbCr or grey with no embedded ICC profile. The JDK converts an embedded
     * profile (and reads CMYK files differently, or not at all) where JavaFX ignores it; the same
     * photo must not change colour depending on which decoder happened to open it.
     */
    static ArgbPixels plainJpeg(Path file) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(file.toFile())) {
            if (input == null) throw new IOException("cannot read " + file);
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("jpeg");
            if (!readers.hasNext()) throw new IOException("no JPEG reader");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                // The first type is the file's own: an ICC colour space when it embeds a profile.
                ImageTypeSpecifier type = reader.getImageTypes(0).next();
                int kind = type.getBufferedImageType();
                if (kind != BufferedImage.TYPE_3BYTE_BGR && kind != BufferedImage.TYPE_BYTE_GRAY) {
                    throw new IOException(
                            "not a plain JPEG (" + type.getColorModel().getColorSpace() + ")");
                }
                ImageReadParam param = reader.getDefaultReadParam();
                param.setDestinationType(type);
                return toArgb(reader.read(0, param));
            } finally {
                reader.dispose();
            }
        }
    }

    /** Fast for the byte layouts JPEG decodes to; through {@code getRGB}, a row at a time, for the rest. */
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
