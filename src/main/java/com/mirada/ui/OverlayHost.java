package com.mirada.ui;

import javafx.scene.AccessibleAction;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

import org.jspecify.annotations.Nullable;

import static com.mirada.i18n.Messages.tr;

/**
 * The in-scene overlay of a window (Editora's {@code OverlayHost}): the one place the command
 * palette, its pickers and the Message Log show their card, over a backdrop that cancels on a press.
 * One card at a time: showing one replaces whatever card was up (QOPEN-11), and hiding the last gives
 * the focus back to the node that had it before the first opened (PAL-07, QOPEN-07). Never a {@code
 * javafx.stage.Popup}, which on Windows does not reliably take the keyboard (KBD-07).
 *
 * <p>FX thread only.
 */
final class OverlayHost {

    private final StackPane root;

    /** A press on it cancels, so a screen reader hears a button that does the same. */
    private final Region backdrop = new Region() {
        @Override
        public void executeAccessibleAction(AccessibleAction action, Object... parameters) {
            if (action == AccessibleAction.FIRE) hide();
            else super.executeAccessibleAction(action, parameters);
        }
    };

    private @Nullable Node card;
    private @Nullable String backdropStyle;
    private @Nullable Runnable onHide;
    /** The focus owner before the first card opened: where the focus goes back to when the last one goes. */
    private @Nullable Node returnFocus;

    OverlayHost(StackPane root) {
        this.root = root;
        backdrop.setAccessibleRole(AccessibleRole.BUTTON);
        backdrop.setAccessibleText(tr("keys.cancel"));
        // Dismiss on press and consume it, so the click that closes the card does not also reach
        // what lies under it.
        backdrop.setOnMousePressed(e -> {
            hide();
            e.consume();
        });
    }

    StackPane root() {
        return root;
    }

    /** Whether {@code node} is the card up now. */
    boolean isShowing(Node node) {
        return card == node;
    }

    boolean isShowing() {
        return card != null;
    }

    /**
     * Shows {@code node} over a backdrop styled {@code backdropStyle}, replacing the card up now, if
     * any (its {@code onHide} runs; the same card shown again stays, with its new {@code onHide}); the
     * focus to give back stays the one from before the first card.
     *
     * @param onHide runs when this card goes, whether hidden or replaced
     */
    void show(Node node, String backdropStyle, @Nullable Runnable onHide) {
        Node before = card;
        if (before == null) {
            Scene scene = root.getScene();
            returnFocus = scene == null ? null : scene.getFocusOwner();
        } else if (before != node) {
            // Another card is up: it goes as if hidden, but the focus waits for the last one.
            root.getChildren().remove(before);
            card = null;
            Runnable replaced = this.onHide;
            this.onHide = null;
            if (replaced != null) replaced.run();
        }
        String style = this.backdropStyle;
        if (style != null) backdrop.getStyleClass().remove(style);
        backdrop.getStyleClass().add(backdropStyle);
        this.backdropStyle = backdropStyle;
        card = node;
        this.onHide = onHide;
        root.getChildren().removeAll(backdrop, node);
        root.getChildren().addAll(backdrop, node);
    }

    /** Takes the card down, if one is up, and gives the focus back to where it was before it opened. */
    void hide() {
        Node open = card;
        if (open == null) return;
        card = null;
        root.getChildren().removeAll(backdrop, open);
        Runnable after = onHide;
        onHide = null;
        if (after != null) after.run();
        Node focus = returnFocus;
        returnFocus = null;
        if (focus != null && focus.getScene() == root.getScene()) focus.requestFocus();
    }
}
