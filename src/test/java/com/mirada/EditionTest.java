package com.mirada;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Pauta edition has one source, {@code pauta.version} in {@code pom.xml} (ABOUT-08): the build
 * filters it into {@link AppInfo}, and the README's badge and "Built to" (or "Working toward") line
 * and {@code AGENTS.md} name the same number, so moving to a new edition changes them together or
 * fails here (PAUTA-11, PAUTA-17).
 */
class EditionTest {

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

    @Test
    void appInfoReadsTheEditionFromThePom() throws Exception {
        assertEquals(pomEdition(), AppInfo.PAUTA_EDITION);
    }

    @Test
    void theEditionsPageIsNumberedNeverLatest() throws Exception {
        assertEquals("https://pauta-project.dev/" + pomEdition() + "/", AppInfo.pautaUrl());
    }

    @Test
    void theVersionTextNamesTheEditionRightAfterTheCommitThenTheConformance() {
        String[] lines = AppInfo.versionText().split("\n");
        assertTrue(lines[1].startsWith("Commit: "), lines[1]);
        assertEquals("Pauta: " + AppInfo.PAUTA_EDITION, lines[2]);
        assertEquals(PautaConformance.environmentLine(PautaConformance.exceptions()), lines[3]);
    }

    @Test
    void theReadmeAndAgentsNameTheSameEdition() throws Exception {
        String edition = pomEdition();
        String page = "https://pauta-project.dev/" + edition + "/";
        String readme = read("README.md");
        // The badge and the line also name the conformance: PautaConformanceTest holds them.
        assertTrue(readme.contains("badge/Pauta-" + edition + "%20"), "README badge");
        assertTrue(readme.contains("edition [" + edition + "](" + page + ")"), "README names " + edition);
        assertTrue(read("AGENTS.md").contains("edition [" + edition + "](" + page + ")"), "AGENTS.md");
        Matcher other = Pattern.compile("Pauta[- ](\\d+\\.\\d+\\.\\d+)").matcher(readme + read("AGENTS.md"));
        while (other.find()) assertEquals(edition, other.group(1), "names another edition");
    }

    /** PAUTA-17: the agent reads the edition's own rules at its numbered address, never latest/. */
    @Test
    void agentsLinksTheEditionsRulesForCodingAgents() throws Exception {
        String edition = pomEdition();
        String agents = read("AGENTS.md");
        assertTrue(agents.contains("\n## Pauta\n"), "AGENTS.md has a Pauta section");
        String base = "https://pauta-project.dev/" + edition + "/";
        assertTrue(agents.contains(base + "llms.txt"), "AGENTS.md links " + base + "llms.txt");
        assertTrue(agents.contains(base + "llms-full.txt"), "AGENTS.md links " + base + "llms-full.txt");
        assertFalse(agents.contains("pauta-project.dev/latest/"), "AGENTS.md links latest/");
        Matcher other =
                Pattern.compile("pauta-project\\.dev/(\\d+\\.\\d+\\.\\d+)/").matcher(agents);
        while (other.find()) assertEquals(edition, other.group(1), "AGENTS.md links another edition");
    }
}
