package com.mirada.edit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HistoryTest {

    @Test
    void undoesAndRedoesStepByStep() {
        History<String> history = new History<>("a");
        assertFalse(history.canUndo());
        history.record("b");
        history.record("c");
        assertEquals("b", history.undo());
        assertEquals("a", history.undo());
        assertFalse(history.canUndo());
        assertEquals("a", history.undo(), "nothing left to undo: the value stays");
        assertEquals("b", history.redo());
        assertEquals("c", history.redo());
        assertFalse(history.canRedo());
        assertEquals("c", history.redo());
    }

    @Test
    void aNewStepAfterUndoingDropsWhatCouldBeRedone() {
        History<String> history = new History<>("a");
        history.record("b");
        history.undo();
        history.record("x");
        assertFalse(history.canRedo());
        assertEquals("a", history.undo());
    }

    @Test
    void theSameValueAgainIsNoStep() {
        History<String> history = new History<>("a");
        history.record("a");
        assertFalse(history.canUndo());
    }

    @Test
    void aRunUndoesAsOneAndEndsAtTheFirstStepOutsideIt() {
        History<String> history = new History<>("start");
        history.record("left 1", "move");
        history.record("left 2", "move");
        history.record("left 3", "move");
        assertEquals("start", history.undo(), "three presses, one undo");
        history.redo();
        history.record("turned");
        history.record("left 4", "move");
        assertEquals("turned", history.undo(), "a run broken by another step starts again");
    }

    @Test
    void aRunDoesNotJoinAcrossAnUndo() {
        History<String> history = new History<>("start");
        history.record("left 1", "move");
        history.undo();
        history.record("left 1", "move");
        history.record("left 2", "move");
        assertEquals("start", history.undo());
        assertTrue(history.canRedo());
    }

    @Test
    void keepsALimitedNumberOfSteps() {
        History<Integer> history = new History<>(0);
        for (int i = 1; i <= History.LIMIT + 50; i++) history.record(i);
        int undone = 0;
        while (history.canUndo()) {
            history.undo();
            undone++;
        }
        assertEquals(History.LIMIT, undone);
        assertEquals(50, history.current());
    }

    @Test
    void resetForgetsEverything() {
        History<String> history = new History<>("a");
        history.record("b");
        history.undo();
        history.reset("z");
        assertFalse(history.canUndo());
        assertFalse(history.canRedo());
        assertEquals("z", history.current());
    }
}
