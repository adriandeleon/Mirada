package com.mirada.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RenamingTest {

    @Test
    void theStemIsWhatGetsSelected() {
        assertEquals(8, Renaming.stemLength("IMG_0042.jpg"));
        assertEquals(6, Renaming.stemLength("README"));
        assertEquals(7, Renaming.stemLength(".hidden"));
    }

    @Test
    void aNameWithoutAnExtensionKeepsTheOldOne() {
        assertEquals("beach.jpg", Renaming.validName("IMG_0042.jpg", "beach"));
        assertEquals("beach.png", Renaming.validName("IMG_0042.jpg", "beach.png"));
        assertEquals("beach.jpg", Renaming.validName("IMG_0042.jpg", "  beach  "));
    }

    @Test
    void namesThatCannotBeAFileAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> Renaming.validName("a.jpg", " "));
        assertThrows(IllegalArgumentException.class, () -> Renaming.validName("a.jpg", "../b"));
        assertThrows(IllegalArgumentException.class, () -> Renaming.validName("a.jpg", "b\\c"));
        assertThrows(IllegalArgumentException.class, () -> Renaming.validName("a.jpg", ".."));
    }
}
