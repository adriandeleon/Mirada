package com.mirada.ui;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

import javafx.application.Application;
import javafx.application.ColorScheme;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.collections.MapChangeListener;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.stage.PopupWindow;
import javafx.stage.Window;

import atlantafx.base.theme.CupertinoDark;
import atlantafx.base.theme.CupertinoLight;
import com.mirada.settings.Settings;
import org.jspecify.annotations.Nullable;

/**
 * AtlantaFX's Cupertino pair: the system's light or dark setting, followed as it changes, or one of
 * them always, as the settings say. The accent is the system's too ({@link Accent}), on every OS alike
 * (OS-04), followed as it changes: a stylesheet of its own, a data URL, kept on every window's scene.
 * Popups copy their owner's stylesheets as they open. On Linux JavaFX reads the GTK theme's selection
 * colour; where the platform reports none, Cupertino's blue stays.
 *
 * <p>Every window's scene is also told the scheme chosen ({@code Scene.Preferences.colorScheme}):
 * JavaFX draws an extended window's minimize, maximize and close buttons from it, and a decorated
 * window's frame, falling back to the platform's scheme, which is not the theme: a dark theme
 * chosen in the settings over a light system left the light buttons' dark grey glyphs on a dark
 * header bar.
 *
 * <p>High contrast (ACC-09) is Cupertino Dark with {@code high-contrast.css} last on every window, its
 * roles black, white and saturated: chosen in the settings, or taken while Windows' high-contrast
 * mode is on ({@code Windows.SPI.HighContrast}) and left as it ends.
 */
public final class Theme {

    private static Settings.ThemeChoice choice = Settings.ThemeChoice.SYSTEM;
    private static boolean following;
    /** The accent stylesheet on every window now, or null for Cupertino's own. */
    private static @Nullable String accentSheet;
    /** The scheme every window's scene is told, for the window buttons JavaFX draws. */
    private static ColorScheme scheme = ColorScheme.LIGHT;

    /** Windows' own high-contrast preference (ACC-09): while it is on, the high-contrast theme. */
    static final String WINDOWS_HIGH_CONTRAST = "Windows.SPI.HighContrast";

    /** Whether the OS asks for high contrast now. */
    private static boolean systemHighContrast;
    /** {@code high-contrast.css}'s URL, made the first time it is needed. */
    private static @Nullable String highContrastSheet;
    /** Whether the high-contrast sheet is on every window now. */
    private static boolean highContrast;

    /** The control theme's own stylesheet now, before the UI zoom scales it; null before the first apply. */
    private static @Nullable String userAgent;

    private Theme() {}

    /** The UI zoom changed ({@link UiZoom#apply}): the control theme again, at the new zoom. */
    static void zoomChanged() {
        String sheet = userAgent;
        if (sheet != null) Application.setUserAgentStylesheet(UiZoom.userAgent(sheet));
    }

    public static void apply(Settings.ThemeChoice value) {
        // Called on every change of every setting: restyling every window is for the theme's own.
        if (following && value == choice) return;
        choice = value;
        if (!following) {
            following = true;
            Platform.getPreferences().colorSchemeProperty().addListener((obs, was, now) -> refresh());
            Platform.getPreferences().accentColorProperty().addListener((obs, was, now) -> refresh());
            // ACC-09: Windows' high-contrast mode switches to the high-contrast theme, and back as it ends.
            systemHighContrast = Boolean.TRUE.equals(Platform.getPreferences().get(WINDOWS_HIGH_CONTRAST));
            Platform.getPreferences().addListener((MapChangeListener<String, Object>) change -> {
                if (!WINDOWS_HIGH_CONTRAST.equals(change.getKey())) return;
                boolean on = Boolean.TRUE.equals(change.getValueAdded());
                if (on == systemHighContrast) return;
                systemHighContrast = on;
                refresh();
            });
            // A window joins the list as it is shown, before its first frame is drawn.
            Window.getWindows().addListener((ListChangeListener<Window>) change -> {
                while (change.next()) {
                    for (Window window : change.getAddedSubList()) restyle(window, null);
                }
            });
        }
        refresh();
    }

    /** The theme in effect: the high-contrast one while the OS asks for it, else the one chosen. Pure. */
    static Settings.ThemeChoice effective(Settings.ThemeChoice choice, boolean systemHighContrast) {
        return systemHighContrast ? Settings.ThemeChoice.HIGH_CONTRAST : choice;
    }

    static boolean dark(Settings.ThemeChoice choice, ColorScheme system) {
        return switch (choice) {
            case DARK, HIGH_CONTRAST -> true;
            case LIGHT -> false;
            case SYSTEM -> system == ColorScheme.DARK;
        };
    }

    private static void refresh() {
        Settings.ThemeChoice shown = effective(choice, systemHighContrast);
        boolean dark = dark(shown, Platform.getPreferences().getColorScheme());
        highContrast = shown == Settings.ThemeChoice.HIGH_CONTRAST;
        atlantafx.base.theme.Theme theme = dark ? new CupertinoDark() : new CupertinoLight();
        userAgent = theme.getUserAgentStylesheet();
        Application.setUserAgentStylesheet(UiZoom.userAgent(userAgent));
        scheme = dark ? ColorScheme.DARK : ColorScheme.LIGHT;
        String old = accentSheet;
        // High contrast has its own accent; the system's would undo it.
        accentSheet = highContrast ? null : accent(Platform.getPreferences().getAccentColor(), dark);
        for (Window window : Window.getWindows()) restyle(window, Objects.equals(old, accentSheet) ? null : old);
    }

    private static @Nullable String accent(@Nullable Color system, boolean dark) {
        return system == null ? null : dataUrl(Accent.css(system, dark));
    }

    /**
     * Gives {@code window}'s scene the chosen scheme and swaps {@code old} for the current accent
     * stylesheet. Popups are left alone: they copy their owner's stylesheets and have no window
     * buttons.
     */
    private static void restyle(Window window, @Nullable String old) {
        Scene scene = window.getScene();
        if (scene == null || window instanceof PopupWindow) return;
        scene.getPreferences().setColorScheme(scheme);
        if (old != null) scene.getStylesheets().remove(old);
        String sheet = accentSheet;
        if (sheet != null && !scene.getStylesheets().contains(sheet))
            scene.getStylesheets().add(sheet);
        // Last, over mirada.css's roles; never made while high contrast has not been asked for.
        String high = highContrastSheet;
        if (high != null) scene.getStylesheets().remove(high);
        if (highContrast) scene.getStylesheets().add(highContrastSheet());
    }

    private static String highContrastSheet() {
        String sheet = highContrastSheet;
        if (sheet == null) {
            sheet = Objects.requireNonNull(Theme.class.getResource("/com/mirada/styles/high-contrast.css"))
                    .toExternalForm();
            highContrastSheet = sheet;
        }
        return sheet;
    }

    private static String dataUrl(String css) {
        return "data:text/css;base64," + Base64.getEncoder().encodeToString(css.getBytes(StandardCharsets.UTF_8));
    }
}
