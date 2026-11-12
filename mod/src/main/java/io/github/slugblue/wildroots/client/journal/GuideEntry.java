package io.github.slugblue.wildroots.client.journal;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * A guide entry (journal v2 §2.2, §5.2): one row of a category, 1–24
 * pages. {@code unlock} names the journal entries that open it (empty =
 * always open, §2.3); {@code items} the items whose icons lead here (П3).
 * Its title is the lang key {@link #titleKey()}:
 * {@code guide.cycle} → {@code wildroots.journal.guide.cycle.title}.
 */
@OnlyIn(Dist.CLIENT)
public record GuideEntry(String id, String category, GuideIcon icon, int order, List<String> unlock,
                         List<ResourceLocation> items, List<String> seeAlso, List<GuidePage> pages) {

    public GuideEntry {
        unlock = List.copyOf(unlock);
        items = List.copyOf(items);
        seeAlso = List.copyOf(seeAlso);
        pages = List.copyOf(pages);
    }

    public String titleKey() {
        return "wildroots.journal." + id + ".title";
    }
}
