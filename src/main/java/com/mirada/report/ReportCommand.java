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

/**
 * {@code mirada --report}: the problem report from the command line, for when Mirada cannot start
 * (CLI-05, DIAG-06, Pauta 4.0.1). Reads the settings and the last two session logs, redacts them,
 * writes {@link #FILE} beside the settings and opens the prefilled issue. No JavaFX.
 */
public final class ReportCommand {

    public static final String FILE = "problem-report.txt";

    private ReportCommand() {}

    /**
     * Runs the command; returns the process's exit code.
     *
     * @param settingsFile the settings file, whose folder holds the session logs
     * @param browser opens a link; false when it could not
     */
    public static int run(Path settingsFile, PrintStream out, Predicate<String> browser, Redactor redactor) {
        Settings settings = new Settings(settingsFile);
        settings.load();
        Path folder = settingsFile.toAbsolutePath().getParent();
        if (folder == null) folder = Path.of(".");
        String log = read(folder.resolve(DebugLog.PREVIOUS_FILE)) + read(folder.resolve(DebugLog.FILE));
        String environment = redactor.redact(AppInfo.environment(AppInfo.runtime(), settingsFile));
        String report = redactor.redact(ProblemReport.build(settings, log));
        Path file = folder.resolve(FILE);
        try {
            Files.createDirectories(folder);
            Files.writeString(file, environment + "\n\n" + report + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            out.println("Could not write " + file + ": " + e.getMessage());
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

    private static String read(Path file) {
        try {
            return Files.isRegularFile(file) ? Files.readString(file, StandardCharsets.UTF_8) : "";
        } catch (IOException e) {
            return "";
        }
    }
}
