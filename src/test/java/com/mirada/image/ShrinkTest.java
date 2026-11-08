package com.mirada.image;

import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

@Tag("fx")
class ShrinkTest {

    @BeforeAll
    static void toolkit() throws InterruptedException {
        FxToolkit.start();
    }

    @Test
    void fitsTheLongerSideAndKeepsTheShape() {
        assertArrayEquals(new int[] {1280, 853}, Shrink.fittedSize(6000, 4000, 1280));
        assertArrayEquals(new int[] {853, 1280}, Shrink.fittedSize(4000, 6000, 1280));
        assertArrayEquals(new int[] {100, 50}, Shrink.fittedSize(100, 50, 1280));
        assertArrayEquals(new int[] {1280, 1}, Shrink.fittedSize(100_000, 10, 1280));
    }

    @Test
    void averagesEachBlock() {
        // Two 2 × 2 blocks: black and white, then red and blue.
        int[] pixels = {
            0xFF000000, 0xFFFFFFFF, 0xFFFF0000, 0xFF0000FF,
            0xFFFFFFFF, 0xFF000000, 0xFF0000FF, 0xFFFF0000,
        };
        Image source = new ArgbPixels(4, 2, pixels).toImage();
        ArgbPixels small = Shrink.box(source.getPixelReader(), 4, 2, 2, 1);
        assertEquals(0xFF7F7F7F, small.pixels()[0]);
        assertEquals(0xFF7F007F, small.pixels()[1]);
    }

    @Test
    void anImageThatAlreadyFitsIsReturnedAsItIs() {
        Image small = new WritableImage(10, 10);
        assertSame(small, Shrink.toFit(small, 1280));
    }
}
