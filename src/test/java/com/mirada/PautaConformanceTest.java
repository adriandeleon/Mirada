package com.mirada;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.mirada.PautaConformance.Checked;
import com.mirada.PautaConformance.Conformance;
import com.mirada.PautaConformance.Entry;
import com.mirada.PautaConformance.Kind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pauta's Conformance page: the helper computes Complete, Core or Partial (PAUTA-02); {@code
 * pauta.json} agrees with the edition's {@code rules.json}, copied to {@code
 * src/test/resources/pauta-rules.json} (PAUTA-06, PAUTA-07, PAUTA-09, PAUTA-10), its {@code
 * checked} list included (PAUTA-18); what a template would have left fails the release build
 * (PAUTA-16); and the README's badge and line name the edition and the conformance the helper
 * returns (PAUTA-15). The app template's test, read with Mirada's own JSON reader.
 */
class PautaConformanceTest {

    /** Set by the {@code dist} profile: what a template left fails a release build. */
    private static final boolean RELEASE_BUILD = Boolean.getBoolean("pauta.release");

    private static Entry entry(String rule, String level, Kind kind) {
        String link = kind == Kind.NOT_APPLICABLE || kind == Kind.APP_ONLY ? null : "https://github.com/x/y/issues/1";
        return new Entry(rule, level, "https://pauta-project.dev/6.1.0/x/#" + rule.toLowerCase(), kind, "R.", link);
    }

    // ---- the helper (PAUTA-02) ----

    @Test
    void noExceptionsIsComplete() {
        assertEquals(Conformance.COMPLETE, PautaConformance.of(List.of()));
    }

    @Test
    void aPendingMustIsPartialAndAPendingShouldIsCore() {
        assertEquals(Conformance.PARTIAL, PautaConformance.of(List.of(entry("A-01", "MUST", Kind.PENDING))));
        assertEquals(Conformance.CORE, PautaConformance.of(List.of(entry("A-02", "SHOULD", Kind.PENDING))));
        assertEquals(
                Conformance.PARTIAL,
                PautaConformance.of(
                        List.of(entry("A-02", "SHOULD", Kind.PENDING), entry("A-01", "MUST", Kind.PENDING))));
    }

    @Test
    void deviationsAppOnlyAndNotApplicableEntriesAndMaysLeaveTheConformanceAlone() {
        List<Entry> none = List.of(
                entry("A-01", "MUST", Kind.NOT_APPLICABLE),
                entry("A-02", "SHOULD", Kind.NOT_APPLICABLE),
                entry("A-03", "SHOULD", Kind.DEVIATION),
                entry("A-04", "MAY", Kind.PENDING),
                entry("A-07", "MUST", Kind.APP_ONLY),
                entry("A-08", "SHOULD", Kind.APP_ONLY));
        assertEquals(Conformance.COMPLETE, PautaConformance.of(none));
        List<Entry> core = new ArrayList<>(none);
        core.add(entry("A-05", "SHOULD", Kind.PENDING));
        assertEquals(Conformance.CORE, PautaConformance.of(core));
    }

    @Test
    void theNamesAreTheWordsTheReadmeAndTheEnvironmentWrite() {
        assertEquals("Complete", Conformance.COMPLETE.english());
        assertEquals("Core", Conformance.CORE.english());
        assertEquals("partial", Conformance.PARTIAL.key());
        assertEquals("notApplicable", Kind.NOT_APPLICABLE.key());
        assertEquals("not-applicable", Kind.NOT_APPLICABLE.json());
        assertEquals("appOnly", Kind.APP_ONLY.key());
        assertEquals("app-only", Kind.APP_ONLY.json());
        assertThrows(IllegalArgumentException.class, () -> Kind.parse("waived"));
    }

    @Test
    void pendingFirstThenDeviationsThenAppOnlyThenNotApplicableMustsBeforeShouldsThenById() {
        List<Entry> sorted = PautaConformance.sorted(List.of(
                entry("Z-01", "MUST", Kind.NOT_APPLICABLE),
                entry("Y-07", "SHOULD", Kind.APP_ONLY),
                entry("Y-08", "MUST", Kind.APP_ONLY),
                entry("B-02", "SHOULD", Kind.DEVIATION),
                entry("C-03", "SHOULD", Kind.PENDING),
                entry("D-04", "MUST", Kind.PENDING),
                entry("A-05", "MUST", Kind.PENDING),
                entry("A-06", "MAY", Kind.PENDING)));
        assertEquals(
                List.of("A-05", "D-04", "C-03", "A-06", "B-02", "Y-08", "Y-07", "Z-01"),
                sorted.stream().map(Entry::rule).toList());
    }

    @Test
    void countsAndTheEnvironmentLine() {
        List<Entry> list = List.of(
                entry("A-01", "MUST", Kind.PENDING),
                entry("A-02", "SHOULD", Kind.PENDING),
                entry("A-03", "SHOULD", Kind.DEVIATION));
        assertEquals(Map.of(Kind.PENDING, 2, Kind.DEVIATION, 1), PautaConformance.counts(list));
        assertEquals(1, PautaConformance.pending(list, "MUST"));
        assertEquals(1, PautaConformance.pending(list, "SHOULD"));
        assertEquals("Pauta conformance: Partial (3 exceptions)", PautaConformance.environmentLine(list));
        assertEquals(
                "Pauta conformance: Core (1 exception)",
                PautaConformance.environmentLine(List.of(entry("A-02", "SHOULD", Kind.PENDING))));
        assertEquals("Pauta conformance: Complete (no exceptions)", PautaConformance.environmentLine(List.of()));
    }

    @Test
    void issueAndDecisionNumbers() {
        assertEquals("#212", PautaConformance.issueNumber("https://github.com/a/b/issues/212"));
        assertNull(PautaConformance.issueNumber("https://github.com/a/b/blob/main/docs/decisions/0007-x.md"));
        assertEquals(
                "0007", PautaConformance.decisionNumber("https://github.com/a/b/blob/main/docs/decisions/0007-x.md"));
        assertNull(PautaConformance.decisionNumber("https://github.com/a/b/issues/212"));
        assertNull(PautaConformance.decisionNumber("https://github.com/a/b/blob/main/docs/decisions/README.md"));
    }

    @Test
    void parsesEveryField() {
        List<Entry> parsed = PautaConformance.parse(PautaConformance.read("""
                {"exceptions": [
                  {"rule": "A-01", "level": "MUST", "url": "u", "kind": "pending", "reason": "R.", "link": "l"},
                  {"rule": "A-02", "level": "SHOULD", "url": "v", "kind": "not-applicable", "reason": "S."}
                ]}"""));
        assertEquals(new Entry("A-01", "MUST", "u", Kind.PENDING, "R.", "l"), parsed.get(0));
        assertNull(parsed.get(1).link());
        assertEquals(Kind.NOT_APPLICABLE, parsed.get(1).kind());
        assertTrue(PautaConformance.parse(PautaConformance.read("{}")).isEmpty());
        assertThrows(IllegalStateException.class, () -> PautaConformance.read("[]"));
        assertThrows(IllegalStateException.class, () -> PautaConformance.read("{"));
    }

    @Test
    void readsTheTemplateFlagAndTheCheckedList() {
        Map<?, ?> root = PautaConformance.read("""
                {"template": true, "exceptions": [], "checked": [
                  {"rule": "SET-02", "edition": "6.1.0", "date": "2026-11-10"},
                  {"rule": "ACC-07", "edition": "6.1.0", "date": "2026-11-10", "version": "1.2.0"}
                ]}""");
        assertTrue(PautaConformance.isTemplate(root));
        assertFalse(PautaConformance.isTemplate(PautaConformance.read("{\"template\": false}")));
        assertFalse(PautaConformance.isTemplate(PautaConformance.read("{}")));
        assertEquals(
                List.of(
                        new Checked("SET-02", "6.1.0", "2026-11-10", null),
                        new Checked("ACC-07", "6.1.0", "2026-11-10", "1.2.0")),
                PautaConformance.parseChecked(root));
        assertTrue(PautaConformance.parseChecked(PautaConformance.read("{}")).isEmpty());
    }

    @Test
    void editionsCompareByNumberNotByText() {
        assertTrue(compare("4.10.0", "4.9.0") > 0);
        assertTrue(compare("5.0.0", "6.0.0") < 0);
        assertEquals(0, compare("6.0.0", "6.0.0"));
    }

    // ---- pauta.json against the edition (PAUTA-09, PAUTA-10) ----

    private static String read(String file) throws Exception {
        // A Windows checkout may end lines in \r\n.
        return Files.readString(Path.of(file), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private static String pomEdition() throws Exception {
        Matcher m =
                Pattern.compile("<pauta\\.version>([^<]+)</pauta\\.version>").matcher(read("pom.xml"));
        assertTrue(m.find(), "pom.xml defines pauta.version");
        return m.group(1).strip();
    }

    private static Map<?, ?> edition() throws Exception {
        return PautaConformance.read(read("src/test/resources/pauta-rules.json"));
    }

    private static Map<?, ?> file() throws Exception {
        return PautaConformance.read(read("src/main/resources/com/mirada/pauta.json"));
    }

    private static List<Map<?, ?>> list(Map<?, ?> root, String field) {
        List<Map<?, ?>> found = new ArrayList<>();
        if (root.get(field) instanceof List<?> items) {
            for (Object item : items) if (item instanceof Map<?, ?> map) found.add(map);
        }
        return found;
    }

    private static String text(Map<?, ?> node, String field) {
        return PautaConformance.text(node, field);
    }

    /** The edition's rules by ID. */
    private static Map<String, Map<?, ?>> rules() throws Exception {
        Map<String, Map<?, ?>> rules = new HashMap<>();
        for (Map<?, ?> rule : list(edition(), "rules")) rules.put(text(rule, "id"), rule);
        return rules;
    }

    @Test
    void theRulesCopyIsTheEditionThePomNames() throws Exception {
        assertEquals(pomEdition(), text(edition(), "edition"), "replace pauta-rules.json with this edition's");
        assertTrue(list(edition(), "rules").size() > 100);
    }

    @Test
    void theBundledFileIsTheOneTheAppReads() throws Exception {
        assertEquals(PautaConformance.parse(file()), PautaConformance.exceptions());
        assertFalse(PautaConformance.isTemplate(), "Mirada was not made from a template");
    }

    @Test
    void everyEntryNamesARuleOfTheEditionWithItsLevelAndAddress() throws Exception {
        Map<String, Map<?, ?>> rules = rules();
        Set<String> seen = new HashSet<>();
        for (Map<?, ?> node : list(file(), "exceptions")) {
            String id = text(node, "rule");
            for (String field : List.of("rule", "level", "url", "kind", "reason")) {
                assertFalse(text(node, field).isBlank(), id + " has no " + field);
            }
            Map<?, ?> rule = rules.get(id);
            assertNotNull(rule, id + " is not a rule of Pauta " + pomEdition());
            assertTrue(seen.add(id), id + " appears twice");
            assertEquals(text(rule, "level"), text(node, "level"), id + "'s level");
            assertEquals(text(rule, "url"), text(node, "url"), id + "'s url");
        }
    }

    @Test
    void eachKindHasWhatItNeeds() throws Exception {
        String repo = Pattern.quote(AppInfo.GITHUB_REPO);
        Pattern issue = Pattern.compile("https://github\\.com/" + repo + "/issues/\\d+");
        Pattern decision = Pattern.compile("https://github\\.com/" + repo + "/blob/main/(docs/decisions/[^/]+\\.md)");
        for (Entry e : PautaConformance.parse(file())) {
            String link = e.link();
            switch (e.kind()) {
                case NOT_APPLICABLE -> assertNull(link, e.rule() + ": a not-applicable entry has no link");
                // Outside a template an app-only entry is inherited: theAppListsWhatItInherited.
                case APP_ONLY -> assertNull(link, e.rule() + ": an app-only entry has no link");
                case PENDING -> {
                    assertNotNull(link, e.rule() + ": a pending entry links its issue (PAUTA-07)");
                    assertTrue(issue.matcher(link).matches(), e.rule() + ": not an issue in this repository: " + link);
                }
                case DEVIATION -> {
                    assertEquals("SHOULD", e.level(), e.rule() + ": only a SHOULD can be a deviation (PAUTA-06)");
                    assertNotNull(link, e.rule() + ": a deviation links its decision record");
                    Matcher m = decision.matcher(link);
                    assertTrue(m.matches(), e.rule() + ": not a decision record: " + link);
                    Path record = Path.of(m.group(1));
                    assertTrue(Files.exists(record), record + " is missing");
                    assertTrue(read(record.toString()).contains(e.rule()), record + " does not name " + e.rule());
                }
            }
            // PAUTA-08: one plain sentence for the user, without rule text or IDs.
            assertTrue(e.reason().endsWith("."), e.rule() + ": the reason is a sentence");
            assertFalse(e.reason().matches(".*\\b[A-Z]{2,6}-\\d{2,3}\\b.*"), e.rule() + ": the reason names a rule");
            assertFalse(
                    e.reason().substring(0, e.reason().length() - 1).matches(".*[.!?] .*"),
                    e.rule() + ": the reason is one sentence");
        }
    }

    /**
     * PAUTA-16: outside a template, each app-only entry and each pending link to another repository
     * is what a template left behind. The everyday build lists them; the release build fails on them.
     * Mirada was made from no template, so there should never be one.
     */
    @Test
    void theAppListsWhatItInherited() throws Exception {
        if (PautaConformance.isTemplate(file())) return;
        String own = "https://github.com/" + AppInfo.GITHUB_REPO + "/issues/";
        List<String> inherited = new ArrayList<>();
        for (Entry e : PautaConformance.parse(file())) {
            String link = e.link();
            if (e.kind() == Kind.APP_ONLY) inherited.add(e.rule() + ": app-only, which only a template may record");
            else if (e.kind() == Kind.PENDING && link != null && !link.startsWith(own))
                inherited.add(e.rule() + ": pending on " + link + ", not an issue of " + AppInfo.GITHUB_REPO);
        }
        if (inherited.isEmpty()) return;
        String list = String.join("\n  ", inherited);
        if (RELEASE_BUILD) {
            throw new AssertionError("Meet each one, or record it as pending with an issue in this app's"
                    + " own repository, before a release (PAUTA-16):\n  " + list);
        }
        System.err.println("[pauta] Not this repository's; the release build fails on these (PAUTA-16):\n  " + list);
    }

    // ---- the checked list (PAUTA-18) ----

    @Test
    void eachCheckedEntryIsWellFormedAndCurrent() throws Exception {
        Map<String, Map<?, ?>> rules = rules();
        Set<String> excepted = new HashSet<>();
        for (Entry e : PautaConformance.parse(file())) excepted.add(e.rule());
        String pom = pomEdition();
        Set<String> seen = new HashSet<>();
        for (Map<?, ?> node : list(file(), "checked")) {
            String id = text(node, "rule");
            for (String field : List.of("rule", "edition", "date")) {
                assertFalse(text(node, field).isBlank(), "checked " + id + " has no " + field);
            }
            assertTrue(text(node, "date").matches("\\d{4}-\\d{2}-\\d{2}"), "checked " + id + "'s date");
            Map<?, ?> rule = rules.get(id);
            assertNotNull(rule, "checked " + id + " is not a rule of Pauta " + pom);
            String kind = rule.get("check") instanceof Map<?, ?> check ? text(check, "kind") : "";
            assertEquals("by hand", kind, "checked " + id + ": only a rule checked by hand is recorded");
            assertTrue(seen.add(id), "checked " + id + " appears twice");
            assertFalse(excepted.contains(id), id + " is both checked and an exception");
            String edition = text(node, "edition");
            assertTrue(compare(edition, pom) <= 0, "checked " + id + ": " + edition + " is newer than " + pom);
            String changed = text(rule, "changed").isEmpty() ? pom : text(rule, "changed");
            assertTrue(
                    compare(edition, changed) >= 0,
                    "checked " + id + " is stale: checked against " + edition + ", the rule changed in " + changed
                            + "; remove it and check the rule again");
        }
    }

    /** Compares two editions number by number: 4.10.0 is newer than 4.9.0. */
    private static int compare(String a, String b) {
        String[] x = a.split("\\.");
        String[] y = b.split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? Integer.parseInt(x[i]) : 0;
            int q = i < y.length ? Integer.parseInt(y[i]) : 0;
            if (p != q) return Integer.compare(p, q);
        }
        return 0;
    }

    // ---- the README (PAUTA-15) ----

    @Test
    void theReadmeNamesTheEditionAndTheConformanceAndLinksTheDocsPage() throws Exception {
        String edition = pomEdition();
        Conformance conformance = PautaConformance.conformance();
        String readme = read("README.md");
        String docs = AppInfo.pautaDocsUrl();

        Matcher badge = Pattern.compile(
                        "\\[!\\[Pauta]\\(https://img\\.shields\\.io/badge/Pauta-([0-9.]+)%20%C2%B7%20(\\w+)-(\\w+)\\)]\\(([^)]+)\\)")
                .matcher(readme);
        assertTrue(badge.find(), "README has the Pauta badge");
        assertEquals(edition, badge.group(1), "the badge's edition");
        assertEquals(conformance.english(), badge.group(2), "the badge's conformance");
        // Green for Complete, blue for Core, amber for Partial (Pauta's README badges).
        String color =
                switch (conformance) {
                    case COMPLETE -> "2ea043";
                    case CORE -> "5b8def";
                    case PARTIAL -> "d29922";
                };
        assertEquals(color, badge.group(3), "the badge's color");
        assertEquals(docs, badge.group(4), "the badge links the conformance page");

        String opening = conformance == Conformance.PARTIAL ? "Working toward" : "Built to";
        String other = conformance == Conformance.PARTIAL ? "Built to [Pauta" : "Working toward [Pauta";
        String line = opening + " [Pauta (the adl desktop apps guidelines)](https://pauta-project.dev/), edition ["
                + edition + "](https://pauta-project.dev/" + edition + "/), [Pauta conformance: "
                + conformance.english() + "](" + docs + ").";
        assertTrue(readme.contains(line), "README's line: " + line);
        assertFalse(readme.contains(other), "README still says " + other);
    }
}
