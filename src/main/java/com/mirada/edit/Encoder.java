package com.mirada.edit;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.zip.CRC32;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;

import org.jspecify.annotations.Nullable;

/**
 * Writes an edited image, in the format its file name says, through the JDK's own ImageIO writers:
 * JPEG (at quality {@value #JPEG_QUALITY}), PNG, TIFF and BMP. WebP, HEIC, AVIF, JPEG XL and PSD
 * can be opened but not written; the editor offers JPEG or PNG for those instead.
 *
 * <p>EXIF, when given ({@link ExifBlock#carried}), goes into a JPEG as its APP1 segment and into a
 * PNG as its eXIf chunk; TIFF and BMP are written without it. Nothing else is carried: not XMP,
 * which repeats the orientation, and not an ICC profile.
 */
public final class Encoder {

    static final float JPEG_QUALITY = 0.92f;

    /** The extensions it writes, lower case, the one it prefers for each format first. */
    public static final List<String> EXTENSIONS = List.of("jpg", "jpeg", "png", "tif", "tiff", "bmp");

    private Encoder() {}

    /** ImageIO's format name for {@code file}'s extension, or null when it is not one this writes. */
    static @Nullable String formatFor(Path file) {
        Path name = file.getFileName();
        String text = name == null ? "" : name.toString();
        int dot = text.lastIndexOf('.');
        String extension = dot < 0 ? "" : text.substring(dot + 1).toLowerCase(Locale.ROOT);
        return switch (extension) {
            case "jpg", "jpeg" -> "jpeg";
            case "png" -> "png";
            case "tif", "tiff" -> "tiff";
            case "bmp" -> "bmp";
            default -> null;
        };
    }

    /**
     * The extension to save an edit of {@code source} under: its own when this can write it, PNG for
     * a source with transparency it cannot, JPEG otherwise (a photo from a phone, typically).
     */
    public static String extensionFor(Path source, boolean opaque) {
        if (formatFor(source) != null) {
            Path name = source.getFileName();
            String text = name == null ? "" : name.toString();
            return text.substring(text.lastIndexOf('.') + 1);
        }
        return opaque ? "jpg" : "png";
    }

    /** Writes {@code image} to {@code file}, replacing it if it exists, through a temporary file. */
    public static void write(Raster image, Path file) throws IOException {
        write(image, file, null);
    }

    /**
     * Writes {@code image} to {@code file} as {@link #write(Raster, Path)} does, with {@code exif} (a
     * TIFF structure, as {@link ExifBlock} gives) in it when the format carries EXIF.
     */
    public static void write(Raster image, Path file, byte @Nullable [] exif) throws IOException {
        String format = formatFor(file);
        if (format == null) throw new IOException("Mirada can write JPEG, PNG, TIFF and BMP");
        boolean alpha = !image.opaque() && (format.equals("png") || format.equals("tiff"));
        BufferedImage out = toBuffered(image, alpha);
        // Beside the target, created as any new file is (createTempFile would make it owner-only).
        Path temporary = file.resolveSibling(".mirada-" + Long.toHexString(System.nanoTime()) + ".tmp");
        boolean withExif =
                exif != null && (format.equals("png") || format.equals("jpeg") && exif.length <= ExifBlock.MAX_IN_JPEG);
        try {
            if (exif != null && withExif) {
                // Encoded in memory, so the block can go in near the start of the file.
                ByteArrayOutputStream encoded = new ByteArrayOutputStream();
                try (ImageOutputStream stream = new MemoryCacheImageOutputStream(encoded)) {
                    encode(out, format, stream);
                }
                byte[] bytes = encoded.toByteArray();
                Files.write(
                        temporary, format.equals("png") ? withExifChunk(bytes, exif) : withExifSegment(bytes, exif));
            } else {
                try (ImageOutputStream stream = ImageIO.createImageOutputStream(temporary.toFile())) {
                    encode(out, format, stream);
                }
            }
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void encode(BufferedImage image, String format, ImageOutputStream stream) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(format);
        if (!writers.hasNext()) throw new IOException("no " + format + " writer");
        ImageWriter writer = writers.next();
        try {
            writer.setOutput(stream);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (format.equals("jpeg")) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(JPEG_QUALITY);
            }
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
    }

    /**
     * {@code jpeg} with an APP1 segment holding {@code exif}, after the JFIF segment when there is one
     * (JFIF must come first; readers find EXIF after it), else right after the start of image.
     */
    static byte[] withExifSegment(byte[] jpeg, byte[] exif) {
        int at = 2;
        if ((jpeg[2] & 0xFF) == 0xFF && (jpeg[3] & 0xFF) == 0xE0) {
            at = 4 + ((jpeg[4] & 0xFF) << 8 | jpeg[5] & 0xFF);
        }
        int length = 2 + 6 + exif.length;
        ByteBuffer out = ByteBuffer.allocate(jpeg.length + 2 + length);
        out.put(jpeg, 0, at);
        out.put((byte) 0xFF).put((byte) 0xE1).putShort((short) length);
        out.put("Exif\0\0".getBytes(StandardCharsets.ISO_8859_1)).put(exif);
        out.put(jpeg, at, jpeg.length - at);
        return out.array();
    }

    /** {@code png} with an eXIf chunk holding {@code exif}, right after IHDR (it must precede IDAT). */
    static byte[] withExifChunk(byte[] png, byte[] exif) {
        int ihdrLength = ByteBuffer.wrap(png, 8, 4).getInt();
        int at = 8 + 12 + ihdrLength;
        byte[] type = "eXIf".getBytes(StandardCharsets.ISO_8859_1);
        CRC32 crc = new CRC32();
        crc.update(type);
        crc.update(exif);
        ByteBuffer out = ByteBuffer.allocate(png.length + 12 + exif.length);
        out.put(png, 0, at);
        out.putInt(exif.length).put(type).put(exif).putInt((int) crc.getValue());
        out.put(png, at, png.length - at);
        return out.array();
    }

    /**
     * Straight (not premultiplied) ARGB with {@code alpha}, else RGB with any transparency laid over
     * white, as a JPEG must carry it.
     */
    static BufferedImage toBuffered(Raster image, boolean alpha) {
        int w = image.width();
        int h = image.height();
        int[] pixels = image.pixels();
        int[] out = new int[pixels.length];
        for (int i = 0; i < pixels.length; i++) {
            int argb = pixels[i];
            int a = argb >>> 24;
            if (a == 0xFF) {
                out[i] = argb;
            } else if (alpha) {
                out[i] = unpremultiply(argb);
            } else {
                // Premultiplied colour over white: c + (1 - a) × 255.
                int fill = 0xFF - a;
                out[i] = 0xFF000000
                        | Math.min(0xFF, (argb >> 16 & 0xFF) + fill) << 16
                        | Math.min(0xFF, (argb >> 8 & 0xFF) + fill) << 8
                        | Math.min(0xFF, (argb & 0xFF) + fill);
            }
        }
        BufferedImage buffered =
                new BufferedImage(w, h, alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        buffered.setRGB(0, 0, w, h, out, 0, w);
        return buffered;
    }

    static int unpremultiply(int argb) {
        int a = argb >>> 24;
        if (a == 0) return 0;
        int r = Math.min(0xFF, ((argb >> 16 & 0xFF) * 0xFF + a / 2) / a);
        int g = Math.min(0xFF, ((argb >> 8 & 0xFF) * 0xFF + a / 2) / a);
        int b = Math.min(0xFF, ((argb & 0xFF) * 0xFF + a / 2) / a);
        return a << 24 | r << 16 | g << 8 | b;
    }

    /**
     * Writes a small image in every format into {@code args[0]}: run by scripts/native-metadata.sh
     * under GraalVM's tracing agent, so the native image's metadata covers the writers (JPEG's goes
     * through JNI) without a person saving an edit during the recording.
     */
    public static void main(String[] args) throws IOException {
        Path folder = Path.of(args.length > 0 ? args[0] : ".");
        int[] pixels = new int[16 * 8];
        for (int i = 0; i < pixels.length; i++) pixels[i] = i % 16 < 8 ? 0xFFFF0000 : 0x800000FF;
        Raster raster = new Raster(16, 8, pixels);
        for (String extension : List.of("jpg", "png", "tif", "bmp")) {
            write(raster, folder.resolve("encoded." + extension));
        }
        System.out.println("encoded into " + folder);
    }
}
