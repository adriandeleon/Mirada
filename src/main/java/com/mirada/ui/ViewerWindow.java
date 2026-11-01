package com.mirada.ui;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
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
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import com.mirada.AppInfo;
import com.mirada.folder.FolderImages;
import com.mirada.folder.ImageFormats;
import com.mirada.image.ImageCache;
import com.mirada.perf.Startup;
import com.mirada.perf.Tour;
import com.mirada.settings.Settings;
import org.jspecify.annotations.Nullable;

/**
 * The one window, laid out as GNOME Image Viewer's: a header bar with the file name ({@link
 * TitleBar}), the image with floating controls over it ({@link OnScreenControls}), and an info
 * sidebar that opens beside it ({@link InfoPanel}). Opening a file loads its folder; the arrow keys
 * walk it.
 *
 * <p>Stepping keeps the previous image on screen until the next one is decoded and its EXIF read,
 * so the window never flashes empty or shows a photo on its side before turning it. The neighbours
 * either side are loaded once the current image is up, so the next step is usually instant.
 */
public final class ViewerWindow {

    private static final PseudoClass FULL_SCREEN = PseudoClass.getPseudoClass("full-screen");
    /** Reads the sidebar's details; a virtual thread per read, as the metadata reads in ImageCache. */
    private static final Executor DETAILS_READER = Executors.newVirtualThreadPerTaskExecutor();

    private static final String EMPTY_HINT = "Open an image with Ctrl+O, or drop one here";

    private final Stage stage;
    private final ImageViewport viewport = new ImageViewport();
    private final Label placeholder = new Label(EMPTY_HINT);
    private final BorderPane root;
    private final TitleBar titleBar;
    /** Built on the first pointer movement over the image: hidden until then anyway. */
    private @Nullable OnScreenControls controls;

    private final StackPane canvas;
    /** What waits until the first image is on screen (or half a second has passed without one). */
    private @Nullable List<Runnable> afterFirstPaint = new ArrayList<>();

    private final BooleanProperty infoShowing = new SimpleBooleanProperty(this, "infoShowing", false);
    private final Settings settings;
    private final Consumer<String> openLink;
    /** Built the first time each is opened. */
    private @Nullable AboutWindow about;

    private @Nullable SettingsWindow settingsWindow;
    /** Built the first time the sidebar opens. */
    private @Nullable InfoPanel info;

    private final BooleanProperty thumbnailsShowing = new SimpleBooleanProperty(this, "thumbnailsShowing", false);
    /** Built the first time the strip opens. */
    private @Nullable ThumbnailStrip strip;

    private final ImageCache cache = new ImageCache(3);
    private final CommandPalette palette;
    /** Steps through the folder on its own when measuring or training the AOT cache; else null. */
    private final @Nullable Tour tour;

    private FolderImages folder = FolderImages.EMPTY;
    /** The image asked for most recently; a load that finishes for any other is dropped. */
    private @Nullable Path wanted;

    /** @param openLink opens a link or a folder ({@code file:} URI) with the platform's handler */
    public ViewerWindow(Stage stage, Settings settings, Consumer<String> openLink) {
        this.stage = stage;
        this.settings = settings;
        this.openLink = openLink;
        this.tour = Tour.start(() -> go(folder.step(1)));

        placeholder.getStyleClass().add("placeholder");
        placeholder.setWrapText(true);
        canvas = new StackPane(viewport, placeholder);
        canvas.getStyleClass().add("canvas");
        canvas.addEventFilter(MouseEvent.MOUSE_MOVED, e -> {
            if (controls == null) onScreenControls().wake();
        });
        settings.addListener(() -> {
            OnScreenControls built = controls;
            if (built != null) built.setIdle(settings.controlsHideSeconds());
        });
        viewport.fittedProperty().addListener((o, was, now) -> updateControls());

        root = new BorderPane(canvas);
        root.getStyleClass().add("mirada");
        titleBar = new TitleBar(stage, this::run, infoShowing);
        root.setTop(titleBar.node());
        infoShowing.addListener((o, was, now) -> {
            root.setRight(now ? infoPanel().node() : null);
            refreshInfo();
        });
        thumbnailsShowing.addListener((o, was, now) -> {
            root.setBottom(now ? thumbnailStrip().node() : null);
            refreshStrip();
        });

        // The palette lays its card and backdrop over everything, the header included.
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
            titleBar.fullScreen(root, now);
            root.pseudoClassStateChanged(FULL_SCREEN, now);
        });
        updateControls();
        afterFirstPaint(titleBar::finish);
        afterFirstPaint(() -> WindowIcon.applyTo(stage));
        // A window opened without an image has no first paint to wait for.
        PauseTransition fallback = new PauseTransition(Duration.millis(500));
        fallback.setOnFinished(e -> releaseAfterFirstPaint());
        fallback.play();
        // What the settings open at start; after the listeners above, so they build what they show.
        thumbnailsShowing.set(settings.startWithThumbnails());
        infoShowing.set(settings.startWithInfo());
    }

    private void afterFirstPaint(Runnable task) {
        List<Runnable> waiting = afterFirstPaint;
        if (waiting == null) {
            task.run();
        } else {
            waiting.add(task);
        }
    }

    /**
     * Runs what was waiting, once. Called on the pulse after the first image is shown, so the frame
     * with the image is drawn before any of it.
     */
    private void releaseAfterFirstPaint() {
        List<Runnable> waiting = afterFirstPaint;
        if (waiting == null) return;
        afterFirstPaint = null;
        waiting.forEach(Runnable::run);
    }

    private OnScreenControls onScreenControls() {
        OnScreenControls built = controls;
        if (built == null) {
            built = new OnScreenControls(canvas, this::run);
            built.setIdle(settings.controlsHideSeconds());
            controls = built;
            updateControls();
        }
        return built;
    }

    private InfoPanel infoPanel() {
        InfoPanel panel = info;
        if (panel == null) {
            panel = new InfoPanel(folder -> openLink.accept(folder.toUri().toString()));
            info = panel;
        }
        return panel;
    }

    private ThumbnailStrip thumbnailStrip() {
        ThumbnailStrip made = strip;
        if (made == null) {
            made = new ThumbnailStrip(index -> go(folder.jump(index)));
            strip = made;
        }
        return made;
    }

    /** The strip, when it is open, on the folder's current image. */
    private void refreshStrip() {
        ThumbnailStrip shown = strip;
        if (shown != null && thumbnailsShowing.get()) shown.show(folder.images(), folder.index());
    }

    /**
     * The sidebar's contents, when it is open: details of the image on screen, read off the FX thread
     * (a stat and a metadata walk) and shown if that image is still the one on screen.
     */
    private void refreshInfo() {
        InfoPanel panel = info;
        if (panel == null || !infoShowing.get()) return;
        Path file = folder.current();
        if (file == null || placeholder.isVisible()) {
            panel.show(null);
            return;
        }
        int index = folder.index();
        int count = folder.size();
        double width = viewport.contentWidth();
        double height = viewport.contentHeight();
        CompletableFuture.supplyAsync(() -> ImageDetails.read(file, index, count, width, height), DETAILS_READER)
                .thenAcceptAsync(
                        details -> {
                            if (file.equals(wanted) && infoShowing.get()) panel.show(details);
                        },
                        Platform::runLater);
    }

    private void updateControls() {
        boolean showing = !placeholder.isVisible() && !folder.isEmpty();
        OnScreenControls built = controls;
        if (built != null)
            built.update(
                    folder.index(),
                    folder.size(),
                    showing,
                    viewport.fittedProperty().get());
        titleBar.setImageShowing(showing);
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
        titleBar.setTitle(fileName(file));
        refreshStrip();

        if (!ImageFormats.isImage(file)) {
            String extension = ImageFormats.extension(file).toUpperCase(Locale.ROOT);
            showMessage(
                    extension.isEmpty()
                            ? "Mirada can't open " + fileName(file)
                            : "Mirada can't open " + extension + " files yet");
            return;
        }
        ImageCache.Entry entry = cache.get(file);
        if (tour != null) tour.requested(entry.ready().isDone());
        entry.ready()
                .thenAcceptAsync(decoded -> display(file, decoded), Platform::runLater)
                .exceptionally(error -> {
                    // ready() completes normally even for a broken file; landing here means something
                    // unforeseen went wrong on the way, and silence would leave the old image up.
                    System.err.println("[mirada] could not show " + file + ": " + error);
                    error.printStackTrace();
                    Platform.runLater(() -> {
                        if (file.equals(wanted)) showMessage("Couldn't open " + fileName(file) + ": " + error);
                    });
                    return null;
                });
    }

    private void display(Path file, ImageCache.Decoded decoded) {
        if (!file.equals(wanted)) return;
        Image image = decoded.image();
        if (image == null) {
            // On stderr too: the window says it to the user, this is for whoever has to find out why.
            System.err.println("[mirada] could not open " + file + ": " + decoded.error());
            showMessage("Couldn't open " + fileName(file) + ": " + decoded.error());
            return;
        }
        Startup.mark("image-decoded");
        placeholder.setVisible(false);
        viewport.show(image, decoded.exif().orientation());
        Startup.markFirstPaint();
        Platform.runLater(() -> Platform.runLater(this::releaseAfterFirstPaint));
        if (tour != null) tour.shown(file, folder.index() == folder.size() - 1);
        updateControls();
        refreshInfo();
        for (Path neighbour : folder.neighbours()) {
            if (ImageFormats.isImage(neighbour)) cache.get(neighbour);
        }
    }

    private void showMessage(String message) {
        viewport.clear();
        placeholder.setText(message);
        placeholder.setVisible(true);
        if (folder.isEmpty()) {
            titleBar.setTitle(AppInfo.NAME);
            stage.setTitle(AppInfo.NAME);
        }
        updateControls();
        refreshInfo();
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
            case INFO -> infoShowing.set(!infoShowing.get());
            case THUMBNAILS -> thumbnailsShowing.set(!thumbnailsShowing.get());
            case SETTINGS -> settingsWindow().show(stage);
            case ABOUT -> aboutWindow().show(stage);
        }
    }

    private SettingsWindow settingsWindow() {
        SettingsWindow made = settingsWindow;
        if (made == null) {
            made = new SettingsWindow(settings, stylesheet());
            settingsWindow = made;
        }
        return made;
    }

    private AboutWindow aboutWindow() {
        AboutWindow made = about;
        if (made == null) {
            made = new AboutWindow(settings.file(), openLink, stylesheet());
            about = made;
        }
        return made;
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
        refreshInfo();
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
                item(Command.INFO),
                item(Command.THUMBNAILS),
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

    static String stylesheet() {
        return Objects.requireNonNull(ViewerWindow.class.getResource("/com/mirada/styles/mirada.css"), "mirada.css")
                .toExternalForm();
    }
}
