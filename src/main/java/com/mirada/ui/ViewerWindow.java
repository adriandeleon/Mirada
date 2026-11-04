package com.mirada.ui;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToggleGroup;
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
import com.mirada.folder.Sorting;
import com.mirada.image.Animation;
import com.mirada.image.ImageCache;
import com.mirada.image.Orientation;
import com.mirada.os.MacAppMenu;
import com.mirada.os.Trash;
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

    /** Whether Escape closes the window once there is nothing to leave: Linux's habit, not the others'. */
    private static final boolean ESCAPE_CLOSES = Command.escapeCloses(System.getProperty("os.name", ""));

    private final Stage stage;
    private final ImageViewport viewport = new ImageViewport();
    private final Label placeholder =
            new Label("Open an image with " + Command.OPEN.chord().getDisplayText() + ", or drop one here");
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

    private @Nullable ShortcutsWindow shortcutsWindow;

    /** A line over the image saying what a toggle or a file operation did; fades on its own. */
    private final Label toast = new Label();

    private final PauseTransition toastTimer = new PauseTransition(Duration.seconds(2));

    /** Steps to the next image while the slideshow runs; stopped otherwise. */
    private final PauseTransition slideshow = new PauseTransition();

    private boolean slideshowRunning;

    /** The order the open folder is in. */
    private Sorting sortedBy;

    /** The full-resolution image on screen and its orientation, for the editor; null while there is none. */
    private @Nullable Image shownImage;

    private Orientation shownOrientation = Orientation.NORMAL;

    /** Built the first time it opens. */
    private @Nullable Editor editor;

    /** Shows the next frame of the animation on screen; stopped when there is none. */
    private final PauseTransition frameTimer = new PauseTransition();

    private @Nullable Animation playing;
    private int frame;
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
    /** The image whose preview the viewport shows until its full resolution arrives; else null. */
    private @Nullable Path previewing;

    /** @param openLink opens a link or a folder ({@code file:} URI) with the platform's handler */
    public ViewerWindow(Stage stage, Settings settings, Consumer<String> openLink) {
        this.stage = stage;
        this.settings = settings;
        this.openLink = openLink;
        this.tour = Tour.start(() -> go(folder.step(1)));
        this.sortedBy = settings.sorting();

        placeholder.getStyleClass().add("placeholder");
        placeholder.setWrapText(true);
        toast.getStyleClass().add("toast");
        toast.setVisible(false);
        toast.setMouseTransparent(true);
        StackPane.setAlignment(toast, Pos.BOTTOM_CENTER);
        StackPane.setMargin(toast, new Insets(0, 0, 72, 0));
        toastTimer.setOnFinished(e -> toast.setVisible(false));
        slideshow.setOnFinished(e -> slideshowStep());
        frameTimer.setOnFinished(e -> nextFrame());
        viewport.setKeepZoom(settings.keepZoom());
        settings.addListener(() -> {
            viewport.setKeepZoom(settings.keepZoom());
            // From the menu, the palette or the settings window: the open folder follows.
            if (!settings.sorting().equals(sortedBy)) {
                sortedBy = settings.sorting();
                folder = folder.sorted(sortedBy);
                refreshStrip();
            }
        });
        stage.setOnHidden(e -> slideshow.stop());
        canvas = new StackPane(viewport, placeholder, toast);
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
        viewport.setOnSwipe(step -> run(step > 0 ? Command.NEXT : Command.PREVIOUS));
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
        if (Command.MAC) afterFirstPaint(() -> installMacMenus(overlay));
        // A window opened without an image has no first paint to wait for.
        PauseTransition fallback = new PauseTransition(Duration.millis(500));
        fallback.setOnFinished(e -> releaseAfterFirstPaint());
        fallback.play();
        // What the settings open at start; after the listeners above, so they build what they show.
        thumbnailsShowing.set(settings.startWithThumbnails());
        infoShowing.set(settings.startWithInfo());
    }

    /**
     * The menu bar along the top of a Mac's screen, and About and Settings in its application menu.
     * After the first paint, like the rest of the chrome: nobody reaches for a menu before the image.
     */
    private void installMacMenus(StackPane overlay) {
        MenuBar bar = MacMenus.build(
                command -> {
                    // A menu's key equivalent can arrive while the palette is up; the palette goes.
                    palette.hide();
                    run(command);
                },
                this::unavailable,
                this::ticked,
                this::label);
        bar.setManaged(false);
        overlay.getChildren().add(bar);
        String appMenu = MacAppMenu.install(
                Command.ABOUT.label(), () -> run(Command.ABOUT), "Settings…", () -> run(Command.SETTINGS));
        if (Startup.enabled()) System.out.println("[perf] application menu: " + appMenu);
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
            sortedBy = settings.sorting();
            folder = FolderImages.open(path, sortedBy);
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
        ImageCache.Preview preview = entry.preview();
        shownImage = null; // until this one's full resolution is in
        stopAnimation();
        if (preview != null && !entry.ready().isDone()) {
            placeholder.setVisible(false);
            viewport.show(preview.image(), preview.exif().orientation(), preview.width(), preview.height());
            previewing = file;
            updateControls();
        }
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
            if (tour != null) tour.failed(file, decoded.error(), folder.index() == folder.size() - 1);
            return;
        }
        Startup.mark("image-decoded");
        placeholder.setVisible(false);
        // In place of its preview, keeping whatever the user did to it meanwhile.
        if (file.equals(previewing)) viewport.replace(image);
        else viewport.show(image, decoded.exif().orientation());
        previewing = null;
        shownImage = image;
        shownOrientation = decoded.exif().orientation();
        animate(file);
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
        stopAnimation();
        previewing = null;
        shownImage = null;
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
        // The editor's keys are its own while it is open; the viewer's wait.
        Editor open = editor;
        if (open != null && open.isShowing()) {
            if (open.onKey(e)) e.consume();
            return;
        }
        Command command =
                Command.forKey(e.getCode(), e.isShiftDown(), e.isShortcutDown(), e.isControlDown(), Command.MAC);
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
        // From the header's menu while the editor is open: what would change the image under it waits.
        Editor open = editor;
        if (open != null && open.isShowing() && !duringEdit(command)) {
            say("Finish editing first: Save or Cancel");
            return;
        }
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
                if (slideshowRunning) {
                    stopSlideshow();
                } else if (stage.isFullScreen()) {
                    stage.setFullScreen(false);
                } else if (ESCAPE_CLOSES) {
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
            case SHORTCUTS -> shortcutsWindow().show(stage);
            case PAN_LEFT -> viewport.pan(-PAN_STEP, 0);
            case PAN_RIGHT -> viewport.pan(PAN_STEP, 0);
            case PAN_UP -> viewport.pan(0, -PAN_STEP);
            case PAN_DOWN -> viewport.pan(0, PAN_STEP);
            case KEEP_ZOOM -> {
                settings.setKeepZoom(!settings.keepZoom());
                say(settings.keepZoom() ? "Keeping the zoom when stepping" : "Fitting each image");
            }
            case SLIDESHOW -> {
                if (slideshowRunning) stopSlideshow();
                else startSlideshow();
            }
            case EDIT -> edit();
            case RENAME -> rename();
            case MOVE_TO_TRASH -> moveToTrash();
            case SORT_BY_NAME -> sort(settings.sorting().by(Sorting.By.NAME));
            case SORT_BY_DATE -> sort(settings.sorting().by(Sorting.By.DATE));
            case SORT_BY_SIZE -> sort(settings.sorting().by(Sorting.By.SIZE));
            case REVERSE_ORDER -> sort(settings.sorting().reverse());
        }
    }

    /** The commands that leave the image alone, so they can run while the editor is open. */
    private static boolean duringEdit(Command command) {
        return switch (command) {
            case FULL_SCREEN, INFO, THUMBNAILS, SETTINGS, SHORTCUTS, ABOUT, CLOSE -> true;
            default -> false;
        };
    }

    /** How far one press of a panning key moves the view: a tenth of the window. */
    private static final double PAN_STEP = 0.1;

    /** Shows {@code message} over the image for two seconds. */
    private void say(String message) {
        toast.setText(message);
        toast.setVisible(true);
        toastTimer.playFromStart();
    }

    private void startSlideshow() {
        if (folder.size() < 2) return;
        slideshowRunning = true;
        stage.setFullScreen(true);
        say("Slideshow: every " + settings.slideshowSeconds() + " s. Escape stops it.");
        restartSlideshowTimer();
    }

    private void stopSlideshow() {
        slideshowRunning = false;
        slideshow.stop();
        stage.setFullScreen(false);
        say("Slideshow stopped");
    }

    private void restartSlideshowTimer() {
        if (!slideshowRunning) return;
        slideshow.setDuration(Duration.seconds(settings.slideshowSeconds()));
        slideshow.playFromStart();
    }

    /** The next image, or the first again after the last: a slideshow goes round. */
    private void slideshowStep() {
        if (!slideshowRunning) return;
        go(folder.index() >= folder.size() - 1 ? folder.first() : folder.step(1));
        restartSlideshowTimer();
    }

    private void sort(Sorting sorting) {
        settings.setSorting(sorting); // the settings listener re-sorts the folder
        String by =
                switch (sorting.by()) {
                    case NAME -> "name";
                    case DATE -> "date";
                    case SIZE -> "size";
                };
        say("Sorted by " + by + (sorting.reversed() ? ", reversed" : ""));
    }

    /**
     * Asks for a new name, in the same folder, and renames the file. The extension is kept unless the
     * new name gives one; an existing file is never replaced.
     */
    private void rename() {
        Path file = folder.current();
        if (file == null) return;
        String old = fileName(file);
        TextInputDialog dialog = new TextInputDialog(old);
        dialog.initOwner(stage);
        dialog.setTitle("Rename");
        dialog.setHeaderText("Rename " + old);
        dialog.setContentText("New name:");
        dialog.getDialogPane().getStylesheets().add(stylesheet());
        // The name without its extension selected, ready to be typed over.
        Platform.runLater(() -> dialog.getEditor().selectRange(0, Renaming.stemLength(old)));
        Optional<String> answer = dialog.showAndWait();
        if (answer.isEmpty()) return;
        String name;
        try {
            name = Renaming.validName(old, answer.get());
        } catch (IllegalArgumentException e) {
            say("Not renamed: " + e.getMessage());
            return;
        }
        if (name.equals(old)) return;
        Path target = file.resolveSibling(name);
        try {
            Files.move(file, target);
        } catch (FileAlreadyExistsException e) {
            say("Not renamed: " + name + " already exists");
            return;
        } catch (IOException e) {
            say("Not renamed: " + e.getMessage());
            return;
        }
        folder = folder.renamed(file, target, settings.sorting());
        showCurrent();
        say("Renamed to " + name);
    }

    /** Starts {@code file} moving once its frames are decoded, if it is an animation still on screen. */
    private void animate(Path file) {
        cache.animation(file)
                .thenAcceptAsync(
                        animation -> {
                            if (animation == null || !file.equals(wanted) || !file.equals(folder.current())) return;
                            playing = animation;
                            frame = 0;
                            frameTimer.setDuration(Duration.millis(animation.millis()[0]));
                            frameTimer.playFromStart();
                        },
                        Platform::runLater);
    }

    private void nextFrame() {
        Animation animation = playing;
        if (animation == null) return;
        frame = (frame + 1) % animation.frames().size();
        viewport.replace(animation.frames().get(frame));
        frameTimer.setDuration(Duration.millis(animation.millis()[frame]));
        frameTimer.playFromStart();
    }

    private void stopAnimation() {
        playing = null;
        frameTimer.stop();
    }

    /** Opens the editor on the image showing, once its full resolution is in. */
    private void edit() {
        stopAnimation();
        Image image = shownImage;
        Path file = folder.current();
        if (image == null || file == null) {
            say("The image is still loading");
            return;
        }
        Editor made = editor;
        if (made == null) {
            made = new Editor(root, canvas, this::say);
            editor = made;
        }
        made.open(image, shownOrientation, file, saved -> {
            open(saved);
            say("Saved " + fileName(saved));
        });
    }

    /** Off the FX thread: a file on another drive goes through {@code gio}, which can take a moment. */
    private void moveToTrash() {
        Path file = folder.current();
        if (file == null) return;
        Thread.ofVirtual().name("trash").start(() -> {
            try {
                Trash.move(file);
                Platform.runLater(() -> {
                    FolderImages rest = folder.without(file);
                    folder = rest;
                    say("Moved " + fileName(file) + " to the Trash");
                    if (rest.isEmpty()) showMessage("No images left in this folder");
                    else showCurrent();
                });
            } catch (IOException | RuntimeException e) {
                Platform.runLater(() -> say("Couldn't move " + fileName(file) + " to the Trash: " + e.getMessage()));
            }
        });
    }

    private ShortcutsWindow shortcutsWindow() {
        ShortcutsWindow made = shortcutsWindow;
        if (made == null) {
            made = new ShortcutsWindow(stylesheet());
            shortcutsWindow = made;
        }
        return made;
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
        // A step by hand during a slideshow gives the new image its full time.
        restartSlideshowTimer();
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
                item(Command.RENAME),
                item(Command.MOVE_TO_TRASH),
                new SeparatorMenuItem(),
                sortMenu(),
                item(Command.INFO),
                item(Command.THUMBNAILS),
                item(Command.FULL_SCREEN),
                item(Command.SLIDESHOW));
        menu.setAutoHide(true);
        return menu;
    }

    /** Sort by name, date or size, the current one ticked, and the order's direction. */
    private Menu sortMenu() {
        Sorting sorting = settings.sorting();
        ToggleGroup group = new ToggleGroup();
        Menu menu = new Menu("Sort");
        for (Command command : List.of(Command.SORT_BY_NAME, Command.SORT_BY_DATE, Command.SORT_BY_SIZE)) {
            RadioMenuItem item = new RadioMenuItem(command.label());
            item.setToggleGroup(group);
            item.setSelected(
                    switch (command) {
                        case SORT_BY_DATE -> sorting.by() == Sorting.By.DATE;
                        case SORT_BY_SIZE -> sorting.by() == Sorting.By.SIZE;
                        default -> sorting.by() == Sorting.By.NAME;
                    });
            item.setOnAction(e -> run(command));
            menu.getItems().add(item);
        }
        CheckMenuItem reversed = new CheckMenuItem(Command.REVERSE_ORDER.label());
        reversed.setSelected(sorting.reversed());
        reversed.setOnAction(e -> run(Command.REVERSE_ORDER));
        menu.getItems().addAll(new SeparatorMenuItem(), reversed);
        menu.setDisable(folder.isEmpty());
        return menu;
    }

    /** A context-menu row, named as the palette names it and greyed when the palette would grey it. */
    private MenuItem item(Command command) {
        MenuItem item = new MenuItem(label(command));
        item.setOnAction(e -> run(command));
        item.setDisable(unavailable(command) != null);
        return item;
    }

    /** What a menu calls {@code command} now: its label, but for full screen while in it. */
    private String label(Command command) {
        return command == Command.FULL_SCREEN && stage.isFullScreen() ? "Leave Full Screen" : command.label();
    }

    /** Whether a setting or panel the Mac menus tick ({@link MacMenus#TICKED}) is on. */
    private boolean ticked(Command command) {
        Sorting sorting = settings.sorting();
        return switch (command) {
            case KEEP_ZOOM -> settings.keepZoom();
            case INFO -> infoShowing.get();
            case THUMBNAILS -> thumbnailsShowing.get();
            case SORT_BY_NAME -> sorting.by() == Sorting.By.NAME;
            case SORT_BY_DATE -> sorting.by() == Sorting.By.DATE;
            case SORT_BY_SIZE -> sorting.by() == Sorting.By.SIZE;
            case REVERSE_ORDER -> sorting.reversed();
            default -> false;
        };
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
