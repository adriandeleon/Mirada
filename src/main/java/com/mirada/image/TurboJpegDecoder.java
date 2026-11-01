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

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

/**
 * JPEG through libjpeg-turbo's TurboJPEG API, called with the foreign function API.
 *
 * <p>Two to three times faster than JavaFX's own decoder, which is the classic libjpeg without SIMD,
 * and it needs no JavaFX toolkit, so {@link HeadStart} can decode the first JPEG while the toolkit is
 * still starting. Like JavaFX it ignores an embedded ICC profile, so a photo looks the same whichever
 * of the two opened it.
 *
 * <p>It decodes straight to BGRA with the alpha filled in, which read as little-endian ints is
 * opaque ARGB: no conversion, one copy into the Java array. What it will not decode as 8-bit RGB
 * (CMYK and YCCK files, 12-bit precision) is an {@link IOException}, and {@link ImageCache} hands
 * the file to JavaFX instead.
 *
 * <p>Enum values are TurboJPEG 3's ({@code turbojpeg.h}, libjpeg-turbo 3.0 and later).
 */
final class TurboJpegDecoder {

    private static final int TJINIT_DECOMPRESS = 1;
    private static final int TJPARAM_JPEGWIDTH = 5;
    private static final int TJPARAM_JPEGHEIGHT = 6;
    private static final int TJPARAM_PRECISION = 7;
    private static final int TJPARAM_COLORSPACE = 8;
    private static final int TJCS_RGB = 0;
    private static final int TJCS_YCBCR = 1;
    private static final int TJCS_GRAY = 2;
    private static final int TJPF_BGRA = 8;

    /** {@code tjscalingfactor}: passed by value. */
    private static final StructLayout SCALING_FACTOR =
            MemoryLayout.structLayout(JAVA_INT.withName("num"), JAVA_INT.withName("denom"));

    private static final ValueLayout.OfInt LITTLE_ENDIAN_INT =
            ValueLayout.JAVA_INT_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN);

    private TurboJpegDecoder() {}

    static boolean available() {
        return Library.INSTANCE.isPresent();
    }

    static WritableImage decode(Path file) throws IOException {
        return pixels(file).toImage();
    }

    /** The image at full size, decoded without the JavaFX toolkit. */
    static ArgbPixels pixels(Path file) throws IOException {
        return pixels(file, 0);
    }

    /**
     * The image at the smallest of 1/8, 1/4, 1/2 or full size whose longer side is still at least
     * {@code atLeast} pixels: libjpeg-turbo scales in the DCT, far faster than decoding in full and
     * shrinking. 0 for full size.
     */
    static ArgbPixels pixels(Path file, int atLeast) throws IOException {
        Library tj = Library.INSTANCE.orElseThrow(() -> new IOException("libjpeg-turbo was not found"));
        try (Arena arena = Arena.ofConfined();
                FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            MemorySegment data = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size(), arena);
            MemorySegment handle = (MemorySegment) tj.init.invoke(TJINIT_DECOMPRESS);
            if (handle.equals(MemorySegment.NULL)) throw new IOException("libjpeg-turbo could not start");
            try {
                check(tj, handle, (int) tj.decompressHeader.invoke(handle, data, data.byteSize()));
                int colorspace = (int) tj.get.invoke(handle, TJPARAM_COLORSPACE);
                int precision = (int) tj.get.invoke(handle, TJPARAM_PRECISION);
                if (colorspace != TJCS_YCBCR && colorspace != TJCS_GRAY && colorspace != TJCS_RGB) {
                    throw new IOException("colour space " + colorspace + " is left to JavaFX");
                }
                if (precision != 8) throw new IOException(precision + "-bit JPEG is left to JavaFX");
                int width = (int) tj.get.invoke(handle, TJPARAM_JPEGWIDTH);
                int height = (int) tj.get.invoke(handle, TJPARAM_JPEGHEIGHT);
                int denominator = denominator(Math.max(width, height), atLeast);
                if (denominator > 1) {
                    MemorySegment factor = arena.allocate(SCALING_FACTOR);
                    factor.set(JAVA_INT, 0, 1);
                    factor.set(JAVA_INT, 4, denominator);
                    check(tj, handle, (int) tj.setScalingFactor.invoke(handle, factor));
                    // TJSCALED: rounded up.
                    width = (width + denominator - 1) / denominator;
                    height = (height + denominator - 1) / denominator;
                }
                if (width <= 0 || height <= 0) throw new IOException("the file holds no image");
                int pitch = Math.multiplyExact(width, 4);
                MemorySegment bgra = arena.allocate((long) pitch * height);
                check(tj, handle, (int) tj.decompress8.invoke(handle, data, data.byteSize(), bgra, pitch, TJPF_BGRA));
                int[] argb = new int[Math.multiplyExact(width, height)];
                MemorySegment.copy(bgra, LITTLE_ENDIAN_INT, 0, argb, 0, argb.length);
                return new ArgbPixels(width, height, argb);
            } finally {
                tj.destroy.invoke(handle);
            }
        } catch (IOException | RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new IOException("libjpeg-turbo call failed", e);
        }
    }

    /** The largest of 8, 4 and 2 that keeps {@code longSide} at least {@code atLeast}; else 1. */
    static int denominator(int longSide, int atLeast) {
        if (atLeast <= 0) return 1;
        for (int denominator = 8; denominator > 1; denominator /= 2) {
            if ((longSide + denominator - 1) / denominator >= atLeast) return denominator;
        }
        return 1;
    }

    private static void check(Library tj, MemorySegment handle, int status) throws Throwable {
        if (status == 0) return;
        MemorySegment message = (MemorySegment) tj.errorString.invoke(handle);
        throw new IOException(
                message.equals(MemorySegment.NULL)
                        ? "libjpeg-turbo error " + status
                        : message.reinterpret(1024).getString(0));
    }

    /** The bound functions, or empty when libjpeg-turbo is not installed. Looked up once, on first use. */
    private record Library(
            MethodHandle init,
            MethodHandle destroy,
            MethodHandle errorString,
            MethodHandle get,
            MethodHandle decompressHeader,
            MethodHandle setScalingFactor,
            MethodHandle decompress8) {

        static final Optional<Library> INSTANCE = NativeLibraries.load(
                        NativeLibraries.fileNames("turbojpeg", List.of("0")))
                .flatMap(Library::bind);

        private static Optional<Library> bind(SymbolLookup lookup) {
            Linker linker = Linker.nativeLinker();
            try {
                return Optional.of(new Library(
                        handle(linker, lookup, "tj3Init", FunctionDescriptor.of(ADDRESS, JAVA_INT)),
                        handle(linker, lookup, "tj3Destroy", FunctionDescriptor.ofVoid(ADDRESS)),
                        handle(linker, lookup, "tj3GetErrorStr", FunctionDescriptor.of(ADDRESS, ADDRESS)),
                        handle(linker, lookup, "tj3Get", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT)),
                        handle(
                                linker,
                                lookup,
                                "tj3DecompressHeader",
                                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_LONG)),
                        handle(
                                linker,
                                lookup,
                                "tj3SetScalingFactor",
                                FunctionDescriptor.of(JAVA_INT, ADDRESS, SCALING_FACTOR)),
                        handle(
                                linker,
                                lookup,
                                "tj3Decompress8",
                                FunctionDescriptor.of(
                                        JAVA_INT, ADDRESS, ADDRESS, JAVA_LONG, ADDRESS, JAVA_INT, JAVA_INT))));
            } catch (RuntimeException e) {
                // A libjpeg-turbo older than 3.0 has no tj3 functions; JavaFX decodes instead.
                return Optional.empty();
            }
        }

        private static MethodHandle handle(Linker linker, SymbolLookup lookup, String name, FunctionDescriptor type) {
            return linker.downcallHandle(
                    lookup.find(name).orElseThrow(() -> new IllegalArgumentException("no " + name)), type);
        }
    }
}
