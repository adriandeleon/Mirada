package com.mirada.folder;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

/**
 * Which files Mirada opens, by extension. Only what JavaFX's {@code Image} decodes itself: BMP,
 * GIF, JPEG and PNG. WebP, TIFF, HEIC and AVIF need ImageIO plugins or a native decoder and are not
 * wired in yet; a file of those kinds opened by name gets a message rather than a broken image.
 */
public final class ImageFormats {

    /** Lower case, without the dot. */
    public static final Set<String> EXTENSIONS = Set.of("jpg", "jpeg", "jpe", "jfif", "png", "gif", "bmp");

    private ImageFormats() {}

    public static boolean isImage(Path file) {
        return EXTENSIONS.contains(extension(file));
    }

    /** The text after the last dot of the file name, lower case; empty when there is none. */
    public static String extension(Path file) {
        String name = FolderImages.name(file);
        int dot = name.lastIndexOf('.');
        return dot <= 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
