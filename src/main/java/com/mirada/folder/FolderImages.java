package com.mirada.folder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;

/**
 * The "document" of an image viewer: the images in one folder, in the chosen order ({@link
 * Sorting}, natural name order by default), and which one is showing. Immutable; stepping returns a
 * new value.
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

    /** A file opens with its folder around it; a folder opens at its first image. In name order. */
    public static FolderImages open(Path path) throws IOException {
        return open(path, Sorting.DEFAULT);
    }

    /** A file opens with its folder around it; a folder opens at its first image. */
    public static FolderImages open(Path path, Sorting sorting) throws IOException {
        Path target = path.toAbsolutePath().normalize();
        if (Files.isDirectory(target)) return select(list(target), null, sorting);
        Path folder = target.getParent();
        if (folder == null) return new FolderImages(List.of(target), 0);
        return select(list(folder), target, sorting);
    }

    static FolderImages select(List<Path> candidates, @Nullable Path selected) {
        return select(candidates, selected, Sorting.DEFAULT);
    }

    /** Hidden files left out, the selected one put back in, sorted, and the selection found. */
    static FolderImages select(List<Path> candidates, @Nullable Path selected, Sorting sorting) {
        List<Path> images = new ArrayList<>();
        for (Path candidate : candidates) {
            if (!name(candidate).startsWith(".")) images.add(candidate);
        }
        if (selected != null && !images.contains(selected)) images.add(selected);
        List<Path> sorted = sorting.sort(images);
        int index = selected == null ? 0 : sorted.indexOf(selected);
        return new FolderImages(sorted, index);
    }

    /** The same images in another order, still on the one showing. */
    public FolderImages sorted(Sorting sorting) {
        Path showing = current();
        List<Path> sorted = sorting.sort(images);
        return new FolderImages(sorted, showing == null ? 0 : sorted.indexOf(showing));
    }

    /**
     * Without {@code gone} (trashed, or moved away), on the image that took its place: the next one,
     * or the previous when it was the last.
     */
    public FolderImages without(Path gone) {
        int at = images.indexOf(gone);
        if (at < 0) return this;
        List<Path> rest = new ArrayList<>(images);
        rest.remove(at);
        int next = at < index ? index - 1 : at == index ? Math.min(at, rest.size() - 1) : index;
        return new FolderImages(rest, Math.max(0, next));
    }

    /**
     * With {@code from} renamed to {@code to}, in its place, and on {@code to} if {@code from} was
     * showing. Not sorted again: that needs the files' dates or sizes, read off the FX thread, then
     * {@link #reordered}.
     */
    public FolderImages renamed(Path from, Path to) {
        int at = images.indexOf(from);
        if (at < 0) return this;
        List<Path> changed = new ArrayList<>(images);
        changed.set(at, to);
        return new FolderImages(changed, index);
    }

    /**
     * The same images in {@code order}'s order, still on the one showing: a sort made off the FX thread
     * from what the folder was then. An image {@code order} lacks (come since) goes last, in the order
     * it had; one only {@code order} has (gone since) is left out. Linear in the folder's size.
     */
    public FolderImages reordered(List<Path> order) {
        Set<Path> here = new HashSet<>(images);
        List<Path> out = new ArrayList<>(images.size());
        Set<Path> placed = new HashSet<>();
        for (Path image : order) {
            if (here.contains(image) && placed.add(image)) out.add(image);
        }
        for (Path image : images) {
            if (!placed.contains(image)) out.add(image);
        }
        Path showing = current();
        return new FolderImages(out, showing == null ? 0 : Math.max(0, out.indexOf(showing)));
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
