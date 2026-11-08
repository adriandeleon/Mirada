package com.mirada.settings;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/**
 * Reading a config file that may be broken (SET-11, Pauta 4.0.1): a bad file never stops a start
 * and is never lost to one. A file that will not decode (a malformed {@code \\uXXXX} escape, bytes
 * that are not UTF-8) is moved aside to {@code <name>.corrupt.bak}, where it can be mended, and the
 * defaults load. A file that cannot be read at all (permissions, a lock) is left where it is, and the
 * caller must not write over it this run.
 */
final class ConfigFile {

    static final String CORRUPT_SUFFIX = ".corrupt.bak";

    enum Outcome {
        /** Read as it is. */
        READ,
        /** Not there: the defaults, written on the first change. */
        MISSING,
        /** Would not decode: moved to {@link #CORRUPT_SUFFIX}, and the defaults load. */
        SET_ASIDE,
        /** Could not be read: the defaults for this run, and the file left alone. */
        UNREADABLE
    }

    record Result(Properties properties, Outcome outcome) {

        /** Whether the file may be written: never over one that is there and was not read. */
        boolean writable() {
            return outcome != Outcome.UNREADABLE;
        }
    }

    private ConfigFile() {}

    static Result read(Path file) {
        Properties properties = new Properties();
        if (!Files.isRegularFile(file)) return new Result(properties, Outcome.MISSING);
        try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(in);
            return new Result(properties, Outcome.READ);
        } catch (IllegalArgumentException | CharacterCodingException e) {
            return new Result(new Properties(), setAside(file) ? Outcome.SET_ASIDE : Outcome.UNREADABLE);
        } catch (IOException e) {
            return new Result(new Properties(), Outcome.UNREADABLE);
        }
    }

    /** Where a file that would not decode is kept. */
    static Path backupOf(Path file) {
        return file.resolveSibling(file.getFileName() + CORRUPT_SUFFIX);
    }

    private static boolean setAside(Path file) {
        try {
            Files.move(file, backupOf(file), StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
