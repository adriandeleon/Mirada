package com.mirada.ui;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.mirada.AppInfo;
import com.mirada.PautaConformance.Conformance;
import com.mirada.PautaConformance.Entry;
import com.mirada.PautaConformance.Kind;
import com.mirada.update.UpdateCoordinator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the Pauta window and About say about the conformance, without a window: the sentence under
 * the pill, the counts, Copy as text (PWIN-02, PWIN-06, PWIN-08), the edition's link at Partial
 * (ABOUT-09) and the update line's state colour (ABOUT-10).
 */
class PautaWindowTest {

    private static final Entry MUST = new Entry(
            "A-01",
            "MUST",
            "https://pauta-project.dev/6.1.0/a/#a-01",
            Kind.PENDING,
            "One.",
            "https://github.com/x/y/issues/7");
    private static final Entry SHOULD = new Entry(
            "B-02",
            "SHOULD",
            "https://pauta-project.dev/6.1.0/b/#b-02",
            Kind.DEVIATION,
            "Two.",
            "https://github.com/x/y/blob/main/docs/decisions/0003-two.md");

    @Test
    void theMeaningCountsWhatIsPending() {
        assertEquals(
                "Mirada does not meet every MUST yet: 1 is pending.",
                PautaWindow.meaning(Conformance.PARTIAL, List.of(MUST, SHOULD)));
        assertEquals(
                "Mirada meets every MUST and SHOULD of this edition.",
                PautaWindow.meaning(Conformance.COMPLETE, List.of()));
        assertEquals("1 pending · 1 deviation", PautaWindow.counts(List.of(SHOULD, MUST)));
        assertEquals("", PautaWindow.counts(List.of()));
    }

    @Test
    void copyAsTextIsMarkdownOneLinePerExceptionPendingFirst() {
        String text = PautaWindow.markdown(List.of(SHOULD, MUST));
        assertTrue(text.startsWith("# Pauta " + AppInfo.PAUTA_EDITION + ": Partial\n"), text);
        int pending = text.indexOf("- [A-01](https://pauta-project.dev/6.1.0/a/#a-01) MUST: One. [#7]");
        int deviation = text.indexOf("- [B-02](https://pauta-project.dev/6.1.0/b/#b-02) SHOULD: Two. [Decision 0003]");
        assertTrue(pending > 0 && deviation > pending, text);
        assertTrue(text.endsWith(AppInfo.pautaUrl() + "\n"), text);
        assertTrue(PautaWindow.markdown(List.of()).contains(PautaWindow.none()));
    }

    @Test
    void atPartialAboutSaysWorkingTowardAndOtherwiseBuiltTo() {
        assertEquals("Working toward Pauta 6.1.0", AboutWindow.pautaLink(Conformance.PARTIAL, "6.1.0"));
        assertEquals("Built to Pauta 6.1.0", AboutWindow.pautaLink(Conformance.CORE, "6.1.0"));
        assertEquals("Pauta conformance: Core", AboutWindow.conformanceLink(Conformance.CORE));
    }

    @Test
    void theUpdateLineTakesItsStatesColourAndStaysMutedBeforeAnAnswer() {
        assertEquals("update-available", AboutWindow.updateStyle(UpdateCoordinator.State.AVAILABLE));
        assertEquals("up-to-date", AboutWindow.updateStyle(UpdateCoordinator.State.UP_TO_DATE));
        assertEquals("update-failed", AboutWindow.updateStyle(UpdateCoordinator.State.FAILED));
        assertNull(AboutWindow.updateStyle(UpdateCoordinator.State.NOT_CHECKED));
        assertNull(AboutWindow.updateStyle(UpdateCoordinator.State.CHECKING));
    }

    /** ABOUT-10: amber is the available update's alone, bold; the links keep the accent. */
    @Test
    void amberIsTheAvailableUpdatesAlone() throws Exception {
        String css =
                Files.readString(Path.of("src/main/resources/com/mirada/styles/mirada.css"), StandardCharsets.UTF_8);
        Matcher rule = Pattern.compile("([^{}]+)\\{([^}]*)}").matcher(css);
        int amber = 0;
        while (rule.find()) {
            String selector = rule.group(1).strip();
            String body = rule.group(2);
            // In About and the Pauta window: a warning elsewhere is amber too (ICON-07).
            boolean aboutOrPauta = selector.contains(".about") || selector.contains(".pauta");
            if (selector.endsWith(".root") || !aboutOrPauta || !body.matches("(?s).*-state-amber\\b(?!-).*")) continue;
            amber++;
            assertTrue(selector.endsWith(".about-update.update-available"), selector);
            assertTrue(body.contains("-fx-font-weight: bold"), selector);
        }
        assertEquals(1, amber);
        assertTrue(css.contains(".about .hyperlink,\n.pauta .hyperlink {\n    -fx-text-fill: -state-accent;"));
    }
}
