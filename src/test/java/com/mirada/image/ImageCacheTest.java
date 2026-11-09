package com.mirada.image;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;

import com.mirada.ui.FxTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static com.mirada.ui.FxTestSupport.call;
import static com.mirada.ui.FxTestSupport.field;
import static com.mirada.ui.FxTestSupport.waitUntil;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The image cache is bounded twice (PERF-11): full images beyond its capacity are dropped, the least
 * recently used first, and the previews it keeps of them are dropped past {@link ImageCache#PREVIEWS},
 * the oldest first.
 */
@Tag("fx")
class ImageCacheTest {

    @TempDir
    Path folder;

    private ImageCache cache = new ImageCache(2);

    @BeforeAll
    static void toolkit() throws Exception {
        FxTestSupport.start();
    }

    @AfterEach
    void close() throws Exception {
        FxTestSupport.fx(cache::close);
    }

    private List<Path> pictures(int count) throws IOException {
        List<Path> files = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            BufferedImage image = new BufferedImage(16 + i, 12, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            g.setColor(new Color(i * 8, 100, 200));
            g.fillRect(0, 0, image.getWidth(), image.getHeight());
            g.dispose();
            Path file = folder.resolve(String.format("%02d.png", i));
            ImageIO.write(image, "png", file.toFile());
            files.add(file);
        }
        return files;
    }

    /** Asks for {@code file} as the window does, on the FX thread, and waits for it to be decoded. */
    private ImageCache.Decoded load(Path file) throws Exception {
        ImageCache.Entry entry = call(() -> cache.get(file));
        return entry.ready().get(20, TimeUnit.SECONDS);
    }

    @Test
    void pastItsCapacityTheLeastRecentlyUsedImageIsDroppedAndTheNewestKept() throws Exception {
        List<Path> files = pictures(3);
        ImageCache.Entry first = call(() -> cache.get(files.get(0)));
        first.ready().get(20, TimeUnit.SECONDS);
        assertNotNull(load(files.get(1)).image());
        assertSame(first, call(() -> cache.get(files.get(0))), "within the capacity it is kept");
        load(files.get(2));
        // 0 was used after 1, so 1 is the one that goes.
        Map<Path, ?> entries = call(() -> Map.copyOf(field(cache, "entries")));
        assertEquals(java.util.Set.of(files.get(0), files.get(2)), entries.keySet());
    }

    @Test
    void anImageDroppedLeavesAPreviewAndPastTheirCapThePreviewsOfTheOldestGo() throws Exception {
        int count = ImageCache.PREVIEWS + 2 + 2;
        List<Path> files = pictures(count);
        for (Path file : files) assertTrue(load(file).error().isEmpty(), file + " decodes");
        // Every image but the two kept has been dropped; the previews of all but the oldest two stay.
        waitUntil(
                "the previews",
                () -> this.<Map<Path, ?>>previews().size() == ImageCache.PREVIEWS
                        && this.<Map<Path, ?>>previews().containsKey(files.get(count - 3)));
        Map<Path, ?> previews = call(() -> Map.copyOf(this.<Map<Path, ?>>previews()));
        assertFalse(previews.containsKey(files.get(0)), "the oldest preview was dropped");
        assertFalse(previews.containsKey(files.get(1)), "the second oldest too");
        assertTrue(previews.containsKey(files.get(2)));
        // An image asked for again comes with its preview to stand in while it decodes.
        ImageCache.Entry again = call(() -> cache.get(files.get(5)));
        assertNotNull(again.preview(), "its preview stands in");
        assertEquals(16 + 5, again.preview().width(), "at the full image's size");
        assertNull(call(() -> cache.get(files.get(0))).preview(), "the oldest has none left");
    }

    private <T> T previews() {
        return field(cache, "previews");
    }
}
