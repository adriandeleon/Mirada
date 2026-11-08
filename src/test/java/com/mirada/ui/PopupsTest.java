package com.mirada.ui;

import javafx.geometry.BoundingBox;
import javafx.geometry.Point2D;
import javafx.scene.input.KeyCode;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The keyboard's right-click (Pauta MENU-09). */
class PopupsTest {

    @Test
    void theMenuKeyAndShiftF10AreTheKeyboardsRightClick() {
        assertTrue(Popups.isMenuKey(KeyCode.CONTEXT_MENU, false, false, false));
        assertTrue(Popups.isMenuKey(KeyCode.F10, true, false, false));
        assertFalse(Popups.isMenuKey(KeyCode.F10, false, false, false), "F10 alone");
        assertFalse(Popups.isMenuKey(KeyCode.F10, true, true, false), "Ctrl+Shift+F10");
        assertFalse(Popups.isMenuKey(KeyCode.CONTEXT_MENU, false, false, true), "Alt+Menu");
    }

    @Test
    void aKeyboardMenuStandsUnderItsRowALittleIn() {
        assertEquals(new Point2D(148, 130), Popups.under(new BoundingBox(100, 100, 400, 30)));
        assertEquals(new Point2D(105, 130), Popups.under(new BoundingBox(100, 100, 20, 30)), "a narrow row");
    }
}
