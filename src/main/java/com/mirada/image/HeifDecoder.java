package com.mirada.image;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Optional;

import javafx.scene.image.WritableImage;

import org.jspecify.annotations.Nullable;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

/**
 * HEIC and AVIF through libheif, called with the foreign function API: no JNI, no glue library.
 *
 * <p>libheif applies the container's own rotation and mirroring ({@code irot}, {@code imir}) while
 * decoding, as the HEIF spec requires, so the pixels come out upright and the file's EXIF
 * orientation must not be applied on top (iPhones write both). See {@link
 * com.mirada.folder.ImageFormats.Decoder#turnsUpright}.
 *
 * <p>The file is memory-mapped and handed over whole, rather than passed by name, so a path that is
 * not ASCII opens on Windows too. Which decoders libheif has (libde265 for HEIC, dav1d for AVIF) is
 * up to its build; a missing one surfaces as libheif's own error message.
 */
final class HeifDecoder {

    private static final int COLORSPACE_RGB = 1;
    private static final int CHROMA_INTERLEAVED_RGB = 10;
    private static final int CHROMA_INTERLEAVED_RGBA = 11;
    private static final int CHANNEL_INTERLEAVED = 10;

    /** {@code struct heif_error}: returned by value from most calls. */
    private static final StructLayout ERROR = MemoryLayout.structLayout(
            JAVA_INT.withName("code"), JAVA_INT.withName("subcode"), ADDRESS.withName("message"));

    private HeifDecoder() {}

    static boolean available() {
        return Library.INSTANCE.isPresent();
    }

    static WritableImage decode(Path file) throws IOException {
        return pixels(file).toImage();
    }

    /** The image, decoded without the JavaFX toolkit. */
    static ArgbPixels pixels(Path file) throws IOException {
        Library heif =
                Library.INSTANCE.orElseThrow(() -> new IOException("HEIC and AVIF need libheif, which was not found"));
        try (Arena arena = Arena.ofConfined();
                FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            MemorySegment data = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size(), arena);
            MemorySegment context = (MemorySegment) heif.contextAlloc.invoke();
            if (context.equals(MemorySegment.NULL)) throw new IOException("libheif could not allocate a context");
            // Phones store a HEIC as a grid of tiles, decoded in parallel; libheif's default is four.
            heif.setMaxDecodingThreads.invoke(context, Runtime.getRuntime().availableProcessors());
            MemorySegment handle = MemorySegment.NULL;
            MemorySegment image = MemorySegment.NULL;
            try {
                check((MemorySegment)
                        heif.readFromMemory.invoke(arena, context, data, data.byteSize(), MemorySegment.NULL));
                MemorySegment handleOut = arena.allocate(ADDRESS);
                check((MemorySegment) heif.primaryImageHandle.invoke(arena, context, handleOut));
                handle = handleOut.get(ADDRESS, 0);

                boolean alpha = (int) heif.hasAlphaChannel.invoke(handle) != 0;
                MemorySegment imageOut = arena.allocate(ADDRESS);
                check((MemorySegment) heif.decodeImage.invoke(
                        arena,
                        handle,
                        imageOut,
                        COLORSPACE_RGB,
                        alpha ? CHROMA_INTERLEAVED_RGBA : CHROMA_INTERLEAVED_RGB,
                        MemorySegment.NULL));
                image = imageOut.get(ADDRESS, 0);

                int width = (int) heif.imageWidth.invoke(image, CHANNEL_INTERLEAVED);
                int height = (int) heif.imageHeight.invoke(image, CHANNEL_INTERLEAVED);
                MemorySegment strideOut = arena.allocate(JAVA_INT);
                MemorySegment plane = (MemorySegment) heif.planeReadonly.invoke(image, CHANNEL_INTERLEAVED, strideOut);
                if (plane.equals(MemorySegment.NULL) || width <= 0 || height <= 0) {
                    throw new IOException("libheif returned no pixels");
                }
                int stride = strideOut.get(JAVA_INT, 0);
                return Pixels.fromInterleaved(
                        plane.reinterpret((long) stride * height), width, height, stride, alpha ? 4 : 3);
            } finally {
                if (!image.equals(MemorySegment.NULL)) heif.imageRelease.invoke(image);
                if (!handle.equals(MemorySegment.NULL)) heif.handleRelease.invoke(handle);
                heif.contextFree.invoke(context);
            }
        } catch (IOException | RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new IOException("libheif call failed", e);
        }
    }

    private static void check(MemorySegment error) throws IOException {
        int code = error.get(JAVA_INT, ERROR.byteOffset(MemoryLayout.PathElement.groupElement("code")));
        if (code == 0) return;
        MemorySegment message = error.get(ADDRESS, ERROR.byteOffset(MemoryLayout.PathElement.groupElement("message")));
        String text = message.equals(MemorySegment.NULL)
                ? "libheif error " + code
                : message.reinterpret(4096).getString(0);
        throw new IOException(text);
    }

    /** The bound functions, or empty when libheif is not installed. Looked up once, on first use. */
    private record Library(
            MethodHandle contextAlloc,
            MethodHandle contextFree,
            MethodHandle setMaxDecodingThreads,
            MethodHandle readFromMemory,
            MethodHandle primaryImageHandle,
            MethodHandle handleRelease,
            MethodHandle hasAlphaChannel,
            MethodHandle decodeImage,
            MethodHandle imageWidth,
            MethodHandle imageHeight,
            MethodHandle planeReadonly,
            MethodHandle imageRelease,
            @Nullable Sequences sequences) {

        static final Optional<Library> INSTANCE = NativeLibraries.load(NativeLibraries.fileNames("heif", List.of("1")))
                .flatMap(Library::bind);

        private static Optional<Library> bind(SymbolLookup lookup) {
            Linker linker = Linker.nativeLinker();
            try {
                MethodHandle init = handle(linker, lookup, "heif_init", FunctionDescriptor.of(ERROR, ADDRESS));
                // Loads the decoder plugins (libde265, dav1d) of a plugin build; harmless otherwise.
                try (Arena arena = Arena.ofConfined()) {
                    check((MemorySegment) init.invoke(arena, MemorySegment.NULL));
                }
                return Optional.of(new Library(
                        handle(linker, lookup, "heif_context_alloc", FunctionDescriptor.of(ADDRESS)),
                        handle(linker, lookup, "heif_context_free", FunctionDescriptor.ofVoid(ADDRESS)),
                        handle(
                                linker,
                                lookup,
                                "heif_context_set_max_decoding_threads",
                                FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT)),
                        handle(
                                linker,
                                lookup,
                                "heif_context_read_from_memory_without_copy",
                                FunctionDescriptor.of(ERROR, ADDRESS, ADDRESS, JAVA_LONG, ADDRESS)),
                        handle(
                                linker,
                                lookup,
                                "heif_context_get_primary_image_handle",
                                FunctionDescriptor.of(ERROR, ADDRESS, ADDRESS)),
                        handle(linker, lookup, "heif_image_handle_release", FunctionDescriptor.ofVoid(ADDRESS)),
                        handle(
                                linker,
                                lookup,
                                "heif_image_handle_has_alpha_channel",
                                FunctionDescriptor.of(JAVA_INT, ADDRESS)),
                        handle(
                                linker,
                                lookup,
                                "heif_decode_image",
                                FunctionDescriptor.of(ERROR, ADDRESS, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS)),
                        handle(
                                linker,
                                lookup,
                                "heif_image_get_width",
                                FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT)),
                        handle(
                                linker,
                                lookup,
                                "heif_image_get_height",
                                FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT)),
                        handle(
                                linker,
                                lookup,
                                "heif_image_get_plane_readonly",
                                FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT, ADDRESS)),
                        handle(linker, lookup, "heif_image_release", FunctionDescriptor.ofVoid(ADDRESS)),
                        Sequences.bind(linker, lookup)));
            } catch (Throwable unusable) {
                // A library too old for one of these functions, or one whose init fails: as if absent.
                return Optional.empty();
            }
        }
    }

    /** libheif 1.20's image sequences (an animated AVIF or HEIC): optional, older copies lack them. */
    private record Sequences(
            MethodHandle hasSequence,
            MethodHandle getTrack,
            MethodHandle timescale,
            MethodHandle decodeNext,
            MethodHandle duration,
            MethodHandle trackRelease) {

        static @Nullable Sequences bind(Linker linker, SymbolLookup lookup) {
            try {
                return new Sequences(
                        handle(linker, lookup, "heif_context_has_sequence", FunctionDescriptor.of(JAVA_INT, ADDRESS)),
                        handle(
                                linker,
                                lookup,
                                "heif_context_get_track",
                                FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT)),
                        handle(linker, lookup, "heif_track_get_timescale", FunctionDescriptor.of(JAVA_INT, ADDRESS)),
                        handle(
                                linker,
                                lookup,
                                "heif_track_decode_next_image",
                                FunctionDescriptor.of(ERROR, ADDRESS, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS)),
                        handle(linker, lookup, "heif_image_get_duration", FunctionDescriptor.of(JAVA_INT, ADDRESS)),
                        handle(linker, lookup, "heif_track_release", FunctionDescriptor.ofVoid(ADDRESS)));
            } catch (RuntimeException olderThan120) {
                return null;
            }
        }
    }

    private static final int END_OF_SEQUENCE = 13;

    /**
     * Every frame of an animated HEIF (an AVIF or HEIC image sequence), or null for a still, for a
     * libheif without sequences, or for one too large to hold.
     */
    static Animation.@Nullable Frames frames(Path file) throws IOException {
        Library heif = Library.INSTANCE.orElseThrow(() -> new IOException("libheif was not found"));
        Sequences sequences = heif.sequences;
        if (sequences == null) return null;
        try (Arena arena = Arena.ofConfined();
                FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            MemorySegment data = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size(), arena);
            MemorySegment context = (MemorySegment) heif.contextAlloc.invoke();
            if (context.equals(MemorySegment.NULL)) throw new IOException("libheif could not allocate a context");
            MemorySegment track = MemorySegment.NULL;
            try {
                heif.setMaxDecodingThreads.invoke(context, Runtime.getRuntime().availableProcessors());
                check((MemorySegment)
                        heif.readFromMemory.invoke(arena, context, data, data.byteSize(), MemorySegment.NULL));
                if ((int) sequences.hasSequence.invoke(context) == 0) return null;
                track = (MemorySegment) sequences.getTrack.invoke(context, 0); // the first visual track
                if (track.equals(MemorySegment.NULL)) return null;
                long timescale = Integer.toUnsignedLong((int) sequences.timescale.invoke(track));
                Animation.Collector collector = new Animation.Collector();
                MemorySegment imageOut = arena.allocate(ADDRESS);
                MemorySegment strideOut = arena.allocate(JAVA_INT);
                while (true) {
                    MemorySegment error = (MemorySegment) sequences.decodeNext.invoke(
                            arena, track, imageOut, COLORSPACE_RGB, CHROMA_INTERLEAVED_RGBA, MemorySegment.NULL);
                    if (error.get(JAVA_INT, 0) == END_OF_SEQUENCE) break;
                    check(error);
                    MemorySegment image = imageOut.get(ADDRESS, 0);
                    try {
                        int width = (int) heif.imageWidth.invoke(image, CHANNEL_INTERLEAVED);
                        int height = (int) heif.imageHeight.invoke(image, CHANNEL_INTERLEAVED);
                        MemorySegment plane =
                                (MemorySegment) heif.planeReadonly.invoke(image, CHANNEL_INTERLEAVED, strideOut);
                        if (plane.equals(MemorySegment.NULL) || width <= 0 || height <= 0) break;
                        int stride = strideOut.get(JAVA_INT, 0);
                        ArgbPixels frame = Pixels.fromInterleaved(
                                plane.reinterpret((long) stride * height), width, height, stride, 4);
                        long ticks = Integer.toUnsignedLong((int) sequences.duration.invoke(image));
                        long millis = timescale > 0 ? ticks * 1000 / timescale : 0;
                        if (!collector.add(frame, millis)) return null; // too large to hold
                    } finally {
                        heif.imageRelease.invoke(image);
                    }
                }
                return collector.frames();
            } finally {
                if (!track.equals(MemorySegment.NULL)) sequences.trackRelease.invoke(track);
                heif.contextFree.invoke(context);
            }
        } catch (IOException | RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new IOException("libheif call failed", e);
        }
    }

    static MethodHandle handle(Linker linker, SymbolLookup lookup, String name, FunctionDescriptor descriptor) {
        MemorySegment symbol = lookup.find(name).orElseThrow(() -> new IllegalStateException("no symbol " + name));
        return linker.downcallHandle(symbol, descriptor);
    }
}
