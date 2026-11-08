package com.mirada.settings;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

/**
 * The user's bookmarks and notes on images and folders, Editora's idea moved to pictures: a mark on
 * a file or a folder, a note of the user's own on it, or both, kept by Mirada and never written into
 * the file. In {@code bookmarks.properties} beside the settings ({@link #fileBeside}), plain enough
 * to hand-edit:
 *
 * <pre>
 * mark.0.path=/home/me/Pictures/2026/IMG_0412.jpg
 * mark.0.bookmark=true
 * mark.0.note=The lake at dawn; print this one
 * mark.1.path=/home/me/Pictures/2026
 * mark.1.folder=true
 * mark.1.note=Holiday, not sorted yet
 * </pre>
 *
 * <p>The marks are in the order they were made, the oldest first. A path is kept absolute and
 * normalised, so a photo opened by a relative path is the same photo. A mark whose file has gone is
 * kept (its drive may be unplugged); the lists that offer marks leave it out.
 *
 * <p>Every change reads the file again first and writes it straight after, so two Mirada windows
 * add to each other's marks instead of overwriting them. Read lazily ({@link #ensureLoaded}): start-up
 * never waits for it. FX thread only.
 */
public final class Bookmarks {

    /**
     * What is kept on one image or folder: whether it is bookmarked, and the user's note on it ({@code
     * ""} for none). A mark with neither is not kept.
     */
    public record Mark(Path path, boolean folder, boolean bookmarked, String note) {

        public Mark {
            path = key(path);
            note = note.strip();
        }

        /** Neither bookmarked nor noted: nothing to keep. */
        boolean isEmpty() {
            return !bookmarked && note.isEmpty();
        }

        public boolean hasNote() {
            return !note.isEmpty();
        }
    }

    static final String FILE_NAME = "bookmarks.properties";
    private static final Pattern PATH_KEY = Pattern.compile("mark\\.(\\d+)\\.path");

    private final Path file;
    /** By key ({@link #key}), the oldest first. */
    private final Map<Path, Mark> marks = new LinkedHashMap<>();

    private final List<Runnable> listeners = new ArrayList<>();
    private boolean loaded;

    public Bookmarks(Path file) {
        this.file = file;
    }

    /** {@code bookmarks.properties} in the folder that holds {@code settingsFile}. */
    public static Path fileBeside(Path settingsFile) {
        return settingsFile.resolveSibling(FILE_NAME);
    }

    public Path file() {
        return file;
    }

    /** The path as marks are kept by: absolute, without {@code .} or {@code ..}. */
    public static Path key(Path path) {
        return path.toAbsolutePath().normalize();
    }

    /** Reads the file the first time anything asks; a missing or unreadable one is no marks. */
    public void ensureLoaded() {
        if (!loaded) load();
    }

    /** Whether the file has been read, so a mark shown now is the real one. */
    public boolean loaded() {
        return loaded;
    }

    /** Reads the file again, dropping what was read before. */
    public void load() {
        loaded = true;
        marks.clear();
        Properties properties = new Properties();
        if (Files.isRegularFile(file)) {
            try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                properties.load(in);
            } catch (IOException | IllegalArgumentException e) {
                return; // A file that will not parse is no marks, not a failed start.
            }
        }
        for (Mark mark : marksOf(properties)) {
            marks.remove(mark.path()); // a hand-made copy: the later one, in its place
            marks.put(mark.path(), mark);
        }
    }

    /**
     * The marks in {@code properties}, by their numbers: gaps and numbers out of order are a hand
     * edit, not an error. A mark with no path, a path that is not one, or nothing to keep is dropped.
     */
    static List<Mark> marksOf(Properties properties) {
        TreeMap<Long, Mark> numbered = new TreeMap<>();
        for (String name : properties.stringPropertyNames()) {
            Matcher matcher = PATH_KEY.matcher(name);
            if (!matcher.matches()) continue;
            long number;
            try {
                number = Long.parseLong(matcher.group(1));
            } catch (NumberFormatException e) {
                continue;
            }
            String prefix = "mark." + matcher.group(1) + ".";
            String value = properties.getProperty(name, "").strip();
            if (value.isEmpty()) continue;
            Path path;
            try {
                path = Path.of(value);
            } catch (InvalidPathException e) {
                continue;
            }
            Mark mark = new Mark(
                    path,
                    flag(properties.getProperty(prefix + "folder")),
                    flag(properties.getProperty(prefix + "bookmark")),
                    properties.getProperty(prefix + "note", ""));
            if (!mark.isEmpty()) numbered.put(number, mark);
        }
        return List.copyOf(numbered.values());
    }

    private static boolean flag(@Nullable String value) {
        return value != null && Boolean.parseBoolean(value.strip());
    }

    /** What is kept on {@code path}, or null for nothing. */
    public @Nullable Mark get(Path path) {
        ensureLoaded();
        return marks.get(key(path));
    }

    public boolean isBookmarked(Path path) {
        Mark mark = get(path);
        return mark != null && mark.bookmarked();
    }

    /** The note on {@code path}, or {@code ""} for none. */
    public String note(Path path) {
        Mark mark = get(path);
        return mark == null ? "" : mark.note();
    }

    /** Every mark, the latest first. */
    public List<Mark> all() {
        ensureLoaded();
        return List.copyOf(marks.values()).reversed();
    }

    /**
     * Bookmarks {@code path}, or takes its bookmark off when it has one; its note stays. A new
     * bookmark is the latest mark. Returns whether it is bookmarked now.
     */
    public boolean toggle(Path path, boolean folder) {
        load();
        Path key = key(path);
        Mark was = marks.get(key);
        boolean now = was == null || !was.bookmarked();
        Mark mark = new Mark(key, folder, now, was == null ? "" : was.note());
        if (now) marks.remove(key); // to the end: the latest
        put(mark);
        changed();
        return now;
    }

    /** Sets the note on {@code path}; a blank one takes it off. Its bookmark, if any, stays. */
    public void setNote(Path path, boolean folder, String note) {
        load();
        Path key = key(path);
        Mark was = marks.get(key);
        Mark mark = new Mark(key, folder, was != null && was.bookmarked(), note);
        if (was != null && was.equals(mark)) return;
        put(mark);
        changed();
    }

    /** Takes the whole mark off {@code path}, its bookmark and its note. Returns whether it had one. */
    public boolean remove(Path path) {
        load();
        if (marks.remove(key(path)) == null) return false;
        changed();
        return true;
    }

    /**
     * Carries the mark on {@code from} to {@code to}, after a rename: in its place in the order, as
     * the same image. Nothing is written when there is none.
     */
    public void moved(Path from, Path to) {
        load();
        Path old = key(from);
        Mark mark = marks.get(old);
        if (mark == null) return;
        Path key = key(to);
        Map<Path, Mark> renamed = new LinkedHashMap<>();
        for (Map.Entry<Path, Mark> entry : marks.entrySet()) {
            if (entry.getKey().equals(old)) {
                renamed.put(key, new Mark(key, mark.folder(), mark.bookmarked(), mark.note()));
            } else if (!entry.getKey().equals(key)) {
                renamed.put(entry.getKey(), entry.getValue());
            }
        }
        marks.clear();
        marks.putAll(renamed);
        changed();
    }

    private void put(Mark mark) {
        if (mark.isEmpty()) marks.remove(mark.path());
        else marks.put(mark.path(), mark);
    }

    /**
     * The index of the next image of {@code images} after {@code from} (or before it, {@code
     * direction} -1) that {@code marked} says is marked, or -1 when there is none that way. No
     * wrapping round, as stepping through the folder does not.
     */
    public static int step(List<Path> images, int from, int direction, Predicate<Path> marked) {
        int step = direction < 0 ? -1 : 1;
        for (int i = from + step; i >= 0 && i < images.size(); i += step) {
            if (marked.test(images.get(i))) return i;
        }
        return -1;
    }

    /** Called after every change, once it is saved, so whatever shows a mark can show it again. */
    public void addListener(Runnable listener) {
        listeners.add(listener);
    }

    private void changed() {
        save();
        for (Runnable listener : List.copyOf(listeners)) listener.run();
    }

    /** The marks as the file holds them, numbered from 0 in order, the oldest first. */
    static Properties propertiesOf(List<Mark> marks) {
        Properties properties = new Properties();
        for (int i = 0; i < marks.size(); i++) {
            Mark mark = marks.get(i);
            String prefix = "mark." + i + ".";
            properties.setProperty(prefix + "path", mark.path().toString());
            if (mark.folder()) properties.setProperty(prefix + "folder", "true");
            if (mark.bookmarked()) properties.setProperty(prefix + "bookmark", "true");
            if (mark.hasNote()) properties.setProperty(prefix + "note", mark.note());
        }
        return properties;
    }

    /**
     * Writes the file through a temporary one and a move, as the settings are written. A failure is
     * swallowed: the marks still hold for this run.
     */
    private void save() {
        Properties properties = propertiesOf(List.copyOf(marks.values()));
        try {
            Path folder = file.toAbsolutePath().getParent();
            if (folder != null) Files.createDirectories(folder);
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            try (Writer out = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                properties.store(
                        out,
                        "Mirada's bookmarks and notes on images and folders. Edit freely: mark.N.path,"
                                + " .bookmark, .folder and .note; a mark with neither a bookmark nor a note is dropped.");
            }
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            // Unwritable folder: the marks still hold until Mirada closes.
        }
    }
}
