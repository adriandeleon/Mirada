package com.mirada.image;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import org.jspecify.annotations.Nullable;

/**
 * What Mirada reads from a file's metadata: how to turn it upright, and one line about the shot.
 *
 * <p>Reading never fails: a file with no EXIF, or one metadata-extractor cannot parse, is upright
 * with nothing to say. Reads the file's headers, not its pixels, so it is milliseconds — but it is
 * still I/O, and callers run it off the FX thread.
 */
public record Exif(Orientation orientation, String summary) {

    public static final Exif NONE = new Exif(Orientation.NORMAL, "");

    public static Exif read(Path file) {
        Metadata metadata;
        try {
            metadata = ImageMetadataReader.readMetadata(file.toFile());
        } catch (ImageProcessingException | IOException | RuntimeException e) {
            return NONE;
        }
        ExifIFD0Directory camera = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
        ExifSubIFDDirectory shot = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
        Orientation orientation = Orientation.NORMAL;
        if (camera != null && camera.containsTag(ExifIFD0Directory.TAG_ORIENTATION)) {
            Integer value = camera.getInteger(ExifIFD0Directory.TAG_ORIENTATION);
            if (value != null) orientation = Orientation.fromExif(value);
        }
        String summary = summary(
                describe(camera, ExifIFD0Directory.TAG_MAKE),
                describe(camera, ExifIFD0Directory.TAG_MODEL),
                describe(shot, ExifSubIFDDirectory.TAG_EXPOSURE_TIME),
                describe(shot, ExifSubIFDDirectory.TAG_FNUMBER),
                describe(shot, ExifSubIFDDirectory.TAG_ISO_EQUIVALENT),
                describe(shot, ExifSubIFDDirectory.TAG_FOCAL_LENGTH));
        return new Exif(orientation, summary);
    }

    private static @Nullable String describe(@Nullable Directory directory, int tag) {
        return directory == null ? null : directory.getDescription(tag);
    }

    /**
     * {@code Apple iPhone 15 · 1/120 s · f/1.8 · ISO 80 · 6.9 mm}. The make is dropped when the
     * model already starts with it ("Canon" + "Canon EOS R6"), which most cameras do. Missing parts
     * are left out, not shown as blanks.
     */
    static String summary(
            @Nullable String make,
            @Nullable String model,
            @Nullable String exposure,
            @Nullable String fNumber,
            @Nullable String iso,
            @Nullable String focalLength) {
        List<String> parts = new ArrayList<>();
        String cleanMake = clean(make);
        String cleanModel = clean(model);
        if (cleanModel.isEmpty()) {
            add(parts, cleanMake);
        } else if (cleanMake.isEmpty()
                || cleanModel.toLowerCase(Locale.ROOT).startsWith(cleanMake.toLowerCase(Locale.ROOT))) {
            add(parts, cleanModel);
        } else {
            add(parts, cleanMake + " " + cleanModel);
        }
        String cleanExposure = clean(exposure);
        add(parts, cleanExposure.endsWith(" sec") ? cleanExposure.replace(" sec", " s") : cleanExposure);
        add(parts, clean(fNumber));
        String cleanIso = clean(iso);
        if (!cleanIso.isEmpty()) add(parts, "ISO " + cleanIso);
        add(parts, clean(focalLength));
        return String.join(" · ", parts);
    }

    private static String clean(@Nullable String value) {
        return value == null ? "" : value.strip();
    }

    private static void add(List<String> parts, String part) {
        if (!part.isEmpty()) parts.add(part);
    }
}
