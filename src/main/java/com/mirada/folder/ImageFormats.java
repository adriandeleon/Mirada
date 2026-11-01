package com.mirada.folder;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

/**
 * Which files Mirada opens, by extension, and what decodes each.
 *
 * <p>JavaFX's own {@code Image} decodes BMP, GIF, JPEG and PNG, in the background and with GIF
 * animation, so those stay with it. WebP, TIFF and PSD go through ImageIO and the TwelveMonkeys
 * plugins. HEIC and AVIF go to libheif and JPEG XL to libjxl, native libraries the installers
 * bundle; where one is missing, its files still list and open with a message saying so.
 */
public final class ImageFormats {

    /** What turns a file's bytes into pixels. */
    public enum Decoder {
        JAVAFX(false),
        /** libjpeg-turbo; JavaFX takes over a JPEG it will not decode (CMYK, 12-bit). */
        TURBOJPEG(false),
        IMAGEIO(false),
        /** libwebp; ImageIO's pure-Java reader takes over when libwebp is missing. */
        LIBWEBP(false),
        LIBHEIF(true),
        LIBJXL(true);

        private final boolean turnsUpright;

        Decoder(boolean turnsUpright) {
            this.turnsUpright = turnsUpright;
        }

        /**
         * True when the decoder applies the image's orientation itself, so the EXIF orientation must
         * not be applied again. HEIF requires readers to honour its {@code irot}/{@code imir} boxes,
         * and iPhones write an EXIF orientation as well; applying both turns the photo twice.
         */
        public boolean turnsUpright() {
            return turnsUpright;
        }

        /** The decoder that takes a file this one declines or cannot load for, or null for none. */
        public @Nullable Decoder fallback() {
            return switch (this) {
                case TURBOJPEG -> JAVAFX;
                case LIBWEBP -> IMAGEIO;
                default -> null;
            };
        }

        /** Whether it can decode every frame of an animation (JavaFX animates GIFs itself). */
        public boolean animates() {
            return this == LIBWEBP || this == LIBHEIF || this == LIBJXL;
        }
    }

    /** Lower case, without the dot, to its decoder. */
    private static final Map<String, Decoder> BY_EXTENSION = Map.ofEntries(
            Map.entry("jpg", Decoder.TURBOJPEG),
            Map.entry("jpeg", Decoder.TURBOJPEG),
            Map.entry("jpe", Decoder.TURBOJPEG),
            Map.entry("jfif", Decoder.TURBOJPEG),
            Map.entry("png", Decoder.JAVAFX),
            Map.entry("gif", Decoder.JAVAFX),
            Map.entry("bmp", Decoder.JAVAFX),
            Map.entry("webp", Decoder.LIBWEBP),
            Map.entry("tif", Decoder.IMAGEIO),
            Map.entry("tiff", Decoder.IMAGEIO),
            Map.entry("psd", Decoder.IMAGEIO),
            Map.entry("heic", Decoder.LIBHEIF),
            Map.entry("heif", Decoder.LIBHEIF),
            Map.entry("hif", Decoder.LIBHEIF),
            Map.entry("avif", Decoder.LIBHEIF),
            Map.entry("jxl", Decoder.LIBJXL));

    /** Every extension Mirada lists and opens. */
    public static final Set<String> EXTENSIONS = BY_EXTENSION.keySet();

    private ImageFormats() {}

    public static boolean isImage(Path file) {
        return decoderFor(file) != null;
    }

    public static @Nullable Decoder decoderFor(Path file) {
        return BY_EXTENSION.get(extension(file));
    }

    /** The extensions one decoder takes. */
    public static Set<String> extensionsFor(Decoder decoder) {
        return Set.copyOf(BY_EXTENSION.entrySet().stream()
                .filter(entry -> entry.getValue() == decoder)
                .map(Map.Entry::getKey)
                .toList());
    }

    /** The text after the last dot of the file name, lower case; empty when there is none. */
    public static String extension(Path file) {
        String name = FolderImages.name(file);
        int dot = name.lastIndexOf('.');
        return dot <= 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
