package com.mirada.image;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.lang.GeoLocation;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.Tag;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;
import org.jspecify.annotations.Nullable;

/**
 * Everything the info sidebar shows from a file's metadata: the camera card, a handful of rows in
 * the words a photographer uses, and every tag the file carries, by directory, for "All Metadata".
 *
 * <p>Read on demand when the sidebar is open, off the FX thread: unlike {@link Exif}, which every
 * image needs for its orientation, this walks every directory of the file.
 */
public record ExifDetails(List<Row> camera, List<Section> all) {

    public static final ExifDetails NONE = new ExifDetails(List.of(), List.of());

    /** One label-over-value row. */
    public record Row(String label, String value) {}

    /** One metadata directory (Exif IFD0, Exif SubIFD, GPS, XMP, ...) and its tags. */
    public record Section(String title, List<Row> rows) {}

    /** Never fails: a file without metadata, or one metadata-extractor cannot parse, has none. */
    public static ExifDetails read(Path file) {
        Metadata metadata;
        try {
            metadata = ImageMetadataReader.readMetadata(file.toFile());
        } catch (ImageProcessingException | IOException | RuntimeException e) {
            return NONE;
        }
        return from(metadata);
    }

    static ExifDetails from(Metadata metadata) {
        ExifIFD0Directory ifd0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
        ExifSubIFDDirectory sub = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
        GpsDirectory gps = metadata.getFirstDirectoryOfType(GpsDirectory.class);

        List<Row> camera = new ArrayList<>();
        String model = Exif.summary(
                describe(ifd0, ExifIFD0Directory.TAG_MAKE),
                describe(ifd0, ExifIFD0Directory.TAG_MODEL),
                null,
                null,
                null,
                null);
        add(camera, "Camera", model);
        add(camera, "Lens", describe(sub, ExifSubIFDDirectory.TAG_LENS_MODEL));
        add(camera, "Taken", taken(describe(sub, ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL)));
        add(camera, "Exposure", seconds(describe(sub, ExifSubIFDDirectory.TAG_EXPOSURE_TIME)));
        add(camera, "Aperture", describe(sub, ExifSubIFDDirectory.TAG_FNUMBER));
        String iso = describe(sub, ExifSubIFDDirectory.TAG_ISO_EQUIVALENT);
        add(camera, "ISO", iso);
        add(camera, "Focal Length", describe(sub, ExifSubIFDDirectory.TAG_FOCAL_LENGTH));
        add(camera, "Flash", describe(sub, ExifSubIFDDirectory.TAG_FLASH));
        GeoLocation location = gps == null ? null : gps.getGeoLocation();
        if (location != null && !location.isZero()) add(camera, "Location", coordinates(location));

        List<Section> all = new ArrayList<>();
        for (Directory directory : metadata.getDirectories()) {
            List<Row> rows = new ArrayList<>();
            for (Tag tag : directory.getTags()) {
                String value = tag.getDescription();
                if (value != null && !value.isBlank() && value.length() < 400)
                    rows.add(new Row(tag.getTagName(), value.strip()));
            }
            if (!rows.isEmpty()) all.add(new Section(directory.getName(), List.copyOf(rows)));
        }
        return new ExifDetails(List.copyOf(camera), List.copyOf(all));
    }

    private static @Nullable String describe(@Nullable Directory directory, int tag) {
        return directory == null ? null : directory.getDescription(tag);
    }

    private static void add(List<Row> rows, String label, @Nullable String value) {
        if (value != null && !value.isBlank()) rows.add(new Row(label, value.strip()));
    }

    /** EXIF writes dates as {@code 2026:05:17 18:42:10}; shown with dashes, as people write them. */
    static @Nullable String taken(@Nullable String value) {
        if (value == null || value.length() < 10) return value;
        return value.substring(0, 10).replace(':', '-') + value.substring(10);
    }

    static @Nullable String seconds(@Nullable String value) {
        return value != null && value.endsWith(" sec") ? value.replace(" sec", " s") : value;
    }

    /** Decimal degrees to five places (about a metre), signed as maps take them: {@code 19.43250, -99.13306}. */
    static String coordinates(GeoLocation location) {
        return String.format(Locale.ROOT, "%.5f, %.5f", location.getLatitude(), location.getLongitude());
    }
}
