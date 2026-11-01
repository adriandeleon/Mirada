package com.mirada.ui;

import javafx.application.Application;
import javafx.application.ColorScheme;
import javafx.application.Platform;

import atlantafx.base.theme.CupertinoDark;
import atlantafx.base.theme.CupertinoLight;
import com.mirada.settings.Settings;

/**
 * AtlantaFX's Cupertino pair: the system's light or dark setting, followed as it changes, or one of
 * them always, as the settings say.
 */
public final class Theme {

    private static Settings.ThemeChoice choice = Settings.ThemeChoice.SYSTEM;
    private static boolean following;

    private Theme() {}

    public static void apply(Settings.ThemeChoice value) {
        choice = value;
        if (!following) {
            following = true;
            Platform.getPreferences().colorSchemeProperty().addListener((obs, was, now) -> refresh());
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

    private static void refresh() {
        boolean dark = dark(choice, Platform.getPreferences().getColorScheme());
        atlantafx.base.theme.Theme theme = dark ? new CupertinoDark() : new CupertinoLight();
        Application.setUserAgentStylesheet(theme.getUserAgentStylesheet());
    }
}
