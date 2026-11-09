package com.mirada.ui;

import java.nio.file.Path;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;

import com.mirada.folder.FolderImages;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static com.mirada.ui.FxTestSupport.call;
import static com.mirada.ui.FxTestSupport.fx;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The hot paths that once did work for every image of the folder on every step, each with a headless
 * test that fails on the old code (PERF-08, PERF-21).
 */
@Tag("fx")
class HotPathsTest {

    /** A folder's list that counts how often its images are read. */
    private static final class Counted extends AbstractList<Path> {
        private final List<Path> paths;
        int reads;

        Counted(int size) {
            List<Path> made = new ArrayList<>(size);
            for (int i = 0; i < size; i++) made.add(Path.of("IMG_" + i + ".jpg"));
            paths = List.copyOf(made);
        }

        @Override
        public Path get(int index) {
            reads++;
            return paths.get(index);
        }

        @Override
        public int size() {
            return paths.size();
        }
    }

    @Test
    void aStepAlongTheStripReadsNoneOfTheFoldersImages() throws Exception {
        FxTestSupport.start();
        Counted folder = new Counted(5_000);
        ThumbnailStrip strip = call(() -> new ThumbnailStrip(index -> {}, new ImageViewport().thumbnailDragger()));
        try {
            fx(() -> strip.show(folder, 0));
            folder.reads = 0;
            for (int step = 1; step < 50; step++) {
                int at = step;
                fx(() -> strip.show(folder, at));
            }
            // The old code compared the whole list on every step: 5,000 reads a step.
            assertEquals(0, folder.reads, "a step compares the list once, by identity");
        } finally {
            fx(strip::close);
        }
    }

    @Test
    void aStepKeepsTheFoldersListSoTheStripKnowsItAtOnce() {
        FolderImages folder = new FolderImages(List.of(Path.of("a.jpg"), Path.of("b.jpg"), Path.of("c.jpg")), 0);
        assertSame(folder.images(), folder.step(1).images());
        assertSame(folder.images(), folder.jump(2).images());
        assertSame(folder.images(), folder.last().first().images());
        assertFalse(ThumbnailStrip.changed(folder.images(), folder.step(1).images()));
        assertTrue(ThumbnailStrip.changed(folder.images(), List.copyOf(new ArrayList<>(folder.images()))));
    }
}
