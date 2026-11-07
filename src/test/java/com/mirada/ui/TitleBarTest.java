package com.mirada.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TitleBarTest {

    @Test
    void aMacCentresTheTitle() {
        assertEquals(new TitleBar.Layout(false), TitleBar.Layout.of("Mac OS X"));
    }

    @Test
    void windowsPutsTheTitleOnTheLeft() {
        assertEquals(new TitleBar.Layout(true), TitleBar.Layout.of("Windows 11"));
    }

    @Test
    void linuxIsGnomes() {
        assertEquals(new TitleBar.Layout(false), TitleBar.Layout.of("Linux"));
    }

    @Test
    void withRoomTheFolderGetsAllItAsksFor() {
        // 120 + 6 + 10 + 6 + 200 = 342
        assertEquals(120, TitleBar.Heading.folderWidth(342, 120, 10, 200, 6));
        assertEquals(120, TitleBar.Heading.folderWidth(800, 120, 10, 200, 6));
    }

    @Test
    void shortOfRoomTheFolderGivesWayAndTheNameKeepsItsWidth() {
        assertEquals(78, TitleBar.Heading.folderWidth(300, 120, 10, 200, 6));
    }

    @Test
    void tooLittleOfTheFolderIsNone() {
        double atTheLeast = TitleBar.Heading.MIN_FOLDER + 6 + 10 + 6 + 200;
        assertEquals(TitleBar.Heading.MIN_FOLDER, TitleBar.Heading.folderWidth(atTheLeast, 120, 10, 200, 6));
        assertEquals(0, TitleBar.Heading.folderWidth(atTheLeast - 1, 120, 10, 200, 6));
        assertEquals(0, TitleBar.Heading.folderWidth(100, 120, 10, 200, 6));
    }
}
