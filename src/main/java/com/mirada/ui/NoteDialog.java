package com.mirada.ui;

import java.util.Optional;

import javafx.application.Platform;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import static com.mirada.i18n.Messages.tr;

/**
 * Asks for the note on an image or a folder: a few lines of the user's own, Editora's personal note
 * moved to pictures. Return starts a new line, as in any note; Ctrl+Return (Cmd+Return on a Mac)
 * saves, as does the Save button; Escape leaves the note as it was. Emptied, the note goes.
 */
final class NoteDialog {

    private NoteDialog() {}

    /**
     * The note as the user leaves it, or empty when they cancel.
     *
     * @param about what the note is on, for the header: "IMG_0412.jpg", "the folder Holiday"
     */
    static Optional<String> ask(Window owner, String title, String about, String note, String stylesheet) {
        Dialog<String> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle(title);
        dialog.setHeaderText(tr("note.header", about));
        dialog.setResizable(true);
        ButtonType save = new ButtonType(tr("note.save"), ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType(tr("note.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(save, cancel);
        dialog.getDialogPane().getStylesheets().add(stylesheet);
        dialog.getDialogPane().getStyleClass().add("note-dialog");

        TextArea text = new TextArea(note);
        text.setWrapText(true);
        text.setPrefColumnCount(40);
        text.setPrefRowCount(6);
        // The dialog pane sizes its content by the minimum, which a text area puts at two lines.
        text.setMinHeight(140);
        VBox.setVgrow(text, Priority.ALWAYS);
        text.setPromptText(tr("note.prompt"));
        String saveKey = (Command.MAC ? "⌘" : "Ctrl+") + "Return";
        Label hint = new Label(tr("note.hint", saveKey));
        hint.getStyleClass().add("note-hint");
        dialog.getDialogPane().setContent(new VBox(8, text, hint));
        text.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ENTER && e.isShortcutDown()) {
                e.consume();
                dialog.setResult(text.getText());
                dialog.close();
            }
        });
        dialog.setResultConverter(button -> button == save ? text.getText() : null);
        Platform.runLater(() -> {
            text.requestFocus();
            text.end();
        });
        return dialog.showAndWait();
    }
}
