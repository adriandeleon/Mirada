package com.mirada;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.mirada.update.Json;
import org.jspecify.annotations.Nullable;

/**
 * How fully Mirada conforms to its Pauta edition (PAUTA-02): computed from the exceptions in {@code
 * pauta.json}, never stored. About, the Pauta window and {@code --version} take it from here; the
 * README is held to it by {@code PautaConformanceTest}. The app template's, read with Mirada's own
 * {@link Json} instead of a JSON library.
 *
 * <p>Partial when a MUST is pending, Core when a SHOULD is pending, Complete otherwise. A deviation,
 * an app-only or a not-applicable entry leaves the result alone, and so does a MAY: MAYs never
 * count. The {@code checked} list (PAUTA-18) is a record and never enters it.
 */
public final class PautaConformance {

    /** The bundled exceptions, next to {@code build-info.properties}. */
    static final String RESOURCE = "/com/mirada/pauta.json";

    /** Complete, Core or Partial. Their names live in the catalogs ({@code pauta.conformance.<key>}). */
    public enum Conformance {
        COMPLETE,
        CORE,
        PARTIAL;

        /** The catalog key part and the README word's lower case: {@code complete}, {@code core}, {@code partial}. */
        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** The English word, as the README badge and the environment block write it. */
        public String english() {
            return name().charAt(0) + key().substring(1);
        }
    }

    /**
     * The four kinds of exception, in the order the Pauta window lists them (PWIN-05). {@code
     * app-only} is a template's alone (PAUTA-16): a rule only an app made from it can meet.
     */
    public enum Kind {
        PENDING("pending", "pending"),
        DEVIATION("deviation", "deviation"),
        APP_ONLY("app-only", "appOnly"),
        NOT_APPLICABLE("not-applicable", "notApplicable");

        private final String json;
        private final String key;

        Kind(String json, String key) {
            this.json = json;
            this.key = key;
        }

        /** How {@code pauta.json} spells it. */
        public String json() {
            return json;
        }

        /** The catalog key part: {@code pauta.kind.<key>}. */
        public String key() {
            return key;
        }

        static Kind parse(String value) {
            for (Kind kind : values()) if (kind.json.equals(value)) return kind;
            throw new IllegalArgumentException("Unknown exception kind: " + value);
        }
    }

    /**
     * One rule Mirada does not meet as written: the rule's ID, and its level and URL copied from the
     * edition's {@code rules.json}; the kind; one plain sentence for the user; for a deviation its
     * decision record and for a pending entry its issue.
     */
    public record Entry(
            String rule,
            String level,
            String url,
            Kind kind,
            String reason,
            @Nullable String link) {

        boolean isMust() {
            return "MUST".equals(level);
        }

        boolean isShould() {
            return "SHOULD".equals(level);
        }
    }

    /**
     * One rule a person checked by hand in the running app and found met (PAUTA-18): the rule, the
     * edition it was checked against, the date and, for a rule checked again with each release, the
     * app's version. A record only: it never changes the conformance.
     */
    public record Checked(
            String rule,
            String edition,
            String date,
            @Nullable String version) {}

    private static @Nullable Map<?, ?> bundled;

    private static volatile @Nullable List<Entry> exceptions;

    private PautaConformance() {}

    /** The bundled {@code pauta.json}, read once. */
    private static synchronized Map<?, ?> file() {
        Map<?, ?> root = bundled;
        if (root == null) {
            try (InputStream in = PautaConformance.class.getResourceAsStream(RESOURCE)) {
                if (in == null) throw new IllegalStateException(RESOURCE + " is not bundled");
                root = read(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            bundled = root;
        }
        return root;
    }

    /** {@code text} as a JSON object; anything else is a broken build, not a user's mistake. */
    static Map<?, ?> read(String text) {
        try {
            if (Json.parse(text) instanceof Map<?, ?> root) return root;
            throw new IllegalStateException("pauta.json is not an object");
        } catch (Json.Malformed e) {
            throw new IllegalStateException("pauta.json does not parse: " + e.getMessage(), e);
        }
    }

    /**
     * Mirada's own exceptions, from the bundled {@code pauta.json}: read and parsed once, off the FX
     * thread after the first paint, so About and the Pauta window parse nothing (PERF-01).
     */
    public static List<Entry> exceptions() {
        List<Entry> parsed = exceptions;
        if (parsed == null) {
            parsed = parse(file());
            exceptions = parsed;
        }
        return parsed;
    }

    /** The by-hand checks Mirada records (PAUTA-18); empty when it records none. */
    public static List<Checked> checked() {
        return parseChecked(file());
    }

    /** Whether this is a template repository, which new apps are made from (PAUTA-16). */
    public static boolean isTemplate() {
        return isTemplate(file());
    }

    /** Mirada's own conformance. */
    public static Conformance conformance() {
        return of(exceptions());
    }

    /** Reads {@code pauta.json}'s {@code exceptions} array. */
    static List<Entry> parse(Map<?, ?> root) {
        List<Entry> entries = new ArrayList<>();
        for (Map<?, ?> node : objects(root.get("exceptions"))) {
            entries.add(new Entry(
                    text(node, "rule"),
                    text(node, "level"),
                    text(node, "url"),
                    Kind.parse(text(node, "kind")),
                    text(node, "reason"),
                    node.get("link") instanceof String link ? link : null));
        }
        return List.copyOf(entries);
    }

    /** Reads {@code pauta.json}'s {@code checked} array; a file without one records nothing. */
    static List<Checked> parseChecked(Map<?, ?> root) {
        List<Checked> checked = new ArrayList<>();
        for (Map<?, ?> node : objects(root.get("checked"))) {
            checked.add(new Checked(
                    text(node, "rule"),
                    text(node, "edition"),
                    text(node, "date"),
                    node.get("version") instanceof String version ? version : null));
        }
        return List.copyOf(checked);
    }

    /** {@code "template": true}; an app made from a template has {@code false}, and Mirada none. */
    static boolean isTemplate(Map<?, ?> root) {
        return Boolean.TRUE.equals(root.get("template"));
    }

    /** The objects of a JSON array, or none when there is no array. */
    private static List<Map<?, ?>> objects(@Nullable Object array) {
        List<Map<?, ?>> found = new ArrayList<>();
        if (array instanceof List<?> list) {
            for (Object item : list) if (item instanceof Map<?, ?> map) found.add(map);
        }
        return found;
    }

    /** A string field, or empty when it is missing or not a string. */
    static String text(Map<?, ?> node, String field) {
        return node.get(field) instanceof String value ? value : "";
    }

    /** Partial when any MUST is pending, Core when any SHOULD is pending, Complete otherwise. */
    public static Conformance of(List<Entry> exceptions) {
        boolean should = false;
        for (Entry entry : exceptions) {
            if (entry.kind() != Kind.PENDING) continue;
            if (entry.isMust()) return Conformance.PARTIAL;
            if (entry.isShould()) should = true;
        }
        return should ? Conformance.CORE : Conformance.COMPLETE;
    }

    /** How many pending exceptions have this level: the number Core's and Partial's sentences give. */
    public static long pending(List<Entry> exceptions, String level) {
        return exceptions.stream()
                .filter(e -> e.kind() == Kind.PENDING && e.level().equals(level))
                .count();
    }

    /** How many exceptions of each kind, in list order; a kind with none is left out. */
    public static Map<Kind, Integer> counts(List<Entry> exceptions) {
        Map<Kind, Integer> counts = new EnumMap<>(Kind.class);
        for (Entry entry : exceptions) counts.merge(entry.kind(), 1, Integer::sum);
        return counts;
    }

    /**
     * Pending first, then deviations, then app-only, then not applicable; within a kind MUSTs before
     * SHOULDs, then by ID (PWIN-05).
     */
    public static List<Entry> sorted(List<Entry> exceptions) {
        return exceptions.stream()
                .sorted(Comparator.comparing(Entry::kind)
                        .thenComparingInt(PautaConformance::levelRank)
                        .thenComparing(Entry::rule))
                .toList();
    }

    private static int levelRank(Entry entry) {
        return switch (entry.level()) {
            case "MUST" -> 0;
            case "SHOULD" -> 1;
            default -> 2;
        };
    }

    /**
     * The environment block's line (ABOUT-09): {@code Pauta conformance: Core (2 exceptions)}. Plain
     * English on purpose, like the rest of the block: it goes into bug reports.
     */
    public static String environmentLine(List<Entry> exceptions) {
        int n = exceptions.size();
        String count = n == 0 ? "no exceptions" : n == 1 ? "1 exception" : n + " exceptions";
        return "Pauta conformance: " + of(exceptions).english() + " (" + count + ")";
    }

    /** A pending entry's issue number, {@code #212}, or {@code null} when the link is not an issue. */
    public static @Nullable String issueNumber(String link) {
        int at = link.indexOf("/issues/");
        return at < 0 ? null : "#" + link.substring(at + "/issues/".length());
    }

    /** A deviation's decision record number, {@code 0007}, or {@code null} when the link is not one. */
    public static @Nullable String decisionNumber(String link) {
        int at = link.indexOf("/docs/decisions/");
        if (at < 0) return null;
        String file = link.substring(at + "/docs/decisions/".length());
        int dash = file.indexOf('-');
        return dash > 0 ? file.substring(0, dash) : null;
    }
}
