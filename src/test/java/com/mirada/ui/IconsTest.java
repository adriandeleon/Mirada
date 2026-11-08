package com.mirada.ui;

import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class IconsTest {

    /** The order's choices are ticks in a submenu, which has the icon; everything else has its own. */
    private static final Set<Command> UNICONED =
            Set.of(Command.SORT_BY_NAME, Command.SORT_BY_DATE, Command.SORT_BY_SIZE, Command.REVERSE_ORDER);

    /** Keys only, never a menu's item: Escape and panning. */
    private static final Set<Command> KEYS_ONLY =
            Set.of(Command.ESCAPE, Command.PAN_LEFT, Command.PAN_RIGHT, Command.PAN_UP, Command.PAN_DOWN);

    @Test
    void everyCommandInTheMenusHasAnIcon() {
        for (Command command : Command.values()) {
            if (command.context() != Command.Context.VIEWER || KEYS_ONLY.contains(command)) continue;
            if (UNICONED.contains(command)) assertNull(Icons.forCommand(command), command.name());
            else assertNotNull(Icons.forCommand(command), command.name());
        }
    }
}
