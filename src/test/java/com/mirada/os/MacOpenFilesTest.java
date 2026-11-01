package com.mirada.os;

import java.io.File;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MacOpenFilesTest {

    @Test
    void onlyMacOsNeedsAwt() {
        assertFalse(MacOpenFiles.headless("Mac OS X"));
        assertFalse(MacOpenFiles.headless("Darwin"));
        assertTrue(MacOpenFiles.headless("Linux"));
        assertTrue(MacOpenFiles.headless("Windows 11"));
        assertTrue(MacOpenFiles.headless(null));
    }

    @Test
    void headlessInstallsNothingAndSaysWhy() {
        // Surefire runs the tests headless on every platform.
        assertEquals("none (AWT is headless)", MacOpenFiles.install(path -> {}));
    }

    @Test
    void ofSeveralFilesTheFirstIsShown() {
        assertEquals(
                new File("a.jpg"),
                MacOpenFiles.first(List.of(new File("a.jpg"), new File("b.jpg")))
                        .orElseThrow());
        assertTrue(MacOpenFiles.first(List.of()).isEmpty());
    }
}
