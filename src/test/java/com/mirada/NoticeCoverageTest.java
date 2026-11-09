package com.mirada;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * NOTICE keeps up with the pom (REPO-04): a dependency is added in one file and attributed in the
 * other, with no compiler between them, so this test is the link. Every dependency that ships (the
 * main build's and the profiles', anything not {@code test} or {@code provided}) is named in NOTICE
 * by its {@code groupId:artifactId}, beside its licence.
 */
class NoticeCoverageTest {

    private static final Path REPO = Path.of(System.getProperty("user.dir"));

    /** {@code groupId:artifactId} of every dependency that ships, the profiles' included. */
    static List<String> shippedDependencies() throws Exception {
        Document pom = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(REPO.resolve("pom.xml").toFile());
        List<String> shipped = new ArrayList<>();
        NodeList all = pom.getElementsByTagName("dependency");
        for (int i = 0; i < all.getLength(); i++) {
            Element dependency = (Element) all.item(i);
            Node owner = dependency.getParentNode().getParentNode();
            // <project><dependencies> or <profile><dependencies>; a plugin's own do not ship.
            if (!"project".equals(owner.getNodeName()) && !"profile".equals(owner.getNodeName())) continue;
            String scope = child(dependency, "scope");
            if ("test".equals(scope) || "provided".equals(scope)) continue;
            shipped.add(child(dependency, "groupId") + ":" + child(dependency, "artifactId"));
        }
        return shipped;
    }

    private static String child(Element parent, String name) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (name.equals(children.item(i).getNodeName()))
                return children.item(i).getTextContent().trim();
        }
        return "";
    }

    @Test
    void everyDependencyThatShipsIsNamedInNotice() throws Exception {
        String notice = Files.readString(REPO.resolve("NOTICE"));
        List<String> shipped = shippedDependencies();
        assertTrue(shipped.size() >= 7, "the pom parse found suspiciously few dependencies: " + shipped);
        List<String> missing = new ArrayList<>();
        for (String coordinates : shipped) {
            if (!notice.contains(coordinates)) missing.add(coordinates);
        }
        assertEquals(
                List.of(),
                missing,
                "declared in pom.xml but not named in NOTICE by groupId:artifactId; add an entry (name, URL,"
                        + " copyright, licence) for each");
    }

    @Test
    void testAndCompileOnlyDependenciesAreNotCounted() throws Exception {
        List<String> shipped = shippedDependencies();
        assertTrue(shipped.contains("org.openjfx:javafx-controls"));
        assertTrue(shipped.contains("us.hebi.graalvm:jfx-static-libs"), "a profile's dependencies ship too");
        for (String never :
                List.of("org.jspecify:jspecify", "org.junit.jupiter:junit-jupiter", "org.testfx:testfx-core")) {
            assertTrue(!shipped.contains(never), never + " does not ship");
        }
    }

    @Test
    void noticeStartsWithTheAppItsCopyrightAndItsLicence() throws Exception {
        List<String> lines = Files.readAllLines(REPO.resolve("NOTICE"));
        assertEquals(AppInfo.NAME, lines.get(0));
        assertTrue(lines.get(1).startsWith("Copyright"), lines.get(1));
        assertTrue(String.join("\n", lines).contains("licensed under the MIT License"));
    }
}
