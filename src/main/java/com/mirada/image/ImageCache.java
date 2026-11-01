package com.mirada.image;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import javafx.scene.image.Image;

import com.mirada.folder.ImageFormats;
import org.jspecify.annotations.Nullable;

/**
 * The last few images, decoded, so stepping back and forth and stepping onto a prefetched
 * neighbour are instant.
 *
 * <p>Small on purpose: a decoded image is four bytes a pixel, so a 48-megapixel photo is about
 * 190 MB, and the cache holds full resolution because zooming to 100 % needs it. Three entries is
 * the current image and one either side.
 *
 * <p>Four decoders feed it (see {@link ImageFormats.Decoder}). JavaFX's loads in its own background
 * thread and reports progress on the FX thread; ImageIO, libheif and libjxl run on two decoder
 * threads here — two, because each decode briefly holds a full image twice over.
 *
 * <p>FX thread only.
 */
public final class ImageCache {

    /**
     * One image on its way. {@code ready} completes, never exceptionally, once the pixels are
     * decoded and the metadata read, or once either has failed.
     */
    public record Entry(Path file, CompletableFuture<Decoded> ready, Runnable cancel) {}

    /** The outcome of a load: an image and its EXIF, or the reason there is none. */
    public record Decoded(@Nullable Image image, Exif exif, String error) {

        static Decoded of(Image image, Exif exif) {
            return new Decoded(image, exif, "");
        }

        static Decoded failed(String error) {
            return new Decoded(null, Exif.NONE, error);
        }
    }

    private final int capacity;
    private final Executor metadataReader;
    private final Executor imageDecoder;
    private final Map<Path, Entry> entries = new LinkedHashMap<>(16, 0.75f, true);

    public ImageCache(int capacity) {
        this(
                capacity,
                Executors.newVirtualThreadPerTaskExecutor(),
                Executors.newFixedThreadPool(
                        2, Thread.ofPlatform().daemon().name("decoder-", 0).factory()));
    }

    ImageCache(int capacity, Executor metadataReader, Executor imageDecoder) {
        this.capacity = capacity;
        this.metadataReader = metadataReader;
        this.imageDecoder = imageDecoder;
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
        CompletableFuture<Exif> exif = CompletableFuture.supplyAsync(() -> Exif.read(file), metadataReader);
        CompletableFuture<Image> pixels;
        Runnable cancel;
        ImageFormats.Decoder decoder = ImageFormats.decoderFor(file);
        if (decoder == null || decoder == ImageFormats.Decoder.JAVAFX) {
            Image image = new Image(file.toUri().toString(), true);
            // JavaFX's decoders miss variants ImageIO's readers handle (a 32-bit BMP with alpha
            // throws inside JavaFX): when JavaFX gives up, ImageIO gets a turn before it is an error.
            pixels = whenLoaded(image)
                    .exceptionallyCompose(javafxFailed -> CompletableFuture.supplyAsync(
                            () -> decodeOffThread(ImageFormats.Decoder.IMAGEIO, file), imageDecoder));
            cancel = () -> {
                if (image.getProgress() < 1) image.cancel();
            };
        } else {
            pixels = CompletableFuture.supplyAsync(() -> decodeOffThread(decoder, file), imageDecoder);
            CompletableFuture<Image> started = pixels;
            cancel = () -> started.cancel(false);
        }
        // A decoder that turns the image upright itself must not have the EXIF turn applied again.
        CompletableFuture<Exif> orientation =
                decoder != null && decoder.turnsUpright() ? exif.thenApply(Exif::upright) : exif;
        CompletableFuture<Decoded> ready = pixels.thenCombine(orientation, Decoded::of)
                .exceptionally(error -> {
                    Throwable cause = innermost(error);
                    // A broken file is an IOException and says so in the window. Anything else is a
                    // bug, or a native image missing metadata, and its trace is what finds it.
                    if (!(cause instanceof IOException)) cause.printStackTrace();
                    return Decoded.failed(message(error));
                });
        return new Entry(file, ready, cancel);
    }

    static Image decodeOffThread(ImageFormats.Decoder decoder, Path file) {
        try {
            return switch (decoder) {
                case IMAGEIO -> ImageIoDecoder.decode(file);
                case LIBHEIF -> HeifDecoder.decode(file);
                case LIBJXL -> JxlDecoder.decode(file);
                case JAVAFX -> throw new IllegalArgumentException("JavaFX decodes in its own thread");
            };
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Completes when JavaFX's background load of {@code image} has finished or failed. */
    private static CompletableFuture<Image> whenLoaded(Image image) {
        CompletableFuture<Image> done = new CompletableFuture<>();
        Runnable settle = () -> {
            if (image.isError()) {
                Exception cause = image.getException();
                done.completeExceptionally(cause != null ? cause : new IOException("could not be decoded"));
            } else if (image.getProgress() >= 1) {
                done.complete(image);
            }
        };
        settle.run();
        if (!done.isDone()) {
            image.progressProperty().addListener((obs, was, now) -> settle.run());
            image.errorProperty().addListener((obs, was, now) -> settle.run());
        }
        return done;
    }

    /** The innermost cause's message, which is the one that says what was wrong with the file. */
    static String message(Throwable error) {
        Throwable cause = innermost(error);
        String text = cause.getMessage();
        return text == null || text.isBlank() ? cause.getClass().getSimpleName() : text;
    }

    /** Unwraps the wrappers futures and initializers add, down to the error that says what happened. */
    static Throwable innermost(Throwable error) {
        Throwable cause = error;
        while ((cause instanceof CompletionException
                        || cause instanceof UncheckedIOException
                        || cause instanceof ExceptionInInitializerError)
                && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    /** Drops the least recently used beyond capacity, cancelling a load still in flight. */
    private void trim() {
        Iterator<Entry> oldestFirst = entries.values().iterator();
        while (entries.size() > capacity && oldestFirst.hasNext()) {
            Entry evicted = oldestFirst.next();
            oldestFirst.remove();
            evicted.cancel().run();
        }
    }
}
