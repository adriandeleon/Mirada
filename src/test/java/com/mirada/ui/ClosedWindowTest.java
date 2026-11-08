package com.mirada.ui;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

import javafx.animation.Animation;
import javafx.scene.input.KeyCode;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.api.FxToolkit;

import static com.mirada.ui.FxTestSupport.call;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A closed window releases what it held (PERF-13): windows opened, used (stepping, the sidebar, the
 * strip with its thumbnail workers, the tool windows, the palette) and closed leave no thread of
 * theirs running, no timer of theirs playing, no tool window showing, and nothing outside them
 * holding on: the window itself can be collected.
 */
@Tag("fx")
class ClosedWindowTest {

    @TempDir
    Path dir;

    @AfterEach
    void cleanUp() throws Exception {
        FxToolkit.cleanupStages();
    }

    /** The platform threads a window starts: decoders, previews, thumbnail workers. */
    private static Set<String> windowThreads() {
        Set<String> names = new TreeSet<>();
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            String name = thread.getName();
            if (thread.isAlive()
                    && (name.startsWith("decoder-") || name.equals("previews") || name.startsWith("thumbnails-"))) {
                names.add(name + "#" + thread.threadId());
            }
        }
        return names;
    }

    private static Set<Long> nonDaemonThreads() {
        Set<Long> ids = new TreeSet<>();
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if (thread.isAlive() && !thread.isDaemon()) ids.add(thread.threadId());
        }
        return ids;
    }

    @Test
    void windowsOpenedUsedAndClosedLeaveNothingRunning() throws Exception {
        FxTestSupport.start();
        Path photos = WindowFixture.threeImages(dir.resolve("photos"));
        Set<String> threadsBefore = windowThreads();
        Set<Long> nonDaemonBefore = nonDaemonThreads();
        List<WeakReference<ViewerWindow>> closed = new ArrayList<>();
        List<Animation> timers = new ArrayList<>();

        for (int round = 0; round < 3; round++) {
            WindowFixture fixture = WindowFixture.open(dir.resolve("config"), photos);
            fixture.waitForImage("a.png");
            fixture.run(Command.INFO);
            fixture.run(Command.THUMBNAILS);
            fixture.run(Command.NEXT);
            fixture.waitForImage("b.png");
            fixture.run(Command.SETTINGS);
            fixture.run(Command.ABOUT);
            fixture.run(Command.SHORTCUTS);
            // A bookmark, so the Bookmarks window's list asks for a thumbnail on threads of its own.
            fixture.waitForFirstPaint();
            if (round == 0) fixture.run(Command.BOOKMARK);
            fixture.run(Command.BOOKMARKS);
            fixture.run(Command.DEBUG_LOG);
            fixture.run(Command.MESSAGE_LOG);
            fixture.press(KeyCode.ESCAPE);
            fixture.run(Command.PALETTE);
            fixture.waitForFirstPaint();
            assertTrue(windowThreads().size() > threadsBefore.size(), "a window at work has threads of its own");
            assertTrue(WindowFixture.others(fixture.stage).size() >= 4, "the tool windows are up");

            fixture.close();
            timers.addAll(call(() -> animations(fixture.window)));
            closed.add(new WeakReference<>(fixture.window));
            assertEquals(List.of(), WindowFixture.others(fixture.stage), "a tool window outlived the viewer");
        }

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!windowThreads().equals(threadsBefore) && System.nanoTime() < deadline) Thread.sleep(20);
        assertEquals(threadsBefore, windowThreads(), "threads of closed windows still running");
        assertEquals(nonDaemonBefore, nonDaemonThreads(), "a closed window left a thread that keeps the JVM up");
        List<String> playing = call(() -> timers.stream()
                .filter(timer -> timer.getStatus() == Animation.Status.RUNNING)
                .map(timer -> timer.getClass().getSimpleName() + " " + timer.getCycleDuration())
                .toList());
        assertEquals(List.of(), playing, "timers of closed windows still playing");

        // Nothing outside the windows (a static, a toolkit listener, a running timer) holds them.
        timers.clear();
        FxToolkit.cleanupStages();
        while (closed.stream().anyMatch(ref -> ref.get() != null) && System.nanoTime() < deadline + 10_000_000_000L) {
            System.gc();
            FxTestSupport.drain();
            Thread.sleep(50);
        }
        assertEquals(0, closed.stream().filter(ref -> ref.get() != null).count(), "a closed window is still held");
    }

    /**
     * Every timer the window holds, in its own fields and its parts' (Mirada's classes only, a few
     * levels down): the slideshow, the toast, the frame timer, the floating controls' idle timer.
     */
    private static List<Animation> animations(Object window) {
        List<Animation> found = new ArrayList<>();
        collect(window, 0, Collections.newSetFromMap(new IdentityHashMap<>()), found);
        return found;
    }

    private static void collect(Object object, int depth, Set<Object> seen, List<Animation> found) {
        if (!seen.add(object)) return;
        if (object instanceof Animation animation) {
            found.add(animation);
            return;
        }
        if (depth > 3 || !object.getClass().getName().startsWith("com.mirada.")) return;
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) continue;
                try {
                    field.setAccessible(true);
                    Object value = field.get(object);
                    if (value != null) collect(value, depth + 1, seen, found);
                } catch (ReflectiveOperationException | RuntimeException e) {
                    // a field we cannot read holds no timer of ours
                }
            }
        }
    }
}
