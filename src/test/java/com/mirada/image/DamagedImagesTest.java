package com.mirada.image;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import com.mirada.folder.ImageFormats;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A picture cut short (a copy interrupted, a card pulled out) is refused with a reason, never
 * crashes the decoder: every decoder of Mirada's own, given each kind of file the format matrix has
 * cut at its header, inside it and just before its end, returns pixels or throws an IOException,
 * which the window shows as "Couldn't open". A thumbnail of it is refused the same way.
 */
class DamagedImagesTest {

    @TempDir
    Path dir;

    private static List<Path> matrix() throws URISyntaxException, IOException {
        Path folder = Path.of(Objects.requireNonNull(DamagedImagesTest.class.getResource("/com/mirada/matrix"))
                .toURI());
        try (var files = Files.list(folder)) {
            return files.sorted().toList();
        }
    }

    private static void decode(ImageFormats.Decoder decoder, Path file) throws IOException {
        switch (decoder) {
            case TURBOJPEG -> TurboJpegDecoder.pixels(file);
            case LIBWEBP -> {
                WebpDecoder.pixels(file);
                WebpDecoder.frames(file);
            }
            case IMAGEIO -> ImageIoDecoder.pixels(file);
            case LIBHEIF -> {
                HeifDecoder.pixels(file);
                HeifDecoder.frames(file);
            }
            case LIBJXL -> {
                JxlDecoder.pixels(file);
                JxlDecoder.frames(file);
            }
            case JAVAFX -> {} // JavaFX's own, in its own thread: the window's tests open those
        }
    }

    @Test
    void everyDecoderRefusesAFileCutShortWithAReason() throws Exception {
        List<String> escaped = new ArrayList<>();
        int refused = 0;
        for (Path source : matrix()) {
            ImageFormats.Decoder decoder = ImageFormats.decoderFor(source);
            if (decoder == null || decoder == ImageFormats.Decoder.JAVAFX) continue;
            byte[] whole = Files.readAllBytes(source);
            for (int length : new int[] {0, 12, 40, whole.length / 3, whole.length * 2 / 3, whole.length - 1}) {
                Path cut = dir.resolve("cut-" + source.getFileName());
                Files.write(cut, Arrays.copyOf(whole, Math.max(0, length)));
                try {
                    decode(decoder, cut);
                } catch (IOException reason) {
                    refused++;
                } catch (RuntimeException | LinkageError e) {
                    escaped.add(source.getFileName() + " cut at " + length + ": " + e);
                }
            }
        }
        assertEquals(List.of(), escaped, "only IOException may say a file cannot be decoded");
        assertTrue(refused > 20, "most cuts are refused: " + refused);
    }

    @Test
    void aThumbnailOfADamagedFileIsRefusedNotMade() throws Exception {
        Thumbnails thumbnails = new Thumbnails();
        try {
            Path cut = dir.resolve("half.jpg");
            byte[] whole = Files.readAllBytes(
                    Path.of(Objects.requireNonNull(getClass().getResource("/com/mirada/matrix/jpeg-rgb.jpg"))
                            .toURI()));
            Files.write(cut, Arrays.copyOf(whole, 30));
            var result = thumbnails.get(cut);
            org.junit.jupiter.api.Assertions.assertThrows(
                    java.util.concurrent.ExecutionException.class,
                    () -> result.get(20, java.util.concurrent.TimeUnit.SECONDS),
                    "no thumbnail is made of a damaged file");
        } finally {
            thumbnails.close();
        }
    }

    @Test
    void theCamerasThumbnailIsLookedForSafelyInAJpegCutShortOrDamaged() throws Exception {
        List<String> escaped = new ArrayList<>();
        java.util.Random random = new java.util.Random(8);
        for (String name : List.of(
                "/com/mirada/matrix/exif-heavy.jpg", "/com/mirada/image/exif.jpg", "/com/mirada/matrix/orient-6.jpg")) {
            byte[] whole = Files.readAllBytes(
                    Path.of(Objects.requireNonNull(getClass().getResource(name)).toURI()));
            Path file = dir.resolve("thumb.jpg");
            for (int round = 0; round < 80; round++) {
                byte[] damaged = round < 40 ? Arrays.copyOf(whole, Math.min(whole.length, round * 7)) : whole.clone();
                if (round >= 40) damaged[random.nextInt(Math.min(64, damaged.length))] = (byte) random.nextInt(256);
                Files.write(file, damaged);
                try {
                    long at = Thumbnails.exifTiff(file);
                    assertTrue(at == -1 || at > 0);
                } catch (IOException reason) {
                    // said why
                } catch (RuntimeException e) {
                    escaped.add(name + " round " + round + ": " + e);
                }
            }
        }
        assertEquals(List.of(), escaped);
    }
}
