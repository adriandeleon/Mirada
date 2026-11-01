package com.mirada.edit;

/**
 * The crop rectangle being dragged, in the pixels of the picture it is drawn over, and the rules
 * for dragging it: moved whole, or by one of its edges or corners, never outside the picture,
 * never smaller than {@link #MIN} on a side, and held to a ratio when one is chosen.
 */
public record CropBox(double x, double y, double width, double height) {

    /** The smallest side a drag can make, in picture pixels. */
    public static final double MIN = 16;

    /** Where a drag starts: inside the box, or on one of its edges or corners. */
    public enum Handle {
        MOVE,
        N,
        S,
        E,
        W,
        NE,
        NW,
        SE,
        SW;

        boolean left() {
            return this == W || this == NW || this == SW;
        }

        boolean right() {
            return this == E || this == NE || this == SE;
        }

        boolean top() {
            return this == N || this == NE || this == NW;
        }

        boolean bottom() {
            return this == S || this == SE || this == SW;
        }

        boolean corner() {
            return this == NE || this == NW || this == SE || this == SW;
        }
    }

    public static CropBox of(Crop crop, double pictureWidth, double pictureHeight) {
        return new CropBox(
                crop.x() * pictureWidth,
                crop.y() * pictureHeight,
                crop.width() * pictureWidth,
                crop.height() * pictureHeight);
    }

    public Crop toCrop(double pictureWidth, double pictureHeight) {
        return new Crop(x / pictureWidth, y / pictureHeight, width / pictureWidth, height / pictureHeight);
    }

    /** The largest box of {@code ratio} (width over height; 0 for the whole picture), centred. */
    public static CropBox largest(double ratio, double pictureWidth, double pictureHeight) {
        if (ratio <= 0) return new CropBox(0, 0, pictureWidth, pictureHeight);
        double width = pictureWidth;
        double height = width / ratio;
        if (height > pictureHeight) {
            height = pictureHeight;
            width = height * ratio;
        }
        return new CropBox((pictureWidth - width) / 2, (pictureHeight - height) / 2, width, height);
    }

    /** This box held to {@code ratio} about its centre, as large as fits in itself and the picture. */
    public CropBox heldTo(double ratio, double pictureWidth, double pictureHeight) {
        if (ratio <= 0) return this;
        double w = width;
        double h = w / ratio;
        if (h > height) {
            h = height;
            w = h * ratio;
        }
        double cx = x + width / 2;
        double cy = y + height / 2;
        return new CropBox(cx - w / 2, cy - h / 2, w, h).inside(pictureWidth, pictureHeight);
    }

    /** Moved by ({@code dx}, {@code dy}), stopping at the picture's edges. */
    public CropBox moved(double dx, double dy, double pictureWidth, double pictureHeight) {
        return new CropBox(
                Math.clamp(x + dx, 0, Math.max(0, pictureWidth - width)),
                Math.clamp(y + dy, 0, Math.max(0, pictureHeight - height)),
                width,
                height);
    }

    /**
     * Dragged by {@code handle} through ({@code dx}, {@code dy}). The opposite edge or corner stays
     * put; with a {@code ratio} (width over height, 0 for none) a corner sets the width and the
     * height follows, and an edge sets its own side and the other grows about the centre.
     */
    public CropBox dragged(
            Handle handle, double dx, double dy, double ratio, double pictureWidth, double pictureHeight) {
        if (handle == Handle.MOVE) return moved(dx, dy, pictureWidth, pictureHeight);
        double left = x;
        double top = y;
        double right = x + width;
        double bottom = y + height;
        if (handle.left()) left = Math.clamp(left + dx, 0, right - MIN);
        if (handle.right()) right = Math.clamp(right + dx, left + MIN, pictureWidth);
        if (handle.top()) top = Math.clamp(top + dy, 0, bottom - MIN);
        if (handle.bottom()) bottom = Math.clamp(bottom + dy, top + MIN, pictureHeight);
        if (ratio <= 0) return new CropBox(left, top, right - left, bottom - top);

        if (handle.corner()) {
            // The width leads; the height follows from the anchored edge, and if that runs out of
            // picture the height leads instead.
            double w = right - left;
            double h = w / ratio;
            double room = handle.top() ? y + height : pictureHeight - y;
            if (h > room) {
                h = room;
                w = h * ratio;
            }
            double newLeft = handle.left() ? x + width - w : x;
            double newTop = handle.top() ? y + height - h : y;
            return new CropBox(newLeft, newTop, w, h);
        }
        if (handle.left() || handle.right()) {
            double w = right - left;
            double h = Math.min(w / ratio, pictureHeight);
            w = h * ratio;
            double cy = y + height / 2;
            double newLeft = handle.left() ? x + width - w : x;
            return new CropBox(newLeft, cy - h / 2, w, h).inside(pictureWidth, pictureHeight);
        }
        double h = bottom - top;
        double w = Math.min(h * ratio, pictureWidth);
        h = w / ratio;
        double cx = x + width / 2;
        double newTop = handle.top() ? y + height - h : y;
        return new CropBox(cx - w / 2, newTop, w, h).inside(pictureWidth, pictureHeight);
    }

    /** Shifted, never shrunk, so it lies inside the picture (shrunk only if larger than it). */
    CropBox inside(double pictureWidth, double pictureHeight) {
        double w = Math.min(width, pictureWidth);
        double h = Math.min(height, pictureHeight);
        return new CropBox(Math.clamp(x, 0, pictureWidth - w), Math.clamp(y, 0, pictureHeight - h), w, h);
    }
}
