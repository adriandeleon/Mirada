package com.mirada.ui;

import javafx.scene.paint.Color;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccentTest {

    /** Cupertino's dark blue gives Cupertino's dark shades back, each channel within one step. */
    @Test
    void cupertinosBlueGivesCupertinosShades() {
        Color[] shades = Accent.shades(Color.rgb(10, 132, 255));
        String[] cupertino = {
            "#c2e0ff", "#9dceff", "#78bbff", "#54a9ff", "#2f96ff", "#0a84ff", "#0970d9", "#075cb3", "#06498c", "#043566"
        };
        for (int i = 0; i < shades.length; i++) {
            Color expected = Color.web(cupertino[i]);
            assertEquals(expected.getRed(), shades[i].getRed(), 1.5 / 255, "shade " + i);
            assertEquals(expected.getGreen(), shades[i].getGreen(), 1.5 / 255, "shade " + i);
            assertEquals(expected.getBlue(), shades[i].getBlue(), 1.5 / 255, "shade " + i);
        }
    }

    @Test
    void theThemesOwnTextColourIsKeptWhenItReads() {
        Color blue = Color.rgb(0, 122, 255);
        Color[] shades = Accent.shades(blue);
        assertEquals(blue, Accent.text(shades, false));
        assertEquals(shades[4], Accent.text(shades, true));
    }

    @Test
    void aPaleAccentIsDarkenedForTextOnLight() {
        Color yellow = Color.rgb(255, 198, 0); // macOS's yellow accent
        Color text = Accent.text(Accent.shades(yellow), false);
        assertTrue(Accent.contrast(text, Accent.LIGHT_BACKGROUND) >= Accent.MIN_CONTRAST, Accent.hex(text));
    }

    @Test
    void aDeepAccentIsLightenedForTextOnDark() {
        Color navy = Color.rgb(0, 40, 120);
        Color text = Accent.text(Accent.shades(navy), true);
        assertTrue(Accent.contrast(text, Accent.DARK_BACKGROUND) >= Accent.MIN_CONTRAST, Accent.hex(text));
    }

    @Test
    void theStylesheetSetsEveryAccentRole() {
        String css = Accent.css(Color.rgb(255, 59, 48), true);
        for (String role :
                new String[] {"-color-accent-0:", "-color-accent-9:", "-fg:", "-emphasis:", "-muted:", "-subtle:"}) {
            assertTrue(css.contains(role), role);
        }
        assertTrue(css.contains("-color-accent-emphasis: #ff3b30;"), css);
        assertTrue(css.startsWith(".root {"), css);
    }
}
