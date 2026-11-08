package com.mirada;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Identity: the one place a version number is read from.
 *
 * <p>It comes from {@code pom.xml} through a Maven-filtered {@code build-info.properties}, so
 * cutting a release means editing one number.
 */
public final class AppInfo {

    public static final String NAME = "Mirada";
    public static final String VERSION = load("version", "0.0.0");
    public static final String BUILD_TIME = load("build.time", "");
    /** The short commit the build came from, or {@code unknown} outside a Git checkout. */
    public static final String COMMIT = load("build.commit", "unknown");
    /**
     * The Pauta edition Mirada is built to: {@code pauta.version} in {@code pom.xml}, its one source
     * (ABOUT-08). The edition it meets, not the newest.
     */
    public static final String PAUTA_EDITION = load("pauta.version", "unknown");

    public static final String COPYRIGHT = "Copyright © 2026 Adrián Arturo De León Saldivar";
    public static final String LICENSE = "MIT License";
    public static final String HOMEPAGE = "https://github.com/adriandeleon/Mirada";
    /** The user guide, on the website (DOCS-02, DOCS-06). */
    public static final String DOCS_URL = "https://mirada-project.dev/docs/";

    private AppInfo() {}

    /**
     * Falls back to the default when the resource was not filtered — that happens when classes are
     * run straight from an IDE build rather than through Maven, and it should read as "unknown
     * build" rather than crash.
     */
    private static String load(String key, String fallback) {
        try (InputStream in = AppInfo.class.getResourceAsStream("/com/mirada/build-info.properties")) {
            if (in == null) return fallback;
            Properties properties = new Properties();
            properties.load(in);
            String value = properties.getProperty(key, fallback);
            // An unfiltered resource still contains the literal ${...} placeholder.
            return value.startsWith("${") ? fallback : value;
        } catch (IOException e) {
            return fallback;
        }
    }

    /** True for a development build; releases drop the suffix. */
    public static boolean isSnapshot() {
        return VERSION.endsWith("-SNAPSHOT");
    }

    /**
     * This build's release notes (DOCS-05): the GitHub release of {@link #VERSION}, whose notes are
     * its {@code CHANGELOG.md} section, or the changelog itself for a snapshot, which has no release.
     */
    public static String releaseNotesUrl() {
        return releaseNotesUrl(VERSION);
    }

    static String releaseNotesUrl(String version) {
        if (version.endsWith("-SNAPSHOT") || version.equals("0.0.0")) {
            return HOMEPAGE + "/blob/main/CHANGELOG.md";
        }
        return HOMEPAGE + "/releases/tag/v" + version;
    }

    /**
     * The edition's own page, which never changes: {@code https://pauta-project.dev/<edition>/},
     * never {@code latest/}, which moves on to editions Mirada may not meet (ABOUT-06).
     */
    public static String pautaUrl() {
        return "https://pauta-project.dev/" + PAUTA_EDITION + "/";
    }

    /**
     * What {@code --version} prints and the environment block opens with: version and build, then
     * the commit, then the Pauta edition (ABOUT-05). Plain English on purpose: it goes into bug
     * reports.
     */
    public static String versionText() {
        String build = BUILD_TIME.isEmpty() ? "" : " (built " + BUILD_TIME + ")";
        return NAME + " " + VERSION + build + "\n" + "Commit: " + COMMIT + "\n" + "Pauta: " + PAUTA_EDITION;
    }

    /**
     * The environment block About shows and copies: {@link #versionText}, then the runtime, the OS
     * and the settings file, with the home folder shown as {@code ~}.
     */
    public static String environment(String runtime, Path settingsFile) {
        return versionText() + "\n"
                + runtime + "\n"
                + "OS: " + System.getProperty("os.name") + " " + System.getProperty("os.version") + " ("
                + System.getProperty("os.arch") + ")\n"
                + "Settings: " + tildeHome(settingsFile.toString());
    }

    /** Replaces the user's home folder with {@code ~}. */
    public static String tildeHome(String path) {
        String home = System.getProperty("user.home", "");
        if (!home.isEmpty() && path.startsWith(home)) return "~" + path.substring(home.length());
        return path;
    }
}
