package com.mirada.perf;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PERF-20: what the FX-thread sampler counts as idle, as a stall, and whom it blames. */
class FxSamplerTest {

    private static StackTraceElement frame(String type, String method) {
        return new StackTraceElement(type, method, null, -1);
    }

    @Test
    void anFxThreadInItsEventLoopIsIdle() {
        assertTrue(FxSampler.idle(new StackTraceElement[] {frame("com.sun.glass.ui.gtk.GtkApplication", "_runLoop")}));
        assertTrue(FxSampler.idle(new StackTraceElement[] {frame("jdk.internal.misc.Unsafe", "park")}));
        assertTrue(FxSampler.idle(new StackTraceElement[0]));
        assertFalse(FxSampler.idle(new StackTraceElement[] {frame("java.nio.file.Files", "list")}));
    }

    @Test
    void aStallIsAHundredMillisecondsOfBusySamples() {
        assertFalse(FxSampler.stalled(FxSampler.STALL_MS / FxSampler.INTERVAL_MS - 1));
        assertTrue(FxSampler.stalled(FxSampler.STALL_MS / FxSampler.INTERVAL_MS));
    }

    @Test
    void theDeepestOfMiradasFramesIsBlamedElseTheToolkit() {
        StackTraceElement[] stack = {
            frame("java.nio.file.Files", "list"),
            frame("com.mirada.perf.FxSampler", "sample"),
            frame("com.mirada.folder.FolderImages", "open"),
            frame("com.mirada.ui.ViewerWindow", "open"),
        };
        assertEquals("folder.FolderImages.open", FxSampler.attribute(stack));
        assertEquals(
                "(toolkit) javafx.scene.Parent.layout",
                FxSampler.attribute(new StackTraceElement[] {frame("javafx.scene.Parent", "layout")}));
        assertEquals("(nothing)", FxSampler.attribute(new StackTraceElement[0]));
    }

    @Test
    void aStallAndTheTallyAreReportedHottestFirst() {
        StackTraceElement[] stack = {frame("java.nio.file.Files", "list"), frame("com.mirada.ui.ViewerWindow", "open")};
        String stall = FxSampler.stall(30, stack);
        assertTrue(stall.startsWith("[perf] FX thread busy 150 ms in ui.ViewerWindow.open"), stall);
        assertTrue(stall.contains("at java.nio.file.Files.list"), stall);
        Map<String, Integer> tally = new HashMap<>(Map.of("ui.A.cool", 1, "ui.B.hot", 9));
        String report = FxSampler.report(tally);
        assertTrue(report.indexOf("ui.B.hot") < report.indexOf("ui.A.cool"), report);
        assertEquals("", FxSampler.report(new HashMap<>()));
    }
}
