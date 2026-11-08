package com.mirada.ui;

import java.io.File;

import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import org.jspecify.annotations.Nullable;

/**
 * Where every file dialog is shown: Open Image, Open Folder, Save Edited Image, Export Debug Log.
 * One place, so the FX tests can answer a dialog JavaFX's headless platform cannot show ({@link
 * #answeredBy}); the app never sets it.
 */
final class FileDialogs {

    /** Chooses the file a dialog would have, for a test; null shows the real dialog. */
    interface Answer {
        @Nullable
        File choose(String title, @Nullable File initialFolder, String initialName);
    }

    private static @Nullable Answer answer;

    private FileDialogs() {}

    /** For tests only: answers every dialog until set back to null. */
    static void answeredBy(@Nullable Answer by) {
        answer = by;
    }

    static @Nullable File open(FileChooser chooser, @Nullable Window owner) {
        Answer by = answer;
        if (by != null) return by.choose(chooser.getTitle(), chooser.getInitialDirectory(), "");
        return chooser.showOpenDialog(owner);
    }

    static @Nullable File save(FileChooser chooser, @Nullable Window owner) {
        Answer by = answer;
        String name = chooser.getInitialFileName();
        if (by != null) return by.choose(chooser.getTitle(), chooser.getInitialDirectory(), name == null ? "" : name);
        return chooser.showSaveDialog(owner);
    }

    static @Nullable File folder(DirectoryChooser chooser, @Nullable Window owner) {
        Answer by = answer;
        if (by != null) return by.choose(chooser.getTitle(), chooser.getInitialDirectory(), "");
        return chooser.showDialog(owner);
    }
}
