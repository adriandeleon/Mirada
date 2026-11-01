package com.mirada.image;

import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;

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
        if (decoder == ImageFormats.Decoder.JAVAFX) {
            // Decoded at the small size by JavaFX itself, synchronously on this thread.
            image = new Image(file.toUri().toString(), SIZE, SIZE, true, true, false);
            if (image.isError()) throw new IllegalStateException("could not decode " + file);
        } else {
            image = shrink(ImageCache.decodeOffThread(decoder, file), SIZE);
        }
        return new Thumbnail(image, orientation);
    }

    /**
     * {@code source} scaled so its longer side is at most {@code size}, each output pixel the average
     * of the source pixels it covers (a box filter: no aliasing, and quick enough at thumbnail size).
     */
    static Image shrink(Image source, int size) {
        int width = (int) source.getWidth();
        int height = (int) source.getHeight();
        double scale = Math.min(1, (double) size / Math.max(width, height));
        if (scale >= 1) return source;
        int outWidth = Math.max(1, (int) Math.round(width * scale));
        int outHeight = Math.max(1, (int) Math.round(height * scale));
        PixelReader reader = source.getPixelReader();
        WritableImage out = new WritableImage(outWidth, outHeight);
        PixelWriter writer = out.getPixelWriter();
        for (int y = 0; y < outHeight; y++) {
            int y0 = y * height / outHeight;
            int y1 = Math.max(y0 + 1, (y + 1) * height / outHeight);
            for (int x = 0; x < outWidth; x++) {
                int x0 = x * width / outWidth;
                int x1 = Math.max(x0 + 1, (x + 1) * width / outWidth);
                long a = 0, r = 0, g = 0, b = 0;
                for (int sy = y0; sy < y1; sy++) {
                    for (int sx = x0; sx < x1; sx++) {
                        int argb = reader.getArgb(sx, sy);
                        a += argb >>> 24;
                        r += (argb >> 16) & 0xFF;
                        g += (argb >> 8) & 0xFF;
                        b += argb & 0xFF;
                    }
                }
                long n = (long) (x1 - x0) * (y1 - y0);
                writer.setArgb(x, y, (int) (a / n) << 24 | (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n));
            }
        }
        return out;
    }
}
