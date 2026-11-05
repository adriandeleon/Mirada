package com.mirada.ui;

import java.util.List;

import javafx.geometry.Rectangle2D;

import org.jspecify.annotations.Nullable;

/**
 * Where to put the window again: where it was, if that is still on a screen. A monitor unplugged
 * since, or a resolution lowered, would otherwise open the window where nobody can reach it.
 */
final class WindowPlacement {

    /** How much of the window must show on some screen, title bar included, to be reachable. */
    static final double MIN_VISIBLE_WIDTH = 120;

    static final double MIN_VISIBLE_HEIGHT = 60;

    private WindowPlacement() {}

    /**
     * {@code saved}, shrunk to the screen it is mostly on when it is larger, or null when no screen
     * shows enough of it: the window then opens where the system puts it.
     *
     * @param screens each screen's usable area (without the menu bar, dock or taskbar)
     */
    static @Nullable Rectangle2D placed(Rectangle2D saved, List<Rectangle2D> screens) {
        Rectangle2D best = null;
        double bestArea = 0;
        for (Rectangle2D screen : screens) {
            double width = Math.min(saved.getMaxX(), screen.getMaxX()) - Math.max(saved.getMinX(), screen.getMinX());
            double height = Math.min(saved.getMaxY(), screen.getMaxY()) - Math.max(saved.getMinY(), screen.getMinY());
            if (width < MIN_VISIBLE_WIDTH || height < MIN_VISIBLE_HEIGHT) continue;
            // The top must be on the screen too: a window is moved by its title bar.
            if (saved.getMinY() < screen.getMinY() || saved.getMinY() > screen.getMaxY() - MIN_VISIBLE_HEIGHT) continue;
            if (width * height > bestArea) {
                bestArea = width * height;
                best = screen;
            }
        }
        if (best == null) return null;
        double width = Math.min(saved.getWidth(), best.getWidth());
        double height = Math.min(saved.getHeight(), best.getHeight());
        double x = Math.clamp(saved.getMinX(), best.getMinX(), best.getMaxX() - width);
        double y = Math.clamp(saved.getMinY(), best.getMinY(), best.getMaxY() - height);
        return new Rectangle2D(x, y, width, height);
    }
}
