package com.mirada.ui;

import java.util.Optional;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import org.jspecify.annotations.Nullable;

/**
 * The one confirm helper (WIN-05, CONF-01 to CONF-04, Pauta 4.0.1): a question before something the
 * user made is removed, discarded or replaced. The question names the thing and what happens to it;
 * the action button names the action, in AtlantaFX's {@code danger} style, never OK or Yes; Cancel
 * has the {@code CANCEL_CLOSE} role, so Escape and the window's close button answer Cancel, and
 * anything but the action button is a Cancel. The words come from the pure builders below, tested
 * without the toolkit (CONF-12).
 */
final class Confirm {

    /** What to ask: the window's title, the question, a line on what follows, and the action's verb. */
    record Question(String title, String header, String message, String action) {}

    private Confirm() {}

    /** Removing a mark that carries a note, which cannot be typed again from memory. */
    static Question removeMark(String name, boolean bookmarked) {
        String what = bookmarked ? "bookmark and note" : "note";
        return new Question(
                "Remove",
                "Remove the " + what + " on " + name + "?",
                "The note goes with it. This cannot be undone.",
                "Remove");
    }

    /** Leaving the editor with a crop, turn or flip not yet saved. */
    static Question discardEdit(String name) {
        return new Question(
                "Discard Edit",
                "Discard the edit to " + name + "?",
                "The crop, turns and flips made since it opened are lost; the file itself is unchanged.",
                "Discard");
    }

    /**
     * Saving over a file the save dialog never asked about: the extension Mirada added made the name
     * of one that exists.
     */
    static Question replaceFile(String name) {
        return new Question(
                "Replace File", "Replace " + name + "?", "A file with that name is already there.", "Replace");
    }

    /**
     * Importing a configuration over the one in use: the parts ticked under the question are
     * replaced (SET-12).
     */
    static Question importConfiguration(String zip) {
        return new Question(
                "Import Configuration",
                "Import " + zip + " over your configuration?",
                "The parts ticked below replace yours; the rest stay as they are. This cannot be undone,"
                        + " but Export first keeps a copy.",
                "Import");
    }

    /** Asks over {@code owner}, styled as it is, and waits; true only for the action button. */
    static boolean ask(Question question, @Nullable Window owner) {
        return ask(question, owner, null);
    }

    /**
     * {@link #ask} with {@code extra} under the message: what to choose before answering, such as
     * the parts of an import to replace.
     */
    static boolean ask(Question question, @Nullable Window owner, @Nullable Node extra) {
        ButtonType action = new ButtonType(question.action(), ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, question.message(), action, cancel);
        alert.setTitle(question.title());
        alert.setHeaderText(question.header());
        alert.getDialogPane().lookupButton(action).getStyleClass().add("danger");
        if (extra != null) {
            Label message = new Label(question.message());
            message.setWrapText(true);
            alert.getDialogPane().setContent(new VBox(12, message, extra));
        }
        if (owner != null) {
            alert.initOwner(owner);
            Scene scene = owner.getScene();
            if (scene != null) alert.getDialogPane().getStylesheets().addAll(scene.getStylesheets());
        }
        return confirmed(alert.showAndWait());
    }

    /** Whether an answer is the action: a close by any other route is Cancel. */
    static boolean confirmed(Optional<ButtonType> answer) {
        return answer.map(b -> b.getButtonData() == ButtonBar.ButtonData.OK_DONE)
                .orElse(false);
    }
}
