package com.mirada.ui;

import java.util.List;

import javafx.geometry.Rectangle2D;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class WindowPlacementTest {

    private static final Rectangle2D LAPTOP = new Rectangle2D(0, 25, 1440, 875);
    private static final Rectangle2D MONITOR = new Rectangle2D(1440, 0, 2560, 1400);

    @Test
    void aWindowStillOnAScreenGoesBackWhereItWas() {
        Rectangle2D saved = new Rectangle2D(1600, 100, 1200, 800);
        assertEquals(saved, WindowPlacement.placed(saved, List.of(LAPTOP, MONITOR)));
    }

    @Test
    void aWindowOnAMonitorUnpluggedSinceOpensWhereTheSystemPutsIt() {
        assertNull(WindowPlacement.placed(new Rectangle2D(1600, 100, 1200, 800), List.of(LAPTOP)));
    }

    @Test
    void aWindowLargerThanItsScreenIsShrunkOntoIt() {
        Rectangle2D placed = WindowPlacement.placed(new Rectangle2D(100, 50, 2400, 1300), List.of(LAPTOP));
        assertEquals(new Rectangle2D(0, 25, 1440, 875), placed);
    }

    @Test
    void aTitleBarAboveTheScreenIsNotReachable() {
        assertNull(WindowPlacement.placed(new Rectangle2D(100, -400, 800, 600), List.of(LAPTOP)));
    }

    @Test
    void aWindowHalfOffTheEdgeIsPulledBackOn() {
        Rectangle2D placed = WindowPlacement.placed(new Rectangle2D(1000, 100, 800, 600), List.of(LAPTOP));
        assertEquals(new Rectangle2D(640, 100, 800, 600), placed);
    }
}
