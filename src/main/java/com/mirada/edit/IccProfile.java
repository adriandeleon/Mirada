package com.mirada.edit;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

import org.jspecify.annotations.Nullable;

import static com.mirada.edit.Containers.MAX_BLOCK;
import static com.mirada.edit.Containers.bytes;
import static com.mirada.edit.Containers.child;
import static com.mirada.edit.Containers.is;
import static com.mirada.edit.Containers.u16;
import static com.mirada.edit.Containers.u32;
import static com.mirada.edit.Containers.u8;

/**
 * A file's embedded ICC profile, the description of what its pixel values mean as colours, so a
 * saved edit of a wide-gamut photo (an iPhone's Display P3, a camera's Adobe RGB) keeps its colours
 * in an app that manages them instead of turning dull.
 *
 * <p>Read from JPEG (APP2 "ICC_PROFILE", in as many segments as it takes), PNG (iCCP), WebP (ICCP)
 * and HEIC and AVIF (the primary image's colr property, when it holds a profile rather than nclx
 * codes). Only an RGB profile is carried: the edit is saved as RGB, so a CMYK JPEG's profile (JavaFX
 * converts its pixels) or a grey one no longer describes it. The caller decides whether the decoder
 * left the pixels in the file's colours ({@code ImageCache.keepsColours}); libjxl does not.
 */
public final class IccProfile {

    /** The largest profile carried; real ones are a few hundred bytes to a few hundred KB. */
    static final int MAX_SIZE = 4 << 20;

    private static final int HEADER = 128;

    private IccProfile() {}

    /** {@code source}'s RGB profile, or null when it has none, or none this can read. */
    public static byte @Nullable [] carried(Path source) {
        try {
            return read(source);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** {@code file}'s profile, by what the file is rather than its name, when it is an RGB one. */
    public static byte @Nullable [] read(Path file) throws IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            byte[] head = bytes(channel, 0, 12);
            if (head == null) return null;
            byte[] profile = null;
            if (u8(head, 0) == 0xFF && u8(head, 1) == 0xD8) profile = fromJpeg(channel);
            else if (u8(head, 0) == 0x89 && is(head, 1, "PNG")) profile = fromPng(channel);
            else if (is(head, 0, "RIFF") && is(head, 8, "WEBP")) profile = fromWebp(channel);
            else if (is(head, 4, "ftyp")) profile = fromHeif(channel);
            return profile == null ? null : rgb(profile);
        }
    }

    /**
     * {@code profile} up to the size its header gives, when it is a profile of RGB colours; null
     * when it is not a profile, or of another colour space.
     */
    static byte @Nullable [] rgb(byte[] profile) {
        if (profile.length < HEADER + 4 || !is(profile, 36, "acsp") || !is(profile, 16, "RGB ")) return null;
        long size = u32(profile, 0, false);
        if (size < HEADER + 4 || size > profile.length || size > MAX_SIZE) return null;
        return size == profile.length ? profile : Arrays.copyOf(profile, (int) size);
    }

    /** The APP2 segments "ICC_PROFILE\0", a sequence number and a count, then the chunk. */
    private static byte @Nullable [] fromJpeg(FileChannel channel) throws IOException {
        byte[][] chunks = null;
        long at = 2;
        while (true) {
            byte[] marker = bytes(channel, at, 4);
            if (marker == null || u8(marker, 0) != 0xFF) break;
            int type = u8(marker, 1);
            if (type == 0xDA || type == 0xD9) break; // the picture itself: no metadata after it
            int length = u16(marker, 2, false);
            if (length < 2) return null;
            if (type == 0xE2 && length > 2 + 14) {
                byte[] segment = bytes(channel, at + 4, length - 2);
                if (segment != null && is(segment, 0, "ICC_PROFILE") && segment[11] == 0) {
                    int sequence = u8(segment, 12);
                    int count = u8(segment, 13);
                    if (chunks == null) chunks = new byte[count][];
                    if (count != chunks.length || sequence < 1 || sequence > count) return null;
                    chunks[sequence - 1] = Arrays.copyOfRange(segment, 14, segment.length);
                }
            }
            at += 2 + length;
        }
        if (chunks == null) return null;
        ByteArrayOutputStream joined = new ByteArrayOutputStream();
        for (byte[] chunk : chunks) {
            if (chunk == null) return null; // a segment is missing: half a profile is none
            joined.writeBytes(chunk);
        }
        return joined.toByteArray();
    }

    /** iCCP: a name of 1 to 79 bytes, a zero, compression method 0, then the profile, deflated. */
    private static byte @Nullable [] fromPng(FileChannel channel) throws IOException {
        long at = 8;
        while (true) {
            byte[] header = bytes(channel, at, 8);
            if (header == null) return null;
            long length = u32(header, 0, false);
            if (is(header, 4, "IDAT") || is(header, 4, "IEND") || length > Integer.MAX_VALUE) return null;
            if (is(header, 4, "iCCP")) {
                byte[] data = length > MAX_BLOCK ? null : bytes(channel, at + 8, (int) length);
                if (data == null) return null;
                int zero = 0;
                while (zero < data.length && data[zero] != 0) zero++;
                if (zero == 0 || zero > 79 || zero + 2 > data.length || data[zero + 1] != 0) return null;
                return Containers.inflated(data, zero + 2, MAX_SIZE);
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
            if (is(header, 0, "ICCP")) return size > MAX_SIZE ? null : bytes(channel, at + 8, (int) size);
            // The ICCP chunk comes before the image data, after VP8X.
            if (is(header, 0, "VP8 ") || is(header, 0, "VP8L") || is(header, 0, "ANIM")) return null;
            at += 8 + size + (size & 1);
        }
    }

    /**
     * HEIC and AVIF: a colr property of type "prof" or "rICC" in meta's iprp/ipco, the one the
     * primary item (pitm) is associated with in ipma; or, when it has none, the first in ipco (a
     * grid's tiles carry it rather than the grid, and all of a photo's tiles have the same one).
     */
    private static byte @Nullable [] fromHeif(FileChannel channel) throws IOException {
        byte[] data = Containers.heifMeta(channel);
        if (data == null) return null;
        int[] iprp = child(data, 4, data.length, "iprp");
        if (iprp == null) return null;
        int[] ipco = child(data, iprp[0], iprp[0] + iprp[1], "ipco");
        if (ipco == null) return null;
        int[] pitm = child(data, 4, data.length, "pitm");
        int[] ipma = child(data, iprp[0], iprp[0] + iprp[1], "ipma");
        if (pitm != null && ipma != null && pitm[1] >= 6) {
            long primary = u8(data, pitm[0]) == 0 ? u16(data, pitm[0] + 4, false) : u32(data, pitm[0] + 4, false);
            for (int index : associations(data, ipma[0], ipma[0] + ipma[1], primary)) {
                byte[] profile = colr(data, property(data, ipco[0], ipco[0] + ipco[1], index));
                if (profile != null) return profile;
            }
        }
        int at = ipco[0];
        int end = ipco[0] + ipco[1];
        while (at + 8 <= end) {
            long size = u32(data, at, false);
            if (size < 8 || at + size > end) return null;
            byte[] profile = colr(data, new int[] {at, (int) size});
            if (profile != null) return profile;
            at += (int) size;
        }
        return null;
    }

    /** The property indices (from 1) ipma associates with {@code item}; empty when it lists none. */
    private static int[] associations(byte[] data, int start, int end, long item) {
        int version = u8(data, start);
        boolean wide = (u8(data, start + 3) & 1) != 0;
        int at = start + 4;
        if (at + 4 > end) return new int[0];
        long entries = u32(data, at, false);
        at += 4;
        for (long i = 0; i < entries; i++) {
            int idSize = version < 1 ? 2 : 4;
            if (at + idSize + 1 > end) break;
            long id = version < 1 ? u16(data, at, false) : u32(data, at, false);
            int count = u8(data, at + idSize);
            at += idSize + 1;
            int each = wide ? 2 : 1;
            if (at + count * each > end) break;
            if (id == item) {
                int[] indices = new int[count];
                for (int a = 0; a < count; a++) {
                    // The top bit says whether the property is essential; the rest is the index.
                    indices[a] = wide ? u16(data, at + 2 * a, false) & 0x7FFF : u8(data, at + a) & 0x7F;
                }
                return indices;
            }
            at += count * each;
        }
        return new int[0];
    }

    /** The {@code index}th box (from 1) in ipco's content, as {its start, its size}; null if none. */
    private static int @Nullable [] property(byte[] data, int start, int end, int index) {
        int at = start;
        for (int i = 1; at + 8 <= end; i++) {
            long size = u32(data, at, false);
            if (size < 8 || at + size > end) return null;
            if (i == index) return new int[] {at, (int) size};
            at += (int) size;
        }
        return null;
    }

    /** The profile in a colr box, given as {its start, its size}; null for nclx or another box. */
    private static byte @Nullable [] colr(byte[] data, int @Nullable [] box) {
        if (box == null || box[1] < 12 || !is(data, box[0] + 4, "colr")) return null;
        int type = box[0] + 8;
        if (!is(data, type, "prof") && !is(data, type, "rICC")) return null;
        return Arrays.copyOfRange(data, type + 4, box[0] + box[1]);
    }
}
