package com.mirada.print;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrintLayoutTest {

    @Test
    void aWideImageFillsTheWidthAndIsCentredDownThePage() {
        // A 4:3 photo on a 500 × 700 area: 500 wide, 375 tall, 162.5 from the top.
        assertEquals(new PrintLayout.Placement(0, 162.5, 500, 375), PrintLayout.fit(4000, 3000, 500, 700));
    }

    @Test
    void aTallImageFillsTheHeightAndIsCentredAcross() {
        assertEquals(new PrintLayout.Placement(150, 0, 200, 400), PrintLayout.fit(100, 200, 500, 400));
    }

    @Test
    void aSmallImageIsEnlargedToThePage() {
        assertEquals(new PrintLayout.Placement(0, 0, 600, 600), PrintLayout.fit(10, 10, 600, 600));
    }

    @Test
    void nothingToFitPlacesNothing() {
        assertEquals(new PrintLayout.Placement(0, 0, 0, 0), PrintLayout.fit(0, 10, 600, 600));
        assertEquals(new PrintLayout.Placement(0, 0, 0, 0), PrintLayout.fit(10, 10, 600, 0));
    }

    @Test
    void onlyAWideImageStartsOnALandscapePage() {
        assertTrue(PrintLayout.landscape(3000, 2000));
        assertFalse(PrintLayout.landscape(2000, 3000));
        assertFalse(PrintLayout.landscape(2000, 2000));
    }
}
