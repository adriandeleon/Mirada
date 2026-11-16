package com.mirada.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import javafx.scene.AccessibleAttribute;
import javafx.scene.control.ListView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;

import org.jspecify.annotations.Nullable;

import static com.mirada.i18n.Messages.tr;

/**
 * The one definition of how a list overlay is driven from the keyboard, and of the legend that tells
 * the user so: the command palette, the pickers on its card, the Message Log and the Bookmarks list
 * all move, accept and cancel by these keys (PAL-06, QOPEN-06, QOPEN-12).
 *
 * <p>The arrows, PageUp/PageDown, Ctrl/Cmd+Home and End, Enter and Escape always work. On top of
 * those the active keymap's own moves do, its {@code lists} section ({@link Keymap#lists}): in Emacs
 * {@code C-n}/{@code C-p} a row, {@code C-v}/{@code M-v} a page, {@code M-<}/{@code M->} the ends, as
 * in Editora. Its cancel chord, a key of {@link Command#ESCAPE} ({@code C-g} in Emacs), closes the
 * overlay as Escape does (PAL-07, QOPEN-07, MLOG-03). Nothing here is a fixed table of letters: a
 * keymap that binds no moves leaves the fixed keys alone.
 *
 * <p>The legend is built from the same keys, so it can only name keys that reach the overlay.
 */
final class PaletteKeys {

    /** What a key press means to the palette. */
    enum Action {
        NONE,
        UP,
        DOWN,
        PAGE_UP,
        PAGE_DOWN,
        FIRST,
        LAST,
        ACCEPT,
        CANCEL
    }

    /** The list moves a keymap binds in its {@code lists} section. */
    static final String LINE_DOWN = "nav.lineDown";

    static final String LINE_UP = "nav.lineUp";
    static final String PAGE_DOWN = "nav.pageDown";
    static final String PAGE_UP = "nav.pageUp";
    static final String FIRST = "nav.first";
    static final String LAST = "nav.last";

    /** Rows moved by PageUp/PageDown when the list cannot say how many it shows. */
    private static final int DEFAULT_PAGE = 8;

    private static final String SEPARATOR = "  ·  ";

    private PaletteKeys() {}

    /**
     * The action for a key press under the keys in force. {@code queryField} says whether a text
     * field has the focus: then plain Home/End stay with its caret.
     */
    static Action action(KeyEvent e, boolean queryField) {
        return resolve(KeyChord.of(e), queryField, Keybindings.current());
    }

    /**
     * The pure decision behind {@link #action}: the fixed keys first, then the keymap's list moves
     * and its cancel chord. Plain Home/End move the query field's caret when there is one (as in
     * every search box); Ctrl/Cmd+Home and Ctrl/Cmd+End always jump to the first/last row.
     */
    static Action resolve(@Nullable String token, boolean queryField, Keybindings keys) {
        if (token == null) return Action.NONE;
        switch (token) {
            case "down":
                return Action.DOWN;
            case "up":
                return Action.UP;
            case "pagedown":
                return Action.PAGE_DOWN;
            case "pageup":
                return Action.PAGE_UP;
            case "enter":
                return Action.ACCEPT;
            case "escape":
                return Action.CANCEL;
            case "C-home", "Cmd-home", "Cmd-up":
                return Action.FIRST;
            case "C-end", "Cmd-end", "Cmd-down":
                return Action.LAST;
            case "home":
                return queryField ? Action.NONE : Action.FIRST;
            case "end":
                return queryField ? Action.NONE : Action.LAST;
            default:
                break;
        }
        String move = keys.listMove(token);
        if (move != null) {
            return switch (move) {
                case LINE_DOWN -> Action.DOWN;
                case LINE_UP -> Action.UP;
                case PAGE_DOWN -> Action.PAGE_DOWN;
                case PAGE_UP -> Action.PAGE_UP;
                case FIRST -> Action.FIRST;
                case LAST -> Action.LAST;
                default -> Action.NONE;
            };
        }
        return keys.cancels(token) ? Action.CANCEL : Action.NONE;
    }

    /**
     * The row a navigation action lands on, or -1 when {@code action} is not navigation or the list
     * is empty. Up/Down wrap around; the page and first/last jumps stop at the ends. {@code current}
     * may be -1 (nothing selected). Pure.
     */
    static int target(Action action, int current, int size, int page) {
        if (size <= 0) return -1;
        int step = Math.max(1, page);
        return switch (action) {
            case DOWN -> current < 0 ? 0 : Math.floorMod(current + 1, size);
            case UP -> current < 0 ? size - 1 : Math.floorMod(current - 1, size);
            case PAGE_DOWN -> Math.min(size - 1, Math.max(0, current) + step);
            case PAGE_UP -> Math.max(0, Math.max(0, current) - step);
            case FIRST -> 0;
            case LAST -> size - 1;
            default -> -1;
        };
    }

    /** How many rows a page moves: one less than the list shows, so a row of context remains. */
    static int page(ListView<?> list) {
        double cell = list.getFixedCellSize();
        if (cell > 0 && list.getHeight() > cell) return Math.max(1, (int) (list.getHeight() / cell) - 1);
        return DEFAULT_PAGE;
    }

    /**
     * Applies a navigation action to {@code list}, landing only on rows {@code selectable} accepts
     * (so the cursor steps over greyed-out rows). Up/Down wrap; a page or first/last jump lands on
     * the nearest selectable row at or beyond its target, else the nearest before it. Returns false —
     * leaving the event for someone else — only when {@code action} is not navigation.
     */
    static <T> boolean navigate(ListView<T> list, Action action, Predicate<? super T> selectable) {
        if (!isNavigation(action)) return false;
        List<T> items = list.getItems();
        int size = items.size();
        int current = list.getSelectionModel().getSelectedIndex();
        int idx = -1;
        if (action == Action.UP || action == Action.DOWN) {
            int dir = action == Action.DOWN ? 1 : -1;
            int start = current >= 0 ? current : dir > 0 ? -1 : size;
            for (int step = 1; step <= size && idx < 0; step++) {
                int i = Math.floorMod(start + dir * step, size);
                idx = selectable.test(items.get(i)) ? i : -1;
            }
        } else if (size > 0) {
            int target = target(action, current, size, page(list));
            int dir = action == Action.PAGE_DOWN || action == Action.FIRST ? 1 : -1;
            idx = seek(items, target, dir, selectable);
            if (idx < 0) idx = seek(items, target, -dir, selectable);
        }
        if (idx >= 0) select(list, idx);
        return true;
    }

    /**
     * Puts the cursor on row {@code index} (-1 clears it) and tells a screen reader, since the focus
     * stays in the query field: the list's focused item is the row, and the list says it moved
     * (ACC-08).
     */
    static void select(ListView<?> list, int index) {
        if (index < 0) {
            list.getSelectionModel().clearSelection();
            return;
        }
        list.getSelectionModel().clearAndSelect(index);
        list.getFocusModel().focus(index);
        list.scrollTo(index);
        list.notifyAccessibleAttributeChanged(AccessibleAttribute.FOCUS_ITEM);
    }

    private static <T> int seek(List<T> items, int from, int dir, Predicate<? super T> selectable) {
        for (int i = from; i >= 0 && i < items.size(); i += dir) {
            if (selectable.test(items.get(i))) return i;
        }
        return -1;
    }

    static boolean isNavigation(Action action) {
        return switch (action) {
            case UP, DOWN, PAGE_UP, PAGE_DOWN, FIRST, LAST -> true;
            default -> false;
        };
    }

    // ---------------------------------------------------------------- legend

    /**
     * One legend entry: the keys that trigger it, then what they do ("↵ run"), or null when there are none (the entry is then left out of the legend).
     */
    static @Nullable String hint(String what, String keys) {
        return keys == null || keys.isBlank() ? null : tr("keys.hint", keys, what);
    }

    /**
     * The palette legend: "move", the palette's own entries (nulls skipped), "cancel". The keymap's
     * moves and cancel chord are shown beside the fixed keys in the platform's notation, which is
     * what the menus use; a keymap with none shows the fixed keys alone. Read again when the keymap
     * changes (PAL-09).
     */
    static String legend(Keybindings keys, @Nullable String... entries) {
        List<String> parts = new ArrayList<>();
        StringBuilder moves = new StringBuilder("↑↓");
        List<String> down = keys.listKeys(LINE_DOWN);
        List<String> up = keys.listKeys(LINE_UP);
        if (!down.isEmpty() && !up.isEmpty()) {
            moves.append(" / ")
                    .append(KeyChord.display(down.getFirst()))
                    .append(' ')
                    .append(KeyChord.display(up.getFirst()));
        }
        parts.add(hint(tr("keys.move"), moves.toString()));
        for (String entry : entries) {
            if (entry != null) parts.add(entry);
        }
        StringBuilder cancel = new StringBuilder("Esc");
        for (KeyCombination chord : keys.chords(Command.ESCAPE)) {
            if (!ESCAPE.equals(chord)) cancel.append(" / ").append(chord.getDisplayText());
        }
        parts.add(hint(tr("keys.cancel"), cancel.toString()));
        return String.join(SEPARATOR, parts);
    }

    private static final KeyCombination ESCAPE = new KeyCodeCombination(KeyCode.ESCAPE);
}
