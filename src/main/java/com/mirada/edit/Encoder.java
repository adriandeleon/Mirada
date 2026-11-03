package com.mirada.edit;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
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
 * <p>EXIF and an ICC profile, when given ({@link ExifBlock#carried}, {@link IccProfile#carried}),
 * go into a JPEG as its APP1 and APP2 segments and into a PNG as its eXIf and iCCP chunks; TIFF and
 * BMP are written without them. XMP is not carried: it repeats the orientation.
 */
public final class Encoder {

    static final float JPEG_QUALITY = 0.92f;

    /** The most of a profile one APP2 segment holds, after its length, "ICC_PROFILE\0" and two bytes. */
    static final int ICC_CHUNK = 0xFFFF - 2 - 14;

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
        write(image, file, null, null);
    }

    /**
     * Writes {@code image} to {@code file} as {@link #write(Raster, Path)} does, with {@code exif} (a
     * TIFF structure, as {@link ExifBlock} gives) and {@code profile} (an ICC profile) in it when the
     * format carries them.
     */
    public static void write(Raster image, Path file, byte @Nullable [] exif, byte @Nullable [] profile)
            throws IOException {
        String format = formatFor(file);
        if (format == null) throw new IOException("Mirada can write JPEG, PNG, TIFF and BMP");
        boolean alpha = !image.opaque() && (format.equals("png") || format.equals("tiff"));
        BufferedImage out = toBuffered(image, alpha);
        // Beside the target, created as any new file is (createTempFile would make it owner-only).
        Path temporary = file.resolveSibling(".mirada-" + Long.toHexString(System.nanoTime()) + ".tmp");
        boolean carries = format.equals("png") || format.equals("jpeg");
        byte[] keptExif = carries && !(format.equals("jpeg") && exif != null && exif.length > ExifBlock.MAX_IN_JPEG)
                ? exif
                : null;
        byte[] keptProfile = carries ? profile : null;
        try {
            if (keptExif != null || keptProfile != null) {
                // Encoded in memory, so the metadata can go in near the start of the file.
                ByteArrayOutputStream encoded = new ByteArrayOutputStream();
                try (ImageOutputStream stream = new MemoryCacheImageOutputStream(encoded)) {
                    encode(out, format, stream);
                }
                byte[] bytes = encoded.toByteArray();
                Files.write(
                        temporary,
                        format.equals("png")
                                ? withChunks(bytes, keptExif, keptProfile)
                                : withSegments(bytes, keptExif, keptProfile));
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
     * {@code jpeg} with an APP1 segment holding {@code exif} and the APP2 segments holding {@code
     * profile}, after the JFIF segment when there is one (JFIF must come first; readers find the rest
     * after it), else right after the start of image. A profile is cut into chunks of at most
     * {@value #ICC_CHUNK} bytes, each with its number and the count, as the ICC specification says.
     */
    static byte[] withSegments(byte[] jpeg, byte @Nullable [] exif, byte @Nullable [] profile) {
        int at = 2;
        if ((jpeg[2] & 0xFF) == 0xFF && (jpeg[3] & 0xFF) == 0xE0) {
            at = 4 + ((jpeg[4] & 0xFF) << 8 | jpeg[5] & 0xFF);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(jpeg.length + (1 << 10));
        out.write(jpeg, 0, at);
        if (exif != null) segment(out, 0xE1, "Exif\0\0".getBytes(StandardCharsets.ISO_8859_1), exif, 0, exif.length);
        if (profile != null) {
            int count = (profile.length + ICC_CHUNK - 1) / ICC_CHUNK;
            for (int i = 0; i < count; i++) {
                byte[] head = Arrays.copyOf("ICC_PROFILE\0".getBytes(StandardCharsets.ISO_8859_1), 14);
                head[12] = (byte) (i + 1);
                head[13] = (byte) count;
                int from = i * ICC_CHUNK;
                segment(out, 0xE2, head, profile, from, Math.min(profile.length, from + ICC_CHUNK) - from);
            }
        }
        out.write(jpeg, at, jpeg.length - at);
        return out.toByteArray();
    }

    private static void segment(ByteArrayOutputStream out, int type, byte[] head, byte[] data, int from, int length) {
        int size = 2 + head.length + length;
        out.write(0xFF);
        out.write(type);
        out.write(size >> 8);
        out.write(size);
        out.writeBytes(head);
        out.write(data, from, length);
    }

    /**
     * {@code png} with an iCCP chunk holding {@code profile} and an eXIf chunk holding {@code exif},
     * right after IHDR (both must precede IDAT).
     */
    static byte[] withChunks(byte[] png, byte @Nullable [] exif, byte @Nullable [] profile) {
        int ihdrLength = ByteBuffer.wrap(png, 8, 4).getInt();
        int at = 8 + 12 + ihdrLength;
        ByteArrayOutputStream out = new ByteArrayOutputStream(png.length + (1 << 10));
        out.write(png, 0, at);
        if (profile != null) {
            // The profile's name, a zero, compression method 0 (deflate), then the deflated profile.
            ByteArrayOutputStream iccp = new ByteArrayOutputStream();
            iccp.writeBytes("ICC profile\0\0".getBytes(StandardCharsets.ISO_8859_1));
            Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
            try (DeflaterOutputStream deflated = new DeflaterOutputStream(iccp, deflater)) {
                deflated.write(profile);
            } catch (IOException e) {
                throw new UncheckedIOException(e); // in memory: cannot happen
            } finally {
                deflater.end();
            }
            chunk(out, "iCCP", iccp.toByteArray());
        }
        if (exif != null) chunk(out, "eXIf", exif);
        out.write(png, at, png.length - at);
        return out.toByteArray();
    }

    private static void chunk(ByteArrayOutputStream out, String type, byte[] data) {
        byte[] name = type.getBytes(StandardCharsets.ISO_8859_1);
        CRC32 crc = new CRC32();
        crc.update(name);
        crc.update(data);
        out.writeBytes(ByteBuffer.allocate(8).putInt(data.length).put(name).array());
        out.writeBytes(data);
        out.writeBytes(ByteBuffer.allocate(4).putInt((int) crc.getValue()).array());
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
