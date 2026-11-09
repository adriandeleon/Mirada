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
 * not. {@link #pixels} decodes an animation's first frame, {@link #frames} all of them (libjxl
 * composes partial frames itself).
 *
 * <p>The pixels come out in sRGB, as every other decoder's do: libjxl converts to it through its
 * colour management ({@code libjxl_cms}, set with {@code JxlDecoderSetCms}, libjxl 0.9 and later).
 * Without one, it can still convert an XYB-encoded (lossy) file, and a lossless file in another
 * colour space keeps it.
 *
 * <p>The struct offsets below are libjxl's ABI from 0.7 to 0.12: {@code JxlBasicInfo} starts with {@code
 * have_container, xsize, ysize}, has {@code have_animation} at byte 44, {@code num_color_channels} at
 * 52, {@code alpha_bits} at 60 and the animation's ticks per second at 80 and 84;
 * {@code JxlFrameHeader} starts with its duration; {@code JxlColorEncoding} is laid out as {@link
 * #COLOR_ENCODING_SIZE} describes; {@code JxlCmsInterface} is eight pointers.
 */
final class JxlDecoder {

    private static final int SUCCESS = 0;
    private static final int ERROR = 1;
    private static final int NEED_MORE_INPUT = 2;
    private static final int NEED_IMAGE_OUT_BUFFER = 5;
    private static final int BASIC_INFO = 0x40;
    private static final int COLOR_ENCODING = 0x100;
    private static final int FRAME = 0x400;
    private static final int FULL_IMAGE = 0x1000;

    private static final int TYPE_UINT8 = 2;
    private static final int NATIVE_ENDIAN = 0;

    private static final long INFO_XSIZE = 4;
    private static final long INFO_YSIZE = 8;
    private static final long INFO_HAVE_ANIMATION = 44;
    private static final long INFO_COLOR_CHANNELS = 52;
    private static final long INFO_ALPHA_BITS = 60;
    private static final long INFO_TICKS_NUMERATOR = 80;
    private static final long INFO_TICKS_DENOMINATOR = 84;
    /** {@code JxlFrameHeader} is 56 bytes in 0.12; room to spare. */
    private static final long FRAME_HEADER_SIZE = 128;

    /**
     * {@code JxlColorEncoding}: colour space and white point (ints), the white point's xy (two
     * doubles, at 8), primaries (int, at 24), three primaries' xy (six doubles, at 32), the transfer
     * function (int, at 80), gamma (double, at 88), the rendering intent (int, at 96): 104 bytes.
     */
    static final long COLOR_ENCODING_SIZE = 104;

    private static final int COLOR_SPACE_RGB = 0;
    private static final int COLOR_SPACE_GRAY = 1;
    private static final int WHITE_POINT_D65 = 1;
    private static final int PRIMARIES_SRGB = 1;
    private static final int TRANSFER_FUNCTION_SRGB = 13;
    private static final int RENDERING_INTENT_RELATIVE = 1;

    /** {@code JxlCmsInterface}, passed by value to {@code JxlDecoderSetCms}. */
    private static final StructLayout CMS_INTERFACE =
            MemoryLayout.structLayout(ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS);
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
        return pixels(file).toImage();
    }

    /** The image, or an animation's first frame, decoded without the JavaFX toolkit. */
    static ArgbPixels pixels(Path file) throws IOException {
        ArgbPixels first = decode(file, false).first();
        if (first == null) throw new IOException("the file holds no image");
        return first;
    }

    /** Every frame of an animated JPEG XL, or null for a still or one too large to hold. */
    static Animation.@Nullable Frames frames(Path file) throws IOException {
        return decode(file, true).frames();
    }

    /** What a decode produced: the first frame (null when only frames were wanted and it is a still). */
    private record Result(@Nullable ArgbPixels first, Animation.@Nullable Frames frames) {}

    private static Result decode(Path file, boolean allFrames) throws IOException {
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
                int events = BASIC_INFO | COLOR_ENCODING | FULL_IMAGE | (allFrames ? FRAME : 0);
                expect(SUCCESS, (int) jxl.subscribeEvents.invoke(decoder, events));
                expect(SUCCESS, (int) jxl.setInput.invoke(decoder, data, data.byteSize()));
                jxl.closeInput.invoke(decoder);
                return run(jxl, decoder, arena, allFrames);
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

    /**
     * Drives the decoder's event loop: to the first full frame, or with {@code allFrames} through
     * every frame of an animation (and no further than the basic information for a still).
     */
    private static Result run(Library jxl, MemorySegment decoder, Arena arena, boolean allFrames) throws Throwable {
        MemorySegment info = arena.allocate(INFO_SIZE);
        MemorySegment format = arena.allocate(PIXEL_FORMAT);
        MemorySegment header = arena.allocate(FRAME_HEADER_SIZE);
        MemorySegment pixels = MemorySegment.NULL;
        int width = 0;
        int height = 0;
        int channels = 4;
        double millisPerTick = 0;
        long frameMillis = 0;
        ArgbPixels first = null;
        Animation.Collector collector = new Animation.Collector();
        while (true) {
            int status = (int) jxl.processInput.invoke(decoder);
            switch (status) {
                case ERROR -> throw new IOException("not a valid JPEG XL file");
                case NEED_MORE_INPUT -> throw new IOException("the file is truncated");
                case SUCCESS -> {
                    if (first == null) throw new IOException("the file holds no image");
                    return new Result(first, allFrames ? collector.frames() : null);
                }
                case BASIC_INFO -> {
                    expect(SUCCESS, (int) jxl.getBasicInfo.invoke(decoder, info));
                    width = info.get(JAVA_INT, INFO_XSIZE);
                    height = info.get(JAVA_INT, INFO_YSIZE);
                    // From the header, before a pixel is decoded: refused if it will not fit (PERF-12).
                    DecodeGuard.check(width, height);
                    channels = info.get(JAVA_INT, INFO_ALPHA_BITS) > 0 ? 4 : 3;
                    format.set(JAVA_INT, 0, channels);
                    format.set(JAVA_INT, 4, TYPE_UINT8);
                    format.set(JAVA_INT, 8, NATIVE_ENDIAN);
                    format.set(JAVA_LONG, 16, 0L);
                    boolean animated = info.get(JAVA_INT, INFO_HAVE_ANIMATION) != 0;
                    if (allFrames && !animated) return new Result(null, null);
                    int numerator = info.get(JAVA_INT, INFO_TICKS_NUMERATOR);
                    int denominator = info.get(JAVA_INT, INFO_TICKS_DENOMINATOR);
                    millisPerTick = numerator > 0 ? 1000.0 * denominator / numerator : 0;
                }
                case COLOR_ENCODING -> toSrgb(jxl, decoder, arena, info.get(JAVA_INT, INFO_COLOR_CHANNELS) == 1);
                case FRAME -> {
                    expect(SUCCESS, (int) jxl.getFrameHeader.invoke(decoder, header));
                    frameMillis = Math.round(Integer.toUnsignedLong(header.get(JAVA_INT, 0)) * millisPerTick);
                }
                case NEED_IMAGE_OUT_BUFFER -> {
                    if (pixels.equals(MemorySegment.NULL)) {
                        MemorySegment size = arena.allocate(JAVA_LONG);
                        expect(SUCCESS, (int) jxl.imageOutBufferSize.invoke(decoder, format, size));
                        pixels = arena.allocate(size.get(JAVA_LONG, 0));
                    }
                    expect(SUCCESS, (int) jxl.setImageOutBuffer.invoke(decoder, format, pixels, pixels.byteSize()));
                }
                case FULL_IMAGE -> {
                    if (pixels.equals(MemorySegment.NULL) || width <= 0 || height <= 0) {
                        throw new IOException("libjxl returned no pixels");
                    }
                    ArgbPixels frame = Pixels.fromInterleaved(pixels, width, height, width * channels, channels);
                    if (first == null) first = frame;
                    if (!allFrames) return new Result(first, null);
                    if (!collector.add(frame, frameMillis)) return new Result(first, null); // too large to hold
                }
                default -> {
                    // An event we did not subscribe to; keep going.
                }
            }
        }
    }

    /**
     * Asks for sRGB output: through the colour management when libjxl has it, which converts any
     * file; else as a preference, which converts an XYB-encoded one. A refusal keeps the file's own.
     */
    private static void toSrgb(Library jxl, MemorySegment decoder, Arena arena, boolean grey) throws Throwable {
        MemorySegment srgb = arena.allocate(COLOR_ENCODING_SIZE, 8);
        srgb.set(JAVA_INT, 0, grey ? COLOR_SPACE_GRAY : COLOR_SPACE_RGB);
        srgb.set(JAVA_INT, 4, WHITE_POINT_D65);
        srgb.set(JAVA_INT, 24, PRIMARIES_SRGB);
        srgb.set(JAVA_INT, 80, TRANSFER_FUNCTION_SRGB);
        srgb.set(JAVA_INT, 96, RENDERING_INTENT_RELATIVE);
        Colour colour = jxl.colour;
        if (colour == null) return;
        MemorySegment cms = colour.defaultCms;
        if (cms != null && (int) colour.setCms.invoke(decoder, cms) == SUCCESS) {
            colour.setOutputColorProfile.invoke(decoder, srgb, MemorySegment.NULL, 0L);
        } else {
            colour.setPreferredColorProfile.invoke(decoder, srgb);
        }
    }

    private static void expect(int wanted, int status) throws IOException {
        if (status != wanted) throw new IOException("libjxl status " + status);
    }

    /**
     * libjxl 0.9's colour output: optional, as older system copies lack it. {@code defaultCms} is
     * {@code libjxl_cms}'s interface, null without that library.
     */
    private record Colour(
            MethodHandle setCms,
            MethodHandle setOutputColorProfile,
            MethodHandle setPreferredColorProfile,
            @Nullable MemorySegment defaultCms) {

        static @Nullable Colour bind(Linker linker, SymbolLookup jxl) {
            try {
                MethodHandle setCms = HeifDecoder.handle(
                        linker, jxl, "JxlDecoderSetCms", FunctionDescriptor.of(JAVA_INT, ADDRESS, CMS_INTERFACE));
                MethodHandle output = HeifDecoder.handle(
                        linker,
                        jxl,
                        "JxlDecoderSetOutputColorProfile",
                        FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, JAVA_LONG));
                MethodHandle preferred = HeifDecoder.handle(
                        linker,
                        jxl,
                        "JxlDecoderSetPreferredColorProfile",
                        FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));
                return new Colour(setCms, output, preferred, defaultCms(linker));
            } catch (RuntimeException olderThan09) {
                return null;
            }
        }

        private static @Nullable MemorySegment defaultCms(Linker linker) {
            Optional<SymbolLookup> lookup = NativeLibraries.load(NativeLibraries.fileNames("jxl_cms", VERSIONS));
            if (lookup.isEmpty()) return null;
            try {
                MethodHandle get =
                        HeifDecoder.handle(linker, lookup.get(), "JxlGetDefaultCms", FunctionDescriptor.of(ADDRESS));
                MemorySegment cms = (MemorySegment) get.invoke();
                return cms.equals(MemorySegment.NULL) ? null : cms.reinterpret(CMS_INTERFACE.byteSize());
            } catch (Throwable unusable) {
                return null;
            }
        }
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
            MethodHandle getFrameHeader,
            @Nullable Threads threads,
            @Nullable Colour colour) {

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
                        HeifDecoder.handle(
                                linker,
                                lookup,
                                "JxlDecoderGetFrameHeader",
                                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS)),
                        Threads.bind(linker),
                        Colour.bind(linker, lookup)));
            } catch (RuntimeException unusable) {
                return Optional.empty();
            }
        }
    }
}
