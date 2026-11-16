package com.mirada;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import com.mirada.image.NativeLibraries;
import com.mirada.os.Os;
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
    /**
     * Mirada's repository on GitHub, {@code owner/name}: its source, its releases and its issues. A
     * pending exception in {@code pauta.json} links an issue here (PAUTA-07, PAUTA-10).
     */
    public static final String GITHUB_REPO = "adriandeleon/Mirada";
    /** The source code: the repository's page. */
    public static final String REPOSITORY = "https://github.com/" + GITHUB_REPO;
    /** The website, Mirada's home page (ABOUT-02). */
    public static final String HOMEPAGE = "https://mirada-project.dev";
    /** The user guide, on the website (DOCS-02, DOCS-06). */
    public static final String DOCS_URL = HOMEPAGE + "/docs/";
    /** Where Report a Problem opens its prefilled issue ({@code .github/ISSUE_TEMPLATE/bug_report.yml}). */
    public static final String NEW_ISSUE_URL = REPOSITORY + "/issues/new";
    /**
     * The releases, each with its notes, installers and source: About's "Source code and releases"
     * (ABOUT-02), and an update notice that has no page of its own.
     */
    public static final String RELEASES_URL = REPOSITORY + "/releases";
    /** What the update check asks: the newest release that is neither a draft nor a pre-release. */
    public static final String LATEST_RELEASE_API = "https://api.github.com/repos/" + GITHUB_REPO + "/releases/latest";

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
            return REPOSITORY + "/blob/main/CHANGELOG.md";
        }
        return REPOSITORY + "/releases/tag/v" + version;
    }

    /**
     * The edition's own page, which never changes: {@code https://pauta-project.dev/<edition>/},
     * never {@code latest/}, which moves on to editions Mirada may not meet (ABOUT-06).
     */
    public static String pautaUrl() {
        return "https://pauta-project.dev/" + PAUTA_EDITION + "/";
    }

    /**
     * The website's page on Mirada's Pauta conformance and exceptions (PAUTA-13, PWIN-07): {@code
     * /docs/pauta/} until the site serves docs by version (DOCS-03), when it becomes this version's.
     */
    public static String pautaDocsUrl() {
        return DOCS_URL + "pauta/";
    }

    /**
     * What {@code --version} prints and the environment block opens with: version and build, then
     * the commit, then the Pauta edition (ABOUT-05) and the conformance with its count of exceptions
     * (ABOUT-09). Plain English on purpose: it goes into bug reports.
     */
    public static String versionText() {
        String build = BUILD_TIME.isEmpty() ? "" : " (built " + BUILD_TIME + ")";
        return NAME + " " + VERSION + build + "\n" + "Commit: " + COMMIT + "\n" + "Pauta: " + PAUTA_EDITION + "\n"
                + PautaConformance.environmentLine(PautaConformance.exceptions());
    }

    /**
     * The environment block About shows and copies: {@link #versionText}, then the runtime, the OS
     * and the settings file, with the home folder shown as {@code ~}.
     */
    public static String environment(String runtime, Path settingsFile) {
        return versionText() + "\n"
                + runtime + "\n"
                + "OS: " + Os.description() + "\n"
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

    /** The bundle's VERSIONS.txt, read once; null without a bundle. */
    private static final class Codecs {
        static final @Nullable String VERSIONS = readCodecVersions();
    }

    /**
     * The bundle's VERSIONS.txt, joined on one line; null without a bundle. Read the first time it is
     * asked for, which the window does off the FX thread after its first paint.
     */
    private static @Nullable String codecVersions() {
        return Codecs.VERSIONS;
    }

    private static @Nullable String readCodecVersions() {
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
