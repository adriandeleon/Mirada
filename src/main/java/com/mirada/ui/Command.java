package com.mirada.ui;

import java.util.Arrays;
import java.util.List;

import javafx.scene.input.KeyCharacterCombination;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;

import org.jspecify.annotations.Nullable;

/**
 * Everything Mirada can do: which keys do it, and how the command palette names and describes it.
 * The key filter, the palette and the context menu all read from here, so a command has one name
 * and one chord wherever it appears.
 */
enum Command {
    NEXT("Next Image", "Shows the next image in the folder.", "go navigate forward", key(KeyCode.RIGHT)),
    PREVIOUS("Previous Image", "Shows the previous image in the folder.", "go navigate back", key(KeyCode.LEFT)),
    FIRST("First Image", "Jumps to the first image in the folder.", "go navigate start", key(KeyCode.HOME)),
    LAST("Last Image", "Jumps to the last image in the folder.", "go navigate end", key(KeyCode.END)),
    ZOOM_IN("Zoom In", "Zooms to the next step, about the centre of the window.", "view magnify", text("+")),
    ZOOM_OUT("Zoom Out", "Zooms to the previous step, about the centre of the window.", "view", text("-")),
    FIT("Zoom to Fit", "Shows the whole image, fitted to the window.", "view", key(KeyCode.DIGIT0)),
    ACTUAL_SIZE(
            "Actual Size",
            "Shows the image at 100 %: one image pixel per screen pixel.",
            "view zoom 100",
            key(KeyCode.DIGIT1)),
    ROTATE_CLOCKWISE(
            "Rotate Right", "Turns the image a quarter clockwise. The file is not changed.", "turn", key(KeyCode.R)),
    ROTATE_COUNTERCLOCKWISE(
            "Rotate Left",
            "Turns the image a quarter counterclockwise. The file is not changed.",
            "turn",
            new KeyCodeCombination(KeyCode.R, KeyCombination.SHIFT_DOWN)),
    FULL_SCREEN("Full Screen", "Fills the screen with the image. Escape leaves.", "view", key(KeyCode.F)),
    /** Leaves full screen, or closes the window: look, then go. */
    ESCAPE("Escape", "Leaves full screen, or closes the window.", "", key(KeyCode.ESCAPE)),
    OPEN("Open…", "Opens an image, with its folder, or a folder.", "file", shortcut(KeyCode.O)),
    CLOSE("Close Window", "Closes Mirada.", "quit exit", shortcut(KeyCode.W)),
    PALETTE(
            "Command Palette",
            "Finds and runs any command by name.",
            "",
            new KeyCodeCombination(KeyCode.P, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN));

    private final String label;
    private final String description;
    private final String keywords;
    private final KeyCombination chord;

    Command(String label, String description, String keywords, KeyCombination chord) {
        this.label = label;
        this.description = description;
        this.keywords = keywords;
        this.chord = chord;
    }

    String label() {
        return label;
    }

    /** One line, shown under the palette's list for the highlighted command. */
    String description() {
        return description;
    }

    /** Words the palette also finds the command by, besides its label. */
    String keywords() {
        return keywords;
    }

    /** The chord shown beside the command: its main key, one of those {@link #forKey} accepts. */
    KeyCombination chord() {
        return chord;
    }

    /**
     * Why this command cannot run right now, or null when it can: stepping needs somewhere to step
     * to, and zooming or turning needs an image on screen. Shown as the greyed row's tooltip in the
     * palette.
     */
    @Nullable
    String unavailable(int index, int size, boolean imageShowing) {
        return switch (this) {
            case NEXT, LAST -> size == 0 ? "No folder is open." : index >= size - 1 ? "This is the last image." : null;
            case PREVIOUS, FIRST -> size == 0 ? "No folder is open." : index <= 0 ? "This is the first image." : null;
            case ZOOM_IN, ZOOM_OUT, FIT, ACTUAL_SIZE, ROTATE_CLOCKWISE, ROTATE_COUNTERCLOCKWISE ->
                imageShowing ? null : "No image is showing.";
            default -> null;
        };
    }

    /** The commands the palette lists, in the order it lists them before anything is typed. */
    static List<Command> inPalette() {
        return Arrays.stream(values())
                .filter(command -> command != ESCAPE && command != PALETTE)
                .toList();
    }

    /**
     * The command for a key press, or null when the key is not one of Mirada's. {@code shortcut}
     * is Ctrl, or Cmd on macOS. Navigation and zoom take bare keys: there is no text to type into.
     */
    static @Nullable Command forKey(KeyCode code, boolean shift, boolean shortcut) {
        if (shortcut) {
            return switch (code) {
                case O -> OPEN;
                case W, Q -> CLOSE;
                case P -> shift ? PALETTE : null;
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
            case F1 -> PALETTE;
            case ESCAPE -> ESCAPE;
            default -> null;
        };
    }

    private static KeyCombination key(KeyCode code) {
        return new KeyCodeCombination(code);
    }

    private static KeyCombination shortcut(KeyCode code) {
        return new KeyCodeCombination(code, KeyCombination.SHORTCUT_DOWN);
    }

    /** For keys named by the character they type, whose key code differs by keyboard layout. */
    private static KeyCombination text(String character) {
        return new KeyCharacterCombination(character);
    }
}
