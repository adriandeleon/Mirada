package com.mirada.edit;

import com.mirada.image.Orientation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditTest {

    /**
     * 3 × 2, numbered in reading order:
     *
     * <pre>
     *   1 2 3
     *   4 5 6
     * </pre>
     */
    private static final Raster SIX = new Raster(3, 2, new int[] {1, 2, 3, 4, 5, 6});

    @Test
    void turningClockwisePutsTheLeftColumnOnTop() {
        Raster turned = SIX.turned(1);
        assertEquals(2, turned.width());
        assertEquals(3, turned.height());
        assertArrayEquals(new int[] {4, 1, 5, 2, 6, 3}, turned.pixels());
        assertArrayEquals(new int[] {3, 6, 2, 5, 1, 4}, SIX.turned(-1).pixels());
        assertArrayEquals(new int[] {6, 5, 4, 3, 2, 1}, SIX.turned(2).pixels());
        assertArrayEquals(SIX.pixels(), SIX.turned(4).pixels());
    }

    @Test
    void mirrorsAndCrops() {
        assertArrayEquals(new int[] {3, 2, 1, 6, 5, 4}, SIX.mirrored().pixels());
        assertArrayEquals(new int[] {4, 5, 6, 1, 2, 3}, SIX.flippedVertically().pixels());
        assertArrayEquals(new int[] {2, 3, 5, 6}, SIX.cropped(1, 0, 2, 2).pixels());
    }

    /** The file's orientation means "mirror, then turn clockwise", as JavaFX's node transforms do. */
    @Test
    void uprightMirrorsFirstThenTurns() {
        assertArrayEquals(SIX.pixels(), Edit.upright(SIX, Orientation.NORMAL).pixels());
        assertArrayEquals(
                SIX.turned(1).pixels(), Edit.upright(SIX, Orientation.ROTATE_90).pixels());
        assertArrayEquals(
                SIX.mirrored().turned(3).pixels(),
                Edit.upright(SIX, Orientation.TRANSPOSE).pixels());
    }

    @Test
    void anEditTurnsThenFlipsThenCrops() {
        Edit edit = Edit.NONE.turned(1).flippedHorizontally().withCrop(new Crop(0, 0, 1, 1.0 / 3));
        // Turned: 4 1 / 5 2 / 6 3; mirrored: 1 4 / 2 5 / 3 6; top third: 1 4.
        assertArrayEquals(new int[] {1, 4}, edit.apply(SIX).pixels());
    }

    @Test
    void turningStartsTheCropAgainAndFlippingCarriesIt() {
        Edit cropped = Edit.NONE.withCrop(new Crop(0, 0, 0.5, 1));
        assertTrue(cropped.turned(1).crop().isAll());
        assertEquals(new Crop(0.5, 0, 0.5, 1), cropped.flippedHorizontally().crop());
        assertFalse(cropped.isNone());
        assertTrue(Edit.NONE.isNone());
    }

    @Test
    void aCropBecomesWholePixelsInsideTheImage() {
        assertArrayEquals(new int[] {0, 0, 100, 50}, new Crop(0, 0, 1, 0.5).pixels(100, 100));
        assertArrayEquals(new int[] {99, 0, 1, 100}, new Crop(1, 0, 0, 1).pixels(100, 100));
        assertEquals(new Crop(0.5, 0, 0.5, 1), new Crop(0.5, 0, 0.9, 1));
    }
}
