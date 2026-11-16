package com.mirada.report;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Predicate;

import com.mirada.AppInfo;
import com.mirada.diagnostics.DebugLog;
import com.mirada.settings.Settings;
import org.jspecify.annotations.Nullable;

/**
 * {@code mirada --report}: the problem report from the command line, for when Mirada cannot start
 * (CLI-05, DIAG-06, Pauta 6.1.0). Reads the settings and the last two session logs, redacts them,
 * writes {@link #FILE} in the folder the command was typed in (the temp folder when that one can't be
 * written), prints its path and opens the prefilled issue. No JavaFX.
 */
public final class ReportCommand {

    public static final String FILE = "problem-report.txt";

    private ReportCommand() {}

    /**
     * Runs the command; returns the process's exit code.
     *
     * @param settingsFile the settings file, whose folder holds the session logs
     * @param here the current folder, where the report goes
     * @param browser opens a link; false when it could not
     */
    public static int run(Path settingsFile, Path here, PrintStream out, Predicate<String> browser, Redactor redactor) {
        return run(settingsFile, here, Path.of(System.getProperty("java.io.tmpdir", ".")), out, browser, redactor);
    }

    static int run(
            Path settingsFile, Path here, Path temp, PrintStream out, Predicate<String> browser, Redactor redactor) {
        Settings settings = new Settings(settingsFile);
        settings.load();
        Path folder = settingsFile.toAbsolutePath().getParent();
        if (folder == null) folder = Path.of(".");
        String log = read(folder.resolve(DebugLog.PREVIOUS_FILE)) + read(folder.resolve(DebugLog.FILE));
        String environment = redactor.redact(AppInfo.environment(AppInfo.runtime(), settingsFile));
        String report = redactor.redact(ProblemReport.build(settings, log));
        String text = environment + "\n\n" + report + "\n";
        Path file = write(here.resolve(FILE), text);
        if (file == null) file = write(temp.resolve(FILE), text);
        if (file == null) {
            out.println("Could not write " + FILE + " in " + here + " or in the temp folder, " + temp + ".");
            return 1;
        }
        out.println("Wrote the redacted report to " + file);
        out.println("Read it before you submit: anything left in it becomes public.");
        String url = ProblemReport.issueUrl(environment, report);
        if (!ProblemReport.fitsInUrl(environment, report)) {
            out.println("The report is too long for a link: paste it from " + file + " into the issue.");
        }
        if (!browser.test(url)) out.println("Open this link to file the issue:\n" + url);
        return 0;
    }

    /** Writes the report; its path, or null when its folder can't be written. */
    private static @Nullable Path write(Path file, String text) {
        try {
            return Files.writeString(file, text, StandardCharsets.UTF_8).toAbsolutePath();
        } catch (IOException e) {
            return null;
        }
    }

    private static String read(Path file) {
        try {
            return Files.isRegularFile(file) ? Files.readString(file, StandardCharsets.UTF_8) : "";
        } catch (IOException e) {
            return "";
        }
    }
}
