package com.mirada;

import java.nio.file.Path;
import java.util.List;

import javafx.application.Application;
import javafx.stage.Stage;

import com.mirada.ui.Theme;
import com.mirada.ui.ViewerWindow;

/**
 * Entry point. {@code mirada [file-or-folder]}: a file opens with its folder behind it, a folder
 * opens at its first image, and nothing opens an empty window that takes a drop or Ctrl+O.
 */
public final class App extends Application {

    public static void main(String[] args) {
        // First, before any AWT class can load: ImageIO and BufferedImage are java.desktop, and on
        // macOS an initialised AWT contends with JavaFX for the AppKit run loop (Termina's note).
        System.setProperty("java.awt.headless", "true");
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        Theme.followSystem();
        ViewerWindow window = new ViewerWindow(stage);
        window.show();
        List<String> args = getParameters().getUnnamed();
        if (!args.isEmpty()) window.open(Path.of(args.getFirst()));
    }
}
