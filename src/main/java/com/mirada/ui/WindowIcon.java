package com.mirada.ui;

import java.io.InputStream;

import javafx.scene.image.Image;
import javafx.stage.Stage;

import org.jspecify.annotations.Nullable;

/** Mirada's icon (branding/mirada-icon.svg at 256 px), for the window, the taskbar and About. */
final class WindowIcon {

    private static @Nullable Image icon;
    private static boolean loaded;

    private WindowIcon() {}

    static @Nullable Image image() {
        if (!loaded) {
            loaded = true;
            try (InputStream in = WindowIcon.class.getResourceAsStream("/com/mirada/icon.png")) {
                if (in != null) icon = new Image(in);
            } catch (java.io.IOException e) {
                icon = null;
            }
        }
        return icon;
    }

    static void applyTo(Stage stage) {
        Image image = image();
        if (image != null) stage.getIcons().setAll(image);
    }
}
