package io.github.slugblue.wildroots.client.journal.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Search over the text the reader can see (journal v2 §3.9): titles and
 * texts of open entries and records, in the client's language, without
 * markup. Whatever is closed is never added, so it is never found. Case
 * and "ё/е" do not matter; title matches come first; at most
 * {@value #MAX_RESULTS} results.
 *
 * @param <T> what a result opens
 */
@OnlyIn(Dist.CLIENT)
public final class SearchIndex<T> {

    public static final int MAX_RESULTS = 32;

    /** One searchable thing: its title, its category label, its whole text, and where it leads. */
    public record Doc<T>(String title, String category, String text, T target) {
    }

    private record Indexed<T>(Doc<T> doc, String title, String text) {
    }

    private final List<Indexed<T>> docs = new ArrayList<>();

    public SearchIndex(List<Doc<T>> docs) {
        for (Doc<T> d : docs) {
            this.docs.add(new Indexed<>(d, normalize(d.title()), normalize(d.text())));
        }
    }

    /** Lower case (root locale), ё → е, runs of white space → one space. */
    public static String normalize(String s) {
        return s.toLowerCase(Locale.ROOT).replace('ё', 'е').replaceAll("\\s+", " ").strip();
    }

    public List<Doc<T>> search(String query) {
        String q = normalize(query);
        if (q.isEmpty()) {
            return List.of();
        }
        List<Doc<T>> title = new ArrayList<>();
        List<Doc<T>> text = new ArrayList<>();
        for (Indexed<T> d : docs) {
            if (d.title().contains(q)) {
                title.add(d.doc());
            } else if (d.text().contains(q)) {
                text.add(d.doc());
            }
        }
        title.addAll(text);
        return title.size() > MAX_RESULTS ? List.copyOf(title.subList(0, MAX_RESULTS)) : title;
    }

    public int size() {
        return docs.size();
    }
}
