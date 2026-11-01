package com.mirada.ui;

import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.scene.Cursor;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Region;
import javafx.scene.shape.Rectangle;

import com.mirada.image.Orientation;
import org.jspecify.annotations.Nullable;

/**
 * One image on a canvas: fitted to the window until the user zooms, then panned by dragging.
 *
 * <p>The image is an {@link ImageView} sized to the zoomed image and turned about its centre, so
 * the GPU does the scaling and rotation and the pixels are never copied. All placement happens in
 * {@link #layoutChildren}, from four numbers: the scale, whether it follows the window ({@code
 * fitted}), and the image's top-left corner on screen ({@code offsetX}, {@code offsetY}) as the
 * upright, turned image would stand.
 *
 * <p>Wheel zooms about the pointer, drag pans, double-click toggles between fitting and 100 %.
 */
public final class ImageViewport extends Region {

    /** Wheel zoom per unit of scroll delta; a mouse notch is 40 units, so about 22 % a notch. */
    private static final double WHEEL_RATE = 1.005;

    private final ImageView view = new ImageView();
    private final ReadOnlyDoubleWrapper scale = new ReadOnlyDoubleWrapper(this, "scale", 1);
    private final ReadOnlyBooleanWrapper fitted = new ReadOnlyBooleanWrapper(this, "fitted", true);

    private @Nullable Image image;
    /**
     * The real image's size, unturned. The same as {@link #image}'s, except while a preview stands
     * in for it: then the smaller preview is stretched to this size, so zoom, fit and what the user
     * sees do not jump when the full image replaces it.
     */
    private double imageWidth;

    private double imageHeight;
    private Orientation orientation = Orientation.NORMAL;
    /** The user's turns on top of the file's own orientation, clockwise, 0 to 3. */
    private int turns;

    private double offsetX;
    private double offsetY;
    private double dragX;
    private double dragY;

    public ImageViewport() {
        getStyleClass().add("viewport");
        view.setPreserveRatio(false);
        view.setManaged(false);
        getChildren().add(view);

        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(widthProperty());
        clip.heightProperty().bind(heightProperty());
        setClip(clip);

        setOnScroll(this::onScroll);
        setOnMousePressed(this::onPressed);
        setOnMouseDragged(this::onDragged);
        setOnMouseReleased(e -> setCursor(Cursor.DEFAULT));
        setOnMouseClicked(this::onClicked);
    }

    /** Whether a newly shown image keeps the zoom and position, rather than being fitted. */
    private boolean keepZoom;

    /** Shows {@code image} upright by {@code orientation}, with the user's turns reset. */
    public void show(Image image, Orientation orientation) {
        show(image, orientation, image.getWidth(), image.getHeight());
    }

    /**
     * Shows a preview of an image {@code width} × {@code height} pixels, laid out as that image would
     * be, until {@link #replace} hands over the image itself.
     *
     * <p>Fitted, unless {@link #setKeepZoom keeping the zoom} and the last image was zoomed: then at
     * the same scale and position, to compare the same detail across a burst of photos.
     */
    public void show(Image preview, Orientation orientation, double width, double height) {
        this.image = preview;
        this.imageWidth = width;
        this.imageHeight = height;
        this.orientation = orientation;
        this.turns = 0;
        view.setImage(preview);
        if (!keepZoom) fitted.set(true);
        requestLayout();
    }

    public void setKeepZoom(boolean keep) {
        keepZoom = keep;
    }

    /**
     * Moves a zoomed-in image by a fraction of the window: {@code (-0.1, 0)} shows what is to the
     * left. Nothing happens to an image that fits.
     */
    public void pan(double fractionX, double fractionY) {
        if (image == null || fitted.get()) return;
        offsetX -= fractionX * getWidth();
        offsetY -= fractionY * getHeight();
        requestLayout();
    }

    /** Puts the full image in place of its preview, keeping the zoom, position and turns. */
    public void replace(Image full) {
        this.image = full;
        this.imageWidth = full.getWidth();
        this.imageHeight = full.getHeight();
        view.setImage(full);
        requestLayout();
    }

    public void clear() {
        image = null;
        view.setImage(null);
        requestLayout();
    }

    public ReadOnlyDoubleProperty scaleProperty() {
        return scale.getReadOnlyProperty();
    }

    public ReadOnlyBooleanProperty fittedProperty() {
        return fitted.getReadOnlyProperty();
    }

    /** The image's width as it stands on screen, turned: what the status bar reports. */
    public double contentWidth() {
        if (image == null) return 0;
        return sideways() ? imageHeight : imageWidth;
    }

    public double contentHeight() {
        if (image == null) return 0;
        return sideways() ? imageWidth : imageHeight;
    }

    public void fit() {
        fitted.set(true);
        requestLayout();
    }

    public void zoomIn() {
        zoomTo(Zoom.zoomIn(scale.get()), getWidth() / 2, getHeight() / 2);
    }

    public void zoomOut() {
        zoomTo(Zoom.zoomOut(scale.get()), getWidth() / 2, getHeight() / 2);
    }

    public void actualSize() {
        zoomTo(1, getWidth() / 2, getHeight() / 2);
    }

    /** Turns the image a quarter clockwise ({@code +1}) or counterclockwise ({@code -1}). */
    public void rotate(int quarterTurns) {
        turns = Math.floorMod(turns + quarterTurns, 4);
        requestLayout();
    }

    /** Zooms to {@code newScale}, keeping the image point under ({@code pivotX}, {@code pivotY}) still. */
    void zoomTo(double newScale, double pivotX, double pivotY) {
        if (image == null) return;
        double target = Zoom.clamp(newScale);
        double old = scale.get();
        offsetX = Zoom.offsetAbout(offsetX, pivotX, old, target);
        offsetY = Zoom.offsetAbout(offsetY, pivotY, old, target);
        scale.set(target);
        fitted.set(false);
        requestLayout();
    }

    @Override
    protected void layoutChildren() {
        if (image == null || imageWidth <= 0 || imageHeight <= 0) {
            view.setVisible(false);
            return;
        }
        double width = contentWidth();
        double height = contentHeight();
        if (fitted.get()) scale.set(Zoom.fitScale(width, height, getWidth(), getHeight()));
        double s = scale.get();
        offsetX = Zoom.clampOffset(offsetX, width * s, getWidth());
        offsetY = Zoom.clampOffset(offsetY, height * s, getHeight());

        // The view is laid out unturned and turns about its own centre, so place that centre on
        // the centre of where the turned image should stand.
        double viewWidth = imageWidth * s;
        double viewHeight = imageHeight * s;
        double centreX = offsetX + width * s / 2;
        double centreY = offsetY + height * s / 2;
        view.setFitWidth(viewWidth);
        view.setFitHeight(viewHeight);
        view.setLayoutX(centreX - viewWidth / 2);
        view.setLayoutY(centreY - viewHeight / 2);
        view.setScaleX(orientation.mirrored() ? -1 : 1);
        view.setRotate(totalDegrees());
        view.setSmooth(s < Zoom.CRISP_FROM);
        view.setVisible(true);
    }

    private int totalDegrees() {
        return Math.floorMod(orientation.degrees() + 90 * turns, 360);
    }

    private boolean sideways() {
        return totalDegrees() % 180 != 0;
    }

    private boolean pannable() {
        double s = scale.get();
        return contentWidth() * s > getWidth() + 0.5 || contentHeight() * s > getHeight() + 0.5;
    }

    private void onScroll(ScrollEvent e) {
        if (e.getDeltaY() == 0 || image == null) return;
        zoomTo(scale.get() * Math.pow(WHEEL_RATE, e.getDeltaY()), e.getX(), e.getY());
        e.consume();
    }

    private void onPressed(MouseEvent e) {
        dragX = e.getX();
        dragY = e.getY();
        if (e.getButton() == MouseButton.PRIMARY && pannable()) setCursor(Cursor.CLOSED_HAND);
    }

    private void onDragged(MouseEvent e) {
        if (e.getButton() != MouseButton.PRIMARY || !pannable()) return;
        offsetX += e.getX() - dragX;
        offsetY += e.getY() - dragY;
        dragX = e.getX();
        dragY = e.getY();
        requestLayout();
    }

    private void onClicked(MouseEvent e) {
        if (e.getButton() != MouseButton.PRIMARY || e.getClickCount() != 2 || !e.isStillSincePress()) return;
        if (fitted.get()) {
            zoomTo(1, e.getX(), e.getY());
        } else {
            fit();
        }
    }
}
