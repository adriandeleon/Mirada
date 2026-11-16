package com.mirada.os;

import java.io.PrintStream;
import java.lang.foreign.MemorySegment;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** CLI-11: what counts as no standard handle, and nothing done off Windows. */
class WindowsConsoleTest {

    @Test
    void nullAndInvalidHandleValueAreNone() {
        assertTrue(WindowsConsole.missing(MemorySegment.NULL));
        assertTrue(WindowsConsole.missing(MemorySegment.ofAddress(-1)));
        assertFalse(WindowsConsole.missing(MemorySegment.ofAddress(0x1234)));
    }

    @Test
    void offWindowsTheStreamsAreLeftAlone() {
        if (Os.host() == Os.Kind.WINDOWS) return;
        PrintStream out = System.out;
        PrintStream err = System.err;
        WindowsConsole.attach();
        assertSame(out, System.out);
        assertSame(err, System.err);
    }
}
