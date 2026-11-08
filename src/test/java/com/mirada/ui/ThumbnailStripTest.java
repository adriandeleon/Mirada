package com.mirada.ui;

import java.util.List;

import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

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
    void theStripGoesToTheSideAndBackToTheBottomAboveTheStatusBar() {
        BorderPane root = new BorderPane();
        Region statusBar = new Region();
        VBox bottom = new VBox(statusBar);
        root.setBottom(bottom);
        Region strip = new Region();
        ThumbnailStrip.place(root, bottom, strip, false);
        ThumbnailStrip.place(root, bottom, strip, true);
        assertSame(strip, root.getLeft());
        assertEquals(List.of(statusBar), bottom.getChildren());
        ThumbnailStrip.place(root, bottom, strip, false);
        assertEquals(List.of(strip, statusBar), bottom.getChildren());
        assertNull(root.getLeft());
        assertEquals(List.of(bottom), root.getChildren());
    }

    @Test
    void namesOnlyIgnoreCaptions() {
        assertEquals(ThumbnailStrip.size(true, true, false), ThumbnailStrip.size(true, true, true));
        assertEquals(ThumbnailStrip.size(false, true, false), ThumbnailStrip.size(false, true, true));
    }
}
