package com.mirada.ui;

import static com.mirada.i18n.Messages.tr;

/**
 * The viewport's arithmetic, kept apart from the nodes so it can be tested: fitting, stepping
 * through zoom levels, zooming about a point, and keeping the image on screen while panning.
 */
final class Zoom {

    /** The keyboard's zoom stops. The wheel zooms continuously between the first and last. */
    static final double[] LEVELS = {0.05, 0.1, 0.125, 0.25, 1 / 3d, 0.5, 2 / 3d, 1, 1.5, 2, 3, 4, 6, 8, 12, 16, 24, 32};

    static final double MIN = LEVELS[0];
    static final double MAX = LEVELS[LEVELS.length - 1];

    /** Above this, pixels are drawn as crisp squares rather than smoothed: zooming in is for inspecting them. */
    static final double CRISP_FROM = 2;

    private Zoom() {}

    /**
     * The scale that shows all of a {@code contentWidth} × {@code contentHeight} image in the view.
     * Never above 1: a small image is shown at its own size, not blown up to fill the window.
     */
    static double fitScale(double contentWidth, double contentHeight, double viewWidth, double viewHeight) {
        if (contentWidth <= 0 || contentHeight <= 0 || viewWidth <= 0 || viewHeight <= 0) return 1;
        return Math.min(1, Math.min(viewWidth / contentWidth, viewHeight / contentHeight));
    }

    /** The next stop above {@code scale}; a scale between stops goes to the one above it. */
    static double zoomIn(double scale) {
        for (double level : LEVELS) {
            if (level > scale * 1.001) return level;
        }
        return MAX;
    }

    /** The next stop below {@code scale}. */
    static double zoomOut(double scale) {
        for (int i = LEVELS.length - 1; i >= 0; i--) {
            if (LEVELS[i] < scale / 1.001) return LEVELS[i];
        }
        return MIN;
    }

    static double clamp(double scale) {
        return Math.clamp(scale, MIN, MAX);
    }

    /**
     * The image's new left (or top) edge after a zoom from {@code oldScale} to {@code newScale}
     * that keeps the image point under {@code pivot} where it is.
     */
    static double offsetAbout(double offset, double pivot, double oldScale, double newScale) {
        return pivot - (pivot - offset) * (newScale / oldScale);
    }

    /**
     * Where the image's left (or top) edge may be: centred when it is narrower than the view, and
     * otherwise never pulled in far enough to show a gap at either side.
     */
    static double clampOffset(double offset, double content, double view) {
        if (content <= view) return (view - content) / 2;
        return Math.clamp(offset, view - content, 0);
    }

    /** {@code Fit · 23%} while fitting, otherwise {@code 150%}. */
    static String label(double scale, boolean fitted) {
        String percent = tr("zoom.percent", Math.max(1, Math.round(scale * 100)));
        return fitted ? tr("zoom.fit", percent) : percent;
    }
}
