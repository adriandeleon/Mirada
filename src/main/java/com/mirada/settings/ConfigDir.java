package com.mirada.settings;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.mirada.os.Os;
import org.jspecify.annotations.Nullable;

/**
 * Where the config folder is (SET-07, Pauta 6.1.0): the settings, the bookmarks, the Debug Log and the
 * lock that keeps one Mirada per folder all live in it. {@link #resolve} is the one resolver, pure so
 * the order is tested; {@link #adoptOldFolder} brings a folder an older Mirada kept elsewhere.
 */
public final class ConfigDir {

    /** The environment variable naming the config folder. */
    public static final String ENV = "MIRADA_CONFIG_DIR";

    private ConfigDir() {}

    /**
     * The config folder: {@code --config-dir}'s, then {@code --dev}'s {@code ~/.mirada-dev}, then
     * {@code MIRADA_CONFIG_DIR}'s, then {@code ~/.mirada}. A flag is typed for this run, so it wins over
     * the variable, and {@code --config-dir} over {@code --dev} whichever comes first. The same on
     * every OS: no branch on it.
     */
    public static Path resolve(@Nullable Path configDir, boolean dev, @Nullable String env, String home) {
        if (configDir != null) return configDir;
        if (dev) return Path.of(home, ".mirada-dev");
        if (env != null && !env.isBlank()) return Path.of(env.trim());
        return defaultFolder(home);
    }

    /** {@link #resolve} with this process's environment and home folder. */
    public static Path resolve(@Nullable Path configDir, boolean dev) {
        return resolve(configDir, dev, System.getenv(ENV), home());
    }

    /** {@code ~/.mirada}, on every OS. */
    public static Path defaultFolder(String home) {
        return Path.of(home, ".mirada");
    }

    static String home() {
        return System.getProperty("user.home", ".");
    }

    /**
     * Where Mirada 0.2 to 0.x kept its config on {@code os}: {@code ~/Library/Application Support/Mirada}
     * on macOS, {@code %APPDATA%\Mirada} on Windows; null on Linux, where it was {@code ~/.mirada}
     * already.
     */
    static @Nullable Path oldFolder(Os.Kind os, String home, @Nullable String appData) {
        return switch (os) {
            case MAC -> Path.of(home, "Library", "Application Support", "Mirada");
            case WINDOWS ->
                appData != null && !appData.isBlank()
                        ? Path.of(appData.trim(), "Mirada")
                        : Path.of(home, "AppData", "Roaming", "Mirada");
            case LINUX -> null;
        };
    }

    /**
     * Before the first read of the default folder: when {@code ~/.mirada} is not there but the folder
     * an older Mirada kept on this OS is, it moves to {@code ~/.mirada}, once. One check on Linux and
     * when {@code ~/.mirada} is there, which is every start after the first.
     */
    public static void adoptOldFolder() {
        adopt(oldFolder(Os.host(), home(), System.getenv("APPDATA")), defaultFolder(home()));
    }

    /**
     * Moves {@code old} to {@code now} when only {@code old} is there; copies it where a move cannot
     * be made (another disk), keeping {@code old}. Returns what it did, for the log and the tests:
     * {@code "none"}, {@code "moved"}, {@code "copied"} or {@code "failed"}. A failure starts on the
     * defaults; the old folder is still there to be moved by hand.
     */
    static String adopt(@Nullable Path old, Path now) {
        if (old == null || Files.exists(now) || !Files.isDirectory(old)) return "none";
        try {
            Path parent = now.toAbsolutePath().getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.move(old, now);
            log().info("Moved the config folder from " + old + " to " + now);
            return "moved";
        } catch (IOException moveFailed) {
            try {
                copyTree(old, now);
                log().info("Copied the config folder from " + old + " to " + now + " (it could not be moved: "
                        + moveFailed + "); the old one is left as it was");
                return "copied";
            } catch (IOException copyFailed) {
                log().log(Level.WARNING, "Could not move the config folder from " + old + " to " + now, copyFailed);
                return "failed";
            }
        }
    }

    private static void copyTree(Path from, Path to) throws IOException {
        Files.walkFileTree(from, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(to.resolve(from.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(
                        file,
                        to.resolve(from.relativize(file).toString()),
                        StandardCopyOption.COPY_ATTRIBUTES,
                        StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static Logger log() {
        return Logger.getLogger(ConfigDir.class.getName());
    }
}
