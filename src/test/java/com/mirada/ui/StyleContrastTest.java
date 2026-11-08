package com.mirada.ui;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javafx.scene.paint.Color;

import atlantafx.base.theme.CupertinoLight;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contrast and colour-token rules for Mirada's stylesheets, Editora's StyleContrastTest moved to
 * Cupertino (Pauta PLAT-06, ICON-01, ICON-08, ICON-10, ICON-11, ICON-12, ICON-14). Pure: it reads
 * the CSS text, resolves each theme's tokens (Cupertino's {@code .root}, then {@code mirada.css}'s
 * over it, ladders included) and does the WCAG arithmetic; {@code Color.web} needs no toolkit.
 */
class StyleContrastTest {

    private static final List<String> ROLES = List.of("accent", "warning", "success", "danger");
    private static final List<String> THEMES = List.of("cupertino-light", "cupertino-dark");

    private static String uncommented(String css) {
        return css.replaceAll("(?s)/\\*.*?\\*/", "");
    }

    private static String read(InputStream in) throws Exception {
        try (in) {
            return uncommented(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static String sheet(String name) throws Exception {
        return read(Objects.requireNonNull(
                StyleContrastTest.class.getResourceAsStream("/com/mirada/styles/" + name), name));
    }

    /** The declarations of the first {@code .root} block of {@code css}. */
    private static Map<String, String> rootTokens(String css) {
        int start = css.indexOf(".root {");
        String block = css.substring(start, css.indexOf("}", start));
        Map<String, String> tokens = new LinkedHashMap<>();
        Matcher m = Pattern.compile("(-[a-z][a-z0-9-]*)\\s*:\\s*([^;]+);").matcher(block);
        while (m.find()) tokens.put(m.group(1), m.group(2).strip());
        return tokens;
    }

    /** A theme's tokens: Cupertino's, then mirada.css's over them, as the cascade has them. */
    private static Map<String, String> palette(String theme) throws Exception {
        InputStream in = CupertinoLight.class.getResourceAsStream("/atlantafx/base/theme/" + theme + ".css");
        Map<String, String> tokens = rootTokens(read(Objects.requireNonNull(in, theme)));
        tokens.putAll(rootTokens(sheet("mirada.css")));
        return tokens;
    }

    private static Color color(Map<String, String> palette, String token) {
        String value = palette.get(token);
        if (value == null) throw new AssertionError("no " + token);
        if (palette.containsKey(value)) return color(palette, value);
        Matcher ladder = Pattern.compile("ladder\\((-[a-z0-9-]+),(.+)\\)").matcher(value);
        if (ladder.matches()) return ladder(color(palette, ladder.group(1)), ladder.group(2));
        return Color.web(value);
    }

    /** JavaFX's ladder(): the stops, interpolated at the reference's brightness (.3r + .59g + .11b). */
    static Color ladder(Color reference, String stops) {
        List<Double> offsets = new ArrayList<>();
        List<Color> colors = new ArrayList<>();
        Matcher stop = Pattern.compile("(#[0-9a-fA-F]{6})\\s+([0-9.]+)%").matcher(stops);
        while (stop.find()) {
            colors.add(Color.web(stop.group(1)));
            offsets.add(Double.parseDouble(stop.group(2)) / 100);
        }
        double b = 0.3 * reference.getRed() + 0.59 * reference.getGreen() + 0.11 * reference.getBlue();
        if (b <= offsets.getFirst()) return colors.getFirst();
        for (int i = 1; i < offsets.size(); i++) {
            if (b <= offsets.get(i)) {
                double t = (b - offsets.get(i - 1)) / (offsets.get(i) - offsets.get(i - 1));
                return colors.get(i - 1).interpolate(colors.get(i), t);
            }
        }
        return colors.getLast();
    }

    /** The colour {@code token} draws over the opaque {@code ground}. */
    private static Color on(Map<String, String> palette, String token, Color ground) {
        return Accent.over(color(palette, token), ground);
    }

    /** Declarations of every rule whose selector list contains {@code selector} exactly. */
    private static List<String> rules(String css, String selector) {
        List<String> bodies = new ArrayList<>();
        Matcher m = Pattern.compile("([^{}]+)\\{([^{}]*)\\}").matcher(css);
        while (m.find()) {
            for (String s : m.group(1).split(",")) {
                if (s.strip().replaceAll("\\s+", " ").equals(selector)) bodies.add(m.group(2));
            }
        }
        return bodies;
    }

    private static final Pattern FIXED_COLOUR = Pattern.compile(
            "#[0-9a-fA-F]{3,8}\\b|\\b(rgba?|hsba?)\\(|:\\s*(white|black|red|green|blue|gray|grey|yellow|orange)\\b"
                    + "|,\\s*(white|black)\\b");

    @Test
    void noRuleAfterTheTokenBlockHoldsAFixedColour() throws Exception {
        String css = sheet("mirada.css");
        assertTrue(css.strip().startsWith(".root {"), "the token block comes first");
        int end = css.indexOf("}", css.indexOf(".root {"));
        String rules = css.substring(end + 1);
        assertFalse(rules.contains(".root {"), "one .root block: every colour is named in it");
        List<String> fixed = new ArrayList<>();
        for (String line : rules.split("\n")) if (FIXED_COLOUR.matcher(line).find()) fixed.add(line.strip());
        assertTrue(fixed.isEmpty(), "fixed colours outside .root:\n" + String.join("\n", fixed));
        assertFalse(FIXED_COLOUR.matcher(sheet("ui-font.css")).find(), "ui-font.css names no colour");
    }

    @Test
    void theUiFontIsTheSceneSheetsNotTheAppSheets() throws Exception {
        // ICON-01: Inter comes from ui-font.css on every scene, never from a .root rule in mirada.css.
        assertFalse(String.join(";", rules(sheet("mirada.css"), ".root")).contains("-fx-font-family"));
        String font = String.join(";", rules(sheet("ui-font.css"), ".root"));
        assertTrue(font.contains("-fx-font-family: \"Inter\""), font);
        // MENU-16: menus pinned in absolute pixels, keys in 11 px.
        assertTrue(rules(sheet("ui-font.css"), ".context-menu .menu-item .label")
                .getFirst()
                .contains("-fx-font-size: 14px"));
        assertTrue(rules(sheet("ui-font.css"), ".context-menu .menu-item .accelerator-text")
                .getFirst()
                .contains("-fx-font-size: 11px"));
    }

    @Test
    void tintedTextReachesAaOnItsOwnFillAndOnBothGrounds() throws Exception {
        List<String> problems = new ArrayList<>();
        for (String theme : THEMES) {
            Map<String, String> p = palette(theme);
            Color ground = color(p, "-color-bg-default");
            Color raised = color(p, "-color-bg-subtle");
            for (String role : ROLES) {
                Color fg = color(p, "-color-" + role + "-fg");
                Map<String, Color> grounds = new LinkedHashMap<>();
                grounds.put("bg-default", ground);
                grounds.put("bg-subtle", raised);
                grounds.put(role + "-subtle on bg-default", on(p, "-color-" + role + "-subtle", ground));
                grounds.put(role + "-subtle on bg-subtle", on(p, "-color-" + role + "-subtle", raised));
                grounds.forEach((name, g) -> {
                    double ratio = Accent.contrast(fg, g);
                    if (ratio < 4.5) problems.add(String.format("%s %s-fg on %s: %.2f", theme, role, name, ratio));
                });
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void inkOnAnEmphasisFillIsTheThemesOnEmphasisColour() throws Exception {
        List<String> problems = new ArrayList<>();
        for (String theme : THEMES) {
            Map<String, String> p = palette(theme);
            for (String role : ROLES) {
                double ratio =
                        Accent.contrast(color(p, "-color-fg-emphasis"), color(p, "-color-" + role + "-emphasis"));
                if (ratio < 4.5) problems.add(String.format("%s fg-emphasis on %s-emphasis: %.2f", theme, role, ratio));
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
        // The rules that paint on an emphasis fill (a highlighted menu row, the unread count) use it.
        String css = sheet("mirada.css");
        Map<String, String> painted = Map.of(
                ".menu-item:focused .icon", "-fx-stroke: -color-fg-emphasis",
                ".menu-item:focused .accelerator-text", "-fx-text-fill: -color-fg-emphasis",
                ".status-bar .status-unread", "-fx-text-fill: -color-fg-emphasis");
        painted.forEach((selector, declaration) -> {
            String body = String.join(";", rules(css, selector));
            assertTrue(body.contains(declaration), selector + ": " + body);
        });
    }

    @Test
    void theFocusRingIsAtLeastThreeToOneOnEverySurface() throws Exception {
        String css = sheet("mirada.css");
        assertEquals("-color-accent-emphasis", rootTokens(css).get("-focus-ring"));
        for (String theme : THEMES) {
            Map<String, String> p = palette(theme);
            for (String surface :
                    List.of("-color-bg-default", "-color-bg-subtle", "-color-bg-inset", "-color-bg-overlay")) {
                double ratio = Accent.contrast(color(p, "-focus-ring"), color(p, surface));
                assertTrue(ratio >= 3, theme + ": ring on " + surface + " is " + ratio);
            }
        }
        // Drawn by one :focus-visible block, the sheet's last rules.
        Matcher m = Pattern.compile("([^{}]+)\\{([^{}]*)\\}").matcher(css);
        List<String> selectors = new ArrayList<>();
        while (m.find()) {
            boolean usesRing = !m.group(1).strip().equals(".root") && m.group(2).contains("-focus-ring");
            if (usesRing) assertTrue(m.group(1).contains(":focus-visible"), m.group(1));
            selectors.add(m.group(1).strip());
        }
        int first = -1;
        for (int i = 0; i < selectors.size(); i++) {
            if (selectors.get(i).contains(":focus-visible") && first < 0) first = i;
        }
        assertTrue(first > 0, "a :focus-visible block");
        for (int i = first; i < selectors.size(); i++) {
            for (String s : selectors.get(i).split(","))
                assertTrue(s.contains(":focus-visible"), "after the ring: " + s);
        }
    }

    @Test
    void theKeyboardPeriwinkleReadsInBothThemes() throws Exception {
        for (String theme : THEMES) {
            Map<String, String> p = palette(theme);
            for (String surface : List.of("-color-bg-default", "-color-bg-subtle", "-color-bg-overlay")) {
                double ratio = Accent.contrast(color(p, "-state-key"), color(p, surface));
                assertTrue(ratio >= 4.5, theme + ": -state-key on " + surface + " is " + ratio);
            }
        }
    }

    /** What mirada.css sets for the accent is what Accent makes of Cupertino's own blue. */
    @Test
    void theStylesheetsAccentIsAccentsOwnForCupertinosBlue() throws Exception {
        for (String theme : THEMES) {
            Map<String, String> cupertino = rootTokens(read(Objects.requireNonNull(
                    CupertinoLight.class.getResourceAsStream("/atlantafx/base/theme/" + theme + ".css"))));
            boolean dark = theme.endsWith("dark");
            Color blue = Color.web(cupertino.get("-color-accent-emphasis"));
            Map<String, String> p = palette(theme);
            assertEquals(Accent.hex(Accent.text(blue, dark)), Accent.hex(color(p, "-color-accent-fg")), theme);
            assertEquals(
                    Accent.hex(Accent.emphasis(blue, dark)), Accent.hex(color(p, "-color-accent-emphasis")), theme);
        }
    }
}
