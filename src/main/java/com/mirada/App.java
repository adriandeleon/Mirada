package com.mirada;

import java.nio.file.Path;
import java.util.List;

import javafx.application.Application;
import javafx.stage.Stage;

import com.mirada.diagnostics.DebugLog;
import com.mirada.diagnostics.MessageLog;
import com.mirada.image.HeadStart;
import com.mirada.os.MacOpenFiles;
import com.mirada.os.Trash;
import com.mirada.perf.Startup;
import com.mirada.report.Browser;
import com.mirada.report.Redactor;
import com.mirada.report.ReportCommand;
import com.mirada.settings.Settings;
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

    /** The window, for {@link #stop}; null until it is made. */
    private @Nullable ViewerWindow window;

    /** Quitting (Cmd-Q) may not hide the window first: its place is saved here too. */
    @Override
    public void stop() {
        ViewerWindow shown = window;
        if (shown != null) shown.rememberPlace();
    }

    @Override
    public void start(Stage stage) {
        Startup.mark("fx-start");
        Settings settings = new Settings(Settings.defaultFile(configDir));
        settings.load();
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
        window.show();
        Startup.mark("window-shown");
        List<String> args = files;
        if (!args.isEmpty()) window.open(Path.of(args.getFirst()));
        // After the first image is asked for, so a launch with an argument starts decoding first. A
        // macOS launch by double-click has no argument; its file has waited for this.
        if (Startup.enabled()) System.out.println("[perf] open-file handler: " + openFiles);
        MacOpenFiles.receive(args.isEmpty() ? null : Path.of(args.getFirst()), file -> {
            if (Startup.enabled()) System.out.println("[perf] open-files " + file);
            window.open(file);
        });
    }
}
