package com.mirada.ui;

import java.io.File;
import java.io.IOException;
import java.nio.IntBuffer;
import java.nio.file.Path;
import java.util.Locale;
import java.util.function.Consumer;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.PixelBuffer;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import javafx.util.StringConverter;

import com.mirada.edit.AspectRatio;
import com.mirada.edit.CropBox;
import com.mirada.edit.Edit;
import com.mirada.edit.Encoder;
import com.mirada.edit.ExifBlock;
import com.mirada.edit.History;
import com.mirada.edit.IccProfile;
import com.mirada.edit.Raster;
import com.mirada.edit.Xmp;
import com.mirada.image.ImageCache;
import com.mirada.image.Orientation;
import com.mirada.image.Shrink;
import org.jspecify.annotations.Nullable;

/**
 * The editor, GNOME Image Viewer's: crop (free, or held to a shape), turn, flip, reset; Cancel or
 * Save. It takes the window's content area, below the header, and works on a preview of at most
 * {@value #PREVIEW_SIZE} pixels so every turn and drag is instant; Save applies the same edit to the
 * full image off the FX thread and writes it as a new file, the original untouched unless it is
 * chosen in the save dialog.
 *
 * <p>Every step can be undone, Reset included: a turn, a flip, a new shape, each drag of the box,
 * and each run of arrow presses. The saved file keeps the original's EXIF, made true of the edit
 * ({@link ExifBlock}), and its colour profile ({@link IccProfile}) when the pixels are still in its
 * colours.
 *
 * <p>Keys: Enter or Ctrl+S saves, Escape cancels, R and Shift+R turn, H and V flip, A steps through
 * the shapes, Backspace resets, Ctrl+Z undoes and Ctrl+Shift+Z or Ctrl+Y redoes, the arrows move the
 * box (Shift: further) and Ctrl+arrows resize it.
 */
final class Editor {

    static final int PREVIEW_SIZE = 1600;

    /** What one undo takes back: the edit and the shape the crop is held to. */
    private record Step(Edit edit, AspectRatio shape) {}

    /** The runs of arrow presses that undo as one: moving the box, and resizing it. */
    private enum Nudging {
        MOVE,
        RESIZE
    }

    private final BorderPane window;
    private final Node viewer;
    private final BorderPane node = new BorderPane();
    private final CropView crop = new CropView(this::cropped, this::record);
    private final ChoiceBox<AspectRatio> shape = new ChoiceBox<>();
    private final Label title = new Label("Edit");
    private final Consumer<String> say;

    private @Nullable Image full;
    private Orientation orientation = Orientation.NORMAL;
    private @Nullable Path source;
    private Consumer<Path> saved = file -> {};
    private Raster uprightPreview = new Raster(1, 1, new int[] {0});
    private Edit edit = Edit.NONE;
    private final History<Step> history = new History<>(new Step(Edit.NONE, AspectRatio.FREE));
    private final Button undoButton = tool("Undo", "Ctrl+Z", this::undo);
    private final Button redoButton = tool("Redo", "Ctrl+Shift+Z", this::redo);
    /** True while the editor sets the shape itself, so the shape's listener records no step. */
    private boolean quiet;

    private boolean saving;

    /**
     * @param window the window's layout, whose centre the editor takes while it is open
     * @param viewer what that centre holds otherwise, put back when the editor closes
     * @param say shows a short message over the window, as the viewer does
     */
    Editor(BorderPane window, Node viewer, Consumer<String> say) {
        this.window = window;
        this.viewer = viewer;
        this.say = say;
        node.getStyleClass().add("editor");

        Button cancel = new Button("Cancel");
        cancel.setCancelButton(true);
        cancel.setOnAction(e -> close());
        Button save = new Button("Save…");
        save.getStyleClass().add("accent");
        save.setDefaultButton(true);
        save.setOnAction(e -> save());
        title.getStyleClass().add("editor-title");
        Region left = new Region();
        Region right = new Region();
        HBox.setHgrow(left, Priority.ALWAYS);
        HBox.setHgrow(right, Priority.ALWAYS);
        HBox top = new HBox(12, cancel, left, title, right, save);
        top.setAlignment(Pos.CENTER);
        top.setPadding(new Insets(10, 14, 10, 14));
        top.getStyleClass().add("editor-bar");

        shape.getItems().addAll(AspectRatio.values());
        shape.setConverter(new StringConverter<>() {
            @Override
            public String toString(AspectRatio ratio) {
                return ratio == null ? "" : ratio.label();
            }

            @Override
            public AspectRatio fromString(String text) {
                return AspectRatio.FREE;
            }
        });
        shape.setValue(AspectRatio.FREE);
        shape.valueProperty().addListener((o, was, now) -> {
            if (quiet) return;
            shapeChanged();
            record();
        });
        shape.setTooltip(new Tooltip("The crop's shape (A)"));
        HBox bottom = new HBox(
                8,
                new Label("Crop"),
                shape,
                spacer(),
                tool("Rotate Left", "Shift+R", () -> turn(-1)),
                tool("Rotate Right", "R", () -> turn(1)),
                tool("Flip Horizontally", "H", () -> apply(edit.flippedHorizontally())),
                tool("Flip Vertically", "V", () -> apply(edit.flippedVertically())),
                spacer(),
                undoButton,
                redoButton,
                tool("Reset", "Backspace", this::reset));
        bottom.setAlignment(Pos.CENTER);
        bottom.setPadding(new Insets(10, 14, 10, 14));
        bottom.getStyleClass().add("editor-bar");

        node.setTop(top);
        node.setCenter(crop);
        node.setBottom(bottom);
    }

    boolean isShowing() {
        return window.getCenter() == node;
    }

    /**
     * Opens on {@code image} (full resolution), shown upright by {@code imageOrientation}; {@code
     * whenSaved} receives the file written.
     */
    void open(Image image, Orientation imageOrientation, Path file, Consumer<Path> whenSaved) {
        full = image;
        orientation = imageOrientation;
        source = file;
        saved = whenSaved;
        uprightPreview = Edit.upright(raster(Shrink.toFit(image, PREVIEW_SIZE)), imageOrientation);
        edit = Edit.NONE;
        quiet = true;
        shape.setValue(AspectRatio.FREE);
        quiet = false;
        history.reset(new Step(Edit.NONE, AspectRatio.FREE));
        updateUndo();
        title.setText("Edit " + name(file));
        showArranged();
        if (!isShowing()) window.setCenter(node);
        node.requestFocus();
    }

    void close() {
        if (isShowing()) window.setCenter(viewer);
        full = null;
    }

    /** The editor's keys, while it shows; true when one was used. */
    boolean onKey(KeyEvent e) {
        KeyCode code = e.getCode();
        double step = e.isShiftDown() ? 0.1 : 0.01;
        double dx = step * crop.pictureWidth();
        double dy = step * crop.pictureHeight();
        switch (code) {
            case ESCAPE -> close();
            case ENTER -> save();
            case S -> {
                if (!e.isShortcutDown()) return false;
                save();
            }
            case Z -> {
                if (!e.isShortcutDown()) return false;
                if (e.isShiftDown()) redo();
                else undo();
            }
            case Y -> {
                if (!e.isShortcutDown()) return false;
                redo();
            }
            case R -> turn(e.isShiftDown() ? -1 : 1);
            case H -> apply(edit.flippedHorizontally());
            case V -> apply(edit.flippedVertically());
            case A -> shape.setValue(shape.getValue().next());
            case BACK_SPACE -> reset();
            case LEFT, RIGHT ->
                nudge(e.isShortcutDown() ? CropBox.Handle.E : CropBox.Handle.MOVE, code == KeyCode.LEFT ? -dx : dx, 0);
            case UP, DOWN ->
                nudge(e.isShortcutDown() ? CropBox.Handle.S : CropBox.Handle.MOVE, 0, code == KeyCode.UP ? -dy : dy);
            default -> {
                return false;
            }
        }
        return true;
    }

    private void nudge(CropBox.Handle handle, double dx, double dy) {
        CropBox moved = crop.box().dragged(handle, dx, dy, ratio(), crop.pictureWidth(), crop.pictureHeight());
        crop.setBox(moved, ratio());
        cropped(moved);
        history.record(new Step(edit, shape.getValue()), handle == CropBox.Handle.MOVE ? Nudging.MOVE : Nudging.RESIZE);
        updateUndo();
    }

    private void turn(int quarterTurns) {
        edit = edit.turned(quarterTurns);
        showArranged();
        shapeChanged(); // the whole picture again, held to the shape if one is chosen
        record();
    }

    private void reset() {
        quiet = true;
        shape.setValue(AspectRatio.FREE);
        quiet = false;
        apply(Edit.NONE);
    }

    private void apply(Edit next) {
        edit = next;
        showArranged();
        record();
    }

    /** The edit and shape as they are now, a step to undo. */
    private void record() {
        history.record(new Step(edit, shape.getValue()));
        updateUndo();
    }

    private void undo() {
        restore(history.undo());
    }

    private void redo() {
        restore(history.redo());
    }

    private void restore(Step step) {
        quiet = true;
        shape.setValue(step.shape());
        quiet = false;
        edit = step.edit();
        showArranged();
        updateUndo();
    }

    private void updateUndo() {
        undoButton.setDisable(!history.canUndo());
        redoButton.setDisable(!history.canRedo());
    }

    /** The preview turned and flipped as the edit says, with its crop box. */
    private void showArranged() {
        Raster arranged = edit.arranged(uprightPreview);
        CropBox box = CropBox.of(edit.crop(), arranged.width(), arranged.height());
        crop.show(image(arranged), box, ratio(arranged.width(), arranged.height()));
    }

    private void shapeChanged() {
        double width = crop.pictureWidth();
        double height = crop.pictureHeight();
        double ratio = ratio(width, height);
        CropBox box = edit.crop().isAll() || ratio <= 0
                ? CropBox.largest(ratio, width, height)
                : crop.box().heldTo(ratio, width, height);
        crop.setBox(box, ratio);
        cropped(box);
    }

    private void cropped(CropBox box) {
        edit = edit.withCrop(box.toCrop(crop.pictureWidth(), crop.pictureHeight()));
    }

    private double ratio() {
        return ratio(crop.pictureWidth(), crop.pictureHeight());
    }

    private double ratio(double width, double height) {
        AspectRatio chosen = shape.getValue();
        return chosen == null ? 0 : chosen.ratio(width, height);
    }

    /**
     * Asks where, then applies the edit to the full image and writes it, off the FX thread. The
     * suggested name is the original's with "(edited)", in a format Mirada can write.
     */
    private void save() {
        Image image = full;
        Path file = source;
        if (image == null || file == null || saving) return;
        if (edit.isNone()) {
            say.accept("Nothing to save: crop, turn or flip first");
            return;
        }
        Path folder = file.toAbsolutePath().getParent();
        String stem = name(file).replaceFirst("\\.[^.]+$", "");
        String extension = Encoder.extensionFor(file, true);
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Edited Image");
        if (folder != null) chooser.setInitialDirectory(folder.toFile());
        chooser.setInitialFileName(stem + " (edited)." + extension);
        chooser.getExtensionFilters()
                .addAll(
                        new FileChooser.ExtensionFilter("JPEG", "*.jpg", "*.jpeg"),
                        new FileChooser.ExtensionFilter("PNG", "*.png"),
                        new FileChooser.ExtensionFilter("TIFF", "*.tif", "*.tiff"),
                        new FileChooser.ExtensionFilter("BMP", "*.bmp"));
        Window owner = node.getScene() == null ? null : node.getScene().getWindow();
        File chosen = chooser.showSaveDialog(owner);
        if (chosen == null) return;
        Path target = withExtension(chosen.toPath(), extension);
        Edit applied = edit;
        Orientation turnedBy = orientation;
        saving = true;
        say.accept("Saving " + name(target) + "…");
        Thread.ofPlatform().daemon().name("save").start(() -> {
            try {
                Raster result = applied.apply(Edit.upright(raster(image), turnedBy));
                Encoder.write(
                        result,
                        target,
                        ExifBlock.carried(file, result.width(), result.height()),
                        ImageCache.keepsColours(file) ? IccProfile.carried(file) : null,
                        Xmp.carried(file, result.width(), result.height()));
                Platform.runLater(() -> {
                    saving = false;
                    close();
                    saved.accept(target);
                });
            } catch (IOException | RuntimeException | OutOfMemoryError e) {
                Platform.runLater(() -> {
                    saving = false;
                    say.accept("Couldn't save " + name(target) + ": " + e.getMessage());
                });
            }
        });
    }

    /** {@code file}, given {@code extension} when its name has none Mirada can write. */
    static Path withExtension(Path file, String extension) {
        String name = name(file);
        int dot = name.lastIndexOf('.');
        String own = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
        return Encoder.EXTENSIONS.contains(own) ? file : file.resolveSibling(name + "." + extension);
    }

    /** Every pixel of a loaded image, premultiplied. Any thread. */
    static Raster raster(Image image) {
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        int[] pixels = new int[width * height];
        image.getPixelReader().getPixels(0, 0, width, height, PixelFormat.getIntArgbPreInstance(), pixels, 0, width);
        return new Raster(width, height, pixels);
    }

    static Image image(Raster raster) {
        return new WritableImage(new PixelBuffer<>(
                raster.width(), raster.height(), IntBuffer.wrap(raster.pixels()), PixelFormat.getIntArgbPreInstance()));
    }

    private static Button tool(String label, String key, Runnable action) {
        Button button = new Button(label);
        button.setTooltip(new Tooltip(label + " (" + key + ")"));
        button.setOnAction(e -> action.run());
        button.setFocusTraversable(false);
        return button;
    }

    private static Region spacer() {
        Region region = new Region();
        HBox.setHgrow(region, Priority.ALWAYS);
        return region;
    }

    private static String name(Path file) {
        Path name = file.getFileName();
        return name == null ? file.toString() : name.toString();
    }
}
