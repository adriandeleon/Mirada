package com.mirada.settings;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.EnumSet;
import java.util.Properties;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

import org.jspecify.annotations.Nullable;

import static com.mirada.i18n.Messages.tr;

/**
 * Import Configuration (SET-12, Pauta 4.0.1): reads a {@code .zip} made by {@link ConfigExport},
 * says which parts it holds, and replaces only the parts the user keeps ticked; the rest of the
 * config folder stays as it is.
 *
 * <p>A zip is refused before anything is written when it is not a zip, has no {@code
 * settings.properties} at its root, names a path outside the folder, holds an entry or a total over
 * the limits below, has a file that will not decode, or was written by a newer Mirada. Sizes are
 * counted as the entries inflate, not taken from the zip's own headers. What is this computer's own
 * (the window's place, the recent folders, the last update check) is never imported.
 *
 * <p>No JavaFX, so it runs off the FX thread; the javafx-app-template's {@code ConfigImport} for
 * Mirada's two properties files.
 */
public final class ConfigImport {

    /** Largest single file taken from a zip. */
    static final long MAX_ENTRY_BYTES = 4L * 1024 * 1024;
    /** Largest total taken from a zip. */
    static final long MAX_TOTAL_BYTES = 16L * 1024 * 1024;
    /** Most entries an import looks at: an export has two. */
    static final int MAX_ENTRIES = 100;

    /** What can be imported, in the order the question lists them. */
    public enum Part {
        /** Every setting but the keys: theme, what opens at start, the folder's order, … */
        PREFERENCES("import.part.preferences", "import.part.preferences.inSentence"),
        /** The keymap, the user's own keys and the palette's. */
        KEYS("import.part.keys", "import.part.keys.inSentence"),
        /** {@code bookmarks.properties}: every bookmark and note. */
        BOOKMARKS("import.part.bookmarks", "import.part.bookmarks.inSentence");

        private final String labelKey;
        private final String inSentenceKey;

        Part(String labelKey, String inSentenceKey) {
            this.labelKey = labelKey;
            this.inSentenceKey = inSentenceKey;
        }

        /** How the question names it, in the interface's language. */
        public String label() {
            return tr(labelKey);
        }

        /** How a sentence names it ("the keys"), for what the status bar says after an import. */
        public String inSentence() {
            return tr(inSentenceKey);
        }
    }

    /** Why a zip is not imported; the message names the reason and the entry. */
    public static final class Refused extends IOException {

        public enum Reason {
            NOT_A_ZIP,
            NOT_AN_EXPORT,
            UNSAFE_PATH,
            TOO_LARGE,
            NEWER_VERSION
        }

        private final Reason reason;

        Refused(Reason reason, String detail) {
            super(reason + (detail.isEmpty() ? "" : ": " + detail));
            this.reason = reason;
        }

        public Reason reason() {
            return reason;
        }

        /** The reason as the status bar says it. */
        public String sentence() {
            return switch (reason) {
                case NOT_A_ZIP -> tr("import.refused.notAZip");
                case NOT_AN_EXPORT -> tr("import.refused.notAnExport");
                case UNSAFE_PATH -> tr("import.refused.unsafePath");
                case TOO_LARGE -> tr("import.refused.tooLarge");
                case NEWER_VERSION -> tr("import.refused.newerVersion");
            };
        }
    }

    /** A zip that passed every check: its settings and bookmarks, brought to this build's versions. */
    public record Scan(Properties settings, @Nullable Properties bookmarks) {

        /** The parts this zip holds. */
        public Set<Part> available() {
            Set<Part> parts = EnumSet.noneOf(Part.class);
            for (String key : settings.stringPropertyNames()) {
                Part part = partOf(key);
                if (part != null) parts.add(part);
            }
            if (bookmarks != null) parts.add(Part.BOOKMARKS);
            return parts;
        }
    }

    private ConfigImport() {}

    /**
     * The part a {@code settings.properties} key belongs to, or null for this computer's own: the
     * window's place, the recent folders, the last update check, the file's version.
     */
    static @Nullable Part partOf(String key) {
        if (key.equals(Migrations.VERSION_KEY)
                || key.equals(Settings.WINDOW_BOUNDS)
                || key.equals(Settings.WINDOW_MAXIMIZED)
                || key.equals(Settings.LAST_UPDATE_CHECK)
                || key.startsWith(Settings.RECENT)) return null;
        if (key.equals(Settings.KEYMAP) || key.equals(Settings.PALETTE_KEYS) || key.startsWith(Settings.KEYS))
            return Part.KEYS;
        return Part.PREFERENCES;
    }

    /** Reads and checks {@code zip}; nothing is written. */
    public static Scan read(Path zip) throws IOException {
        byte[] settings = null;
        byte[] bookmarks = null;
        long total = 0;
        int entries = 0;
        boolean sawEntry = false;
        try (InputStream in = Files.newInputStream(zip);
                ZipInputStream zipIn = new ZipInputStream(in)) {
            for (ZipEntry entry; (entry = zipIn.getNextEntry()) != null; ) {
                sawEntry = true;
                if (++entries > MAX_ENTRIES) throw new Refused(Refused.Reason.TOO_LARGE, entries + " entries");
                String name = entry.getName();
                if (!safe(name)) throw new Refused(Refused.Reason.UNSAFE_PATH, name);
                if (entry.isDirectory()) continue;
                byte[] bytes = inflate(zipIn, name);
                total += bytes.length;
                if (total > MAX_TOTAL_BYTES) throw new Refused(Refused.Reason.TOO_LARGE, "total");
                if (name.equals(Settings.FILE_NAME)) settings = bytes;
                else if (name.equals(Bookmarks.FILE_NAME)) bookmarks = bytes;
                // Anything else is a newer Mirada's file this one has no use for.
            }
        } catch (ZipException e) {
            throw new Refused(Refused.Reason.NOT_A_ZIP, String.valueOf(e.getMessage()));
        }
        if (!sawEntry) throw new Refused(Refused.Reason.NOT_A_ZIP, "");
        if (settings == null) throw new Refused(Refused.Reason.NOT_AN_EXPORT, "no " + Settings.FILE_NAME);
        return new Scan(
                checked(settings, Migrations.SETTINGS),
                bookmarks == null ? null : checked(bookmarks, Migrations.BOOKMARKS));
    }

    /** One file of the zip, decoded and brought forward; refused when it will not or is too new. */
    private static Properties checked(byte[] bytes, Migrations.Schema schema) throws Refused {
        Properties properties = new Properties();
        try {
            String text = StandardCharsets.UTF_8
                    .newDecoder()
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
            properties.load(new StringReader(text));
        } catch (CharacterCodingException | IllegalArgumentException e) {
            throw new Refused(Refused.Reason.NOT_AN_EXPORT, schema.file());
        } catch (IOException e) {
            throw new Refused(Refused.Reason.NOT_AN_EXPORT, schema.file()); // a StringReader does not fail
        }
        int version = Migrations.versionOf(properties);
        if (version > schema.version()) throw new Refused(Refused.Reason.NEWER_VERSION, schema.file() + " " + version);
        return Migrations.migrate(properties, schema);
    }

    /**
     * Replaces the ticked {@code parts} of the config folder that holds {@code settingsFile} with the
     * zip's: in {@code settings.properties} the keys of each ticked part (those of the part the zip
     * lacks are removed, the other parts' kept as they are here), and {@code bookmarks.properties}
     * whole. Returns the parts replaced. The live {@link Settings} and {@link Bookmarks} must then
     * {@code reload()}.
     */
    public static Set<Part> apply(Scan scan, Path settingsFile, Set<Part> parts) throws IOException {
        Set<Part> done = EnumSet.noneOf(Part.class);
        Set<Part> settingsParts = EnumSet.noneOf(Part.class);
        for (Part part : parts) if (part != Part.BOOKMARKS) settingsParts.add(part);
        if (!settingsParts.isEmpty()) {
            Properties current =
                    ConfigFile.read(settingsFile, Migrations.SETTINGS).properties();
            Properties merged = merged(current, scan.settings(), settingsParts);
            write(settingsFile, merged, Settings.SCHEMA_VERSION, "Mirada settings, imported.");
            Set<Part> available = scan.available();
            for (Part part : settingsParts) if (available.contains(part)) done.add(part);
        }
        Properties bookmarks = scan.bookmarks();
        if (parts.contains(Part.BOOKMARKS) && bookmarks != null) {
            write(
                    Bookmarks.fileBeside(settingsFile),
                    bookmarks,
                    Bookmarks.SCHEMA_VERSION,
                    "Mirada's bookmarks and notes, imported.");
            done.add(Part.BOOKMARKS);
        }
        return done;
    }

    /** {@code current} with every key of {@code parts} taken from {@code imported} instead. Pure. */
    static Properties merged(Properties current, Properties imported, Set<Part> parts) {
        Properties out = new Properties();
        for (String key : current.stringPropertyNames()) {
            Part part = partOf(key);
            if (part == null || !parts.contains(part)) out.setProperty(key, current.getProperty(key));
        }
        for (String key : imported.stringPropertyNames()) {
            Part part = partOf(key);
            if (part != null && parts.contains(part)) out.setProperty(key, imported.getProperty(key));
        }
        return out;
    }

    /** A relative path inside the folder: no {@code ..}, no absolute or drive path, no backslash. */
    static boolean safe(String name) {
        if (name.isEmpty() || name.startsWith("/") || name.contains("\\") || name.contains(":")) return false;
        for (String segment : name.split("/")) {
            if (segment.equals("..")) return false;
        }
        return true;
    }

    private static byte[] inflate(InputStream in, String name) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long size = 0;
        for (int n; (n = in.read(buffer)) > 0; ) {
            size += n;
            if (size > MAX_ENTRY_BYTES) throw new Refused(Refused.Reason.TOO_LARGE, name);
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }

    /** Writes {@code properties} at {@code version} through a temporary file and a move. */
    private static void write(Path file, Properties properties, int version, String comment) throws IOException {
        Properties out = new Properties();
        out.putAll(properties);
        out.setProperty(Migrations.VERSION_KEY, Integer.toString(version));
        Path folder = file.toAbsolutePath().getParent();
        if (folder != null) Files.createDirectories(folder);
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
            out.store(writer, comment);
        }
        try {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
