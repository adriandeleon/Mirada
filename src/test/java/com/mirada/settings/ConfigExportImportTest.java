package com.mirada.settings;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import com.mirada.folder.Sorting;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Export writes a timestamped zip of the config folder; Import replaces only the ticked parts, and
 * refuses a zip that is not an export before anything is written (SET-12).
 */
class ConfigExportImportTest {

    @TempDir
    Path dir;

    @Test
    void theZipIsNamedForTheMomentItWasMade() {
        assertEquals(
                "mirada-config-20261108-093005.zip", ConfigExport.defaultName(LocalDateTime.of(2026, 11, 8, 9, 30, 5)));
    }

    @Test
    void theExportHoldsTheSettingsAndBookmarksButNotLogsOrBackups() throws IOException {
        Path folder = dir.resolve("config");
        Settings settings = new Settings(folder.resolve("settings.properties"));
        settings.setTheme(Settings.ThemeChoice.DARK);
        new Bookmarks(folder.resolve("bookmarks.properties")).toggle(dir.resolve("a.jpg"), false);
        for (String name : List.of(
                "mirada-session.log",
                "mirada-session.prev.log",
                "settings.properties.corrupt.bak",
                "settings.properties.v9.bak",
                "bookmarks.properties.tmp",
                ".lock")) Files.writeString(folder.resolve(name), "x");
        Path zip = dir.resolve("out.zip");

        assertEquals(2, ConfigExport.export(folder, zip));
        Map<String, String> entries = entries(zip);
        assertEquals(List.of("bookmarks.properties", "settings.properties"), new ArrayList<>(entries.keySet()));
        assertTrue(entries.get("settings.properties").contains("theme=dark"));
        assertTrue(entries.get("settings.properties").contains("schemaVersion=" + Settings.SCHEMA_VERSION));
    }

    @Test
    void aZipSavedIntoTheFolderItselfIsNotPutInsideItself() throws IOException {
        Path folder = dir.resolve("config");
        new Settings(folder.resolve("settings.properties")).setKeepZoom(true);
        Path zip = folder.resolve("backup.zip");
        assertEquals(1, ConfigExport.export(folder, zip));
        assertEquals(0, ConfigExport.export(dir.resolve("none"), dir.resolve("empty.zip")));
    }

    @Test
    void whatIsExportedImportsBackWhole() throws IOException {
        Path from = dir.resolve("from").resolve("settings.properties");
        Settings settings = new Settings(from);
        settings.setTheme(Settings.ThemeChoice.LIGHT);
        settings.setSorting(new Sorting(Sorting.By.SIZE, true));
        settings.setKeymap("emacs");
        new Bookmarks(Bookmarks.fileBeside(from)).setNote(dir.resolve("b.jpg"), false, "the good one");
        Path zip = dir.resolve("config.zip");
        ConfigExport.export(from.getParent(), zip);

        Path to = dir.resolve("to").resolve("settings.properties");
        ConfigImport.Scan scan = ConfigImport.read(zip);
        assertEquals(EnumSet.allOf(ConfigImport.Part.class), scan.available());
        assertEquals(EnumSet.allOf(ConfigImport.Part.class), ConfigImport.apply(scan, to, scan.available()));
        Settings imported = new Settings(to);
        imported.load();
        assertEquals(Settings.ThemeChoice.LIGHT, imported.theme());
        assertEquals(new Sorting(Sorting.By.SIZE, true), imported.sorting());
        assertEquals("emacs", imported.keymap());
        Bookmarks marks = new Bookmarks(Bookmarks.fileBeside(to));
        marks.load();
        assertEquals("the good one", marks.all().getFirst().note());
    }

    @Test
    void onlyTheTickedPartsAreReplaced() throws Exception {
        Path here = dir.resolve("here").resolve("settings.properties");
        Settings settings = new Settings(here);
        settings.setTheme(Settings.ThemeChoice.DARK);
        settings.setKeymap("cua");
        settings.setWindow(new Settings.WindowPlace(10, 20, 1234, 800, false));
        settings.addRecent(dir);
        Files.writeString(here, Files.readString(here) + "keys.go.next=n\n");
        Bookmarks marks = new Bookmarks(Bookmarks.fileBeside(here));
        marks.toggle(dir.resolve("mine.jpg"), false);

        Path zip = zip(Map.of(
                "settings.properties",
                "schemaVersion=2\ntheme=light\nkeymap=emacs\nkeys.go.first=g\nwindow.bounds=0,0,500,500\n"
                        + "recent.0=/elsewhere\n",
                "bookmarks.properties",
                "mark.0.path=/theirs.jpg\nmark.0.bookmark=true\n"));
        ConfigImport.Scan scan = ConfigImport.read(zip);
        assertEquals(EnumSet.allOf(ConfigImport.Part.class), scan.available());

        assertEquals(
                EnumSet.of(ConfigImport.Part.KEYS), ConfigImport.apply(scan, here, EnumSet.of(ConfigImport.Part.KEYS)));
        settings.reload();
        assertEquals("emacs", settings.keymap(), "the ticked part is replaced");
        assertEquals(Map.of("go.first", "g"), settings.keys(), "a key the zip lacks is gone with the part");
        assertEquals(Settings.ThemeChoice.DARK, settings.theme(), "an unticked part is kept");
        marks.reload();
        assertEquals(
                dir.resolve("mine.jpg").toAbsolutePath().normalize(),
                marks.all().getFirst().path());

        ConfigImport.apply(scan, here, EnumSet.allOf(ConfigImport.Part.class));
        settings.reload();
        marks.reload();
        assertEquals(Settings.ThemeChoice.LIGHT, settings.theme());
        assertEquals(1234, settings.window().width(), "this computer's own state is never imported");
        assertEquals(List.of(dir), settings.recent());
        assertEquals(
                Path.of("/theirs.jpg").toAbsolutePath().normalize(),
                marks.all().getFirst().path());
    }

    @Test
    void theListenersHearAnImport() throws IOException {
        Path here = dir.resolve("settings.properties");
        Settings settings = new Settings(here);
        int[] heard = {0};
        settings.addListener(() -> heard[0]++);
        settings.reload();
        assertEquals(1, heard[0]);
        Bookmarks marks = new Bookmarks(Bookmarks.fileBeside(here));
        marks.addListener(() -> heard[0]++);
        marks.reload();
        assertEquals(2, heard[0]);
    }

    @Test
    void whatEachKeyBelongsTo() {
        assertNull(ConfigImport.partOf("window.bounds"));
        assertNull(ConfigImport.partOf("window.maximized"));
        assertNull(ConfigImport.partOf("recent.3"));
        assertNull(ConfigImport.partOf("updates.lastCheck"));
        assertNull(ConfigImport.partOf("schemaVersion"));
        assertEquals(ConfigImport.Part.KEYS, ConfigImport.partOf("keymap"));
        assertEquals(ConfigImport.Part.KEYS, ConfigImport.partOf("palette.keys"));
        assertEquals(ConfigImport.Part.KEYS, ConfigImport.partOf("keys.go.next"));
        assertEquals(ConfigImport.Part.PREFERENCES, ConfigImport.partOf("theme"));
        assertEquals(ConfigImport.Part.PREFERENCES, ConfigImport.partOf("window.statusBar"));
        assertEquals("Bookmarks and notes", ConfigImport.Part.BOOKMARKS.label());
    }

    @Test
    void aZipThatIsNotAnExportIsRefusedBeforeAnythingIsWritten() throws Exception {
        Path notZip = Files.writeString(dir.resolve("x.zip"), "hello");
        assertRefused(ConfigImport.Refused.Reason.NOT_A_ZIP, notZip);
        assertRefused(ConfigImport.Refused.Reason.NOT_AN_EXPORT, zip(Map.of("a.properties", "x=1")));
        assertRefused(ConfigImport.Refused.Reason.NOT_AN_EXPORT, zip(Map.of("settings.properties", "m=\\u1")));
        assertRefused(
                ConfigImport.Refused.Reason.UNSAFE_PATH,
                zip(Map.of("settings.properties", "theme=dark", "../evil", "x")));
        assertRefused(
                ConfigImport.Refused.Reason.NEWER_VERSION, zip(Map.of("settings.properties", "schemaVersion=999")));
        assertRefused(
                ConfigImport.Refused.Reason.NEWER_VERSION,
                zip(Map.of("settings.properties", "theme=dark", "bookmarks.properties", "schemaVersion=999")));
        assertFalse(Files.exists(dir.resolve("evil")));
        for (ConfigImport.Refused.Reason reason : ConfigImport.Refused.Reason.values())
            assertFalse(new ConfigImport.Refused(reason, "").sentence().isBlank());
    }

    @Test
    void anEntryOverTheLimitIsRefusedByWhatItInflatesTo() throws Exception {
        String big = "x".repeat((int) ConfigImport.MAX_ENTRY_BYTES + 1);
        assertRefused(
                ConfigImport.Refused.Reason.TOO_LARGE, zip(Map.of("settings.properties", "theme=dark", "big", big)));
        Map<String, String> many = new LinkedHashMap<>();
        for (int i = 0; i <= ConfigImport.MAX_ENTRIES; i++) many.put("f" + i, "");
        assertRefused(ConfigImport.Refused.Reason.TOO_LARGE, zip(many));
    }

    @Test
    void aPathIsSafeOnlyInsideTheFolder() {
        assertTrue(ConfigImport.safe("settings.properties"));
        assertTrue(ConfigImport.safe("sub/file"));
        assertFalse(ConfigImport.safe(""));
        assertFalse(ConfigImport.safe("/etc/passwd"));
        assertFalse(ConfigImport.safe("a/../../b"));
        assertFalse(ConfigImport.safe("C:/x"));
        assertFalse(ConfigImport.safe("a\\b"));
    }

    private static void assertRefused(ConfigImport.Refused.Reason reason, Path zip) {
        assertEquals(
                reason,
                assertThrows(ConfigImport.Refused.class, () -> ConfigImport.read(zip))
                        .reason());
    }

    private Path zip(Map<String, String> entries) throws IOException {
        Path zip = Files.createTempFile(dir, "import", ".zip");
        try (OutputStream file = Files.newOutputStream(zip);
                ZipOutputStream out = new ZipOutputStream(file)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                out.putNextEntry(new ZipEntry(entry.getKey()));
                out.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
        return zip;
    }

    private static Map<String, String> entries(Path zip) throws IOException {
        Map<String, String> out = new LinkedHashMap<>();
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
            for (ZipEntry entry; (entry = in.getNextEntry()) != null; )
                out.put(entry.getName(), new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
        return out;
    }
}
