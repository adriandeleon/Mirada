package com.mirada;

import java.nio.file.Path;
import java.util.List;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;

import com.mirada.os.MacOpenFiles;
import com.mirada.perf.Startup;
import com.mirada.settings.Settings;
import com.mirada.ui.Theme;
import com.mirada.ui.ViewerWindow;

/**
 * Entry point. {@code mirada [file-or-folder]}: a file opens with its folder behind it, a folder
 * opens at its first image, and nothing opens an empty window that takes a drop or Ctrl+O.
 */
public final class App extends Application {

    public static void main(String[] args) {
        // First, before any AWT class can load. ImageIO and BufferedImage are java.desktop and need no
        // display, so AWT is headless — except on macOS, where Finder's "open this image" arrives as
        // an Apple Event only AWT's Desktop handler receives (MacOpenFiles).
        System.setProperty("java.awt.headless", String.valueOf(MacOpenFiles.headless(System.getProperty("os.name"))));
        Startup.mark("main");
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        Startup.mark("fx-start");
        Settings settings = new Settings(Settings.defaultFile());
        settings.load();
        Theme.apply(settings.theme());
        settings.addListener(() -> Theme.apply(settings.theme()));
        ViewerWindow window = new ViewerWindow(stage, settings, getHostServices()::showDocument);
        window.show();
        Startup.mark("window-shown");
        List<String> args = getParameters().getUnnamed();
        if (!args.isEmpty()) window.open(Path.of(args.getFirst()));
        // After the first image is asked for, so a launch with an argument starts decoding first. On
        // Linux and Windows it returns at once (AWT is headless; measured: no start-up cost). A macOS
        // launch by double-click has no argument, and its event waits in the JDK until this installs.
        MacOpenFiles.install(file -> Platform.runLater(() -> window.open(file)));
    }
}
