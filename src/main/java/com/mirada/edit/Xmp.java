package com.mirada.edit;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

import static com.mirada.edit.Containers.MAX_BLOCK;
import static com.mirada.edit.Containers.box;
import static com.mirada.edit.Containers.bytes;
import static com.mirada.edit.Containers.is;
import static com.mirada.edit.Containers.u16;
import static com.mirada.edit.Containers.u32;
import static com.mirada.edit.Containers.u8;

/**
 * A file's XMP packet, and the same packet made true of an edited copy, so a saved edit keeps the
 * title, the description, the keywords, the rating and the rights of the picture it was made from.
 *
 * <p>Read from JPEG (APP1 "http://ns.adobe.com/xap/1.0/"), PNG (iTXt "XML:com.adobe.xmp"), WebP
 * (XMP), HEIC and AVIF (the "mime" item of type application/rdf+xml) and JPEG XL (the xml box).
 * JPEG's extended XMP, the part past 64 KB in further segments, is not read.
 *
 * <p>The edited copy is the packet's own text with a few values changed, as {@link ExifBlock}
 * changes its block: the orientation becomes 1 and the sizes the edit's, wherever the packet
 * repeats EXIF's (tiff: and exif:), and what describes the picture before the edit goes (the xmp:
 * thumbnail, Camera Raw's crop, the note naming extended XMP that is not carried). A packet whose
 * orientation this cannot find to change is dropped, as it would turn the saved picture again.
 */
public final class Xmp {

    static final String TIFF = "http://ns.adobe.com/tiff/1.0/";
    static final String EXIF = "http://ns.adobe.com/exif/1.0/";
    static final String XMP = "http://ns.adobe.com/xap/1.0/";
    static final String NOTE = "http://ns.adobe.com/xmp/note/";
    static final String CAMERA_RAW = "http://ns.adobe.com/camera-raw-settings/1.0/";

    /** What starts JPEG's APP1 segment of XMP, its zero included. */
    static final byte[] JPEG_HEADER = (XMP + "\0").getBytes(StandardCharsets.ISO_8859_1);

    /** The most of a packet JPEG's APP1 segment holds, after its length and its header. */
    public static final int MAX_IN_JPEG = 0xFFFF - 2 - JPEG_HEADER.length;

    /** PNG's iTXt keyword for XMP. */
    static final String PNG_KEYWORD = "XML:com.adobe.xmp";

    private Xmp() {}

    /**
     * The XMP of {@code source}, made true of an edit of it {@code width} × {@code height} pixels;
     * null when it has none, or none this can read or change. A save goes ahead without it.
     */
    public static byte @Nullable [] carried(Path source, int width, int height) {
        try {
            byte[] packet = read(source);
            return packet == null ? null : forEdited(packet, width, height);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** {@code file}'s XMP packet, by what the file is rather than its name; null when it has none. */
    public static byte @Nullable [] read(Path file) throws IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            byte[] head = bytes(channel, 0, 12);
            if (head == null) return null;
            if (u8(head, 0) == 0xFF && u8(head, 1) == 0xD8) return fromJpeg(channel);
            if (u8(head, 0) == 0x89 && is(head, 1, "PNG")) return fromPng(channel);
            if (is(head, 0, "RIFF") && is(head, 8, "WEBP")) return fromWebp(channel);
            if (is(head, 4, "ftyp")) return Containers.heifItem(channel, "mime", "application/rdf+xml");
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
            if (type == 0xE1 && length > 2 + JPEG_HEADER.length) {
                byte[] segment = bytes(channel, at + 4, length - 2);
                if (segment != null
                        && Arrays.equals(segment, 0, JPEG_HEADER.length, JPEG_HEADER, 0, JPEG_HEADER.length)) {
                    return Arrays.copyOfRange(segment, JPEG_HEADER.length, segment.length);
                }
            }
            at += 2 + length;
        }
    }

    /**
     * iTXt: the keyword, a zero, whether it is compressed and how, the language and the translated
     * keyword (each ending in a zero), then the text.
     */
    private static byte @Nullable [] fromPng(FileChannel channel) throws IOException {
        long at = 8;
        byte[] keyword = (PNG_KEYWORD + "\0").getBytes(StandardCharsets.ISO_8859_1);
        while (true) {
            byte[] header = bytes(channel, at, 8);
            if (header == null) return null;
            long length = u32(header, 0, false);
            if (is(header, 4, "IEND") || length > Integer.MAX_VALUE) return null;
            if (is(header, 4, "iTXt") && length > keyword.length + 2) {
                byte[] data = length > MAX_BLOCK ? null : bytes(channel, at + 8, (int) length);
                if (data != null && Arrays.equals(data, 0, keyword.length, keyword, 0, keyword.length)) {
                    int flag = keyword.length;
                    int language = flag + 2;
                    int translated = zeroAfter(data, language);
                    int text = translated < 0 ? -1 : zeroAfter(data, translated);
                    if (text < 0) return null;
                    if (data[flag] == 0) return Arrays.copyOfRange(data, text, data.length);
                    return data[flag + 1] == 0 ? Containers.inflated(data, text, MAX_BLOCK) : null;
                }
            }
            at += 12 + length;
        }
    }

    private static int zeroAfter(byte[] data, int at) {
        for (int i = at; i < data.length; i++) {
            if (data[i] == 0) return i + 1;
        }
        return -1;
    }

    private static byte @Nullable [] fromWebp(FileChannel channel) throws IOException {
        long at = 12;
        while (true) {
            byte[] header = bytes(channel, at, 8);
            if (header == null) return null;
            long size = u32(header, 4, true);
            if (is(header, 0, "XMP ")) return size > MAX_BLOCK ? null : bytes(channel, at + 8, (int) size);
            at += 8 + size + (size & 1);
        }
    }

    private static byte @Nullable [] fromJxl(FileChannel channel) throws IOException {
        long[] box = box(channel, 0, channel.size(), "xml ");
        return box == null || box[1] > MAX_BLOCK ? null : bytes(channel, box[0], (int) box[1]);
    }

    /**
     * A copy of {@code packet} as XMP of an edit {@code width} × {@code height} pixels, saved upright;
     * null when it is not UTF-8 text, or holds an orientation this cannot set.
     */
    public static byte @Nullable [] forEdited(byte[] packet, int width, int height) {
        String text;
        try {
            text = StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(packet))
                    .toString();
        } catch (CharacterCodingException e) {
            return null;
        }
        if (!text.contains("x:xmpmeta") && !text.contains("rdf:RDF")) return null;
        // A namespace bound as the default would name its properties without a prefix; not followed.
        for (String namespace : List.of(TIFF, EXIF)) {
            if (Pattern.compile("xmlns\\s*=\\s*[\"']" + Pattern.quote(namespace))
                    .matcher(text)
                    .find()) return null;
        }
        String out = text;
        for (String tiff : prefixes(text, TIFF)) {
            Changed orientation = set(out, tiff, "Orientation", "1");
            if (orientation.left() > 0) return null;
            out = orientation.text();
            out = set(out, tiff, "ImageWidth", Integer.toString(width)).text();
            out = set(out, tiff, "ImageLength", Integer.toString(height)).text();
        }
        for (String exif : prefixes(text, EXIF)) {
            out = set(out, exif, "PixelXDimension", Integer.toString(width)).text();
            out = set(out, exif, "PixelYDimension", Integer.toString(height)).text();
        }
        for (String xmp : prefixes(text, XMP)) out = removed(out, xmp, "Thumbnails");
        for (String note : prefixes(text, NOTE)) out = removed(out, note, "HasExtendedXMP");
        for (String raw : prefixes(text, CAMERA_RAW)) {
            for (String name : List.of(
                    "HasCrop", "CropTop", "CropLeft", "CropBottom", "CropRight", "CropAngle", "CropConstrainToWarp")) {
                out = removed(out, raw, name);
            }
        }
        return out.getBytes(StandardCharsets.UTF_8);
    }

    /** The prefixes {@code text} binds to {@code namespace}, each once. */
    static List<String> prefixes(String text, String namespace) {
        Matcher bound = Pattern.compile("xmlns:([\\w.-]+)\\s*=\\s*[\"']" + Pattern.quote(namespace) + "[\"']")
                .matcher(text);
        List<String> found = new ArrayList<>();
        while (bound.find()) {
            if (!found.contains(bound.group(1))) found.add(bound.group(1));
        }
        return found;
    }

    /** Text after a change, and how many mentions of the property were left unchanged. */
    record Changed(String text, int left) {}

    /**
     * {@code text} with {@code prefix:name} set to {@code value}, as an attribute ({@code
     * p:name="6"}) and as a simple element ({@code <p:name>6</p:name>}); {@link Changed#left} counts
     * the mentions in neither form.
     */
    static Changed set(String text, String prefix, String name, String value) {
        String property = Pattern.quote(prefix + ":" + name);
        Matcher attribute = Pattern.compile("(\\s" + property + "\\s*=\\s*)([\"'])[^\"']*\\2")
                .matcher(text);
        String out = attribute.replaceAll(m -> Matcher.quoteReplacement(m.group(1) + m.group(2) + value + m.group(2)));
        Matcher element = Pattern.compile("(<" + property + ">)\\s*[^<\\s]*\\s*(</" + property + ">)")
                .matcher(out);
        out = element.replaceAll(m -> Matcher.quoteReplacement(m.group(1) + value + m.group(2)));
        // Every mention (an opening tag or an attribute; a closing tag is "</") is one just set, or
        // one in a form not followed.
        long mentions = Pattern.compile("[<\\s]" + property + "\\b")
                .matcher(out)
                .results()
                .count();
        long attributes = Pattern.compile("\\s" + property + "\\s*=\\s*([\"'])" + Pattern.quote(value) + "\\1")
                .matcher(out)
                .results()
                .count();
        long elements = Pattern.compile("<" + property + ">" + Pattern.quote(value) + "</" + property + ">")
                .matcher(out)
                .results()
                .count();
        return new Changed(out, (int) (mentions - attributes - elements));
    }

    /** {@code text} without {@code prefix:name}, as an attribute or an element with its content. */
    static String removed(String text, String prefix, String name) {
        String property = Pattern.quote(prefix + ":" + name);
        String out = Pattern.compile("\\s" + property + "\\s*=\\s*([\"'])[^\"']*\\1")
                .matcher(text)
                .replaceAll("");
        out = Pattern.compile("\\s*<" + property + "\\s*/>").matcher(out).replaceAll("");
        return Pattern.compile("\\s*<" + property + "(\\s[^>]*)?>.*?</" + property + ">", Pattern.DOTALL)
                .matcher(out)
                .replaceAll("");
    }
}
