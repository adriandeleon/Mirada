package com.mirada.ui;

import javafx.scene.input.KeyCode;

import org.jspecify.annotations.Nullable;

/** Everything the keyboard can do, and which keys do it. */
enum Command {
    NEXT,
    PREVIOUS,
    FIRST,
    LAST,
    ZOOM_IN,
    ZOOM_OUT,
    FIT,
    ACTUAL_SIZE,
    ROTATE_CLOCKWISE,
    ROTATE_COUNTERCLOCKWISE,
    FULL_SCREEN,
    /** Leaves full screen, or closes the window: look, then go. */
    ESCAPE,
    OPEN,
    CLOSE;

    /**
     * The command for a key press, or null when the key is not one of Mirada's. {@code shortcut}
     * is Ctrl, or Cmd on macOS. Navigation and zoom take bare keys: there is no text to type into.
     */
    static @Nullable Command forKey(KeyCode code, boolean shift, boolean shortcut) {
        if (shortcut) {
            return switch (code) {
                case O -> OPEN;
                case W, Q -> CLOSE;
                case EQUALS, PLUS, ADD -> ZOOM_IN;
                case MINUS, SUBTRACT -> ZOOM_OUT;
                case DIGIT0, NUMPAD0 -> FIT;
                case DIGIT1, NUMPAD1 -> ACTUAL_SIZE;
                default -> null;
            };
        }
        return switch (code) {
            case RIGHT, PAGE_DOWN -> NEXT;
            case LEFT, PAGE_UP, BACK_SPACE -> PREVIOUS;
            case SPACE -> shift ? PREVIOUS : NEXT;
            case HOME -> FIRST;
            case END -> LAST;
            case EQUALS, PLUS, ADD -> ZOOM_IN;
            case MINUS, SUBTRACT -> ZOOM_OUT;
            case DIGIT0, NUMPAD0 -> FIT;
            case DIGIT1, NUMPAD1 -> ACTUAL_SIZE;
            case R -> shift ? ROTATE_COUNTERCLOCKWISE : ROTATE_CLOCKWISE;
            case F, F11 -> FULL_SCREEN;
            case ESCAPE -> ESCAPE;
            default -> null;
        };
    }
}
