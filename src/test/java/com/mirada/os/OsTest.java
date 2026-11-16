package com.mirada.os;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** OS-02, OS-03: one class names the OS, and a test can run as any. */
class OsTest {

    @AfterEach
    void reset() {
        Os.override(null);
    }

    @Test
    void theNamesTheJvmGivesAreRead() {
        assertEquals(Os.Kind.MAC, Os.parse("Mac OS X"));
        assertEquals(Os.Kind.MAC, Os.parse("Darwin"));
        assertEquals(Os.Kind.WINDOWS, Os.parse("Windows 11"));
        assertEquals(Os.Kind.LINUX, Os.parse("Linux"));
        assertEquals(Os.Kind.LINUX, Os.parse("FreeBSD"));
        assertEquals(Os.Kind.WINDOWS, Os.parse("windows"));
    }

    @Test
    void theOverrideMovesWhatMiradaBehavesAsButNotTheHost() {
        Os.Kind host = Os.host();
        for (Os.Kind kind : Os.Kind.values()) {
            Os.override(kind);
            assertEquals(kind, Os.current());
            assertEquals(kind == Os.Kind.MAC, Os.isMac());
            assertEquals(kind == Os.Kind.WINDOWS, Os.isWindows());
            assertEquals(kind == Os.Kind.LINUX, Os.isLinux());
            assertEquals(host, Os.host(), "a native library is still the host's");
        }
        Os.override(null);
        assertEquals(host, Os.current());
    }

    @Test
    void theDescriptionNamesTheOsItsVersionAndArchitecture() {
        String description = Os.description();
        assertTrue(description.contains(System.getProperty("os.arch")), description);
        assertFalse(description.isBlank());
    }
}
