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
    void aTextColourThatAlreadyReadsIsKept() {
        Color deep = Color.rgb(0, 80, 160);
        assertEquals(Accent.hex(deep), Accent.hex(Accent.text(deep, false)));
    }

    /**
     * Whatever accent the system has, the text reads at 4.5:1 on both grounds and on the accent's
     * own subtle fill, white reads at 4.5:1 on the emphasis fill, and that fill, the focus ring,
     * stands out at 3:1 from both grounds (ICON-10, ICON-11, ICON-12).
     */
    @Test
    void everySystemAccentReadsInBothThemes() {
        Color[] accents = {
            Color.rgb(0, 122, 255), // macOS blue
            Color.rgb(10, 132, 255), // its dark form
            Color.rgb(149, 61, 150), // purple
            Color.rgb(247, 79, 158), // pink
            Color.rgb(224, 56, 62), // red
            Color.rgb(247, 130, 27), // orange
            Color.rgb(255, 198, 0), // yellow
            Color.rgb(98, 186, 70), // green
            Color.rgb(140, 140, 140), // graphite
            Color.rgb(0, 120, 212), // Windows' default
            Color.rgb(0, 40, 120), // navy
            Color.rgb(230, 255, 230), // all but white
            Color.rgb(10, 10, 10), // all but black
        };
        for (Color accent : accents) {
            for (boolean dark : new boolean[] {false, true}) {
                String name = Accent.hex(accent) + (dark ? " dark" : " light");
                Color text = Accent.text(accent, dark);
                for (Color ground : Accent.textGrounds(accent, dark)) {
                    assertTrue(
                            Accent.contrast(text, ground) >= Accent.TEXT_CONTRAST, name + " text " + Accent.hex(text));
                }
                Color fill = Accent.emphasis(accent, dark);
                assertTrue(
                        Accent.contrast(Color.WHITE, fill) >= Accent.TEXT_CONTRAST, name + " fill " + Accent.hex(fill));
                for (Color ground : dark
                        ? new Color[] {Accent.DARK_BACKGROUND, Accent.DARK_SUBTLE}
                        : new Color[] {Accent.LIGHT_BACKGROUND, Accent.LIGHT_SUBTLE}) {
                    assertTrue(
                            Accent.contrast(fill, ground) >= Accent.RING_CONTRAST, name + " ring " + Accent.hex(fill));
                }
            }
        }
    }

    @Test
    void theStylesheetSetsEveryAccentRole() {
        String css = Accent.css(Color.rgb(255, 59, 48), true);
        for (String role :
                new String[] {"-color-accent-0:", "-color-accent-9:", "-fg:", "-emphasis:", "-muted:", "-subtle:"}) {
            assertTrue(css.contains(role), role);
        }
        assertTrue(
                css.contains(
                        "-color-accent-emphasis: " + Accent.hex(Accent.emphasis(Color.rgb(255, 59, 48), true)) + ";"),
                css);
        assertTrue(css.startsWith(".root {"), css);
    }
}
