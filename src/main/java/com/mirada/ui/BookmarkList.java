package com.mirada.ui;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.mirada.folder.NaturalOrder;
import com.mirada.settings.Bookmarks;
import org.jspecify.annotations.Nullable;

/**
 * What the Bookmarks and Notes window lists, and in what order: Editora's Bookmarks and Personal
 * Notes tool windows, which group by file, grouped here by folder. No UI, so the rules are tested.
 *
 * <p>A folder comes where its latest mark is, the latest first, as Go to Bookmark lists them; its
 * images in name order, as the folder shows them. A folder that is marked itself heads its group with
 * its mark; one that is not heads it with its name alone.
 */
final class BookmarkList {

    private BookmarkList() {}

    /** Which marks to list. */
    enum Show {
        ALL("All"),
        BOOKMARKS("Bookmarks"),
        NOTES("Notes");

        final String label;

        Show(String label) {
            this.label = label;
        }

        boolean includes(Bookmarks.Mark mark) {
            return switch (this) {
                case ALL -> true;
                case BOOKMARKS -> mark.bookmarked();
                case NOTES -> mark.hasNote();
            };
        }
    }

    /** One row: a folder heading its group (its mark, if it has one), or a marked image under it. */
    sealed interface Row permits FolderRow, ImageRow {
        Path path();
    }

    record FolderRow(Path path, Bookmarks.@Nullable Mark mark) implements Row {}

    record ImageRow(Path path, Bookmarks.Mark mark) implements Row {}

    private static final Comparator<Bookmarks.Mark> BY_NAME =
            Comparator.comparing(mark -> name(mark.path()), NaturalOrder.INSTANCE);

    /**
     * The rows for {@code latestFirst} ({@link Bookmarks#all}): those {@code show} includes and
     * {@code query} finds, each folder followed by its images.
     */
    static List<Row> rows(List<Bookmarks.Mark> latestFirst, String query, Show show) {
        List<String> words = words(query);
        Map<Path, Bookmarks.Mark> folderMarks = new LinkedHashMap<>();
        Map<Path, List<Bookmarks.Mark>> images = new LinkedHashMap<>();
        // The folders in the order of their latest mark, whichever kind it is.
        List<Path> order = new ArrayList<>();
        for (Bookmarks.Mark mark : latestFirst) {
            if (!show.includes(mark) || !matches(mark, words)) continue;
            Path folder = mark.folder() ? mark.path() : parent(mark.path());
            if (!order.contains(folder)) order.add(folder);
            if (mark.folder()) folderMarks.put(folder, mark);
            else images.computeIfAbsent(folder, f -> new ArrayList<>()).add(mark);
        }
        List<Row> rows = new ArrayList<>();
        for (Path folder : order) {
            rows.add(new FolderRow(folder, folderMarks.get(folder)));
            List<Bookmarks.Mark> in = images.getOrDefault(folder, List.of());
            in.sort(BY_NAME);
            for (Bookmarks.Mark mark : in) rows.add(new ImageRow(mark.path(), mark));
        }
        return rows;
    }

    /**
     * Whether every word of the query is in the mark's name, its note, or the name of the folder it
     * is in (an image) or is (a folder): typing a folder's name lists all that is marked in it.
     */
    static boolean matches(Bookmarks.Mark mark, List<String> words) {
        if (words.isEmpty()) return true;
        String folderName = mark.folder() ? "" : name(parent(mark.path()));
        String text = (name(mark.path()) + "\n" + folderName + "\n" + mark.note()).toLowerCase(Locale.ROOT);
        for (String word : words) {
            if (!text.contains(word)) return false;
        }
        return true;
    }

    static List<String> words(String query) {
        String folded = query.strip().toLowerCase(Locale.ROOT);
        return folded.isEmpty() ? List.of() : List.of(folded.split("\\s+"));
    }

    /**
     * The row to select after the list changed under a selection at {@code index}: the same row
     * where it is still there, else whatever took its place, else the last; -1 for an empty list.
     */
    static int keep(List<Row> rows, @Nullable Path selected, int index) {
        if (rows.isEmpty()) return -1;
        if (selected != null) {
            for (int i = 0; i < rows.size(); i++) {
                if (rows.get(i).path().equals(selected)) return i;
            }
        }
        return Math.clamp(index, 0, rows.size() - 1);
    }

    static String name(Path path) {
        Path name = path.getFileName();
        return name == null ? path.toString() : name.toString();
    }

    private static Path parent(Path path) {
        Path parent = path.getParent();
        return parent == null ? path : parent;
    }
}
