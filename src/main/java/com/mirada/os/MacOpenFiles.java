package com.mirada.os;

import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

/**
 * Where macOS hands Mirada the image it was asked to open: a double-click in Finder, "Open With",
 * a file dropped on the Dock icon. macOS does not pass these as arguments; it sends an "open
 * documents" Apple Event, which the JDK surfaces only through AWT's {@link Desktop} open-file
 * handler — so on macOS AWT must not be headless (Torrenta's {@code os.MacHandlers} and
 * {@code AwtUse}). An event that arrives before the handler is installed, as the one that launched
 * the app does, is held by the JDK and delivered when it is.
 *
 * <p>Everywhere else the file arrives as an argument (the Linux menu entry's {@code %f}, Windows'
 * registered command), and AWT stays headless.
 */
public final class MacOpenFiles {

    private MacOpenFiles() {}

    /**
     * Whether AWT must be headless on {@code osName}: everywhere but macOS. Decided in {@code
     * App.main} before any AWT class loads; on macOS an initialised AWT also contends with JavaFX for
     * the AppKit run loop, which is why it is headless by default in the sibling apps.
     */
    public static boolean headless(@Nullable String osName) {
        String os = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        return !(os.contains("mac") || os.contains("darwin"));
    }

    /**
     * Installs the open-file handler, which hands the first file of each event to {@code open} on
     * AWT's thread (the consumer hops to the FX thread). Returns what it did, or why it did nothing:
     * headless, or a platform without the action.
     */
    public static String install(Consumer<Path> open) {
        // The property first: asking GraphicsEnvironment would load AWT classes on the start-up path
        // of every platform where the answer is already known to be no.
        if (Boolean.getBoolean("java.awt.headless") || GraphicsEnvironment.isHeadless()) {
            return "none (AWT is headless)";
        }
        if (!Desktop.isDesktopSupported()) return "none (no desktop integration)";
        Desktop desktop;
        try {
            desktop = Desktop.getDesktop();
        } catch (RuntimeException | LinkageError e) {
            return "none (" + e.getMessage() + ")";
        }
        if (!desktop.isSupported(Desktop.Action.APP_OPEN_FILE)) return "none (not supported here)";
        desktop.setOpenFileHandler(event -> first(event.getFiles()).ifPresent(file -> open.accept(file.toPath())));
        return "files";
    }

    /** One window shows one folder, so of several files opened at once the first is the one shown. */
    static Optional<File> first(List<File> files) {
        return files.stream().findFirst();
    }
}
