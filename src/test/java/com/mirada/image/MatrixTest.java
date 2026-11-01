package com.mirada.image;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import javafx.scene.image.Image;

import com.mirada.folder.ImageFormats;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every variant in the format matrix (scripts/make-format-matrix.sh) decodes, through the decoder
 * Mirada would use for it: bit depths, grey, CMYK, alpha, palettes, compressions, animation. CI's
 * native job opens the same files in the native image, where a gap in the metadata shows only when
 * such a file is opened.
 */
class MatrixTest {

    @BeforeAll
    static void toolkit() throws InterruptedException {
        FxToolkit.start();
    }

    static Path matrix() throws URISyntaxException {
        return Path.of(Objects.requireNonNull(MatrixTest.class.getResource("/com/mirada/matrix"), "matrix")
                .toURI());
    }

    @Test
    void everyVariantDecodes() throws Exception {
        List<String> failures = new ArrayList<>();
        int checked = 0;
        try (Stream<Path> files = Files.list(matrix()).sorted()) {
            for (Path file : files.toList()) {
                ImageFormats.Decoder decoder = ImageFormats.decoderFor(file);
                if (decoder == null) continue;
                if (decoder != ImageFormats.Decoder.JAVAFX && decoder != ImageFormats.Decoder.IMAGEIO) {
                    // libheif and libjxl: checked where installed, as NativeDecodersTest does.
                    if (decoder == ImageFormats.Decoder.LIBHEIF && !HeifDecoder.available()) continue;
                    if (decoder == ImageFormats.Decoder.LIBJXL && !JxlDecoder.available()) continue;
                }
                checked++;
                String problem = decode(decoder, file);
                if (problem != null) failures.add(file.getFileName() + ": " + problem);
            }
        }
        assertTrue(failures.isEmpty(), String.join("\n", failures));
        assertTrue(checked >= 30, "only " + checked + " variants checked");
    }

    /** JavaFX's decoder, and ImageIO when it gives up, as ImageCache does. */
    private static Image javaFx(Path file) {
        Image image = new Image(file.toUri().toString(), false);
        return image.isError() ? ImageCache.decodeOffThread(ImageFormats.Decoder.IMAGEIO, file) : image;
    }

    /** Null when the file decoded to an image of the matrix's size; else what went wrong. */
    private static String decode(ImageFormats.Decoder decoder, Path file) {
        try {
            Image image;
            if (decoder == ImageFormats.Decoder.TURBOJPEG) {
                // As ImageCache does: JavaFX for what libjpeg-turbo declines (CMYK, or no library).
                try {
                    image = ImageCache.decodeOffThread(decoder, file);
                } catch (RuntimeException declined) {
                    image = javaFx(file);
                }
            } else if (decoder == ImageFormats.Decoder.JAVAFX) {
                image = javaFx(file);
            } else {
                image = ImageCache.decodeOffThread(decoder, file);
            }
            if (image.isError()) return String.valueOf(image.getException());
            if (image.getWidth() <= 0 || image.getHeight() <= 0) return "empty image";
            String name = file.getFileName().toString();
            if (name.startsWith("orient-") || name.startsWith("exif-")) return null; // other sizes
            assertEquals(48, (int) image.getWidth(), file.toString());
            return null;
        } catch (RuntimeException | AssertionError e) {
            Throwable cause = ImageCache.innermost(e);
            return cause.getClass().getSimpleName() + ": " + cause.getMessage();
        }
    }

    @Test
    void theMatrixCoversEveryFormat() throws IOException, URISyntaxException {
        List<String> names;
        try (Stream<Path> files = Files.list(matrix())) {
            names = files.map(f -> ImageFormats.extension(f)).distinct().toList();
        }
        for (String extension : List.of("jpg", "png", "gif", "bmp", "webp", "tif", "psd", "heic", "avif", "jxl")) {
            assertTrue(names.contains(extension), "no ." + extension + " in the matrix");
        }
    }
}
