package com.mirada.folder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;

/**
 * The "document" of an image viewer: the images in one folder, in name order, and which one is
 * showing. Immutable; stepping returns a new value.
 *
 * <p>A file opened by name is always in the list, even when it is hidden or its extension is not
 * one Mirada decodes, so the window can say what is wrong with it instead of opening a different
 * image.
 */
public record FolderImages(List<Path> images, int index) {

    public static final FolderImages EMPTY = new FolderImages(List.of(), 0);

    static final Comparator<Path> BY_NAME = Comparator.comparing(FolderImages::name, NaturalOrder.INSTANCE);

    public FolderImages {
        images = List.copyOf(images);
        index = images.isEmpty() ? 0 : Math.clamp(index, 0, images.size() - 1);
    }

    /** A file opens with its folder around it; a folder opens at its first image. */
    public static FolderImages open(Path path) throws IOException {
        Path target = path.toAbsolutePath().normalize();
        if (Files.isDirectory(target)) return select(list(target), null);
        Path folder = target.getParent();
        if (folder == null) return new FolderImages(List.of(target), 0);
        return select(list(folder), target);
    }

    /** Hidden files left out, the selected one put back in, sorted, and the selection found. */
    static FolderImages select(List<Path> candidates, @Nullable Path selected) {
        List<Path> images = new ArrayList<>();
        for (Path candidate : candidates) {
            if (!name(candidate).startsWith(".")) images.add(candidate);
        }
        if (selected != null && !images.contains(selected)) images.add(selected);
        images.sort(BY_NAME);
        int index = selected == null ? 0 : images.indexOf(selected);
        return new FolderImages(images, index);
    }

    private static List<Path> list(Path folder) throws IOException {
        try (Stream<Path> entries = Files.list(folder)) {
            return entries.filter(ImageFormats::isImage)
                    .filter(Files::isRegularFile)
                    .toList();
        }
    }

    static String name(Path path) {
        Path name = path.getFileName();
        return name == null ? "" : name.toString();
    }

    public boolean isEmpty() {
        return images.isEmpty();
    }

    public int size() {
        return images.size();
    }

    public @Nullable Path current() {
        return images.isEmpty() ? null : images.get(index);
    }

    /** Moves by {@code delta}, stopping at either end rather than wrapping. */
    public FolderImages step(int delta) {
        return new FolderImages(images, index + delta);
    }

    /** The image at {@code index}, clamped to the folder. */
    public FolderImages jump(int index) {
        return new FolderImages(images, index);
    }

    public FolderImages first() {
        return new FolderImages(images, 0);
    }

    public FolderImages last() {
        return new FolderImages(images, images.size() - 1);
    }

    /** The images one step either way, the next first: what is worth loading before it is asked for. */
    public List<Path> neighbours() {
        List<Path> out = new ArrayList<>(2);
        if (index + 1 < images.size()) out.add(images.get(index + 1));
        if (index > 0) out.add(images.get(index - 1));
        return out;
    }
}
