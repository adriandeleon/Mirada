package com.mirada.edit;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * Undo and redo over a value that is replaced whole at each step (the editor's {@link Edit} and
 * shape). A step can belong to a run — the arrow keys nudging the crop, say — and the steps of one
 * run undo together, as one.
 */
public final class History<T> {

    /** Steps kept to undo; the oldest go first. */
    static final int LIMIT = 200;

    private final Deque<T> undo = new ArrayDeque<>();
    private final Deque<T> redo = new ArrayDeque<>();
    private T current;
    private @Nullable Object run;

    public History(T start) {
        current = start;
    }

    public T current() {
        return current;
    }

    /** Starts again from {@code start}, with nothing to undo or redo. */
    public void reset(T start) {
        undo.clear();
        redo.clear();
        current = start;
        run = null;
    }

    /** {@code next} is now the value, a step of its own; nothing happens if it is the value already. */
    public void record(T next) {
        record(next, null);
    }

    /**
     * {@code next} is now the value. In the same {@code run} as the step before it (by {@code
     * equals}, null being no run), it joins that step: one undo takes back the whole run.
     */
    public void record(T next, @Nullable Object inRun) {
        if (next.equals(current)) return;
        if (inRun == null || !inRun.equals(run)) {
            undo.push(current);
            if (undo.size() > LIMIT) undo.removeLast();
        }
        current = next;
        run = inRun;
        redo.clear();
    }

    public boolean canUndo() {
        return !undo.isEmpty();
    }

    public boolean canRedo() {
        return !redo.isEmpty();
    }

    /** The value before the last step, now the value; the same value when there is nothing to undo. */
    public T undo() {
        if (undo.isEmpty()) return current;
        redo.push(current);
        current = undo.pop();
        run = null;
        return current;
    }

    /** The value the last undo took back, now the value again; the same value when there is none. */
    public T redo() {
        if (redo.isEmpty()) return current;
        undo.push(current);
        current = redo.pop();
        run = null;
        return current;
    }

    @Override
    public String toString() {
        return "History[" + undo.size() + " back, " + redo.size() + " forward, at " + Objects.toString(current) + "]";
    }
}
