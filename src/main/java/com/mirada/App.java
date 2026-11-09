package com.mirada;

import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;

import com.mirada.diagnostics.DebugLog;
import com.mirada.diagnostics.MessageLog;
import com.mirada.i18n.Messages;
import com.mirada.image.HeadStart;
import com.mirada.os.MacOpenFiles;
import com.mirada.os.SingleInstance;
import com.mirada.os.Trash;
import com.mirada.perf.FxSampler;
import com.mirada.perf.Startup;
import com.mirada.perf.Tour;
import com.mirada.report.Browser;
import com.mirada.report.Redactor;
import com.mirada.report.ReportCommand;
import com.mirada.settings.FileWrites;
import com.mirada.settings.Settings;
import com.mirada.ui.Fonts;
import com.mirada.ui.Theme;
import com.mirada.ui.ViewerWindow;
import org.jspecify.annotations.Nullable;

import static com.mirada.i18n.Messages.tr;

/**
 * Entry point. {@code mirada [file-or-folder]}: a file opens with its folder behind it, a folder
 * opens at its first image, and nothing opens an empty window that takes a drop or Ctrl+O.
 */
public final class App extends Application {

    /** What {@link MacOpenFiles#listen} did, for the perf trace. */
    private static String openFiles = "none (not started)";

    /** The settings file, in the config folder the command line named or the usual one. */
    private static @Nullable Path settingsFile;

    /** This process's claim on the config folder, listening for later launches if it was first. */
    private static @Nullable SingleInstance instance;

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
        // Exit, not return: started as an Application (the jlink image, dev runs) the launcher has
        // already started the toolkit, whose thread would keep the process alive.
        if (printed(launch, System.out)) System.exit(0);
        switch (launch.action()) {
            case HELP, VERSION -> {}
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
            case START -> {}
        }
        files = launch.files();
        Path settings = Settings.defaultFile(launch.configDir());
        settingsFile = settings;
        Path folder = settings.toAbsolutePath().getParent();
        // One Mirada per config folder (CLI-07): a running one takes this launch's file and this
        // process ends here. With none running this is one file check, before anything decodes. A
        // measured start or a tour (start-up, AOT training, smoke tests) is about this process, and
        // never hands off to a Mirada the user has open.
        if (folder != null
                && launch.handsOff()
                && !Startup.enabled()
                && !Tour.enabled()
                && SingleInstance.handOff(
                        folder, Launch.absolute(files, Path.of("").toAbsolutePath()))) {
            Startup.mark("handed-off");
            System.exit(0);
        }
        // The settings are read on a thread of their own while the toolkit starts (PERF-16); after the
        // hand-off, so a launch that hands off reads nothing.
        settingsRead = readSettings(settings);
        // The first image decodes while the toolkit starts, which is most of what comes before the
        // window (HeadStart). After the headless property: ImageIO is AWT.
        HeadStart.begin(files);
        // Listening for later launches, on a thread of its own: binding the socket costs the first
        // image nothing. A --new-instance launch claims too, and listens if it turns out to be first.
        if (folder != null) instance = SingleInstance.claimInBackground(folder);
        // Before the toolkit: on macOS the file Finder launched Mirada for arrives while it starts,
        // and only what is listening by then hears it (MacOpenFiles). Elsewhere it returns at once.
        openFiles = MacOpenFiles.listen();
        Startup.mark("open-files-listening");
        launch();
    }

    /** Prints what {@code --help} or {@code --version} asks for; false for any other launch. */
    static boolean printed(Launch launch, PrintStream out) {
        switch (launch.action()) {
            case HELP -> out.print(Launch.USAGE);
            case VERSION -> out.println(AppInfo.versionText());
            default -> {
                return false;
            }
        }
        out.flush();
        return true;
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
            window.sayLater(tr("app.uncaught", what), MessageLog.Severity.ERROR);
        }
    }

    /** The settings, being read off the FX thread since {@link #main}; taken once by {@link #start}. */
    private static @Nullable CompletableFuture<Settings> settingsRead;

    /** Reads the settings on a daemon thread of its own. */
    static CompletableFuture<Settings> readSettings(Path file) {
        CompletableFuture<Settings> read = new CompletableFuture<>();
        Thread.ofPlatform().daemon().name("settings-read").start(() -> {
            try {
                Settings settings = new Settings(file);
                settings.load();
                // The catalog is read here too, off the FX thread: the window is built in one language.
                Messages.init(language(settings));
                read.complete(settings);
            } catch (RuntimeException | Error e) {
                read.completeExceptionally(e);
            }
        });
        return read;
    }

    /**
     * The interface's language: the one Settings names if it is bundled, else the system's if it is,
     * else English (INTL-02).
     */
    static String language(Settings settings) {
        return Messages.resolve(
                settings.language(),
                Messages.available().keySet(),
                Locale.getDefault().getLanguage());
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
        Path chosen = settingsFile;
        Settings settings = new Settings(chosen != null ? chosen : Settings.defaultFile(null));
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
        // The next launch then starts its own window at once instead of knocking on a closing one.
        SingleInstance single = instance;
        if (single != null) single.close();
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
        String language = language(settings);
        if (!Messages.initialized() || !Messages.current().equals(language)) Messages.init(language);
        // JavaFX words its own dialogs' buttons by the default locale: the interface's language.
        Locale.setDefault(Locale.forLanguageTag(Messages.current()));
        // Written on the files' thread from now on, never the FX thread (PERF-01).
        settings.writeOn(FileWrites.background(), Platform::runLater);
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
        // Later launches on this config folder, and any that came while the window was being built.
        SingleInstance single = instance;
        if (single != null) single.receive(later -> Platform.runLater(() -> handedOver(stage, window, later)));
    }

    /**
     * A later launch's files, on the FX thread: the window comes to the front and opens the first, as
     * a launch with it would have. A launch with none only brings the window forward.
     */
    private static void handedOver(Stage stage, ViewerWindow window, List<String> args) {
        if (stage.isIconified()) stage.setIconified(false);
        stage.toFront();
        stage.requestFocus();
        if (!args.isEmpty()) window.open(Path.of(args.getFirst()));
    }
}
