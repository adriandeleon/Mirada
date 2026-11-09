package com.mirada.folder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.jspecify.annotations.Nullable;

/**
 * How a folder's images are ordered: by name (natural order, so {@code IMG_9} before {@code
 * IMG_10}), by date modified or by size, and which way round. Ties fall back to the name, so the
 * order is the same every time.
 */
public record Sorting(By by, boolean reversed) {

    public enum By {
        NAME,
        DATE,
        SIZE
    }

    public static final Sorting DEFAULT = new Sorting(By.NAME, false);

    /** {@code name}, {@code date} or {@code size}, any case; the name for anything else. */
    public static By byOf(@Nullable String value) {
        if (value == null) return By.NAME;
        try {
            return By.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return By.NAME;
        }
    }

    public Sorting by(By newBy) {
        return new Sorting(newBy, reversed);
    }

    public Sorting reverse() {
        return new Sorting(by, !reversed);
    }

    /**
     * {@code images} sorted. Dates and sizes are read once each, before sorting, not once per
     * comparison; a file that cannot be read sorts as the oldest and smallest. By date or size that is
     * a stat for every image: never on the FX thread.
     */
    public List<Path> sort(List<Path> images) {
        Comparator<Path> names = FolderImages.BY_NAME;
        Comparator<Path> order =
                switch (by) {
                    case NAME -> names;
                    case DATE -> {
                        Map<Path, Long> modified =
                                attributes(images, a -> a.lastModifiedTime().toMillis());
                        yield Comparator.<Path>comparingLong(p -> modified.getOrDefault(p, 0L))
                                .thenComparing(names);
                    }
                    case SIZE -> {
                        Map<Path, Long> sizes = attributes(images, BasicFileAttributes::size);
                        yield Comparator.<Path>comparingLong(p -> sizes.getOrDefault(p, 0L))
                                .thenComparing(names);
                    }
                };
        return images.stream().sorted(reversed ? order.reversed() : order).toList();
    }

    private interface Attribute {
        long of(BasicFileAttributes attributes);
    }

    private static Map<Path, Long> attributes(List<Path> images, Attribute attribute) {
        Map<Path, Long> out = new HashMap<>(images.size() * 2);
        for (Path image : images) {
            try {
                out.put(image, attribute.of(Files.readAttributes(image, BasicFileAttributes.class)));
            } catch (IOException e) {
                // gone or unreadable: sorts first
            }
        }
        return out;
    }
}
