package com.mirada.image;

/**
 * The EXIF orientation tag, as the turn and mirror that put the stored pixels upright.
 *
 * <p>Cameras and phones store the sensor's pixels as they were read and record how the device was
 * held; a viewer that ignores the tag shows portrait photos lying on their side. JavaFX ignores it.
 *
 * <p>Read as: mirror left-to-right first (when {@link #mirrored}), then turn clockwise by {@link
 * #degrees} — the order JavaFX applies a node's scale and rotate in. The constants are in the tag's
 * order, 1 to 8.
 */
public enum Orientation {
    NORMAL(0, false),
    MIRROR(0, true),
    ROTATE_180(180, false),
    FLIP_VERTICAL(180, true),
    TRANSPOSE(270, true),
    ROTATE_90(90, false),
    TRANSVERSE(90, true),
    ROTATE_270(270, false);

    private final int degrees;
    private final boolean mirrored;

    Orientation(int degrees, boolean mirrored) {
        this.degrees = degrees;
        this.mirrored = mirrored;
    }

    /** The tag's value, 1 to 8; anything else (absent, corrupt) is upright. */
    public static Orientation fromExif(int value) {
        return value >= 1 && value <= 8 ? values()[value - 1] : NORMAL;
    }

    /** Clockwise, 0, 90, 180 or 270. */
    public int degrees() {
        return degrees;
    }

    public boolean mirrored() {
        return mirrored;
    }
}
