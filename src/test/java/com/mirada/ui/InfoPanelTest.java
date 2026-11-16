package com.mirada.ui;

import java.util.List;

import com.mirada.image.ExifDetails;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** PERF-08: All Metadata lists at most a named number of tags, in the file's order. */
class InfoPanelTest {

    private static ExifDetails.Section section(String title, int rows) {
        return new ExifDetails.Section(
                title,
                java.util.stream.IntStream.range(0, rows)
                        .mapToObj(i -> new ExifDetails.Row(title + i, "v"))
                        .toList());
    }

    @Test
    void theTagsAreCutToTheFirstOnesInOrder() {
        List<ExifDetails.Section> all = List.of(section("a", 3), section("b", 4), section("c", 2));
        assertEquals(all, InfoPanel.firstRows(all, 100), "under the cap, all of them");
        List<ExifDetails.Section> cut = InfoPanel.firstRows(all, 5);
        assertEquals(
                List.of("a", "b"), cut.stream().map(ExifDetails.Section::title).toList());
        assertEquals(3, cut.get(0).rows().size());
        assertEquals(
                List.of("b0", "b1"),
                cut.get(1).rows().stream().map(ExifDetails.Row::label).toList());
        assertEquals(List.of(), InfoPanel.firstRows(all, 0));
        assertEquals(300, InfoPanel.MAX_METADATA_ROWS);
    }
}
