package com.mirada.ui;

/** The rules for a new file name typed into the rename dialog. */
final class Renaming {

    private Renaming() {}

    /** How much of {@code name} is the name before its extension: what the dialog selects. */
    static int stemLength(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? dot : name.length();
    }

    /**
     * The file name to rename {@code old} to, from what was typed: trimmed, and given {@code old}'s
     * extension when it has none of its own, so typing over the selected stem keeps the file an
     * image. Throws, with the reason, for a name that cannot be a file in the same folder.
     */
    static String validName(String old, String typed) {
        String name = typed.strip();
        if (name.isEmpty()) throw new IllegalArgumentException("the name is empty");
        if (name.equals(".") || name.equals("..")) throw new IllegalArgumentException("that is not a file name");
        for (char c : name.toCharArray()) {
            if (c == '/' || c == '\\' || c == '\0') {
                throw new IllegalArgumentException("a name cannot contain " + (c == '\0' ? "NUL" : String.valueOf(c)));
            }
        }
        boolean hasExtension = name.lastIndexOf('.') > 0;
        int oldDot = old.lastIndexOf('.');
        if (!hasExtension && oldDot > 0) name += old.substring(oldDot);
        return name;
    }
}
