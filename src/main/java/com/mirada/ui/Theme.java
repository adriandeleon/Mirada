package com.mirada.ui;

import javafx.application.Application;
import javafx.application.ColorScheme;
import javafx.application.Platform;

import atlantafx.base.theme.CupertinoDark;
import atlantafx.base.theme.CupertinoLight;

/** AtlantaFX's Cupertino pair, following the system's light or dark setting as it changes. */
public final class Theme {

    private Theme() {}

    public static void followSystem() {
        apply(Platform.getPreferences().getColorScheme());
        Platform.getPreferences().colorSchemeProperty().addListener((obs, was, now) -> apply(now));
    }

    private static void apply(ColorScheme scheme) {
        atlantafx.base.theme.Theme theme = scheme == ColorScheme.DARK ? new CupertinoDark() : new CupertinoLight();
        Application.setUserAgentStylesheet(theme.getUserAgentStylesheet());
    }
}
