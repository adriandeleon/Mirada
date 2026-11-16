package com.mirada.ui;

import java.util.Optional;
import java.util.function.Function;

import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.stage.Window;

import org.jspecify.annotations.Nullable;

import static com.mirada.i18n.Messages.tr;

/**
 * The one confirm helper (WIN-05, CONF-01 to CONF-05, CONF-12 to CONF-14, Pauta 6.1.0): a question
 * before something the user made is discarded or replaced. The question names the thing and what
 * happens to it; the action button names the action, in AtlantaFX's {@code danger} style, never OK
 * or Yes, and is never the default: Cancel is, or Save where saving is an option, so Enter keeps
 * what the user has. Cancel has the {@code CANCEL_CLOSE} role, so Escape and the window's close
 * button answer Cancel, and anything but the action (or Save) is a Cancel. The words come from the
 * pure builders below, and a test answers through {@link #answeredBy} without a dialog (CONF-12).
 * Every dialog goes through {@link Dialogs#styled}.
 */
final class Confirm {

    /** What to ask: the window's title, the question, a line on what follows, and the action's verb. */
    record Question(String title, String header, String message, String action) {}

    private Confirm() {}

    /** Leaving the editor with a crop, turn or flip not yet saved. */
    static Question discardEdit(String name) {
        return new Question(
                tr("confirm.discardEdit.title"),
                tr("confirm.discardEdit.header", name),
                tr("confirm.discardEdit.message"),
                tr("confirm.discardEdit.action"));
    }

    /**
     * Saving over a file the save dialog never asked about: the extension Mirada added made the name
     * of one that exists.
     */
    static Question replaceFile(String name) {
        return new Question(
                tr("confirm.replaceFile.title"),
                tr("confirm.replaceFile.header", name),
                tr("confirm.replaceFile.message"),
                tr("confirm.replaceFile.action"));
    }

    /**
     * Importing a configuration over the one in use: the parts ticked under the question are
     * replaced (SET-12).
     */
    static Question importConfiguration(String zip) {
        return new Question(
                tr("confirm.importConfiguration.title"),
                tr("confirm.importConfiguration.header", zip),
                tr("confirm.importConfiguration.message"),
                tr("confirm.importConfiguration.action"));
    }

    /** Settings' Default keys: the keys the user gave the palette, {@code keys}, give way to its own. */
    static Question resetPaletteKeys(String keys) {
        return new Question(
                tr("confirm.resetPaletteKeys.title"),
                tr("confirm.resetPaletteKeys.header", keys),
                tr("confirm.resetPaletteKeys.message"),
                tr("confirm.resetPaletteKeys.action"));
    }

    /**
     * The answers to a question: its action, Save where saving is an option (CONF-05), or Cancel,
     * which Escape, the window's close button and anything else but the other two are.
     */
    enum Answer {
        CANCEL,
        SAVE,
        ACTION
    }

    /** Answers a question for a test instead of showing it (CONF-12); the app never sets it. */
    private static @Nullable Function<Question, Answer> answer;

    /** For tests only: answers every question until set back to null. */
    static void answeredBy(@Nullable Function<Question, Answer> by) {
        answer = by;
    }

    /** Asks over {@code owner} and waits; true only for the action button. */
    static boolean ask(Question question, @Nullable Window owner) {
        return ask(question, owner, null);
    }

    /**
     * {@link #ask} with {@code extra} under the message: what to choose before answering, such as
     * the parts of an import to replace.
     */
    static boolean ask(Question question, @Nullable Window owner, @Nullable Node extra) {
        Function<Question, Answer> by = answer;
        if (by != null) return by.apply(question) == Answer.ACTION;
        Alert alert = dialog(question, owner, extra, false);
        return answer(alert.showAndWait()) == Answer.ACTION;
    }

    /**
     * Asks before an edit not yet saved is lost, where saving is an option (CONF-05): Cancel, Save in
     * green and the action (Discard) in red, Save the default (CONF-13).
     */
    static Answer askOrSave(Question question, @Nullable Window owner) {
        Function<Question, Answer> by = answer;
        if (by != null) return by.apply(question);
        Alert alert = dialog(question, owner, null, true);
        return answer(alert.showAndWait());
    }

    /**
     * The question's dialog, not yet shown: the action in AtlantaFX's {@code danger} style and never
     * a default button (CONF-13). The default, which has the focus as the dialog opens, is Cancel, or
     * Save when {@code withSave}, so Enter on a question that has just opened keeps what the user
     * has; the red action runs only from its own button.
     */
    static Alert dialog(Question question, @Nullable Window owner, @Nullable Node extra, boolean withSave) {
        // Save is YES and the action NO when there are three, OK_DONE when there are two: the order
        // string draws Cancel, Save, Discard (WIN-11) whatever order they are added in.
        ButtonType action =
                new ButtonType(question.action(), withSave ? ButtonBar.ButtonData.NO : ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType(tr("confirm.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType save = new ButtonType(tr("confirm.save"), ButtonBar.ButtonData.YES);
        Alert alert = withSave
                ? new Alert(Alert.AlertType.CONFIRMATION, question.message(), action, save, cancel)
                : new Alert(Alert.AlertType.CONFIRMATION, question.message(), action, cancel);
        alert.setTitle(question.title());
        alert.setHeaderText(question.header());
        if (extra != null) {
            Label message = new Label(question.message());
            message.setWrapText(true);
            alert.getDialogPane().setContent(UiZoom.vbox(12, message, extra));
        }
        Button act = (Button) alert.getDialogPane().lookupButton(action);
        act.getStyleClass().add("danger");
        act.setDefaultButton(false);
        // NO is a cancel button to JavaFX: Escape must never answer Discard.
        act.setCancelButton(false);
        Button keep = (Button) alert.getDialogPane().lookupButton(cancel);
        keep.setDefaultButton(!withSave);
        if (withSave) {
            Button saving = (Button) alert.getDialogPane().lookupButton(save);
            saving.getStyleClass().add("success");
            saving.setDefaultButton(true);
        }
        return Dialogs.styled(alert, owner);
    }

    /** Which answer a button is: a close by any other route is Cancel. */
    static Answer answer(Optional<ButtonType> pressed) {
        return switch (pressed.map(ButtonType::getButtonData).orElse(ButtonBar.ButtonData.CANCEL_CLOSE)) {
            case OK_DONE, NO -> Answer.ACTION;
            case YES -> Answer.SAVE;
            default -> Answer.CANCEL;
        };
    }
}
