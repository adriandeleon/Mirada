package com.mirada.image;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javafx.scene.image.Image;

import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifThumbnailDirectory;
import com.drew.metadata.jpeg.JpegDirectory;
import com.mirada.folder.ImageFormats;
import org.jspecify.annotations.Nullable;

/**
 * Small versions of a folder's images, for the thumbnail strip.
 *
 * <p>Made on low-priority threads of their own, a quarter of the cores ({@link #threads}), and only
 * when asked for: the strip's list view asks only for the cells on screen. The newest request is
 * made first, as the cells on screen now asked last, and one a cell no longer wants is dropped before
 * it starts ({@link #release}), so scrolling through thousands never leaves the view waiting behind
 * the ones it passed.
 *
 * <p>A JPEG from a camera or a phone usually carries a small JPEG of itself in its EXIF, often
 * 512 pixels across: that one is taken when it is large enough and the photo's shape ({@link #fits}),
 * a millisecond against the 20 to 130 of decoding the photo even at an eighth. Other JPEGs decode at
 * an eighth, a quarter or half size in libjpeg-turbo, JavaFX's own formats straight to the small
 * size; the rest decode whole and are box-filtered down, then the large image is dropped.
 *
 * <p>FX thread only, as ImageCache.
 */
public final class Thumbnails {

    /** A thumbnail, and the turn that puts it upright (the full image's EXIF rule). */
    public record Thumbnail(Image image, Orientation orientation) {}

    /** Longest side, in pixels: twice the strip's cell, so it stays sharp on a 2x display. */
    static final int SIZE = 160;

    /** About 100 KB each: a few hundred cost tens of megabytes at most. */
    static final int CAPACITY = 400;

    /** How far an embedded thumbnail's shape may stray from the photo's: rounding, not a crop. */
    private static final double SHAPE_TOLERANCE = 0.02;

    private record Entry(CompletableFuture<Thumbnail> result, Runnable job) {}

    private final ThreadPoolExecutor workers;
    private final Map<Path, Entry> cache = new LinkedHashMap<>(64, 0.75f, true);

    public Thumbnails() {
        int threads = threads(Runtime.getRuntime().availableProcessors());
        workers = new ThreadPoolExecutor(
                threads,
                threads,
                0,
                TimeUnit.SECONDS,
                new NewestFirst(),
                Thread.ofPlatform()
                        .daemon()
                        .priority(Thread.MIN_PRIORITY)
                        .name("thumbnails-", 1)
                        .factory());
    }

    /**
     * A quarter of the cores, one to four: the image being looked at decodes on two threads of its
     * own (ImageCache), and those come first.
     */
    static int threads(int cores) {
        return Math.clamp(cores / 4, 1, 4);
    }

    public CompletableFuture<Thumbnail> get(Path file) {
        Entry cached = cache.get(file);
        if (cached != null) return cached.result();
        CompletableFuture<Thumbnail> result = new CompletableFuture<>();
        Runnable job = () -> {
            try {
                result.complete(make(file));
            } catch (Throwable e) {
                result.completeExceptionally(e);
            }
        };
        cache.put(file, new Entry(result, job));
        Iterator<Entry> oldest = cache.values().iterator();
        while (cache.size() > CAPACITY && oldest.hasNext()) {
            workers.remove(oldest.next().job());
            oldest.remove();
        }
        workers.execute(job);
        return result;
    }

    /** Stops the workers, for a strip whose window has closed: nothing queued is made. */
    public void close() {
        workers.shutdownNow();
        cache.clear();
    }

    /**
     * No cell shows {@code file} any more: a thumbnail not yet started is not made (asked for again,
     * it is queued again); one being made or made already is kept.
     */
    public void release(Path file) {
        Entry entry = cache.get(file);
        if (entry != null && workers.remove(entry.job())) cache.remove(file);
    }

    /**
     * Whether a {@code width} × {@code height} thumbnail embedded in a photo of {@code photoWidth} ×
     * {@code photoHeight} may stand for it: at least {@link #SIZE} across, and the photo's shape. A
     * camera that letterboxes a 16:9 photo's thumbnail into 4:3, or an editor that cropped the photo
     * and left the thumbnail, gives one of another shape, and the photo is decoded instead.
     */
    static boolean fits(int width, int height, int photoWidth, int photoHeight) {
        if (Math.max(width, height) < SIZE || width <= 0 || height <= 0) return false;
        if (photoWidth <= 0 || photoHeight <= 0) return false;
        double shape = ((double) width * photoHeight) / ((double) height * photoWidth);
        return Math.abs(shape - 1) <= SHAPE_TOLERANCE;
    }

    private static Thumbnail make(Path file) {
        ImageFormats.Decoder decoder = ImageFormats.decoderFor(file);
        if (decoder == null) throw new IllegalArgumentException("not an image: " + file);
        if (decoder == ImageFormats.Decoder.TURBOJPEG) return jpeg(file);
        Orientation orientation =
                decoder.turnsUpright() ? Orientation.NORMAL : Exif.read(file).orientation();
        Image image =
                decoder == ImageFormats.Decoder.JAVAFX ? javaFx(file) : shrink(decodeOrFallBack(decoder, file), SIZE);
        return new Thumbnail(image, orientation);
    }

    /** The camera's own thumbnail when it {@link #fits}, else the photo at a fraction of its size. */
    private static Thumbnail jpeg(Path file) {
        Metadata metadata = Exif.metadata(file);
        if (metadata == null) return new Thumbnail(turboOrJavaFx(file), Orientation.NORMAL);
        ArgbPixels embedded = embedded(file, metadata);
        Image image = embedded == null ? turboOrJavaFx(file) : shrink(embedded.toImage(), SIZE);
        return new Thumbnail(image, Exif.of(metadata).orientation());
    }

    /** The thumbnail in the EXIF (IFD1), turned the same way as the photo, or null when it will not do. */
    static @Nullable ArgbPixels embedded(Path file, Metadata metadata) {
        ExifThumbnailDirectory exif = metadata.getFirstDirectoryOfType(ExifThumbnailDirectory.class);
        JpegDirectory jpeg = metadata.getFirstDirectoryOfType(JpegDirectory.class);
        if (exif == null || jpeg == null) return null;
        // From the TIFF block's start; metadata-extractor's "adjusted" offset is from its segment's.
        Integer offset = exif.getInteger(ExifThumbnailDirectory.TAG_THUMBNAIL_OFFSET);
        Integer length = exif.getInteger(ExifThumbnailDirectory.TAG_THUMBNAIL_LENGTH);
        Integer width = jpeg.getInteger(JpegDirectory.TAG_IMAGE_WIDTH);
        Integer height = jpeg.getInteger(JpegDirectory.TAG_IMAGE_HEIGHT);
        if (offset == null || length == null || width == null || height == null) return null;
        long tiff;
        try {
            tiff = exifTiff(file);
        } catch (IOException e) {
            return null;
        }
        if (tiff < 0) return null;
        int photoWidth = width;
        int photoHeight = height;
        return TurboJpegDecoder.embedded(
                file, tiff + offset, length, SIZE, (w, h) -> fits(w, h, photoWidth, photoHeight));
    }

    /**
     * Where the TIFF block of a JPEG's EXIF starts in the file, or -1 without one: the segments are
     * walked from the start, four bytes read of each, to the APP1 that begins {@code Exif\0\0}.
     * The image data (SOS) ends the search, as nothing after it is metadata.
     */
    static long exifTiff(Path file) throws IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            ByteBuffer header = ByteBuffer.allocate(10);
            if (channel.read(header, 0) < 2 || (header.getShort(0) & 0xFFFF) != 0xFFD8) return -1;
            long at = 2;
            while (true) {
                header.clear();
                int read = channel.read(header, at);
                if (read < 4 || (header.get(0) & 0xFF) != 0xFF) return -1;
                int marker = header.get(1) & 0xFF;
                if (marker == 0xFF) {
                    at++; // fill byte
                    continue;
                }
                if (marker == 0xDA || marker == 0xD9) return -1;
                if (marker == 0xE1 && read >= 10 && isExif(header)) return at + 10;
                at += 2 + (header.getShort(2) & 0xFFFF);
            }
        }
    }

    private static boolean isExif(ByteBuffer header) {
        return header.get(4) == 'E'
                && header.get(5) == 'x'
                && header.get(6) == 'i'
                && header.get(7) == 'f'
                && header.get(8) == 0
                && header.get(9) == 0;
    }

    /** Hands out the newest job first: the cells on screen now are the ones that asked last. */
    private static final class NewestFirst extends LinkedBlockingDeque<Runnable> {
        @Override
        public boolean offer(Runnable job) {
            return offerFirst(job);
        }
    }

    /** At an eighth, a quarter or half size straight out of libjpeg-turbo, then shrunk the rest of the way. */
    private static Image turboOrJavaFx(Path file) {
        try {
            return shrink(TurboJpegDecoder.pixels(file, SIZE).toImage(), SIZE);
        } catch (IOException declined) {
            return javaFx(file);
        }
    }

    /** libwebp, and ImageIO for what it leaves; the rest, as they are. */
    private static Image decodeOrFallBack(ImageFormats.Decoder decoder, Path file) {
        ImageFormats.Decoder fallback = decoder.fallback();
        try {
            return ImageCache.decodeOffThread(decoder, file);
        } catch (RuntimeException declined) {
            if (fallback == null) throw declined;
            return ImageCache.decodeOffThread(fallback, file);
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
