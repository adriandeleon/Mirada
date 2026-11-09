package com.mirada;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import javafx.application.Application;
import javafx.stage.Stage;

import com.mirada.diagnostics.DebugLog;
import com.mirada.diagnostics.MessageLog;
import com.mirada.image.HeadStart;
import com.mirada.os.MacOpenFiles;
import com.mirada.os.Trash;
import com.mirada.perf.FxSampler;
import com.mirada.perf.Startup;
import com.mirada.report.Browser;
import com.mirada.report.Redactor;
import com.mirada.report.ReportCommand;
import com.mirada.settings.FileWrites;
import com.mirada.settings.Settings;
import com.mirada.ui.Fonts;
import com.mirada.ui.Theme;
import com.mirada.ui.ViewerWindow;
import org.jspecify.annotations.Nullable;

/**
 * Entry point. {@code mirada [file-or-folder]}: a file opens with its folder behind it, a folder
 * opens at its first image, and nothing opens an empty window that takes a drop or Ctrl+O.
 */
public final class App extends Application {

    /** What {@link MacOpenFiles#listen} did, for the perf trace. */
    private static String openFiles = "none (not started)";

    /** The config folder the command line named, or null for the usual one (Settings.defaultFile). */
    private static @Nullable Path configDir;

    /** What the command line asked to open; JavaFX's own parameters would take a "--a=b" file. */
    private static List<String> files = List.of();

    /** Where an error no code caught is told: the window, once there is one. */
    private static volatile @Nullable ViewerWindow shown;

    public static void main(String[] args) {
        // Before anything that can throw on another thread, the toolkit's included: every uncaught
        // exception and log record goes to the Debug Log, and the first to the window (DIAG-04).
        DebugLog.install();
        DebugLog.onUncaught(App::uncaught);
        // Before any AWT class can load. ImageIO and BufferedImage are java.desktop and need no
        // display, so AWT is headless — except on macOS, where Move to Trash is AWT's Desktop (Trash).
        System.setProperty("java.awt.headless", String.valueOf(Trash.headless(System.getProperty("os.name"))));
        Startup.mark("main");
        // --help and --version print and exit before anything starts (CLI-01); --config-dir and --dev
        // are settled before anything reads the config folder (CLI-02).
        Launch launch = Launch.parse(List.of(args), System.getProperty("user.home", "."));
        switch (launch.action()) {
            case HELP -> {
                System.out.print(Launch.USAGE);
                return;
            }
            case VERSION -> {
                System.out.println(AppInfo.versionText());
                return;
            }
            case REPORT -> {
                // No window: for when Mirada will not start (CLI-05).
                System.exit(ReportCommand.run(
                        Settings.defaultFile(launch.configDir()),
                        System.out,
                        Browser::open,
                        Redactor.forThisMachine()));
            }
            case MISTAKE -> {
                System.err.println("mirada: " + launch.error());
                System.err.print(Launch.USAGE);
                System.exit(2);
            }
            case START -> configDir = launch.configDir();
        }
        files = launch.files();
        // The settings are read on a thread of their own while the toolkit starts (PERF-16).
        settingsRead = readSettings(configDir);
        // The first image decodes while the toolkit starts, which is most of what comes before the
        // window (HeadStart). After the headless property: ImageIO is AWT.
        HeadStart.begin(files);
        // Before the toolkit: on macOS the file Finder launched Mirada for arrives while it starts,
        // and only what is listening by then hears it (MacOpenFiles). Elsewhere it returns at once.
        openFiles = MacOpenFiles.listen();
        Startup.mark("open-files-listening");
        launch();
    }

    /**
     * An exception no code caught, on any thread, once the Debug Log has it with its stack: said in
     * the status bar in red, never a stack-trace dialog (DIAG-04). Before the window there is no one
     * to tell; the Debug Log and its session file still have it.
     */
    private static void uncaught(Thread thread, Throwable error) {
        ViewerWindow window = shown;
        if (window != null) {
            String what = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            window.sayLater(
                    "Something went wrong: " + what + ". The Debug Log has the details.", MessageLog.Severity.ERROR);
        }
    }

    /** The settings, being read off the FX thread since {@link #main}; taken once by {@link #start}. */
    private static @Nullable CompletableFuture<Settings> settingsRead;

    /** Reads the settings on a daemon thread of its own. */
    static CompletableFuture<Settings> readSettings(@Nullable Path configDir) {
        CompletableFuture<Settings> read = new CompletableFuture<>();
        Thread.ofPlatform().daemon().name("settings-read").start(() -> {
            try {
                Settings settings = new Settings(Settings.defaultFile(configDir));
                settings.load();
                read.complete(settings);
            } catch (RuntimeException | Error e) {
                read.completeExceptionally(e);
            }
        });
        return read;
    }

    /** The settings {@link #main} started reading, or read here if it did not or could not. */
    private static Settings settings() {
        CompletableFuture<Settings> read = settingsRead;
        settingsRead = null;
        if (read != null) {
            try {
                return read.join();
            } catch (RuntimeException e) {
                // Read again below, where a failure is the toolkit's to report.
            }
        }
        Settings settings = new Settings(Settings.defaultFile(configDir));
        settings.load();
        return settings;
    }

    /** The window, for {@link #stop}; null until it is made. */
    private @Nullable ViewerWindow window;

    /** Quitting (Cmd-Q) may not hide the window first: its place is saved here too. */
    @Override
    public void stop() {
        ViewerWindow shown = window;
        if (shown != null) shown.rememberPlace();
        // The files' thread is a daemon: what it has still to write is written before the JVM goes.
        FileWrites.background().flush(Duration.ofSeconds(2));
    }

    @Override
    public void start(Stage stage) {
        Startup.mark("fx-start");
        // MIRADA_PERF_SAMPLE=1 names what holds the FX thread when it stalls (PERF-20); off otherwise.
        FxSampler.start(Thread.currentThread());
        // Inter's four faces register on a thread of their own while the window is built (Fonts).
        Fonts.load();
        Settings settings = settings();
        // Written on the files' thread from now on, never the FX thread (PERF-01).
        settings.writeOn(FileWrites.background());
        DebugLog.writeOn(FileWrites.background());
        // The Debug Log's session file beside the settings, off the FX thread: rotating last
        // session's is a copy, and nothing it holds is needed before the first image (DIAG-02).
        Path folder = settings.file().toAbsolutePath().getParent();
        if (folder != null) {
            Thread attach = new Thread(() -> DebugLog.attachFile(folder), "debug-log-file");
            attach.setDaemon(true);
            attach.start();
        }
        Theme.apply(settings.theme());
        settings.addListener(() -> Theme.apply(settings.theme()));
        ViewerWindow window = new ViewerWindow(stage, settings, getHostServices()::showDocument);
        this.window = window;
        shown = window;
        Startup.mark("window-built");
        // The first styling resolves the family, so the faces must be in by then.
        Fonts.ready();
        Startup.mark("fonts-ready");
        if (Startup.enabled()) System.out.println("[perf] fonts: " + Fonts.registered());
        List<String> args = files;
        // Listed off the FX thread while the window is shown, which takes the FX thread some 60 ms.
        if (!args.isEmpty()) window.open(Path.of(args.getFirst()));
        window.show();
        Startup.mark("window-shown");
        // After the first image is asked for, so a launch with an argument starts decoding first. A
        // macOS launch by double-click has no argument; its file has waited for this.
        if (Startup.enabled()) System.out.println("[perf] open-file handler: " + openFiles);
        MacOpenFiles.receive(args.isEmpty() ? null : Path.of(args.getFirst()), file -> {
            if (Startup.enabled()) System.out.println("[perf] open-files " + file);
            window.open(file);
        });
    }
}
