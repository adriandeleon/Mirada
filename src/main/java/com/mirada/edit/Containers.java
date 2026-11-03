package com.mirada.edit;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.jspecify.annotations.Nullable;

/**
 * Reading the containers images come in — JPEG's segments, PNG's chunks, RIFF's chunks and the ISO
 * boxes of HEIF and JPEG XL — for the metadata a saved edit carries ({@link ExifBlock}, {@link
 * IccProfile}). Every read is bounds-checked: a damaged file gives null, never an exception from
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
