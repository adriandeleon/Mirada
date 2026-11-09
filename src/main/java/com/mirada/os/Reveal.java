package com.mirada.os;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import static com.mirada.i18n.Messages.tr;

/**
 * Shows a file in the desktop's file manager, selected in its folder: Finder through {@code open
 * -R}, File Explorer through {@code explorer /select,}, and on Linux the freedesktop.org
 * {@code FileManager1.ShowItems} call that Files, Dolphin, Nemo and Thunar answer, asked through
 * {@code gdbus} or {@code dbus-send}, with the bare folder through {@code xdg-open} when no file
 * manager answers it. Off the FX thread: a command may take a moment to answer.
 */
public final class Reveal {

    private Reveal() {}

    /** Shows {@code file} selected in its folder, or says why no file manager would. */
    public static void show(Path file) throws IOException {
        String os = System.getProperty("os.name", "");
        IOException failure = null;
        for (List<String> command : commands(os, file)) {
            try {
                Process process = ToolPath.process(command)
                        .redirectErrorStream(true)
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .start();
                // explorer.exe exits with 1 when it has done what it was asked; there is no telling.
                if (os.toLowerCase(Locale.ROOT).startsWith("windows")) return;
                if (process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0) return;
                failure = new IOException(tr("reveal.didNotShow", command.getFirst()));
            } catch (IOException e) {
                failure = e;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException(tr("reveal.interrupted"), e);
            }
        }
        throw failure != null ? failure : new IOException(tr("reveal.noFileManager"));
    }

    /** The commands that show {@code file} on {@code osName}, in the order they are tried. */
    static List<List<String>> commands(String osName, Path file) {
        String os = osName.toLowerCase(Locale.ROOT);
        Path absolute = file.toAbsolutePath();
        if (os.startsWith("mac")) return List.of(List.of("open", "-R", absolute.toString()));
        // "/select," and the path apart: Java quotes a path with spaces, and explorer takes it so.
        if (os.startsWith("windows")) return List.of(List.of("explorer.exe", "/select,", absolute.toString()));
        String uri = uri(absolute);
        List<List<String>> commands = new ArrayList<>();
        commands.add(List.of(
                "gdbus",
                "call",
                "--session",
                "--dest",
                "org.freedesktop.FileManager1",
                "--object-path",
                "/org/freedesktop/FileManager1",
                "--method",
                "org.freedesktop.FileManager1.ShowItems",
                "['" + uri + "']",
                ""));
        commands.add(List.of(
                "dbus-send",
                "--session",
                "--print-reply",
                "--dest=org.freedesktop.FileManager1",
                "/org/freedesktop/FileManager1",
                "org.freedesktop.FileManager1.ShowItems",
                "array:string:" + uri,
                "string:"));
        Path folder = absolute.getParent();
        if (folder != null) commands.add(List.of("xdg-open", folder.toString()));
        return commands;
    }

    /**
     * The file's URI, with the two characters a URI may leave bare escaped as well: a quote would
     * end gdbus's string, and dbus-send splits an array at a comma.
     */
    static String uri(Path absolute) {
        return absolute.toUri().toString().replace("'", "%27").replace(",", "%2C");
    }
}
