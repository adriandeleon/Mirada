package com.mirada.report;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.mirada.diagnostics.DebugLog;
import com.mirada.os.Os;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code mirada --report}: the redacted report written and the issue opened, with no window (CLI-05). */
class ReportCommandTest {

    @TempDir
    Path folder;

    @Test
    void writesTheRedactedReportAndOpensTheIssue() throws IOException {
        Path home = folder.resolve("home");
        Path config = home.resolve(".mirada");
        Files.createDirectories(config);
        Files.writeString(
                config.resolve(DebugLog.PREVIOUS_FILE), "12:00:00.000  UNCAUGHT: crashed for ada@example.com\n");
        Files.writeString(
                config.resolve(DebugLog.FILE), "12:01:00.000  WARNING could not open " + home + "/Pictures/a.heic\n");
        Redactor redactor = new Redactor(home.toString(), "ada", "");
        ByteArrayOutputStream printed = new ByteArrayOutputStream();
        List<String> opened = new ArrayList<>();

        Path here = Files.createDirectories(folder.resolve("here"));
        int code = ReportCommand.run(
                config.resolve("settings.properties"),
                here,
                new PrintStream(printed, true, StandardCharsets.UTF_8),
                url -> opened.add(url),
                redactor);

        assertEquals(0, code);
        // In the folder the command was typed in, its path printed (CLI-05).
        Path written = here.resolve(ReportCommand.FILE);
        String report = Files.readString(written);
        assertTrue(report.contains("Mirada "), report);
        assertTrue(report.contains("crashed for <email>"), report);
        assertTrue(report.contains("could not open ~/Pictures/a.heic"), report);
        assertFalse(report.contains(home.toString()), report);
        assertEquals(1, opened.size());
        assertTrue(URLDecoder.decode(opened.getFirst(), StandardCharsets.UTF_8).contains("crashed for <email>"));
        assertTrue(printed.toString(StandardCharsets.UTF_8)
                .contains("Wrote the redacted report to " + written.toAbsolutePath()));
        assertFalse(Files.exists(config.resolve(ReportCommand.FILE)), "not in the config folder");
        assertFalse(Files.exists(config.resolve("settings.properties")), "the report never writes the settings");
    }

    @Test
    void withNoBrowserTheLinkIsPrinted() {
        ByteArrayOutputStream printed = new ByteArrayOutputStream();
        int code = ReportCommand.run(
                folder.resolve("settings.properties"),
                folder,
                new PrintStream(printed, true, StandardCharsets.UTF_8),
                url -> false,
                new Redactor("/nowhere", "", ""));
        assertEquals(0, code);
        assertTrue(printed.toString(StandardCharsets.UTF_8).contains("Open this link to file the issue:\nhttps://"));
    }

    @Test
    void aCurrentFolderThatCantBeWrittenSendsTheReportToTheTempFolder() throws IOException {
        // A file where the folder should be: nothing can be written "in" it, on any OS.
        Path here = Files.writeString(folder.resolve("not-a-folder"), "");
        Path temp = Files.createDirectories(folder.resolve("temp"));
        ByteArrayOutputStream printed = new ByteArrayOutputStream();
        int code = ReportCommand.run(
                folder.resolve("config/settings.properties"),
                here,
                temp,
                new PrintStream(printed, true, StandardCharsets.UTF_8),
                url -> true,
                new Redactor("/nowhere", "", ""));
        assertEquals(0, code);
        Path written = temp.resolve(ReportCommand.FILE);
        assertTrue(Files.isRegularFile(written));
        assertTrue(printed.toString(StandardCharsets.UTF_8)
                .contains(written.toAbsolutePath().toString()));
    }

    @Test
    void theBrowserCommandIsThePlatformsOwn() {
        assertEquals(List.of("open", "u"), Browser.command(Os.Kind.MAC, "u"));
        assertEquals(List.of("rundll32", "url.dll,FileProtocolHandler", "u"), Browser.command(Os.Kind.WINDOWS, "u"));
        assertEquals(List.of("xdg-open", "u"), Browser.command(Os.Kind.LINUX, "u"));
    }
}
