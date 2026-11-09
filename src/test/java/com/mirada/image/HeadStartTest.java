package com.mirada.image;

import java.lang.reflect.Field;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The first image decodes while the toolkit starts (HeadStart): the window claims it for its file,
 * once; a head start for another file, or one JavaFX's own decoder must make, is not taken.
 */
class HeadStartTest {

    private static AtomicReference<Object> state() throws ReflectiveOperationException {
        Field field = HeadStart.class.getDeclaredField("STATE");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        AtomicReference<Object> state = (AtomicReference<Object>) field.get(null);
        return state;
    }

    private static Path fixture(String name) throws URISyntaxException {
        return Path.of(
                Objects.requireNonNull(HeadStartTest.class.getResource(name)).toURI());
    }

    private Object before;

    @BeforeEach
    void open() throws ReflectiveOperationException {
        before = state().getAndSet(null);
    }

    @AfterEach
    void close() throws ReflectiveOperationException {
        state().set(before);
    }

    private static Object waitForStart() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (state().get() == null && System.nanoTime() < deadline) Thread.sleep(5);
        return state().get();
    }

    @Test
    void theFirstImageIsDecodedAheadAndClaimedOnceForItsFile() throws Exception {
        Path photo = fixture("red-blue.tif");
        HeadStart.begin(List.of(photo.toString()));
        assertNotNull(waitForStart(), "started");
        HeadStart started = HeadStart.claim(photo.toAbsolutePath().normalize());
        assertNotNull(started, "claimed for its file");
        ArgbPixels pixels = started.pixels.get(20, TimeUnit.SECONDS);
        assertTrue(pixels.width() > 0);
        assertNotNull(started.exif.get(20, TimeUnit.SECONDS));
        assertNull(HeadStart.claim(photo.toAbsolutePath().normalize()), "the first claim closes the door");
    }

    @Test
    void anotherFilesClaimClosesTheDoorWithoutTakingIt() throws Exception {
        Path photo = fixture("red-blue.tif");
        HeadStart.begin(List.of(photo.toString()));
        waitForStart();
        assertNull(HeadStart.claim(photo.resolveSibling("red-blue.jpg")));
        assertNull(HeadStart.claim(photo.toAbsolutePath().normalize()), "too late: the window asked first");
    }

    @Test
    void nothingStartsForJavaFxsOwnFormatsAMissingFileOrNoFile() throws Exception {
        HeadStart.begin(List.of());
        HeadStart.begin(List.of(fixture("red-blue.png").toString()));
        HeadStart.begin(List.of(
                fixture("red-blue.png").resolveSibling("missing-folder/x.jpg").toString()));
        Thread.sleep(200);
        assertSame(null, state().get());
        assertFalse(HeadStart.canStartEarly(com.mirada.folder.ImageFormats.Decoder.JAVAFX));
        assertTrue(HeadStart.canStartEarly(com.mirada.folder.ImageFormats.Decoder.TURBOJPEG));
        assertEquals(null, HeadStart.claim(fixture("red-blue.png")));
    }
}
