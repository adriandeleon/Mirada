package com.mirada.ui;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.css.PseudoClass;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.image.Image;
import javafx.scene.input.DragEvent;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import com.mirada.AppInfo;
import com.mirada.folder.FolderImages;
import com.mirada.folder.ImageFormats;
import com.mirada.image.ImageCache;
import org.jspecify.annotations.Nullable;

/**
 * The one window: the image, and a status line under it. Opening a file loads its folder; the arrow
 * keys walk it.
 *
 * <p>Stepping keeps the previous image on screen until the next one is decoded and its EXIF read,
 * so the window never flashes empty or shows a photo on its side before turning it. The neighbours
 * either side are loaded once the current image is up, so the next step is usually instant.
 */
public final class ViewerWindow {

    private static final PseudoClass FULL_SCREEN = PseudoClass.getPseudoClass("full-screen");
    private static final String EMPTY_HINT = "Open an image with Ctrl+O, or drop one here";

    private final Stage stage;
    private final ImageViewport viewport = new ImageViewport();
    private final Label placeholder = new Label(EMPTY_HINT);
    private final Label name = new Label();
    private final Label position = new Label();
    private final Label dimensions = new Label();
    private final Label zoom = new Label();
    private final Label shot = new Label();
    private final HBox statusBar;
    private final ImageCache cache = new ImageCache(3);
    private final CommandPalette palette;

    private FolderImages folder = FolderImages.EMPTY;
    /** The image asked for most recently; a load that finishes for any other is dropped. */
    private @Nullable Path wanted;

    public ViewerWindow(Stage stage) {
        this.stage = stage;

        placeholder.getStyleClass().add("placeholder");
        placeholder.setWrapText(true);
        StackPane canvas = new StackPane(viewport, placeholder);
        canvas.getStyleClass().add("canvas");

        name.getStyleClass().add("name");
        Region spring = new Region();
        HBox.setHgrow(spring, Priority.ALWAYS);
        statusBar = new HBox(name, position, dimensions, zoom, spring, shot);
        statusBar.getStyleClass().add("status-bar");
        zoom.textProperty()
                .bind(Bindings.createStringBinding(
                        () -> folder.isEmpty()
                                ? ""
                                : Zoom.label(
                                        viewport.scaleProperty().get(),
                                        viewport.fittedProperty().get()),
                        viewport.scaleProperty(),
                        viewport.fittedProperty()));

        BorderPane root = new BorderPane(canvas);
        root.setBottom(statusBar);
        root.getStyleClass().add("mirada");

        // The palette lays its card and backdrop over everything, the status bar included.
        StackPane overlay = new StackPane(root);
        palette = new CommandPalette(overlay, this::run);

        Scene scene = new Scene(overlay, 1100, 760);
        scene.getStylesheets().add(stylesheet());
        scene.addEventFilter(KeyEvent.KEY_PRESSED, this::onKey);
        scene.setOnDragOver(this::onDragOver);
        scene.setOnDragDropped(this::onDragDropped);
        viewport.setOnContextMenuRequested(e -> contextMenu().show(viewport, e.getScreenX(), e.getScreenY()));

        stage.setScene(scene);
        stage.setMinWidth(480);
        stage.setMinHeight(320);
        stage.setTitle(AppInfo.NAME);
        // Escape is ours (Command.ESCAPE): JavaFX's own exit key would leave full screen and then
        // let the same press reach the filter, which would close the window.
        stage.setFullScreenExitKeyCombination(KeyCombination.NO_MATCH);
        stage.setFullScreenExitHint("");
        stage.fullScreenProperty().addListener((obs, was, now) -> {
            statusBar.setVisible(!now);
            statusBar.setManaged(!now);
            root.pseudoClassStateChanged(FULL_SCREEN, now);
        });
    }

    public void show() {
        stage.show();
    }

    /** Opens a file with its folder behind it, or a folder at its first image. */
    public void open(Path path) {
        try {
            folder = FolderImages.open(path);
        } catch (IOException e) {
            folder = FolderImages.EMPTY;
            showMessage("Couldn't read " + path + ": " + e.getMessage());
            return;
        }
        if (folder.isEmpty()) {
            showMessage("No images in " + path);
            return;
        }
        showCurrent();
    }

    private void showCurrent() {
        Path file = folder.current();
        if (file == null) return;
        wanted = file;
        stage.setTitle(fileName(file) + " — " + AppInfo.NAME);
        name.setText(fileName(file));
        position.setText((folder.index() + 1) + " / " + folder.size());

        if (!ImageFormats.isImage(file)) {
            String extension = ImageFormats.extension(file).toUpperCase(Locale.ROOT);
            showMessage(
                    extension.isEmpty()
                            ? "Mirada can't open " + fileName(file)
                            : "Mirada can't open " + extension + " files yet");
            return;
        }
        ImageCache.Entry entry = cache.get(file);
        entry.ready().thenAcceptAsync(decoded -> display(file, decoded), Platform::runLater);
    }

    private void display(Path file, ImageCache.Decoded decoded) {
        if (!file.equals(wanted)) return;
        Image image = decoded.image();
        if (image == null) {
            showMessage("Couldn't open " + fileName(file) + ": " + decoded.error());
            return;
        }
        placeholder.setVisible(false);
        viewport.show(image, decoded.exif().orientation());
        dimensions.setText(dimensionsText());
        shot.setText(decoded.exif().summary());
        for (Path neighbour : folder.neighbours()) {
            if (ImageFormats.isImage(neighbour)) cache.get(neighbour);
        }
    }

    private void showMessage(String message) {
        viewport.clear();
        placeholder.setText(message);
        placeholder.setVisible(true);
        dimensions.setText("");
        shot.setText("");
        if (folder.isEmpty()) {
            name.setText("");
            position.setText("");
            stage.setTitle(AppInfo.NAME);
        }
    }

    private String dimensionsText() {
        return Math.round(viewport.contentWidth()) + " × " + Math.round(viewport.contentHeight());
    }

    private void onKey(KeyEvent e) {
        Command command = Command.forKey(e.getCode(), e.isShiftDown(), e.isShortcutDown());
        // While the palette is up, keys are its own (typing "r" must not rotate the image); only its
        // chord reaches here, to close it again.
        if (palette.isShowing()) {
            if (command == Command.PALETTE) {
                palette.hide();
                e.consume();
            }
            return;
        }
        if (command == null) return;
        run(command);
        e.consume();
    }

    private void run(Command command) {
        switch (command) {
            case NEXT -> go(folder.step(1));
            case PREVIOUS -> go(folder.step(-1));
            case FIRST -> go(folder.first());
            case LAST -> go(folder.last());
            case ZOOM_IN -> viewport.zoomIn();
            case ZOOM_OUT -> viewport.zoomOut();
            case FIT -> viewport.fit();
            case ACTUAL_SIZE -> viewport.actualSize();
            case ROTATE_CLOCKWISE -> rotate(1);
            case ROTATE_COUNTERCLOCKWISE -> rotate(-1);
            case FULL_SCREEN -> stage.setFullScreen(!stage.isFullScreen());
            case ESCAPE -> {
                if (stage.isFullScreen()) {
                    stage.setFullScreen(false);
                } else {
                    stage.close();
                }
            }
            case OPEN -> chooseFile();
            case CLOSE -> stage.close();
            case PALETTE -> palette.show(Command.inPalette(), this::unavailable);
        }
    }

    /** Why {@code command} cannot run right now, or null when it can. */
    private @Nullable String unavailable(Command command) {
        return command.unavailable(folder.index(), folder.size(), !placeholder.isVisible());
    }

    private void go(FolderImages next) {
        if (next.index() == folder.index() || next.isEmpty()) return;
        folder = next;
        showCurrent();
    }

    private void rotate(int quarterTurns) {
        viewport.rotate(quarterTurns);
        if (!placeholder.isVisible()) dimensions.setText(dimensionsText());
    }

    private void chooseFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Open Image");
        List<String> patterns = ImageFormats.EXTENSIONS.stream()
                .sorted()
                .map(extension -> "*." + extension)
                .toList();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", patterns));
        Path current = folder.current();
        Path parent = current == null ? null : current.getParent();
        if (parent != null) chooser.setInitialDirectory(parent.toFile());
        File chosen = chooser.showOpenDialog(stage);
        if (chosen != null) open(chosen.toPath());
    }

    private void onDragOver(DragEvent e) {
        if (e.getDragboard().hasFiles()) e.acceptTransferModes(TransferMode.COPY_OR_MOVE);
        e.consume();
    }

    private void onDragDropped(DragEvent e) {
        List<File> files = e.getDragboard().getFiles();
        boolean accepted = !files.isEmpty();
        if (accepted) open(files.getFirst().toPath());
        e.setDropCompleted(accepted);
        e.consume();
    }

    private ContextMenu contextMenu() {
        ContextMenu menu = new ContextMenu(
                item(Command.PALETTE),
                new SeparatorMenuItem(),
                item(Command.OPEN),
                new SeparatorMenuItem(),
                item(Command.FIT),
                item(Command.ACTUAL_SIZE),
                item(Command.ROTATE_CLOCKWISE),
                item(Command.ROTATE_COUNTERCLOCKWISE),
                new SeparatorMenuItem(),
                item(Command.FULL_SCREEN));
        menu.setAutoHide(true);
        return menu;
    }

    /** A context-menu row, named as the palette names it and greyed when the palette would grey it. */
    private MenuItem item(Command command) {
        String label = command == Command.FULL_SCREEN && stage.isFullScreen() ? "Leave Full Screen" : command.label();
        MenuItem item = new MenuItem(label);
        item.setOnAction(e -> run(command));
        item.setDisable(unavailable(command) != null);
        return item;
    }

    private static String fileName(Path file) {
        Path name = file.getFileName();
        return name == null ? file.toString() : name.toString();
    }

    private static String stylesheet() {
        return Objects.requireNonNull(ViewerWindow.class.getResource("/com/mirada/styles/mirada.css"), "mirada.css")
                .toExternalForm();
    }
}
