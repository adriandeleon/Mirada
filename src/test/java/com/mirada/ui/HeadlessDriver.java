package com.mirada.ui;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import javax.imageio.ImageIO;

import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.MouseButton;
import javafx.scene.robot.Robot;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;

import com.mirada.App;
import org.jspecify.annotations.Nullable;

/**
 * Runs Mirada on JavaFX's Headless platform and drives it by steps: keys, clicks, screenshots. No
 * window reaches the desktop, so it can be run while someone works at it ({@code
 * scripts/headless-run.sh}). Arguments before {@code --} go to Mirada; each one after is a step:
 *
 * <pre>
 *   wait MS            let MS milliseconds of pulses pass
 *   key CHORD          press and release a KeyChord token: r, C-S-p, Right, S-F10
 *   type TEXT          type letters, digits and spaces
 *   move X Y           move the pointer, in the main window's coordinates
 *   click X Y          left-click there; rclick X Y right-clicks, dclick X Y double-clicks
 *   wheel N            turn the wheel N notches (negative is up)
 *   size W H           resize the main window (the headless screen is 1000x1000; it starts 1000x760)
 *   shot FILE          save the main window, popups over it included, as a PNG
 *   windows            print every showing window and its bounds
 * </pre>
 *
 * <p>After the last step the app quits. Every step waits a little for the scene to settle.
 */
public final class HeadlessDriver {

    private static final long SETTLE_MS = 150;

    private HeadlessDriver() {}

    public static void main(String[] args) throws Exception {
        int split = Arrays.asList(args).indexOf("--");
        String[] appArgs = split < 0 ? args : Arrays.copyOfRange(args, 0, split);
        List<String> steps = split < 0 ? List.of() : List.of(args).subList(split + 1, args.length);

        Thread app = new Thread(() -> App.main(appArgs), "mirada");
        app.setDaemon(true);
        app.start();

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (mainWindow() == null) {
            if (System.nanoTime() > deadline) throw new IllegalStateException("Mirada showed no window");
            Thread.sleep(50);
        }
        // JavaFX's headless screen is 1000 pixels square and draws nothing past it (a window over the
        // edge throws as it is drawn), so the window is put inside it.
        fx(() -> {
            fit(stage(), 1000, 760);
            // Menus and popups too: one opened near the bottom would hang past it.
            FxTestSupport.keepWindowsOnScreen();
        });
        // The first image is painted on a pulse after the window shows.
        Thread.sleep(500);
        Robot robot = call(Robot::new);
        for (String step : steps) {
            System.out.println("[drive] " + step);
            run(robot, step.trim());
            Thread.sleep(SETTLE_MS);
        }
        Platform.exit();
        app.join(5000);
        System.exit(0);
    }

    private static void run(Robot robot, String step) throws Exception {
        String[] word = step.split("\\s+", 2);
        String rest = word.length > 1 ? word[1] : "";
        switch (word[0]) {
            case "wait" -> Thread.sleep(Long.parseLong(rest));
            case "key" -> input(() -> press(robot, rest));
            case "type" -> input(() -> type(robot, rest));
            case "move" -> input(() -> move(robot, rest));
            case "click" -> input(() -> click(robot, rest, MouseButton.PRIMARY, 1));
            case "dclick" -> input(() -> click(robot, rest, MouseButton.PRIMARY, 2));
            case "rclick" -> input(() -> click(robot, rest, MouseButton.SECONDARY, 1));
            case "wheel" -> input(() -> robot.mouseWheel(Integer.parseInt(rest)));
            case "size" ->
                fx(() -> {
                    String[] wh = rest.split("\\s+");
                    fit(stage(), Double.parseDouble(wh[0]), Double.parseDouble(wh[1]));
                });
            case "shot" -> write(call(HeadlessDriver::shot), Path.of(rest));
            case "windows" ->
                fx(() -> {
                    for (Window w : Window.getWindows()) {
                        System.out.printf(
                                Locale.ROOT,
                                "[drive] %s %s at %.0f,%.0f %.0fx%.0f%s%n",
                                w.getClass().getSimpleName(),
                                w instanceof Stage s ? '"' + s.getTitle() + '"' : "",
                                w.getX(),
                                w.getY(),
                                w.getWidth(),
                                w.getHeight(),
                                w.isFocused() ? " focused" : "");
                    }
                });
            default -> throw new IllegalArgumentException("unknown step: " + step);
        }
    }

    /** Puts the window at the screen's corner at this size, no larger than the screen. */
    private static void fit(Stage stage, double width, double height) {
        Rectangle2D screen = Screen.getPrimary().getBounds();
        stage.setX(screen.getMinX());
        stage.setY(screen.getMinY());
        stage.setWidth(Math.min(width, screen.getWidth()));
        stage.setHeight(Math.min(height, screen.getHeight()));
    }

    private static void press(Robot robot, String token) {
        KeyCodeCombination chord = chord(token);
        List<KeyCode> held = new ArrayList<>();
        if (chord.getControl() == KeyCombination.ModifierValue.DOWN) held.add(KeyCode.CONTROL);
        if (chord.getAlt() == KeyCombination.ModifierValue.DOWN) held.add(KeyCode.ALT);
        if (chord.getMeta() == KeyCombination.ModifierValue.DOWN) held.add(KeyCode.META);
        if (chord.getShift() == KeyCombination.ModifierValue.DOWN) held.add(KeyCode.SHIFT);
        held.forEach(robot::keyPress);
        robot.keyType(chord.getCode());
        held.reversed().forEach(robot::keyRelease);
    }

    /** A KeyChord token, or a bare KeyCode name ({@code ESCAPE}, {@code Esc}) after its modifiers. */
    private static KeyCodeCombination chord(String token) {
        if (KeyChord.toCombination(token) instanceof KeyCodeCombination chord) return chord;
        int last = token.lastIndexOf('-', token.length() - 2);
        String prefix = token.substring(0, last + 1);
        String name = token.substring(last + 1);
        KeyCode code = KeyCode.getKeyCode(name);
        if (code == null) {
            try {
                code = KeyCode.valueOf(name.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("no such key: " + token, e);
            }
        }
        if (KeyChord.toCombination(prefix + KeyChord.keyName(code)) instanceof KeyCodeCombination chord) return chord;
        throw new IllegalArgumentException("no such key: " + token);
    }

    private static void type(Robot robot, String text) {
        for (char c : text.toCharArray()) {
            KeyCode code = c == ' ' ? KeyCode.SPACE : KeyCode.getKeyCode(String.valueOf(Character.toUpperCase(c)));
            if (code == null) throw new IllegalArgumentException("cannot type '" + c + "'");
            boolean shift = Character.isUpperCase(c);
            if (shift) robot.keyPress(KeyCode.SHIFT);
            robot.keyType(code);
            if (shift) robot.keyRelease(KeyCode.SHIFT);
        }
    }

    private static void move(Robot robot, String xy) {
        String[] p = xy.split("\\s+");
        Stage stage = stage();
        robot.mouseMove(stage.getX() + Double.parseDouble(p[0]), stage.getY() + Double.parseDouble(p[1]));
    }

    private static void click(Robot robot, String xy, MouseButton button, int times) {
        move(robot, xy);
        for (int i = 0; i < times; i++) robot.mouseClick(button);
    }

    /**
     * The main window with every window over it, each drawn from its scene in the order they
     * opened. Not the robot's capture: the headless screen is one buffer every window draws into as
     * it repaints, so the main window's next frame paints over an open menu.
     */
    private static BufferedImage shot() {
        Stage stage = stage();
        BufferedImage out =
                new BufferedImage((int) stage.getWidth(), (int) stage.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        for (Window window : List.copyOf(Window.getWindows())) {
            Scene scene = window.getScene();
            if (!window.isShowing() || scene == null) continue;
            WritableImage image = scene.snapshot(null);
            int w = (int) image.getWidth();
            int h = (int) image.getHeight();
            BufferedImage layer = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            PixelReader pixels = image.getPixelReader();
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) layer.setRGB(x, y, pixels.getArgb(x, y));
            g.drawImage(
                    layer,
                    (int) Math.round(window.getX()
                            + scene.getX()
                            - stage.getX()
                            - stage.getScene().getX()),
                    (int) Math.round(window.getY()
                            + scene.getY()
                            - stage.getY()
                            - stage.getScene().getY()),
                    null);
        }
        g.dispose();
        return out;
    }

    private static void write(BufferedImage image, Path file) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        ImageIO.write(image, "png", file.toFile());
        System.out.println(
                "[drive] wrote " + file.toAbsolutePath() + " (" + image.getWidth() + "x" + image.getHeight() + ")");
    }

    /** The first showing Stage, Mirada's window; null before it shows. */
    private static @Nullable Stage mainWindow() throws Exception {
        return call(() -> {
            for (Window w : Window.getWindows()) if (w instanceof Stage s && s.isShowing()) return s;
            return null;
        });
    }

    private static Stage stage() {
        for (Window w : Window.getWindows()) if (w instanceof Stage s && s.isShowing()) return s;
        throw new IllegalStateException("no window");
    }

    /**
     * Sends input on the FX thread. A press that opens a modal dialog does not return until the
     * dialog closes ({@code showAndWait} runs a nested event loop), so after a moment the next steps
     * go on inside that loop, where they reach the dialog.
     */
    private static void input(Runnable action) throws Exception {
        CompletableFuture<@Nullable Void> done = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                action.run();
                done.complete(null);
            } catch (Throwable t) {
                done.completeExceptionally(t);
            }
        });
        try {
            done.get(2, TimeUnit.SECONDS);
        } catch (TimeoutException modal) {
            System.out.println("[drive] (still held: a modal dialog is up)");
        }
    }

    private static void fx(Runnable action) throws Exception {
        call(() -> {
            action.run();
            return null;
        });
    }

    /** Runs on the FX thread and waits; before the toolkit is up, waits for it first. */
    private static <T> T call(Supplier<T> action) throws Exception {
        CompletableFuture<T> result = new CompletableFuture<>();
        Runnable task = () -> {
            try {
                result.complete(action.get());
            } catch (Throwable t) {
                result.completeExceptionally(t);
            }
        };
        while (true) {
            try {
                Platform.runLater(task);
                return result.get(30, TimeUnit.SECONDS);
            } catch (IllegalStateException toolkitStarting) {
                Thread.sleep(50);
            }
        }
    }
}
