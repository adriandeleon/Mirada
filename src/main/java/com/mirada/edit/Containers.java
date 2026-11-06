package com.mirada.edit;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import org.jspecify.annotations.Nullable;

/**
 * Reading the containers images come in — JPEG's segments, PNG's chunks, RIFF's chunks and the ISO
 * boxes of HEIF and JPEG XL — for the metadata a saved edit carries ({@link ExifBlock}, {@link
 * IccProfile}, {@link Xmp}). Every read is bounds-checked: a damaged file gives null, never an exception from
 * here.
 */
final class Containers {

    /** Boxes, chunks and segments of metadata larger than this are not trusted, and not read. */
    static final int MAX_BLOCK = 16 << 20;

    private Containers() {}

    /** A HEIF file's meta box, its content read whole; null when there is none, or it is too large. */
    static byte @Nullable [] heifMeta(FileChannel channel) throws IOException {
        long[] meta = box(channel, 0, channel.size(), "meta");
        return meta == null || meta[1] > MAX_BLOCK ? null : bytes(channel, meta[0], (int) meta[1]);
    }

    /**
     * The data of a HEIF file's first item of {@code type} (as iinf lists it), when {@code
     * contentType} is null, or of its first "mime" item of that content type, unencoded; null when
     * there is none, or it cannot be read.
     */
    static byte @Nullable [] heifItem(FileChannel channel, String type, @Nullable String contentType)
            throws IOException {
        byte[] data = heifMeta(channel);
        if (data == null) return null;
        // The meta box is a full box: version and flags, then its children.
        int[] iinf = child(data, 4, data.length, "iinf");
        int[] iloc = child(data, 4, data.length, "iloc");
        if (iinf == null || iloc == null) return null;
        long item = itemId(data, iinf[0], iinf[0] + iinf[1], type, contentType);
        if (item < 0) return null;
        int[] idat = child(data, 4, data.length, "idat");
        return itemData(channel, data, iloc[0], iloc[0] + iloc[1], item, idat);
    }

    /**
     * The id of the first item in an iinf box's content of {@code type} (and, when given, {@code
     * contentType}, without a content encoding), or -1.
     */
    static long itemId(byte[] data, int start, int end, String type, @Nullable String contentType) {
        if (start + 4 > end) return -1;
        int version = u8(data, start);
        int at = start + 4 + (version == 0 ? 2 : 4);
        while (at + 8 <= end) {
            long size = u32(data, at, false);
            if (size < 8 || at + size > end) return -1;
            if (is(data, at + 4, "infe")) {
                int infe = at + 8;
                int infeEnd = at + (int) size;
                int infeVersion = u8(data, infe);
                if (infeVersion >= 2) {
                    int id = infe + 4;
                    long itemId = infeVersion == 2 ? u16(data, id, false) : u32(data, id, false);
                    int itemType = id + (infeVersion == 2 ? 2 : 4) + 2;
                    if (itemType + 4 <= infeEnd && is(data, itemType, type)) {
                        if (contentType == null) return itemId;
                        // The item's name, then its content type and its encoding, each ending in a zero.
                        int name = itemType + 4;
                        int content = afterZero(data, name, infeEnd);
                        int encoding = afterZero(data, content, infeEnd);
                        if (encoding > 0
                                && encoding - content == contentType.length() + 1
                                && is(data, content, contentType)
                                && (encoding >= infeEnd || data[encoding] == 0)) {
                            return itemId;
                        }
                    }
                }
            }
            at += (int) size;
        }
        return -1;
    }

    /** Where the text from {@code at} ends, after its zero; -1 when it runs past {@code end}. */
    private static int afterZero(byte[] data, int at, int end) {
        if (at < 0) return -1;
        for (int i = at; i < end; i++) {
            if (data[i] == 0) return i + 1;
        }
        return -1;
    }

    /** An item's bytes, by its iloc entry: from the file (construction method 0) or the idat box (1). */
    static byte @Nullable [] itemData(
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

    /** The zlib stream in {@code data} from {@code from} on, inflated; null when damaged or over {@code max}. */
    static byte @Nullable [] inflated(byte[] data, int from, int max) {
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(data, from, data.length - from);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[16 << 10];
            while (!inflater.finished()) {
                int n = inflater.inflate(buffer);
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) return null;
                out.write(buffer, 0, n);
                if (out.size() > max) return null;
            }
            return out.toByteArray();
        } catch (DataFormatException e) {
            return null;
        } finally {
            inflater.end();
        }
    }

    /**
     * The first box of {@code type} among those from {@code start} to {@code end} of the file, as
     * {position of its content, length of its content}; null when there is none.
     */
    static long @Nullable [] box(FileChannel channel, long start, long end, String type) throws IOException {
        long at = start;
        while (at + 8 <= end) {
            byte[] header = bytes(channel, at, 16);
            if (header == null) header = bytes(channel, at, 8);
            if (header == null) return null;
            long size = u32(header, 0, false);
            int headerSize = 8;
            if (size == 1) {
                if (header.length < 16) return null;
                size = u32(header, 8, false) << 32 | u32(header, 12, false);
                headerSize = 16;
            } else if (size == 0) {
                size = end - at;
            }
            if (size < headerSize) return null;
            if (is(header, 4, type)) return new long[] {at + headerSize, size - headerSize};
            at += size;
        }
        return null;
    }

    /** {@link #box} within bytes already read: {offset of its content, length of its content}. */
    static int @Nullable [] child(byte[] data, int start, int end, String type) {
        int at = start;
        while (at + 8 <= end) {
            long size = u32(data, at, false);
            if (size < 8 || at + size > end) return null;
            if (is(data, at + 4, type)) return new int[] {at + 8, (int) size - 8};
            at += (int) size;
        }
        return null;
    }

    static byte @Nullable [] bytes(FileChannel channel, long at, int length) throws IOException {
        if (at < 0 || length < 0 || at + length > channel.size()) return null;
        ByteBuffer buffer = ByteBuffer.allocate(length);
        while (buffer.hasRemaining()) {
            if (channel.read(buffer, at + buffer.position()) < 0) return null;
        }
        return buffer.array();
    }

    static boolean is(byte[] data, int at, String text) {
        byte[] wanted = text.getBytes(StandardCharsets.ISO_8859_1);
        return at + wanted.length <= data.length
                && Arrays.equals(data, at, at + wanted.length, wanted, 0, wanted.length);
    }

    static int u8(byte[] data, int at) {
        return data[at] & 0xFF;
    }

    static int u16(byte[] data, int at, boolean little) {
        return little ? u8(data, at) | u8(data, at + 1) << 8 : u8(data, at) << 8 | u8(data, at + 1);
    }

    static long u32(byte[] data, int at, boolean little) {
        long low = u16(data, at + (little ? 0 : 2), little);
        long high = u16(data, at + (little ? 2 : 0), little);
        return high << 16 | low;
    }

    /** A big-endian number of {@code size} bytes (0, 4 or 8, as iloc's fields are). */
    static long sized(byte[] data, int at, int size) {
        long value = 0;
        for (int i = 0; i < size; i++) value = value << 8 | u8(data, at + i);
        return value;
    }
}
