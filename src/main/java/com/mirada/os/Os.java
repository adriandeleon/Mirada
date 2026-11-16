package com.mirada.os;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

/**
 * Which OS Mirada runs on (OS-02, Pauta 6.1.0): the one class that reads {@code os.name}. Every other
 * class asks here, so the places that differ by OS are the callers of these methods.
 *
 * <p>Two answers. {@link #current} is the OS Mirada <em>behaves</em> as: its keymap, its menus, the
 * names it gives the trash. Tests run as any OS through {@link #override} or the {@code mirada.os}
 * system property (OS-03). {@link #host} is the OS it really runs on, which no override moves: what
 * loads a native library, starts a program or registers with the system must ask that one, or a test
 * run as macOS would look for a {@code .dylib} on Linux.
 */
public final class Os {

    /** The three OSes Mirada is built for. */
    public enum Kind {
        WINDOWS,
        MAC,
        LINUX
    }

    /** A system property that runs Mirada as another OS: {@code windows}, {@code mac} or {@code linux}. */
    public static final String PROPERTY = "mirada.os";

    /** The OS this process runs on, read once. */
    private static final Kind HOST = parse(System.getProperty("os.name", ""));

    private static volatile @Nullable Kind override;

    private Os() {}

    /** The OS's own name, version and architecture, for About and a problem report. */
    public static String description() {
        return System.getProperty("os.name", "") + " " + System.getProperty("os.version", "") + " ("
                + System.getProperty("os.arch", "") + ")";
    }

    /** The OS this run behaves as: the override, else the property, else the one it runs on. */
    public static Kind current() {
        Kind forced = override;
        if (forced != null) return forced;
        String property = System.getProperty(PROPERTY);
        if (property != null && !property.isBlank()) return parse(property);
        return HOST;
    }

    /** The OS this process really runs on, whatever {@link #current} behaves as. */
    public static Kind host() {
        return HOST;
    }

    /** {@code Mac OS X}, {@code Darwin} and {@code mac} are MAC, {@code Windows 11} WINDOWS, the rest LINUX. */
    static Kind parse(String name) {
        String os = name.toLowerCase(Locale.ROOT);
        if (os.startsWith("mac") || os.startsWith("darwin")) return Kind.MAC;
        if (os.startsWith("windows")) return Kind.WINDOWS;
        return Kind.LINUX;
    }

    public static boolean isWindows() {
        return current() == Kind.WINDOWS;
    }

    public static boolean isMac() {
        return current() == Kind.MAC;
    }

    public static boolean isLinux() {
        return current() == Kind.LINUX;
    }

    /** For tests: behave as {@code kind} until reset with {@code null} (OS-03). */
    public static void override(@Nullable Kind kind) {
        override = kind;
    }
}
