package com.mirada.ui;

import java.io.InputStream;
import java.util.List;
import java.util.Objects;

import javafx.collections.ListChangeListener;
import javafx.scene.Scene;
import javafx.scene.text.Font;
import javafx.stage.Window;

import org.jspecify.annotations.Nullable;

/**
 * Inter, the family's interface face on every platform (Pauta ICON-01, ICON-02): its four faces
 * bundled under {@code com/mirada/fonts/inter/} with their {@code OFL.txt}, registered before the
 * first window is styled, and named by {@code ui-font.css}, a scene stylesheet put on every window's
 * scene as it appears: dialogs, menus and tooltips live in scenes of their own, which share no
 * stylesheet with the main window. JavaFX does not fall through a font-family list, and AtlantaFX
 * neither bundles nor names Inter, so without both the interface is drawn in the platform's
 * "System" face, and on macOS that is San Francisco, which JavaFX can only faux-bold.
 *
 * <p>Registering a face parses about 400 KB of TrueType, so the four are registered on a thread of
 * their own that starts as {@code App.start} does, while the window is being built, and the first
 * styling waits for it ({@link #ready}). Measured on the Headless platform (scripts/measure-startup.sh,
 * median of 8 warm runs), the wait is 0 ms: the faces are in before the window is built, about
 * 100 ms later. Launch to the first image was faster with Inter than with the "System" face it
 * replaces, about 500 ms against 720.
 */
public final class Fonts {

    /** The family {@code ui-font.css} names. */
    public static final String FAMILY = "Inter";

    static final List<String> FACES =
            List.of("Inter-Regular.ttf", "Inter-Bold.ttf", "Inter-Italic.ttf", "Inter-BoldItalic.ttf");

    private static final String FOLDER = "/com/mirada/fonts/inter/";

    private static @Nullable Thread loading;
    private static boolean installed;

    private Fonts() {}

    /** Starts registering the four faces, once; {@link #ready} waits for them. */
    public static synchronized void load() {
        if (loading != null) return;
        Thread thread = new Thread(Fonts::register, "fonts");
        thread.setDaemon(true);
        loading = thread;
        thread.start();
    }

    /**
     * Waits for the faces {@link #load} started (starting them first if it has not), then puts
     * {@code ui-font.css} on every window's scene, now and from now on. Before the first window is
     * shown: CSS resolves the family when it first styles a node.
     */
    public static void ready() {
        load();
        Thread thread;
        synchronized (Fonts.class) {
            thread = Objects.requireNonNull(loading);
        }
        try {
            thread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        installSheet();
    }

    /** The faces registered with the toolkit, by name ("Inter Bold"): for the tests and the perf trace. */
    public static List<String> registered() {
        return Font.getFontNames(FAMILY);
    }

    private static void register() {
        for (String face : FACES) {
            try (InputStream in = Fonts.class.getResourceAsStream(FOLDER + face)) {
                if (in != null) Font.loadFont(in, 12);
            } catch (Exception ignored) {
                // A face that will not load is drawn by the toolkit's default; the rest still load.
            }
        }
    }

    /** {@code ui-font.css}, as a stylesheet URL. */
    static String sheet() {
        return Objects.requireNonNull(Fonts.class.getResource("/com/mirada/styles/ui-font.css"), "ui-font.css")
                .toExternalForm();
    }

    private static void installSheet() {
        if (installed) return;
        installed = true;
        String sheet = sheet();
        Window.getWindows().addListener((ListChangeListener<Window>) change -> {
            while (change.next()) {
                for (Window window : change.getAddedSubList()) hook(window, sheet);
            }
        });
        for (Window window : Window.getWindows()) hook(window, sheet);
    }

    /** Puts the sheet on {@code window}'s scene now and on any scene it is given later. */
    private static void hook(Window window, String sheet) {
        add(window.getScene(), sheet);
        if (window.getProperties().putIfAbsent(Fonts.class, Boolean.TRUE) == null) {
            window.sceneProperty().addListener((o, was, now) -> add(now, sheet));
        }
    }

    private static void add(@Nullable Scene scene, String sheet) {
        if (scene != null && !scene.getStylesheets().contains(sheet))
            scene.getStylesheets().add(sheet);
    }
}
