package com.mirada.image;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import javafx.scene.image.Image;

import com.mirada.folder.ImageFormats;

/**
 * Small versions of a folder's images, for the thumbnail strip.
 *
 * <p>Made on one low-priority thread of their own, so a strip full of thumbnails never competes with
 * the decode of the image being looked at ({@link ImageCache}'s two threads), and only when asked for:
 * the strip's list view asks only for the cells on screen. JavaFX's own formats decode straight to
 * the small size; the others decode whole and are box-filtered down, then the large image is dropped.
 *
 * <p>FX thread only, as ImageCache.
 */
public final class Thumbnails {

    /** A thumbnail, and the turn that puts it upright (the full image's EXIF rule). */
    public record Thumbnail(Image image, Orientation orientation) {}

    /** Longest side, in pixels: twice the strip's cell, so it stays sharp on a 2x display. */
    static final int SIZE = 160;

    /** About 100 KB each: a few hundred cost tens of megabytes at most. */
    private static final int CAPACITY = 400;

    private final Executor worker = Executors.newSingleThreadExecutor(Thread.ofPlatform()
            .daemon()
            .priority(Thread.MIN_PRIORITY)
            .name("thumbnails")
            .factory());
    private final Map<Path, CompletableFuture<Thumbnail>> cache = new LinkedHashMap<>(64, 0.75f, true);

    public CompletableFuture<Thumbnail> get(Path file) {
        CompletableFuture<Thumbnail> cached = cache.get(file);
        if (cached != null) return cached;
        CompletableFuture<Thumbnail> made = CompletableFuture.supplyAsync(() -> make(file), worker);
        cache.put(file, made);
        Iterator<CompletableFuture<Thumbnail>> oldest = cache.values().iterator();
        while (cache.size() > CAPACITY && oldest.hasNext()) {
            oldest.next();
            oldest.remove();
        }
        return made;
    }

    private static Thumbnail make(Path file) {
        ImageFormats.Decoder decoder = ImageFormats.decoderFor(file);
        if (decoder == null) throw new IllegalArgumentException("not an image: " + file);
        Orientation orientation =
                decoder.turnsUpright() ? Orientation.NORMAL : Exif.read(file).orientation();
        Image image;
        if (decoder == ImageFormats.Decoder.TURBOJPEG) {
            image = turboOrJavaFx(file);
        } else if (decoder == ImageFormats.Decoder.JAVAFX) {
            image = javaFx(file);
        } else {
            image = shrink(ImageCache.decodeOffThread(decoder, file), SIZE);
        }
        return new Thumbnail(image, orientation);
    }

    /** At an eighth, a quarter or half size straight out of libjpeg-turbo, then shrunk the rest of the way. */
    private static Image turboOrJavaFx(Path file) {
        try {
            return shrink(TurboJpegDecoder.pixels(file, SIZE).toImage(), SIZE);
        } catch (IOException declined) {
            return javaFx(file);
        }
    }

    /** Decoded at the small size by JavaFX itself, synchronously on this thread. */
    private static Image javaFx(Path file) {
        Image image = new Image(file.toUri().toString(), SIZE, SIZE, true, true, false);
        if (image.isError()) throw new IllegalStateException("could not decode " + file);
        return image;
    }

    /** {@code source} scaled so its longer side is at most {@code size} (a box filter, {@link Shrink}). */
    static Image shrink(Image source, int size) {
        return Shrink.toFit(source, size);
    }
}
