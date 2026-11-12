package io.github.slugblue.wildroots.client.journal.book;

import io.github.slugblue.wildroots.client.journal.GuideEntry;
import io.github.slugblue.wildroots.client.journal.GuidePage;
import io.github.slugblue.wildroots.client.journal.GuideUnlocks;
import io.github.slugblue.wildroots.client.journal.page.CraftingPage;
import io.github.slugblue.wildroots.client.journal.page.LinkMarkup;
import io.github.slugblue.wildroots.client.journal.page.LinksPage;
import io.github.slugblue.wildroots.client.journal.page.SpotlightPage;
import io.github.slugblue.wildroots.client.journal.page.TextPage;
import io.github.slugblue.wildroots.client.journal.records.RecordBlocks;
import io.github.slugblue.wildroots.client.journal.search.SearchIndex;
import io.github.slugblue.wildroots.journal.JournalData;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * What the search sees (journal v2 §3.9): the shown pages of open guide
 * entries and the records as the book shows them, translated, without
 * markup. Closed entries, closed pages and silhouettes are left out.
 */
@OnlyIn(Dist.CLIENT)
final class BookSearch {

    private BookSearch() {
    }

    static String tr(String key) {
        return Component.translatable(key).getString();
    }

    static String plain(String key) {
        return LinkMarkup.plainText(tr(key), id -> tr("wildroots.journal." + id + ".title"));
    }

    static List<SearchIndex.Doc<BookLocation>> docs(GuideUnlocks unlocks, List<JournalData.Entry> entries) {
        List<SearchIndex.Doc<BookLocation>> out = new ArrayList<>();
        for (GuideEntry e : unlocks.book().entries()) {
            if (!unlocks.isOpen(e)) {
                continue;
            }
            StringBuilder text = new StringBuilder();
            for (GuidePage p : unlocks.pages(e)) {
                List<Optional<String>> keys = new ArrayList<>();
                if (p.data() instanceof TextPage t) {
                    keys.add(t.title());
                    keys.add(Optional.of(t.text()));
                } else if (p.data() instanceof SpotlightPage s) {
                    keys.add(s.title());
                    keys.add(s.text());
                    var item = ForgeRegistries.ITEMS.getValue(s.item());
                    if (item != null) {
                        text.append(' ').append(item.getDescription().getString());
                    }
                } else if (p.data() instanceof CraftingPage c) {
                    keys.add(c.text());
                } else if (p.data() instanceof LinksPage l) {
                    keys.add(l.title());
                }
                keys.forEach(k -> k.ifPresent(key -> text.append(' ').append(plain(key))));
            }
            String category = unlocks.book().category(e.category()).map(c -> tr(c.titleKey())).orElse("");
            out.add(new SearchIndex.Doc<>(tr(e.titleKey()), category, text.toString(), BookLocation.entry(e.id())));
        }
        for (RecordBlocks.Tab tab : RecordBlocks.Tab.values()) {
            String category = tr(tab.key);
            for (RecordBlocks.Block b : RecordBlocks.blocks(tab, entries)) {
                if (b.ids().isEmpty() || b.lines().isEmpty()) {
                    continue; // silhouettes and line copies: not searchable
                }
                String id = b.ids().get(0);
                Optional<BookLocation> at = BookPages.whereIs(id);
                if (at.isEmpty()) {
                    continue;
                }
                StringBuilder text = new StringBuilder();
                b.lines().stream().skip(1).forEach(c -> text.append(' ').append(c.getString()));
                String title = entries.stream().filter(x -> x.id().equals(id)).findFirst()
                        .map(x -> BookPages.recordTitle(x).getString()).orElse(b.lines().get(0).getString());
                out.add(new SearchIndex.Doc<>(title, category, text.toString(), at.get()));
            }
        }
        return out;
    }
}
