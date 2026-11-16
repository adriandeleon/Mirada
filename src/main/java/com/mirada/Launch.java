package com.mirada;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * The command line, read before anything else (CLI-01, CLI-02, CLI-12, CLI-13, Pauta 6.1.0): what to
 * print and exit on, which config folder to use, and the files to open. Plain English literals, never
 * translated: scripts and bug reports read them.
 *
 * @param action what {@code main} does: start the window, or print and exit
 * @param configDir the folder named by {@code --config-dir}; null for none
 * @param dev {@code --dev}: the config folder is {@code ~/.mirada-dev}, unless {@code --config-dir} names one
 * @param files what to open, in order
 * @param newInstance {@code --new-instance}: start a window of its own, never hand off (CLI-03)
 * @param error what was wrong with the line, for {@link Action#MISTAKE}
 */
public record Launch(
        Action action, @Nullable Path configDir, boolean dev, List<String> files, boolean newInstance, String error) {

    public enum Action {
        START,
        HELP,
        VERSION,
        /** Write the problem report and open the issue, without the window (CLI-05). */
        REPORT,
        MISTAKE
    }

    public static final String USAGE = """
            Usage: mirada [options] [files...]

            Opens an image with its folder behind it, or a folder at its first image.
            Several images open together, to step through just those.

            Options:
              -h, --help          print this, and exit
              -v, -V, --version   print the version, build, commit and Pauta edition, and exit
              --config-dir <dir>  keep settings and bookmarks in <dir> (default ~/.mirada)
              --dev               keep them in ~/.mirada-dev, apart from your own
              --new-instance      start a window of its own, even when Mirada is running
              --no-session        open only what is named; Mirada restores no session, so
                                  this changes nothing, and is taken for scripts that pass it
              --report            write a redacted problem report in the current folder,
                                  open a prefilled GitHub issue with it, and exit; for
                                  when Mirada will not start
              --                  what follows is a file, even if it starts with -

            One Mirada runs per config folder: a second launch hands its files to the
            running one, which opens them and comes to the front, and exits.

            MIRADA_CONFIG_DIR names the config folder too; --config-dir and --dev win over it.
            """;

    /** The line that follows a mistake on standard error. */
    public static final String HELP_HINT = "Run 'mirada --help' for the options.";

    /** Reads {@code args}. */
    public static Launch parse(List<String> args) {
        List<String> files = new ArrayList<>();
        @Nullable Path configDir = null;
        boolean dev = false;
        boolean onlyFiles = false;
        boolean report = false;
        boolean newInstance = false;
        for (int i = 0; i < args.size(); i++) {
            String arg = args.get(i);
            // A lone "-" is a file named so; after "--" everything is (CLI-12).
            if (onlyFiles || !arg.startsWith("-") || arg.equals("-")) {
                files.add(arg);
                continue;
            }
            switch (arg) {
                case "--" -> onlyFiles = true;
                case "-h", "--help" -> {
                    return new Launch(Action.HELP, null, false, List.of(), false, "");
                }
                case "-v", "-V", "--version" -> {
                    return new Launch(Action.VERSION, null, false, List.of(), false, "");
                }
                // JVM options a launcher can leave among the program's arguments: skipped with their
                // value, never refused and never opened as a file (CLI-12).
                case "--add-exports", "--add-opens", "-p" -> i++;
                // Not at once: a --config-dir after it still names the folder it reports on.
                case "--report" -> report = true;
                case "--new-instance" -> newInstance = true;
                // CLI-04: Mirada restores no session, so a launch already opens only what it names.
                case "--no-session" -> {}
                case "--dev" -> dev = true;
                case "--config-dir" -> {
                    if (i + 1 >= args.size() || args.get(i + 1).isBlank())
                        return mistake("--config-dir needs a folder");
                    configDir = Path.of(args.get(++i));
                }
                default -> {
                    if (arg.startsWith("--add-exports=") || arg.startsWith("--add-opens=")) continue;
                    if (arg.startsWith("--config-dir=") && arg.length() > "--config-dir=".length()) {
                        configDir = Path.of(arg.substring("--config-dir=".length()));
                    } else {
                        return mistake("unknown option " + arg);
                    }
                }
            }
        }
        return new Launch(report ? Action.REPORT : Action.START, configDir, dev, List.copyOf(files), newInstance, "");
    }

    /** Whether this launch goes to a running Mirada first (CLI-07), unless told not to (CLI-03). */
    public boolean handsOff() {
        return action == Action.START && !newInstance;
    }

    /**
     * The files as the running Mirada must read them: resolved against this launch's working folder,
     * {@code cwd}, which is not its own. One that is no path at all is passed as it is, to be refused
     * there as it would be here.
     */
    public static List<String> absolute(List<String> files, Path cwd) {
        List<String> absolute = new ArrayList<>(files.size());
        for (String file : files) {
            try {
                absolute.add(cwd.resolve(file).normalize().toString());
            } catch (InvalidPathException notAPath) {
                absolute.add(file);
            }
        }
        return List.copyOf(absolute);
    }

    private static Launch mistake(String error) {
        return new Launch(Action.MISTAKE, null, false, List.of(), false, error);
    }
}
