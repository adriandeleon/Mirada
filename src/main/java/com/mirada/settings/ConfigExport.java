package com.mirada.settings;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Export Configuration (SET-12, Pauta 4.0.1): the config folder as a timestamped {@code .zip};
 * {@link ConfigImport} reads one back. What is only this run's stays out: the session logs, the
 * files kept aside ({@code .bak}) and half-written ones ({@code .tmp}).
 *
 * <p>No JavaFX, so it runs off the FX thread; the javafx-app-template's {@code ConfigExporter}.
 */
public final class ConfigExport {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private ConfigExport() {}

    /** The name the save dialog suggests: {@code mirada-config-<yyyyMMdd-HHmmss>.zip}. */
    public static String defaultName(LocalDateTime now) {
        return "mirada-config-" + STAMP.format(now) + ".zip";
    }

    /** Whether a path relative to the config folder belongs in an export. */
    static boolean exported(Path relative) {
        String name = String.valueOf(relative.getFileName());
        return !name.endsWith(".log") && !name.endsWith(".bak") && !name.endsWith(".tmp") && !name.startsWith(".");
    }

    /**
     * Writes the config folder to {@code zip}, its files by their paths inside it, in name order;
     * returns how many went in. An empty folder or none makes an empty zip, which Import refuses.
     */
    public static int export(Path folder, Path zip) throws IOException {
        List<Path> files = List.of();
        if (Files.isDirectory(folder)) {
            try (Stream<Path> walk = Files.walk(folder)) {
                files = walk.filter(Files::isRegularFile)
                        .filter(file -> !file.toAbsolutePath().equals(zip.toAbsolutePath()))
                        .filter(file -> exported(folder.relativize(file)))
                        .sorted()
                        .toList();
            }
        }
        try (OutputStream out = Files.newOutputStream(zip);
                ZipOutputStream zipOut = new ZipOutputStream(out)) {
            for (Path file : files) {
                zipOut.putNextEntry(
                        new ZipEntry(folder.relativize(file).toString().replace('\\', '/')));
                Files.copy(file, zipOut);
                zipOut.closeEntry();
            }
        }
        return files.size();
    }
}
