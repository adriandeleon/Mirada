package com.mirada.ui;

import java.util.Locale;
import java.util.function.Predicate;

import javafx.scene.paint.Color;

/**
 * The theme's accent colours made from the system's accent, as a stylesheet over AtlantaFX's
 * Cupertino pair: the selection, the focused row and the current thumbnail then wear the colour the
 * user chose in System Settings or Windows' Personalization, as every native app does.
 *
 * <p>Cupertino's own values are one colour in ten shades and four roles, and they follow a pattern
 * this repeats: shades 0–4 are the accent mixed with white (75 % down to 15 %), 6–9 it darkened (to
 * 85 % down to 40 %); dark uses the accent see-through for the muted and subtle fills, light pale
 * mixes. Given Cupertino's blue it gives back the values {@code mirada.css} puts in place of
 * Cupertino's ({@code AccentTest}).
 *
 * <p>Two roles are moved, the least they need, until they read (Pauta ICON-10, ICON-11, ICON-12):
 * the text colour, darkened on light or lightened on dark until it reaches {@link #TEXT_CONTRAST}
 * on the window's grounds and on the accent's own subtle fill; and the emphasis fill, until the
 * theme's white ink reads on it at {@link #TEXT_CONTRAST} while it still stands out from every
 * ground at {@link #RING_CONTRAST}, as it is the focus ring too. Cupertino's own blue fails both
 * by a little: #007aff is 4.0:1 under white.
 */
final class Accent {

    private static final double[] LIGHTER = {0.75, 0.6, 0.45, 0.3, 0.15};
    private static final double[] DARKER = {0.85, 0.7, 0.55, 0.4};

    /** The windows' grounds the accent stands on: Cupertino's default and subtle, light and dark. */
    static final Color LIGHT_BACKGROUND = Color.WHITE;

    static final Color LIGHT_SUBTLE = Color.rgb(242, 242, 247);
    static final Color DARK_BACKGROUND = Color.rgb(28, 28, 30);
    static final Color DARK_SUBTLE = Color.rgb(44, 44, 46);

    /** WCAG's AA for text: tinted text and the ink on an emphasis fill. */
    static final double TEXT_CONTRAST = 4.5;

    /** WCAG's non-text contrast: the focus ring, which is the emphasis fill, on every ground. */
    static final double RING_CONTRAST = 3;

    /** How much of the accent a dark theme's subtle fill is, over the ground. */
    static final double DARK_SUBTLE_ALPHA = 0.15;

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

    /** The accent's subtle fill, as opaque as it is drawn: a pale mix on light, see-through on dark. */
    static Color subtle(Color accent, boolean dark) {
        return dark
                ? Color.color(accent.getRed(), accent.getGreen(), accent.getBlue(), DARK_SUBTLE_ALPHA)
                : accent.interpolate(Color.WHITE, 0.9);
    }

    /** The grounds accent text is read on: the window's two, and the subtle fill over each. */
    static Color[] textGrounds(Color accent, boolean dark) {
        Color ground = dark ? DARK_BACKGROUND : LIGHT_BACKGROUND;
        Color raised = dark ? DARK_SUBTLE : LIGHT_SUBTLE;
        Color fill = subtle(accent, dark);
        return new Color[] {ground, raised, over(fill, ground), over(fill, raised)};
    }

    /**
     * The colour for accent text: the theme's choice (the accent in light, shade 4 in dark), moved
     * toward black on light or white on dark only as far as it must to read at {@link
     * #TEXT_CONTRAST} on each of {@link #textGrounds}.
     */
    static Color text(Color accent, boolean dark) {
        Color[] grounds = textGrounds(accent, dark);
        Color start = dark ? shades(accent)[4] : accent;
        return toward(start, dark ? Color.WHITE : Color.BLACK, c -> {
            for (Color ground : grounds) if (contrast(c, ground) < TEXT_CONTRAST) return false;
            return true;
        });
    }

    /**
     * The emphasis fill: the accent, darkened until white reads on it at {@link #TEXT_CONTRAST} and
     * it stands out from the grounds at {@link #RING_CONTRAST}; on dark, an accent too deep to stand
     * out is lightened instead, as far as that takes.
     */
    static Color emphasis(Color accent, boolean dark) {
        Color[] grounds =
                dark ? new Color[] {DARK_BACKGROUND, DARK_SUBTLE} : new Color[] {LIGHT_BACKGROUND, LIGHT_SUBTLE};
        Predicate<Color> ring = c -> {
            for (Color ground : grounds) if (contrast(c, ground) < RING_CONTRAST) return false;
            return true;
        };
        Predicate<Color> ink = c -> contrast(Color.WHITE, c) >= TEXT_CONTRAST;
        // Too deep to show on a dark ground: lightened, which white still reads on that far.
        if (dark && !ring.test(accent)) return toward(accent, Color.WHITE, ring);
        return toward(accent, Color.BLACK, ink.and(ring));
    }

    /** The stylesheet that puts {@code accent} in place of Cupertino's blue. */
    static String css(Color accent, boolean dark) {
        Color opaque = Color.color(accent.getRed(), accent.getGreen(), accent.getBlue());
        Color[] shades = shades(opaque);
        StringBuilder css = new StringBuilder(".root {\n");
        for (int i = 0; i < shades.length; i++) line(css, "-color-accent-" + i, hex(shades[i]));
        line(css, "-color-accent-fg", hex(text(opaque, dark)));
        line(css, "-color-accent-emphasis", hex(emphasis(opaque, dark)));
        if (dark) {
            line(css, "-color-accent-muted", rgba(opaque, 0.4));
            line(css, "-color-accent-subtle", rgba(opaque, DARK_SUBTLE_ALPHA));
        } else {
            line(css, "-color-accent-muted", hex(shades[1]));
            line(css, "-color-accent-subtle", hex(subtle(opaque, false)));
        }
        return css.append("}\n").toString();
    }

    /**
     * The first colour from {@code from} toward {@code to}, in steps of 1/200, that {@code good}
     * accepts as written in a stylesheet (eight bits a channel), or {@code to} when none does.
     */
    private static Color toward(Color from, Color to, Predicate<Color> good) {
        for (int step = 0; step <= 200; step++) {
            Color c = Color.web(hex(from.interpolate(to, step / 200.0)));
            if (good.test(c)) return c;
        }
        return to;
    }

    /** {@code top} drawn over the opaque {@code ground}. */
    static Color over(Color top, Color ground) {
        double a = top.getOpacity();
        return Color.color(
                top.getRed() * a + ground.getRed() * (1 - a),
                top.getGreen() * a + ground.getGreen() * (1 - a),
                top.getBlue() * a + ground.getBlue() * (1 - a));
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
