package com.mirada.edit;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.zip.DeflaterOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XmpTest {

    /** A packet as a phone or Lightroom writes one: EXIF's values repeated, and a title. */
    static final String PACKET = """
            <?xpacket begin="﻿" id="W5M0MpCehiHzreSzNTczkc9d"?>
            <x:xmpmeta xmlns:x="adobe:ns:meta/">
             <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
              <rdf:Description rdf:about=""
                xmlns:tiff="http://ns.adobe.com/tiff/1.0/"
                xmlns:exif="http://ns.adobe.com/exif/1.0/"
                xmlns:xmp="http://ns.adobe.com/xap/1.0/"
                xmlns:xmpNote="http://ns.adobe.com/xmp/note/"
                xmlns:crs="http://ns.adobe.com/camera-raw-settings/1.0/"
                xmlns:dc="http://purl.org/dc/elements/1.1/"
                tiff:Orientation="6"
                tiff:ImageWidth="4000"
                xmp:Rating="4"
                xmpNote:HasExtendedXMP="0123456789ABCDEF0123456789ABCDEF"
                crs:HasCrop="True"
                crs:CropTop="0.1">
               <tiff:ImageLength>3000</tiff:ImageLength>
               <exif:PixelXDimension>4000</exif:PixelXDimension>
               <exif:PixelYDimension>3000</exif:PixelYDimension>
               <xmp:Thumbnails>
                <rdf:Alt><rdf:li rdf:parseType="Resource"><xmpGImg:image>/9j/4AAQ</xmpGImg:image></rdf:li></rdf:Alt>
               </xmp:Thumbnails>
               <dc:title><rdf:Alt><rdf:li xml:lang="x-default">Harbour at dusk</rdf:li></rdf:Alt></dc:title>
              </rdf:Description>
             </rdf:RDF>
            </x:xmpmeta>
            <?xpacket end="w"?>""";

    static byte[] packet() {
        return PACKET.getBytes(StandardCharsets.UTF_8);
    }

    private static Path fixture(String name) throws Exception {
        return Path.of(
                Objects.requireNonNull(XmpTest.class.getResource(name), name).toURI());
    }

    @Test
    void anEditedPacketIsUprightAtItsNewSize() {
        String edited = new String(Objects.requireNonNull(Xmp.forEdited(packet(), 1200, 700)), StandardCharsets.UTF_8);
        assertTrue(edited.contains("tiff:Orientation=\"1\""), edited);
        assertTrue(edited.contains("tiff:ImageWidth=\"1200\""), edited);
        assertTrue(edited.contains("<tiff:ImageLength>700</tiff:ImageLength>"), edited);
        assertTrue(edited.contains("<exif:PixelXDimension>1200</exif:PixelXDimension>"), edited);
        assertTrue(edited.contains("<exif:PixelYDimension>700</exif:PixelYDimension>"), edited);
        // What described the picture before the edit is gone; what describes the photo stays.
        assertFalse(edited.contains("Thumbnails"), edited);
        assertFalse(edited.contains("HasExtendedXMP"), edited);
        assertFalse(edited.contains("crs:HasCrop"), edited);
        assertFalse(edited.contains("crs:CropTop"), edited);
        assertTrue(edited.contains("Harbour at dusk"), edited);
        assertTrue(edited.contains("xmp:Rating=\"4\""), edited);
        assertTrue(edited.endsWith("<?xpacket end=\"w\"?>"), edited);
    }

    @Test
    void anOrientationWrittenAnotherWayDropsThePacket() {
        // A tiff:Orientation this does not follow would turn the saved, upright picture again.
        String resource = PACKET.replace("tiff:Orientation=\"6\"", "")
                .replace("<dc:title>", "<tiff:Orientation rdf:parseType=\"Resource\"/><dc:title>");
        assertNull(Xmp.forEdited(resource.getBytes(StandardCharsets.UTF_8), 1, 1));
        // A prefix other than tiff: is followed.
        String other = PACKET.replace("xmlns:tiff=", "xmlns:t=").replace("tiff:", "t:");
        String edited = new String(Objects.requireNonNull(Xmp.forEdited(other.getBytes(StandardCharsets.UTF_8), 5, 6)));
        assertTrue(edited.contains("t:Orientation=\"1\""), edited);
        // The default namespace as tiff's is not.
        String bare = PACKET.replace("xmlns:tiff=", "xmlns=");
        assertNull(Xmp.forEdited(bare.getBytes(StandardCharsets.UTF_8), 1, 1));
    }

    @Test
    void whatIsNotXmpIsDropped() {
        assertNull(Xmp.forEdited(new byte[] {(byte) 0xFF, (byte) 0xFE, 0, 0}, 1, 1));
        assertNull(Xmp.forEdited("plain text".getBytes(StandardCharsets.UTF_8), 1, 1));
        assertNull(Xmp.carried(Path.of("no such file.jpg"), 1, 1));
    }

    @Test
    void filesWithoutXmpHaveNone() throws Exception {
        for (String name :
                new String[] {"exif.jpg", "red-blue.png", "red-blue.webp", "red-blue.heic", "red-blue.jxl"}) {
            assertNull(Xmp.read(fixture("/com/mirada/image/" + name)), name);
        }
    }

    @Test
    void readsThePacketFromEveryFormatThatCarriesOne(@TempDir Path dir) throws Exception {
        byte[] packet = packet();
        byte[] jpeg =
                Encoder.withSegments(Files.readAllBytes(fixture("/com/mirada/image/exif.jpg")), null, null, packet);
        assertArrayEquals(packet, Xmp.read(write(dir, "a.jpg", jpeg)), "jpeg");
        byte[] png =
                Encoder.withChunks(Files.readAllBytes(fixture("/com/mirada/image/red-blue.png")), null, null, packet);
        assertArrayEquals(packet, Xmp.read(write(dir, "a.png", png)), "png");
        assertArrayEquals(packet, Xmp.read(write(dir, "z.png", compressedPng(packet))), "compressed iTXt");
        assertArrayEquals(packet, Xmp.read(write(dir, "a.webp", webp(packet))), "webp");
        assertArrayEquals(packet, Xmp.read(write(dir, "a.heic", heif(packet))), "heif");
        assertArrayEquals(packet, Xmp.read(write(dir, "a.jxl", jxl(packet))), "jxl");
    }

    @Test
    void theEditedPacketGoesIntoJpegPngAndTiffAndReadsBack(@TempDir Path dir) throws IOException {
        int[] pixels = new int[8 * 4];
        java.util.Arrays.fill(pixels, 0xFF336699);
        Raster image = new Raster(8, 4, pixels);
        byte[] edited = Objects.requireNonNull(Xmp.forEdited(packet(), 8, 4));
        for (String extension : new String[] {"jpg", "png"}) {
            Path file = dir.resolve("with-xmp." + extension);
            Encoder.write(image, file, null, null, edited);
            assertArrayEquals(edited, Xmp.read(file), extension);
        }
        Path tiff = dir.resolve("with-xmp.tif");
        Encoder.write(image, tiff, null, null, edited);
        assertTrue(new String(Files.readAllBytes(tiff), StandardCharsets.ISO_8859_1).contains("Harbour at dusk"));
    }

    private static Path write(Path dir, String name, byte[] bytes) throws IOException {
        return Files.write(dir.resolve(name), bytes);
    }

    /** A PNG whose iTXt holds the packet deflated, as some writers do. */
    private static byte[] compressedPng(byte[] packet) throws Exception {
        byte[] png = Files.readAllBytes(fixture("/com/mirada/image/red-blue.png"));
        ByteArrayOutputStream deflated = new ByteArrayOutputStream();
        try (DeflaterOutputStream out = new DeflaterOutputStream(deflated)) {
            out.write(packet);
        }
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        data.writeBytes("XML:com.adobe.xmp\0\1\0en\0\0".getBytes(StandardCharsets.ISO_8859_1));
        data.writeBytes(deflated.toByteArray());
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update("iTXt".getBytes(StandardCharsets.ISO_8859_1));
        crc.update(data.toByteArray());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(png, 0, 33); // the signature and IHDR
        out.writeBytes(ByteBuffer.allocate(4).putInt(data.size()).array());
        out.writeBytes("iTXt".getBytes(StandardCharsets.ISO_8859_1));
        out.writeBytes(data.toByteArray());
        out.writeBytes(ByteBuffer.allocate(4).putInt((int) crc.getValue()).array());
        out.write(png, 33, png.length - 33);
        return out.toByteArray();
    }

    private static byte[] webp(byte[] packet) {
        ByteArrayOutputStream chunks = new ByteArrayOutputStream();
        chunks.writeBytes("WEBP".getBytes(StandardCharsets.ISO_8859_1));
        riff(chunks, "VP8X", new byte[10]);
        riff(chunks, "VP8L", new byte[] {0x2F, 0, 0, 0, 0});
        riff(chunks, "XMP ", packet);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes("RIFF".getBytes(StandardCharsets.ISO_8859_1));
        out.writeBytes(ByteBuffer.allocate(4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(chunks.size())
                .array());
        out.writeBytes(chunks.toByteArray());
        return out.toByteArray();
    }

    private static void riff(ByteArrayOutputStream out, String type, byte[] data) {
        out.writeBytes(type.getBytes(StandardCharsets.ISO_8859_1));
        out.writeBytes(ByteBuffer.allocate(4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(data.length)
                .array());
        out.writeBytes(data);
        if ((data.length & 1) != 0) out.write(0);
    }

    /**
     * The HEIF a phone writes, reduced to what XMP needs: a meta box listing item 1 as "mime",
     * application/rdf+xml, its bytes in idat (iloc version 1, construction method 1).
     */
    private static byte[] heif(byte[] packet) {
        ByteArrayOutputStream infe = new ByteArrayOutputStream();
        infe.writeBytes(new byte[] {2, 0, 0, 0, 0, 1, 0, 0});
        infe.writeBytes("mime\0application/rdf+xml\0".getBytes(StandardCharsets.ISO_8859_1));
        ByteArrayOutputStream iinf = new ByteArrayOutputStream();
        iinf.writeBytes(new byte[] {0, 0, 0, 0, 0, 1});
        iinf.writeBytes(box("infe", infe.toByteArray()));
        ByteBuffer iloc = ByteBuffer.allocate(4 + 2 + 2 + 2 + 2 + 2 + 2 + 4 + 4);
        iloc.put(new byte[] {1, 0, 0, 0}).put((byte) 0x44).put((byte) 0);
        iloc.putShort((short) 1).putShort((short) 1).putShort((short) 1).putShort((short) 0);
        iloc.putShort((short) 1).putInt(0).putInt(packet.length);
        ByteArrayOutputStream meta = new ByteArrayOutputStream();
        meta.writeBytes(new byte[4]);
        meta.writeBytes(box("iinf", iinf.toByteArray()));
        meta.writeBytes(box("iloc", iloc.array()));
        meta.writeBytes(box("idat", packet));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(box("ftyp", "heic\0\0\0\0mif1heic".getBytes(StandardCharsets.ISO_8859_1)));
        out.writeBytes(box("meta", meta.toByteArray()));
        return out.toByteArray();
    }

    private static byte[] jxl(byte[] packet) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(new byte[] {0, 0, 0, 12, 'J', 'X', 'L', ' ', 0x0D, 0x0A, (byte) 0x87, 0x0A});
        out.writeBytes(box("ftyp", "jxl \0\0\0\0jxl ".getBytes(StandardCharsets.ISO_8859_1)));
        out.writeBytes(box("xml ", packet));
        out.writeBytes(box("jxlc", new byte[] {(byte) 0xFF, 0x0A, 1, 2, 3}));
        return out.toByteArray();
    }

    private static byte[] box(String type, byte[] data) {
        return ByteBuffer.allocate(8 + data.length)
                .putInt(8 + data.length)
                .put(type.getBytes(StandardCharsets.ISO_8859_1))
                .put(data)
                .array();
    }
}
