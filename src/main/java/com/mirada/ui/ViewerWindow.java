package com.mirada.ui;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.css.PseudoClass;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DragEvent;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.util.Duration;

import com.mirada.AppInfo;
import com.mirada.diagnostics.DebugLog;
import com.mirada.diagnostics.MessageLog;
import com.mirada.edit.Edit;
import com.mirada.edit.Raster;
import com.mirada.folder.FolderImages;
import com.mirada.folder.ImageFormats;
import com.mirada.folder.Sorting;
import com.mirada.image.Animation;
import com.mirada.image.ImageCache;
import com.mirada.image.Orientation;
import com.mirada.os.MacAppMenu;
import com.mirada.os.Reveal;
import com.mirada.os.Trash;
import com.mirada.perf.Startup;
import com.mirada.perf.Tour;
import com.mirada.report.Redactor;
import com.mirada.settings.Bookmarks;
import com.mirada.settings.FileWrites;
import com.mirada.settings.Settings;
import com.mirada.update.UpdateCoordinator;
import com.mirada.update.UpdateService;
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
    /**
     * Where the window's file work runs, never on the FX thread (PERF-01): listing a folder, the stats
     * a sort by date or size reads, the sidebar's details, whether a bookmark's file is still there. A
     * named virtual thread per task, as the metadata reads in ImageCache.
     */
    static final Executor FILES = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("window-files-", 0).factory());

    /** Whether Escape closes the window once there is nothing to leave: Linux's habit, not the others'. */
    private static final boolean ESCAPE_CLOSES = Command.escapeCloses(System.getProperty("os.name", ""));

    private final Stage stage;
    private final ImageViewport viewport = new ImageViewport();
    /** Says how to open an image, or why the one asked for is not showing; its first words wait for {@link #offerToOpen}. */
    private final Label placeholder = new Label();
    /** The placeholder, with Open Image and Open Folder under it while no folder is open. */
    private final VBox emptyState = new VBox(placeholder);
    /** Those two buttons, built the first time the window has no folder to show. */
    private @Nullable HBox openButtons;

    private final BorderPane root;
    private final TitleBar titleBar;
    /** Built on the first pointer movement over the image: hidden until then anyway. */
    private @Nullable OnScreenControls controls;

    private final StackPane canvas;
    /** What waits until the first image is on screen (or half a second has passed without one). */
    private @Nullable List<Runnable> afterFirstPaint = new ArrayList<>();

    private final BooleanProperty infoShowing = new SimpleBooleanProperty(this, "infoShowing", false);
    private final Settings settings;
    /** The user's bookmarks and notes; read after the first paint, or when first asked for. */
    private final Bookmarks bookmarks;
    /** The image's marks over its corner; built the first time an image on screen has one. */
    private @Nullable MarksBadge marksBadge;

    private final Consumer<String> openLink;
    /** Built the first time each is opened. */
    private @Nullable AboutWindow about;

    private @Nullable SettingsWindow settingsWindow;
    /** The Mac's menu bar, once it is built after the first paint; its palette key follows the setting. */
    private @Nullable MenuBar macMenus;

    private @Nullable ShortcutsWindow shortcutsWindow;
    /** Built the first time it is asked for. */
    private @Nullable BookmarksWindow bookmarksWindow;

    /** Asked for when something is logged: starting the logging system is no part of start-up. */
    private static Logger log() {
        return Logger.getLogger(ViewerWindow.class.getName());
    }

    /** Everything the status bar has said this session (DIAG-01). */
    private final MessageLog messages = new MessageLog();
    /** The update check, once this run (DIAG-05). */
    private final UpdateCoordinator updates;
    /** Along the bottom, under the strip when that is there too (WIN-01). */
    private final StatusBar statusBar;
    /** The strip, when it runs along the bottom, then the status bar. */
    private final VBox bottom = new VBox();

    private @Nullable MessageLogView messageLog;
    private @Nullable DebugLogWindow debugLog;

    /**
     * What the status bar would say, over the image instead while the bar is away (full screen, a
     * slideshow, or switched off); fades on its own.
     */
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
    /** The window's size and place when last neither maximised nor full screen. */
    private @Nullable Rectangle2D normalPlace;
    /** The pointer's rest before it hides in full screen ({@link #showPointer}). */
    private final PauseTransition pointerIdle = new PauseTransition();

    private boolean remembered;
    /** What Move to Trash can put back, the latest first; only Linux's trash says where a file went. */
    private final Deque<Trash.Trashed> trashed = new ArrayDeque<>();

    static final int UNDO_DEPTH = 20;

    /** Lists the folder being opened; a later open drops an earlier one's listing (PERF-03). */
    private final LatestOnly listing = new LatestOnly(FILES, Platform::runLater);
    /** Sorts the open folder by date or size, reading every file's stats; the latest order only. */
    private final LatestOnly sorting = new LatestOnly(FILES, Platform::runLater);
    /** Reads the sidebar's details of the image on screen; only the latest read is shown. */
    private final LatestOnly details = new LatestOnly(FILES, Platform::runLater);
    /** Finds which bookmarks or recent folders are still there, for the palette; the latest only. */
    private final LatestOnly lists = new LatestOnly(FILES, Platform::runLater);

    /** The tool windows this window has opened: About, Settings and the rest, closed with it. */
    private final List<Stage> toolWindows = new ArrayList<>();
    /** The image whose preview the viewport shows until its full resolution arrives; else null. */
    private @Nullable Path previewing;

    /** @param openLink opens a link or a folder ({@code file:} URI) with the platform's handler */
    public ViewerWindow(Stage stage, Settings settings, Consumer<String> openLink) {
        this.stage = stage;
        this.settings = settings;
        this.openLink = openLink;
        this.tour = Tour.start(() -> go(folder.step(1)), this::copy);
        this.sortedBy = settings.sorting();
        // Read and written where the settings are, off the FX thread in the application.
        FileWrites writes = settings.writes();
        this.bookmarks = new Bookmarks(
                Bookmarks.fileBeside(settings.file()), writes, writes.isDirect() ? Runnable::run : Platform::runLater);
        bookmarks.addListener(() -> {
            showMarks();
            refreshInfo();
        });

        updates = new UpdateCoordinator(
                settings,
                // Made when it is first asked: HttpClient is nothing start-up should load.
                version -> new UpdateService().check(version),
                (delay, task) -> {
                    PauseTransition wait = new PauseTransition(Duration.millis(delay.toMillis()));
                    wait.setOnFinished(e -> task.run());
                    wait.play();
                },
                task -> Thread.ofVirtual().name("update-check").start(task),
                Platform::runLater,
                this::say,
                System::currentTimeMillis);
        statusBar = new StatusBar(messages, updates, this::run);

        placeholder.getStyleClass().add("placeholder");
        placeholder.setWrapText(true);
        emptyState.getStyleClass().add("empty-state");
        emptyState.setAlignment(Pos.CENTER);
        emptyState.visibleProperty().bind(placeholder.visibleProperty());
        // Clicks beside the label and the buttons reach the canvas, for its context menu.
        emptyState.setPickOnBounds(false);
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
            if (strip != null) placeStrip();
            // From the menu, the palette or the settings window: the open folder follows.
            if (!settings.sorting().equals(sortedBy)) {
                sortedBy = settings.sorting();
                sortFolder();
            }
        });
        // The window's own close button: an edit not yet saved asks first (CONF-01).
        stage.setOnCloseRequest(e -> {
            if (!mayClose()) e.consume();
        });
        stage.setOnHidden(e -> {
            slideshow.stop();
            rememberPlace();
            release();
        });
        canvas = new StackPane(viewport, emptyState, toast);
        canvas.getStyleClass().add("canvas");
        canvas.addEventFilter(MouseEvent.MOUSE_MOVED, e -> {
            if (controls == null) onScreenControls().wake();
            showPointer();
        });
        canvas.addEventFilter(MouseEvent.MOUSE_DRAGGED, e -> showPointer());
        pointerIdle.setOnFinished(e -> {
            if (stage.isFullScreen()) canvas.setCursor(Cursor.NONE);
        });
        settings.addListener(() -> {
            OnScreenControls built = controls;
            if (built != null) built.setIdle(settings.controlsHideSeconds());
        });
        // Nothing is read here: the keymap is parsed when a key is first pressed or a chord first shown.
        Keybindings.use(settings.keymap(), settings.keys(), settings.paletteKeys());
        settings.addListener(this::followKeys);
        viewport.fittedProperty().addListener((o, was, now) -> updateControls());

        root = new BorderPane(canvas);
        root.getStyleClass().add("mirada");
        bottom.getChildren().add(statusBar.node());
        root.setBottom(bottom);
        showStatusBar();
        settings.addListener(this::showStatusBar);
        // The menu's tick and the setting, kept in step both ways.
        BooleanProperty statusBarOn = new SimpleBooleanProperty(this, "statusBarOn", settings.statusBar());
        statusBarOn.addListener((o, was, now) -> {
            if (now != settings.statusBar()) settings.setStatusBar(now);
        });
        settings.addListener(() -> statusBarOn.set(settings.statusBar()));
        titleBar = new TitleBar(stage, this::run, infoShowing, thumbnailsShowing, statusBarOn);
        root.setTop(titleBar.node());
        infoShowing.addListener((o, was, now) -> {
            root.setRight(now ? infoPanel().node() : null);
            refreshInfo();
        });
        thumbnailsShowing.addListener((o, was, now) -> placeStrip());

        // The palette lays its card and backdrop over everything, the header included.
        StackPane overlay = new StackPane(root);
        palette = new CommandPalette(overlay, this::run);

        Scene scene = new Scene(overlay, 1100, 760);
        scene.getStylesheets().add(stylesheet());
        scene.addEventFilter(KeyEvent.KEY_PRESSED, this::onKey);
        Popups.closeOnPressOutside(stage, scene);
        Popups.menusByKey(scene);
        scene.setOnDragOver(this::onDragOver);
        scene.setOnDragDropped(this::onDragDropped);
        viewport.setOnSwipe(step -> run(step > 0 ? Command.NEXT : Command.PREVIOUS));
        viewport.setOnDragOut(() -> folder.current());
        viewport.setOnContextMenuRequested(e -> Popups.show(contextMenu(), viewport, e.getScreenX(), e.getScreenY()));

        stage.setScene(scene);
        stage.setMinWidth(480);
        stage.setMinHeight(320);
        restorePlace();
        stage.setTitle(AppInfo.NAME);
        // Escape is ours (Command.ESCAPE): JavaFX's own exit key would leave full screen and then
        // let the same press reach the filter, which would close the window.
        stage.setFullScreenExitKeyCombination(KeyCombination.NO_MATCH);
        stage.setFullScreenExitHint("");
        stage.fullScreenProperty().addListener((obs, was, now) -> {
            titleBar.fullScreen(root, now);
            Icons.setFullScreen(now);
            OnScreenControls built = controls;
            if (built != null) built.setFullScreen(now);
            root.pseudoClassStateChanged(FULL_SCREEN, now);
            showStatusBar();
            showPointer();
        });
        updateControls();
        afterFirstPaint(titleBar::finish);
        afterFirstPaint(statusBar::finish);
        // A few seconds after the image, never on the way to it; not on a timing or training tour.
        if (tour == null) afterFirstPaint(updates::scheduleStartupCheck);
        afterFirstPaint(() -> WindowIcon.load(FILES, Platform::runLater, () -> WindowIcon.applyTo(stage)));
        // What a first key press, About or Copy Details would read: read now, off the FX thread.
        afterFirstPaint(() -> {
            String keymap = settings.keymap();
            FILES.execute(() -> {
                Keybindings.preload(keymap);
                AppInfo.runtime();
            });
        });
        // The marks are not start-up's: read once the image is up, then shown on it.
        afterFirstPaint(() -> {
            bookmarks.ensureLoaded();
            showMarks();
            refreshInfo();
        });
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
                    // A menu's key equivalent can arrive while the palette is up; the palette goes,
                    // and the palette's own key only closes it, as it does from the key filter.
                    boolean wasShowing = palette.isShowing();
                    palette.hide();
                    if (command != Command.PALETTE || !wasShowing) run(command);
                },
                this::unavailable,
                this::ticked,
                this::label);
        bar.setManaged(false);
        overlay.getChildren().add(bar);
        macMenus = bar;
        String appMenu = MacAppMenu.install(
                Command.ABOUT.label(), () -> run(Command.ABOUT), "Settings…", () -> run(Command.SETTINGS));
        if (Startup.enabled()) System.out.println("[perf] application menu: " + appMenu);
    }

    /** Follows the settings' keys: the keymap, the user's, the palette's; what shows them reads them again. */
    private void followKeys() {
        if (!Keybindings.use(settings.keymap(), settings.keys(), settings.paletteKeys())) return;
        palette.refreshKeys();
        BookmarksWindow marks = bookmarksWindow;
        if (marks != null) marks.refreshKeys();
        // The Mac menus write their chords as they open; the buttons' tooltips are written now.
        relabelButtons();
        ShortcutsWindow shortcuts = shortcutsWindow;
        if (shortcuts != null) shortcuts.refresh();
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
            built.setVertical(Command.vertical());
            built.setFullScreen(stage.isFullScreen());
            controls = built;
            updateControls();
        }
        return built;
    }

    private InfoPanel infoPanel() {
        InfoPanel panel = info;
        if (panel == null) {
            panel = new InfoPanel(this::run);
            info = panel;
        }
        return panel;
    }

    private ThumbnailStrip thumbnailStrip() {
        ThumbnailStrip made = strip;
        if (made == null) {
            made = new ThumbnailStrip(index -> go(folder.jump(index)), viewport.thumbnailDragger());
            made.setMarked(file -> bookmarks.loaded() && bookmarks.isBookmarked(file));
            made.setMenu(this::stripMenu);
            strip = made;
        }
        return made;
    }

    /**
     * Puts the strip where the settings say, along the bottom or down the left, laid out for
     * pictures or names, or takes it away when it is closed. Building it waits for its first opening.
     */
    private void placeStrip() {
        stepAlong(thumbnailsShowing.get() && settings.thumbnailsAtSide());
        ThumbnailStrip built = strip;
        if (!thumbnailsShowing.get()) {
            if (built != null) {
                bottom.getChildren().remove(built.node());
                if (root.getLeft() == built.node()) root.setLeft(null);
            }
            return;
        }
        ThumbnailStrip shown = thumbnailStrip();
        boolean side = settings.thumbnailsAtSide();
        shown.setLayout(side, settings.thumbnailNames(), settings.thumbnailCaptions());
        ThumbnailStrip.place(root, bottom, shown.node(), side);
        refreshStrip();
    }

    /**
     * Steps the folder along the strip: Up and Down, with up and down arrows over the image, while
     * it stands down the side; Left and Right otherwise ({@link Command#vertical()}).
     */
    private void stepAlong(boolean vertical) {
        if (vertical == Command.vertical()) return;
        Command.setVertical(vertical);
        OnScreenControls built = controls;
        if (built != null) built.setVertical(vertical);
        relabelButtons();
        ShortcutsWindow shortcuts = shortcutsWindow;
        if (shortcuts != null) shortcuts.refresh();
    }

    /** The strip's own menu: closing it, and where it stands and what it shows. */
    private ContextMenu stripMenu() {
        MenuItem hide = new MenuItem("Hide Thumbnails", Icons.menu(Command.THUMBNAILS));
        hide.setOnAction(e -> run(Command.THUMBNAILS));
        MenuChord.on(hide, Command.THUMBNAILS);
        ContextMenu menu = new ContextMenu(
                hide,
                new SeparatorMenuItem(),
                item(Command.THUMBNAILS_AT_SIDE),
                item(Command.THUMBNAIL_CAPTIONS),
                item(Command.THUMBNAIL_NAMES));
        Popups.closedByWindow(menu);
        return menu;
    }

    /**
     * The open folder in {@link #sortedBy}'s order: sorted off the FX thread, as a sort by date or size
     * reads every file's stats, then applied to the folder as it is by then ({@link
     * FolderImages#reordered}), on the image showing.
     */
    private void sortFolder() {
        List<Path> images = folder.images();
        Sorting order = sortedBy;
        sorting.submit(() -> order.sort(images), sorted -> {
            folder = folder.reordered(sorted);
            refreshStrip();
            updateControls();
            refreshInfo();
        });
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
            details.cancel();
            panel.show(null, InfoPanel.Marks.NONE);
            return;
        }
        InfoPanel.Marks marks = marks(file);
        int index = folder.index();
        int count = folder.size();
        double width = viewport.contentWidth();
        double height = viewport.contentHeight();
        // Only the latest read is shown: an earlier one for this image, turned since, or for the
        // image before, is dropped however late it comes (PERF-03).
        details.submit(() -> ImageDetails.read(file, index, count, width, height), read -> {
            if (file.equals(wanted) && infoShowing.get()) panel.show(read, marks);
        });
    }

    /** What the user keeps on {@code file} and its folder, for the sidebar; none until they are read. */
    private InfoPanel.Marks marks(Path file) {
        if (!bookmarks.loaded()) return InfoPanel.Marks.NONE;
        Path parent = file.toAbsolutePath().getParent();
        return new InfoPanel.Marks(
                bookmarks.isBookmarked(file),
                bookmarks.note(file),
                parent != null && bookmarks.isBookmarked(parent),
                parent == null ? "" : bookmarks.note(parent));
    }

    /**
     * The marks of the image on screen over its corner, and the strip's badges. Nothing before the
     * marks are read (after the first paint), so stepping onto the first image never waits for them.
     */
    private void showMarks() {
        if (!bookmarks.loaded()) return;
        Path file = folder.current();
        boolean bookmarked = file != null && bookmarks.isBookmarked(file);
        String note = file == null ? "" : bookmarks.note(file);
        MarksBadge badge = marksBadge;
        if (badge == null && (bookmarked || !note.isEmpty())) {
            badge = new MarksBadge(canvas, this::run);
            marksBadge = badge;
        }
        if (badge != null) badge.show(bookmarked, note, slideshowRunning || placeholder.isVisible());
        ThumbnailStrip shown = strip;
        if (shown != null) shown.refreshMarks();
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
        // After App.start has opened what it was given: a window with nothing to show offers to open.
        Platform.runLater(this::offerToOpen);
        // A first start, or a place no longer on a screen: where the system put it is the place.
        if (normalPlace == null && !stage.isMaximized()) {
            normalPlace = new Rectangle2D(stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight());
        }
    }

    /**
     * Puts the window where it was when it last closed, if that is still on a screen
     * ({@link WindowPlacement}), and maximised if it was. From then on its size and place are
     * followed while it is neither maximised nor full screen, so a window closed maximised opens
     * maximised and, restored, goes back to its own size.
     */
    private void restorePlace() {
        Settings.WindowPlace saved = settings.window();
        if (saved != null) {
            List<Rectangle2D> screens = new ArrayList<>();
            for (Screen screen : Screen.getScreens()) screens.add(screen.getVisualBounds());
            Rectangle2D placed = WindowPlacement.placed(
                    new Rectangle2D(saved.x(), saved.y(), saved.width(), saved.height()), screens);
            if (placed != null) {
                stage.setX(placed.getMinX());
                stage.setY(placed.getMinY());
                stage.setWidth(placed.getWidth());
                stage.setHeight(placed.getHeight());
                normalPlace = placed;
            }
            if (saved.maximized()) stage.setMaximized(true);
        }
        for (var property :
                List.of(stage.xProperty(), stage.yProperty(), stage.widthProperty(), stage.heightProperty())) {
            property.addListener((o, was, now) -> {
                if (stage.isShowing() && !stage.isMaximized() && !stage.isFullScreen() && !stage.isIconified()) {
                    normalPlace = new Rectangle2D(stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight());
                }
            });
        }
    }

    /**
     * Lets go of what this window started, as it closes (PERF-13): its tool windows, which are not
     * owned by it and would otherwise keep the app running; its timers; the decoder and thumbnail
     * threads.
     */
    private void release() {
        released = true;
        for (Stage tool : List.copyOf(toolWindows)) tool.close();
        toolWindows.clear();
        for (LatestOnly work : List.of(listing, sorting, details, lists)) work.cancel();
        toastTimer.stop();
        frameTimer.stop();
        pointerIdle.stop();
        cache.close();
        ThumbnailStrip built = strip;
        if (built != null) built.close();
        BookmarksWindow marks = bookmarksWindow;
        if (marks != null) marks.close();
    }

    /** Closed: a result that comes after is dropped, as the decoders it would start have stopped. */
    private boolean released;

    /** Saves where the window is, for the next start: once, as it closes or the app quits. */
    public void rememberPlace() {
        Rectangle2D place = normalPlace;
        if (place == null || remembered) return;
        remembered = true;
        settings.setWindow(new Settings.WindowPlace(
                place.getMinX(), place.getMinY(), place.getWidth(), place.getHeight(), stage.isMaximized()));
    }

    /**
     * Opens a file with its folder behind it, or a folder at its first image. The folder is listed,
     * and sorted, off the FX thread; a later open drops this one's listing.
     */
    public void open(Path path) {
        Sorting order = settings.sorting();
        listing.submit(() -> Listed.of(path, order), listed -> opened(path, listed));
    }

    /** A folder listed off the FX thread, or why it could not be. */
    record Listed(
            FolderImages images, Sorting order, @Nullable String error) {

        static Listed of(Path path, Sorting order) {
            try {
                return new Listed(FolderImages.open(path, order), order, null);
            } catch (IOException | UncheckedIOException e) {
                return new Listed(FolderImages.EMPTY, order, e.getMessage());
            }
        }
    }

    private void opened(Path path, Listed listed) {
        // A sort asked for while listing was for the folder before.
        sorting.cancel();
        sortedBy = listed.order();
        folder = listed.images();
        if (listed.error() != null) {
            showMessage("Couldn't read " + path + ": " + listed.error());
            return;
        }
        // The order changed while it was listed: sorted again, the image found showing meanwhile.
        if (!settings.sorting().equals(sortedBy)) {
            sortedBy = settings.sorting();
            sortFolder();
        }
        if (folder.isEmpty()) {
            showMessage("No images in " + path);
            return;
        }
        showCurrent();
        // Remembered for Open Recent once the image is up: writing the settings is not start-up's.
        Path first = folder.current();
        Path opened = first == null ? null : first.toAbsolutePath().getParent();
        if (opened != null) afterFirstPaint(() -> settings.addRecent(opened));
    }

    private void showCurrent() {
        Path file = folder.current();
        if (file == null || released) return;
        wanted = file;
        stage.setTitle(fileName(file) + " — " + AppInfo.NAME);
        titleBar.setTitle(fileName(file));
        titleBar.setFolder(file.toAbsolutePath().getParent());
        refreshStrip();
        showMarks();

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
                    log().log(Level.WARNING, "Could not show " + file, error);
                    Platform.runLater(() -> {
                        if (file.equals(wanted)) {
                            showMessage("Couldn't open " + fileName(file) + ": " + error);
                            say("Couldn't open " + fileName(file) + ": " + error, MessageLog.Severity.ERROR);
                        }
                    });
                    return null;
                });
    }

    private void display(Path file, ImageCache.Decoded decoded) {
        if (!file.equals(wanted) || released) return;
        Image image = decoded.image();
        if (image == null) {
            // In the Debug Log too: the window says it to the user, this is for whoever has to find out why.
            log().warning("Could not open " + file + ": " + decoded.error());
            showMessage("Couldn't open " + fileName(file) + ": " + decoded.error());
            say("Couldn't open " + fileName(file) + ": " + decoded.error(), MessageLog.Severity.ERROR);
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
        offerToOpen();
        MarksBadge badge = marksBadge;
        if (badge != null) badge.show(false, "", true);
        if (folder.isEmpty()) {
            titleBar.setTitle(AppInfo.NAME);
            titleBar.setFolder(null);
            stage.setTitle(AppInfo.NAME);
        }
        updateControls();
        refreshInfo();
    }

    private void onKey(KeyEvent e) {
        // The Message Log's keys are its own while it is open: Escape closes it, not the window.
        MessageLogView log = messageLog;
        if (log != null && log.isShowing()) return;
        // The palette's keys are the user's, and win over every other key, the editor's too.
        boolean paletteKey = PaletteChords.opens(e);
        // While the palette is up, keys are its own (typing "r" must not rotate the image); only its
        // chord reaches here, to close it again — unless the palette moves by that key (C-p).
        if (palette.isShowing()) {
            if (paletteKey && !PaletteChords.keptByPalette(KeyChord.of(e))) {
                palette.hide();
                e.consume();
            }
            return;
        }
        if (paletteKey) {
            run(Command.PALETTE);
            e.consume();
            return;
        }
        // The editor's keys are its own while it is open; the viewer's wait.
        Editor open = editor;
        boolean editing = open != null && open.isShowing();
        // The keyboard's right-click: the image's menu, as a right-click on it opens (MENU-09).
        if (!editing && Popups.isMenuKey(e)) {
            showMenuFromKeyboard();
            e.consume();
            return;
        }
        Command command = Keybindings.current().commandFor(context(), e);
        if (command != null) {
            run(command);
            e.consume();
        } else if (open != null && editing && open.onKey(e)) {
            e.consume();
        }
    }

    /** Where the keys go now: the editor's tools while it is open, else the viewer's. */
    private Command.Context context() {
        Editor open = editor;
        return open != null && open.isShowing() ? Command.Context.EDITOR : Command.Context.VIEWER;
    }

    /**
     * Every icon button's tooltip again, from the chords now (TBAR-03): the user's keys changed, or
     * the strip's axis, which turns the stepping keys.
     */
    private void relabelButtons() {
        titleBar.relabel();
        OnScreenControls built = controls;
        if (built != null) built.relabel();
        MarksBadge badge = marksBadge;
        if (badge != null) badge.relabel();
    }

    /**
     * The image's menu, opened by the Menu key or Shift+F10: under the image's thumbnail while the
     * strip shows it, the selected row there, and in the middle of the image otherwise.
     */
    private void showMenuFromKeyboard() {
        ThumbnailStrip shown = strip;
        if (shown != null && thumbnailsShowing.get()) {
            Bounds row = Popups.selectedRow(shown.node());
            if (row != null) {
                Point2D at = Popups.under(row);
                Popups.show(contextMenu(), shown.node(), at.getX(), at.getY());
                return;
            }
        }
        Bounds image = viewport.localToScreen(viewport.getBoundsInLocal());
        if (image == null) return;
        Popups.show(contextMenu(), viewport, image.getCenterX(), image.getCenterY());
    }

    private void run(Command command) {
        // From the header's menu while the editor is open: what would change the image under it waits.
        Editor open = editor;
        if (open != null && open.isShowing() && !duringEdit(command)) {
            say("Finish editing first: Save or Cancel", MessageLog.Severity.WARNING);
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
                } else if (ESCAPE_CLOSES && mayClose()) {
                    stage.close();
                }
            }
            case OPEN -> chooseFile();
            case OPEN_FOLDER -> chooseFolder();
            case CLOSE -> {
                if (mayClose()) stage.close();
            }
            case PALETTE -> palette.show(Command.inPalette(context()), this::unavailable);
            case GO_TO_IMAGE -> goToImage();
            case OPEN_RECENT -> openRecent();
            case INFO -> infoShowing.set(!infoShowing.get());
            case THUMBNAILS -> thumbnailsShowing.set(!thumbnailsShowing.get());
            case THUMBNAILS_AT_SIDE -> {
                settings.setThumbnailsAtSide(!settings.thumbnailsAtSide());
                thumbnailsShowing.set(true);
            }
            case THUMBNAIL_NAMES -> {
                settings.setThumbnailNames(!settings.thumbnailNames());
                thumbnailsShowing.set(true);
            }
            case THUMBNAIL_CAPTIONS -> {
                settings.setThumbnailCaptions(!settings.thumbnailCaptions());
                thumbnailsShowing.set(true);
            }
            case SETTINGS -> settingsWindow().show(stage);
            case ABOUT -> aboutWindow().show(stage);
            case SHORTCUTS -> shortcutsWindow().show(stage);
            case DOCUMENTATION -> openLink.accept(AppInfo.DOCS_URL);
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
            case UNDO_TRASH -> undoTrash();
            case COPY -> copy();
            case SHOW_IN_FOLDER -> showInFolder();
            case SORT_BY_NAME -> sort(settings.sorting().by(Sorting.By.NAME));
            case SORT_BY_DATE -> sort(settings.sorting().by(Sorting.By.DATE));
            case SORT_BY_SIZE -> sort(settings.sorting().by(Sorting.By.SIZE));
            case REVERSE_ORDER -> sort(settings.sorting().reverse());
            case BOOKMARK -> toggleBookmark(false);
            case BOOKMARK_FOLDER -> toggleBookmark(true);
            case NOTE -> editNote(false);
            case FOLDER_NOTE -> editNote(true);
            case NEXT_BOOKMARK -> stepToBookmark(1);
            case PREVIOUS_BOOKMARK -> stepToBookmark(-1);
            case GO_TO_BOOKMARK -> goToBookmark();
            case BOOKMARKS -> bookmarksWindow().show(stage);
            case STATUS_BAR -> settings.setStatusBar(!settings.statusBar());
            case MESSAGE_LOG -> messageLog().show(statusBar.isShowing() ? StatusBar.HEIGHT : 0);
            case COPY_MESSAGES -> copyText(MessageLogView.asText(messageLog().listed()), "Copied the messages");
            case CLEAR_MESSAGES -> messages.clear();
            case DEBUG_LOG -> debugLog().show(stage);
            case COPY_DEBUG_LOG -> copyText(DebugLog.text(), "Copied the Debug Log");
            case CLEAR_DEBUG_LOG -> {
                DebugLog.clear();
                DebugLogWindow window = debugLog;
                if (window != null) window.refresh();
            }
            case EXPORT_DEBUG_LOG -> exportDebugLog();
            case REPORT_PROBLEM -> {
                ReportProblemWindow report =
                        new ReportProblemWindow(settings, Redactor.forThisMachine(), openLink, this::say, stylesheet());
                Stage reportStage = report.stage();
                toolWindows.add(reportStage);
                reportStage.addEventHandler(WindowEvent.WINDOW_HIDDEN, e -> toolWindows.remove(reportStage));
                report.show(stage);
            }
            case CHECK_FOR_UPDATES -> updates.checkNow(true);
            case OPEN_RELEASE -> {
                String page = updates.releasePage();
                if (page != null) openLink.accept(page);
            }
            case COPY_DETAILS ->
                copyText(AppInfo.environment(AppInfo.runtime(), settings.file()), "Copied the details");
            case EDITOR_ROTATE_LEFT,
                    EDITOR_ROTATE_RIGHT,
                    EDITOR_FLIP_HORIZONTALLY,
                    EDITOR_FLIP_VERTICALLY,
                    EDITOR_NEXT_SHAPE,
                    EDITOR_RESET,
                    EDITOR_UNDO,
                    EDITOR_REDO,
                    EDITOR_SAVE,
                    EDITOR_CANCEL -> {
                if (open != null && open.isShowing()) open.run(command);
            }
            case BOOKMARKS_OPEN,
                    BOOKMARKS_EDIT_NOTE,
                    BOOKMARKS_TOGGLE,
                    BOOKMARKS_SHOW_IN_FOLDER,
                    BOOKMARKS_REMOVE,
                    BOOKMARKS_LIST_ALL,
                    BOOKMARKS_LIST_BOOKMARKED,
                    BOOKMARKS_LIST_NOTES -> {
                BookmarksWindow window = bookmarksWindow;
                if (window != null && window.isShowing()) window.run(command);
            }
        }
    }

    /** Whether the window may close: an edit in progress is discarded only when the user says so. */
    private boolean mayClose() {
        Editor open = editor;
        return open == null || open.mayDiscard();
    }

    /** The commands that leave the image alone, so they can run while the editor is open: its own too. */
    private static boolean duringEdit(Command command) {
        if (command.context() != Command.Context.VIEWER) return true;
        return switch (command) {
            case PALETTE,
                    COPY_DETAILS,
                    FULL_SCREEN,
                    INFO,
                    THUMBNAILS,
                    SETTINGS,
                    SHORTCUTS,
                    DOCUMENTATION,
                    ABOUT,
                    CLOSE,
                    BOOKMARK,
                    BOOKMARK_FOLDER,
                    NOTE,
                    FOLDER_NOTE,
                    BOOKMARKS,
                    STATUS_BAR,
                    MESSAGE_LOG,
                    COPY_MESSAGES,
                    CLEAR_MESSAGES,
                    DEBUG_LOG,
                    COPY_DEBUG_LOG,
                    EXPORT_DEBUG_LOG,
                    CLEAR_DEBUG_LOG,
                    REPORT_PROBLEM,
                    CHECK_FOR_UPDATES,
                    OPEN_RELEASE -> true;
            default -> false;
        };
    }

    /** How far one press of a panning key moves the view: a tenth of the window. */
    private static final double PAN_STEP = 0.1;

    /** {@link #say}, from any thread: how an error no code caught reaches the user (DIAG-04). */
    public void sayLater(String message, MessageLog.Severity severity) {
        if (Platform.isFxApplicationThread()) say(message, severity);
        else Platform.runLater(() -> say(message, severity));
    }

    /** {@link #say}, the severity read from the message's first words ({@link MessageLog#severityOf}). */
    private void say(String message) {
        say(message, MessageLog.severityOf(message));
    }

    /**
     * Says {@code message} in the status bar's echo area and keeps it in the Message Log; while the
     * bar is away (full screen, a slideshow, switched off), over the image for two seconds instead.
     */
    private void say(String message, MessageLog.Severity severity) {
        messages.add(message, severity, java.time.LocalTime.now());
        if (statusBarShows()) {
            toast.setVisible(false);
            return;
        }
        toast.setText(message);
        toast.pseudoClassStateChanged(TOAST_ERROR, severity == MessageLog.Severity.ERROR);
        toast.pseudoClassStateChanged(TOAST_WARNING, severity == MessageLog.Severity.WARNING);
        toast.setVisible(true);
        toastTimer.playFromStart();
    }

    private static final PseudoClass TOAST_ERROR = PseudoClass.getPseudoClass("error");
    private static final PseudoClass TOAST_WARNING = PseudoClass.getPseudoClass("warning");

    /** Whether the status bar is on screen: switched on, and not full screen or a slideshow. */
    private boolean statusBarShows() {
        return statusBarWanted(settings.statusBar(), stage.isFullScreen(), slideshowRunning);
    }

    /** Whether the status bar shows: when switched on, but never over a full-screen image or a slideshow. */
    static boolean statusBarWanted(boolean switchedOn, boolean fullScreen, boolean slideshow) {
        return switchedOn && !fullScreen && !slideshow;
    }

    private void showStatusBar() {
        statusBar.setShowing(statusBarShows());
    }

    private MessageLogView messageLog() {
        MessageLogView made = messageLog;
        if (made == null) {
            made = new MessageLogView(
                    (StackPane) Objects.requireNonNull(stage.getScene()).getRoot(), messages, this::run);
            messageLog = made;
        }
        return made;
    }

    private DebugLogWindow debugLog() {
        DebugLogWindow made = debugLog;
        if (made == null) {
            made = new DebugLogWindow(this::run, stylesheet());
            debugLog = made;
            toolWindows.add(made.stage());
        }
        return made;
    }

    /** Puts {@code text} on the clipboard and says {@code said}. */
    private void copyText(String text, String said) {
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);
        say(said);
    }

    /** Saves the Debug Log where the user chooses. */
    private void exportDebugLog() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Debug Log");
        chooser.setInitialFileName("mirada-debug-log.txt");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text", "*.txt", "*.log"));
        DebugLogWindow window = debugLog;
        File chosen = FileDialogs.save(chooser, window != null && window.isShowing() ? window.stage() : stage);
        if (chosen == null) return;
        String text = DebugLog.text() + System.lineSeparator();
        Thread.ofVirtual().name("export-debug-log").start(() -> {
            try {
                Files.writeString(chosen.toPath(), text, java.nio.charset.StandardCharsets.UTF_8);
                Platform.runLater(() -> say("Saved the Debug Log to " + chosen.getName()));
            } catch (IOException e) {
                log().log(Level.WARNING, "Could not export the Debug Log", e);
                Platform.runLater(
                        () -> say("Couldn't save the Debug Log: " + e.getMessage(), MessageLog.Severity.ERROR));
            }
        });
    }

    /**
     * Shows the pointer, and in full screen hides it again after the same rest as the floating
     * controls: over a photo filling the screen, an arrow is in the way. Outside full screen it stays.
     */
    private void showPointer() {
        if (canvas.getCursor() == Cursor.NONE) canvas.setCursor(null);
        if (stage.isFullScreen()) {
            pointerIdle.setDuration(Duration.seconds(settings.controlsHideSeconds()));
            pointerIdle.playFromStart();
        } else {
            pointerIdle.stop();
        }
    }

    private void startSlideshow() {
        if (folder.size() < 2) return;
        slideshowRunning = true;
        stage.setFullScreen(true);
        say("Slideshow: every " + settings.slideshowSeconds() + " s. Escape stops it.");
        showMarks();
        restartSlideshowTimer();
    }

    private void stopSlideshow() {
        slideshowRunning = false;
        slideshow.stop();
        stage.setFullScreen(false);
        say("Slideshow stopped");
        showMarks();
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
        // The move, and the sort that puts the new name in its place, off the FX thread (PERF-01).
        List<Path> images = folder.renamed(file, target).images();
        Sorting order = sortedBy;
        Thread.ofVirtual().name("rename").start(() -> {
            String failed;
            try {
                Files.move(file, target);
                List<Path> sorted = order.sort(images);
                Platform.runLater(() -> renamed(file, target, sorted));
                return;
            } catch (FileAlreadyExistsException e) {
                failed = "Not renamed: " + name + " already exists";
            } catch (IOException | RuntimeException e) {
                failed = "Not renamed: " + e.getMessage();
            }
            String said = failed;
            Platform.runLater(() -> say(said));
        });
    }

    /** {@code file} is {@code target} now: in the folder, in its place by {@code sorted}, and in the marks. */
    private void renamed(Path file, Path target, List<Path> sorted) {
        boolean showing = file.equals(folder.current());
        folder = folder.renamed(file, target).reordered(sorted);
        bookmarks.moved(file, target);
        if (showing) showCurrent();
        else refreshStrip();
        say("Renamed to " + fileName(target));
    }

    /** The folder of the image on screen, absolute, or null when there is none. */
    private @Nullable Path shownFolder() {
        Path file = folder.current();
        return file == null ? null : file.toAbsolutePath().getParent();
    }

    /** Bookmarks the image on screen or its folder, or takes the bookmark off. */
    private void toggleBookmark(boolean ofFolder) {
        Path target = ofFolder ? shownFolder() : folder.current();
        if (target == null) return;
        boolean now = bookmarks.toggle(target, ofFolder);
        String what = ofFolder ? "the folder " + fileName(target) : fileName(target);
        say(now ? "Bookmarked " + what : "Took the bookmark off " + what);
    }

    /** Asks for the note on the image on screen or its folder ({@link NoteDialog}); emptied, it goes. */
    private void editNote(boolean ofFolder) {
        Path target = ofFolder ? shownFolder() : folder.current();
        if (target == null) return;
        String was = bookmarks.note(target);
        String what = ofFolder ? "the folder " + fileName(target) : fileName(target);
        Optional<String> answer = NoteDialog.ask(stage, ofFolder ? "Folder Note" : "Note", what, was, stylesheet());
        if (answer.isEmpty()) return;
        bookmarks.setNote(target, ofFolder, answer.get());
        String now = bookmarks.note(target);
        if (now.equals(was)) return;
        say(now.isEmpty() ? "Took the note off " + what : "Kept the note on " + what);
    }

    /** The next bookmarked image of the folder that way, or -1 when there is none. */
    private int bookmarkFrom(int direction) {
        return Bookmarks.step(folder.images(), folder.index(), direction, bookmarks::isBookmarked);
    }

    private void stepToBookmark(int direction) {
        int index = bookmarkFrom(direction);
        if (index < 0) {
            say("No bookmarked image " + (direction > 0 ? "after" : "before") + " this one");
            return;
        }
        go(folder.jump(index));
    }

    /**
     * The palette over every bookmark and note, the latest first, found by name or by note; one whose
     * file or folder has gone (or whose drive is not there) is left out. An image of the folder on
     * screen is stepped to; any other opens with its folder.
     */
    private void goToBookmark() {
        List<Bookmarks.Mark> all = bookmarks.all();
        // Whether each is still there is a stat apiece: asked off the FX thread (PERF-01).
        lists.submit(() -> stillThere(all), this::goToBookmark);
    }

    /** The marks whose file or folder is there now. Reads the disk: never on the FX thread. */
    static List<Bookmarks.Mark> stillThere(List<Bookmarks.Mark> marks) {
        List<Bookmarks.Mark> there = new ArrayList<>();
        for (Bookmarks.Mark mark : marks) {
            if (mark.folder() ? Files.isDirectory(mark.path()) : Files.isRegularFile(mark.path())) there.add(mark);
        }
        return there;
    }

    private void goToBookmark(List<Bookmarks.Mark> marks) {
        if (marks.isEmpty()) {
            say("Nothing is bookmarked or noted yet");
            return;
        }
        List<String> names = new ArrayList<>(marks.size());
        for (Bookmarks.Mark mark : marks) names.add(bookmarkName(mark));
        palette.showChoices(
                new CommandPalette.Choices(
                        Command.GO_TO_BOOKMARK,
                        "Go to bookmark…",
                        "No matching bookmarks or notes",
                        "open",
                        names,
                        0,
                        index -> marks.get(index).path().toString(),
                        index -> bookmarkDetail(marks.get(index))),
                index -> openMark(marks.get(index)));
    }

    /**
     * A mark's row in the palette: its name, a folder's with a slash after it, and the first line of
     * its note, so typing a word of the note finds it.
     */
    static String bookmarkName(Bookmarks.Mark mark) {
        String name = fileName(mark.path()) + (mark.folder() ? "/" : "");
        if (!mark.hasNote()) return name;
        String line = mark.note().lines().findFirst().orElse("").strip();
        if (line.length() > NOTE_IN_ROW)
            line = line.substring(0, NOTE_IN_ROW - 1).strip() + "…";
        return name + " — " + line;
    }

    /** How much of a note the palette's row shows. */
    private static final int NOTE_IN_ROW = 72;

    /** What stands on the right of a mark's row: the image's folder, or that it is a folder. */
    static String bookmarkDetail(Bookmarks.Mark mark) {
        String kind = mark.bookmarked() ? "" : "Note · ";
        if (mark.folder()) return kind + "Folder";
        Path parent = mark.path().getParent();
        return kind + (parent == null ? "" : fileName(parent));
    }

    private void openMark(Bookmarks.Mark mark) {
        int index = mark.folder() ? -1 : folder.images().indexOf(mark.path());
        if (index >= 0) go(folder.jump(index));
        else open(mark.path());
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
                Trash.Trashed moved = Trash.move(file);
                Platform.runLater(() -> {
                    FolderImages rest = folder.without(file);
                    folder = rest;
                    String said = "Moved " + fileName(file) + " to the " + Command.trash(Command.WINDOWS);
                    if (moved.restorable()) {
                        trashed.push(moved);
                        while (trashed.size() > UNDO_DEPTH) trashed.removeLast();
                        said += ". " + Command.UNDO_TRASH.chord().getDisplayText() + " puts it back";
                    }
                    say(said);
                    if (rest.isEmpty()) showMessage("No images left in this folder");
                    else showCurrent();
                });
            } catch (IOException | RuntimeException e) {
                Platform.runLater(() -> say("Couldn't move " + fileName(file) + " to the "
                        + Command.trash(Command.WINDOWS) + ": " + e.getMessage()));
            }
        });
    }

    /**
     * Puts the image showing on the clipboard as it stands on screen, upright by the file's
     * orientation and the user's turns, and its file beside it: an app that takes images pastes the
     * picture, a folder the file. Turning a large photo takes a moment, so it is done off the FX
     * thread; one that needs no turning goes as it is.
     */
    private void copy() {
        Image image = shownImage;
        Path file = folder.current();
        if (image == null || file == null) {
            say("The image is still loading");
            return;
        }
        Orientation orientation = shownOrientation;
        int turns = viewport.turns();
        if (orientation == Orientation.NORMAL && turns == 0) {
            putOnClipboard(image, file);
            return;
        }
        Thread.ofVirtual().name("copy").start(() -> {
            try {
                Raster upright = Edit.upright(Editor.raster(image), orientation).turned(turns);
                Platform.runLater(() -> putOnClipboard(Editor.image(upright), file));
            } catch (RuntimeException | OutOfMemoryError e) {
                Platform.runLater(() -> say("Couldn't copy " + fileName(file) + ": " + e.getMessage()));
            }
        });
    }

    private void putOnClipboard(Image image, Path file) {
        try {
            putOnClipboard(image, file.toFile());
        } catch (IllegalArgumentException e) {
            // JavaFX's clipboard takes only RGB pixels, and a grey PNG loads as grey ("non-RGB
            // image format"): those go as premultiplied ARGB.
            putOnClipboard(Editor.image(Editor.raster(image)), file.toFile());
        }
        say("Copied " + fileName(file));
    }

    private static void putOnClipboard(Image image, File file) {
        ClipboardContent content = new ClipboardContent();
        content.putImage(image);
        content.putFiles(List.of(file));
        Clipboard.getSystemClipboard().setContent(content);
    }

    /**
     * The palette over the folder's file names, the current one under the cursor. Ctrl+P does not
     * close it again, as Shift+Ctrl+P closes the commands: inside the palette it is the Emacs key for
     * the row above, and stays that.
     */
    private void goToImage() {
        List<String> names = new ArrayList<>(folder.size());
        for (Path image : folder.images()) names.add(fileName(image));
        palette.showImages(names, folder.index(), index -> go(folder.jump(index)));
    }

    /**
     * The palette over the folders opened lately, the latest first, by name with the whole path
     * under the list; one gone since is left out. The one on screen is not offered.
     */
    private void openRecent() {
        Path shown = folder.current();
        Path here = shown == null ? null : shown.toAbsolutePath().getParent();
        List<Path> recent = settings.recent();
        // A stat apiece, off the FX thread (PERF-01).
        lists.submit(() -> recentFolders(recent, here), this::openRecent);
    }

    /** The folders of {@code recent} that are there now, but {@code here}. Reads the disk: never on the FX thread. */
    static List<Path> recentFolders(List<Path> recent, @Nullable Path here) {
        List<Path> folders = new ArrayList<>();
        for (Path folder : recent) {
            if (!folder.equals(here) && Files.isDirectory(folder)) folders.add(folder);
        }
        return folders;
    }

    private void openRecent(List<Path> folders) {
        if (folders.isEmpty()) {
            say("No other folder has been opened lately");
            return;
        }
        List<String> names = new ArrayList<>(folders.size());
        for (Path recent : folders) names.add(fileName(recent));
        palette.showChoices(
                new CommandPalette.Choices(
                        Command.OPEN_RECENT,
                        "Open recent folder…",
                        "No matching folders",
                        "open",
                        names,
                        0,
                        index -> folders.get(index).toString(),
                        index -> ""),
                index -> open(folders.get(index)));
    }

    /** Opens the file manager on the image's folder with the image selected ({@link Reveal}). */
    private void showInFolder() {
        Path file = folder.current();
        if (file != null) reveal(file);
    }

    /** Shows {@code file} selected in the file manager, off the FX thread. */
    private void reveal(Path file) {
        Thread.ofVirtual().name("reveal").start(() -> {
            try {
                Reveal.show(file);
            } catch (IOException e) {
                Platform.runLater(() -> say("Couldn't show " + fileName(file) + ": " + e.getMessage()));
            }
        });
    }

    /**
     * Puts the image last trashed back, and shows it when it belongs to the folder on screen. Off the
     * FX thread, as the move was; taken off the list first, so a second press puts back the one before.
     */
    private void undoTrash() {
        Trash.Trashed last = trashed.poll();
        if (last == null) return;
        Path file = last.original();
        Thread.ofVirtual().name("untrash").start(() -> {
            try {
                Trash.restore(last);
                Platform.runLater(() -> {
                    Path shown = folder.current();
                    Path here = shown == null ? null : shown.getParent();
                    if (here == null || here.equals(file.getParent())) open(file);
                    say("Put back " + fileName(file));
                });
            } catch (IOException | RuntimeException e) {
                Platform.runLater(() -> say("Couldn't put back " + fileName(file) + ": " + e.getMessage()));
            }
        });
    }

    private BookmarksWindow bookmarksWindow() {
        BookmarksWindow made = bookmarksWindow;
        if (made == null) {
            made = new BookmarksWindow(
                    bookmarks, this::openFromBookmarks, this::reveal, this::run, this::unavailable, stylesheet());
            bookmarksWindow = made;
            toolWindows.add(made.stage());
        }
        return made;
    }

    /**
     * Opens a mark chosen in the Bookmarks and Notes window, as Go to Bookmark does, and brings the
     * viewer forward to show it; the list stays open behind.
     */
    private void openFromBookmarks(Path path, boolean isFolder) {
        Editor open = editor;
        if (open != null && open.isShowing()) {
            say("Finish editing first: Save or Cancel");
            stage.toFront();
            return;
        }
        openMark(new Bookmarks.Mark(path, isFolder, true, ""));
        stage.toFront();
        stage.requestFocus();
    }

    private ShortcutsWindow shortcutsWindow() {
        ShortcutsWindow made = shortcutsWindow;
        if (made == null) {
            made = new ShortcutsWindow(stylesheet());
            shortcutsWindow = made;
            toolWindows.add(made.stage());
        }
        return made;
    }

    private SettingsWindow settingsWindow() {
        SettingsWindow made = settingsWindow;
        if (made == null) {
            made = new SettingsWindow(settings, stylesheet());
            settingsWindow = made;
            toolWindows.add(made.stage());
        }
        return made;
    }

    private AboutWindow aboutWindow() {
        AboutWindow made = about;
        if (made == null) {
            made = new AboutWindow(settings.file(), openLink, updates, () -> run(Command.COPY_DETAILS), stylesheet());
            about = made;
            toolWindows.add(made.stage());
        }
        return made;
    }

    /**
     * Why {@code command} cannot run right now, or null when it can: the palette greys it with this
     * as its reason, the menus disable it. The editor's tools need the editor, the Bookmarks
     * window's row actions that window; while the editor is open, what would change the image
     * under it waits.
     */
    private @Nullable String unavailable(Command command) {
        Editor open = editor;
        boolean editing = open != null && open.isShowing();
        if (command.context() == Command.Context.EDITOR) {
            return open != null && editing ? open.unavailable(command) : "The editor is not open.";
        }
        if (command.context() == Command.Context.BOOKMARKS) {
            BookmarksWindow window = bookmarksWindow;
            return window != null && window.isShowing()
                    ? window.unavailable(command)
                    : "The Bookmarks and Notes window is not open.";
        }
        if (editing && !duringEdit(command)) return "Finish editing first: Save or Cancel.";
        if (command == Command.OPEN_RECENT && settings.recent().isEmpty()) return "No folder has been opened yet.";
        if (command == Command.GO_TO_BOOKMARK && bookmarks.all().isEmpty())
            return "Nothing is bookmarked or noted yet.";
        String folderReason = command.unavailable(folder.index(), folder.size(), !placeholder.isVisible());
        if (folderReason == null && command == Command.NEXT_BOOKMARK && bookmarkFrom(1) < 0)
            return "No bookmarked image after this one.";
        if (folderReason == null && command == Command.PREVIOUS_BOOKMARK && bookmarkFrom(-1) < 0)
            return "No bookmarked image before this one.";
        if (command == Command.OPEN_RELEASE && updates.releasePage() == null)
            return updates.state() == UpdateCoordinator.State.NOT_CHECKED
                    ? "No newer release is known: Check for Updates first."
                    : "No newer release is out.";
        if (command == Command.CHECK_FOR_UPDATES && updates.state() == UpdateCoordinator.State.CHECKING)
            return "Already checking.";
        if (command == Command.COPY_DEBUG_LOG && DebugLog.text().isEmpty()) return "Nothing is logged yet.";
        if (command == Command.COPY_MESSAGES && messages.entries().isEmpty()) return "Nothing has been said yet.";
        if (command == Command.UNDO_TRASH && trashed.isEmpty()) {
            String trash = Command.trash(Command.WINDOWS);
            return Trash.restores()
                    ? "Nothing has been moved to the " + trash + " yet."
                    : "Put it back from the " + trash + " itself.";
        }
        return folderReason;
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

    /**
     * Shows Open Image and Open Folder, and a link to the documentation, under the placeholder while
     * no folder is open (at start, or
     * when one turned out to have no images), and hides them once one is: within a folder the
     * arrows go on to the next image. Built on first need, never on a start with an image.
     */
    private void offerToOpen() {
        // A folder on its way is not an empty window: its listing says what to show.
        if (listing.pending()) return;
        HBox buttons = openButtons;
        if (!folder.isEmpty()) {
            if (buttons != null) buttons.setVisible(false);
            return;
        }
        if (placeholder.getText().isEmpty())
            placeholder.setText("Open an image with " + Command.OPEN.chord().getDisplayText() + ", or drop one here");
        if (buttons == null) {
            buttons = new HBox(openButton(Command.OPEN), openButton(Command.OPEN_FOLDER));
            buttons.getStyleClass().add("open-buttons");
            buttons.setAlignment(Pos.CENTER);
            buttons.managedProperty().bind(buttons.visibleProperty());
            openButtons = buttons;
            // Help → Documentation, offered where a first-time user starts (DOCS-06).
            Hyperlink docs = new Hyperlink(Command.DOCUMENTATION.label());
            docs.getStyleClass().add("empty-docs");
            docs.setOnAction(e -> run(Command.DOCUMENTATION));
            docs.visibleProperty().bind(buttons.visibleProperty());
            docs.managedProperty().bind(buttons.visibleProperty());
            emptyState.getChildren().addAll(buttons, docs);
        }
        buttons.setVisible(true);
    }

    private Button openButton(Command command) {
        Button button = new Button(command.label(), Icons.menu(command));
        button.getStyleClass().add("open-button");
        button.setTooltip(new Tooltip(Icons.tooltip(command)));
        button.setOnAction(e -> run(command));
        return button;
    }

    private void chooseFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Open Folder");
        Path current = folder.current();
        Path parent = current == null ? null : current.toAbsolutePath().getParent();
        if (parent != null) chooser.setInitialDirectory(parent.toFile());
        File chosen = FileDialogs.folder(chooser, stage);
        if (chosen != null) open(chosen.toPath());
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
        File chosen = FileDialogs.open(chooser, stage);
        if (chosen != null) open(chosen.toPath());
    }

    private void onDragOver(DragEvent e) {
        // A file dragged out of this window is for somewhere else, not to be opened again here.
        if (e.getDragboard().hasFiles() && e.getGestureSource() == null) {
            e.acceptTransferModes(TransferMode.COPY_OR_MOVE);
        }
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
                item(Command.OPEN_FOLDER),
                new SeparatorMenuItem(),
                item(Command.FIT),
                item(Command.ACTUAL_SIZE),
                item(Command.ROTATE_CLOCKWISE),
                item(Command.ROTATE_COUNTERCLOCKWISE),
                new SeparatorMenuItem(),
                item(Command.COPY),
                item(Command.SHOW_IN_FOLDER),
                new SeparatorMenuItem(),
                item(Command.BOOKMARK),
                item(Command.NOTE),
                item(Command.GO_TO_BOOKMARK),
                item(Command.BOOKMARKS),
                new SeparatorMenuItem(),
                item(Command.RENAME),
                item(Command.MOVE_TO_TRASH),
                new SeparatorMenuItem(),
                sortMenu(),
                item(Command.INFO),
                item(Command.THUMBNAILS),
                item(Command.THUMBNAILS_AT_SIDE),
                item(Command.THUMBNAIL_CAPTIONS),
                item(Command.THUMBNAIL_NAMES),
                item(Command.FULL_SCREEN),
                item(Command.SLIDESHOW));
        Popups.closedByWindow(menu);
        return menu;
    }

    /** Sort by name, date or size, the current one ticked, and the order's direction. */
    private Menu sortMenu() {
        Sorting sorting = settings.sorting();
        ToggleGroup group = new ToggleGroup();
        Menu menu = new Menu("Sort", Icons.of(Icons.SORT));
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
            menu.getItems().add(MenuChord.on(item, command));
        }
        CheckMenuItem reversed = new CheckMenuItem(Command.REVERSE_ORDER.label());
        reversed.setSelected(sorting.reversed());
        reversed.setOnAction(e -> run(Command.REVERSE_ORDER));
        MenuChord.on(reversed, Command.REVERSE_ORDER);
        menu.getItems().addAll(new SeparatorMenuItem(), reversed);
        menu.setDisable(folder.isEmpty());
        return menu;
    }

    /** A context-menu row, named as the palette names it and greyed when the palette would grey it. */
    private MenuItem item(Command command) {
        MenuItem item;
        if (MacMenus.TICKED.contains(command)) {
            // A setting or a panel: ticked when on, as the Mac's menus tick it.
            CheckMenuItem check = new CheckMenuItem(label(command), Icons.menu(command));
            check.setSelected(ticked(command));
            item = check;
        } else {
            item = new MenuItem(label(command), Icons.menu(command));
        }
        item.setOnAction(e -> run(command));
        item.setDisable(unavailable(command) != null);
        return MenuChord.on(item, command);
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
            case BOOKMARK -> {
                Path file = folder.current();
                yield file != null && bookmarks.isBookmarked(file);
            }
            case BOOKMARK_FOLDER -> {
                Path shown = shownFolder();
                yield shown != null && bookmarks.isBookmarked(shown);
            }
            case INFO -> infoShowing.get();
            case STATUS_BAR -> settings.statusBar();
            case THUMBNAILS -> thumbnailsShowing.get();
            case THUMBNAILS_AT_SIDE -> settings.thumbnailsAtSide();
            case THUMBNAIL_NAMES -> settings.thumbnailNames();
            case THUMBNAIL_CAPTIONS -> settings.thumbnailCaptions();
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
