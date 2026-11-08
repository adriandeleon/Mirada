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
import com.mirada.settings.Settings;
import com.mirada.ui.FxTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.api.FxToolkit;

import static com.mirada.ui.FxTestSupport.call;
import static com.mirada.ui.FxTestSupport.fx;
import static com.mirada.ui.FxTestSupport.waitUntil;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The entry point: --version and --help print and return before anything starts, and a start with
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
        statics(null, List.of());
        // App.start handed the window to MacOpenFiles; the next test of it starts with none.
        Field receiver = com.mirada.os.MacOpenFiles.class.getDeclaredField("receiver");
        receiver.setAccessible(true);
        receiver.set(null, null);
    }

    private static String printed(String... args) {
        PrintStream was = System.out;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
        try {
            App.main(args);
        } finally {
            System.setOut(was);
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    @Test
    void versionAndHelpPrintAndReturnWithoutAWindow() {
        assertEquals(AppInfo.versionText() + System.lineSeparator(), printed("--version"));
        assertEquals(Launch.USAGE, printed("--help"));
    }

    /** What the command line said, as main leaves it for start. */
    private static void statics(Path configDir, List<String> files) throws ReflectiveOperationException {
        Field config = App.class.getDeclaredField("configDir");
        config.setAccessible(true);
        config.set(null, configDir);
        Field named = App.class.getDeclaredField("files");
        named.setAccessible(true);
        named.set(null, files);
    }

    @Test
    @Tag("fx")
    void aStartOpensTheWindowOnTheFileWithTheSettingsWhereTheCommandLineSaid() throws Exception {
        FxTestSupport.start();
        Path photo = Files.createDirectories(dir.resolve("photos")).resolve("a.png");
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(20, 10, 1), "png", photo.toFile());
        Path config = dir.resolve("config");
        statics(config, List.of(photo.toString()));
        // Kept inside the headless screen, as the driver does.
        Settings place = new Settings(Settings.defaultFile(config));
        place.setWindow(new Settings.WindowPlace(0, 0, 900, 700, false));

        App app = new App();
        Stage stage = call(Stage::new);
        fx(() -> app.start(stage));
        waitUntil("a.png on screen", () -> stage.getTitle().startsWith("a.png"));
        waitUntil("the session log", () -> Files.exists(config.resolve(DebugLog.FILE)));

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
        Settings saved = new Settings(Settings.defaultFile(config));
        saved.load();
        assertEquals(10, saved.window().x(), "the place is saved as the app quits");
        assertTrue(saved.recent().isEmpty()
                || saved.recent().contains(photo.getParent().toAbsolutePath()));
        fx(stage::close);
        FxToolkit.cleanupStages();
    }
}
