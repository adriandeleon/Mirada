package com.mirada.image;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.ByteOrder;
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
 * WebP, still and animated, through libwebp's animation decoder ({@code libwebpdemux}), called with
 * the foreign function API. Several times faster than the pure-Java reader it replaces, which stays
 * as the fallback when libwebp is missing.
 *
 * <p>The animation decoder composes each frame onto the canvas (offsets, blending, disposal) and
 * hands back the whole picture, premultiplied BGRA ({@code MODE_bgrA}): read as little-endian ints,
 * premultiplied ARGB, one copy into the Java array. A still is an animation of one frame to it.
 *
 * <p>The {@code Init} and {@code New} functions are inline wrappers in {@code demux.h} around
 * {@code ...Internal} ones taking the ABI version, which is what is called here: {@value
 * #DEMUX_ABI_VERSION}, libwebp 1.x's.
 */
final class WebpDecoder {

    static final int DEMUX_ABI_VERSION = 0x0107;
    private static final int MODE_BGRA_PREMULTIPLIED = 8;

    /** {@code WebPData}. */
    private static final StructLayout DATA =
            MemoryLayout.structLayout(ADDRESS.withName("bytes"), JAVA_LONG.withName("size"));

    /** {@code WebPAnimDecoderOptions}: colour mode, threads, seven padding words. */
    private static final long OPTIONS_SIZE = 4 + 4 + 7 * 4;

    /** {@code WebPAnimInfo}: canvas width and height, loop count, background, frame count, padding. */
    private static final long INFO_SIZE = 9 * 4;

    private static final ValueLayout.OfInt LITTLE_ENDIAN_INT =
            ValueLayout.JAVA_INT_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN);

    private WebpDecoder() {}

    static boolean available() {
        return Library.INSTANCE.isPresent();
    }

    static WritableImage decode(Path file) throws IOException {
        return pixels(file).toImage();
    }

    /** The image, or an animation's first frame, decoded without the JavaFX toolkit. */
    static ArgbPixels pixels(Path file) throws IOException {
        Animation.Frames[] none = new Animation.Frames[1];
        return decode(file, false, none);
    }

    /** Every frame of an animated WebP, or null for a still or one too large to hold. */
    static Animation.@Nullable Frames frames(Path file) throws IOException {
        Animation.Frames[] out = new Animation.Frames[1];
        decode(file, true, out);
        return out[0];
    }

    /** The first frame; with {@code all}, the rest into {@code frames[0]} as well. */
    private static ArgbPixels decode(Path file, boolean all, Animation.@Nullable Frames[] frames) throws IOException {
        Library webp = Library.INSTANCE.orElseThrow(() -> new IOException("libwebp was not found"));
        try (Arena arena = Arena.ofConfined();
                FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            MemorySegment bytes = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size(), arena);
            MemorySegment data = arena.allocate(DATA);
            data.set(ADDRESS, 0, bytes);
            data.set(JAVA_LONG, 8, bytes.byteSize());
            MemorySegment options = arena.allocate(OPTIONS_SIZE, 8);
            if ((int) webp.optionsInit.invoke(options, DEMUX_ABI_VERSION) == 0) {
                throw new IOException("libwebp's version does not match");
            }
            options.set(JAVA_INT, 0, MODE_BGRA_PREMULTIPLIED);
            options.set(JAVA_INT, 4, 1); // threads
            MemorySegment decoder = (MemorySegment) webp.decoderNew.invoke(data, options, DEMUX_ABI_VERSION);
            if (decoder.equals(MemorySegment.NULL)) throw new IOException("not a valid WebP file");
            try {
                MemorySegment info = arena.allocate(INFO_SIZE, 4);
                if ((int) webp.getInfo.invoke(decoder, info) == 0) throw new IOException("not a valid WebP file");
                int width = info.get(JAVA_INT, 0);
                int height = info.get(JAVA_INT, 4);
                int count = info.get(JAVA_INT, 16);
                if (width <= 0 || height <= 0) throw new IOException("the file holds no image");
                MemorySegment buffer = arena.allocate(ADDRESS);
                MemorySegment timestamp = arena.allocate(JAVA_INT);
                if ((int) webp.getNext.invoke(decoder, buffer, timestamp) == 0) {
                    throw new IOException("the image could not be decoded");
                }
                ArgbPixels first = copy(buffer, width, height);
                if (all && count > 1) {
                    Animation.Collector collector = new Animation.Collector();
                    int previous = timestamp.get(JAVA_INT, 0);
                    boolean room = collector.add(first, previous);
                    while (room && (int) webp.hasMore.invoke(decoder) != 0) {
                        if ((int) webp.getNext.invoke(decoder, buffer, timestamp) == 0) break;
                        int end = timestamp.get(JAVA_INT, 0);
                        room = collector.add(copy(buffer, width, height), end - previous);
                        previous = end;
                    }
                    if (room) frames[0] = collector.frames();
                }
                return first;
            } finally {
                webp.delete.invoke(decoder);
            }
        } catch (IOException | RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new IOException("libwebp call failed", e);
        }
    }

    /** The decoder's canvas, valid until its next frame, into a Java array. */
    private static ArgbPixels copy(MemorySegment bufferPointer, int width, int height) {
        int[] argb = new int[Math.multiplyExact(width, height)];
        MemorySegment canvas = bufferPointer.get(ADDRESS, 0).reinterpret((long) argb.length * 4);
        MemorySegment.copy(canvas, LITTLE_ENDIAN_INT, 0, argb, 0, argb.length);
        return new ArgbPixels(width, height, argb);
    }

    /** The bound functions, or empty when libwebp is not installed. Looked up once, on first use. */
    private record Library(
            MethodHandle optionsInit,
            MethodHandle decoderNew,
            MethodHandle getInfo,
            MethodHandle getNext,
            MethodHandle hasMore,
            MethodHandle delete) {

        static final Optional<Library> INSTANCE = NativeLibraries.load(
                        NativeLibraries.fileNames("webpdemux", List.of("2")))
                .flatMap(Library::bind);

        private static Optional<Library> bind(SymbolLookup lookup) {
            Linker linker = Linker.nativeLinker();
            try {
                return Optional.of(new Library(
                        handle(
                                linker,
                                lookup,
                                "WebPAnimDecoderOptionsInitInternal",
                                FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT)),
                        handle(
                                linker,
                                lookup,
                                "WebPAnimDecoderNewInternal",
                                FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, JAVA_INT)),
                        handle(
                                linker,
                                lookup,
                                "WebPAnimDecoderGetInfo",
                                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS)),
                        handle(
                                linker,
                                lookup,
                                "WebPAnimDecoderGetNext",
                                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS)),
                        handle(
                                linker,
                                lookup,
                                "WebPAnimDecoderHasMoreFrames",
                                FunctionDescriptor.of(JAVA_INT, ADDRESS)),
                        handle(linker, lookup, "WebPAnimDecoderDelete", FunctionDescriptor.ofVoid(ADDRESS))));
            } catch (RuntimeException e) {
                return Optional.empty();
            }
        }

        private static MethodHandle handle(Linker linker, SymbolLookup lookup, String name, FunctionDescriptor type) {
            return linker.downcallHandle(
                    lookup.find(name).orElseThrow(() -> new IllegalArgumentException("no " + name)), type);
        }
    }
}
