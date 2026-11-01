package com.mirada.settings;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

import org.jspecify.annotations.Nullable;

/**
 * Mirada's settings, in {@code ~/.mirada/settings.properties} ({@code MIRADA_CONFIG_DIR} moves the
 * folder, for tests and timing runs), Termina's way: a plain file meant to be hand-edited, every
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

    public static final int MIN_HIDE_SECONDS = 1;
    public static final int MAX_HIDE_SECONDS = 10;

    private final Path file;
    private final List<Runnable> listeners = new ArrayList<>();

    private ThemeChoice theme = ThemeChoice.SYSTEM;
    private boolean startWithThumbnails;
    private boolean startWithInfo;
    private int controlsHideSeconds = 2;

    public Settings(Path file) {
        this.file = file;
    }

    /** {@code ~/.mirada/settings.properties}, or under {@code MIRADA_CONFIG_DIR} when it is set. */
    public static Path defaultFile() {
        return defaultFile(System.getenv("MIRADA_CONFIG_DIR"), System.getProperty("user.home", "."));
    }

    static Path defaultFile(@Nullable String override, String home) {
        Path folder = override != null && !override.isBlank() ? Path.of(override.trim()) : Path.of(home, ".mirada");
        return folder.resolve("settings.properties");
    }

    public Path file() {
        return file;
    }

    /** Reads the file; a missing or unreadable one leaves the defaults. */
    public void load() {
        Properties properties = new Properties();
        if (Files.isRegularFile(file)) {
            try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                properties.load(in);
            } catch (IOException | IllegalArgumentException e) {
                // A file that will not parse is the defaults, not a failed start.
            }
        }
        theme = themeOf(properties.getProperty(THEME));
        startWithThumbnails = Boolean.parseBoolean(
                properties.getProperty(START_WITH_THUMBNAILS, "false").trim());
        startWithInfo = Boolean.parseBoolean(
                properties.getProperty(START_WITH_INFO, "false").trim());
        controlsHideSeconds = hideSecondsOf(properties.getProperty(CONTROLS_HIDE_SECONDS));
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
        Properties properties = new Properties();
        properties.setProperty(THEME, theme.name().toLowerCase(Locale.ROOT));
        properties.setProperty(START_WITH_THUMBNAILS, Boolean.toString(startWithThumbnails));
        properties.setProperty(START_WITH_INFO, Boolean.toString(startWithInfo));
        properties.setProperty(CONTROLS_HIDE_SECONDS, Integer.toString(controlsHideSeconds));
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
