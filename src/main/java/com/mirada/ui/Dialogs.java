package com.mirada.ui;

import java.util.Map;

import javafx.collections.ListChangeListener;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.stage.Window;

import org.jspecify.annotations.Nullable;

import static com.mirada.i18n.Messages.tr;

/**
 * The one way Mirada makes a dialog (WIN-04, Pauta 6.1.0), the app template's: a dialog is its own
 * scene and inherits nothing from the window, so every {@code Alert} and {@code Dialog} goes through
 * {@link #styled}. It puts {@code mirada.css} first on the dialog's scene, at the UI zoom in effect
 * ({@link UiZoom} keeps it current); the control theme, the accent, the UI font and high contrast
 * reach it as they reach every window, when it shows ({@link Theme}, {@link Fonts}), after
 * {@code mirada.css}, so high contrast's roles win. It also gives every dialog one button order on
 * every OS, Cancel left of every action (WIN-11), and words JavaFX's stock buttons from the catalog
 * rather than in the JVM's locale (CONF-14).
 */
final class Dialogs {

    /**
     * One {@code ButtonBar} order for every OS (WIN-11): help at the left, then Cancel ({@code C})
     * before every action, so the unsaved-edit question reads Cancel, Save ({@code Y}), Discard
     * ({@code N}) (CONF-05). JavaFX's own default changes with the OS.
     */
    static final String BUTTON_ORDER = "L_HE+CUYNBXIAO_R";

    /** JavaFX's stock buttons and the catalog key that words each one (CONF-14). */
    static final Map<ButtonType, String> STOCK = Map.of(
            ButtonType.OK, "button.ok",
            ButtonType.CANCEL, "button.cancel",
            ButtonType.YES, "button.yes",
            ButtonType.NO, "button.no",
            ButtonType.APPLY, "button.apply",
            ButtonType.CLOSE, "button.close");

    private Dialogs() {}

    /** {@code dialog}, owned by {@code owner} when there is one, styled, ordered and worded. */
    static <T extends Dialog<?>> T styled(T dialog, @Nullable Window owner) {
        if (owner != null && dialog.getOwner() == null) dialog.initOwner(owner);
        DialogPane pane = dialog.getDialogPane();
        Scene scene = pane.getScene();
        String sheet = ViewerWindow.stylesheet();
        // On the scene, not the pane: a pane's sheets would outrank high contrast's, which is the scene's.
        if (scene != null && !scene.getStylesheets().contains(sheet))
            scene.getStylesheets().addFirst(sheet);
        if (pane.lookup(".button-bar") instanceof ButtonBar bar) bar.setButtonOrder(BUTTON_ORDER);
        wordStockButtons(pane);
        // A button added later is worded too: DialogPane makes a new Button for each change.
        pane.getButtonTypes().addListener((ListChangeListener<ButtonType>) change -> wordStockButtons(pane));
        return dialog;
    }

    /** Sets each stock button's text from the catalog; a button Mirada named itself is left alone. */
    private static void wordStockButtons(DialogPane pane) {
        for (ButtonType type : pane.getButtonTypes()) {
            String key = STOCK.get(type);
            if (key != null && pane.lookupButton(type) instanceof Button button) button.setText(tr(key));
        }
    }
}
