package com.mirada.ui;

import java.util.List;

import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThumbnailStripTest {

    @Test
    void aCaptionMakesTheStripThickerAndItsCellsWiderAlongTheBottom() {
        ThumbnailStrip.Size plain = ThumbnailStrip.size(false, false, false);
        ThumbnailStrip.Size captioned = ThumbnailStrip.size(false, false, true);
        assertTrue(captioned.thickness() > plain.thickness());
        assertTrue(captioned.cell() > plain.cell());
    }

    @Test
    void downTheSideACaptionMakesEachCellTallerAndTheColumnWider() {
        ThumbnailStrip.Size plain = ThumbnailStrip.size(true, false, false);
        ThumbnailStrip.Size captioned = ThumbnailStrip.size(true, false, true);
        assertEquals(plain.cell() + ThumbnailStrip.CAPTION, captioned.cell());
        assertTrue(captioned.thickness() > plain.thickness());
    }

    @Test
    void theStripGoesToTheSideAndBackToTheBottom() {
        BorderPane root = new BorderPane();
        Region strip = new Region();
        ThumbnailStrip.place(root, strip, false);
        ThumbnailStrip.place(root, strip, true);
        assertSame(strip, root.getLeft());
        assertNull(root.getBottom());
        ThumbnailStrip.place(root, strip, false);
        assertSame(strip, root.getBottom());
        assertNull(root.getLeft());
        assertEquals(List.of(strip), root.getChildren());
    }

    @Test
    void namesOnlyIgnoreCaptions() {
        assertEquals(ThumbnailStrip.size(true, true, false), ThumbnailStrip.size(true, true, true));
        assertEquals(ThumbnailStrip.size(false, true, false), ThumbnailStrip.size(false, true, true));
    }
}
