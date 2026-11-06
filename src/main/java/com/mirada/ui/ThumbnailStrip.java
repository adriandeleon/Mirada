package com.mirada.ui;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.IntConsumer;
import java.util.function.Predicate;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.OverrunStyle;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import com.mirada.image.Thumbnails;
import org.jspecify.annotations.Nullable;

/**
 * The folder as a strip along the bottom of the window, or a column down its left side, of
 * thumbnails or of file names: optional, closed until asked for (Command.THUMBNAILS), and built the
 * first time it opens, so it costs start-up nothing. Where it stands and what it shows are settings
 * ({@link #setLayout}).
 *
 * <p>A list view, so only the cells on screen exist and only they ask for a thumbnail; scrolling a
 * folder of thousands asks for the few dozen in view, and the names asks for none. The image on
 * screen is the selected cell, kept in view as the arrow keys step. A click shows that image. A
 * bookmarked image wears a bookmark in its corner, or after its name ({@link #setMarked}).
 */
final class ThumbnailStrip {

    static final double CELL = 92;
    static final double PICTURE = 76;
    /** A name's row in the column, and its width across the bottom. */
    static final double NAME_ROW = 30;

    static final double NAME_WIDTH = 180;
    /** The column of names: wide enough for a camera's file name and most people's. */
    static final double NAMES_COLUMN = 240;
    /** A thumbnail with its name under it: wider, for the name, and a line taller. */
    static final double CAPTIONED_WIDTH = 124;

    static final double CAPTION = 20;
    /** The list's own padding and border, and room for a scroll bar across it. */
    private static final double CHROME = 18;

    private static final PseudoClass SIDE = PseudoClass.getPseudoClass("side");
    private static final PseudoClass NAMES = PseudoClass.getPseudoClass("names");

    private final ListView<Path> list = new ListView<>();
    private final ObservableList<Path> items = FXCollections.observableArrayList();
    private final Thumbnails thumbnails = new Thumbnails();
    private boolean side;
    private boolean names;
    private boolean captions;
    private boolean laidOut;
    /** Whether an image is bookmarked; asked as each cell shows one, and again by {@link #refreshMarks}. */
    private Predicate<Path> marked = file -> false;
    /** The cells alive, to show a mark that changed without making them again; weak, as the list drops them. */
    private final Set<Cell> cells = Collections.newSetFromMap(new WeakHashMap<>());

    /** Starts dragging a file out, from a node, with a picture under the pointer. */
    @FunctionalInterface
    interface Dragger {
        void drag(Node source, Path file, @Nullable Image picture);
    }

    /**
     * @param pick shows the image at an index of the folder
     * @param drag starts a drag of a cell's file out of the window
     */
    ThumbnailStrip(IntConsumer pick, Dragger drag) {
        list.setItems(items);
        list.getStyleClass().add("thumbnail-strip");
        // The keyboard is the viewer's: arrows step the folder, not the list's selection.
        list.setFocusTraversable(false);
        list.setCellFactory(view -> new Cell(pick, drag));
        setLayout(false, false, false);
    }

    ListView<Path> node() {
        return list;
    }

    /** Whether it stands down the left side; along the bottom otherwise. */
    boolean atSide() {
        return side;
    }

    /**
     * A column down the side or a row along the bottom ({@code side}), of file names or of pictures
     * ({@code names}), the pictures with their names under them or not ({@code captions}). The cells
     * are made again, as each kind is laid out for one of them.
     */
    void setLayout(boolean side, boolean names, boolean captions) {
        if (laidOut && side == this.side && names == this.names && captions == this.captions) return;
        laidOut = true;
        this.side = side;
        this.names = names;
        this.captions = captions;
        list.setOrientation(side ? Orientation.VERTICAL : Orientation.HORIZONTAL);
        Size size = size(side, names, captions);
        list.setFixedCellSize(size.cell());
        double thickness = size.thickness();
        list.pseudoClassStateChanged(SIDE, side);
        list.pseudoClassStateChanged(NAMES, names);
        if (side) {
            fix(thickness, Region.USE_COMPUTED_SIZE);
        } else {
            fix(Region.USE_COMPUTED_SIZE, thickness);
        }
        list.refresh();
    }

    /**
     * Stands {@code strip} down {@code root}'s left side or along its bottom, out of the other place
     * first. Setting a place adds the node to the pane's children, and one it still holds in the
     * other place is a duplicate: going from the side back to the bottom threw, then emptying the
     * side took the strip away altogether.
     */
    static void place(BorderPane root, Node strip, boolean side) {
        if (root.getLeft() == strip) root.setLeft(null);
        if (root.getBottom() == strip) root.setBottom(null);
        if (side) {
            root.setLeft(strip);
        } else {
            root.setBottom(strip);
        }
    }

    /** A cell's length along the strip, and the strip's thickness across it. */
    record Size(double cell, double thickness) {}

    static Size size(boolean side, boolean names, boolean captions) {
        if (names) return side ? new Size(NAME_ROW, NAMES_COLUMN) : new Size(NAME_WIDTH, NAME_ROW + CHROME);
        if (!captions) return new Size(CELL, CELL + CHROME);
        return side
                ? new Size(CELL + CAPTION, CAPTIONED_WIDTH + CHROME)
                : new Size(CAPTIONED_WIDTH, CELL + CAPTION + CHROME);
    }

    private void fix(double width, double height) {
        list.setMinWidth(width);
        list.setPrefWidth(width);
        list.setMaxWidth(width == Region.USE_COMPUTED_SIZE ? Double.MAX_VALUE : width);
        list.setMinHeight(height);
        list.setPrefHeight(height);
        list.setMaxHeight(height == Region.USE_COMPUTED_SIZE ? Double.MAX_VALUE : height);
    }

    /** Says which images are bookmarked, for the cells' badges. */
    void setMarked(Predicate<Path> marked) {
        this.marked = marked;
        refreshMarks();
    }

    /** Shows each cell's bookmark again, after one was set or taken off. */
    void refreshMarks() {
        for (Cell cell : cells) cell.showMark();
    }

    /** Shows {@code images}, the one at {@code index} selected and in view. */
    void show(List<Path> images, int index) {
        if (!items.equals(images)) items.setAll(images);
        if (index < 0 || index >= items.size()) {
            list.getSelectionModel().clearSelection();
            return;
        }
        list.getSelectionModel().select(index);
        // Scrolled so the current cell sits about the middle, where the eye already is.
        double extent = side ? list.getHeight() : list.getWidth();
        int visible = (int) Math.max(1, extent / list.getFixedCellSize());
        list.scrollTo(Math.max(0, index - visible / 2));
    }

    private final class Cell extends ListCell<Path> {
        private final ImageView picture = new ImageView();
        private final StackPane frame = new StackPane(picture);
        private final Label name = new Label();
        private final Label caption = new Label();
        private final VBox captioned = new VBox(2);
        /** On the picture's corner, and after the name in the list of names. */
        private final Node badge = Icons.of(Icons.BOOKMARK);

        private final Node nameBadge = Icons.of(Icons.BOOKMARK);
        /** The file whose thumbnail this cell asked for, until it shows another. */
        private @Nullable Path asked;

        Cell(IntConsumer pick, Dragger drag) {
            picture.setPreserveRatio(true);
            picture.setSmooth(true);
            // A square box: a portrait thumbnail and a landscape one, turned or not, both fit it.
            picture.setFitWidth(PICTURE);
            picture.setFitHeight(PICTURE);
            frame.getStyleClass().add("thumbnail");
            frame.setAlignment(Pos.CENTER);
            badge.getStyleClass().add("thumbnail-mark");
            badge.setMouseTransparent(true);
            badge.setVisible(false);
            StackPane.setAlignment(badge, Pos.TOP_RIGHT);
            StackPane.setMargin(badge, new Insets(4));
            frame.getChildren().add(badge);
            nameBadge.getStyleClass().add("thumbnail-mark");
            name.setContentDisplay(ContentDisplay.RIGHT);
            frame.setPrefSize(CELL - 8, CELL - 8);
            // Square in a captioned cell too, which is wider than the picture.
            frame.setMaxSize(CELL - 8, CELL - 8);
            name.getStyleClass().add("thumbnail-name");
            name.setMaxWidth(Double.MAX_VALUE);
            // The extension and the number at the end tell a burst apart: cut the middle.
            name.setTextOverrun(OverrunStyle.CENTER_ELLIPSIS);
            caption.getStyleClass().add("thumbnail-caption");
            caption.setMaxWidth(CAPTIONED_WIDTH - 8);
            caption.setTextOverrun(OverrunStyle.CENTER_ELLIPSIS);
            caption.setAlignment(Pos.CENTER);
            captioned.setAlignment(Pos.TOP_CENTER);
            setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.PRIMARY && !isEmpty()) pick.accept(getIndex());
            });
            setOnDragDetected(e -> {
                Path file = getItem();
                if (e.getButton() != MouseButton.PRIMARY || isEmpty() || file == null) return;
                drag.drag(this, file, picture.getImage());
                e.consume();
            });
            cells.add(this);
        }

        /** The bookmark on the picture's corner, or after the name, while the image has one. */
        void showMark() {
            Path file = getItem();
            boolean on = !isEmpty() && file != null && marked.test(file);
            badge.setVisible(on);
            name.setGraphic(on ? nameBadge : null);
        }

        @Override
        protected void updateItem(Path file, boolean empty) {
            super.updateItem(file, empty);
            picture.setImage(null);
            Path previous = asked;
            if (previous != null && (names || empty || !previous.equals(file))) {
                // Scrolled past before it was made: let the ones on screen go first.
                thumbnails.release(previous);
                asked = null;
            }
            showMark();
            if (empty || file == null) {
                setGraphic(null);
                return;
            }
            Path fileName = file.getFileName();
            String text = fileName == null ? file.toString() : fileName.toString();
            if (names) {
                name.setText(text);
                setGraphic(name);
                return;
            }
            if (captions) {
                caption.setText(text);
                // The frame moves between the two shapes as the setting changes; never in both.
                captioned.getChildren().setAll(frame, caption);
                setGraphic(captioned);
            } else {
                captioned.getChildren().clear();
                setGraphic(frame);
            }
            asked = file;
            thumbnails
                    .get(file)
                    .thenAcceptAsync(
                            thumbnail -> {
                                // Cells are recycled while scrolling: only the file this cell still shows.
                                if (!file.equals(getItem()) || names) return;
                                picture.setRotate(thumbnail.orientation().degrees());
                                picture.setScaleX(thumbnail.orientation().mirrored() ? -1 : 1);
                                picture.setImage(thumbnail.image());
                            },
                            Platform::runLater);
        }
    }
}
