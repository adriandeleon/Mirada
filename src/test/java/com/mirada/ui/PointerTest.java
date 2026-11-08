package com.mirada.ui;

import java.util.ArrayList;
import java.util.List;

import javafx.event.EventType;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.SwipeEvent;
import javafx.scene.input.ZoomEvent;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import com.mirada.edit.CropBox;
import com.mirada.image.Orientation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testfx.api.FxToolkit;

import static com.mirada.ui.FxTestSupport.call;
import static com.mirada.ui.FxTestSupport.fx;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pointer on the image and on the crop: a wheel zooms, a trackpad pans or swipes, a pinch
 * zooms, a drag pans what is zoomed in, a double click goes between fitted and actual size; the
 * crop's edges and corners resize it and its inside moves it, each drag one step of undo.
 */
@Tag("fx")
class PointerTest {

    @BeforeAll
    static void toolkit() throws Exception {
        FxTestSupport.start();
    }

    @AfterEach
    void close() throws Exception {
        FxToolkit.cleanupStages();
    }

    /** {@code node} alone in a 400×300 window, laid out. */
    private static <T extends javafx.scene.Parent> T shown(T node) throws Exception {
        fx(() -> {
            Stage stage = new Stage();
            stage.setScene(new Scene(new StackPane(node), 400, 300));
            stage.setX(0);
            stage.setY(0);
            stage.show();
            node.applyCss();
            node.layout();
        });
        return node;
    }

    private static MouseEvent mouse(EventType<MouseEvent> type, double x, double y, MouseButton button, int clicks) {
        return new MouseEvent(
                type,
                x,
                y,
                x,
                y,
                button,
                clicks,
                false,
                false,
                false,
                false,
                button == MouseButton.PRIMARY,
                false,
                false,
                false,
                false,
                true,
                null);
    }

    private static ScrollEvent scroll(double dx, double dy, int touches, boolean inertia, boolean control) {
        return new ScrollEvent(
                ScrollEvent.SCROLL,
                200,
                150,
                200,
                150,
                false,
                control,
                false,
                false,
                false,
                inertia,
                dx,
                dy,
                dx,
                dy,
                ScrollEvent.HorizontalTextScrollUnits.NONE,
                0,
                ScrollEvent.VerticalTextScrollUnits.NONE,
                0,
                touches,
                null);
    }

    // ---- The image ----

    @Test
    void aWheelZoomsAPinchZoomsAndADoubleClickGoesBetweenFittedAndActualSize() throws Exception {
        ImageViewport viewport = shown(new ImageViewport());
        fx(() -> viewport.show(new WritableImage(800, 600), Orientation.NORMAL));
        fx(viewport::layout);
        assertTrue(call(() -> viewport.fittedProperty().get()));
        double fitted = call(() -> viewport.scaleProperty().get());
        assertEquals(0.5, fitted, 0.01, "800×600 in 400×300");

        fx(() -> viewport.fireEvent(scroll(0, 40, 0, false, false)));
        assertTrue(call(() -> viewport.scaleProperty().get()) > fitted, "the wheel up zooms in");
        fx(() -> viewport.fireEvent(scroll(0, 0, 0, false, false)));
        fx(() -> {
            viewport.fit(); // fitted again on the next layout
            viewport.layout();
        });
        fx(() -> viewport.fireEvent(new ZoomEvent(
                ZoomEvent.ZOOM, 200, 150, 200, 150, false, false, false, false, false, false, 2, 2, null)));
        assertEquals(fitted * 2, call(() -> viewport.scaleProperty().get()), 0.01, "a pinch zooms by its factor");

        fx(viewport::fit);
        fx(() -> viewport.fireEvent(mouse(MouseEvent.MOUSE_CLICKED, 100, 100, MouseButton.PRIMARY, 2)));
        assertEquals(1.0, call(() -> viewport.scaleProperty().get()), "a double click: actual size");
        fx(() -> viewport.fireEvent(mouse(MouseEvent.MOUSE_CLICKED, 100, 100, MouseButton.PRIMARY, 2)));
        assertTrue(call(() -> viewport.fittedProperty().get()), "and back to fitted");
        fx(() -> viewport.fireEvent(mouse(MouseEvent.MOUSE_CLICKED, 100, 100, MouseButton.SECONDARY, 2)));
        assertTrue(call(() -> viewport.fittedProperty().get()), "a right double click does nothing");
    }

    @Test
    void aDragPansWhatIsZoomedInAndATrackpadPansOrSwipes() throws Exception {
        ImageViewport viewport = shown(new ImageViewport());
        List<Integer> swipes = new ArrayList<>();
        fx(() -> {
            viewport.setOnSwipe(swipes::add);
            viewport.show(new WritableImage(800, 600), Orientation.NORMAL);
            viewport.layout();
        });
        // Fitted, a trackpad swipe steps once a gesture, whichever way it goes.
        fx(() -> {
            viewport.fireEvent(new ScrollEvent(
                    ScrollEvent.SCROLL_STARTED,
                    200,
                    150,
                    200,
                    150,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    0,
                    0,
                    0,
                    0,
                    ScrollEvent.HorizontalTextScrollUnits.NONE,
                    0,
                    ScrollEvent.VerticalTextScrollUnits.NONE,
                    0,
                    2,
                    null));
            for (int i = 0; i < 5; i++) viewport.fireEvent(scroll(-40, 0, 2, false, false));
            viewport.fireEvent(scroll(-40, 0, 2, true, false)); // the glide after the fingers lift
        });
        assertEquals(List.of(1), swipes, "a swipe to the left: the next image, once");
        fx(() -> viewport.fireEvent(new SwipeEvent(
                SwipeEvent.SWIPE_RIGHT, 200, 150, 200, 150, false, false, false, false, false, 3, null)));
        fx(() -> viewport.fireEvent(
                new SwipeEvent(SwipeEvent.SWIPE_LEFT, 200, 150, 200, 150, false, false, false, false, false, 3, null)));
        assertEquals(List.of(1, -1, 1), swipes);

        fx(viewport::actualSize);
        fx(viewport::layout);
        javafx.scene.Node view = FxTestSupport.field(viewport, "view");
        double before = call(view::getLayoutX);
        fx(() -> {
            viewport.fireEvent(mouse(MouseEvent.MOUSE_PRESSED, 200, 150, MouseButton.PRIMARY, 1));
            viewport.fireEvent(mouse(MouseEvent.MOUSE_DRAGGED, 250, 170, MouseButton.PRIMARY, 1));
        });
        assertEquals(Cursor.CLOSED_HAND, call(viewport::getCursor));
        fx(viewport::layout);
        assertEquals(before + 50, call(view::getLayoutX), 0.01, "dragged 50 to the right");
        fx(() -> viewport.fireEvent(mouse(MouseEvent.MOUSE_RELEASED, 250, 170, MouseButton.PRIMARY, 1)));
        assertEquals(null, call(viewport::getCursor));
        // Zoomed in, a trackpad pans instead of swiping, and Ctrl with it zooms.
        fx(() -> viewport.fireEvent(scroll(-30, 0, 2, false, false)));
        fx(viewport::layout);
        assertEquals(before + 20, call(view::getLayoutX), 0.01);
        double scale = call(() -> viewport.scaleProperty().get());
        fx(() -> viewport.fireEvent(scroll(0, -40, 2, false, true)));
        assertTrue(call(() -> viewport.scaleProperty().get()) < scale);
        assertEquals(List.of(1, -1, 1), swipes, "no swipe while there is something to pan");
        fx(() -> viewport.fireEvent(mouse(MouseEvent.MOUSE_DRAGGED, 300, 170, MouseButton.SECONDARY, 1)));
    }

    @Test
    void anEmptyViewportIgnoresThePointer() throws Exception {
        ImageViewport viewport = shown(new ImageViewport());
        List<Integer> swipes = new ArrayList<>();
        fx(() -> {
            viewport.setOnSwipe(swipes::add);
            viewport.fireEvent(scroll(0, 40, 0, false, false));
            viewport.fireEvent(new ZoomEvent(
                    ZoomEvent.ZOOM, 200, 150, 200, 150, false, false, false, false, false, false, 2, 2, null));
            viewport.fireEvent(new SwipeEvent(
                    SwipeEvent.SWIPE_LEFT, 200, 150, 200, 150, false, false, false, false, false, 3, null));
        });
        assertEquals(List.of(), swipes);
        assertEquals(0.0, call(viewport::contentWidth));
    }

    @Test
    void aPreviewStandsInAtTheFullSizeAndATurnedOrMirroredImageIsLaidOutTurned() throws Exception {
        ImageViewport viewport = shown(new ImageViewport());
        javafx.scene.Node view = FxTestSupport.field(viewport, "view");
        fx(() -> {
            viewport.show(new WritableImage(100, 75), Orientation.MIRROR, 800, 600);
            viewport.layout();
        });
        assertEquals(800.0, call(viewport::contentWidth), "the preview is laid out at the full image's size");
        assertEquals(-1.0, call(view::getScaleX), "mirrored");
        fx(() -> {
            viewport.replace(new WritableImage(800, 600));
            viewport.layout();
        });
        assertEquals(800.0, call(viewport::contentWidth));
        // An orientation that turns it: laid out sideways, so the content is the turned size.
        fx(() -> {
            viewport.show(new WritableImage(800, 600), Orientation.TRANSVERSE);
            viewport.layout();
        });
        assertEquals(600.0, call(viewport::contentWidth));
        fx(() -> viewport.pan(0.1, 0.1)); // fitted: nothing to pan
        // Kept, the zoom carries to the next image.
        fx(() -> {
            viewport.setKeepZoom(true);
            viewport.actualSize();
            viewport.zoomIn();
            viewport.zoomIn();
            viewport.layout();
        });
        double scale = call(() -> viewport.scaleProperty().get());
        fx(() -> {
            viewport.show(new WritableImage(640, 480), Orientation.NORMAL);
            viewport.layout();
        });
        assertEquals(scale, call(() -> viewport.scaleProperty().get()), "the zoom kept across the step");
        javafx.scene.image.ImageView picture = (javafx.scene.image.ImageView) view;
        assertFalse(call(picture::isSmooth) && scale >= Zoom.CRISP_FROM, "crisp pixels when zoomed in far");
        fx(() -> {
            viewport.clear();
            viewport.layout();
        });
        assertEquals(0.0, call(viewport::contentWidth));
    }

    // ---- The crop ----

    @Test
    void theCropsCornersAndEdgesResizeItItsInsideMovesItAndEachDragIsOneStep() throws Exception {
        List<CropBox> changes = new ArrayList<>();
        int[] drags = {0};
        CropView crop = shown(new CropView(changes::add, () -> drags[0]++));
        fx(() -> {
            crop.show(new WritableImage(200, 100), new CropBox(0, 0, 200, 100), 0);
            crop.layout();
        });
        // 200×100 in 400×300 less a 28-pixel margin: scale 1.72, the picture at (28, 64)–(372, 236).
        record Spot(double x, double y, Cursor cursor) {}
        for (Spot spot : List.of(
                new Spot(28, 64, Cursor.NW_RESIZE),
                new Spot(372, 64, Cursor.NE_RESIZE),
                new Spot(28, 236, Cursor.SW_RESIZE),
                new Spot(372, 236, Cursor.SE_RESIZE),
                new Spot(28, 150, Cursor.W_RESIZE),
                new Spot(372, 150, Cursor.E_RESIZE),
                new Spot(200, 64, Cursor.N_RESIZE),
                new Spot(200, 236, Cursor.S_RESIZE),
                new Spot(200, 150, Cursor.MOVE),
                new Spot(5, 5, Cursor.DEFAULT))) {
            fx(() -> crop.fireEvent(mouse(MouseEvent.MOUSE_MOVED, spot.x(), spot.y(), MouseButton.NONE, 0)));
            assertEquals(spot.cursor(), call(crop::getCursor), "at " + spot);
        }

        // The bottom-right corner, dragged in.
        fx(() -> {
            crop.fireEvent(mouse(MouseEvent.MOUSE_PRESSED, 372, 236, MouseButton.PRIMARY, 1));
            crop.fireEvent(mouse(MouseEvent.MOUSE_DRAGGED, 286, 193, MouseButton.PRIMARY, 1));
            crop.fireEvent(mouse(MouseEvent.MOUSE_RELEASED, 286, 193, MouseButton.PRIMARY, 1));
        });
        CropBox resized = call(crop::box);
        assertEquals(150, resized.width(), 0.5, "86 pixels at 1.72 is 50 of the picture's");
        assertEquals(75, resized.height(), 0.5);
        assertEquals(1, drags[0], "one step of undo");
        assertFalse(changes.isEmpty());

        // Its inside, dragged: moved, the same size.
        fx(() -> {
            crop.fireEvent(mouse(MouseEvent.MOUSE_PRESSED, 100, 100, MouseButton.PRIMARY, 1));
            crop.fireEvent(mouse(MouseEvent.MOUSE_DRAGGED, 143, 143, MouseButton.PRIMARY, 1));
            crop.fireEvent(mouse(MouseEvent.MOUSE_RELEASED, 143, 143, MouseButton.PRIMARY, 1));
        });
        CropBox moved = call(crop::box);
        assertNotEquals(resized.x(), moved.x());
        assertEquals(resized.width(), moved.width(), 0.001);
        assertEquals(2, drags[0]);

        // A press outside the picture and a click that moves nothing record no step.
        fx(() -> {
            crop.fireEvent(mouse(MouseEvent.MOUSE_PRESSED, 2, 2, MouseButton.PRIMARY, 1));
            crop.fireEvent(mouse(MouseEvent.MOUSE_DRAGGED, 50, 50, MouseButton.PRIMARY, 1));
            crop.fireEvent(mouse(MouseEvent.MOUSE_RELEASED, 50, 50, MouseButton.PRIMARY, 1));
            crop.fireEvent(mouse(MouseEvent.MOUSE_PRESSED, 200, 150, MouseButton.PRIMARY, 1));
            crop.fireEvent(mouse(MouseEvent.MOUSE_RELEASED, 200, 150, MouseButton.PRIMARY, 1));
        });
        assertEquals(2, drags[0]);
        assertEquals(moved, call(crop::box));
    }
}
