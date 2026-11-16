package com.mirada.os;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.charset.Charset;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

/**
 * Windows: {@code --help}, {@code --version}, {@code --report} and the refusal of an unknown option
 * print in the console they were typed in (CLI-11, Pauta 6.1.0). Mirada's executable is a GUI one (the
 * native image is linked {@code /SUBSYSTEM:WINDOWS} by the StaticFX feature, jpackage's launcher is
 * one too), so it starts with no console and its output goes nowhere; {@link #attach} joins the
 * console of the shell that started it, as {@code AttachConsole(ATTACH_PARENT_PROCESS)} does, and
 * points {@code System.out} and {@code System.err} at it. Started from Explorer there is no parent
 * console, and nothing changes. Output sent to a file or a pipe keeps going there.
 *
 * <p>cmd does not wait for a GUI program, so the text appears after its next prompt, and {@code
 * %ERRORLEVEL%} is the exit status only under {@code start /wait}; PowerShell waits when the output is
 * piped ({@code mirada --version | Out-Host}).
 */
public final class WindowsConsole {

    private static final int STD_OUTPUT_HANDLE = -11;
    private static final int STD_ERROR_HANDLE = -12;
    private static final int ATTACH_PARENT_PROCESS = -1;

    private static boolean tried;

    private WindowsConsole() {}

    /**
     * Before {@code main} prints, on Windows: the parent's console for whichever of standard output and
     * error has nowhere to go. Elsewhere, and on a second call, nothing. Never throws.
     */
    public static synchronized void attach() {
        if (Os.host() != Os.Kind.WINDOWS || tried) return;
        tried = true;
        try (Arena arena = Arena.ofConfined()) {
            Linker linker = Linker.nativeLinker();
            SymbolLookup kernel = SymbolLookup.libraryLookup("kernel32", arena);
            MethodHandle standard = linker.downcallHandle(
                    kernel.find("GetStdHandle").orElseThrow(), FunctionDescriptor.of(ADDRESS, JAVA_INT));
            MethodHandle attachConsole = linker.downcallHandle(
                    kernel.find("AttachConsole").orElseThrow(), FunctionDescriptor.of(JAVA_INT, JAVA_INT));
            boolean out = missing((MemorySegment) standard.invokeExact(STD_OUTPUT_HANDLE));
            boolean err = missing((MemorySegment) standard.invokeExact(STD_ERROR_HANDLE));
            if (!out && !err) return;
            if ((int) attachConsole.invokeExact(ATTACH_PARENT_PROCESS) == 0) return;
            if (out) System.setOut(console());
            if (err) System.setErr(console());
        } catch (Throwable e) {
            // No console to be had: there is nowhere to say so either.
        }
    }

    /** A handle that is none: NULL, or INVALID_HANDLE_VALUE. */
    static boolean missing(MemorySegment handle) {
        return handle.address() == 0 || handle.address() == -1;
    }

    private static PrintStream console() throws FileNotFoundException {
        return new PrintStream(new FileOutputStream("CONOUT$"), true, Charset.defaultCharset());
    }
}
