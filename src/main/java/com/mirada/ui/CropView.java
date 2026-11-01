package com.mirada.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javafx.scene.Cursor;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;

import com.mirada.edit.CropBox;
import org.jspecify.annotations.Nullable;

/**
 * The editor's canvas: the picture as the edit has turned and flipped it, fitted to the space, with
 * the crop box over it — the outside dimmed, the box ruled in thirds, a handle at each corner and
 * edge. Dragging inside moves the box, dragging a handle resizes it ({@link CropBox} has the rules);
 * every change is reported in the picture's own pixels.
 */
final class CropView extends Region {

    /** Space kept around the picture, so the handles at its edges can be grabbed. */
    private static final double MARGIN = 28;
    /** How near a handle the pointer must be to take it, in screen pixels. */
    private static final double REACH = 12;

    private final ImageView picture = new ImageView();
    private final Rectangle[] shade = new Rectangle[4];
    private final Rectangle border = new Rectangle();
    private final Line[] thirds = new Line[4];
    private final List<Rectangle> handles = new ArrayList<>();
    private final Consumer<CropBox> changed;

    private double pictureWidth = 1;
    private double pictureHeight = 1;
    private CropBox box = new CropBox(0, 0, 1, 1);
    private double ratio;

    /** Where on screen the picture's top left corner is, and screen pixels per picture pixel. */
    private double originX;

    private double originY;
    private double scale = 1;

    private CropBox.@Nullable Handle dragging;
    private double lastX;
    private double lastY;

    CropView(Consumer<CropBox> changed) {
        this.changed = changed;
        getStyleClass().add("crop-view");
        picture.setPreserveRatio(false);
        picture.setSmooth(true);
        picture.setManaged(false);
        getChildren().add(picture);
        for (int i = 0; i < shade.length; i++) {
            shade[i] = new Rectangle();
            shade[i].getStyleClass().add("crop-shade");
            shade[i].setManaged(false);
            shade[i].setMouseTransparent(true);
        }
        getChildren().addAll(shade);
        for (int i = 0; i < thirds.length; i++) {
            thirds[i] = new Line();
            thirds[i].getStyleClass().add("crop-third");
            thirds[i].setManaged(false);
            thirds[i].setMouseTransparent(true);
        }
        getChildren().addAll(thirds);
        border.getStyleClass().add("crop-border");
        border.setManaged(false);
        border.setMouseTransparent(true);
        getChildren().add(border);
        for (CropBox.Handle handle : CropBox.Handle.values()) {
            if (handle == CropBox.Handle.MOVE) continue;
            Rectangle knob = new Rectangle(10, 10);
            knob.getStyleClass().add("crop-handle");
            knob.setManaged(false);
            knob.setMouseTransparent(true);
            knob.setUserData(handle);
            handles.add(knob);
        }
        getChildren().addAll(handles);

        setOnMouseMoved(e -> setCursor(cursorFor(handleAt(e.getX(), e.getY()))));
        setOnMousePressed(this::onPressed);
        setOnMouseDragged(this::onDragged);
        setOnMouseReleased(e -> dragging = null);
    }

    /** A new picture (after a turn or a flip), with the box to show on it. */
    void show(Image image, CropBox newBox, double newRatio) {
        picture.setImage(image);
        pictureWidth = image.getWidth();
        pictureHeight = image.getHeight();
        box = newBox;
        ratio = newRatio;
        requestLayout();
    }

    void setBox(CropBox newBox, double newRatio) {
        box = newBox;
        ratio = newRatio;
        requestLayout();
    }

    CropBox box() {
        return box;
    }

    double pictureWidth() {
        return pictureWidth;
    }

    double pictureHeight() {
        return pictureHeight;
    }

    private void onPressed(MouseEvent e) {
        dragging = handleAt(e.getX(), e.getY());
        lastX = e.getX();
        lastY = e.getY();
        e.consume();
    }

    private void onDragged(MouseEvent e) {
        CropBox.Handle handle = dragging;
        if (handle == null) return;
        double dx = (e.getX() - lastX) / scale;
        double dy = (e.getY() - lastY) / scale;
        lastX = e.getX();
        lastY = e.getY();
        box = box.dragged(handle, dx, dy, ratio, pictureWidth, pictureHeight);
        requestLayout();
        changed.accept(box);
        e.consume();
    }

    /** The handle under ({@code x}, {@code y}), MOVE inside the box, or null outside it. */
    private CropBox.@Nullable Handle handleAt(double x, double y) {
        double left = originX + box.x() * scale;
        double top = originY + box.y() * scale;
        double right = left + box.width() * scale;
        double bottom = top + box.height() * scale;
        boolean nearLeft = Math.abs(x - left) <= REACH;
        boolean nearRight = Math.abs(x - right) <= REACH;
        boolean nearTop = Math.abs(y - top) <= REACH;
        boolean nearBottom = Math.abs(y - bottom) <= REACH;
        boolean withinX = x >= left - REACH && x <= right + REACH;
        boolean withinY = y >= top - REACH && y <= bottom + REACH;
        if (nearTop && nearLeft) return CropBox.Handle.NW;
        if (nearTop && nearRight) return CropBox.Handle.NE;
        if (nearBottom && nearLeft) return CropBox.Handle.SW;
        if (nearBottom && nearRight) return CropBox.Handle.SE;
        if (nearLeft && withinY) return CropBox.Handle.W;
        if (nearRight && withinY) return CropBox.Handle.E;
        if (nearTop && withinX) return CropBox.Handle.N;
        if (nearBottom && withinX) return CropBox.Handle.S;
        if (x > left && x < right && y > top && y < bottom) return CropBox.Handle.MOVE;
        return null;
    }

    private static Cursor cursorFor(CropBox.@Nullable Handle handle) {
        if (handle == null) return Cursor.DEFAULT;
        return switch (handle) {
            case MOVE -> Cursor.MOVE;
            case N -> Cursor.N_RESIZE;
            case S -> Cursor.S_RESIZE;
            case E -> Cursor.E_RESIZE;
            case W -> Cursor.W_RESIZE;
            case NE -> Cursor.NE_RESIZE;
            case NW -> Cursor.NW_RESIZE;
            case SE -> Cursor.SE_RESIZE;
            case SW -> Cursor.SW_RESIZE;
        };
    }

    @Override
    protected void layoutChildren() {
        double areaWidth = Math.max(1, getWidth() - 2 * MARGIN);
        double areaHeight = Math.max(1, getHeight() - 2 * MARGIN);
        scale = Math.min(areaWidth / pictureWidth, areaHeight / pictureHeight);
        double shownWidth = pictureWidth * scale;
        double shownHeight = pictureHeight * scale;
        originX = (getWidth() - shownWidth) / 2;
        originY = (getHeight() - shownHeight) / 2;
        picture.setFitWidth(shownWidth);
        picture.setFitHeight(shownHeight);
        picture.relocate(originX, originY);

        double left = originX + box.x() * scale;
        double top = originY + box.y() * scale;
        double width = box.width() * scale;
        double height = box.height() * scale;
        double right = left + width;
        double bottom = top + height;
        // Above, below, left of and right of the box, over the picture only.
        place(shade[0], originX, originY, shownWidth, top - originY);
        place(shade[1], originX, bottom, shownWidth, originY + shownHeight - bottom);
        place(shade[2], originX, top, left - originX, height);
        place(shade[3], right, top, originX + shownWidth - right, height);
        place(border, left, top, width, height);
        for (int i = 0; i < 2; i++) {
            double gx = left + width * (i + 1) / 3;
            double gy = top + height * (i + 1) / 3;
            thirds[i].setStartX(gx);
            thirds[i].setStartY(top);
            thirds[i].setEndX(gx);
            thirds[i].setEndY(bottom);
            thirds[i + 2].setStartX(left);
            thirds[i + 2].setStartY(gy);
            thirds[i + 2].setEndX(right);
            thirds[i + 2].setEndY(gy);
        }
        for (Rectangle knob : handles) {
            CropBox.Handle handle = (CropBox.Handle) knob.getUserData();
            double hx =
                    switch (handle) {
                        case W, NW, SW -> left;
                        case E, NE, SE -> right;
                        default -> left + width / 2;
                    };
            double hy =
                    switch (handle) {
                        case N, NE, NW -> top;
                        case S, SE, SW -> bottom;
                        default -> top + height / 2;
                    };
            knob.relocate(hx - knob.getWidth() / 2, hy - knob.getHeight() / 2);
        }
    }

    private static void place(Rectangle rectangle, double x, double y, double width, double height) {
        rectangle.setX(0);
        rectangle.setY(0);
        rectangle.relocate(x, y);
        rectangle.setWidth(Math.max(0, width));
        rectangle.setHeight(Math.max(0, height));
    }
}
