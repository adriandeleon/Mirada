package com.mirada.edit;

import static com.mirada.i18n.Messages.tr;

/**
 * The shapes a crop can be held to, as GNOME Image Viewer offers them. A fixed ratio follows the
 * image: on a portrait picture 3:2 is taller than wide.
 */
public enum AspectRatio {
    FREE("aspect.free", 0, 0),
    ORIGINAL("aspect.original", 0, 0),
    SQUARE("aspect.square", 1, 1),
    FIVE_FOUR("aspect.fiveFour", 5, 4),
    FOUR_THREE("aspect.fourThree", 4, 3),
    THREE_TWO("aspect.threeTwo", 3, 2),
    SIXTEEN_NINE("aspect.sixteenNine", 16, 9);

    /** The catalog key of its name, translated at use. */
    private final String labelKey;

    private final int longSide;
    private final int shortSide;

    AspectRatio(String labelKey, int longSide, int shortSide) {
        this.labelKey = labelKey;
        this.longSide = longSide;
        this.shortSide = shortSide;
    }

    /** Its name in the language in use: "Free", "Square", "3:2". */
    public String label() {
        return tr(labelKey);
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
