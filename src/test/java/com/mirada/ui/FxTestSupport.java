package com.mirada.ui;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.geometry.Rectangle2D;
import javafx.stage.PopupWindow;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.stage.WindowEvent;

import org.jspecify.annotations.Nullable;
import org.testfx.api.FxToolkit;

/**
 * The plumbing every FX test shares (all tagged {@code fx}): TestFX starts the toolkit once, on
 * JavaFX's own Headless glass with software rendering (surefire sets {@code glass.platform} and
 * {@code prism.order} before any class loads), so no test needs a display, xvfb or Monocle. Work
 * runs on the FX thread and its failures fail the test.
 */
public final class FxTestSupport {

    private static final long TIMEOUT_SECONDS = 20;
    private static boolean started;

    private FxTestSupport() {}

    /** Starts the toolkit, once per run; later calls return at once. */
    public static synchronized void start() throws Exception {
        if (started) return;
        FxToolkit.registerPrimaryStage();
        // Closing a test's last window must not end the toolkit for the tests after it.
        Platform.setImplicitExit(false);
        fx(FxTestSupport::keepWindowsOnScreen);
        started = true;
    }

    /**
     * JavaFX's headless screen is 1000 pixels square and throws on a window drawn past its edge, so
     * every window and popup is moved inside it as it shows, as a desktop's window manager would.
     */
    static void keepWindowsOnScreen() {
        Window.getWindows().forEach(FxTestSupport::keepOnScreen);
        Window.getWindows().addListener((ListChangeListener<Window>) change -> {
            while (change.next()) change.getAddedSubList().forEach(FxTestSupport::keepOnScreen);
        });
    }

    /**
     * Moves a window back inside the screen as it shows, before its first frame is drawn. A popup's
     * own fix keeps its content inside but not its shadow, and would undo ours, so it is turned off.
     */
    private static void keepOnScreen(Window window) {
        if (window instanceof PopupWindow popup) popup.setAutoFix(false);
        // A stage taller than the screen is held to it as it joins the windows, before glass first
        // sizes it: the headless glass throws on clearing a place that was ever past the edge.
        if (window instanceof Stage stage) {
            fitScreen(stage);
            window.addEventHandler(WindowEvent.WINDOW_SHOWING, e -> fitScreen(stage));
        }
        window.addEventHandler(WindowEvent.WINDOW_SHOWN, e -> moveOnScreen(window));
        // A window that grows after it shows (its content laid out late) is moved again.
        window.widthProperty().addListener((o, was, now) -> moveOnScreen(window));
        window.heightProperty().addListener((o, was, now) -> moveOnScreen(window));
        moveOnScreen(window);
    }

    private static void fitScreen(Stage stage) {
        Rectangle2D screen = Screen.getPrimary().getBounds();
        double y = Double.isNaN(stage.getY()) ? screen.getMinY() : Math.max(screen.getMinY(), stage.getY());
        double room = screen.getMaxY() - y;
        javafx.scene.Scene scene = stage.getScene();
        if (scene == null || !Double.isNaN(stage.getHeight())) return;
        javafx.scene.Parent root = scene.getRoot();
        root.applyCss();
        double width = Double.isNaN(stage.getWidth()) ? root.prefWidth(-1) : stage.getWidth();
        if (root.prefHeight(width) > room) {
            stage.setY(y);
            stage.setHeight(room);
        }
    }

    private static void moveOnScreen(Window window) {
        // A stage placed before it shows has no size yet: wait for WINDOW_SHOWN.
        if (Double.isNaN(window.getWidth()) || Double.isNaN(window.getHeight())) return;
        Rectangle2D screen = Screen.getPrimary().getBounds();
        if (window.getWidth() > screen.getWidth()) window.setWidth(screen.getWidth());
        if (window.getHeight() > screen.getHeight()) window.setHeight(screen.getHeight());
        double x = Math.max(screen.getMinX(), Math.min(window.getX(), screen.getMaxX() - window.getWidth()));
        double y = Math.max(screen.getMinY(), Math.min(window.getY(), screen.getMaxY() - window.getHeight()));
        if (x != window.getX()) window.setX(x);
        if (y != window.getY()) window.setY(y);
    }

    /** Runs {@code task} on the FX thread and waits for it, rethrowing what it threw. */
    public static void fx(ThrowingRunnable task) throws Exception {
        call(() -> {
            task.run();
            return null;
        });
    }

    /** Computes a value on the FX thread and waits for it. */
    public static <T extends @Nullable Object> T call(Callable<T> task) throws Exception {
        if (Platform.isFxApplicationThread()) return task.call();
        CompletableFuture<T> result = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                result.complete(task.call());
            } catch (Throwable t) {
                result.completeExceptionally(t);
            }
        });
        try {
            return result.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof Exception cause) throw cause;
            if (e.getCause() instanceof Error error) throw error;
            throw e;
        }
    }

    /** Waits until everything queued on the FX thread before this call has run. */
    public static void drain() throws Exception {
        fx(() -> {});
    }

    /**
     * Waits, letting pulses and background work go on, until {@code condition} holds on the FX
     * thread; fails with {@code what} after the timeout.
     */
    public static void waitUntil(String what, BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        while (!call(condition::getAsBoolean)) {
            if (System.nanoTime() > deadline) throw new AssertionError("timed out waiting for " + what);
            Thread.sleep(10);
        }
    }

    /** A private field of {@code target}, read by reflection (tests run as the unnamed module). */
    @SuppressWarnings("unchecked")
    public static <T extends @Nullable Object> T field(Object target, String name) {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return (T) field.get(target);
            } catch (NoSuchFieldException e) {
                // the superclass, then
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
        throw new IllegalArgumentException("no field " + name + " in " + target.getClass());
    }

    /** Calls a private method of {@code target} by reflection, its parameter types given. */
    public static @Nullable Object invoke(Object target, String name, Class<?>[] types, Object... args) {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Method method = type.getDeclaredMethod(name, types);
                method.setAccessible(true);
                return method.invoke(target, args);
            } catch (NoSuchMethodException e) {
                // the superclass, then
            } catch (InvocationTargetException e) {
                if (e.getCause() instanceof RuntimeException runtime) throw runtime;
                if (e.getCause() instanceof Error error) throw error;
                throw new IllegalStateException(e.getCause());
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
        throw new IllegalArgumentException("no method " + name + " in " + target.getClass());
    }

    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Exception;
    }
}
