package com.mirada.ui;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.Objects;

import javafx.application.Application;
import javafx.application.ColorScheme;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
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
 * them always, as the settings say. On macOS and Windows the accent is the system's too ({@link
 * Accent}), followed as it changes: a stylesheet of its own, a data URL, kept on every window's
 * scene. Popups copy their owner's stylesheets as they open. Linux keeps Cupertino's blue: what
 * JavaFX reads there is the GTK theme's selection colour, not a choice the user made.
 */
public final class Theme {

    private static final boolean FOLLOWS_ACCENT = followsAccent(System.getProperty("os.name", ""));

    private static Settings.ThemeChoice choice = Settings.ThemeChoice.SYSTEM;
    private static boolean following;
    /** The accent stylesheet on every window now, or null for Cupertino's own. */
    private static @Nullable String accentSheet;

    private Theme() {}

    public static void apply(Settings.ThemeChoice value) {
        choice = value;
        if (!following) {
            following = true;
            Platform.getPreferences().colorSchemeProperty().addListener((obs, was, now) -> refresh());
            if (FOLLOWS_ACCENT) {
                Platform.getPreferences().accentColorProperty().addListener((obs, was, now) -> refresh());
                Window.getWindows().addListener((ListChangeListener<Window>) change -> {
                    while (change.next()) {
                        for (Window window : change.getAddedSubList()) restyle(window, null);
                    }
                });
            }
        }
        refresh();
    }

    static boolean dark(Settings.ThemeChoice choice, ColorScheme system) {
        return switch (choice) {
            case DARK -> true;
            case LIGHT -> false;
            case SYSTEM -> system == ColorScheme.DARK;
        };
    }

    static boolean followsAccent(String osName) {
        String os = osName.toLowerCase(Locale.ROOT);
        return os.startsWith("mac") || os.startsWith("windows");
    }

    private static void refresh() {
        boolean dark = dark(choice, Platform.getPreferences().getColorScheme());
        atlantafx.base.theme.Theme theme = dark ? new CupertinoDark() : new CupertinoLight();
        Application.setUserAgentStylesheet(theme.getUserAgentStylesheet());
        if (FOLLOWS_ACCENT) accent(Platform.getPreferences().getAccentColor(), dark);
    }

    private static void accent(@Nullable Color system, boolean dark) {
        String next = system == null ? null : dataUrl(Accent.css(system, dark));
        String old = accentSheet;
        if (Objects.equals(next, old)) return;
        accentSheet = next;
        for (Window window : Window.getWindows()) restyle(window, old);
    }

    /** Swaps {@code old} for the current accent stylesheet on {@code window}'s scene. */
    private static void restyle(Window window, @Nullable String old) {
        Scene scene = window.getScene();
        if (scene == null || window instanceof PopupWindow) return;
        if (old != null) scene.getStylesheets().remove(old);
        String sheet = accentSheet;
        if (sheet != null && !scene.getStylesheets().contains(sheet))
            scene.getStylesheets().add(sheet);
    }

    private static String dataUrl(String css) {
        return "data:text/css;base64," + Base64.getEncoder().encodeToString(css.getBytes(StandardCharsets.UTF_8));
    }
}
