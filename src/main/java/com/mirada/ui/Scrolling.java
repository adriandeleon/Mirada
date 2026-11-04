package com.mirada.ui;

/**
 * What a scroll does to the image, kept apart from the events so it can be tested.
 *
 * <p>A mouse wheel zooms, as in GNOME's viewer. A trackpad (macOS reports the fingers on it) pans
 * an image that is zoomed in and swipes to the next one when there is nothing to pan, as Preview
 * and Photos do: zooming there is the pinch's job. With Ctrl or Cmd held either one zooms, which is
 * also how Windows delivers a touchpad's pinch.
 */
final class Scrolling {

    enum Action {
        ZOOM,
        PAN,
        SWIPE
    }

    /** How far the fingers travel sideways, in scroll units (points on a Mac), to step once. */
    static final double SWIPE_DISTANCE = 80;

    private Scrolling() {}

    static Action of(boolean trackpad, boolean zoomKey, boolean pannable) {
        if (zoomKey || !trackpad) return Action.ZOOM;
        return pannable ? Action.PAN : Action.SWIPE;
    }

    /**
     * The step a swipe has earned so far: +1 for the next image once the fingers have travelled
     * {@link #SWIPE_DISTANCE} to the left (the picture pulled aside, as a page is turned), -1 for the
     * previous one to the right, 0 while short of it or while the gesture is mostly vertical. The
     * system has already applied the user's scroll direction to the deltas.
     */
    static int swipeStep(double totalX, double totalY) {
        if (Math.abs(totalX) < SWIPE_DISTANCE || Math.abs(totalX) < 2 * Math.abs(totalY)) return 0;
        return totalX < 0 ? 1 : -1;
    }
}
