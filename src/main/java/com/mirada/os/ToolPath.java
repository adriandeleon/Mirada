package com.mirada.os;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

/**
 * The programs Mirada starts (gio, gdbus, xdg-open, open) are found on the login shell's
 * {@code PATH} as well as its own (CLI-10, Pauta 4.0.1; Editora's {@code ProcessRunner.augmentedPath}).
 * An app started from Finder or a desktop menu inherits a stripped {@code PATH} that leaves out what
 * the user's profile adds, Homebrew's folder among them. The login shell is asked once, the first time
 * a program is started, never at start-up; Windows has no such shell and keeps its own.
 *
 * <p>Mirada's own {@code PATH} comes first, the shell's after it: what a launcher or a test put in
 * front (the headless runs' stubs) still wins. Java looks a program up on its own {@code PATH}, not
 * the child's, so the program is resolved here to its full path too.
 */
public final class ToolPath {

    private ToolPath() {}

    /** Around the PATH in the shell's output, which an interactive profile may add banners to. */
    static final String BEGIN = "__MIRADA_PATH_BEGIN__";

    static final String END = "__MIRADA_PATH_END__";

    /** How long the login shell may take; a profile that waits longer is not waited for. */
    private static final long SHELL_SECONDS = 5;

    private static volatile boolean asked;
    private static volatile @Nullable String loginPath;

    /**
     * A process for {@code command}, its program found on, and its {@code PATH} set to, this
     * process's {@code PATH} followed by the login shell's.
     */
    public static ProcessBuilder process(List<String> command) {
        ProcessBuilder builder = new ProcessBuilder(command);
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows")) return builder;
        String path = merged(System.getenv("PATH"), loginPath(), File.pathSeparator);
        builder.environment().put("PATH", path);
        builder.command(resolved(command, path, File.separator, ToolPath::executable));
        return builder;
    }

    /** {@code inherited}'s folders, then {@code login}'s not among them, blanks dropped. */
    static String merged(@Nullable String inherited, @Nullable String login, String separator) {
        LinkedHashSet<String> folders = new LinkedHashSet<>();
        for (String path : new String[] {inherited, login}) {
            if (path == null) continue;
            for (String folder : path.split(java.util.regex.Pattern.quote(separator))) {
                if (!folder.isBlank()) folders.add(folder);
            }
        }
        return String.join(separator, folders);
    }

    /**
     * {@code command} with its program replaced by the first executable of that name on {@code path};
     * as it is when the program is already a path or is not found (the start then fails as before).
     */
    static List<String> resolved(
            List<String> command, String path, String fileSeparator, Predicate<String> executable) {
        if (command.isEmpty()) return command;
        String program = command.getFirst();
        if (program.isEmpty() || program.contains("/") || program.contains("\\")) return command;
        for (String folder : path.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
            if (folder.isBlank()) continue;
            String candidate = folder.endsWith(fileSeparator) ? folder + program : folder + fileSeparator + program;
            if (executable.test(candidate)) {
                List<String> found = new ArrayList<>(command);
                found.set(0, candidate);
                return List.copyOf(found);
            }
        }
        return command;
    }

    private static boolean executable(String file) {
        try {
            Path path = Path.of(file);
            return Files.isRegularFile(path) && Files.isExecutable(path);
        } catch (InvalidPathException e) {
            return false;
        }
    }

    /** The PATH between the markers in the shell's output; null when they are missing or it is blank. */
    static @Nullable String between(String output) {
        int begin = output.indexOf(BEGIN);
        if (begin < 0) return null;
        int from = begin + BEGIN.length();
        int end = output.indexOf(END, from);
        if (end < 0) return null;
        String path = output.substring(from, end);
        return path.isBlank() ? null : path;
    }

    /** The login shell's PATH, asked once; null when there is no shell to ask or it did not say. */
    private static @Nullable String loginPath() {
        if (asked) return loginPath;
        synchronized (ToolPath.class) {
            if (!asked) {
                String shell = System.getenv("SHELL");
                loginPath = ask(shell == null || shell.isBlank() ? "/bin/sh" : shell);
                asked = true;
            }
            return loginPath;
        }
    }

    /**
     * Asks {@code shell}, as an interactive login shell (the profile files that set PATH run only so),
     * for its PATH. Its input is empty and its errors discarded, so a profile that asks or complains
     * cannot hold it; it is given {@link #SHELL_SECONDS}.
     */
    static @Nullable String ask(String shell) {
        if (!executable(shell)) return null;
        Process process = null;
        try {
            process = new ProcessBuilder(
                            shell, "-l", "-i", "-c", "printf '%s%s%s' '" + BEGIN + "' \"$PATH\" '" + END + "'")
                    .redirectInput(ProcessBuilder.Redirect.from(new File("/dev/null")))
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Process running = process;
            Thread reader = new Thread(
                    () -> {
                        try (InputStream in = running.getInputStream()) {
                            in.transferTo(out);
                        } catch (IOException closed) {
                            // killed: what was read is parsed below
                        }
                    },
                    "login-path-reader");
            reader.setDaemon(true);
            reader.start();
            if (!process.waitFor(SHELL_SECONDS, TimeUnit.SECONDS)) return null;
            reader.join(500);
            synchronized (out) {
                return between(out.toString(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
        }
    }
}
