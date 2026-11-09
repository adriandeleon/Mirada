package com.mirada.settings;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

import org.jspecify.annotations.Nullable;

/**
 * Reading a config file that may be broken or newer than this build (SET-09 to SET-11, Pauta
 * 4.0.1): a bad file never stops a start and is never lost to one. A file that will not decode (a
 * malformed {@code \\uXXXX} escape, bytes that are not UTF-8) is moved aside to {@code
 * <name>.corrupt.bak}, where it can be mended, and the defaults load. A file a newer Mirada wrote is
 * copied to {@code <name>.v<n>.bak} and the defaults load, so this build never clobbers it unseen;
 * an older one is brought forward by its {@link Migrations.Schema}. A file that cannot be read at
 * all (permissions, a lock) is left where it is, and the caller must not write over it this run.
 */
final class ConfigFile {

    static final String CORRUPT_SUFFIX = ".corrupt.bak";

    enum Outcome {
        /** Read as it is, or brought forward from an older version. */
        READ,
        /** Not there: the defaults, written on the first change. */
        MISSING,
        /** Would not decode: moved to {@link #CORRUPT_SUFFIX}, and the defaults load. */
        SET_ASIDE,
        /** Written by a newer Mirada: copied to {@code <name>.v<n>.bak}, and the defaults load. */
        NEWER,
        /** Could not be read: the defaults for this run, and the file left alone. */
        UNREADABLE
    }

    /**
     * @param backup where the file was kept, for {@link Outcome#SET_ASIDE} and {@link Outcome#NEWER}
     *     when the copy was made
     */
    record Result(
            Properties properties,
            Outcome outcome,
            @Nullable Path backup) {

        Result(Properties properties, Outcome outcome) {
            this(properties, outcome, null);
        }

        /** Whether the file may be written: never over one that is there and was not read, or kept. */
        boolean writable() {
            return outcome != Outcome.UNREADABLE && !(outcome == Outcome.NEWER && backup == null);
        }
    }

    private ConfigFile() {}

    /**
     * A file's last-modified time and size, to tell cheaply whether another process wrote it since:
     * {@link #NONE} for one that is not there.
     */
    record Stamp(long modified, long size) {

        static final Stamp NONE = new Stamp(-1, -1);

        static Stamp of(Path file) {
            try {
                return new Stamp(Files.getLastModifiedTime(file).toMillis(), Files.size(file));
            } catch (IOException e) {
                return NONE;
            }
        }
    }

    /**
     * Three files in one: {@code theirs}, the file as another process left it, with every key
     * {@code ours} changed since {@code base} (what this process last read or wrote) set or removed
     * as {@code ours} has it. A key both changed is ours, the change being made now. Pure.
     */
    static Properties merged(Properties base, Properties ours, Properties theirs) {
        Properties out = new Properties();
        out.putAll(theirs);
        java.util.Set<String> keys = new java.util.HashSet<>(base.stringPropertyNames());
        keys.addAll(ours.stringPropertyNames());
        for (String key : keys) {
            String now = ours.getProperty(key);
            if (java.util.Objects.equals(base.getProperty(key), now)) continue;
            if (now == null) out.remove(key);
            else out.setProperty(key, now);
        }
        return out;
    }

    /** Reads {@code file} as it is, unversioned. */
    static Result read(Path file) {
        Properties properties = new Properties();
        if (!Files.isRegularFile(file)) return new Result(properties, Outcome.MISSING);
        try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(in);
            return new Result(properties, Outcome.READ);
        } catch (IllegalArgumentException | CharacterCodingException e) {
            Path backup = backupOf(file);
            return moved(file, backup)
                    ? new Result(new Properties(), Outcome.SET_ASIDE, backup)
                    : new Result(new Properties(), Outcome.UNREADABLE);
        } catch (IOException e) {
            return new Result(new Properties(), Outcome.UNREADABLE);
        }
    }

    /**
     * Reads {@code file} and brings it to {@code schema}'s version: a file a newer Mirada wrote is
     * copied to {@link #newerBackupOf} and the defaults load. The properties come back without
     * {@code schemaVersion}.
     */
    static Result read(Path file, Migrations.Schema schema) {
        Result read = read(file);
        if (read.outcome() != Outcome.READ) return read;
        int version = Migrations.versionOf(read.properties());
        if (version > schema.version()) {
            Path backup = newerBackupOf(file, version);
            return new Result(new Properties(), Outcome.NEWER, copied(file, backup) ? backup : null);
        }
        return new Result(Migrations.migrate(read.properties(), schema), Outcome.READ);
    }

    /** Where a file that would not decode is kept. */
    static Path backupOf(Path file) {
        return file.resolveSibling(file.getFileName() + CORRUPT_SUFFIX);
    }

    /** Where a file version {@code version} of a newer Mirada wrote is kept: {@code <name>.v<n>.bak}. */
    static Path newerBackupOf(Path file, int version) {
        return file.resolveSibling(file.getFileName() + ".v" + version + ".bak");
    }

    private static boolean moved(Path file, Path to) {
        try {
            Files.move(file, to, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean copied(Path file, Path to) {
        try {
            Files.copy(file, to, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
