package com.mirada.edit;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

import org.jspecify.annotations.Nullable;

import static com.mirada.edit.Containers.MAX_BLOCK;
import static com.mirada.edit.Containers.box;
import static com.mirada.edit.Containers.bytes;
import static com.mirada.edit.Containers.child;
import static com.mirada.edit.Containers.is;
import static com.mirada.edit.Containers.sized;
import static com.mirada.edit.Containers.u16;
import static com.mirada.edit.Containers.u32;
import static com.mirada.edit.Containers.u8;

/**
 * A file's EXIF as raw bytes — the TIFF structure EXIF is, from its "II*\0" or "MM\0*" header on —
 * and the same bytes made true of an edited copy, so a saved edit keeps the camera, the date, the
 * exposure and the location of the photo it was made from.
 *
 * <p>Read from JPEG (APP1), PNG (eXIf), WebP (EXIF), HEIC and AVIF (the Exif item) and JPEG XL (the
 * Exif box, when it is not Brotli-compressed). A TIFF's own tags describe its pixels, not a camera's
 * EXIF block, and are not carried; nor are PSD's, GIF's or BMP's.
 *
 * <p>The edited copy is patched in place, never rebuilt, so every offset in it (maker notes
 * included) still points where it did: the orientation becomes 1 (the edit is saved upright), the
 * image sizes become the edit's, and the thumbnail is cut loose, being of the picture before it.
 */
public final class ExifBlock {

    /** The most a JPEG's APP1 segment holds after its length and its "Exif\0\0". */
    public static final int MAX_IN_JPEG = 0xFFFF - 2 - 6;

    private static final int ORIENTATION = 0x0112;
    private static final int IMAGE_WIDTH = 0x0100;
    private static final int IMAGE_HEIGHT = 0x0101;
    private static final int EXIF_IFD = 0x8769;
    private static final int PIXEL_X = 0xA002;
    private static final int PIXEL_Y = 0xA003;
    private static final int SHORT = 3;
    private static final int LONG = 4;

    private ExifBlock() {}

    /**
     * The EXIF of {@code source}, made true of an edit of it {@code width} × {@code height} pixels;
     * null when it has none, or none this can read or patch. A save goes ahead without it.
     */
    public static byte @Nullable [] carried(Path source, int width, int height) {
        try {
            byte[] exif = read(source);
            return exif == null ? null : forEdited(exif, width, height);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** {@code file}'s EXIF, by what the file is rather than its name; null when it has none. */
    public static byte @Nullable [] read(Path file) throws IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            byte[] head = bytes(channel, 0, 12);
            if (head == null) return null;
            if (u8(head, 0) == 0xFF && u8(head, 1) == 0xD8) return fromJpeg(channel);
            if (u8(head, 0) == 0x89 && is(head, 1, "PNG")) return fromPng(channel);
            if (is(head, 0, "RIFF") && is(head, 8, "WEBP")) return fromWebp(channel);
            if (is(head, 4, "ftyp")) return fromHeif(channel);
            if (is(head, 4, "JXL ")) return fromJxl(channel);
            return null;
        }
    }

    private static byte @Nullable [] fromJpeg(FileChannel channel) throws IOException {
        long at = 2;
        while (true) {
            byte[] marker = bytes(channel, at, 4);
            if (marker == null || u8(marker, 0) != 0xFF) return null;
            int type = u8(marker, 1);
            if (type == 0xDA || type == 0xD9) return null; // the picture itself: no metadata after it
            int length = u16(marker, 2, false);
            if (length < 2) return null;
            if (type == 0xE1 && length >= 2 + 6 + 8) {
                byte[] segment = bytes(channel, at + 4, length - 2);
                if (segment != null && is(segment, 0, "Exif") && segment[4] == 0 && segment[5] == 0) {
                    return tiff(segment, 6);
                }
            }
            at += 2 + length;
        }
    }

    private static byte @Nullable [] fromPng(FileChannel channel) throws IOException {
        long at = 8;
        while (true) {
            byte[] header = bytes(channel, at, 8);
            if (header == null) return null;
            long length = u32(header, 0, false);
            if (is(header, 4, "IEND") || length > Integer.MAX_VALUE) return null;
            if (is(header, 4, "eXIf")) {
                byte[] data = length > MAX_BLOCK ? null : bytes(channel, at + 8, (int) length);
                return data == null ? null : tiff(data, 0);
            }
            at += 12 + length;
        }
    }

    private static byte @Nullable [] fromWebp(FileChannel channel) throws IOException {
        long at = 12;
        while (true) {
            byte[] header = bytes(channel, at, 8);
            if (header == null) return null;
            long size = u32(header, 4, true);
            if (is(header, 0, "EXIF")) {
                byte[] data = size > MAX_BLOCK ? null : bytes(channel, at + 8, (int) size);
                if (data == null) return null;
                // Some writers put JPEG's "Exif\0\0" before the TIFF header; the format says not to.
                return is(data, 0, "Exif") ? tiff(data, 6) : tiff(data, 0);
            }
            at += 8 + size + (size & 1);
        }
    }

    /**
     * HEIC and AVIF: the item of type "Exif" listed in the meta box's iinf, found through iloc,
     * whose data is a four-byte offset to the TIFF header and then the block.
     */
    private static byte @Nullable [] fromHeif(FileChannel channel) throws IOException {
        byte[] data = Containers.heifMeta(channel);
        if (data == null) return null;
        // The meta box is a full box: version and flags, then its children.
        int[] iinf = child(data, 4, data.length, "iinf");
        int[] iloc = child(data, 4, data.length, "iloc");
        if (iinf == null || iloc == null) return null;
        long item = exifItem(data, iinf[0], iinf[0] + iinf[1]);
        if (item < 0) return null;
        int[] idat = child(data, 4, data.length, "idat");
        byte[] payload = itemData(channel, data, iloc[0], iloc[0] + iloc[1], item, idat);
        return payload == null ? null : afterOffset(payload);
    }

    /** JPEG XL's container: a top-level "Exif" box, the same four-byte offset and then the block. */
    private static byte @Nullable [] fromJxl(FileChannel channel) throws IOException {
        long[] box = box(channel, 0, channel.size(), "Exif");
        if (box == null || box[1] > MAX_BLOCK) return null;
        byte[] payload = bytes(channel, box[0], (int) box[1]);
        return payload == null ? null : afterOffset(payload);
    }

    private static byte @Nullable [] afterOffset(byte[] payload) {
        if (payload.length < 4) return null;
        long offset = u32(payload, 0, false);
        return offset > payload.length - 4 ? null : tiff(payload, 4 + (int) offset);
    }

    /** The id of the first item of type "Exif" in an iinf box's content, or -1. */
    private static long exifItem(byte[] data, int start, int end) {
        int version = u8(data, start);
        int at = start + 4 + (version == 0 ? 2 : 4);
        while (at + 8 <= end) {
            long size = u32(data, at, false);
            if (size < 8 || at + size > end) return -1;
            if (is(data, at + 4, "infe")) {
                int infe = at + 8;
                int infeVersion = u8(data, infe);
                if (infeVersion >= 2) {
                    int id = infe + 4;
                    long itemId = infeVersion == 2 ? u16(data, id, false) : u32(data, id, false);
                    int type = id + (infeVersion == 2 ? 2 : 4) + 2;
                    if (type + 4 <= end && is(data, type, "Exif")) return itemId;
                }
            }
            at += (int) size;
        }
        return -1;
    }

    /** An item's bytes, by its iloc entry: from the file (construction method 0) or the idat box (1). */
    private static byte @Nullable [] itemData(
            FileChannel channel, byte[] data, int start, int end, long item, int @Nullable [] idat) throws IOException {
        int version = u8(data, start);
        int at = start + 4;
        if (at + 2 > end) return null;
        int offsetSize = u8(data, at) >> 4;
        int lengthSize = u8(data, at) & 0xF;
        int baseSize = u8(data, at + 1) >> 4;
        int indexSize = version == 0 ? 0 : u8(data, at + 1) & 0xF;
        at += 2;
        long count = version < 2 ? u16(data, at, false) : u32(data, at, false);
        at += version < 2 ? 2 : 4;
        int idSize = version < 2 ? 2 : 4;
        for (long i = 0; i < count; i++) {
            if (at + idSize + (version > 0 ? 2 : 0) + 2 + baseSize + 2 > end) return null;
            long id = version < 2 ? u16(data, at, false) : u32(data, at, false);
            at += idSize;
            int method = 0;
            if (version > 0) {
                method = u16(data, at, false) & 0xF;
                at += 2;
            }
            at += 2; // data_reference_index
            long base = sized(data, at, baseSize);
            at += baseSize;
            int extents = u16(data, at, false);
            at += 2;
            int entrySize = indexSize + offsetSize + lengthSize;
            if (at + (long) extents * entrySize > end) return null;
            if (id != item) {
                at += extents * entrySize;
                continue;
            }
            byte[] out = new byte[0];
            for (int e = 0; e < extents; e++) {
                at += indexSize;
                long offset = base + sized(data, at, offsetSize);
                at += offsetSize;
                long length = sized(data, at, lengthSize);
                at += lengthSize;
                if (length <= 0 || out.length + length > MAX_BLOCK) return null;
                byte[] part;
                if (method == 0) {
                    part = bytes(channel, offset, (int) length);
                } else if (method == 1 && idat != null && offset + length <= idat[1]) {
                    part = Arrays.copyOfRange(data, idat[0] + (int) offset, idat[0] + (int) (offset + length));
                } else {
                    return null;
                }
                if (part == null) return null;
                byte[] joined = Arrays.copyOf(out, out.length + part.length);
                System.arraycopy(part, 0, joined, out.length, part.length);
                out = joined;
            }
            return out;
        }
        return null;
    }

    /**
     * A copy of {@code tiff} as EXIF of an edit {@code width} × {@code height} pixels, saved upright
     * and without the thumbnail; null when the block is not one this can follow.
     */
    public static byte @Nullable [] forEdited(byte[] tiff, int width, int height) {
        if (tiff(tiff, 0) == null) return null;
        byte[] out = tiff.clone();
        boolean little = out[0] == 'I';
        long first = u32(out, 4, little);
        return patch(out, first, little, width, height, true) ? out : null;
    }

    private static boolean patch(byte[] out, long ifd, boolean little, int width, int height, boolean first) {
        if (ifd < 8 || ifd + 2 > out.length) return false;
        int at = (int) ifd;
        int count = u16(out, at, little);
        int next = at + 2 + 12 * count;
        if (next + 4 > out.length) return false;
        for (int i = 0; i < count; i++) {
            int entry = at + 2 + 12 * i;
            int tag = u16(out, entry, little);
            int type = u16(out, entry + 2, little);
            long values = u32(out, entry + 4, little);
            if (values != 1) continue;
            if (tag == ORIENTATION && type == SHORT) {
                putU16(out, entry + 8, 1, little); // a SHORT sits in the entry's first two bytes
                putU16(out, entry + 10, 0, little);
            } else if (first && tag == EXIF_IFD && (type == LONG || type == 13)) {
                long exif = u32(out, entry + 8, little);
                if (exif != ifd && !patch(out, exif, little, width, height, false)) return false;
            } else if ((type == SHORT || type == LONG)
                    && (first ? tag == IMAGE_WIDTH || tag == IMAGE_HEIGHT : tag == PIXEL_X || tag == PIXEL_Y)) {
                // A size may be a SHORT or a LONG; LONG holds any size, and both fit in the entry.
                putU16(out, entry + 2, LONG, little);
                putU32(out, entry + 8, tag == IMAGE_WIDTH || tag == PIXEL_X ? width : height, little);
            }
        }
        if (first) putU32(out, next, 0, little); // IFD1, the thumbnail, is no longer reached
        return true;
    }

    /** The bytes from {@code at} on when a TIFF header starts there, else null. */
    private static byte @Nullable [] tiff(byte[] data, int at) {
        if (data.length < at + 8) return null;
        boolean intel = is(data, at, "II") && u8(data, at + 2) == 42 && u8(data, at + 3) == 0;
        boolean motorola = is(data, at, "MM") && u8(data, at + 2) == 0 && u8(data, at + 3) == 42;
        return intel || motorola ? Arrays.copyOfRange(data, at, data.length) : null;
    }

    private static void putU16(byte[] data, int at, int value, boolean little) {
        data[at + (little ? 0 : 1)] = (byte) value;
        data[at + (little ? 1 : 0)] = (byte) (value >> 8);
    }

    private static void putU32(byte[] data, int at, long value, boolean little) {
        putU16(data, at + (little ? 0 : 2), (int) value, little);
        putU16(data, at + (little ? 2 : 0), (int) (value >> 16), little);
    }
}
