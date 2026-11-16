package com.mirada.ui;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import javafx.css.Stylesheet;

/**
 * Precompiles Mirada's stylesheets to JavaFX's binary form (PERF-17), the app template's: every
 * {@code .css} in a folder gets a {@code .bss} beside it, which loads without being parsed. The
 * release build ({@code -Pdist}) runs it on the compiled resources, so the packaged app carries
 * both; {@link UiZoom#sheet} takes the {@code .bss} at 100 % and scales the {@code .css} text at any
 * other zoom. No toolkit is needed.
 */
public final class StyleCompiler {

    private StyleCompiler() {}

    /** {@code args[0]}: the folder of stylesheets. */
    public static void main(String[] args) throws IOException {
        List<Path> done = compile(Path.of(args[0]));
        System.out.println("[style-compiler] " + done.size() + " stylesheets precompiled to .bss");
    }

    /** Writes a {@code .bss} beside every {@code .css} in {@code folder}; returns the files written. */
    static List<Path> compile(Path folder) throws IOException {
        List<Path> written = new ArrayList<>();
        try (Stream<Path> files = Files.list(folder)) {
            for (Path css :
                    files.filter(f -> f.toString().endsWith(".css")).sorted().toList()) {
                Path bss = binaryOf(css);
                Stylesheet.convertToBinary(css.toFile(), new File(bss.toString()));
                written.add(bss);
            }
        }
        return written;
    }

    /** {@code mirada.css} → {@code mirada.bss}, beside it. Pure. */
    static Path binaryOf(Path css) {
        String name = css.getFileName().toString();
        return css.resolveSibling(binaryName(name));
    }

    /** {@code mirada.css} → {@code mirada.bss}; a name that is no {@code .css} is kept. Pure. */
    static String binaryName(String name) {
        return name.endsWith(".css") ? name.substring(0, name.length() - ".css".length()) + ".bss" : name;
    }
}
