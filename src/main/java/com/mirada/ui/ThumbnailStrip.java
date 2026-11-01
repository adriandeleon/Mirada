package com.mirada.ui;

import java.nio.file.Path;
import java.util.List;
import java.util.function.IntConsumer;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.StackPane;

import com.mirada.image.Thumbnails;

/**
 * The folder as a strip of thumbnails along the bottom of the window: optional, closed until asked
 * for (Command.THUMBNAILS), and built the first time it opens, so it costs start-up nothing.
 *
 * <p>A horizontal list view, so only the cells on screen exist and only they ask for a thumbnail;
 * scrolling a folder of thousands asks for the few dozen in view. The image on screen is the
 * selected cell, kept in view as the arrow keys step. A click shows that image.
 */
final class ThumbnailStrip {

    static final double CELL = 92;
    static final double PICTURE = 76;

    private final ListView<Path> list = new ListView<>();
    private final ObservableList<Path> items = FXCollections.observableArrayList();
    private final Thumbnails thumbnails = new Thumbnails();

    /** @param pick shows the image at an index of the folder */
    ThumbnailStrip(IntConsumer pick) {
        list.setItems(items);
        list.setOrientation(Orientation.HORIZONTAL);
        list.getStyleClass().add("thumbnail-strip");
        list.setFixedCellSize(CELL);
        list.setPrefHeight(CELL + 14);
        list.setMinHeight(CELL + 14);
        list.setMaxHeight(CELL + 14);
        // The keyboard is the viewer's: arrows step the folder, not the list's selection.
        list.setFocusTraversable(false);
        list.setCellFactory(view -> new Cell(pick));
    }

    ListView<Path> node() {
        return list;
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
        int visible = (int) Math.max(1, list.getWidth() / CELL);
        list.scrollTo(Math.max(0, index - visible / 2));
    }

    private final class Cell extends ListCell<Path> {
        private final ImageView picture = new ImageView();
        private final StackPane frame = new StackPane(picture);

        Cell(IntConsumer pick) {
            picture.setPreserveRatio(true);
            picture.setSmooth(true);
            // A square box: a portrait thumbnail and a landscape one, turned or not, both fit it.
            picture.setFitWidth(PICTURE);
            picture.setFitHeight(PICTURE);
            frame.getStyleClass().add("thumbnail");
            frame.setAlignment(Pos.CENTER);
            frame.setPrefSize(CELL - 8, CELL - 8);
            setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.PRIMARY && !isEmpty()) pick.accept(getIndex());
            });
        }

        @Override
        protected void updateItem(Path file, boolean empty) {
            super.updateItem(file, empty);
            picture.setImage(null);
            if (empty || file == null) {
                setGraphic(null);
                return;
            }
            setGraphic(frame);
            thumbnails
                    .get(file)
                    .thenAcceptAsync(
                            thumbnail -> {
                                // Cells are recycled while scrolling: only the file this cell still shows.
                                if (!file.equals(getItem())) return;
                                picture.setRotate(thumbnail.orientation().degrees());
                                picture.setScaleX(thumbnail.orientation().mirrored() ? -1 : 1);
                                picture.setImage(thumbnail.image());
                            },
                            Platform::runLater);
        }
    }
}
