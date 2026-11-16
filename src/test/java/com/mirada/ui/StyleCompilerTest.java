package com.mirada.ui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javafx.css.Stylesheet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PERF-17: every stylesheet Mirada ships precompiles to a .bss that loads with the same rules. */
class StyleCompilerTest {

    @TempDir
    Path dir;

    @Test
    void everyStylesheetBecomesABinaryBesideIt() throws IOException {
        Path styles = Path.of("src/main/resources/com/mirada/styles");
        List<String> sheets;
        try (var files = Files.list(styles)) {
            sheets = files.map(f -> f.getFileName().toString())
                    .filter(n -> n.endsWith(".css"))
                    .sorted()
                    .toList();
        }
        assertTrue(sheets.containsAll(List.of("high-contrast.css", "mirada.css", "ui-font.css")), sheets.toString());
        for (String name : sheets) Files.copy(styles.resolve(name), dir.resolve(name));
        Files.writeString(dir.resolve("notes.txt"), "not a stylesheet");

        StyleCompiler.main(new String[] {dir.toString()});

        for (String name : sheets) {
            Path bss = dir.resolve(StyleCompiler.binaryName(name));
            assertTrue(Files.size(bss) > 0, bss.toString());
            Stylesheet binary = Stylesheet.loadBinary(bss.toUri().toURL());
            Stylesheet text =
                    new javafx.css.CssParser().parse(dir.resolve(name).toUri().toURL());
            assertEquals(text.getRules().size(), binary.getRules().size(), name);
        }
        assertFalse(Files.exists(dir.resolve("notes.bss")));
        assertEquals(dir.resolve("a.bss"), StyleCompiler.binaryOf(dir.resolve("a.css")));
        assertEquals("notes.txt", StyleCompiler.binaryName("notes.txt"));
    }
}
