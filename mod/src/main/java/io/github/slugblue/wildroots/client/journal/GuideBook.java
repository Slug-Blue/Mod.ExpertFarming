package io.github.slugblue.wildroots.client.journal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The loaded guide (journal v2 §2.2, §5.2): categories, entries and the
 * hint → entry links, immutable. Which of them the player sees is decided
 * by {@link GuideUnlocks} from the server's journal entries.
 */
@OnlyIn(Dist.CLIENT)
public final class GuideBook {

    public static final GuideBook EMPTY = new GuideBook(List.of(), List.of(), Map.of());

    static final Comparator<GuideCategory> CATEGORY_ORDER =
            Comparator.comparingInt(GuideCategory::order).thenComparing(GuideCategory::id);
    static final Comparator<GuideEntry> ENTRY_ORDER =
            Comparator.comparingInt(GuideEntry::order).thenComparing(GuideEntry::id);

    private final List<GuideCategory> categories;
    private final Map<String, GuideCategory> categoryById = new LinkedHashMap<>();
    private final Map<String, GuideEntry> entryById = new LinkedHashMap<>();
    private final Map<String, String> hintLinks;

    public GuideBook(List<GuideCategory> categories, List<GuideEntry> entries, Map<String, String> hintLinks) {
        List<GuideCategory> cats = new ArrayList<>(categories);
        cats.sort(CATEGORY_ORDER);
        this.categories = List.copyOf(cats);
        cats.forEach(c -> categoryById.put(c.id(), c));
        List<GuideEntry> es = new ArrayList<>(entries);
        es.sort(ENTRY_ORDER);
        es.forEach(e -> entryById.put(e.id(), e));
        this.hintLinks = Map.copyOf(hintLinks);
    }

    /** All categories, in display order. */
    public List<GuideCategory> categories() {
        return categories;
    }

    public Optional<GuideCategory> category(String id) {
        return Optional.ofNullable(categoryById.get(id));
    }

    /** All entries, in display order (order, then id). */
    public List<GuideEntry> entries() {
        return List.copyOf(entryById.values());
    }

    public Optional<GuideEntry> entry(String id) {
        return Optional.ofNullable(entryById.get(id));
    }

    /** Entries of one category, in display order. */
    public List<GuideEntry> entriesOf(String categoryId) {
        return entryById.values().stream().filter(e -> e.category().equals(categoryId)).toList();
    }

    /** Entries that list {@code item} in {@code items} (П3), in display order. */
    public List<GuideEntry> entriesForItem(ResourceLocation item) {
        return entryById.values().stream().filter(e -> e.items().contains(item)).toList();
    }

    /** The "More →" entry of a hint id ({@code hint.mother_plant} → {@code guide.cycle}), §4.3. */
    public Optional<String> hintLink(String hintId) {
        return Optional.ofNullable(hintLinks.get(hintId));
    }

    public Map<String, String> hintLinks() {
        return hintLinks;
    }
}
