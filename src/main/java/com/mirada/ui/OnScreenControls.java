package com.mirada.ui;

import java.util.function.Consumer;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

/**
 * The floating controls over the image, GNOME Image Viewer's: previous and next in a pill at the
 * bottom left, zoom and its menu in a pill at the bottom right.
 *
 * <p>They show when the pointer moves over the image and fade out after {@link #IDLE} without
 * movement, so a still image is just the image; never while the pointer is on one of them. The
 * keyboard does everything they do, so they take no focus.
 */
final class OnScreenControls {

    /** How long the pointer may rest before the controls fade. */
    static final Duration IDLE = Duration.seconds(2);

    private final HBox navigation;
    private final HBox zooming;
    private final Button previous = button(Icons.PREVIOUS, "Previous Image (←)");
    private final Button next = button(Icons.NEXT, "Next Image (→)");
    private final Button zoom = button(Icons.ZOOM_IN, "Actual Size (1)");
    private final MenuButton zoomMenu = new MenuButton();
    private final PauseTransition idle = new PauseTransition(IDLE);
    private boolean fitted = true;
    private boolean hasImage;

    OnScreenControls(StackPane canvas, Consumer<Command> run) {
        previous.setOnAction(e -> run.accept(Command.PREVIOUS));
        next.setOnAction(e -> run.accept(Command.NEXT));
        // One button between the two views people want most, as GNOME's: the whole image, and
        // its pixels.
        zoom.setOnAction(e -> run.accept(fitted ? Command.ACTUAL_SIZE : Command.FIT));
        zoomMenu.setGraphic(Icons.of(Icons.CHEVRON_DOWN));
        zoomMenu.setTooltip(new Tooltip("Zoom"));
        zoomMenu.getStyleClass().addAll("osd-button", "flat", "no-arrow");
        zoomMenu.setFocusTraversable(false);
        zoomMenu.getItems()
                .addAll(
                        item(Command.FIT, run),
                        item(Command.ACTUAL_SIZE, run),
                        new SeparatorMenuItem(),
                        item(Command.ZOOM_IN, run),
                        item(Command.ZOOM_OUT, run));

        navigation = pill(previous, next);
        zooming = pill(zoom, zoomMenu);
        StackPane.setAlignment(navigation, Pos.BOTTOM_LEFT);
        StackPane.setAlignment(zooming, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(navigation, new Insets(0, 0, 18, 18));
        StackPane.setMargin(zooming, new Insets(0, 18, 18, 0));
        canvas.getChildren().addAll(navigation, zooming);

        idle.setOnFinished(e -> {
            if (!navigation.isHover() && !zooming.isHover() && !zoomMenu.isShowing()) fade(false);
        });
        canvas.addEventFilter(MouseEvent.MOUSE_MOVED, e -> wake());
        canvas.addEventFilter(MouseEvent.MOUSE_DRAGGED, e -> wake());
        canvas.setOnMouseExited(e -> {
            if (!zoomMenu.isShowing()) fade(false);
        });
        zoomMenu.showingProperty().addListener((o, was, now) -> {
            if (!now) idle.playFromStart();
        });
        setVisible(navigation, false);
        setVisible(zooming, false);
    }

    /** How long the pointer may rest before the controls fade, in seconds (Settings). */
    void setIdle(int seconds) {
        idle.setDuration(Duration.seconds(seconds));
    }

    /** Brings the controls back and restarts the idle clock. */
    void wake() {
        if (!hasImage) return;
        fade(true);
        idle.playFromStart();
    }

    /**
     * What the controls can do right now: stepping where there is somewhere to step to (the same
     * rule as the palette's), zooming when an image is on screen.
     */
    void update(int index, int size, boolean imageShowing, boolean fittedNow) {
        hasImage = imageShowing;
        fitted = fittedNow;
        previous.setDisable(Command.PREVIOUS.unavailable(index, size, imageShowing) != null);
        next.setDisable(Command.NEXT.unavailable(index, size, imageShowing) != null);
        zoom.setGraphic(Icons.of(fittedNow ? Icons.ZOOM_IN : Icons.ZOOM_FIT));
        zoom.getTooltip().setText(fittedNow ? "Actual Size (1)" : "Zoom to Fit (0)");
        zoom.setDisable(!imageShowing);
        zoomMenu.setDisable(!imageShowing);
        if (!imageShowing) fade(false);
    }

    private void fade(boolean in) {
        for (Region pill : new Region[] {navigation, zooming}) {
            if (in == pill.isVisible() && pill.getOpacity() == (in ? 1 : 0)) continue;
            FadeTransition transition = new FadeTransition(Duration.millis(in ? 120 : 250), pill);
            transition.setToValue(in ? 1 : 0);
            if (in) {
                setVisible(pill, true);
            } else {
                transition.setOnFinished(e -> {
                    if (pill.getOpacity() == 0) setVisible(pill, false);
                });
            }
            transition.play();
        }
    }

    private static void setVisible(Region pill, boolean visible) {
        pill.setVisible(visible);
        if (!visible) pill.setOpacity(0);
    }

    private static HBox pill(Node... buttons) {
        HBox pill = new HBox(2, buttons);
        pill.getStyleClass().add("osd");
        pill.setAlignment(Pos.CENTER);
        pill.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        pill.setPickOnBounds(false);
        return pill;
    }

    private static Button button(String icon, String tip) {
        Button button = new Button();
        button.setGraphic(Icons.of(icon));
        button.setTooltip(new Tooltip(tip));
        button.getStyleClass().addAll("osd-button", "flat");
        button.setFocusTraversable(false);
        return button;
    }

    private static MenuItem item(Command command, Consumer<Command> run) {
        MenuItem item = new MenuItem(command.label());
        item.setOnAction(e -> run.accept(command));
        return item;
    }
}
