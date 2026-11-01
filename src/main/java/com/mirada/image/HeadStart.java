package com.mirada.image;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import com.mirada.folder.FolderImages;
import com.mirada.folder.ImageFormats;
import com.mirada.perf.Startup;
import org.jspecify.annotations.Nullable;

/**
 * Decodes the image Mirada will open first while the JavaFX toolkit is still starting.
 *
 * <p>Bringing the toolkit up (GTK on Linux) takes about 100 ms before the window can even be built,
 * and the first image used to wait for all of it: a 24-megapixel JPEG decodes in 200 ms, a HEIC
 * of that size in 800. Started from {@code main}, before {@code launch}, the decode runs alongside.
 * {@link ImageCache} claims the result when the window asks for that file.
 *
 * <p>Every decoder but JavaFX's own can do this (libjpeg-turbo, ImageIO, libheif and libjxl all
 * produce {@link ArgbPixels}); JavaFX's needs the toolkit's screens, so PNG, GIF and BMP wait for it
 * as before. A head start that fails is not an error: the cache loads the file the usual way, which
 * for a JPEG libjpeg-turbo declines (CMYK, 12-bit) means JavaFX.
 */
public final class HeadStart {

    /** Null until a head start is under way, then it; {@link #CLOSED} once the cache has asked. */
    private static final AtomicReference<@Nullable Object> STATE = new AtomicReference<>();
    /** The cache's first request has been made: a head start that arrives later is not wanted. */
    private static final Object CLOSED = new Object();

    final Path file;
    final ImageFormats.Decoder decoder;
    final CompletableFuture<ArgbPixels> pixels = new CompletableFuture<>();
    final CompletableFuture<Exif> exif = new CompletableFuture<>();

    private HeadStart(Path file, ImageFormats.Decoder decoder) {
        this.file = file;
        this.decoder = decoder;
    }

    /**
     * From {@code main}, before {@code launch}: starts on the image the arguments will open, the same
     * one {@link FolderImages#open} picks. Returns at once; the folder is listed on the new thread.
     */
    public static void begin(List<String> args) {
        if (args.isEmpty()) return;
        Thread.ofPlatform().daemon().name("head-start").start(() -> run(args.getFirst()));
    }

    private static void run(String argument) {
        Path file;
        try {
            file = FolderImages.open(Path.of(argument)).current();
        } catch (IOException | RuntimeException e) {
            return; // the window lists the folder again and reports what is wrong with it
        }
        if (file == null) return;
        ImageFormats.Decoder decoder = ImageFormats.decoderFor(file);
        if (decoder == null || !canStartEarly(decoder)) return;

        HeadStart started = new HeadStart(file, decoder);
        if (!STATE.compareAndSet(null, started)) return; // the window got there first
        Thread.ofVirtual().name("head-start-exif").start(() -> started.exif.complete(Exif.read(file)));
        try {
            started.pixels.complete(decode(decoder, file));
            Startup.mark("head-start-decoded");
        } catch (IOException e) {
            Startup.mark("head-start-gave-up");
            started.pixels.completeExceptionally(new UncheckedIOException(e));
        } catch (RuntimeException | LinkageError e) {
            Startup.mark("head-start-gave-up");
            started.pixels.completeExceptionally(e);
        }
    }

    /** Whether a decoder runs without the toolkit: every one but JavaFX's own. */
    static boolean canStartEarly(ImageFormats.Decoder decoder) {
        return decoder != ImageFormats.Decoder.JAVAFX;
    }

    private static ArgbPixels decode(ImageFormats.Decoder decoder, Path file) throws IOException {
        return switch (decoder) {
            case TURBOJPEG -> TurboJpegDecoder.pixels(file);
            case LIBWEBP -> WebpDecoder.pixels(file);
            case IMAGEIO -> ImageIoDecoder.pixels(file);
            case LIBHEIF -> HeifDecoder.pixels(file);
            case LIBJXL -> JxlDecoder.pixels(file);
            case JAVAFX -> throw new IllegalArgumentException("JavaFX's decoder needs the toolkit");
        };
    }

    /**
     * The head start for {@code file}, handed over once; null when there is none for it. The first
     * call closes the door either way, so a head start still listing the folder gives up rather than
     * holding a decoded image nobody will ask for.
     */
    static @Nullable HeadStart claim(Path file) {
        return STATE.getAndSet(CLOSED) instanceof HeadStart started && started.file.equals(file) ? started : null;
    }
}
