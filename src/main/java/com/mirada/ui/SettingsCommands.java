package com.mirada.ui;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;

import javafx.application.Platform;
import javafx.scene.control.CheckBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import com.mirada.diagnostics.MessageLog;
import com.mirada.settings.Bookmarks;
import com.mirada.settings.ConfigExport;
import com.mirada.settings.ConfigImport;
import com.mirada.settings.Settings;
import org.jspecify.annotations.Nullable;

/**
 * The commands that are settings (SET-06) and Export and Import Configuration (SET-12, Pauta 4.0.1):
 * each flips its setting, or asks for its value in the palette's list, as the Settings window's row
 * would change it. The viewer hands them here ({@link #run}).
 */
final class SettingsCommands {

    private final Settings settings;
    private final Bookmarks bookmarks;
    private final CommandPalette palette;
    private final Supplier<SettingsWindow> settingsWindow;
    private final Stage stage;
    private final BiConsumer<String, MessageLog.Severity> say;

    SettingsCommands(
            Settings settings,
            Bookmarks bookmarks,
            CommandPalette palette,
            Supplier<SettingsWindow> settingsWindow,
            Stage stage,
            BiConsumer<String, MessageLog.Severity> say) {
        this.settings = settings;
        this.bookmarks = bookmarks;
        this.palette = palette;
        this.settingsWindow = settingsWindow;
        this.stage = stage;
        this.say = say;
    }

    /** Runs {@code command} if it is one of these; false for any other. */
    boolean run(Command command) {
        switch (command) {
            case THEME ->
                choose(
                        command,
                        "Theme…",
                        List.of(Settings.ThemeChoice.values()),
                        SettingsWindow::themeName,
                        settings.theme(),
                        settings::setTheme);
            case KEYMAP ->
                choose(
                        command,
                        "Keymap…",
                        Keymap.PRESETS,
                        id -> Keymap.load(id, Command.MAC).name(),
                        Keymap.resolve(settings.keymap()),
                        settings::setKeymap);
            case HIDE_CONTROLS_AFTER ->
                choose(
                        command,
                        "Hide the controls after…",
                        range(Settings.MIN_HIDE_SECONDS, Settings.MAX_HIDE_SECONDS),
                        SettingsCommands::seconds,
                        settings.controlsHideSeconds(),
                        settings::setControlsHideSeconds);
            case SLIDESHOW_SPEED ->
                choose(
                        command,
                        "Show each image for…",
                        range(Settings.MIN_SLIDESHOW_SECONDS, Settings.MAX_SLIDESHOW_SECONDS),
                        SettingsCommands::seconds,
                        settings.slideshowSeconds(),
                        settings::setSlideshowSeconds);
            case THUMBNAILS_AT_START -> {
                settings.setStartWithThumbnails(!settings.startWithThumbnails());
                say(
                        settings.startWithThumbnails()
                                ? "The thumbnails will open at start"
                                : "The thumbnails will stay closed at start");
            }
            case PROPERTIES_AT_START -> {
                settings.setStartWithInfo(!settings.startWithInfo());
                say(
                        settings.startWithInfo()
                                ? "The properties will open at start"
                                : "The properties will stay closed at start");
            }
            case CHECK_DAILY -> {
                settings.setCheckForUpdates(!settings.checkForUpdates());
                say(
                        settings.checkForUpdates()
                                ? "Mirada will check for updates once a day"
                                : "Mirada will not check for updates by itself");
            }
            case PALETTE_KEYS -> settingsWindow.get().recordPaletteKey(stage);
            case EXPORT_CONFIG -> exportConfiguration();
            case IMPORT_CONFIG -> importConfiguration();
            default -> {
                return false;
            }
        }
        return true;
    }

    /** {@code min} to {@code max}, both in. */
    static List<Integer> range(int min, int max) {
        return IntStream.rangeClosed(min, max).boxed().toList();
    }

    /** "1 second", "5 seconds". */
    static String seconds(int count) {
        return count + (count == 1 ? " second" : " seconds");
    }

    /**
     * The palette over {@code values} by name, the one in use under the cursor and ticked; choosing
     * one sets it.
     */
    private <T> void choose(
            Command by, String prompt, List<T> values, Function<T, String> name, T current, Consumer<T> set) {
        List<String> names = new ArrayList<>(values.size());
        for (T value : values) names.add(name.apply(value));
        int now = values.indexOf(current);
        palette.showChoices(
                new CommandPalette.Choices(
                        by,
                        prompt,
                        "No matching choices",
                        "choose",
                        names,
                        Math.max(0, now),
                        index -> index == now ? "In use now." : by.description(),
                        index -> index == now ? "✓" : ""),
                index -> {
                    set.accept(values.get(index));
                    say(by.label().replace("…", "") + ": " + names.get(index));
                });
    }

    /** Asks where, then writes the config folder there as a zip, off the FX thread. */
    private void exportConfiguration() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Configuration");
        chooser.setInitialFileName(ConfigExport.defaultName(LocalDateTime.now()));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Zip", "*.zip"));
        File chosen = FileDialogs.save(chooser, owner());
        if (chosen == null) return;
        Path zip = chosen.toPath();
        Path folder = settings.file().toAbsolutePath().getParent();
        if (folder == null) return;
        Thread.ofVirtual().name("config-export").start(() -> {
            try {
                int files = ConfigExport.export(folder, zip);
                Platform.runLater(() -> say(exported(zip, files)));
            } catch (IOException e) {
                log().log(Level.WARNING, "Could not export the configuration", e);
                Platform.runLater(
                        () -> say("Couldn't export the configuration: " + e.getMessage(), MessageLog.Severity.ERROR));
            }
        });
    }

    /** What the status bar says after an export. */
    static String exported(Path zip, int files) {
        return "Exported " + files + (files == 1 ? " file" : " files") + " of configuration to " + zip.getFileName();
    }

    /**
     * Asks for an exported zip, reads and checks it off the FX thread, asks which of its parts to
     * replace (CONF, through {@link Confirm}), then writes them and loads them into this run.
     */
    private void importConfiguration() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Import Configuration");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Zip", "*.zip"));
        File chosen = FileDialogs.open(chooser, owner());
        if (chosen == null) return;
        Path zip = chosen.toPath();
        Thread.ofVirtual().name("config-import").start(() -> {
            try {
                ConfigImport.Scan scan = ConfigImport.read(zip);
                Platform.runLater(() -> askAndImport(zip, scan));
            } catch (ConfigImport.Refused e) {
                Platform.runLater(() -> say(e.sentence(), MessageLog.Severity.ERROR));
            } catch (IOException e) {
                Platform.runLater(() ->
                        say("Couldn't read " + zip.getFileName() + ": " + e.getMessage(), MessageLog.Severity.ERROR));
            }
        });
    }

    private void askAndImport(Path zip, ConfigImport.Scan scan) {
        Set<ConfigImport.Part> available = scan.available();
        if (available.isEmpty()) {
            say("That zip has nothing to import", MessageLog.Severity.WARNING);
            return;
        }
        List<CheckBox> boxes = new ArrayList<>();
        VBox parts = new VBox(6);
        for (ConfigImport.Part part : available) {
            CheckBox box = new CheckBox(part.label());
            box.setSelected(true);
            box.setUserData(part);
            boxes.add(box);
            parts.getChildren().add(box);
        }
        if (!Confirm.ask(Confirm.importConfiguration(String.valueOf(zip.getFileName())), owner(), parts)) return;
        Set<ConfigImport.Part> ticked = EnumSet.noneOf(ConfigImport.Part.class);
        for (CheckBox box : boxes)
            if (box.isSelected() && box.getUserData() instanceof ConfigImport.Part part) ticked.add(part);
        if (ticked.isEmpty()) return;
        Path file = settings.file();
        Thread.ofVirtual().name("config-import").start(() -> {
            try {
                Set<ConfigImport.Part> done = ConfigImport.apply(scan, file, ticked);
                Platform.runLater(() -> {
                    settings.reload();
                    if (done.contains(ConfigImport.Part.BOOKMARKS)) bookmarks.reload();
                    say(imported(done));
                });
            } catch (IOException e) {
                log().log(Level.WARNING, "Could not import the configuration", e);
                Platform.runLater(
                        () -> say("Couldn't import the configuration: " + e.getMessage(), MessageLog.Severity.ERROR));
            }
        });
    }

    /** What the status bar says after an import: the parts, as the question named them. */
    static String imported(Set<ConfigImport.Part> parts) {
        List<String> names = new ArrayList<>();
        for (ConfigImport.Part part : parts) names.add(part.label().toLowerCase(java.util.Locale.ROOT));
        if (names.isEmpty()) return "Nothing was imported";
        String last = names.removeLast();
        return "Imported the " + (names.isEmpty() ? last : String.join(", the ", names) + " and the " + last);
    }

    /**
     * What to say after a start that could not use a config file, or null when it could: the name
     * it was kept under says why ({@code .corrupt.bak} or a newer Mirada's {@code .v<n>.bak}).
     */
    static @Nullable String setAside(String what, @Nullable Path kept) {
        if (kept == null) return null;
        String name = String.valueOf(kept.getFileName());
        String why = name.endsWith(".corrupt.bak") ? " could not be read" : " are from a newer Mirada";
        return "The " + what + why + ": kept as " + name + ", and the defaults are in use";
    }

    /** The viewer's window, which the dialogs open over. */
    private Stage owner() {
        return stage;
    }

    private void say(String message) {
        say.accept(message, MessageLog.severityOf(message));
    }

    private void say(String message, MessageLog.Severity severity) {
        say.accept(message, severity);
    }

    private static Logger log() {
        return Logger.getLogger(SettingsCommands.class.getName());
    }
}
