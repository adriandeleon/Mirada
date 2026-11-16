package com.mirada.ui;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.mirada.settings.Settings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The UI zoom's rules (Pauta WIN-12, MENU-16): its steps and range, the stylesheet it scales, and the
 * sheets written so that it can scale them; with the one tooltip delay (TBAR-14) and the one way
 * motion starts (ACC-04), which the same sweep over the sources holds. Pure.
 */
class UiZoomTest {

    private static final Path UI = Path.of("src/main/java/com/mirada/ui");

    @Test
    void stepsStayInTheRange() {
        assertEquals(110, UiZoom.in(100));
        assertEquals(90, UiZoom.out(100));
        assertEquals(Settings.MAX_UI_ZOOM, UiZoom.in(Settings.MAX_UI_ZOOM));
        assertEquals(Settings.MIN_UI_ZOOM, UiZoom.out(Settings.MIN_UI_ZOOM));
        assertEquals(Settings.MAX_UI_ZOOM, Settings.clampUiZoom(900));
        assertEquals(Settings.MIN_UI_ZOOM, Settings.clampUiZoom(-5));
        List<Integer> steps = SettingsCommands.uiZoomSteps();
        assertEquals(Settings.MIN_UI_ZOOM, steps.getFirst());
        assertEquals(Settings.MAX_UI_ZOOM, steps.getLast());
        assertTrue(steps.contains(Settings.DEFAULT_UI_ZOOM));
    }

    @Test
    void eachCommandSaysWhyItCannotRunAtTheEndOfTheRange() {
        assertNull(SettingsCommands.uiZoomUnavailable(Command.UI_ZOOM_IN, 100));
        assertNull(SettingsCommands.uiZoomUnavailable(Command.UI_ZOOM_OUT, 100));
        assertTrue(SettingsCommands.uiZoomUnavailable(Command.UI_ZOOM_IN, Settings.MAX_UI_ZOOM) != null);
        assertTrue(SettingsCommands.uiZoomUnavailable(Command.UI_ZOOM_OUT, Settings.MIN_UI_ZOOM) != null);
        assertTrue(SettingsCommands.uiZoomUnavailable(Command.UI_ZOOM_RESET, Settings.DEFAULT_UI_ZOOM) != null);
        assertNull(SettingsCommands.uiZoomUnavailable(Command.UI_ZOOM_RESET, 120));
        assertNull(SettingsCommands.uiZoomUnavailable(Command.ZOOM_IN, Settings.MAX_UI_ZOOM));
    }

    @Test
    void everyPixelLengthIsScaledAndNothingElse() {
        String css = ".root { -fx-font-size: 14px; } .a { -fx-padding: 4px 8.5px; -fx-border-width: 1; "
                + "-fx-font-size: 1.2em; -fx-background-color: #ababab; -fx-show-delay: 400ms; } "
                + ".b-2px { -fx-min-width: 10px; }";
        String scaled = UiZoom.scale(css, 150);
        assertTrue(scaled.contains("-fx-font-size: 21px;"), scaled);
        assertTrue(scaled.contains("-fx-padding: 6px 12.75px;"), scaled);
        assertTrue(scaled.contains("-fx-border-width: 1;"), "a length with no unit is left alone");
        assertTrue(scaled.contains("1.2em"), "an em follows the root size already");
        assertTrue(scaled.contains("#ababab"));
        assertTrue(scaled.contains("400ms"), "a time is no length");
        assertTrue(scaled.contains(".b-2px"), "a selector is never scaled");
        assertTrue(scaled.contains("-fx-min-width: 15px;"), scaled);
        assertEquals(css, UiZoom.scale(css, Settings.DEFAULT_UI_ZOOM));
    }

    @Test
    void theSheetTravelsAsADataUrl() {
        String url = UiZoom.dataUrl(".a { -fx-font-size: 9px; }");
        assertTrue(url.startsWith("data:text/css;base64,"));
        assertEquals(
                ".a { -fx-font-size: 9px; }",
                new String(Base64.getDecoder().decode(url.substring(url.indexOf(',') + 1)), StandardCharsets.UTF_8));
    }

    @Test
    void theZoomIsASettingKeptInTheFileAndClampedOnRead() throws Exception {
        Path dir = Files.createTempDirectory("ui-zoom");
        try {
            Path file = dir.resolve("settings.properties");
            Settings settings = new Settings(file);
            assertEquals(Settings.DEFAULT_UI_ZOOM, settings.uiZoom());
            settings.setUiZoom(130);
            settings.setUiZoom(1000);
            assertEquals(Settings.MAX_UI_ZOOM, settings.uiZoom());
            settings.setUiZoom(130);
            Settings again = new Settings(file);
            again.load();
            assertEquals(130, again.uiZoom());
            Files.writeString(file, "ui.zoom=5\n");
            again.load();
            assertEquals(Settings.MIN_UI_ZOOM, again.uiZoom());
            Files.writeString(file, "ui.zoom=big\n");
            again.load();
            assertEquals(Settings.DEFAULT_UI_ZOOM, again.uiZoom());
        } finally {
            try (Stream<Path> files = Files.walk(dir)) {
                files.sorted((a, b) -> b.compareTo(a)).forEach(p -> p.toFile().delete());
            }
        }
    }

    /**
     * WIN-12: every size in the app's stylesheets is in pixels, which the zoom multiplies, or in em,
     * which follows the root size: a bare number would stay its size at any zoom. A border's width
     * and a shadow's radius are left bare on purpose.
     */
    @Test
    void theStylesheetsWriteTheirSizesInPixels() throws Exception {
        Pattern length = Pattern.compile(
                "(-fx-(padding|spacing|(min|pref|max)-(width|height)|background-insets|background-radius"
                        + "|border-radius|graphic-text-gap|hgap|vgap|translate-x|translate-y|font-size))\\s*:\\s*([^;]+);");
        Pattern bare = Pattern.compile("(?<![\\w.#-])([1-9]\\d*(\\.\\d+)?|0?\\.\\d*[1-9]\\d*)(?![\\w.%])");
        List<String> problems = new ArrayList<>();
        for (String name : UiZoom.SHEETS) {
            String css = sheet(name).replaceAll("(?s)/\\*.*?\\*/", "");
            Matcher m = length.matcher(css);
            while (m.find()) if (bare.matcher(m.group(5)).find()) problems.add(name + ": " + m.group());
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    /** TBAR-14: every tooltip comes from Icons.tip, which gives it the one delay; the stylesheet says the same. */
    @Test
    void everyTooltipShowsAfterTheSameDelay() throws Exception {
        assertEquals(400, Icons.TOOLTIP_DELAY.toMillis());
        List<String> problems = new ArrayList<>();
        for (Path file : sources()) {
            String code = Files.readString(file);
            if (!file.endsWith("Icons.java") && code.contains("new Tooltip(")) problems.add(file + ": new Tooltip");
            if (!file.endsWith("Icons.java") && code.contains("setShowDelay(")) problems.add(file + ": setShowDelay");
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
        String css = sheet("mirada.css").replaceAll("\\s+", " ");
        assertTrue(css.contains(".tooltip { -fx-show-delay: 400ms; }"), "mirada.css's .tooltip rule");
    }

    /** ACC-04: every animation that moves or fades part of the interface starts through Motion. */
    @Test
    void motionStartsInOnePlace() throws Exception {
        Pattern animation = Pattern.compile(
                "new (Fade|Translate|Scale|Rotate|Fill|Stroke|Path|Parallel|Sequential)Transition\\b|new Timeline\\b|new AnimationTimer\\b");
        List<String> problems = new ArrayList<>();
        for (Path file : sources()) {
            String code = Files.readString(file);
            if (animation.matcher(code).find() && !code.contains("Motion.play(")) problems.add(file.toString());
        }
        assertTrue(problems.isEmpty(), "animations not started by Motion.play:\n" + String.join("\n", problems));
        assertFalse(Files.readString(UI.resolve("OnScreenControls.java")).contains("transition.play()"));
    }

    private static List<Path> sources() throws Exception {
        try (Stream<Path> files = Files.list(UI)) {
            return files.filter(f -> f.toString().endsWith(".java")).toList();
        }
    }

    private static String sheet(String name) throws Exception {
        try (InputStream in =
                Objects.requireNonNull(UiZoomTest.class.getResourceAsStream("/com/mirada/styles/" + name), name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
