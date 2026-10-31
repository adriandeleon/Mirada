package com.mirada.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ZoomTest {

    @Test
    void fitShrinksToTheTighterSide() {
        assertEquals(0.25, Zoom.fitScale(4000, 3000, 1000, 1000), 1e-9);
        assertEquals(0.2, Zoom.fitScale(1000, 5000, 1000, 1000), 1e-9);
    }

    @Test
    void fitNeverEnlarges() {
        assertEquals(1, Zoom.fitScale(200, 100, 1000, 1000), 1e-9);
    }

    @Test
    void fitWithoutASizeIsOne() {
        assertEquals(1, Zoom.fitScale(0, 100, 1000, 1000), 1e-9);
        assertEquals(1, Zoom.fitScale(100, 100, 0, 0), 1e-9);
    }

    @Test
    void stepsGoToTheNextStop() {
        assertEquals(1.5, Zoom.zoomIn(1), 1e-9);
        assertEquals(2 / 3.0, Zoom.zoomOut(1), 1e-9);
        assertEquals(0.25, Zoom.zoomIn(0.23), 1e-9);
        assertEquals(0.125, Zoom.zoomOut(0.23), 1e-9);
    }

    @Test
    void stepsStopAtTheEnds() {
        assertEquals(Zoom.MAX, Zoom.zoomIn(Zoom.MAX));
        assertEquals(Zoom.MIN, Zoom.zoomOut(Zoom.MIN));
        assertEquals(Zoom.MAX, Zoom.clamp(1000));
        assertEquals(Zoom.MIN, Zoom.clamp(0));
    }

    @Test
    void zoomingKeepsThePointUnderThePivot() {
        // The image starts at x=100; the pointer at x=300 is over image pixel 200 at scale 1.
        double offset = Zoom.offsetAbout(100, 300, 1, 2);
        assertEquals(-100, offset, 1e-9);
        // At scale 2, pixel 200 is 400 from the image's edge: -100 + 400 = 300, still under the pointer.
        assertEquals(300, offset + 200 * 2, 1e-9);
    }

    @Test
    void aSmallImageIsCentred() {
        assertEquals(250, Zoom.clampOffset(-999, 500, 1000), 1e-9);
    }

    @Test
    void aLargeImageNeverShowsAGap() {
        assertEquals(0, Zoom.clampOffset(50, 2000, 1000), 1e-9);
        assertEquals(-1000, Zoom.clampOffset(-1500, 2000, 1000), 1e-9);
        assertEquals(-400, Zoom.clampOffset(-400, 2000, 1000), 1e-9);
    }

    @Test
    void labels() {
        assertEquals("Fit · 23%", Zoom.label(0.2312, true));
        assertEquals("150%", Zoom.label(1.5, false));
        assertEquals("1%", Zoom.label(0.001, false));
    }
}
