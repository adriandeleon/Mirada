package com.mirada.ui;

import java.util.List;
import java.util.Optional;

import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfirmTest {

    private static final List<Confirm.Question> ALL = List.of(
            Confirm.discardEdit("beach.jpg"),
            Confirm.replaceFile("beach.jpg"),
            Confirm.importConfiguration("beach.jpg"));

    @Test
    void everyQuestionNamesTheThing() {
        for (Confirm.Question q : ALL) assertTrue(q.header().contains("beach.jpg"), q.header());
    }

    @Test
    void theButtonIsTheActionsVerbNeverOkOrYes() {
        for (Confirm.Question q : ALL) {
            assertFalse(List.of("OK", "Yes").contains(q.action()), q.action());
            assertTrue(q.header().startsWith(q.action()), q.header() + " / " + q.action());
        }
    }

    @Test
    void onlyWhatCannotBeUndoneSaysSo() {
        assertTrue(Confirm.importConfiguration("a").message().contains("cannot be undone"));
        assertFalse(Confirm.discardEdit("a").message().contains("cannot be undone"));
    }

    @Test
    void anythingButTheActionOrSaveIsCancel() {
        assertEquals(
                Confirm.Answer.ACTION,
                Confirm.answer(Optional.of(new ButtonType("Replace", ButtonBar.ButtonData.OK_DONE))));
        assertEquals(
                Confirm.Answer.ACTION, Confirm.answer(Optional.of(new ButtonType("Discard", ButtonBar.ButtonData.NO))));
        assertEquals(
                Confirm.Answer.SAVE, Confirm.answer(Optional.of(new ButtonType("Save", ButtonBar.ButtonData.YES))));
        assertEquals(
                Confirm.Answer.CANCEL,
                Confirm.answer(Optional.of(new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE))));
        assertEquals(Confirm.Answer.CANCEL, Confirm.answer(Optional.empty()), "Escape or the close button");
    }

    /** CONF-12: a test answers a question without a dialog, and sees the words it would have shown. */
    @Test
    void aTestAnswersAQuestionWithoutADialog() {
        List<Confirm.Question> asked = new java.util.ArrayList<>();
        try {
            Confirm.answeredBy(q -> {
                asked.add(q);
                return Confirm.Answer.SAVE;
            });
            assertEquals(Confirm.Answer.SAVE, Confirm.askOrSave(Confirm.discardEdit("a.jpg"), null));
            assertFalse(Confirm.ask(Confirm.replaceFile("b.jpg"), null), "Save is no action: a Cancel");
            Confirm.answeredBy(q -> Confirm.Answer.ACTION);
            assertTrue(Confirm.ask(Confirm.replaceFile("b.jpg"), null));
        } finally {
            Confirm.answeredBy(null);
        }
        assertEquals(
                List.of("Discard the edit to a.jpg?", "Replace b.jpg?"),
                asked.stream().map(Confirm.Question::header).toList());
    }
}
