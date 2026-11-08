package com.mirada;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import com.mirada.image.NativeLibraries;
import org.jspecify.annotations.Nullable;

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
    /** Where Report a Problem opens its prefilled issue ({@code .github/ISSUE_TEMPLATE/bug_report.yml}). */
    public static final String NEW_ISSUE_URL = HOMEPAGE + "/issues/new";
    /** The releases, for an update notice that has no page of its own. */
    public static final String RELEASES_URL = HOMEPAGE + "/releases";
    /** What the update check asks: the newest release that is neither a draft nor a pre-release. */
    public static final String LATEST_RELEASE_API = "https://api.github.com/repos/adriandeleon/Mirada/releases/latest";

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

    /**
     * This runtime's line: Java, JavaFX once its toolkit has said its version (not for {@code
     * --report}, which never starts it), and the codec bundle.
     */
    public static String runtime() {
        return runtime(
                System.getProperty("java.runtime.version"),
                System.getProperty("javafx.runtime.version"),
                codecVersions());
    }

    /** {@code Java 25.0.1 · JavaFX 27 · Codecs: libheif v1.23.7, ...}, leaving out what is unknown. */
    static String runtime(@Nullable String java, @Nullable String javafx, @Nullable String codecs) {
        StringBuilder out = new StringBuilder();
        if (java != null) out.append("Java ").append(java);
        if (javafx != null)
            out.append(out.isEmpty() ? "" : " · ").append("JavaFX ").append(javafx);
        out.append(out.isEmpty() ? "" : "\n")
                .append("Codecs: ")
                .append(codecs == null || codecs.isBlank() ? "the system's, or none" : codecs);
        return out.toString();
    }

    /** The bundle's VERSIONS.txt, joined on one line; null without a bundle. */
    private static @Nullable String codecVersions() {
        Path versions = NativeLibraries.versionsFile();
        if (versions == null) return null;
        try {
            return String.join(", ", Files.readAllLines(versions, StandardCharsets.UTF_8))
                    .replace(" (asm: true)", "")
                    .replace(" (asm: false)", " (no asm)")
                    .replace(" (simd: ON)", "")
                    .replace(" (simd: OFF)", " (no SIMD)");
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** Replaces the user's home folder with {@code ~}. */
    public static String tildeHome(String path) {
        String home = System.getProperty("user.home", "");
        if (!home.isEmpty() && path.startsWith(home)) return "~" + path.substring(home.length());
        return path;
    }
}
