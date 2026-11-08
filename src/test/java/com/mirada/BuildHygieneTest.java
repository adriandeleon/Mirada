package com.mirada;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The build's own promises, which nothing else checks: LICENSE and NOTICE travel with every copy
 * (REPO-04), the coverage gate runs at {@code verify} with the floors Pauta asks for (PLAT-12) and the
 * testing guide quotes them as the pom has them (QUAL-04), and the FX tests run on the Headless glass,
 * each tagged (PLAT-11). Textual on purpose: the pom and the scripts are read as they are written.
 */
class BuildHygieneTest {

    private static final Path REPO = Path.of(System.getProperty("user.dir"));

    private static String read(String path) throws IOException {
        return Files.readString(REPO.resolve(path)).replace("\r\n", "\n");
    }

    private static byte[] resource(String name) throws IOException {
        try (InputStream in = BuildHygieneTest.class.getResourceAsStream(name)) {
            assertNotNull(in, name + " is not on the classpath: it would be missing from the jar and the module");
            return in.readAllBytes();
        }
    }

    private static Document pom() throws Exception {
        return DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(REPO.resolve("pom.xml").toFile());
    }

    // ---- LICENSE and NOTICE are packaged (REPO-04) ----

    @Test
    void theJarCarriesLicenseAndNoticeAsTheRepositoryHasThem() throws IOException {
        assertArrayEquals(Files.readAllBytes(REPO.resolve("LICENSE")), resource("/META-INF/mirada/LICENSE"));
        assertArrayEquals(Files.readAllBytes(REPO.resolve("NOTICE")), resource("/META-INF/mirada/NOTICE"));
    }

    @Test
    void everyAppImageAndInstallerGetsLicenseAndNotice() throws IOException {
        String pom = read("pom.xml");
        Matcher notices = Pattern.compile("<id>add-notices</id>(.*?)</execution>", Pattern.DOTALL)
                .matcher(pom);
        assertTrue(notices.find(), "-Pdist no longer runs scripts/add-notices.sh");
        assertTrue(notices.group(1).contains("${project.basedir}/LICENSE"), "add-notices is not given LICENSE");
        assertTrue(notices.group(1).contains("${project.basedir}/NOTICE"), "add-notices is not given NOTICE");
        assertTrue(read("scripts/add-notices.sh").contains("cp \"$file\" \"$appdir/\""), "add-notices copies nothing");
        String check = read("scripts/check-linux-packages.sh");
        assertTrue(check.contains("lib/app/NOTICE\" ] || fail"), "the Linux packages are not checked for NOTICE");
        assertTrue(check.contains("lib/app/LICENSE\" ] || fail"), "the Linux packages are not checked for LICENSE");
        assertTrue(
                read("scripts/build-installers.sh").contains("--license-file \"$root/LICENSE\""),
                "the installers no longer show the licence");
    }

    // ---- The coverage gate (PLAT-12, QUAL-03, QUAL-04) ----

    /** One floor: "Bundle" or a package, the counter, the minimum. */
    record Floor(String scope, String counter, String minimum) implements Comparable<Floor> {
        @Override
        public int compareTo(Floor other) {
            return toString().compareTo(other.toString());
        }
    }

    /** The jacoco-check execution's rules, as floors. */
    private static Set<Floor> pomFloors() throws Exception {
        Element check = null;
        NodeList executions = pom().getElementsByTagName("execution");
        for (int i = 0; i < executions.getLength(); i++) {
            Element execution = (Element) executions.item(i);
            if ("jacoco-check".equals(text(execution, "id"))) check = execution;
        }
        assertNotNull(check, "no jacoco-check execution in pom.xml");
        assertEquals("verify", text(check, "phase"), "the coverage gate must run at verify");
        Set<Floor> floors = new TreeSet<>();
        NodeList rules = check.getElementsByTagName("rule");
        for (int i = 0; i < rules.getLength(); i++) {
            Element rule = (Element) rules.item(i);
            String element = text(rule, "element");
            List<String> scopes = new ArrayList<>();
            if ("BUNDLE".equals(element)) {
                scopes.add("Bundle");
            } else {
                NodeList includes = rule.getElementsByTagName("include");
                for (int j = 0; j < includes.getLength(); j++)
                    scopes.add("`" + includes.item(j).getTextContent().trim() + "`");
            }
            NodeList limits = rule.getElementsByTagName("limit");
            for (int j = 0; j < limits.getLength(); j++) {
                Element limit = (Element) limits.item(j);
                assertEquals("COVEREDRATIO", text(limit, "value"));
                for (String scope : scopes)
                    floors.add(new Floor(scope, text(limit, "counter"), text(limit, "minimum")));
            }
        }
        return floors;
    }

    private static String text(Element parent, String name) {
        NodeList found = parent.getElementsByTagName(name);
        return found.getLength() == 0 ? "" : found.item(0).getTextContent().trim();
    }

    @Test
    void theGateHoldsTheWholeBuildToNinetyPercentOfLinesAndMethods() throws Exception {
        Set<Floor> floors = pomFloors();
        assertTrue(floors.contains(new Floor("Bundle", "LINE", "0.90")), floors.toString());
        assertTrue(floors.contains(new Floor("Bundle", "METHOD", "0.90")), floors.toString());
        assertTrue(floors.stream()
                .anyMatch(f -> f.scope().equals("Bundle") && f.counter().equals("BRANCH")));
        // The agent has to reach surefire's JVM, or every floor measures nothing.
        assertTrue(read("pom.xml").contains("<argLine>@{argLine} "), "surefire's argLine drops JaCoCo's agent");
    }

    @Test
    void theCoverageFloorsQuotedInTheTestingGuideAreTheOnesTheBuildEnforces() throws Exception {
        Set<Floor> quoted = new TreeSet<>();
        Matcher row = Pattern.compile("(?m)^\\| (Bundle|`[a-z.]+`) \\| ([A-Z]+) \\| (0\\.\\d+) \\|$")
                .matcher(read("docs/testing.md"));
        while (row.find()) quoted.add(new Floor(row.group(1), row.group(2), row.group(3)));
        assertFalse(quoted.isEmpty(), "docs/testing.md has no table of coverage floors");
        assertEquals(pomFloors(), quoted, "docs/testing.md's coverage table and pom.xml's jacoco-check disagree");
    }

    // ---- The FX tests (PLAT-11) ----

    @Test
    void theFxTestsRunOnTheHeadlessGlassWithoutMonocleOrXvfb() throws IOException {
        String pom = read("pom.xml");
        assertTrue(pom.contains("<glass.platform>Headless</glass.platform>"), "surefire must set glass.platform");
        assertTrue(pom.contains("<artifactId>testfx-core</artifactId>"));
        assertFalse(pom.contains("monocle"), "no Monocle: JavaFX's own Headless glass replaces it");
        // xvfb is there for the packaged app's smoke tests, which start the real GTK window; never
        // for Maven, whose FX tests need no display.
        try (Stream<Path> workflows = Files.list(REPO.resolve(".github/workflows"))) {
            for (Path workflow : workflows.toList()) {
                for (String line : Files.readAllLines(workflow)) {
                    assertFalse(
                            line.contains("xvfb-run") && line.contains("mvnw"),
                            workflow.getFileName() + " runs Maven under xvfb: " + line.strip());
                }
            }
        }
    }

    @Test
    void everyTestThatStartsTheToolkitIsTaggedFx() throws IOException {
        List<String> untagged = new ArrayList<>();
        try (Stream<Path> sources = Files.walk(REPO.resolve("src/test/java"))) {
            for (Path source : sources.filter(
                            p -> p.toString().endsWith("Test.java") && !p.endsWith("BuildHygieneTest.java"))
                    .toList()) {
                String text = Files.readString(source);
                boolean startsFx = text.contains("FxTestSupport.start()") || text.contains("WindowFixture.open(");
                if (startsFx && !text.contains("@Tag(\"fx\")"))
                    untagged.add(source.getFileName().toString());
            }
        }
        assertEquals(List.of(), untagged, "these start JavaFX but are not @Tag(\"fx\")");
    }
}
