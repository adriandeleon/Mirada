package com.mirada.ui;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javax.imageio.ImageIO;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import javafx.stage.Window;

import com.mirada.diagnostics.MessageLog;
import com.mirada.settings.Settings;
import org.jspecify.annotations.Nullable;

import static com.mirada.ui.FxTestSupport.call;
import static com.mirada.ui.FxTestSupport.fx;
import static com.mirada.ui.FxTestSupport.waitUntil;

/**
 * One {@link ViewerWindow} as the app makes it, in a test: its own settings folder, a 1000×760
 * place inside JavaFX's 1000-pixel headless screen, the links it would open collected instead of
 * opened. Commands run as the key filter, the palette and the menus run them ({@code run}); keys go
 * through the scene's filter. TestFX's {@code FxToolkit.cleanupStages} closes it and every window it opened.
 */
final class WindowFixture implements AutoCloseable {

    final Stage stage;
    final ViewerWindow window;
    final Settings settings;
    /** What the window asked to open: links and folders. */
    final List<String> links;

    private WindowFixture(Stage stage, ViewerWindow window, Settings settings, List<String> links) {
        this.stage = stage;
        this.window = window;
        this.settings = settings;
        this.links = links;
    }

    /** A window over {@code path} (a file or a folder; null for none), its settings in {@code config}. */
    static WindowFixture open(Path config, @Nullable Path path) throws Exception {
        return open(config, path, settings -> {});
    }

    /** As {@link #open(Path, Path)}, the settings changed by {@code setUp} before the window reads them. */
    static WindowFixture open(Path config, @Nullable Path path, Consumer<Settings> setUp) throws Exception {
        FxTestSupport.start();
        Settings settings = new Settings(config.resolve("settings.properties"));
        settings.load();
        settings.setWindow(new Settings.WindowPlace(0, 0, 1000, 760, false));
        setUp.accept(settings);
        // As App.start does: AtlantaFX's stylesheet defines the colours mirada.css looks up.
        fx(() -> Theme.apply(settings.theme()));
        // Not FxToolkit.registerStage: TestFX keeps the stage it registered last, which would keep
        // the window from being collected after it closes (ClosedWindowTest).
        Stage stage = call(Stage::new);
        List<String> links = java.util.Collections.synchronizedList(new ArrayList<>());
        ViewerWindow window = call(() -> {
            ViewerWindow made = new ViewerWindow(stage, settings, links::add);
            made.show();
            if (path != null) made.open(path);
            return made;
        });
        return new WindowFixture(stage, window, settings, links);
    }

    /** Runs {@code command} as a key, the palette or a menu would, and waits for it. */
    void run(Command command) throws Exception {
        fx(() -> FxTestSupport.invoke(window, "run", new Class<?>[] {Command.class}, command));
    }

    /** Presses {@code code} with the shortcut key (Ctrl, or Cmd on a Mac) and Shift as given. */
    void press(KeyCode code, boolean shortcut, boolean shift) throws Exception {
        fx(() -> {
            Scene scene = Objects.requireNonNull(stage.getScene());
            boolean mac = Command.MAC;
            KeyEvent event =
                    new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, shift, shortcut && !mac, false, shortcut && mac);
            Node target = scene.getFocusOwner() != null ? scene.getFocusOwner() : scene.getRoot();
            javafx.event.Event.fireEvent(target, event);
        });
    }

    void press(KeyCode code) throws Exception {
        press(code, false, false);
    }

    /** Waits for {@code name} to be the image on screen, its full resolution in. */
    void waitForImage(String name) throws Exception {
        waitUntil(name + " on screen", () -> stage.getTitle().startsWith(name + " — ") && shownImage() != null);
    }

    @Nullable
    Image shownImage() {
        return FxTestSupport.field(window, "shownImage");
    }

    /** Waits for the window's first paint to have released what waited for it. */
    void waitForFirstPaint() throws Exception {
        waitUntil("the first paint", () -> FxTestSupport.field(window, "afterFirstPaint") == null);
    }

    /** A private field of the window. */
    <T extends @Nullable Object> T field(String name) {
        return FxTestSupport.field(window, name);
    }

    /** Everything the window has said in the status bar, oldest first. */
    List<String> said() throws Exception {
        return call(() -> {
            MessageLog messages = field("messages");
            return messages.entries().stream()
                    .map(MessageLog.Entry::text)
                    .toList()
                    .reversed();
        });
    }

    /** The last thing the window said, or "" before it said anything. */
    String lastSaid() throws Exception {
        List<String> said = said();
        return said.isEmpty() ? "" : said.getLast();
    }

    /** Waits until the window has said something starting with {@code start}. */
    void waitToSay(String start) throws Exception {
        waitUntil("\"" + start + "…\" said", () -> {
            MessageLog messages = field("messages");
            return messages.entries().stream().anyMatch(e -> e.text().startsWith(start));
        });
    }

    /** The windows showing other than this one's stage: dialogs, tool windows, popups. */
    static List<Window> others(Stage main) throws Exception {
        return call(() -> Window.getWindows().stream()
                .filter(w -> w != main && w.isShowing())
                .toList());
    }

    @Override
    public void close() throws Exception {
        fx(() -> {
            if (stage.isShowing()) stage.close();
        });
    }

    // ---- Images ----

    /** Writes a PNG of {@code width}×{@code height} in one colour. */
    static Path png(Path dir, String name, int width, int height, Color colour) throws IOException {
        return write(dir, name, width, height, colour, "png");
    }

    /** Writes a JPEG of {@code width}×{@code height} in one colour. */
    static Path jpeg(Path dir, String name, int width, int height, Color colour) throws IOException {
        return write(dir, name, width, height, colour, "jpg");
    }

    private static Path write(Path dir, String name, int width, int height, Color colour, String format)
            throws IOException {
        Files.createDirectories(dir);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(colour);
        g.fillRect(0, 0, width, height);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width / 4, height / 4);
        g.dispose();
        Path file = dir.resolve(name);
        if (!ImageIO.write(image, format, file.toFile())) throw new IOException("no writer for " + format);
        return file;
    }

    /** A folder of three small images, a.png, b.png and c.jpg, in name order. */
    static Path threeImages(Path dir) throws IOException {
        png(dir, "a.png", 64, 48, Color.RED);
        png(dir, "b.png", 48, 64, Color.GREEN);
        jpeg(dir, "c.jpg", 80, 40, Color.BLUE);
        return dir;
    }
}
