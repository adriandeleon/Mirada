package com.mirada.folder;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NaturalOrderTest {

    private static List<String> sorted(String... names) {
        List<String> out = new ArrayList<>(List.of(names));
        out.sort(NaturalOrder.INSTANCE);
        return out;
    }

    @Test
    void numbersCompareByValue() {
        assertEquals(
                List.of("IMG_2.jpg", "IMG_9.jpg", "IMG_10.jpg", "IMG_100.jpg"),
                sorted("IMG_100.jpg", "IMG_10.jpg", "IMG_9.jpg", "IMG_2.jpg"));
    }

    @Test
    void lettersIgnoreCase() {
        assertEquals(List.of("apple.png", "Banana.png", "cherry.png"), sorted("cherry.png", "Banana.png", "apple.png"));
    }

    @Test
    void leadingZerosDoNotChangeTheValue() {
        assertEquals(
                List.of("page 1.png", "page 002.png", "page 3.png"),
                sorted("page 3.png", "page 002.png", "page 1.png"));
    }

    @Test
    void numbersLongerThanALongStillCompare() {
        assertEquals(
                List.of("a99999999999999999999", "a100000000000000000000"),
                sorted("a100000000000000000000", "a99999999999999999999"));
    }

    @Test
    void aPrefixComesFirst() {
        assertEquals(List.of("cover", "cover 1", "cover 1 back"), sorted("cover 1 back", "cover", "cover 1"));
    }

    @Test
    void onlyEqualNamesCompareEqual() {
        assertTrue(NaturalOrder.INSTANCE.compare("a01", "a1") != 0);
        assertTrue(NaturalOrder.INSTANCE.compare("A", "a") != 0);
        assertEquals(0, NaturalOrder.INSTANCE.compare("same", "same"));
    }

    @Test
    void otherScriptsDigitsAreLetters() {
        // Arabic-Indic three is not taken as a number, so it cannot be compared to "3" by value.
        assertEquals(List.of("x3", "x٣"), sorted("x٣", "x3"));
    }
}
