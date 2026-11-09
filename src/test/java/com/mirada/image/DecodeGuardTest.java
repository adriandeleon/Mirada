package com.mirada.image;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PERF-12: an image's size, read from its header, is weighed against the heap before its pixels are decoded. */
class DecodeGuardTest {

    private static final long MB = 1L << 20;
    private static final long HEAP = 2048 * MB;

    @Test
    void aLargePhotoFitsAnEmptyHeap() {
        // 48 megapixels, eight bytes each while decoding: 384 MB.
        assertTrue(DecodeGuard.fits(8000L * 6000, HEAP, 100 * MB));
        assertNull(DecodeGuard.refusal(8000, 6000, HEAP, 100 * MB));
    }

    @Test
    void whatTheDecodeCostsNotTheFileIsWhatMustFit() {
        long used = HEAP - 400 * MB; // 400 MB free: the pixels' 384 and the margin do not fit
        assertFalse(DecodeGuard.fits(8000L * 6000, HEAP, used));
        String reason = DecodeGuard.refusal(8000, 6000, HEAP, used);
        assertEquals(
                "the image is too large to open with the memory left (8000 × 6000 pixels need 366 MB, 400 MB is"
                        + " free)",
                reason);
    }

    @Test
    void pastWhatAnArrayHoldsNothingFitsWhateverTheHeap() {
        long huge = Long.MAX_VALUE / 2;
        assertFalse(DecodeGuard.fits(DecodeGuard.MAX_PIXELS + 1, huge, 0));
        assertEquals(
                "the image is too large to open (100000 × 100000 pixels)",
                DecodeGuard.refusal(100_000, 100_000, huge, 0));
        assertNotNull(DecodeGuard.refusal(Long.MAX_VALUE, Long.MAX_VALUE, huge, 0), "no overflow");
    }

    @Test
    void aSizeWithNoPixelsIsLeftToTheDecoderToRefuse() {
        assertNull(DecodeGuard.refusal(0, 100, 0, 0));
        assertNull(DecodeGuard.refusal(100, -1, 0, 0));
    }

    @Test
    void usedAboveMaxIsNothingFree() {
        assertFalse(DecodeGuard.fits(1, HEAP, HEAP + MB));
    }

    @Test
    void sizesReadAsAPersonReadsThem() {
        assertEquals("310 MB", DecodeGuard.bytes(310 * MB));
        assertEquals("2.4 GB", DecodeGuard.bytes((long) (2.4 * 1024) * MB));
    }

    @Test
    void checkThrowsTooLargeAndAllocateGivesTheArray() {
        assertThrows(DecodeGuard.TooLarge.class, () -> DecodeGuard.check(100_000, 100_000));
        assertDoesNotThrow(() -> DecodeGuard.check(64, 48));
        assertEquals(12, assertDoesNotThrow(() -> ArgbPixels.allocate(4, 3)).length);
    }

    @Test
    void anAnimationTooLongToHoldIsNotDecodedPastItsFirstFrame() {
        assertTrue(Animation.fits(1000, 1000, 64));
        assertFalse(Animation.fits(1000, 1000, 68), "68 megapixels in all");
        assertFalse(Animation.fits(4000, 4000, 5));
    }

    // ---- The headers JavaFX's decoder would not let us read first ----

    private static byte[] png(int width, int height) {
        ByteBuffer out = ByteBuffer.allocate(33);
        out.put(new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
        out.putInt(13).put("IHDR".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        out.putInt(width).putInt(height);
        out.put(new byte[] {8, 6, 0, 0, 0, 0, 0, 0, 0});
        return out.array();
    }

    @Test
    void theSizeOfAPngGifAndBmpFromTheirFirstBytes() {
        assertEquals(new ImageHeader.Size(640, 480), ImageHeader.of(png(640, 480)));
        byte[] gif = {'G', 'I', 'F', '8', '9', 'a', (byte) 0x80, 0x02, (byte) 0xE0, 0x01};
        assertEquals(new ImageHeader.Size(640, 480), ImageHeader.of(gif));
        byte[] bmp = new byte[26];
        bmp[0] = 'B';
        bmp[1] = 'M';
        bmp[14] = 40; // BITMAPINFOHEADER
        ByteBuffer.wrap(bmp, 18, 8)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN)
                .putInt(640)
                .putInt(-480);
        assertEquals(new ImageHeader.Size(640, 480), ImageHeader.of(bmp), "rows stored top first");
        byte[] os2 = new byte[26];
        os2[0] = 'B';
        os2[1] = 'M';
        os2[14] = 12; // BITMAPCOREHEADER: 16-bit sizes
        ByteBuffer.wrap(os2, 18, 4)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN)
                .putShort((short) 640)
                .putShort((short) 480);
        assertEquals(new ImageHeader.Size(640, 480), ImageHeader.of(os2));
        assertNull(ImageHeader.of(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}), "a JPEG reads its own");
        assertNull(ImageHeader.of(new byte[0]));
        assertNull(ImageHeader.of(new byte[] {'B', 'M'}), "cut short");
    }

    @Test
    void aPngThatWillNotFitIsRefusedBeforeJavaFxIsLetAtIt(@TempDir Path dir) throws IOException {
        Path huge = Files.write(dir.resolve("huge.png"), png(100_000, 100_000));
        UncheckedIOException refused = assertThrows(UncheckedIOException.class, () -> ImageCache.checkHeader(huge));
        assertInstanceOf(DecodeGuard.TooLarge.class, refused.getCause());
        assertDoesNotThrow(() -> ImageCache.checkHeader(Files.write(dir.resolve("small.png"), png(64, 48))));
        assertDoesNotThrow(() -> ImageCache.checkHeader(dir.resolve("not-there.png")), "left to the decoder");
    }

    @Test
    void aTiffThatWillNotFitIsRefusedFromItsHeader(@TempDir Path dir) throws IOException {
        // A little-endian TIFF whose one IFD says 100000 × 100000, with no pixels behind it.
        ByteBuffer tiff = ByteBuffer.allocate(8 + 2 + 12 * 2 + 4).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        tiff.put((byte) 'I').put((byte) 'I').putShort((short) 42).putInt(8);
        tiff.putShort((short) 2);
        tiff.putShort((short) 256).putShort((short) 4).putInt(1).putInt(100_000); // ImageWidth, LONG
        tiff.putShort((short) 257).putShort((short) 4).putInt(1).putInt(100_000); // ImageLength, LONG
        tiff.putInt(0);
        Path file = Files.write(dir.resolve("huge.tif"), tiff.array());
        IOException refused = assertThrows(IOException.class, () -> ImageIoDecoder.pixels(file));
        assertInstanceOf(DecodeGuard.TooLarge.class, refused);
    }
}
