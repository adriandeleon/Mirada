package com.mirada.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TitleBarTest {

    /** WIN-03: the menus in the title bar by default; a Mac's header holds none, and is always drawn. */
    @Test
    void theWindowDrawsItsTitleBarWhereItCanAndTheSettingSays() {
        assertTrue(TitleBar.usesHeaderBar(false, true, true), "Windows and Linux, by default");
        assertFalse(TitleBar.usesHeaderBar(false, false, true), "the setting off: the system's title bar");
        assertTrue(TitleBar.usesHeaderBar(true, false, true), "a Mac's header, which holds no menus");
        assertFalse(TitleBar.usesHeaderBar(false, true, false), "never where the platform cannot");
        assertFalse(TitleBar.usesHeaderBar(true, true, false));
    }

    /** OS-01: the setting is on every OS, and says why where it can do nothing. */
    @Test
    void theSettingSaysWhyWhereItCanDoNothing() {
        assertNull(TitleBar.titleBarMenusUnavailable(false, true));
        assertNotNull(TitleBar.titleBarMenusUnavailable(true, true));
        assertNotNull(TitleBar.titleBarMenusUnavailable(false, false));
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
