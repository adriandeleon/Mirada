package com.mirada;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javafx.stage.Stage;

import com.mirada.diagnostics.DebugLog;
import com.mirada.os.SingleInstance;
import com.mirada.settings.Settings;
import com.mirada.ui.FxTestSupport;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.api.FxToolkit;

import static com.mirada.ui.FxTestSupport.call;
import static com.mirada.ui.FxTestSupport.fx;
import static com.mirada.ui.FxTestSupport.waitUntil;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The entry point: --version and --help print before anything starts, and a start with
 * a file opens the window on it with its settings in the folder the command line named, keeps the
 * Debug Log's session file there, saves the window's place on quitting, and tells the window of an
 * error no code caught.
 */
class AppTest {

    @TempDir
    Path dir;

    @AfterEach
    void cleanUp() throws Exception {
        DebugLog.detachFile();
        statics(null, List.of(), null);
        // App.start handed the window to MacOpenFiles; the next test of it starts with none.
        Field receiver = com.mirada.os.MacOpenFiles.class.getDeclaredField("receiver");
        receiver.setAccessible(true);
        receiver.set(null, null);
    }

    private static String printed(String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        boolean printed =
                App.printed(Launch.parse(List.of(args), "/home/u"), new PrintStream(out, true, StandardCharsets.UTF_8));
        return printed ? out.toString(StandardCharsets.UTF_8) : "(nothing: it starts)";
    }

    @Test
    void versionAndHelpPrintWithoutAWindow() {
        assertEquals(AppInfo.versionText() + System.lineSeparator(), printed("--version"));
        assertEquals(Launch.USAGE, printed("--help"));
        assertEquals("(nothing: it starts)", printed("a.jpg"));
    }

    /** What the command line said, as main leaves it for start. */
    private static void statics(@Nullable Path configDir, List<String> files, @Nullable SingleInstance instance)
            throws ReflectiveOperationException {
        Field config = App.class.getDeclaredField("settingsFile");
        config.setAccessible(true);
        config.set(null, configDir == null ? null : Settings.defaultFile(configDir));
        Field named = App.class.getDeclaredField("files");
        named.setAccessible(true);
        named.set(null, files);
        Field single = App.class.getDeclaredField("instance");
        single.setAccessible(true);
        single.set(null, instance);
    }

    @Test
    @Tag("fx")
    void aStartOpensTheWindowOnTheFileWithTheSettingsWhereTheCommandLineSaid() throws Exception {
        FxTestSupport.start();
        Path photo = Files.createDirectories(dir.resolve("photos")).resolve("a.png");
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(20, 10, 1), "png", photo.toFile());
        Path config = dir.resolve("config");
        Path other = photo.resolveSibling("b.png");
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(10, 20, 1), "png", other.toFile());
        SingleInstance first = SingleInstance.claimInBackground(config);
        statics(config, List.of(photo.toString()), first);
        // Kept inside the headless screen, as the driver does.
        Settings place = new Settings(Settings.defaultFile(config));
        place.setWindow(new Settings.WindowPlace(0, 0, 900, 700, false));

        App app = new App();
        Stage stage = call(Stage::new);
        fx(() -> app.start(stage));
        waitUntil("a.png on screen", () -> stage.getTitle().startsWith("a.png"));
        waitUntil("the session log", () -> Files.exists(config.resolve(DebugLog.FILE)));

        // A second launch on the folder hands its file over, and the window shows it (CLI-07).
        waitUntil("listening for later launches", first::listening);
        assertTrue(SingleInstance.handOff(config, List.of(other.toString())));
        waitUntil("b.png on screen", () -> stage.getTitle().startsWith("b.png"));
        assertTrue(SingleInstance.handOff(config, List.of()), "a launch with nothing just comes forward");

        // An exception no code caught is said in the window.
        Method uncaught = App.class.getDeclaredMethod("uncaught", Thread.class, Throwable.class);
        uncaught.setAccessible(true);
        uncaught.invoke(null, Thread.currentThread(), new IllegalStateException("boom"));
        waitUntil(
                "the error said",
                () -> stage.getScene().getRoot().lookupAll(".label").stream()
                        .anyMatch(n -> n instanceof javafx.scene.control.Label l
                                && l.getText() != null
                                && l.getText().startsWith("Something went wrong: boom")));

        fx(() -> stage.setX(10));
        fx(app::stop);
        assertFalse(first.listening(), "stopping closes the door for later launches");
        Settings saved = new Settings(Settings.defaultFile(config));
        saved.load();
        assertEquals(10, saved.window().x(), "the place is saved as the app quits");
        assertTrue(saved.recent().isEmpty()
                || saved.recent().contains(photo.getParent().toAbsolutePath()));
        fx(stage::close);
        FxToolkit.cleanupStages();
    }
}
