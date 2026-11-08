package com.mirada.settings;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

import com.mirada.folder.Sorting;
import org.jspecify.annotations.Nullable;

/**
 * Mirada's settings: theme, what opens at start, how long the floating controls stay, the folder's
 * order, whether stepping keeps the zoom, the slideshow's pace, and the keys that open the palette. In {@code settings.properties},
 * in the platform's folder for it ({@link #folder}; {@code MIRADA_CONFIG_DIR} moves it, for tests
 * and timing runs), Termina's way: a plain file meant to be hand-edited, every
 * value clamped on read as well as on write, so a bad edit cannot break the next start, and every
 * change written through at once (the settings window has no OK button).
 *
 * <p>FX thread only.
 */
public final class Settings {

    /** The control theme: the system's light or dark setting, or one of them always. */
    public enum ThemeChoice {
        SYSTEM,
        LIGHT,
        DARK
    }

    static final String THEME = "theme";
    static final String START_WITH_THUMBNAILS = "start.thumbnails";
    static final String START_WITH_INFO = "start.info";
    static final String CONTROLS_HIDE_SECONDS = "controls.hideSeconds";
    static final String SORT_BY = "sort.by";
    static final String SORT_REVERSED = "sort.reversed";
    static final String KEEP_ZOOM = "view.keepZoom";
    static final String THUMBNAILS_AT_SIDE = "thumbnails.side";
    static final String THUMBNAIL_NAMES = "thumbnails.names";
    static final String THUMBNAIL_CAPTIONS = "thumbnails.captions";
    static final String WINDOW_BOUNDS = "window.bounds";
    /** {@code recent.0} to {@code recent.9}, the latest first. */
    static final String RECENT = "recent.";

    public static final int MAX_RECENT = 10;
    static final String WINDOW_MAXIMIZED = "window.maximized";
    static final String SLIDESHOW_SECONDS = "slideshow.seconds";
    static final String PALETTE_KEYS = "palette.keys";

    public static final int MIN_HIDE_SECONDS = 1;
    public static final int MAX_HIDE_SECONDS = 10;
    public static final int MIN_SLIDESHOW_SECONDS = 1;
    public static final int MAX_SLIDESHOW_SECONDS = 60;
    static final int DEFAULT_SLIDESHOW_SECONDS = 5;

    private final Path file;
    private final List<Runnable> listeners = new ArrayList<>();

    private ThemeChoice theme = ThemeChoice.SYSTEM;
    private boolean startWithThumbnails;
    private boolean startWithInfo;
    private int controlsHideSeconds = 2;
    private Sorting sorting = Sorting.DEFAULT;
    private boolean keepZoom;
    private boolean thumbnailsAtSide;
    private boolean thumbnailNames;
    private boolean thumbnailCaptions;
    private @Nullable WindowPlace window;
    private List<Path> recent = List.of();
    private int slideshowSeconds = DEFAULT_SLIDESHOW_SECONDS;
    private String paletteKeys = defaultPaletteKeys(isMac(System.getProperty("os.name", "")));

    public Settings(Path file) {
        this.file = file;
    }

    /**
     * The settings file in the folder the command line names ({@code --config-dir}, {@code --dev}),
     * else under {@code MIRADA_CONFIG_DIR}, else in the platform's folder ({@link #folder}): flag,
     * then variable, then default (SET-07, Pauta 4.0.1). A file left in {@code ~/.mirada} by 0.1.0 on
     * macOS or Windows is moved to the default the first time ({@link #movedFrom}).
     */
    public static Path defaultFile(@Nullable Path configDir) {
        if (configDir != null) return configDir.resolve(FILE_NAME);
        String override = System.getenv("MIRADA_CONFIG_DIR");
        String home = System.getProperty("user.home", ".");
        Path file = folder(override, home, System.getProperty("os.name", ""), System.getenv("APPDATA"))
                .resolve(FILE_NAME);
        if (override != null && !override.isBlank()) return file;
        return movedFrom(Path.of(home, ".mirada", FILE_NAME), file);
    }

    static final String FILE_NAME = "settings.properties";

    /**
     * Where each platform keeps an application's settings: {@code ~/Library/Application
     * Support/Mirada} on macOS, {@code %APPDATA%\Mirada} on Windows (its roaming folder, which
     * travels with the user's account), {@code ~/.mirada} elsewhere, as Termina and Torrenta do.
     * {@code override} wins when set.
     */
    static Path folder(@Nullable String override, String home, String osName, @Nullable String appData) {
        if (override != null && !override.isBlank()) return Path.of(override.trim());
        String os = osName.toLowerCase(Locale.ROOT);
        if (os.startsWith("mac")) return Path.of(home, "Library", "Application Support", "Mirada");
        if (os.startsWith("windows")) {
            return appData != null && !appData.isBlank()
                    ? Path.of(appData.trim(), "Mirada")
                    : Path.of(home, "AppData", "Roaming", "Mirada");
        }
        return Path.of(home, ".mirada");
    }

    /**
     * {@code file}, after moving the settings there from {@code old} if only {@code old} has them;
     * {@code old} itself if the move fails, so the settings are never lost to a start. The emptied
     * {@code ~/.mirada} is removed when nothing else is in it. One check when there is nothing to
     * move, and none on Linux, where the two are the same.
     */
    static Path movedFrom(Path old, Path file) {
        if (old.equals(file) || Files.exists(file) || !Files.isRegularFile(old)) return file;
        try {
            Path folder = file.getParent();
            if (folder != null) Files.createDirectories(folder);
            Files.move(old, file);
        } catch (IOException e) {
            return old;
        }
        Path oldFolder = old.getParent();
        if (oldFolder != null) {
            try {
                Files.deleteIfExists(oldFolder);
            } catch (IOException e) {
                // Something else is in it: it stays.
            }
        }
        return file;
    }

    public Path file() {
        return file;
    }

    /** False after a read that failed on a file that is there: it is not written over this run. */
    private boolean writable = true;

    /**
     * Reads the file; a missing or unreadable one leaves the defaults. One that will not decode is
     * kept as {@code settings.properties.corrupt.bak} ({@link ConfigFile}); one that cannot be read
     * is not written over this run.
     */
    public void load() {
        ConfigFile.Result read = ConfigFile.read(file);
        writable = read.writable();
        Properties properties = read.properties();
        theme = themeOf(properties.getProperty(THEME));
        startWithThumbnails = Boolean.parseBoolean(
                properties.getProperty(START_WITH_THUMBNAILS, "false").trim());
        startWithInfo = Boolean.parseBoolean(
                properties.getProperty(START_WITH_INFO, "false").trim());
        controlsHideSeconds = hideSecondsOf(properties.getProperty(CONTROLS_HIDE_SECONDS));
        sorting = new Sorting(
                Sorting.byOf(properties.getProperty(SORT_BY)),
                Boolean.parseBoolean(
                        properties.getProperty(SORT_REVERSED, "false").trim()));
        keepZoom =
                Boolean.parseBoolean(properties.getProperty(KEEP_ZOOM, "false").trim());
        thumbnailsAtSide = Boolean.parseBoolean(
                properties.getProperty(THUMBNAILS_AT_SIDE, "false").trim());
        thumbnailNames = Boolean.parseBoolean(
                properties.getProperty(THUMBNAIL_NAMES, "false").trim());
        thumbnailCaptions = Boolean.parseBoolean(
                properties.getProperty(THUMBNAIL_CAPTIONS, "false").trim());
        window = windowOf(properties.getProperty(WINDOW_BOUNDS), properties.getProperty(WINDOW_MAXIMIZED));
        List<Path> folders = new ArrayList<>();
        for (int i = 0; i < MAX_RECENT; i++) {
            String folder = properties.getProperty(RECENT + i);
            if (folder == null || folder.isBlank()) continue;
            try {
                folders.add(Path.of(folder.trim()));
            } catch (InvalidPathException e) {
                // A bad edit: that one is dropped.
            }
        }
        recent = List.copyOf(folders);
        slideshowSeconds = slideshowSecondsOf(properties.getProperty(SLIDESHOW_SECONDS));
        paletteKeys = paletteKeysOf(properties.getProperty(PALETTE_KEYS), isMac(System.getProperty("os.name", "")));
    }

    /**
     * The keys that open the command palette by default, as chord tokens ({@code C-S-p} is
     * Ctrl+Shift+P, {@code M-x} is Alt+X, {@code Cmd-} is the Mac's Command key): the palette's own
     * chord, Emacs's {@code M-x}, and F1.
     */
    public static String defaultPaletteKeys(boolean mac) {
        return (mac ? "Cmd-S-p" : "C-S-p") + " M-x f1";
    }

    /**
     * The palette's keys as written, spaces or commas between them, collapsed to single spaces; the
     * default for a blank value. Whether each names a key is the window's to decide: one it cannot
     * read is skipped there, and none readable is the default again.
     */
    static String paletteKeysOf(@Nullable String value, boolean mac) {
        String keys = value == null
                ? ""
                : String.join(" ", value.trim().split("[\\s,]+")).trim();
        return keys.isEmpty() ? defaultPaletteKeys(mac) : keys;
    }

    private static boolean isMac(String osName) {
        return osName.toLowerCase(Locale.ROOT).startsWith("mac");
    }

    static int slideshowSecondsOf(@Nullable String value) {
        if (value == null) return DEFAULT_SLIDESHOW_SECONDS;
        try {
            return Math.clamp(Integer.parseInt(value.trim()), MIN_SLIDESHOW_SECONDS, MAX_SLIDESHOW_SECONDS);
        } catch (NumberFormatException e) {
            return DEFAULT_SLIDESHOW_SECONDS;
        }
    }

    static ThemeChoice themeOf(@Nullable String value) {
        if (value == null) return ThemeChoice.SYSTEM;
        try {
            return ThemeChoice.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return ThemeChoice.SYSTEM;
        }
    }

    static int hideSecondsOf(@Nullable String value) {
        if (value == null) return 2;
        try {
            return Math.clamp(Integer.parseInt(value.trim()), MIN_HIDE_SECONDS, MAX_HIDE_SECONDS);
        } catch (NumberFormatException e) {
            return 2;
        }
    }

    public ThemeChoice theme() {
        return theme;
    }

    public boolean startWithThumbnails() {
        return startWithThumbnails;
    }

    public boolean startWithInfo() {
        return startWithInfo;
    }

    public int controlsHideSeconds() {
        return controlsHideSeconds;
    }

    /** The folder's order. */
    public Sorting sorting() {
        return sorting;
    }

    /** Whether stepping to another image keeps the zoom and position instead of fitting it. */
    public boolean keepZoom() {
        return keepZoom;
    }

    /** Whether the thumbnails stand in a column on the left instead of a row along the bottom. */
    public boolean thumbnailsAtSide() {
        return thumbnailsAtSide;
    }

    /** Whether the thumbnail strip lists file names instead of pictures. */
    public boolean thumbnailNames() {
        return thumbnailNames;
    }

    /**
     * Where the window was when it last closed: its size and place when not maximised, and whether
     * it was. Screen coordinates.
     */
    public record WindowPlace(double x, double y, double width, double height, boolean maximized) {}

    /** Where the window last was, or null before it has closed once (or after a bad edit). */
    public @Nullable WindowPlace window() {
        return window;
    }

    public void setWindow(WindowPlace value) {
        window = value;
        changed();
    }

    /** {@code x,y,width,height}, or null for anything that is not four finite numbers of a sane size. */
    static @Nullable WindowPlace windowOf(@Nullable String bounds, @Nullable String maximized) {
        if (bounds == null) return null;
        String[] parts = bounds.split(",");
        if (parts.length != 4) return null;
        double[] values = new double[4];
        try {
            for (int i = 0; i < 4; i++) values[i] = Double.parseDouble(parts[i].trim());
        } catch (NumberFormatException e) {
            return null;
        }
        for (double value : values) if (!Double.isFinite(value)) return null;
        if (values[2] < 100 || values[3] < 100) return null;
        return new WindowPlace(
                values[0],
                values[1],
                values[2],
                values[3],
                Boolean.parseBoolean(String.valueOf(maximized).trim()));
    }

    /** The folders opened lately, the latest first; some may be gone since. */
    public List<Path> recent() {
        return recent;
    }

    /** Puts {@code folder} first among the recent ones; nothing is written when it already is. */
    public void addRecent(Path folder) {
        List<Path> next = withRecent(recent, folder);
        if (next.equals(recent)) return;
        recent = next;
        changed();
    }

    /** {@code folders} with {@code folder} first and no copy of it further down, at most {@link #MAX_RECENT}. */
    static List<Path> withRecent(List<Path> folders, Path folder) {
        List<Path> next = new ArrayList<>(MAX_RECENT);
        next.add(folder);
        for (Path other : folders) {
            if (next.size() == MAX_RECENT) break;
            if (!other.equals(folder)) next.add(other);
        }
        return List.copyOf(next);
    }

    /** Whether each thumbnail has its file name under it. */
    public boolean thumbnailCaptions() {
        return thumbnailCaptions;
    }

    public void setThumbnailCaptions(boolean value) {
        thumbnailCaptions = value;
        changed();
    }

    public void setThumbnailsAtSide(boolean value) {
        thumbnailsAtSide = value;
        changed();
    }

    public void setThumbnailNames(boolean value) {
        thumbnailNames = value;
        changed();
    }

    public int slideshowSeconds() {
        return slideshowSeconds;
    }

    /** The keys that open the command palette, as chord tokens separated by spaces. */
    public String paletteKeys() {
        return paletteKeys;
    }

    public void setPaletteKeys(String value) {
        String keys = paletteKeysOf(value, isMac(System.getProperty("os.name", "")));
        if (keys.equals(paletteKeys)) return;
        paletteKeys = keys;
        changed();
    }

    public void setSorting(Sorting value) {
        sorting = value;
        changed();
    }

    public void setKeepZoom(boolean value) {
        keepZoom = value;
        changed();
    }

    public void setSlideshowSeconds(int value) {
        slideshowSeconds = Math.clamp(value, MIN_SLIDESHOW_SECONDS, MAX_SLIDESHOW_SECONDS);
        changed();
    }

    public void setTheme(ThemeChoice value) {
        theme = value;
        changed();
    }

    public void setStartWithThumbnails(boolean value) {
        startWithThumbnails = value;
        changed();
    }

    public void setStartWithInfo(boolean value) {
        startWithInfo = value;
        changed();
    }

    public void setControlsHideSeconds(int value) {
        controlsHideSeconds = Math.clamp(value, MIN_HIDE_SECONDS, MAX_HIDE_SECONDS);
        changed();
    }

    /** Called after every change, once it is saved, so whatever shows a setting can apply it live. */
    public void addListener(Runnable listener) {
        listeners.add(listener);
    }

    private void changed() {
        save();
        for (Runnable listener : List.copyOf(listeners)) listener.run();
    }

    /**
     * Writes the file through a temporary one and a move, so a crash mid-write leaves the old file
     * rather than half of the new one. A failure is swallowed: the setting still applies to this run.
     */
    void save() {
        if (!writable) return;
        Properties properties = new Properties();
        properties.setProperty(THEME, theme.name().toLowerCase(Locale.ROOT));
        properties.setProperty(START_WITH_THUMBNAILS, Boolean.toString(startWithThumbnails));
        properties.setProperty(START_WITH_INFO, Boolean.toString(startWithInfo));
        properties.setProperty(CONTROLS_HIDE_SECONDS, Integer.toString(controlsHideSeconds));
        properties.setProperty(SORT_BY, sorting.by().name().toLowerCase(Locale.ROOT));
        properties.setProperty(SORT_REVERSED, Boolean.toString(sorting.reversed()));
        properties.setProperty(KEEP_ZOOM, Boolean.toString(keepZoom));
        properties.setProperty(THUMBNAILS_AT_SIDE, Boolean.toString(thumbnailsAtSide));
        properties.setProperty(THUMBNAIL_NAMES, Boolean.toString(thumbnailNames));
        properties.setProperty(THUMBNAIL_CAPTIONS, Boolean.toString(thumbnailCaptions));
        for (int i = 0; i < recent.size(); i++)
            properties.setProperty(RECENT + i, recent.get(i).toString());
        WindowPlace place = window;
        if (place != null) {
            properties.setProperty(
                    WINDOW_BOUNDS,
                    Math.round(place.x()) + "," + Math.round(place.y()) + "," + Math.round(place.width()) + ","
                            + Math.round(place.height()));
            properties.setProperty(WINDOW_MAXIMIZED, Boolean.toString(place.maximized()));
        }
        properties.setProperty(SLIDESHOW_SECONDS, Integer.toString(slideshowSeconds));
        properties.setProperty(PALETTE_KEYS, paletteKeys);
        try {
            Path folder = file.toAbsolutePath().getParent();
            if (folder != null) Files.createDirectories(folder);
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            try (Writer out = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                properties.store(out, "Mirada settings. Edit freely; bad values fall back to defaults.");
            }
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            // Unwritable home: the change still applies until Mirada closes.
        }
    }
}
