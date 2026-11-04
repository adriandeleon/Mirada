package com.mirada.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScrollingTest {

    @Test
    void aWheelZoomsWhateverTheImage() {
        assertEquals(Scrolling.Action.ZOOM, Scrolling.of(false, false, false));
        assertEquals(Scrolling.Action.ZOOM, Scrolling.of(false, false, true));
    }

    @Test
    void aTrackpadPansWhatIsZoomedInAndSwipesOtherwise() {
        assertEquals(Scrolling.Action.PAN, Scrolling.of(true, false, true));
        assertEquals(Scrolling.Action.SWIPE, Scrolling.of(true, false, false));
    }

    @Test
    void theShortcutKeyZoomsFromEither() {
        assertEquals(Scrolling.Action.ZOOM, Scrolling.of(true, true, true));
        assertEquals(Scrolling.Action.ZOOM, Scrolling.of(true, true, false));
    }

    @Test
    void fingersToTheLeftGoToTheNextImageOnceFarEnough() {
        assertEquals(0, Scrolling.swipeStep(-Scrolling.SWIPE_DISTANCE + 1, 0));
        assertEquals(1, Scrolling.swipeStep(-Scrolling.SWIPE_DISTANCE, 0));
        assertEquals(-1, Scrolling.swipeStep(Scrolling.SWIPE_DISTANCE, 0));
    }

    @Test
    void aMostlyVerticalScrollIsNotASwipe() {
        assertEquals(0, Scrolling.swipeStep(-100, 60));
        assertEquals(1, Scrolling.swipeStep(-100, 40));
    }
}
