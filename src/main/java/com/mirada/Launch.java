package com.mirada;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * The command line, read before anything else (CLI-01, CLI-02, Pauta 4.0.1): what to print and exit
 * on, which config folder to use, and the files to open. Plain English literals, never translated:
 * scripts and bug reports read them.
 *
 * @param action what {@code main} does: start the window, or print and exit
 * @param configDir the folder named by {@code --config-dir} or {@code --dev}; null for the usual one
 * @param files what to open, in order
 * @param error what was wrong with the line, for {@link Action#MISTAKE}
 */
public record Launch(Action action, @Nullable Path configDir, List<String> files, String error) {

    public enum Action {
        START,
        HELP,
        VERSION,
        MISTAKE
    }

    public static final String USAGE = """
            Usage: mirada [options] [file-or-folder]

            Opens an image with its folder behind it, or a folder at its first image.

            Options:
              --config-dir <dir>  keep settings and bookmarks in <dir>
              --dev               keep them in ~/.mirada-dev, apart from your own
              --version           print the version, build, commit and Pauta edition, and exit
              --help              print this, and exit
              --                  what follows is a file, even if it starts with --

            MIRADA_CONFIG_DIR names the config folder too; an option wins over it.
            """;

    /** Reads {@code args}; {@code home} is where {@code --dev} puts its folder. */
    public static Launch parse(List<String> args, String home) {
        List<String> files = new ArrayList<>();
        @Nullable Path configDir = null;
        boolean onlyFiles = false;
        for (int i = 0; i < args.size(); i++) {
            String arg = args.get(i);
            if (onlyFiles || !arg.startsWith("--")) {
                files.add(arg);
                continue;
            }
            switch (arg) {
                case "--" -> onlyFiles = true;
                case "--help" -> {
                    return new Launch(Action.HELP, null, List.of(), "");
                }
                case "--version" -> {
                    return new Launch(Action.VERSION, null, List.of(), "");
                }
                case "--dev" -> configDir = Path.of(home, ".mirada-dev");
                case "--config-dir" -> {
                    if (i + 1 >= args.size() || args.get(i + 1).isBlank())
                        return mistake("--config-dir needs a folder");
                    configDir = Path.of(args.get(++i));
                }
                default -> {
                    if (arg.startsWith("--config-dir=") && arg.length() > "--config-dir=".length()) {
                        configDir = Path.of(arg.substring("--config-dir=".length()));
                    } else {
                        return mistake("unknown option " + arg);
                    }
                }
            }
        }
        return new Launch(Action.START, configDir, List.copyOf(files), "");
    }

    private static Launch mistake(String error) {
        return new Launch(Action.MISTAKE, null, List.of(), error);
    }
}
