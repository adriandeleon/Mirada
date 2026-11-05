package com.mirada.ui;

import java.util.Locale;

import javafx.scene.paint.Color;

/**
 * The theme's accent colours made from the system's accent, as a stylesheet over AtlantaFX's
 * Cupertino pair: the selection, the focused row and the current thumbnail then wear the colour the
 * user chose in System Settings or Windows' Personalization, as every native app does.
 *
 * <p>Cupertino's own values are one colour in ten shades and four roles, and they follow a pattern
 * this repeats: shades 0–4 are the accent mixed with white (75 % down to 15 %), 6–9 it darkened (to
 * 85 % down to 40 %); dark uses shade 4 for text and the accent see-through for the muted and subtle
 * fills, light the accent itself for text and pale mixes for the fills. Given Cupertino's blue it
 * gives Cupertino's values back, near enough ({@code AccentTest}). The text colour is the one rule
 * of its own: a pale accent (yellow, light green) is darkened, or a deep one lightened, until it
 * reads on the window's background at 3:1.
 */
final class Accent {

    private static final double[] LIGHTER = {0.75, 0.6, 0.45, 0.3, 0.15};
    private static final double[] DARKER = {0.85, 0.7, 0.55, 0.4};

    /** The windows' backgrounds the accent's text stands on: Cupertino's, light and dark. */
    static final Color LIGHT_BACKGROUND = Color.WHITE;

    static final Color DARK_BACKGROUND = Color.rgb(28, 28, 30);

    /** WCAG's large-text contrast: enough for an accent-coloured label or link. */
    static final double MIN_CONTRAST = 3;

    private Accent() {}

    /** The ten shades, 0 palest to 9 deepest, the accent itself at 5. */
    static Color[] shades(Color accent) {
        Color[] shades = new Color[10];
        for (int i = 0; i < LIGHTER.length; i++) shades[i] = accent.interpolate(Color.WHITE, LIGHTER[i]);
        shades[5] = accent;
        for (int i = 0; i < DARKER.length; i++) {
            double f = DARKER[i];
            shades[6 + i] = Color.color(accent.getRed() * f, accent.getGreen() * f, accent.getBlue() * f);
        }
        return shades;
    }

    /**
     * The colour for accent text: the theme's choice (the accent in light, shade 4 in dark), or the
     * nearest shade toward legibility that reads at {@link #MIN_CONTRAST}, or the last one tried.
     */
    static Color text(Color[] shades, boolean dark) {
        int[] order = dark ? new int[] {4, 3, 2, 1, 0} : new int[] {5, 6, 7, 8, 9};
        Color background = dark ? DARK_BACKGROUND : LIGHT_BACKGROUND;
        for (int i : order) {
            if (contrast(shades[i], background) >= MIN_CONTRAST) return shades[i];
        }
        return shades[order[order.length - 1]];
    }

    /** The stylesheet that puts {@code accent} in place of Cupertino's blue. */
    static String css(Color accent, boolean dark) {
        Color opaque = Color.color(accent.getRed(), accent.getGreen(), accent.getBlue());
        Color[] shades = shades(opaque);
        StringBuilder css = new StringBuilder(".root {\n");
        for (int i = 0; i < shades.length; i++) line(css, "-color-accent-" + i, hex(shades[i]));
        line(css, "-color-accent-fg", hex(text(shades, dark)));
        line(css, "-color-accent-emphasis", hex(opaque));
        if (dark) {
            line(css, "-color-accent-muted", rgba(opaque, 0.4));
            line(css, "-color-accent-subtle", rgba(opaque, 0.15));
        } else {
            line(css, "-color-accent-muted", hex(shades[1]));
            line(css, "-color-accent-subtle", hex(opaque.interpolate(Color.WHITE, 0.9)));
        }
        return css.append("}\n").toString();
    }

    /** WCAG's contrast ratio between two opaque colours, 1 to 21. */
    static double contrast(Color a, Color b) {
        double la = luminance(a);
        double lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    private static double luminance(Color c) {
        return 0.2126 * linear(c.getRed()) + 0.7152 * linear(c.getGreen()) + 0.0722 * linear(c.getBlue());
    }

    private static double linear(double channel) {
        return channel <= 0.04045 ? channel / 12.92 : Math.pow((channel + 0.055) / 1.055, 2.4);
    }

    private static void line(StringBuilder css, String name, String value) {
        css.append("    ").append(name).append(": ").append(value).append(";\n");
    }

    static String hex(Color c) {
        return String.format(
                Locale.ROOT, "#%02x%02x%02x", channel(c.getRed()), channel(c.getGreen()), channel(c.getBlue()));
    }

    private static String rgba(Color c, double alpha) {
        return String.format(
                Locale.ROOT,
                "rgba(%d, %d, %d, %s)",
                channel(c.getRed()),
                channel(c.getGreen()),
                channel(c.getBlue()),
                alpha);
    }

    private static int channel(double value) {
        return (int) Math.round(value * 255);
    }
}
