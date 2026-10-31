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
 * JPEG XL through libjxl, called with the foreign function API.
 *
 * <p>libjxl turns the image upright itself unless asked to keep its orientation, so, as with
 * libheif, the EXIF orientation is not applied again. Decoding is spread over every core with
 * libjxl's thread runner when {@code libjxl_threads} is found, and runs on one thread when it is
 * not. An animation decodes its first frame.
 *
 * <p>The struct offsets below are libjxl's ABI from 0.7 to 0.12: {@code JxlBasicInfo} starts with {@code
 * have_container, xsize, ysize} and has {@code alpha_bits} at byte 60.
 */
final class JxlDecoder {

    private static final int SUCCESS = 0;
    private static final int ERROR = 1;
    private static final int NEED_MORE_INPUT = 2;
    private static final int NEED_IMAGE_OUT_BUFFER = 5;
    private static final int BASIC_INFO = 0x40;
    private static final int FULL_IMAGE = 0x1000;

    private static final int TYPE_UINT8 = 2;
    private static final int NATIVE_ENDIAN = 0;

    private static final long INFO_XSIZE = 4;
    private static final long INFO_YSIZE = 8;
    private static final long INFO_ALPHA_BITS = 60;
    /** {@code sizeof(JxlBasicInfo)} is about 210 bytes; it ends in reserved padding, so room to spare. */
    private static final long INFO_SIZE = 512;

    /** {@code JxlPixelFormat}: three 32-bit fields, then a {@code size_t}. */
    private static final StructLayout PIXEL_FORMAT = MemoryLayout.structLayout(
            JAVA_INT.withName("num_channels"),
            JAVA_INT.withName("data_type"),
            JAVA_INT.withName("endianness"),
            MemoryLayout.paddingLayout(4),
            JAVA_LONG.withName("align"));

    /**
     * The ABI versions tried, newest first: Homebrew ships 0.12, Debian 13 0.11, Ubuntu 24.04 0.7.
     * Add a new one only after checking its headers against the offsets above.
     */
    static final List<String> VERSIONS = List.of("0.12", "0.11", "0.10", "0.9", "0.8", "0.7");

    private JxlDecoder() {}

    static boolean available() {
        return Library.INSTANCE.isPresent();
    }

    static WritableImage decode(Path file) throws IOException {
        Library jxl = Library.INSTANCE.orElseThrow(() -> new IOException("JPEG XL needs libjxl, which was not found"));
        try (Arena arena = Arena.ofConfined();
                FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            MemorySegment data = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size(), arena);
            MemorySegment decoder = (MemorySegment) jxl.create.invoke(MemorySegment.NULL);
            if (decoder.equals(MemorySegment.NULL)) throw new IOException("libjxl could not create a decoder");
            MemorySegment runner = MemorySegment.NULL;
            try {
                if (jxl.threads != null) {
                    long workers = (long) jxl.threads.defaultWorkers.invoke();
                    runner = (MemorySegment) jxl.threads.create.invoke(MemorySegment.NULL, workers);
                    if (!runner.equals(MemorySegment.NULL)) {
                        expect(SUCCESS, (int) jxl.setParallelRunner.invoke(decoder, jxl.threads.runner, runner));
                    }
                }
                expect(SUCCESS, (int) jxl.subscribeEvents.invoke(decoder, BASIC_INFO | FULL_IMAGE));
                expect(SUCCESS, (int) jxl.setInput.invoke(decoder, data, data.byteSize()));
                jxl.closeInput.invoke(decoder);
                return run(jxl, decoder, arena);
            } finally {
                jxl.destroy.invoke(decoder);
                if (jxl.threads != null && !runner.equals(MemorySegment.NULL)) jxl.threads.destroy.invoke(runner);
            }
        } catch (IOException | RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new IOException("libjxl call failed", e);
        }
    }

    /** Drives the decoder's event loop to the first full frame. */
    private static WritableImage run(Library jxl, MemorySegment decoder, Arena arena) throws Throwable {
        MemorySegment info = arena.allocate(INFO_SIZE);
        MemorySegment format = arena.allocate(PIXEL_FORMAT);
        MemorySegment pixels = MemorySegment.NULL;
        int width = 0;
        int height = 0;
        int channels = 4;
        while (true) {
            int status = (int) jxl.processInput.invoke(decoder);
            switch (status) {
                case ERROR -> throw new IOException("not a valid JPEG XL file");
                case NEED_MORE_INPUT -> throw new IOException("the file is truncated");
                case SUCCESS -> throw new IOException("the file holds no image");
                case BASIC_INFO -> {
                    expect(SUCCESS, (int) jxl.getBasicInfo.invoke(decoder, info));
                    width = info.get(JAVA_INT, INFO_XSIZE);
                    height = info.get(JAVA_INT, INFO_YSIZE);
                    channels = info.get(JAVA_INT, INFO_ALPHA_BITS) > 0 ? 4 : 3;
                    format.set(JAVA_INT, 0, channels);
                    format.set(JAVA_INT, 4, TYPE_UINT8);
                    format.set(JAVA_INT, 8, NATIVE_ENDIAN);
                    format.set(JAVA_LONG, 16, 0L);
                }
                case NEED_IMAGE_OUT_BUFFER -> {
                    MemorySegment size = arena.allocate(JAVA_LONG);
                    expect(SUCCESS, (int) jxl.imageOutBufferSize.invoke(decoder, format, size));
                    pixels = arena.allocate(size.get(JAVA_LONG, 0));
                    expect(SUCCESS, (int) jxl.setImageOutBuffer.invoke(decoder, format, pixels, pixels.byteSize()));
                }
                case FULL_IMAGE -> {
                    if (pixels.equals(MemorySegment.NULL) || width <= 0 || height <= 0) {
                        throw new IOException("libjxl returned no pixels");
                    }
                    return Pixels.fromInterleaved(pixels, width, height, width * channels, channels);
                }
                default -> {
                    // An event we did not subscribe to; keep going.
                }
            }
        }
    }

    private static void expect(int wanted, int status) throws IOException {
        if (status != wanted) throw new IOException("libjxl status " + status);
    }

    /** {@code libjxl_threads}: optional, for decoding on every core. */
    private record Threads(
            MemorySegment runner, MethodHandle create, MethodHandle destroy, MethodHandle defaultWorkers) {

        static @Nullable Threads bind(Linker linker) {
            Optional<SymbolLookup> lookup = NativeLibraries.load(NativeLibraries.fileNames("jxl_threads", VERSIONS));
            if (lookup.isEmpty()) return null;
            try {
                return new Threads(
                        lookup.get().find("JxlThreadParallelRunner").orElseThrow(),
                        HeifDecoder.handle(
                                linker,
                                lookup.get(),
                                "JxlThreadParallelRunnerCreate",
                                FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_LONG)),
                        HeifDecoder.handle(
                                linker,
                                lookup.get(),
                                "JxlThreadParallelRunnerDestroy",
                                FunctionDescriptor.ofVoid(ADDRESS)),
                        HeifDecoder.handle(
                                linker,
                                lookup.get(),
                                "JxlThreadParallelRunnerDefaultNumWorkerThreads",
                                FunctionDescriptor.of(JAVA_LONG)));
            } catch (RuntimeException unusable) {
                return null;
            }
        }
    }

    /** The bound functions, or empty when libjxl is not installed. Looked up once, on first use. */
    private record Library(
            MethodHandle create,
            MethodHandle destroy,
            MethodHandle subscribeEvents,
            MethodHandle setInput,
            MethodHandle closeInput,
            MethodHandle processInput,
            MethodHandle getBasicInfo,
            MethodHandle imageOutBufferSize,
            MethodHandle setImageOutBuffer,
            MethodHandle setParallelRunner,
            @Nullable Threads threads) {

        static final Optional<Library> INSTANCE =
                NativeLibraries.load(NativeLibraries.fileNames("jxl", VERSIONS)).flatMap(Library::bind);

        private static Optional<Library> bind(SymbolLookup lookup) {
            Linker linker = Linker.nativeLinker();
            try {
                return Optional.of(new Library(
                        HeifDecoder.handle(linker, lookup, "JxlDecoderCreate", FunctionDescriptor.of(ADDRESS, ADDRESS)),
                        HeifDecoder.handle(linker, lookup, "JxlDecoderDestroy", FunctionDescriptor.ofVoid(ADDRESS)),
                        HeifDecoder.handle(
                                linker,
                                lookup,
                                "JxlDecoderSubscribeEvents",
                                FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT)),
                        HeifDecoder.handle(
                                linker,
                                lookup,
                                "JxlDecoderSetInput",
                                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_LONG)),
                        HeifDecoder.handle(linker, lookup, "JxlDecoderCloseInput", FunctionDescriptor.ofVoid(ADDRESS)),
                        HeifDecoder.handle(
                                linker, lookup, "JxlDecoderProcessInput", FunctionDescriptor.of(JAVA_INT, ADDRESS)),
                        HeifDecoder.handle(
                                linker,
                                lookup,
                                "JxlDecoderGetBasicInfo",
                                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS)),
                        HeifDecoder.handle(
                                linker,
                                lookup,
                                "JxlDecoderImageOutBufferSize",
                                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS)),
                        HeifDecoder.handle(
                                linker,
                                lookup,
                                "JxlDecoderSetImageOutBuffer",
                                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, JAVA_LONG)),
                        HeifDecoder.handle(
                                linker,
                                lookup,
                                "JxlDecoderSetParallelRunner",
                                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS)),
                        Threads.bind(linker)));
            } catch (RuntimeException unusable) {
                return Optional.empty();
            }
        }
    }
}
