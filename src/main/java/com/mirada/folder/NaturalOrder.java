package com.mirada.folder;

import java.util.Comparator;

/**
 * File-name order as people read it: runs of digits compare as numbers, so {@code IMG_9} comes
 * before {@code IMG_10}, and letters compare without case. A camera roll or a scanned comic is
 * numbered, and plain string order scrambles it the moment the numbers change width.
 *
 * <p>Ties under those rules (leading zeros, case) fall back to plain string order, so the order is
 * total and two names never compare equal unless they are equal.
 */
public final class NaturalOrder implements Comparator<String> {

    public static final NaturalOrder INSTANCE = new NaturalOrder();

    private NaturalOrder() {}

    @Override
    public int compare(String a, String b) {
        int i = 0;
        int j = 0;
        while (i < a.length() && j < b.length()) {
            char ca = a.charAt(i);
            char cb = b.charAt(j);
            if (isDigit(ca) && isDigit(cb)) {
                int endA = digitsEnd(a, i);
                int endB = digitsEnd(b, j);
                int byNumber = compareNumbers(a, i, endA, b, j, endB);
                if (byNumber != 0) return byNumber;
                i = endA;
                j = endB;
                continue;
            }
            int byLetter = Character.compare(fold(ca), fold(cb));
            if (byLetter != 0) return byLetter;
            i++;
            j++;
        }
        int byLength = Integer.compare(a.length() - i, b.length() - j);
        return byLength != 0 ? byLength : a.compareTo(b);
    }

    /** Two digit runs by value, with no limit on their length: leading zeros dropped, then longer is larger. */
    private static int compareNumbers(String a, int fromA, int toA, String b, int fromB, int toB) {
        while (fromA < toA - 1 && a.charAt(fromA) == '0') fromA++;
        while (fromB < toB - 1 && b.charAt(fromB) == '0') fromB++;
        int byWidth = Integer.compare(toA - fromA, toB - fromB);
        if (byWidth != 0) return byWidth;
        for (; fromA < toA; fromA++, fromB++) {
            int byDigit = Character.compare(a.charAt(fromA), b.charAt(fromB));
            if (byDigit != 0) return byDigit;
        }
        return 0;
    }

    private static int digitsEnd(String s, int from) {
        int end = from;
        while (end < s.length() && isDigit(s.charAt(end))) end++;
        return end;
    }

    /** ASCII only: {@link Character#isDigit} also accepts Arabic-Indic and other digits whose values differ. */
    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static char fold(char c) {
        return Character.toLowerCase(Character.toUpperCase(c));
    }
}
