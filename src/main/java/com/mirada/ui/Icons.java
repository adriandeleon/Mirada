package com.mirada.ui;

import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.SVGPath;

/**
 * The window's symbolic icons, drawn here as stroked paths on a 24-unit grid: two-pixel strokes,
 * round caps, no fill, in the style of GNOME's symbolic icons. Their colour is CSS's ({@code
 * .icon} in mirada.css: {@code -fx-stroke}), so they follow the theme.
 *
 * <p>Hand-drawn rather than copied from an icon set, so nothing here carries a licence of its own.
 * Path data is never wrapped across source lines: Termina's notes record that a whitespace-aware
 * wrap once joined two numbers and the glyph drew distorted, with no error anywhere.
 */
final class Icons {

    static final String FULL_SCREEN = "M15 3 H21 V9 M9 21 H3 V15 M21 3 L14 10 M3 21 L10 14";
    static final String LEAVE_FULL_SCREEN = "M4 14 H10 V20 M20 10 H14 V4 M14 10 L21 3 M3 21 L10 14";
    static final String ROTATE = "M20 4 V9 H15 M20 9 A8.5 8.5 0 1 0 20.5 14";
    static final String INFO = "M12 3 A9 9 0 1 1 11.99 3 Z M12 11 V16.5 M12 7.5 V7.6";
    static final String MENU = "M4 6.5 H20 M4 12 H20 M4 17.5 H20";
    static final String PREVIOUS = "M15 18 L9 12 L15 6";
    static final String NEXT = "M9 18 L15 12 L9 6";
    static final String CHEVRON_DOWN = "M7 10 L12 15 L17 10";
    static final String ZOOM_IN =
            "M10.5 3.5 A7 7 0 1 1 10.49 3.5 Z M20.5 20.5 L15.5 15.5 M10.5 7.5 V13.5 M7.5 10.5 H13.5";
    static final String ZOOM_FIT = "M10.5 3.5 A7 7 0 1 1 10.49 3.5 Z M20.5 20.5 L15.5 15.5 M7.5 10.5 H13.5";
    static final String FOLDER_OPEN = "M3 18 V6 H9 L11 8 H18 V10.5 M3 18 L6 10.5 H21.5 L18.5 18 Z";

    private Icons() {}

    /** A 16-pixel icon from one of the paths above, centred in a box of its own size. */
    static Node of(String path) {
        SVGPath glyph = new SVGPath();
        glyph.setContent(path);
        glyph.getStyleClass().add("icon");
        // The grid is 24 units; scaled to 16 px it matches GNOME's symbolic icon size.
        glyph.setScaleX(16 / 24.0);
        glyph.setScaleY(16 / 24.0);
        StackPane box = new StackPane(glyph);
        box.setMinSize(16, 16);
        box.setPrefSize(16, 16);
        box.setMaxSize(16, 16);
        return box;
    }
}
