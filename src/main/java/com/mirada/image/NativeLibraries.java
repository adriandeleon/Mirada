package com.mirada.image;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.SymbolLookup;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;

/**
 * Finds a native codec library: first in the folder an installer bundles them in, then wherever the
 * system keeps them.
 *
 * <p>The installers carry libheif and libjxl in a folder of their own and pass it as {@value
 * #BUNDLE_PROPERTY}; a development run has no such folder and uses the system's copies (apt on
 * Linux, Homebrew on macOS). A library found nowhere is not an error here: its formats then open
 * with a message saying what is missing.
 */
public final class NativeLibraries {

    /** System property naming the bundled codecs folder. */
    static final String BUNDLE_PROPERTY = "mirada.codecs";

    /** Where Homebrew installs on Apple silicon and on Intel; not on the default dlopen path. */
    private static final List<Path> EXTRA_DIRECTORIES =
            List.of(Path.of("/opt/homebrew/lib"), Path.of("/usr/local/lib"));

    /** Where each library was found, by the first file name asked for: for diagnostics and tests. */
    private static final Map<String, String> ORIGINS = new ConcurrentHashMap<>();

    private static volatile boolean bundlePreloaded;

    private NativeLibraries() {}

    /** {@code "bundle"}, {@code "system"} or a directory, for a library loaded under {@code fileNames.get(0)}. */
    static Optional<String> origin(String firstFileName) {
        return Optional.ofNullable(ORIGINS.get(firstFileName));
    }

    /** The first of {@code fileNames} that loads, searched in the bundle, the system, then Homebrew. */
    static Optional<SymbolLookup> load(List<String> fileNames) {
        if (fileNames.isEmpty()) return Optional.empty();
        String key = fileNames.getFirst();
        Path folder = bundleFolder(
                System.getProperty(BUNDLE_PROPERTY),
                System.getProperty("org.graalvm.nativeimage.imagecode"),
                ProcessHandle.current().info().command().orElse(null));
        if (folder != null) {
            preloadOnWindows(folder);
            for (String name : fileNames) {
                Optional<SymbolLookup> found = open(folder.resolve(name));
                if (found.isPresent()) return remember(key, "bundle", found);
            }
        }
        for (String name : fileNames) {
            try {
                return remember(key, "system", Optional.of(SymbolLookup.libraryLookup(name, Arena.global())));
            } catch (IllegalArgumentException notOnTheSearchPath) {
                // try the next name
            }
        }
        for (Path directory : EXTRA_DIRECTORIES) {
            for (String name : fileNames) {
                Optional<SymbolLookup> found = open(directory.resolve(name));
                if (found.isPresent()) return remember(key, directory.toString(), found);
            }
        }
        return Optional.empty();
    }

    /**
     * Where the bundle is: the {@value #BUNDLE_PROPERTY} property when it is set (the jlink launcher
     * sets it to {@code $APPDIR/codecs}, runs and tests to {@code target/codecs}); otherwise, in a
     * native image, the {@code codecs} folder beside the executable, so {@code target/mirada} and an
     * installed copy need no option. Null when there is neither.
     */
    static @Nullable Path bundleFolder(
            @Nullable String property, @Nullable String imageCode, @Nullable String executable) {
        if (property != null && !property.isBlank()) return Path.of(property);
        if (!"runtime".equals(imageCode) || executable == null) return null;
        Path parent = Path.of(executable).toAbsolutePath().getParent();
        return parent == null ? null : parent.resolve("codecs");
    }

    /**
     * The bundle's {@code VERSIONS.txt}, for the About window, or null without a bundle. Beside the
     * libraries, except in the macOS native app, where codesign allows only code in {@code
     * Contents/MacOS} and the bundle's text files are in {@code Contents/Resources/codecs}.
     */
    public static @Nullable Path versionsFile() {
        Path folder = bundleFolder(
                System.getProperty(BUNDLE_PROPERTY),
                System.getProperty("org.graalvm.nativeimage.imagecode"),
                ProcessHandle.current().info().command().orElse(null));
        if (folder == null) return null;
        Path beside = folder.resolve("VERSIONS.txt");
        if (Files.isRegularFile(beside)) return beside;
        Path macOs = folder.getParent();
        Path contents = macOs == null ? null : macOs.getParent();
        Path resources = contents == null
                ? null
                : contents.resolve("Resources").resolve("codecs").resolve("VERSIONS.txt");
        return resources != null && Files.isRegularFile(resources) ? resources : null;
    }

    private static Optional<SymbolLookup> remember(String key, String origin, Optional<SymbolLookup> found) {
        ORIGINS.put(key, origin);
        return found;
    }

    /**
     * Windows looks for a DLL's dependencies beside the program, not beside the DLL, so {@code jxl.dll}
     * loaded by full path cannot find {@code brotlidec.dll} next to it. Loading every DLL in the
     * bundle first, retrying the ones whose dependencies were not in yet, means each is already in
     * the process when something asks for it by name. Linux and macOS libraries carry an rpath
     * instead ({@code $ORIGIN}, {@code @loader_path}).
     */
    private static synchronized void preloadOnWindows(Path folder) {
        if (bundlePreloaded
                || !System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows")) {
            return;
        }
        bundlePreloaded = true;
        List<Path> pending = new ArrayList<>();
        try (Stream<Path> files = Files.list(folder)) {
            files.filter(f ->
                            f.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".dll"))
                    .forEach(pending::add);
        } catch (IOException unreadable) {
            return;
        }
        boolean progress = true;
        while (!pending.isEmpty() && progress) {
            progress = pending.removeIf(dll -> open(dll).isPresent());
        }
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
