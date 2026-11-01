package com.mirada.edit;

import com.mirada.image.Orientation;

/**
 * What the editor has done to an image, applied in this order to the upright picture: turned
 * clockwise by {@code turns} quarters, mirrored left to right ({@code flipH}), flipped top to bottom
 * ({@code flipV}), then cropped ({@code crop}, as fractions of the turned and flipped image).
 */
public record Edit(int turns, boolean flipH, boolean flipV, Crop crop) {

    public static final Edit NONE = new Edit(0, false, false, Crop.ALL);

    public Edit {
        turns = Math.floorMod(turns, 4);
    }

    public boolean isNone() {
        return turns == 0 && !flipH && !flipV && crop.isAll();
    }

    /** Turned by another {@code quarterTurns}; the crop starts again from the whole image. */
    public Edit turned(int quarterTurns) {
        return new Edit(turns + quarterTurns, flipH, flipV, Crop.ALL);
    }

    /** Mirrored left to right, the crop with it. */
    public Edit flippedHorizontally() {
        return new Edit(turns, !flipH, flipV, crop.mirrored());
    }

    /** Flipped top to bottom, the crop with it. */
    public Edit flippedVertically() {
        return new Edit(turns, flipH, !flipV, crop.flippedVertically());
    }

    public Edit withCrop(Crop newCrop) {
        return new Edit(turns, flipH, flipV, newCrop);
    }

    /** {@code upright} turned and flipped, before the crop: what the crop rectangle is drawn on. */
    public Raster arranged(Raster upright) {
        Raster out = upright.turned(turns);
        if (flipH) out = out.mirrored();
        if (flipV) out = out.flippedVertically();
        return out;
    }

    /** The whole edit applied to {@code upright}. */
    public Raster apply(Raster upright) {
        Raster arranged = arranged(upright);
        int[] box = crop.pixels(arranged.width(), arranged.height());
        return arranged.cropped(box[0], box[1], box[2], box[3]);
    }

    /**
     * The stored pixels made upright by the file's own orientation: mirrored first, then turned
     * clockwise, as {@link Orientation} reads.
     */
    public static Raster upright(Raster stored, Orientation orientation) {
        Raster out = orientation.mirrored() ? stored.mirrored() : stored;
        return out.turned(orientation.degrees() / 90);
    }
}
