package com.mirada.edit;

/**
 * The shapes a crop can be held to, as GNOME Image Viewer offers them. A fixed ratio follows the
 * image: on a portrait picture 3:2 is taller than wide.
 */
public enum AspectRatio {
    FREE("Free", 0, 0),
    ORIGINAL("Original", 0, 0),
    SQUARE("Square", 1, 1),
    FIVE_FOUR("5:4", 5, 4),
    FOUR_THREE("4:3", 4, 3),
    THREE_TWO("3:2", 3, 2),
    SIXTEEN_NINE("16:9", 16, 9);

    private final String label;
    private final int longSide;
    private final int shortSide;

    AspectRatio(String label, int longSide, int shortSide) {
        this.label = label;
        this.longSide = longSide;
        this.shortSide = shortSide;
    }

    public String label() {
        return label;
    }

    /** Width over height for an image of this size, or 0 for no constraint. */
    public double ratio(double imageWidth, double imageHeight) {
        return switch (this) {
            case FREE -> 0;
            case ORIGINAL -> imageWidth / imageHeight;
            default -> imageHeight > imageWidth ? (double) shortSide / longSide : (double) longSide / shortSide;
        };
    }

    /** The next one, round to the first: what the editor's A key steps through. */
    public AspectRatio next() {
        AspectRatio[] all = values();
        return all[(ordinal() + 1) % all.length];
    }
}
