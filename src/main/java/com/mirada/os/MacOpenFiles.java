package com.mirada.os;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import javafx.application.Platform;

import com.sun.glass.ui.Application;
import org.jspecify.annotations.Nullable;

/**
 * Where macOS hands Mirada the image it was asked to open: a double-click in Finder, "Open With",
 * a file dropped on the Dock icon. macOS does not pass these as arguments; it sends an "open
 * documents" Apple Event to the application's delegate, which is JavaFX's glass ({@code
 * GlassApplication.m}, {@code application:openFiles:}). Glass hands the files to its {@link
 * Application.EventHandler}, and the one JavaFX installs drops them: there is no public API for
 * them. This puts a handler of ours in front of it.
 *
 * <p>AWT's {@code Desktop.setOpenFileHandler} is not the way, though it is the JDK's: AWT receives
 * the event only when it owns the application, and JavaFX started first. jpackage's macOS launcher
 * used to turn the launching event into an argument, which hid that; the native image has no such
 * launcher, so a Finder launch opened an empty window.
 *
 * <p>Glass keeps the files that launched the app until a handler replaces JavaFX's, and then
 * delivers them again, so installing from {@code App.start} loses nothing. The package is not
 * exported: the launchers and the compiler pass {@code --add-exports
 * javafx.graphics/com.sun.glass.ui=com.mirada}, and a launch without it gets no handler rather
 * than an error. Everywhere else the file arrives as an argument (the Linux menu entry's {@code
 * %f}, Windows' registered command).
 */
public final class MacOpenFiles {

    private MacOpenFiles() {}

    /**
     * Installs the handler, on the FX thread, which hands the first file of each event to {@code
     * open} on the FX thread (the launching event's possibly from within this call), except the
     * event that repeats {@code argument}, the file on the command line ({@link #besides}). Returns
     * what it did, or why it did nothing.
     */
    public static String install(@Nullable Path argument, Consumer<Path> open) {
        if (!isMac(System.getProperty("os.name"))) return "none (not macOS)";
        // Touching a glass class without the export is an IllegalAccessError: ask first. On the class
        // path, as the native image is built, everything is exported to everything.
        if (!Platform.class.getModule().isExported("com.sun.glass.ui", MacOpenFiles.class.getModule())) {
            System.err.println("[mirada] no open-file handler: com.sun.glass.ui is not exported to Mirada");
            return "none (com.sun.glass.ui not exported)";
        }
        return Glass.install(besides(argument, open));
    }

    /**
     * {@code open}, less the event that repeats the command line. AppKit turns a file named on it into
     * an "open documents" event as well, so a launch with an argument would open it twice, the second
     * time starting the folder over. Only the first event is dropped, and only when it names the same
     * file: a later double-click on it is the user's.
     */
    static Consumer<Path> besides(@Nullable Path argument, Consumer<Path> open) {
        if (argument == null) return open;
        AtomicBoolean first = new AtomicBoolean(true);
        return file -> {
            if (first.getAndSet(false) && same(file, argument)) return;
            open.accept(file);
        };
    }

    /** The same file, however each was spelled (AppKit's is absolute, the argument as typed). */
    static boolean same(Path a, Path b) {
        try {
            return Files.isSameFile(a, b);
        } catch (IOException | SecurityException e) {
            return a.toAbsolutePath().normalize().equals(b.toAbsolutePath().normalize());
        }
    }

    static boolean isMac(@Nullable String osName) {
        String os = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        return os.contains("mac") || os.contains("darwin");
    }

    /**
     * One window shows one folder, so of several files opened at once the first is the one shown.
     * Glass leaves a slot null for a name it could not convert.
     */
    static Optional<Path> first(String @Nullable [] files) {
        if (files == null) return Optional.empty();
        return Arrays.stream(files).filter(Objects::nonNull).findFirst().flatMap(file -> {
            try {
                return Optional.of(Path.of(file));
            } catch (InvalidPathException e) {
                return Optional.empty();
            }
        });
    }

    /**
     * Everything that names a glass class, apart, so that verifying {@link MacOpenFiles} loads none of
     * them: that would fail without the export, before {@link #install} could ask.
     */
    static final class Glass {

        private Glass() {}

        static String install(Consumer<Path> open) {
            Application glass = Application.GetApplication();
            if (glass == null) return "none (no glass application)";
            glass.setEventHandler(new Handler(glass.getEventHandler(), open));
            return "files";
        }
    }

    /** JavaFX's handler, with the open files handed to Mirada as well; everything else is JavaFX's. */
    static final class Handler extends Application.EventHandler {

        private final Application.EventHandler javafx;
        private final Consumer<Path> open;

        Handler(Application.@Nullable EventHandler javafx, Consumer<Path> open) {
            this.javafx = javafx == null ? new Application.EventHandler() : javafx;
            this.open = open;
        }

        @Override
        public void handleOpenFilesAction(Application app, long time, String[] files) {
            javafx.handleOpenFilesAction(app, time, files);
            first(files).ifPresent(open);
        }

        @Override
        public void handleWillFinishLaunchingAction(Application app, long time) {
            javafx.handleWillFinishLaunchingAction(app, time);
        }

        @Override
        public void handleDidFinishLaunchingAction(Application app, long time) {
            javafx.handleDidFinishLaunchingAction(app, time);
        }

        @Override
        public void handleWillBecomeActiveAction(Application app, long time) {
            javafx.handleWillBecomeActiveAction(app, time);
        }

        @Override
        public void handleDidBecomeActiveAction(Application app, long time) {
            javafx.handleDidBecomeActiveAction(app, time);
        }

        @Override
        public void handleWillResignActiveAction(Application app, long time) {
            javafx.handleWillResignActiveAction(app, time);
        }

        @Override
        public void handleDidResignActiveAction(Application app, long time) {
            javafx.handleDidResignActiveAction(app, time);
        }

        @Override
        public void handleDidReceiveMemoryWarning(Application app, long time) {
            javafx.handleDidReceiveMemoryWarning(app, time);
        }

        @Override
        public void handleWillHideAction(Application app, long time) {
            javafx.handleWillHideAction(app, time);
        }

        @Override
        public void handleDidHideAction(Application app, long time) {
            javafx.handleDidHideAction(app, time);
        }

        @Override
        public void handleWillUnhideAction(Application app, long time) {
            javafx.handleWillUnhideAction(app, time);
        }

        @Override
        public void handleDidUnhideAction(Application app, long time) {
            javafx.handleDidUnhideAction(app, time);
        }

        @Override
        public void handleQuitAction(Application app, long time) {
            javafx.handleQuitAction(app, time);
        }

        @Override
        public void handlePreferencesChanged(Map<String, Object> preferences) {
            javafx.handlePreferencesChanged(preferences);
        }
    }
}
