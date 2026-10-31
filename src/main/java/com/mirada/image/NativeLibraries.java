package com.mirada.image;

import java.lang.foreign.Arena;
import java.lang.foreign.SymbolLookup;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Finds a native codec library: first in the folder an installer bundles them in, then wherever the
 * system keeps them.
 *
 * <p>The installers carry libheif and libjxl in a folder of their own and pass it as {@value
 * #BUNDLE_PROPERTY}; a development run has no such folder and uses the system's copies (apt on
 * Linux, Homebrew on macOS). A library found nowhere is not an error here: its formats then open
 * with a message saying what is missing.
 */
final class NativeLibraries {

    /** System property naming the bundled codecs folder. */
    static final String BUNDLE_PROPERTY = "mirada.codecs";

    /** Where Homebrew installs on Apple silicon and on Intel; not on the default dlopen path. */
    private static final List<Path> EXTRA_DIRECTORIES =
            List.of(Path.of("/opt/homebrew/lib"), Path.of("/usr/local/lib"));

    private NativeLibraries() {}

    /** The first of {@code fileNames} that loads, searched in the bundle, the system, then Homebrew. */
    static Optional<SymbolLookup> load(List<String> fileNames) {
        String bundle = System.getProperty(BUNDLE_PROPERTY);
        if (bundle != null && !bundle.isBlank()) {
            for (String name : fileNames) {
                Optional<SymbolLookup> found = open(Path.of(bundle).resolve(name));
                if (found.isPresent()) return found;
            }
        }
        for (String name : fileNames) {
            try {
                return Optional.of(SymbolLookup.libraryLookup(name, Arena.global()));
            } catch (IllegalArgumentException notOnTheSearchPath) {
                // try the next name
            }
        }
        for (Path directory : EXTRA_DIRECTORIES) {
            for (String name : fileNames) {
                Optional<SymbolLookup> found = open(directory.resolve(name));
                if (found.isPresent()) return found;
            }
        }
        return Optional.empty();
    }

    private static Optional<SymbolLookup> open(Path file) {
        if (!Files.isRegularFile(file)) return Optional.empty();
        try {
            return Optional.of(SymbolLookup.libraryLookup(file, Arena.global()));
        } catch (IllegalArgumentException unloadable) {
            return Optional.empty();
        }
    }

    /**
     * A library's file names on {@code os} (an {@code os.name}), for each ABI version in turn:
     * {@code libheif.so.1} on Linux, {@code libheif.1.dylib} on macOS, {@code heif.dll} (vcpkg) or
     * {@code libheif.dll} (MSYS2) on Windows, where the name carries no version.
     */
    static List<String> fileNames(String os, String base, List<String> versions) {
        String lower = os.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        if (lower.startsWith("windows")) {
            out.add(base + ".dll");
            out.add("lib" + base + ".dll");
            return out;
        }
        boolean mac = lower.startsWith("mac");
        for (String version : versions) {
            out.add(mac ? "lib" + base + "." + version + ".dylib" : "lib" + base + ".so." + version);
        }
        return out;
    }

    static List<String> fileNames(String base, List<String> versions) {
        return fileNames(System.getProperty("os.name", ""), base, versions);
    }
}
