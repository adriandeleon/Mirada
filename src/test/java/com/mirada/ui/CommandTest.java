package com.mirada.ui;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;

import com.mirada.settings.Settings;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The registry and the keys: ids, the bundled keymaps, their layers, and where each key works. */
class CommandTest {

    private static final Keybindings PC = Keybindings.layered(Keymap.load("cua", false), false, Map.of());
    private static final Keybindings PC_SIDE = Keybindings.layered(Keymap.load("cua", false), true, Map.of());
    private static final Keybindings MAC = Keybindings.layered(Keymap.load("cua", true), false, Map.of());
    private static final Keybindings EMACS = Keybindings.layered(Keymap.load("emacs", false), false, Map.of());

    /** A press on Linux or Windows, in the viewer. */
    private static @Nullable Command key(String token) {
        return PC.commandFor(Command.Context.VIEWER, token);
    }

    /** A press on Linux or Windows with the thumbnails down the side. */
    private static @Nullable Command side(String token) {
        return PC_SIDE.commandFor(Command.Context.VIEWER, token);
    }

    /** A press on macOS: {@code Cmd-} is Command, {@code C-} the Ctrl key. */
    private static @Nullable Command mac(String token) {
        return MAC.commandFor(Command.Context.VIEWER, token);
    }

    // --- ids (KBD-02) ---

    @Test
    void everyIdIsDottedAndGroupedByArea() {
        Pattern shape = Pattern.compile("[a-z]+\\.[a-z][a-zA-Z0-9]*");
        Set<String> seen = new HashSet<>();
        for (Command command : Command.values()) {
            assertTrue(shape.matcher(command.id()).matches(), command.id() + " is not <area>.<camelCaseName>");
            assertTrue(seen.add(command.id()), command.id() + " twice");
            assertEquals(command, Command.byId(command.id()));
        }
        assertNull(Command.byId("go.nowhere"));
    }

    // --- the keymaps (KBD-09) ---

    @Test
    void theEmacsAndCuaKeymapsShipWithAMacCua() {
        for (String file : List.of("emacs.json", "cua.json", "cua.mac.json")) {
            assertNotNull(Keymap.class.getResource("/com/mirada/keymaps/" + file), file);
        }
        assertEquals(List.of("cua", "emacs"), Keymap.PRESETS);
        assertEquals(Settings.DEFAULT_KEYMAP, Keymap.DEFAULT);
        assertEquals("Emacs", Keymap.load("emacs", false).name());
        assertEquals("cua", Keymap.resolve("vim"));
        assertEquals("cua", Keymap.resolve(null));
    }

    /** Every binding names a command, every key is a key, and no key is bound twice where it works. */
    @Test
    void everyKeymapBindsCommandsToKeysOnce() {
        for (String id : Keymap.PRESETS) {
            for (boolean mac : new boolean[] {false, true}) {
                Keymap keymap = Keymap.load(id, mac);
                for (boolean vertical : new boolean[] {false, true}) {
                    Map<String, List<String>> bindings = new java.util.LinkedHashMap<>(keymap.bindings());
                    if (vertical) bindings.putAll(keymap.vertical());
                    Keybindings keys = Keybindings.layered(keymap, vertical, Map.of());
                    String where = id + (mac ? ".mac" : "") + (vertical ? ", strip at the side" : "");
                    bindings.forEach((command, tokens) -> {
                        Command named = Command.byId(command);
                        assertNotNull(named, command + " in " + where);
                        for (String token : tokens) {
                            assertNotNull(Keybindings.canonical(token), token + " in " + where);
                            assertEquals(named, keys.commandFor(named.context(), token), token + " in " + where);
                        }
                    });
                }
            }
        }
    }

    /** The chord a command shows, in the palette, the menus and the shortcuts window, must run it. */
    @Test
    void everyShownChordRunsItsCommand() {
        for (String id : Keymap.PRESETS) {
            for (boolean mac : new boolean[] {false, true}) {
                for (boolean vertical : new boolean[] {false, true}) {
                    Keybindings keys = Keybindings.layered(Keymap.load(id, mac), vertical, Map.of());
                    for (Command command : Command.values()) {
                        for (KeyCombination chord : keys.chords(command)) {
                            if (!(chord instanceof KeyCodeCombination code)) continue; // C-?: by its character
                            String token = KeyChord.token(
                                    code.getCode(),
                                    code.getControl() == KeyCombination.ModifierValue.DOWN,
                                    code.getAlt() == KeyCombination.ModifierValue.DOWN,
                                    code.getMeta() == KeyCombination.ModifierValue.DOWN,
                                    code.getShift() == KeyCombination.ModifierValue.DOWN);
                            assertNotNull(token);
                            assertEquals(
                                    command,
                                    keys.commandFor(command.context(), token),
                                    command.id() + " in " + id + (mac ? " on macOS" : "")
                                            + (vertical ? ", strip at the side" : ""));
                        }
                    }
                }
            }
        }
    }

    @Test
    void theKeymapsOpenThePaletteWithTheSettingsDefault() {
        for (String id : Keymap.PRESETS) {
            for (boolean mac : new boolean[] {false, true}) {
                // Emacs has no Mac variant: its C- is the Ctrl key there too, and palette.keys is the Mac's.
                if (mac && Keymap.class.getResource("/com/mirada/keymaps/" + id + ".mac.json") == null) continue;
                assertEquals(
                        Settings.defaultPaletteKeys(mac),
                        String.join(" ", Keymap.load(id, mac).bindings().get("palette.show")),
                        id + (mac ? ".mac" : ""));
            }
        }
    }

    @Test
    void aKeymapReadsOneKeyOrAList() throws Exception {
        Keymap keymap = Keymap.parse(
                "test", "{\"name\": \"Test\", \"bindings\": {\"go.next\": \"n\", \"go.previous\": [\"p\", \"b\"]}}");
        assertEquals("Test", keymap.name());
        assertEquals(List.of("n"), keymap.bindings().get("go.next"));
        assertEquals(List.of("p", "b"), keymap.bindings().get("go.previous"));
        assertTrue(keymap.vertical().isEmpty());
        org.junit.jupiter.api.Assertions.assertThrows(
                com.mirada.update.Json.Malformed.class, () -> Keymap.parse("bad", "{\"bindings\": {\"go.next\": 1}}"));
    }

    @Test
    void tokensAreReadInAnyModifierOrder() {
        assertEquals("C-S-z", Keybindings.canonical("S-C-z"));
        assertEquals("Cmd-S-p", Keybindings.canonical("Cmd-S-p"));
        assertEquals("C-?", Keybindings.canonical("C-S-?"));
        assertEquals("-", Keybindings.canonical("-"));
        assertNull(Keybindings.canonical("nonsense"));
        assertNull(Keybindings.canonical(" "));
        // The Mac's keymap has Cmd where the others have Ctrl, and JavaFX writes it as the symbol.
        assertEquals(Command.MAC ? "⌘?" : "Ctrl+?", Command.SHORTCUTS.chord().getDisplayText());
    }

    // --- the layers (KBD-11) ---

    @Test
    void theUsersKeysGoOnTopAndWin() {
        Keymap cua = Keymap.load("cua", false);
        // A command's keys are replaced, not added to.
        Keybindings mine = Keybindings.layered(cua, false, Map.of("go.next", "n"));
        assertEquals(Command.NEXT, mine.commandFor(Command.Context.VIEWER, "n"));
        assertNull(mine.commandFor(Command.Context.VIEWER, "right"));
        assertEquals(new KeyCodeCombination(KeyCode.N), mine.chord(Command.NEXT));
        // A key the keymap gives another command is taken from it; its other keys stay.
        Keybindings taken = Keybindings.layered(cua, false, Map.of("view.zoomIn", "r"));
        assertEquals(Command.ZOOM_IN, taken.commandFor(Command.Context.VIEWER, "r"));
        assertEquals(Command.ROTATE_CLOCKWISE, taken.commandFor(Command.Context.VIEWER, "C-r"));
        assertEquals(
                new KeyCodeCombination(KeyCode.R, KeyCombination.CONTROL_DOWN), taken.chord(Command.ROTATE_CLOCKWISE));
        // Only where the user's command works: the editor keeps its R.
        assertEquals(Command.EDITOR_ROTATE_RIGHT, taken.commandFor(Command.Context.EDITOR, "r"));
        // Blank unbinds; a typo changes nothing; an id that is no command is skipped.
        assertEquals(
                KeyCombination.NO_MATCH,
                Keybindings.layered(cua, false, Map.of("go.next", "")).chord(Command.NEXT));
        assertEquals(
                Command.NEXT,
                Keybindings.layered(cua, false, Map.of("go.next", "nonsense"))
                        .commandFor(Command.Context.VIEWER, "right"));
        assertEquals(
                Command.NEXT,
                Keybindings.layered(cua, false, Map.of("go.nowhere", "right"))
                        .commandFor(Command.Context.VIEWER, "right"));
    }

    @Test
    void thePalettesKeysComeFirstOfTheUsers() {
        Keybindings keys = Keybindings.layered(
                Keymap.load("cua", false), false, Map.of("palette.show", "C-o", "file.open", "C-o f3"));
        assertEquals(Command.PALETTE, keys.commandFor(Command.Context.VIEWER, "C-o"));
        assertEquals(Command.OPEN, keys.commandFor(Command.Context.VIEWER, "f3"));
    }

    @Test
    void theUsersKeysFollowTheStripsSide() {
        Keybindings keys = Keybindings.layered(Keymap.load("cua", false), true, Map.of("go.next", "n"));
        assertEquals(Command.NEXT, keys.commandFor(Command.Context.VIEWER, "n"));
        assertNull(keys.commandFor(Command.Context.VIEWER, "down"));
        assertEquals(Command.PREVIOUS, keys.commandFor(Command.Context.VIEWER, "up"));
    }

    // --- where keys work ---

    @Test
    void theEditorsKeysAreItsOwnWhileItIsOpen() {
        assertEquals(Command.EDITOR_ROTATE_RIGHT, PC.commandFor(Command.Context.EDITOR, "r"));
        assertEquals(Command.EDITOR_ROTATE_LEFT, PC.commandFor(Command.Context.EDITOR, "S-r"));
        assertEquals(Command.EDITOR_FLIP_HORIZONTALLY, PC.commandFor(Command.Context.EDITOR, "h"));
        assertEquals(Command.EDITOR_FLIP_VERTICALLY, PC.commandFor(Command.Context.EDITOR, "v"));
        assertEquals(Command.EDITOR_NEXT_SHAPE, PC.commandFor(Command.Context.EDITOR, "a"));
        assertEquals(Command.EDITOR_RESET, PC.commandFor(Command.Context.EDITOR, "backspace"));
        assertEquals(Command.EDITOR_SAVE, PC.commandFor(Command.Context.EDITOR, "enter"));
        assertEquals(Command.EDITOR_SAVE, PC.commandFor(Command.Context.EDITOR, "C-s"));
        assertEquals(Command.EDITOR_CANCEL, PC.commandFor(Command.Context.EDITOR, "escape"));
        assertEquals(Command.EDITOR_UNDO, PC.commandFor(Command.Context.EDITOR, "C-z"));
        assertEquals(Command.EDITOR_REDO, PC.commandFor(Command.Context.EDITOR, "C-S-z"));
        assertEquals(Command.EDITOR_REDO, PC.commandFor(Command.Context.EDITOR, "C-y"));
        assertEquals(Command.EDITOR_SAVE, MAC.commandFor(Command.Context.EDITOR, "Cmd-s"));
        // The arrows are the editor's own, to move the crop: no command takes them there.
        assertNull(PC.commandFor(Command.Context.EDITOR, "right"));
        // In the viewer the same keys are the viewer's.
        assertEquals(Command.PREVIOUS, key("backspace"));
        assertEquals(Command.ROTATE_CLOCKWISE, key("r"));
    }

    @Test
    void theBookmarksWindowsKeysAreItsOwnThere() {
        assertEquals(Command.BOOKMARKS_OPEN, PC.commandFor(Command.Context.BOOKMARKS, "enter"));
        assertEquals(Command.BOOKMARKS_EDIT_NOTE, PC.commandFor(Command.Context.BOOKMARKS, "f2"));
        assertEquals(Command.BOOKMARKS_TOGGLE, PC.commandFor(Command.Context.BOOKMARKS, "C-b"));
        assertEquals(Command.BOOKMARKS_REMOVE, PC.commandFor(Command.Context.BOOKMARKS, "C-delete"));
        assertEquals(Command.BOOKMARKS_REMOVE, MAC.commandFor(Command.Context.BOOKMARKS, "Cmd-backspace"));
        assertEquals(Command.BOOKMARKS_LIST_NOTES, PC.commandFor(Command.Context.BOOKMARKS, "C-3"));
        // Bare letters are the filter's, to type.
        assertNull(PC.commandFor(Command.Context.BOOKMARKS, "b"));
        assertEquals(Command.GO_TO_BOOKMARK, key("C-b"));
    }

    @Test
    void everyCommandWorksSomewhere() {
        for (Command command : Command.values()) {
            Command.Context context = command.context();
            assertNotNull(context);
            if (command.id().startsWith("editor.")) assertEquals(Command.Context.EDITOR, context, command.id());
            else if (command.id().startsWith("bookmarks."))
                assertEquals(Command.Context.BOOKMARKS, context, command.id());
            else assertEquals(Command.Context.VIEWER, context, command.id());
        }
    }

    // --- the palette (PAL-02) ---

    @Test
    void thePaletteListsEveryCommandWhereverItOpens() {
        for (Command.Context where : Command.Context.values()) {
            List<Command> listed = Command.inPalette(where);
            assertEquals(Set.of(Command.values()), Set.copyOf(listed), where.name());
            assertEquals(Command.values().length, listed.size(), where.name());
            // Those whose keys work there lead.
            assertEquals(where, listed.getFirst().context(), where.name());
        }
        assertTrue(Command.inPalette(Command.Context.VIEWER).contains(Command.ESCAPE));
        assertTrue(Command.inPalette(Command.Context.VIEWER).contains(Command.PALETTE));
        assertTrue(Command.inPalette(Command.Context.VIEWER).contains(Command.PAN_LEFT));
        assertEquals(
                Command.EDITOR_ROTATE_LEFT,
                Command.inPalette(Command.Context.EDITOR).getFirst());
        for (Command command : Command.values()) {
            assertFalse(command.label().isBlank(), command.name());
            assertFalse(command.description().isBlank(), command.name());
        }
    }

    // --- the viewer's keys, as before the keymaps ---

    @Test
    void arrowsAndPagesWalkTheFolder() {
        assertEquals(Command.NEXT, key("right"));
        assertEquals(Command.NEXT, key("pagedown"));
        assertEquals(Command.PREVIOUS, key("left"));
        assertEquals(Command.PREVIOUS, key("backspace"));
        assertEquals(Command.FIRST, key("home"));
        assertEquals(Command.LAST, key("end"));
    }

    @Test
    void spaceGoesForwardAndShiftSpaceBack() {
        assertEquals(Command.NEXT, key("space"));
        assertEquals(Command.PREVIOUS, key("S-space"));
    }

    @Test
    void zoomWorksBareAndWithTheShortcut() {
        for (String modifier : new String[] {"", "C-"}) {
            assertEquals(Command.ZOOM_IN, key(modifier + "="));
            assertEquals(Command.ZOOM_IN, key(modifier + "S-="));
            assertEquals(Command.ZOOM_IN, key(modifier + "plus"));
            assertEquals(Command.ZOOM_IN, key(modifier + "add"));
            assertEquals(Command.ZOOM_OUT, key(modifier + "-"));
            assertEquals(Command.ZOOM_OUT, key(modifier + "subtract"));
            assertEquals(Command.FIT, key(modifier + "0"));
            assertEquals(Command.ACTUAL_SIZE, key(modifier + "1"));
        }
        assertEquals("+", Command.ZOOM_IN.chord().getDisplayText());
        assertEquals("-", Command.ZOOM_OUT.chord().getDisplayText());
    }

    @Test
    void shiftReversesTheTurn() {
        assertEquals(Command.ROTATE_CLOCKWISE, key("r"));
        assertEquals(Command.ROTATE_COUNTERCLOCKWISE, key("S-r"));
    }

    @Test
    void windowCommandsNeedTheShortcut() {
        assertEquals(Command.OPEN, key("C-o"));
        assertEquals(Command.CLOSE, key("C-w"));
        assertEquals(Command.CLOSE, key("C-q"));
        assertEquals(Command.OPEN_FOLDER, key("C-S-f"));
        assertEquals(Command.OPEN_FOLDER, mac("Cmd-S-f"));
        // Ctrl+F alone is nothing; bare F is full screen, as is Ctrl-Cmd-F on a Mac.
        assertNull(key("C-f"));
        assertEquals(Command.FULL_SCREEN, mac("C-Cmd-f"));
        assertNull(key("o"));
        assertNull(key("w"));
        // The shortcut does not turn a navigation key into something else.
        assertNull(key("C-right"));
    }

    @Test
    void thePalettesKeysAreTheUsers() {
        Keybindings.use(Settings.DEFAULT_KEYMAP, Map.of(), "C-k");
        try {
            assertEquals(new KeyCodeCombination(KeyCode.K, KeyCombination.CONTROL_DOWN), Command.PALETTE.chord());
            assertEquals(List.of(Command.PALETTE.chord()), PaletteChords.current());
        } finally {
            Keybindings.use(Settings.DEFAULT_KEYMAP, Map.of(), Settings.defaultPaletteKeys(Command.MAC));
        }
        assertEquals(PaletteChords.current().getFirst(), Command.PALETTE.chord());
        assertEquals(Command.GO_TO_IMAGE, key("C-p"));
    }

    @Test
    void withTheStripDownTheSideUpAndDownStepAndTheArrowsAcrossPan() {
        assertEquals(Command.NEXT, side("down"));
        assertEquals(Command.PREVIOUS, side("up"));
        assertEquals(Command.PAN_DOWN, side("S-down"));
        assertEquals(Command.PAN_UP, side("S-up"));
        assertEquals(Command.PAN_LEFT, side("left"));
        assertEquals(Command.PAN_RIGHT, side("right"));
        assertEquals(Command.PAN_RIGHT, side("S-right"));
        // The keys that step whichever way the strip stands still do.
        assertEquals(Command.NEXT, side("pagedown"));
        assertEquals(Command.PREVIOUS, side("backspace"));
        assertEquals(Command.NEXT, side("space"));
        assertEquals(new KeyCodeCombination(KeyCode.DOWN), PC_SIDE.chord(Command.NEXT));
        assertEquals(
                new KeyCodeCombination(KeyCode.UP),
                Keybindings.layered(Keymap.load("cua", true), true, Map.of()).chord(Command.PREVIOUS));
        // Along the bottom, as before.
        assertEquals(Command.NEXT, key("right"));
        assertEquals(Command.PAN_DOWN, key("down"));
        assertEquals(new KeyCodeCombination(KeyCode.RIGHT), PC.chord(Command.NEXT));
    }

    @Test
    void theShownChordFollowsTheStrip() {
        boolean was = Command.vertical();
        try {
            Command.setVertical(true);
            assertEquals(new KeyCodeCombination(KeyCode.DOWN), Command.NEXT.chord());
            Command.setVertical(false);
            assertEquals(new KeyCodeCombination(KeyCode.RIGHT), Command.NEXT.chord());
        } finally {
            Command.setVertical(was);
        }
    }

    @Test
    void aMacTrashesWithCommandBackspaceAndBackspaceAloneStillStepsBack() {
        assertEquals(Command.MOVE_TO_TRASH, mac("Cmd-backspace"));
        assertEquals(Command.PREVIOUS, mac("backspace"));
        assertEquals(Command.MOVE_TO_TRASH, mac("delete"));
        assertEquals(
                new KeyCodeCombination(KeyCode.BACK_SPACE, KeyCombination.META_DOWN), MAC.chord(Command.MOVE_TO_TRASH));
        // Elsewhere Ctrl-Backspace is a text key, never a delete.
        assertNull(key("C-backspace"));
    }

    @Test
    void aMacHasPreviewsAndFindersKeys() {
        assertEquals(Command.RENAME, mac("enter"));
        assertEquals(Command.INFO, mac("Cmd-i"));
        assertEquals(Command.ROTATE_CLOCKWISE, mac("Cmd-r"));
        assertEquals(Command.ROTATE_COUNTERCLOCKWISE, mac("Cmd-l"));
        assertEquals(Command.FULL_SCREEN, mac("C-Cmd-f"));
        // Ctrl-Cmd takes nothing else.
        assertNull(mac("C-Cmd-o"));
        // The function keys still work for those who have them.
        assertEquals(Command.RENAME, mac("f2"));
        assertEquals(new KeyCodeCombination(KeyCode.ENTER), MAC.chord(Command.RENAME));
    }

    @Test
    void returnAndTheMacChordsAreNotOursElsewhere() {
        assertNull(key("enter"));
        assertNull(key("C-l"));
        assertNull(key("C-i"));
        assertEquals(new KeyCodeCombination(KeyCode.F2), PC.chord(Command.RENAME));
        assertEquals(new KeyCodeCombination(KeyCode.DELETE), PC.chord(Command.MOVE_TO_TRASH));
    }

    @Test
    void ctrlRTurnsEverywhere() {
        assertEquals(Command.ROTATE_CLOCKWISE, key("C-r"));
        assertEquals(Command.ROTATE_COUNTERCLOCKWISE, key("C-S-r"));
    }

    @Test
    void theShortcutCCopiesEverywhere() {
        assertEquals(Command.COPY, key("C-c"));
        assertEquals(Command.COPY, mac("Cmd-c"));
        assertNull(key("c"));
    }

    @Test
    void fileKeysAreTheDesktopsOwn() {
        assertEquals(Command.MOVE_TO_TRASH, key("delete"));
        assertEquals(Command.RENAME, key("f2"));
        assertEquals(Command.SLIDESHOW, key("f5"));
        assertEquals(Command.SHORTCUTS, key("C-S-/"));
        assertEquals(Command.SHORTCUTS, key("C-?"));
        assertEquals(Command.KEEP_ZOOM, key("z"));
    }

    @Test
    void shiftArrowsPanAndBareArrowsStep() {
        assertEquals(Command.PAN_LEFT, key("S-left"));
        assertEquals(Command.PAN_RIGHT, key("S-right"));
        assertEquals(Command.PAN_UP, key("up"));
        assertEquals(Command.PAN_DOWN, key("S-down"));
        assertEquals(Command.NEXT, key("right"));
    }

    @Test
    void bookmarksAndNotesAreBAndNFoldersWithShift() {
        assertEquals(Command.BOOKMARK, key("b"));
        assertEquals(Command.BOOKMARK_FOLDER, key("S-b"));
        assertEquals(Command.NOTE, key("n"));
        assertEquals(Command.FOLDER_NOTE, key("S-n"));
        assertEquals(Command.NEXT_BOOKMARK, key("]"));
        assertEquals(Command.PREVIOUS_BOOKMARK, key("["));
        assertEquals(Command.GO_TO_BOOKMARK, key("C-b"));
        assertEquals(Command.GO_TO_BOOKMARK, mac("Cmd-b"));
        assertEquals(Command.BOOKMARKS, key("C-S-b"));
        assertEquals(Command.BOOKMARKS, mac("Cmd-S-b"));
        assertEquals("Bookmarks", Command.BOOKMARKS.group());
        assertEquals("No folder is open.", Command.BOOKMARK.unavailable(0, 0, false));
        // A file that would not decode can still be bookmarked, to come back to.
        assertNull(Command.NOTE.unavailable(0, 1, false));
        assertEquals("Bookmarks", Command.GO_TO_BOOKMARK.group());
    }

    @Test
    void emacsHasItsOwnKeys() {
        assertEquals(Command.NEXT, EMACS.commandFor(Command.Context.VIEWER, "n"));
        assertEquals(Command.PREVIOUS, EMACS.commandFor(Command.Context.VIEWER, "p"));
        assertEquals(Command.FIRST, EMACS.commandFor(Command.Context.VIEWER, "M-S-,"));
        assertEquals(Command.ESCAPE, EMACS.commandFor(Command.Context.VIEWER, "C-g"));
        assertEquals(Command.COPY, EMACS.commandFor(Command.Context.VIEWER, "M-w"));
        assertEquals(Command.PAN_RIGHT, EMACS.commandFor(Command.Context.VIEWER, "C-f"));
        assertEquals(Command.EDITOR_CANCEL, EMACS.commandFor(Command.Context.EDITOR, "C-g"));
        assertEquals(Command.EDITOR_UNDO, EMACS.commandFor(Command.Context.EDITOR, "C-/"));
    }

    @Test
    void otherKeysAreNotOurs() {
        assertNull(key("a"));
        assertNull(key("C-M-S-a"));
    }

    // --- labels, groups, availability ---

    @Test
    void windowsCallsTheTrashTheRecycleBin() {
        assertEquals("Move to Recycle Bin", Command.MOVE_TO_TRASH.label(false, true));
        assertEquals("Move to Trash", Command.MOVE_TO_TRASH.label(true, false));
        assertEquals("Move to Trash", Command.MOVE_TO_TRASH.label(false, false));
        assertEquals("Rename…", Command.RENAME.label(false, true));
    }

    @Test
    void theFileManagerIsNamedWhereItHasOneName() {
        assertEquals("Show in Finder", Command.SHOW_IN_FOLDER.label(true, false));
        assertEquals("Show in File Explorer", Command.SHOW_IN_FOLDER.label(false, true));
        assertEquals("Show in Folder", Command.SHOW_IN_FOLDER.label(false, false));
        assertEquals("Bookmarks: Show in Finder", Command.BOOKMARKS_SHOW_IN_FOLDER.label(true, false));
    }

    @Test
    void escapeClosesTheWindowOnlyOnLinux() {
        assertTrue(Command.escapeCloses("Linux"));
        assertFalse(Command.escapeCloses("Mac OS X"));
        assertFalse(Command.escapeCloses("Windows 11"));
    }

    @Test
    void everyCommandHasAGroupAndTheShortcutsWindowListsEveryKey() {
        int keyed = 0;
        for (Command command : Command.values()) {
            assertFalse(command.group().isBlank(), command.name());
            if (command.chord() != KeyCombination.NO_MATCH) keyed++;
        }
        assertEquals(
                keyed,
                ShortcutsWindow.grouped().values().stream().mapToInt(List::size).sum());
        assertEquals("Editor", Command.EDITOR_SAVE.group());
    }

    @Test
    void theShortcutsWindowShowsTheFirstFewKeysOnce() {
        assertEquals(
                String.join(
                        ", ",
                        new KeyCodeCombination(KeyCode.RIGHT).getDisplayText(),
                        new KeyCodeCombination(KeyCode.PAGE_DOWN).getDisplayText(),
                        new KeyCodeCombination(KeyCode.SPACE).getDisplayText()),
                ShortcutsWindow.keys(PC.chords(Command.NEXT)));
        // The keypad's + is the main row's, shown once.
        assertEquals(
                String.join(
                        ", ",
                        "+",
                        new KeyCodeCombination(KeyCode.EQUALS).getDisplayText(),
                        new KeyCodeCombination(KeyCode.EQUALS, KeyCombination.SHIFT_DOWN).getDisplayText()),
                ShortcutsWindow.keys(PC.chords(Command.ZOOM_IN)));
        assertEquals("", ShortcutsWindow.keys(List.of()));
    }

    /** Help → Documentation is a command like any other: in the palette, under Help (DOCS-06). */
    @Test
    void documentationIsAHelpCommandInThePalette() {
        assertEquals("Help", Command.DOCUMENTATION.group());
        assertTrue(Command.inPalette(Command.Context.VIEWER).contains(Command.DOCUMENTATION));
        assertFalse(Command.DOCUMENTATION.description().isBlank());
        assertEquals("help.copyDetails", Command.COPY_DETAILS.id());
    }

    @Test
    void fileCommandsNeedAnImageAndASlideshowTwo() {
        assertEquals("No image is showing.", Command.MOVE_TO_TRASH.unavailable(0, 1, false));
        assertNull(Command.RENAME.unavailable(0, 1, true));
        assertEquals("A slideshow needs a folder with more than one image.", Command.SLIDESHOW.unavailable(0, 1, true));
        assertNull(Command.SLIDESHOW.unavailable(0, 2, true));
    }

    @Test
    void steppingNeedsSomewhereToGo() {
        assertEquals("No folder is open.", Command.NEXT.unavailable(0, 0, false));
        assertEquals("This is the last image.", Command.NEXT.unavailable(2, 3, true));
        assertEquals("This is the last image.", Command.LAST.unavailable(2, 3, true));
        assertEquals("This is the first image.", Command.PREVIOUS.unavailable(0, 3, true));
        assertNull(Command.NEXT.unavailable(1, 3, true));
        assertNull(Command.FIRST.unavailable(1, 3, true));
    }

    @Test
    void zoomingAndTurningNeedAnImage() {
        assertEquals("No image is showing.", Command.ZOOM_IN.unavailable(0, 1, false));
        assertEquals("No image is showing.", Command.ROTATE_CLOCKWISE.unavailable(0, 1, false));
        assertEquals("No image is showing.", Command.PAN_LEFT.unavailable(0, 1, false));
        assertNull(Command.FIT.unavailable(0, 1, true));
        assertNull(Command.OPEN.unavailable(0, 0, false));
        assertNull(Command.OPEN_FOLDER.unavailable(0, 0, false));
    }
}
