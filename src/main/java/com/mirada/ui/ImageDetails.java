package com.mirada.ui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;
import java.util.Map;

import com.mirada.folder.ImageFormats;
import com.mirada.image.ExifDetails;
import org.jspecify.annotations.Nullable;

import static com.mirada.i18n.Messages.tr;

/**
 * What the info sidebar shows about the image on screen, and how it words it: GNOME Image
 * Viewer's properties, plus the camera card and every metadata tag the file carries.
 */
record ImageDetails(
        Path file,
        int index,
        int count,
        long width,
        long height,
        String format,
        long bytes,
        @Nullable FileTime created,
        @Nullable FileTime modified,
        ExifDetails exif) {

    private static final Map<String, String> FORMAT_NAMES = Map.ofEntries(
            Map.entry("jpg", "JPEG"),
            Map.entry("jpeg", "JPEG"),
            Map.entry("jpe", "JPEG"),
            Map.entry("jfif", "JPEG"),
            Map.entry("tif", "TIFF"),
            Map.entry("tiff", "TIFF"),
            Map.entry("psd", "Photoshop"),
            Map.entry("heif", "HEIF"),
            Map.entry("hif", "HEIF"),
            Map.entry("jxl", "JPEG XL"),
            Map.entry("webp", "WebP"));

    /**
     * Reads the file's size and dates and its metadata: I/O, so off the FX thread. Width and height
     * are the image as it stands on screen, turned upright.
     */
    static ImageDetails read(Path file, int index, int count, double width, double height) {
        long bytes = -1;
        FileTime created = null;
        FileTime modified = null;
        try {
            BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
            bytes = attributes.size();
            created = attributes.creationTime();
            modified = attributes.lastModifiedTime();
        } catch (IOException | RuntimeException e) {
            // Shown as unknown: the image is on screen, so the sidebar should not fail over a stat.
        }
        return new ImageDetails(
                file,
                index,
                count,
                Math.round(width),
                Math.round(height),
                formatName(file),
                bytes,
                created,
                modified,
                ExifDetails.read(file));
    }

    /** {@code JPEG}, {@code JPEG XL}, {@code PNG}: the format's usual name, from the extension. */
    static String formatName(Path file) {
        String extension = ImageFormats.extension(file);
        return FORMAT_NAMES.getOrDefault(extension, extension.toUpperCase(Locale.ROOT));
    }

    /**
     * Decimal units, one decimal place, as GNOME and macOS show file sizes: {@code 80.7 kB},
     * {@code 4.2 MB}. Bytes under 1000 are whole.
     */
    static String fileSize(long bytes) {
        if (bytes < 0) return tr("details.unknown");
        if (bytes < 1000) return tr("details.bytes", bytes);
        String[] units = {"kB", "MB", "GB", "TB"};
        double value = bytes;
        int unit = -1;
        while (value >= 1000 && unit < units.length - 1) {
            value /= 1000;
            unit++;
        }
        return String.format(Locale.ROOT, "%.1f %s", value, units[unit]);
    }

    /** In the user's locale and time zone, at the length GNOME uses. */
    static String date(@Nullable FileTime time, Locale locale, ZoneId zone) {
        if (time == null || time.toMillis() <= 0) return tr("details.unknown");
        return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
                .withLocale(locale)
                .withZone(zone)
                .format(time.toInstant());
    }

    String size() {
        return tr("details.size", width, height);
    }

    String folderName() {
        Path parent = file.getParent();
        Path name = parent == null ? null : parent.getFileName();
        return name == null ? String.valueOf(parent) : name.toString();
    }

    /** {@code 3 of 41}: where the image sits in its folder. */
    String position() {
        return tr("details.position", index + 1, count);
    }
}
