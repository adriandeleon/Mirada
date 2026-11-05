package com.mirada.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TitleBarTest {

    @Test
    void aMacHasNeitherTheFullScreenButtonNorTheMenuAndCentresTheTitle() {
        assertEquals(new TitleBar.Layout(false, false, false), TitleBar.Layout.of("Mac OS X"));
    }

    @Test
    void windowsPutsTheTitleOnTheLeft() {
        assertEquals(new TitleBar.Layout(true, true, true), TitleBar.Layout.of("Windows 11"));
    }

    @Test
    void linuxIsGnomes() {
        assertEquals(new TitleBar.Layout(true, true, false), TitleBar.Layout.of("Linux"));
    }
}
