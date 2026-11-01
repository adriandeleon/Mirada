package com.mirada.image;

import java.net.URISyntaxException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Every frame of the matrix's animations, through the decoders that compose them, and none for a still. */
class AnimationTest {

    private static Path matrix(String name) throws URISyntaxException {
        return Path.of(
                AnimationTest.class.getResource("/com/mirada/matrix/" + name).toURI());
    }

    @Test
    void webpFramesAndTheirTimes() throws Exception {
        assumeTrue(WebpDecoder.available(), "libwebp is not installed");
        Animation.Frames frames = WebpDecoder.frames(matrix("webp-animated.webp"));
        assertNotNull(frames);
        assertEquals(3, frames.pixels().size());
        assertEquals(48, frames.pixels().getFirst().width());
        assertNull(WebpDecoder.frames(matrix("webp-lossy.webp")));
    }

    @Test
    void jpegXlFramesAtAQuarterSecondEach() throws Exception {
        assumeTrue(JxlDecoder.available(), "libjxl is not installed");
        Animation.Frames frames = JxlDecoder.frames(matrix("jxl-animated.jxl"));
        assertNotNull(frames);
        assertArrayEquals(new int[] {250, 250, 250}, frames.millis());
        // Red, blue, lime: whole frames, not the differences between them.
        assertEquals(0xFFFF0000, frames.pixels().get(0).pixels()[0]);
        assertEquals(0xFF0000FF, frames.pixels().get(1).pixels()[0]);
        assertEquals(0xFF00FF00, frames.pixels().get(2).pixels()[0]);
        assertNull(JxlDecoder.frames(matrix("jxl-rgb.jxl")));
    }

    @Test
    void anAvifSequence() throws Exception {
        assumeTrue(HeifDecoder.available(), "libheif is not installed");
        Animation.Frames frames = HeifDecoder.frames(matrix("avif-animated.avif"));
        assertNotNull(frames, "libheif without sequences, or the file is not one");
        assertEquals(3, frames.pixels().size());
        assertEquals(48, frames.pixels().getFirst().width());
        assertNull(HeifDecoder.frames(matrix("avif-rgb.avif")));
    }

    @Test
    void durationsTooShortToSeeBecomeATenthOfASecond() {
        assertEquals(100, Animation.sane(0));
        assertEquals(100, Animation.sane(10));
        assertEquals(40, Animation.sane(40));
        assertEquals(60_000, Animation.sane(10_000_000));
    }
}
