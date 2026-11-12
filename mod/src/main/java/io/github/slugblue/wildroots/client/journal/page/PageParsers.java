package io.github.slugblue.wildroots.client.journal.page;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The page types a guide file may name (journal v2 §5.2): {@code text},
 * {@code spotlight}, {@code crafting}, {@code scene}, {@code trait_legend},
 * {@code links}. A page of any other type is skipped with one log line.
 * Dynamic pages (grid, species, cultivar, line, hint) are built by code
 * and never come from JSON.
 */
@OnlyIn(Dist.CLIENT)
public final class PageParsers {

    public static final Pattern TYPE = Pattern.compile("[a-z_]{1,32}");

    private static final Map<String, PageParser> PARSERS = new ConcurrentHashMap<>();

    static {
        register(TextPage.TYPE, TextPage::parse);
        register(SpotlightPage.TYPE, SpotlightPage::parse);
        register(CraftingPage.TYPE, CraftingPage::parse);
        register(ScenePage.TYPE, ScenePage::parse);
        register(TraitLegendPage.TYPE, TraitLegendPage::parse);
        register(LinksPage.TYPE, LinksPage::parse);
    }

    private PageParsers() {
    }

    /** Adds or replaces the parser of a type; call before the first resource reload. */
    public static void register(String type, PageParser parser) {
        if (!TYPE.matcher(type).matches()) {
            throw new IllegalArgumentException("bad page type " + type);
        }
        PARSERS.put(type, parser);
    }

    public static Optional<PageParser> get(String type) {
        return Optional.ofNullable(PARSERS.get(type));
    }
}
