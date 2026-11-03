package com.mirada.folder;

import java.nio.file.Path;
import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageFormatsTest {

    @Test
    void eachExtensionHasOneDecoder() {
        assertEquals(ImageFormats.Decoder.TURBOJPEG, ImageFormats.decoderFor(Path.of("a.JPG")));
        assertEquals(ImageFormats.Decoder.JAVAFX, ImageFormats.decoderFor(Path.of("a.gif")));
        assertEquals(ImageFormats.Decoder.LIBWEBP, ImageFormats.decoderFor(Path.of("a.webp")));
        assertEquals(ImageFormats.Decoder.IMAGEIO, ImageFormats.decoderFor(Path.of("a.TIFF")));
        assertEquals(ImageFormats.Decoder.IMAGEIO, ImageFormats.decoderFor(Path.of("a.psd")));
        assertEquals(ImageFormats.Decoder.LIBHEIF, ImageFormats.decoderFor(Path.of("a.HEIC")));
        assertEquals(ImageFormats.Decoder.LIBHEIF, ImageFormats.decoderFor(Path.of("a.avif")));
        assertEquals(ImageFormats.Decoder.LIBJXL, ImageFormats.decoderFor(Path.of("a.jxl")));
        assertNull(ImageFormats.decoderFor(Path.of("a.cr3")));
        assertNull(ImageFormats.decoderFor(Path.of("notes.txt")));
    }

    @Test
    void onlyTheNativeDecodersTurnTheImageThemselves() {
        assertTrue(ImageFormats.Decoder.LIBHEIF.turnsUpright());
        assertTrue(ImageFormats.Decoder.LIBJXL.turnsUpright());
        assertFalse(ImageFormats.Decoder.JAVAFX.turnsUpright());
        assertFalse(ImageFormats.Decoder.TURBOJPEG.turnsUpright());
        assertFalse(ImageFormats.Decoder.IMAGEIO.turnsUpright());
    }

    @Test
    void imageIoAndLibjxlHandBackColoursOtherThanTheFile() {
        assertTrue(ImageFormats.Decoder.JAVAFX.keepsColours());
        assertTrue(ImageFormats.Decoder.TURBOJPEG.keepsColours());
        assertTrue(ImageFormats.Decoder.LIBWEBP.keepsColours());
        assertTrue(ImageFormats.Decoder.LIBHEIF.keepsColours());
        assertFalse(ImageFormats.Decoder.IMAGEIO.keepsColours());
        assertFalse(ImageFormats.Decoder.LIBJXL.keepsColours());
    }

    /** A plugin dropped from the pom would otherwise show only as "no decoder accepts this file". */
    @Test
    void everyImageIoExtensionHasAReader() {
        for (String extension : ImageFormats.extensionsFor(ImageFormats.Decoder.IMAGEIO)) {
            assertTrue(ImageIO.getImageReadersBySuffix(extension).hasNext(), "no ImageIO reader for ." + extension);
        }
    }
}
