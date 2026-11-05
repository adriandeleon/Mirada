package com.mirada.os;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RevealTest {

    private static final Path PHOTO =
            Path.of("/home/a/Bob's photos, 2026/IMG 1.jpg").toAbsolutePath();

    @Test
    void finderIsAskedToRevealIt() {
        assertEquals(List.of(List.of("open", "-R", PHOTO.toString())), Reveal.commands("Mac OS X", PHOTO));
    }

    @Test
    void explorerIsAskedToSelectIt() {
        assertEquals(
                List.of(List.of("explorer.exe", "/select,", PHOTO.toString())), Reveal.commands("Windows 11", PHOTO));
    }

    @Test
    void linuxAsksTheFileManagerOverDBusThenOpensTheFolder() {
        List<List<String>> commands = Reveal.commands("Linux", PHOTO);
        assertEquals("gdbus", commands.get(0).getFirst());
        assertEquals("dbus-send", commands.get(1).getFirst());
        assertEquals(List.of("xdg-open", PHOTO.getParent().toString()), commands.get(2));
    }

    /** A quote would end gdbus's string and a comma split dbus-send's array: both are escaped. */
    @Test
    void theUriHasNeitherQuoteNorComma() {
        String uri = Reveal.uri(PHOTO);
        assertFalse(uri.contains("'"), uri);
        assertFalse(uri.contains(","), uri);
        assertTrue(uri.startsWith("file:"), uri);
        assertEquals(PHOTO, Path.of(java.net.URI.create(uri)));
    }
}
