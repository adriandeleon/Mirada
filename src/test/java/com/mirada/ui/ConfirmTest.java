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
            Confirm.removeMark("beach.jpg", true),
            Confirm.removeMark("beach.jpg", false),
            Confirm.discardEdit("beach.jpg"),
            Confirm.replaceFile("beach.jpg"));

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
        assertTrue(Confirm.removeMark("a", true).message().contains("cannot be undone"));
        assertFalse(Confirm.discardEdit("a").message().contains("cannot be undone"));
    }

    @Test
    void theRemoveQuestionSaysWhatGoes() {
        assertEquals(
                "Remove the bookmark and note on a.jpg?",
                Confirm.removeMark("a.jpg", true).header());
        assertEquals(
                "Remove the note on a.jpg?", Confirm.removeMark("a.jpg", false).header());
    }

    @Test
    void anythingButTheActionIsCancel() {
        assertTrue(Confirm.confirmed(Optional.of(new ButtonType("Discard", ButtonBar.ButtonData.OK_DONE))));
        assertFalse(Confirm.confirmed(Optional.of(new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE))));
        assertFalse(Confirm.confirmed(Optional.empty()));
    }
}
