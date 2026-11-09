package com.mirada.perf;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Samples the FX thread's stack while Mirada runs, and names what held it when it stalls (PERF-20; after
 * Editora's {@code perf/Startup} sampler). Off unless {@code MIRADA_PERF_SAMPLE=1} or {@code
 * -Dmirada.perfSample}: then every {@value #INTERVAL_MS} ms a daemon thread reads the FX thread's stack,
 * which neither suspends nor instruments it, and a run of busy samples of at least {@value #STALL_MS} ms is
 * printed to stderr with the frames that held it. At exit, the busy samples are tallied by the deepest
 * {@code com.mirada} frame, the method responsible, hottest first.
 *
 * <pre>
 *   MIRADA_PERF_SAMPLE=1 scripts/dev-run.sh ~/Pictures
 * </pre>
 */
public final class FxSampler {

    private static final boolean ENABLED =
            "1".equals(System.getenv("MIRADA_PERF_SAMPLE")) || System.getProperty("mirada.perfSample") != null;

    /** Between two samples. */
    static final int INTERVAL_MS = 5;

    /** The shortest busy stretch worth a report: six frames at 60 Hz. */
    static final int STALL_MS = 100;

    /** How many of a stall's frames are printed. */
    private static final int FRAMES = 14;

    /** Leaf methods of an FX thread waiting for work: GTK's, Windows', macOS' and the Headless glass's loops. */
    private static final Set<String> WAITING =
            Set.of("_runLoop", "_enterNestedEventLoopImpl", "park", "parkNanos", "wait", "wait0", "sleep", "sleep0");

    private FxSampler() {}

    /** Starts sampling {@code fxThread} when asked for; nothing otherwise. */
    public static void start(Thread fxThread) {
        if (!ENABLED) return;
        Map<String, Integer> tally = new HashMap<>();
        Thread.ofPlatform().daemon().name("fx-sampler").start(() -> sample(fxThread, tally));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> System.err.print(report(tally)), "fx-sampler-report"));
    }

    private static void sample(Thread fxThread, Map<String, Integer> tally) {
        int busy = 0;
        StackTraceElement[] longest = new StackTraceElement[0];
        while (fxThread.isAlive()) {
            StackTraceElement[] stack = fxThread.getStackTrace();
            if (idle(stack)) {
                if (stalled(busy)) System.err.print(stall(busy, longest));
                busy = 0;
            } else {
                busy++;
                longest = stack;
                synchronized (tally) {
                    tally.merge(attribute(stack), 1, Integer::sum);
                }
            }
            try {
                Thread.sleep(INTERVAL_MS);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    /** Whether {@code stack} is an FX thread waiting for work, or none at all. Pure. */
    static boolean idle(StackTraceElement[] stack) {
        return stack.length == 0 || WAITING.contains(stack[0].getMethodName());
    }

    /** Whether {@code samples} busy samples in a row are a stall worth naming. Pure. */
    static boolean stalled(int samples) {
        return (long) samples * INTERVAL_MS >= STALL_MS;
    }

    /**
     * The frame a sample is blamed on: the deepest {@code com.mirada} frame, as a JDK or JavaFX leaf says
     * what is running but not which of Mirada's methods asked for it; the leaf when there is none, which
     * means the toolkit itself was busy. Pure.
     */
    static String attribute(StackTraceElement[] stack) {
        for (StackTraceElement frame : stack) {
            if (frame.getClassName().startsWith("com.mirada.")
                    && !frame.getClassName().startsWith("com.mirada.perf.")) {
                return frame.getClassName().substring("com.mirada.".length()) + "." + frame.getMethodName();
            }
        }
        if (stack.length == 0) return "(nothing)";
        return "(toolkit) " + stack[0].getClassName() + "." + stack[0].getMethodName();
    }

    /** A stall's report: how long, whose, and the frames on top when it was last sampled. */
    static String stall(int samples, StackTraceElement[] stack) {
        StringBuilder out = new StringBuilder(
                String.format("[perf] FX thread busy %d ms in %s%n", (long) samples * INTERVAL_MS, attribute(stack)));
        for (int i = 0; i < Math.min(FRAMES, stack.length); i++)
            out.append("[perf]     at ").append(stack[i]).append('\n');
        return out.toString();
    }

    /** The busy samples by the method responsible, hottest first, at most forty. */
    static String report(Map<String, Integer> tally) {
        Map<String, Integer> snapshot;
        synchronized (tally) {
            snapshot = Map.copyOf(tally);
        }
        if (snapshot.isEmpty()) return "";
        StringBuilder out = new StringBuilder("[perf] FX thread busy, by method (" + INTERVAL_MS + " ms samples)\n");
        snapshot.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(40)
                .forEach(entry -> out.append(
                        String.format("[perf]   %6d ms  %s%n", (long) entry.getValue() * INTERVAL_MS, entry.getKey())));
        return out.toString();
    }
}
