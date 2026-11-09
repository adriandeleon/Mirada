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
import com.mirada.i18n.Messages;
import com.mirada.settings.Bookmarks;
import com.mirada.settings.ConfigExport;
import com.mirada.settings.ConfigImport;
import com.mirada.settings.Settings;
import org.jspecify.annotations.Nullable;

import static com.mirada.i18n.Messages.tr;

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
                        tr("settings.themePrompt"),
                        List.of(Settings.ThemeChoice.values()),
                        SettingsWindow::themeName,
                        settings.theme(),
                        settings::setTheme);
            case LANGUAGE ->
                choose(
                        command,
                        tr("settings.languagePrompt"),
                        languages(),
                        SettingsWindow::languageName,
                        settings.language().isEmpty() || !Messages.available().containsKey(settings.language())
                                ? ""
                                : settings.language(),
                        code -> {
                            settings.setLanguage(code);
                            say(languageAtNextStart(code));
                        });
            case KEYMAP ->
                choose(
                        command,
                        tr("keymap.prompt"),
                        Keymap.PRESETS,
                        id -> Keymap.load(id, Command.MAC).name(),
                        Keymap.resolve(settings.keymap()),
                        settings::setKeymap);
            case HIDE_CONTROLS_AFTER ->
                choose(
                        command,
                        tr("settings.hideControlsPrompt"),
                        range(Settings.MIN_HIDE_SECONDS, Settings.MAX_HIDE_SECONDS),
                        SettingsCommands::seconds,
                        settings.controlsHideSeconds(),
                        settings::setControlsHideSeconds);
            case SLIDESHOW_SPEED ->
                choose(
                        command,
                        tr("settings.slideshowPrompt"),
                        range(Settings.MIN_SLIDESHOW_SECONDS, Settings.MAX_SLIDESHOW_SECONDS),
                        SettingsCommands::seconds,
                        settings.slideshowSeconds(),
                        settings::setSlideshowSeconds);
            case THUMBNAILS_AT_START -> {
                settings.setStartWithThumbnails(!settings.startWithThumbnails());
                say(
                        settings.startWithThumbnails()
                                ? tr("settings.thumbnailsOpenAtStart")
                                : tr("settings.thumbnailsClosedAtStart"));
            }
            case PROPERTIES_AT_START -> {
                settings.setStartWithInfo(!settings.startWithInfo());
                say(
                        settings.startWithInfo()
                                ? tr("settings.propertiesOpenAtStart")
                                : tr("settings.propertiesClosedAtStart"));
            }
            case CHECK_DAILY -> {
                settings.setCheckForUpdates(!settings.checkForUpdates());
                say(settings.checkForUpdates() ? tr("settings.checkForUpdatesOn") : tr("settings.checkForUpdatesOff"));
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

    /** The system's, then every bundled language: blank, then the codes. */
    static List<String> languages() {
        List<String> out = new ArrayList<>();
        out.add("");
        out.addAll(Messages.available().keySet());
        return out;
    }

    /** What is said once a language is chosen: it applies from the next start. */
    static String languageAtNextStart(String code) {
        return tr("settings.languageAtNextStart", SettingsWindow.languageName(code));
    }

    /** {@code min} to {@code max}, both in. */
    static List<Integer> range(int min, int max) {
        return IntStream.rangeClosed(min, max).boxed().toList();
    }

    /** "1 second", "5 seconds". */
    static String seconds(int count) {
        return tr("settings.seconds", count);
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
                        tr("settings.noMatchingChoices"),
                        tr("settings.chooseVerb"),
                        names,
                        Math.max(0, now),
                        index -> index == now ? tr("settings.inUseNow") : by.description(),
                        index -> index == now ? "✓" : ""),
                index -> {
                    set.accept(values.get(index));
                    say(tr("settings.chosen", by.label().replace("…", ""), names.get(index)));
                });
    }

    /** Asks where, then writes the config folder there as a zip, off the FX thread. */
    private void exportConfiguration() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(tr("export.title"));
        chooser.setInitialFileName(ConfigExport.defaultName(LocalDateTime.now()));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(tr("export.zipFilter"), "*.zip"));
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
                Platform.runLater(() -> say(tr("export.failed", e.getMessage()), MessageLog.Severity.ERROR));
            }
        });
    }

    /** What the status bar says after an export. */
    static String exported(Path zip, int files) {
        return tr("export.exported", files, String.valueOf(zip.getFileName()));
    }

    /**
     * Asks for an exported zip, reads and checks it off the FX thread, asks which of its parts to
     * replace (CONF, through {@link Confirm}), then writes them and loads them into this run.
     */
    private void importConfiguration() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(tr("import.title"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(tr("import.zipFilter"), "*.zip"));
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
                String name = String.valueOf(zip.getFileName());
                Platform.runLater(
                        () -> say(tr("import.couldNotRead", name, e.getMessage()), MessageLog.Severity.ERROR));
            }
        });
    }

    private void askAndImport(Path zip, ConfigImport.Scan scan) {
        Set<ConfigImport.Part> available = scan.available();
        if (available.isEmpty()) {
            say(tr("import.nothingToImport"), MessageLog.Severity.WARNING);
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
                Platform.runLater(() -> say(tr("import.failed", e.getMessage()), MessageLog.Severity.ERROR));
            }
        });
    }

    /**
     * What the status bar says after an import: the parts, as the question named them, each as it
     * reads in a sentence ("the keys"), listed "a, b and c".
     */
    static String imported(Set<ConfigImport.Part> parts) {
        List<String> names = new ArrayList<>();
        for (ConfigImport.Part part : parts) names.add(part.inSentence());
        if (names.isEmpty()) return tr("import.nothingImported");
        String list = names.getFirst();
        for (int i = 1; i < names.size(); i++)
            list = tr(i == names.size() - 1 ? "import.listLast" : "import.listMore", list, names.get(i));
        return tr("import.imported", list);
    }

    /**
     * What to say after a start that could not use a config file, or null when it could: the name
     * it was kept under says why ({@code .corrupt.bak} or a newer Mirada's {@code .v<n>.bak}).
     */
    static @Nullable String setAside(String what, @Nullable Path kept) {
        if (kept == null) return null;
        String name = String.valueOf(kept.getFileName());
        boolean unreadable = name.endsWith(".corrupt.bak");
        // what is the file's English name, "settings" or "bookmarks": each has a sentence of its own.
        if (what.equals("bookmarks"))
            return unreadable
                    ? tr("settings.setAside.bookmarksUnreadable", name)
                    : tr("settings.setAside.bookmarksNewer", name);
        return unreadable
                ? tr("settings.setAside.settingsUnreadable", name)
                : tr("settings.setAside.settingsNewer", name);
    }

    /** The viewer's window, which the dialogs open over. */
    private Stage owner() {
        return stage;
    }

    /** Information: an error or a warning is said with its severity. */
    private void say(String message) {
        say.accept(message, MessageLog.Severity.INFO);
    }

    private void say(String message, MessageLog.Severity severity) {
        say.accept(message, severity);
    }

    private static Logger log() {
        return Logger.getLogger(SettingsCommands.class.getName());
    }
}
