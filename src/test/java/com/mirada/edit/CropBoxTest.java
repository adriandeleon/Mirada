package com.mirada.edit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CropBoxTest {

    private static final double W = 600;
    private static final double H = 400;

    @Test
    void theLargestBoxOfARatioIsCentred() {
        assertEquals(new CropBox(100, 0, 400, 400), CropBox.largest(1, W, H));
        assertEquals(new CropBox(0, 0, W, H), CropBox.largest(0, W, H));
        assertEquals(new CropBox(0, 31.25, 600, 337.5), CropBox.largest(16.0 / 9, W, H));
    }

    @Test
    void movingStopsAtTheEdges() {
        CropBox box = new CropBox(100, 100, 200, 100);
        assertEquals(new CropBox(400, 0, 200, 100), box.moved(1000, -1000, W, H));
    }

    @Test
    void aFreeCornerMovesOnlyItsOwnEdges() {
        CropBox box = new CropBox(100, 100, 200, 100);
        assertEquals(new CropBox(100, 100, 250, 130), box.dragged(CropBox.Handle.SE, 50, 30, 0, W, H));
        assertEquals(new CropBox(50, 100, 250, 100), box.dragged(CropBox.Handle.W, -50, 999, 0, W, H));
    }

    @Test
    void neverSmallerThanTheMinimumNorOutside() {
        CropBox box = new CropBox(100, 100, 200, 100);
        assertEquals(new CropBox(100, 100, CropBox.MIN, 100), box.dragged(CropBox.Handle.E, -1000, 0, 0, W, H));
        assertEquals(new CropBox(0, 100, 300, 100), box.dragged(CropBox.Handle.W, -1000, 0, 0, W, H));
    }

    @Test
    void aHeldCornerKeepsTheRatioFromTheOppositeCorner() {
        CropBox box = new CropBox(100, 100, 200, 100);
        CropBox dragged = box.dragged(CropBox.Handle.SE, 100, 0, 2, W, H);
        assertEquals(new CropBox(100, 100, 300, 150), dragged);
        // The right edge stops at the picture's; the height follows the width.
        assertEquals(new CropBox(100, 100, 500, 250), box.dragged(CropBox.Handle.SE, 1000, 0, 2, W, H));
        // Out of room below: the height is capped and the width follows.
        assertEquals(
                new CropBox(100, 200, 400, 200),
                new CropBox(100, 200, 200, 100).dragged(CropBox.Handle.SE, 1000, 0, 2, W, H));
    }

    @Test
    void aHeldEdgeGrowsTheOtherSideAboutTheCentre() {
        CropBox box = new CropBox(100, 100, 200, 100);
        assertEquals(new CropBox(100, 75, 300, 150), box.dragged(CropBox.Handle.E, 100, 0, 2, W, H));
    }

    @Test
    void fractionsRoundTrip() {
        CropBox box = new CropBox(150, 100, 300, 200);
        assertEquals(new Crop(0.25, 0.25, 0.5, 0.5), box.toCrop(W, H));
        assertEquals(box, CropBox.of(box.toCrop(W, H), W, H));
    }

    @Test
    void fixedRatiosFollowThePicturesShape() {
        assertEquals(1.5, AspectRatio.THREE_TWO.ratio(600, 400));
        assertEquals(2.0 / 3, AspectRatio.THREE_TWO.ratio(400, 600), 1e-9);
        assertEquals(1.5, AspectRatio.ORIGINAL.ratio(600, 400));
        assertEquals(0, AspectRatio.FREE.ratio(600, 400));
        assertEquals(AspectRatio.FREE, AspectRatio.SIXTEEN_NINE.next());
    }
}
