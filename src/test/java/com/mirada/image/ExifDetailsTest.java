package com.mirada.image;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExifDetailsTest {

    private static Path fixture(String name) throws URISyntaxException {
        return Path.of(Objects.requireNonNull(ExifDetailsTest.class.getResource(name), name)
                .toURI());
    }

    /** exif.jpg carries a Canon EOS R6 shot with a lens, a date, exposure, flash and GPS. */
    @Test
    void theCameraCardReadsAsAPhotographerWouldSayIt() throws Exception {
        ExifDetails details = ExifDetails.read(fixture("exif.jpg"));
        Map<String, String> camera =
                details.camera().stream().collect(Collectors.toMap(ExifDetails.Row::label, ExifDetails.Row::value));

        assertEquals("Canon EOS R6", camera.get("Camera"));
        assertEquals("RF50mm F1.8 STM", camera.get("Lens"));
        assertEquals("2026-05-17 18:42:10", camera.get("Taken"));
        assertEquals("1/250 s", camera.get("Exposure"));
        assertEquals("f/4.0", camera.get("Aperture"));
        assertEquals("400", camera.get("ISO"));
        assertEquals("50 mm", camera.get("Focal Length"));
        assertTrue(camera.get("Flash").toLowerCase().contains("not fire"), camera.get("Flash"));
        // 19°25'57"N 99°7'59"W, as signed decimal degrees.
        assertEquals("19.43250, -99.13306", camera.get("Location"));
    }

    @Test
    void allMetadataListsEveryDirectory() throws Exception {
        ExifDetails details = ExifDetails.read(fixture("exif.jpg"));
        assertTrue(details.all().stream().anyMatch(s -> s.title().equals("Exif IFD0")));
        assertTrue(details.all().stream().anyMatch(s -> s.title().equals("GPS")));
        assertTrue(details.all().stream().allMatch(s -> !s.rows().isEmpty()));
    }

    @Test
    void aFileWithoutExifHasNoCameraCard() throws Exception {
        ExifDetails details = ExifDetails.read(fixture("red-blue.png"));
        assertTrue(details.camera().isEmpty());
        assertEquals(ExifDetails.NONE, ExifDetails.read(Path.of("no-such-file.jpg")));
    }

    @Test
    void datesAndExposuresAreRespelled() {
        assertEquals("2026-05-17 18:42:10", ExifDetails.taken("2026:05:17 18:42:10"));
        assertEquals("1/60 s", ExifDetails.seconds("1/60 sec"));
        assertFalse(ExifDetails.seconds("2 s").contains("sec"));
    }
}
