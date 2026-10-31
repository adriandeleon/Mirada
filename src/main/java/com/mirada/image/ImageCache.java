package com.mirada.image;

import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import javafx.scene.image.Image;

/**
 * The last few images, decoded, so stepping back and forth and stepping onto a prefetched
 * neighbour are instant.
 *
 * <p>Small on purpose: a decoded image is four bytes a pixel, so a 48-megapixel photo is about
 * 190 MB, and the cache holds full resolution because zooming to 100 % needs it. Three entries is
 * the current image and one either side.
 *
 * <p>FX thread only: {@code Image}'s progress is reported there, and that is where {@link
 * Entry#ready} is completed from.
 */
public final class ImageCache {

    /**
     * One image, loading in the background. {@code ready} completes, with the file's EXIF, once the
     * pixels are decoded (or failed — check {@code image.isError()}) and the metadata is read.
     */
    public record Entry(Path file, Image image, CompletableFuture<Exif> ready) {}

    private final int capacity;
    private final Executor metadataReader;
    private final Map<Path, Entry> entries = new LinkedHashMap<>(16, 0.75f, true);

    public ImageCache(int capacity) {
        this(capacity, Executors.newVirtualThreadPerTaskExecutor());
    }

    ImageCache(int capacity, Executor metadataReader) {
        this.capacity = capacity;
        this.metadataReader = metadataReader;
    }

    /** The entry for {@code file}, starting its load if it is not cached; marks it most recently used. */
    public Entry get(Path file) {
        Entry entry = entries.get(file);
        if (entry != null) return entry;
        entry = load(file);
        entries.put(file, entry);
        trim();
        return entry;
    }

    private Entry load(Path file) {
        Image image = new Image(file.toUri().toString(), true);
        CompletableFuture<Void> decoded = new CompletableFuture<>();
        if (image.getProgress() >= 1 || image.isError()) {
            decoded.complete(null);
        } else {
            image.progressProperty().addListener((obs, was, now) -> {
                if (now.doubleValue() >= 1) decoded.complete(null);
            });
            image.errorProperty().addListener((obs, was, now) -> {
                if (now) decoded.complete(null);
            });
        }
        CompletableFuture<Exif> exif = CompletableFuture.supplyAsync(() -> Exif.read(file), metadataReader);
        return new Entry(file, image, decoded.thenCombine(exif, (done, metadata) -> metadata));
    }

    /** Drops the least recently used beyond capacity, cancelling a load still in flight. */
    private void trim() {
        Iterator<Entry> oldestFirst = entries.values().iterator();
        while (entries.size() > capacity && oldestFirst.hasNext()) {
            Entry evicted = oldestFirst.next();
            oldestFirst.remove();
            if (evicted.image().getProgress() < 1) evicted.image().cancel();
        }
    }
}
