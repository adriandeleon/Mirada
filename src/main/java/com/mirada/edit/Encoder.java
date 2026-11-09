package com.mirada.edit;

import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
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

import static com.mirada.i18n.Messages.tr;

/**
 * Writes an edited image, in the format its file name says, through the JDK's own ImageIO writers:
 * JPEG (at quality {@value #JPEG_QUALITY}), PNG, TIFF and BMP. WebP, HEIC, AVIF, JPEG XL and PSD
 * can be opened but not written; the editor offers JPEG or PNG for those instead.
 *
 * <p>EXIF, an ICC profile and XMP, when given ({@link ExifBlock#carried}, {@link IccProfile#carried},
 * {@link Xmp#carried}), go into a JPEG as its APP1 and APP2 segments, into a PNG as its eXIf, iCCP
 * and iTXt chunks, and into a TIFF as its own tags; BMP is written without them. A TIFF is written
 * here rather than by ImageIO, around the EXIF block (see {@link #writeTiff}).
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
        write(image, file, null, null, null);
    }

    /** {@link #write(Raster, Path, byte[], byte[], byte[])} without XMP. */
    public static void write(Raster image, Path file, byte @Nullable [] exif, byte @Nullable [] profile)
            throws IOException {
        write(image, file, exif, profile, null);
    }

    /**
     * Writes {@code image} to {@code file} as {@link #write(Raster, Path)} does, with {@code exif} (a
     * TIFF structure, as {@link ExifBlock} gives), {@code profile} (an ICC profile) and {@code xmp} (a
     * packet, as {@link Xmp} gives) in it when the format carries them.
     */
    public static void write(
            Raster image, Path file, byte @Nullable [] exif, byte @Nullable [] profile, byte @Nullable [] xmp)
            throws IOException {
        String format = formatFor(file);
        if (format == null) throw new IOException(tr("edit.cannotWriteFormat"));
        // Beside the target, created as any new file is (createTempFile would make it owner-only).
        Path temporary = file.resolveSibling(".mirada-" + Long.toHexString(System.nanoTime()) + ".tmp");
        try {
            if (format.equals("tiff")) {
                writeTiff(image, temporary, exif, profile, xmp);
            } else {
                boolean jpeg = format.equals("jpeg");
                boolean carries = jpeg || format.equals("png");
                byte[] keptExif =
                        carries && !(jpeg && exif != null && exif.length > ExifBlock.MAX_IN_JPEG) ? exif : null;
                byte[] keptProfile = carries ? profile : null;
                byte[] keptXmp = carries && !(jpeg && xmp != null && xmp.length > Xmp.MAX_IN_JPEG) ? xmp : null;
                BufferedImage out = toBuffered(image, !image.opaque() && format.equals("png"));
                if (keptExif != null || keptProfile != null || keptXmp != null) {
                    // Encoded in memory, so the metadata can go in near the start of the file.
                    ByteArrayOutputStream encoded = new ByteArrayOutputStream();
                    try (ImageOutputStream stream = new MemoryCacheImageOutputStream(encoded)) {
                        encode(out, format, stream);
                    }
                    byte[] bytes = encoded.toByteArray();
                    Files.write(
                            temporary,
                            jpeg
                                    ? withSegments(bytes, keptExif, keptProfile, keptXmp)
                                    : withChunks(bytes, keptExif, keptProfile, keptXmp));
                } else {
                    try (ImageOutputStream stream = ImageIO.createImageOutputStream(temporary.toFile())) {
                        encode(out, format, stream);
                    }
                }
            }
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /** About how large a TIFF strip is made: a few rows of a photo, read in one go. */
    static final int STRIP = 64 << 10;

    /**
     * The IFD0 tags of a carried EXIF block that say how pixels are stored rather than what the
     * picture is: the TIFF's own take their place. Its orientation (now 1) is TIFF's default.
     */
    static final Set<Integer> STORAGE_TAGS = Set.of(
            254, 255, 256, 257, 258, 259, 262, 263, 264, 265, 266, 273, 274, 277, 278, 279, 280, 281, 284, 317, 320,
            322, 323, 324, 325, 330, 338, 339, 340, 341, 513, 514, 529, 530, 531, 532, 700, 34675);

    private static final int BYTE = 1;
    private static final int SHORT = 3;
    private static final int LONG = 4;
    private static final int RATIONAL = 5;
    private static final int UNDEFINED = 7;

    /** A TIFF directory entry: its tag, type and count, and its value as bytes in the file's order. */
    private record Entry(int tag, int type, long count, byte[] value) {}

    /**
     * Writes {@code image} as an uncompressed RGB (or RGBA, unassociated alpha) TIFF, with {@code
     * exif}, {@code profile} and {@code xmp}.
     *
     * <p>The EXIF block is itself a TIFF structure whose offsets count from its own header, so the
     * file starts with it whole, in its byte order: every offset in it, maker notes' included, stays
     * true without being read. The strips, the values too large for an entry and a new IFD0 follow,
     * and the header is pointed at that IFD0, which takes the block's descriptive tags (camera, date,
     * the links to the EXIF and GPS directories) and leaves its {@link #STORAGE_TAGS}.
     */
    static void writeTiff(
            Raster image, Path file, byte @Nullable [] exif, byte @Nullable [] profile, byte @Nullable [] xmp)
            throws IOException {
        boolean little = exif == null || exif[0] == 'I';
        ByteOrder order = little ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN;
        int w = image.width();
        int h = image.height();
        boolean alpha = !image.opaque();
        int samples = alpha ? 4 : 3;
        long rowBytes = (long) w * samples;
        int rowsPerStrip = (int) Math.max(1, Math.min(h, STRIP / rowBytes));
        int strips = (h + rowsPerStrip - 1) / rowsPerStrip;

        byte[] head = exif == null ? new byte[] {'I', 'I', 42, 0, 0, 0, 0, 0} : exif.clone();
        long pixelsAt = even(head.length);

        SortedMap<Integer, Entry> entries = new TreeMap<>();
        entries.put(256, entry(order, 256, LONG, w));
        entries.put(257, entry(order, 257, LONG, h));
        short[] bits = new short[samples];
        Arrays.fill(bits, (short) 8);
        entries.put(258, shorts(order, 258, bits));
        entries.put(259, entry(order, 259, SHORT, 1)); // no compression
        entries.put(262, entry(order, 262, SHORT, 2)); // RGB
        entries.put(277, entry(order, 277, SHORT, samples));
        entries.put(278, entry(order, 278, LONG, rowsPerStrip));
        entries.put(282, rational(order, 282, 72));
        entries.put(283, rational(order, 283, 72));
        entries.put(284, entry(order, 284, SHORT, 1)); // chunky: R, G, B, A of a pixel together
        entries.put(296, entry(order, 296, SHORT, 2)); // inches
        if (alpha) entries.put(338, entry(order, 338, SHORT, 2)); // unassociated alpha
        if (xmp != null) entries.put(700, new Entry(700, BYTE, xmp.length, xmp));
        if (profile != null) entries.put(34675, new Entry(34675, UNDEFINED, profile.length, profile));
        // The block's own IFD0 entries, kept as they are: their offsets point into the block.
        Map<Integer, byte[]> carried = new TreeMap<>();
        if (exif != null) {
            long ifd = u32(exif, 4, little);
            int count = ifd + 2 <= exif.length ? u16(exif, (int) ifd, little) : 0;
            for (int i = 0; i < count && ifd + 2 + 12L * (i + 1) <= exif.length; i++) {
                int at = (int) ifd + 2 + 12 * i;
                int tag = u16(exif, at, little);
                if (!STORAGE_TAGS.contains(tag)) {
                    carried.put(tag, Arrays.copyOfRange(exif, at, at + 12));
                    entries.remove(tag);
                }
            }
        }

        // Where everything goes: the strips, each value too large for its entry, then IFD0.
        long[] stripOffsets = new long[strips];
        long[] stripCounts = new long[strips];
        for (int s = 0; s < strips; s++) {
            stripOffsets[s] = pixelsAt + rowBytes * rowsPerStrip * s;
            stripCounts[s] = rowBytes * Math.min(rowsPerStrip, h - rowsPerStrip * s);
        }
        entries.put(273, longs(order, 273, stripOffsets));
        entries.put(279, longs(order, 279, stripCounts));
        long at = even(pixelsAt + rowBytes * h);
        Map<Integer, Long> outside = new TreeMap<>();
        for (Entry entry : entries.values()) {
            if (entry.value().length > 4) {
                outside.put(entry.tag(), at);
                at = even(at + entry.value().length);
            }
        }
        long ifdAt = at;
        int count = entries.size() + carried.size();
        if (ifdAt + 2 + 12L * count + 4 > 0xFFFFFFFFL) throw new IOException(tr("edit.tooLargeForTiff"));
        // StripOffsets was laid out with the rest, so its own values are already final.

        ByteBuffer.wrap(head, 4, 4).order(order).putInt((int) ifdAt);
        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(file), 1 << 16)) {
            out.write(head);
            pad(out, pixelsAt - head.length);
            int[] pixels = image.pixels();
            byte[] row = new byte[(int) rowBytes];
            for (int y = 0; y < h; y++) {
                for (int x = 0, i = y * w, b = 0; x < w; x++, i++) {
                    int argb = straight(pixels[i], alpha);
                    row[b++] = (byte) (argb >> 16);
                    row[b++] = (byte) (argb >> 8);
                    row[b++] = (byte) argb;
                    if (alpha) row[b++] = (byte) (argb >>> 24);
                }
                out.write(row);
            }
            long written = pixelsAt + rowBytes * h;
            for (Entry entry : entries.values()) {
                if (entry.value().length > 4) {
                    long place = outside.getOrDefault(entry.tag(), written);
                    pad(out, place - written);
                    out.write(entry.value());
                    written = place + entry.value().length;
                }
            }
            pad(out, ifdAt - written);
            ByteBuffer ifd = ByteBuffer.allocate(2 + 12 * count + 4).order(order);
            ifd.putShort((short) count);
            SortedMap<Integer, byte[]> all = new TreeMap<>(carried);
            for (Entry entry : entries.values()) {
                ByteBuffer raw = ByteBuffer.allocate(12).order(order);
                raw.putShort((short) entry.tag()).putShort((short) entry.type()).putInt((int) entry.count());
                if (entry.value().length > 4) raw.putInt((int) (long) outside.getOrDefault(entry.tag(), 0L));
                else raw.put(entry.value());
                all.put(entry.tag(), raw.array());
            }
            for (byte[] raw : all.values()) ifd.put(raw);
            ifd.putInt(0); // no next image
            out.write(ifd.array());
        }
    }

    private static long even(long at) {
        return at + (at & 1);
    }

    private static void pad(OutputStream out, long bytes) throws IOException {
        for (long i = 0; i < bytes; i++) out.write(0);
    }

    private static Entry entry(ByteOrder order, int tag, int type, long value) {
        ByteBuffer bytes = ByteBuffer.allocate(4).order(order);
        if (type == SHORT) bytes.putShort((short) value);
        else bytes.putInt((int) value);
        return new Entry(tag, type, 1, bytes.array());
    }

    private static Entry shorts(ByteOrder order, int tag, short[] values) {
        ByteBuffer bytes = ByteBuffer.allocate(Math.max(4, 2 * values.length)).order(order);
        for (short value : values) bytes.putShort(value);
        return new Entry(tag, SHORT, values.length, Arrays.copyOf(bytes.array(), Math.max(4, 2 * values.length)));
    }

    private static Entry longs(ByteOrder order, int tag, long[] values) {
        ByteBuffer bytes = ByteBuffer.allocate(4 * values.length).order(order);
        for (long value : values) bytes.putInt((int) value);
        return new Entry(tag, LONG, values.length, bytes.array());
    }

    private static Entry rational(ByteOrder order, int tag, int value) {
        return new Entry(
                tag,
                RATIONAL,
                1,
                ByteBuffer.allocate(8).order(order).putInt(value).putInt(1).array());
    }

    private static int u16(byte[] data, int at, boolean little) {
        return Containers.u16(data, at, little);
    }

    private static long u32(byte[] data, int at, boolean little) {
        return at + 4 <= data.length ? Containers.u32(data, at, little) : 0;
    }

    private static void encode(BufferedImage image, String format, ImageOutputStream stream) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(format);
        if (!writers.hasNext()) throw new IOException(tr("edit.noWriter", format));
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
     * {@code jpeg} with an APP1 segment holding {@code exif}, the APP2 segments holding {@code
     * profile} and an APP1 segment holding {@code xmp}, after the JFIF segment when there is one (JFIF must come first; readers find the rest
     * after it), else right after the start of image. A profile is cut into chunks of at most
     * {@value #ICC_CHUNK} bytes, each with its number and the count, as the ICC specification says.
     */
    static byte[] withSegments(byte[] jpeg, byte @Nullable [] exif, byte @Nullable [] profile, byte @Nullable [] xmp) {
        int at = 2;
        if ((jpeg[2] & 0xFF) == 0xFF && (jpeg[3] & 0xFF) == 0xE0) {
            at = 4 + ((jpeg[4] & 0xFF) << 8 | jpeg[5] & 0xFF);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(jpeg.length + (1 << 10));
        out.write(jpeg, 0, at);
        if (exif != null) segment(out, 0xE1, "Exif\0\0".getBytes(StandardCharsets.ISO_8859_1), exif, 0, exif.length);
        if (xmp != null) segment(out, 0xE1, Xmp.JPEG_HEADER, xmp, 0, xmp.length);
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
     * {@code png} with an iCCP chunk holding {@code profile}, an eXIf chunk holding {@code exif} and
     * an iTXt chunk holding {@code xmp}, uncompressed as XMP's specification asks, right after IHDR
     * (iCCP and eXIf must precede IDAT; readers look for XMP early).
     */
    static byte[] withChunks(byte[] png, byte @Nullable [] exif, byte @Nullable [] profile, byte @Nullable [] xmp) {
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
        if (xmp != null) {
            // The keyword, a zero, uncompressed (0, 0), no language and no translated keyword.
            ByteArrayOutputStream itxt = new ByteArrayOutputStream();
            itxt.writeBytes((Xmp.PNG_KEYWORD + "\0\0\0\0\0").getBytes(StandardCharsets.ISO_8859_1));
            itxt.writeBytes(xmp);
            chunk(out, "iTXt", itxt.toByteArray());
        }
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
        for (int i = 0; i < pixels.length; i++) out[i] = straight(pixels[i], alpha);
        BufferedImage buffered =
                new BufferedImage(w, h, alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        buffered.setRGB(0, 0, w, h, out, 0, w);
        return buffered;
    }

    /** A premultiplied pixel made straight, with {@code alpha}; else opaque, laid over white. */
    static int straight(int argb, boolean alpha) {
        int a = argb >>> 24;
        if (a == 0xFF) return argb;
        if (alpha) return unpremultiply(argb);
        // Premultiplied colour over white: c + (1 - a) × 255.
        int fill = 0xFF - a;
        return 0xFF000000
                | Math.min(0xFF, (argb >> 16 & 0xFF) + fill) << 16
                | Math.min(0xFF, (argb >> 8 & 0xFF) + fill) << 8
                | Math.min(0xFF, (argb & 0xFF) + fill);
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
