package com.mirada.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OnScreenControlsTest {

    @Test
    void aScreenReaderSaysTheTooltipWithoutItsKey() {
        assertEquals("Next Image", OnScreenControls.spoken("Next Image (→)"));
        assertEquals("Zoom to Fit", OnScreenControls.spoken("Zoom to Fit (0)"));
        assertEquals("Zoom", OnScreenControls.spoken("Zoom"));
    }
}
