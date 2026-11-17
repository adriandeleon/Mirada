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
import com.mirada.settings.Settings;
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
    private static final List<String> CUPERTINO = List.of("cupertino-light", "cupertino-dark");
    /** Every control theme: Cupertino's pair, then Editora's, whose roles come back over mirada.css. */
    private static final List<String> THEMES =
            List.of("cupertino-light", "cupertino-dark", "editora-light", "editora-dark");

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

    /**
     * A theme's tokens as the cascade has them: the control theme's (Cupertino's from AtlantaFX's jar,
     * Editora's from Mirada's copy), then mirada.css's over them, then Editora's roles over those.
     */
    private static Map<String, String> palette(String theme) throws Exception {
        boolean editora = theme.startsWith("editora-");
        Map<String, String> tokens = editora
                ? rootTokens(sheet(theme + ".css"))
                : rootTokens(read(Objects.requireNonNull(
                        CupertinoLight.class.getResourceAsStream("/atlantafx/base/theme/" + theme + ".css"), theme)));
        tokens.putAll(rootTokens(sheet("mirada.css")));
        if (editora) tokens.putAll(rootTokens(sheet("editora-roles.css")));
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

    /**
     * The only selectors whose text may be the subtle ink, about 3:1 (ICON-09): disabled text, and a
     * page or a bookmark the search or the disk has ruled out. Every other rule of the app's sheets
     * that sets a text fill to it fails.
     */
    private static final java.util.Set<String> SUBTLE_TEXT = java.util.Set.of(
            ".menu-item:disabled .accelerator-text", // a row that cannot run now: disabled
            ".menu-item.unavailable .menu-item-title",
            ".menu-item.unavailable .menu-item-chord",
            ".settings-sidebar > .virtual-flow > .clipped-container > .sheet > .list-cell.no-match", // ruled out
            ".bookmarks-cell:missing .bookmarks-name", // its file is gone: disabled
            ".bookmarks-cell:missing .bookmarks-note");

    /** Every rule of {@code css}: its selector list and its declarations, comments gone. */
    private static List<String[]> allRules(String css) {
        List<String[]> all = new ArrayList<>();
        Matcher m = Pattern.compile("([^{}]+)\\{([^{}]*)\\}").matcher(css);
        while (m.find()) all.add(new String[] {m.group(1).strip(), m.group(2)});
        return all;
    }

    /** ICON-09: readable text is muted or stronger, and muted is 4.5:1 on every ground of both themes. */
    @Test
    void textThatMustBeReadUsesTheMutedRoleNotSubtle() throws Exception {
        Pattern subtle = Pattern.compile("-fx-(text-)?fill\\s*:\\s*-color-fg-subtle\\b");
        List<String> problems = new ArrayList<>();
        for (String name : List.of("mirada.css", "ui-font.css")) {
            for (String[] rule : allRules(sheet(name))) {
                if (!subtle.matcher(rule[1]).find()) continue;
                for (String selector : rule[0].split(",")) {
                    String s = selector.strip().replaceAll("\\s+", " ");
                    if (!SUBTLE_TEXT.contains(s))
                        problems.add(name + ": " + s + " sets readable text in the subtle ink");
                }
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
        for (String theme : THEMES) {
            Map<String, String> p = palette(theme);
            for (String ground : List.of("-color-bg-default", "-color-bg-subtle", "-color-bg-overlay")) {
                double ratio = Accent.contrast(color(p, "-color-fg-muted"), color(p, ground));
                assertTrue(ratio >= 4.5, theme + ": muted on " + ground + " is " + ratio);
            }
        }
    }

    /** ICON-07: each state's token is named once, in the .root block at the top, and chains to a role. */
    @Test
    void theStateTokensAreDeclaredOnceAtTheTopAndChainToARole() throws Exception {
        String css = sheet("mirada.css");
        List<String[]> all = allRules(css);
        assertEquals(".root", all.getFirst()[0], "the token block is the first rule");
        Matcher declared = Pattern.compile("(-state-[a-z-]+)\\s*:\\s*([^;]+);").matcher(all.getFirst()[1]);
        List<String> names = new ArrayList<>();
        while (declared.find()) {
            assertFalse(names.contains(declared.group(1)), declared.group(1) + " is declared twice");
            names.add(declared.group(1));
            assertTrue(
                    declared.group(2).strip().matches("-color-[a-z0-9-]+|ladder\\(-color-bg-default,.*"),
                    declared.group(1) + " chains to " + declared.group(2));
        }
        assertTrue(
                names.containsAll(List.of("-state-red", "-state-amber", "-state-green", "-state-accent")),
                names::toString);
        for (String[] rule : all.subList(1, all.size())) {
            assertFalse(
                    Pattern.compile("-state-[a-z-]+\\s*:").matcher(rule[1]).find(),
                    rule[0] + " declares a state token outside the .root block");
        }
    }

    /** ICON-07: a rule that paints an error, a warning or a success uses the state's token, never its role. */
    @Test
    void severityColoursComeFromTheStateTokens() throws Exception {
        String css = sheet("mirada.css");
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put(".status-bar .status-echo.status-echo-error", "-state-red");
        expected.put(".status-bar .status-echo.status-echo-warn", "-state-amber");
        expected.put(".status-bar .status-unread", "-state-red-emphasis");
        expected.put(".message-log .list-cell .label.status-echo-error", "-state-red");
        expected.put(".message-log .list-cell .label.status-echo-warn", "-state-amber");
        expected.put(".menu-item.danger .menu-item-title", "-state-red");
        expected.put(".toast:error", "-state-red-osd");
        expected.put(".toast:warning", "-state-amber-osd");
        expected.forEach((selector, token) -> {
            String body = String.join(";", rules(css, selector));
            assertTrue(body.contains(token + ";"), selector + " should use " + token + ": " + body);
        });
        List<String[]> all = allRules(css);
        for (String[] rule : all.subList(1, all.size())) {
            assertFalse(
                    Pattern.compile("-color-(danger|warning|success)-")
                            .matcher(rule[1])
                            .find(),
                    rule[0] + " paints a state with a role, not its -state-* token: " + rule[1]);
        }
    }

    /**
     * ICON-08: a token for the windows' own ground or ink is a role, never a fixed colour; a fixed
     * colour stands only for what is drawn over the photo, a scrim or a shadow.
     */
    @Test
    void theWindowsOwnGroundAndInkAreRoles() throws Exception {
        Map<String, String> tokens = rootTokens(sheet("mirada.css"));
        assertEquals("-color-fg-muted", tokens.get("-window-button-dark"));
        assertEquals("-color-dark", tokens.get("-crop-ground"));
        List<String> fixed = new ArrayList<>();
        tokens.forEach((name, value) -> {
            boolean overThePhoto = name.startsWith("-osd-")
                    || name.startsWith("-toast-")
                    || name.startsWith("-crop-")
                    || name.equals("-scrim")
                    || name.startsWith("-shadow-");
            if (FIXED_COLOUR.matcher(": " + value).find() && !value.startsWith("ladder(") && !overThePhoto)
                fixed.add(name + ": " + value);
        });
        assertTrue(fixed.isEmpty(), "fixed colours for the window's own ground or ink:\n" + String.join("\n", fixed));
    }

    /** ACC-09: high contrast is Cupertino Dark, mirada.css and high-contrast.css over both, in that order. */
    private static Map<String, String> highContrast() throws Exception {
        Map<String, String> tokens = palette("cupertino-dark");
        tokens.putAll(rootTokens(sheet("high-contrast.css")));
        return tokens;
    }

    /** ACC-09: in high contrast every ink reads at 7:1 on every ground, and every state's text too. */
    @Test
    void theHighContrastThemeReadsAtSevenToOne() throws Exception {
        Map<String, String> p = highContrast();
        List<String> problems = new ArrayList<>();
        List<String> inks = new ArrayList<>(List.of("-color-fg-default", "-color-fg-muted"));
        for (String role : ROLES) inks.add("-color-" + role + "-fg");
        for (String ink : inks) {
            for (String ground : List.of("-color-bg-default", "-color-bg-subtle", "-color-bg-overlay")) {
                double ratio = Accent.contrast(color(p, ink), color(p, ground));
                if (ratio < 7) problems.add(String.format("%s on %s: %.2f", ink, ground, ratio));
            }
        }
        for (String role : ROLES) {
            double ratio = Accent.contrast(color(p, "-color-fg-emphasis"), color(p, "-color-" + role + "-emphasis"));
            if (ratio < 7) problems.add(String.format("fg-emphasis on %s-emphasis: %.2f", role, ratio));
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    /** ACC-09: the OS's high-contrast mode wins while it is on, and the theme chosen comes back after. */
    @Test
    void theOsHighContrastModeWinsWhileItIsOn() {
        for (Settings.ThemeChoice choice : Settings.ThemeChoice.values()) {
            assertEquals(Settings.ThemeChoice.HIGH_CONTRAST, Theme.effective(choice, true));
            assertEquals(choice, Theme.effective(choice, false));
        }
        assertTrue(Theme.dark(Settings.ThemeChoice.HIGH_CONTRAST, javafx.application.ColorScheme.LIGHT));
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
        for (String theme : CUPERTINO) {
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

    /** Editora's pair keep their own roles: editora-roles.css gives back each sheet's own values. */
    @Test
    void editorasThemesKeepTheirOwnRoles() throws Exception {
        for (String theme : List.of("editora-light", "editora-dark")) {
            Map<String, String> own = rootTokens(sheet(theme + ".css"));
            Map<String, String> p = palette(theme);
            for (String role : ROLES) {
                for (String part : List.of("-fg", "-emphasis")) {
                    String token = "-color-" + role + part;
                    assertEquals(
                            Accent.hex(Color.web(own.get(token))), Accent.hex(color(p, token)), theme + " " + token);
                }
            }
        }
    }

    /** Which control theme, overlay and darkness each choice gets; the system's accent only without an overlay. */
    @Test
    void eachThemeChoiceHasItsSheets() {
        assertEquals(Theme.EDITORA_LIGHT_SHEET, Theme.controlSheet(Settings.ThemeChoice.EDITORA_LIGHT, false));
        assertEquals(Theme.EDITORA_DARK_SHEET, Theme.controlSheet(Settings.ThemeChoice.EDITORA_DARK, true));
        assertTrue(Theme.controlSheet(Settings.ThemeChoice.DARK, true).endsWith("cupertino-dark.css"));
        assertTrue(Theme.controlSheet(Settings.ThemeChoice.HIGH_CONTRAST, true).endsWith("cupertino-dark.css"));
        assertTrue(Theme.controlSheet(Settings.ThemeChoice.SYSTEM, false).endsWith("cupertino-light.css"));
        assertEquals("editora-roles.css", Theme.overlay(Settings.ThemeChoice.EDITORA_LIGHT));
        assertEquals("editora-roles.css", Theme.overlay(Settings.ThemeChoice.EDITORA_DARK));
        assertEquals("high-contrast.css", Theme.overlay(Settings.ThemeChoice.HIGH_CONTRAST));
        assertEquals(null, Theme.overlay(Settings.ThemeChoice.LIGHT));
        assertTrue(Theme.dark(Settings.ThemeChoice.EDITORA_DARK, javafx.application.ColorScheme.LIGHT));
        assertFalse(Theme.dark(Settings.ThemeChoice.EDITORA_LIGHT, javafx.application.ColorScheme.DARK));
    }
}
