package io.github.slugblue.wildroots.client;

import io.github.slugblue.wildroots.core.safety.DispCodec;
import io.github.slugblue.wildroots.journal.JournalData;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Builds the pages of the journal screen from the entries the server sent
 * (narrative §4.2–4.4, §9.7). Every text is a translation key the server
 * named or a fixed key of the entry kind; nothing here looks at genetics.
 */
public final class JournalPages {

    /** Tabs in the order of narrative §4.2. */
    public enum Tab {
        SPECIES("species"), CULTIVARS("cultivars"), LINES("lines"), NOTES("notes"), TOOLS("tools"), BREEDER("breeder");

        public final String key;

        Tab(String id) {
            this.key = "wildroots.journal.tab." + id;
        }
    }

    /** One entry block: the entry ids it shows (for "mark read") and its paragraphs. */
    public record Block(List<String> ids, List<Component> lines, boolean isNew, Optional<String> pinnable,
                        boolean pinned) {
    }

    private JournalPages() {
    }

    static boolean kind(JournalData.Entry e, String kind) {
        return e.id().startsWith(kind + ".");
    }

    static String rest(JournalData.Entry e) {
        return e.id().substring(e.id().indexOf('.') + 1);
    }

    /** Whether a tab is shown (narrative §4.2 "when it appears"). */
    public static boolean visible(Tab tab, List<JournalData.Entry> entries) {
        return switch (tab) {
            case SPECIES, CULTIVARS -> entries.stream().anyMatch(e -> kind(e, "species"));
            case LINES -> entries.stream().anyMatch(e -> kind(e, "cultivar") && !e.is(JournalData.LOCKED));
            case NOTES -> entries.stream().anyMatch(e -> kind(e, "hint"));
            case TOOLS -> entries.stream().anyMatch(e -> kind(e, "tool"));
            case BREEDER -> entries.stream().anyMatch(e -> kind(e, "note"));
        };
    }

    /** Whether a tab holds something unread ("New" mark). */
    public static boolean hasNew(Tab tab, List<JournalData.Entry> entries) {
        return blocks(tab, entries).stream().anyMatch(Block::isNew);
    }

    static Component arg(String a) {
        return DispCodec.argComponent(a);
    }

    static Component line(DispCodec.Line l) {
        return DispCodec.toComponent(l);
    }

    static MutableComponent heading(String key) {
        return Component.translatable(key).withStyle(s -> s.withBold(true));
    }

    /** The blocks of a tab, in the order the tab shows them. */
    public static List<Block> blocks(Tab tab, List<JournalData.Entry> entries) {
        List<Block> out = new ArrayList<>();
        switch (tab) {
            case SPECIES -> entries.stream().filter(e -> kind(e, "species")).forEach(e -> out.add(species(e)));
            case CULTIVARS -> entries.stream().filter(e -> kind(e, "cultivar")).forEach(e -> out.add(cultivar(e)));
            case LINES -> entries.stream().filter(e -> kind(e, "cultivar") && !e.is(JournalData.LOCKED))
                    .forEach(e -> out.add(line(e)));
            case NOTES -> entries.stream().filter(e -> kind(e, "hint"))
                    .sorted(Comparator.comparing((JournalData.Entry e) -> !e.is(JournalData.PINNED))
                            .thenComparing(Comparator.comparingInt(JournalData.Entry::order).reversed()))
                    .forEach(e -> out.add(hint(e)));
            case TOOLS -> entries.stream().filter(e -> kind(e, "tool")).forEach(e -> out.add(simple(e,
                    "item.wildroots." + rest(e), "wildroots.journal.tool." + rest(e))));
            case BREEDER -> entries.stream().filter(e -> kind(e, "note")).forEach(e -> out.add(simple(e,
                    "wildroots.journal.note." + rest(e) + ".title", "wildroots.journal.note." + rest(e))));
        }
        return out;
    }

    static final String GOAL_PREFIX = "wildroots.journal.cultivar.";
    static final String GOAL_SUFFIX = ".goal";

    static Block species(JournalData.Entry e) {
        String id = rest(e);
        List<Component> l = new ArrayList<>();
        l.add(heading("wildroots.species." + id));
        l.add(heading("wildroots.journal.ui.section.seen"));
        l.add(Component.translatable("wildroots.journal.species." + id + ".seen"));
        List<DispCodec.Line> found = e.rows().stream()
                .filter(r -> r.key().equals("wildroots.journal.species.found_at")).toList();
        if (!found.isEmpty()) {
            l.add(heading("wildroots.journal.ui.section.found_at"));
            found.forEach(r -> l.add(line(r)));
        }
        // only goal rows of the form the server writes; any other key would cut a negative range (batch 5 test pass)
        List<DispCodec.Line> goals = e.rows().stream().filter(r -> r.key().startsWith(GOAL_PREFIX)
                && r.key().endsWith(GOAL_SUFFIX) && r.key().length() > GOAL_PREFIX.length() + GOAL_SUFFIX.length())
                .toList();
        if (!goals.isEmpty()) {
            l.add(heading("wildroots.journal.ui.section.goals"));
            for (DispCodec.Line g : goals) {
                String cv = g.key().substring(GOAL_PREFIX.length(), g.key().length() - GOAL_SUFFIX.length());
                l.add(Component.translatable("wildroots.cultivar." + cv).withStyle(s -> s.withItalic(true)));
                l.add(line(g));
            }
        }
        l.add(heading("wildroots.journal.ui.section.history"));
        l.add(Component.translatable("wildroots.journal.species." + id + ".history"));
        return new Block(List.of(e.id()), l, e.is(JournalData.NEW), Optional.empty(), false);
    }

    static Block cultivar(JournalData.Entry e) {
        String id = rest(e);
        List<Component> l = new ArrayList<>();
        if (e.is(JournalData.LOCKED)) {
            l.add(heading("wildroots.journal.ui.locked"));
            l.add(Component.translatable("wildroots.journal.ui.locked.cultivar"));
            e.args().forEach(a -> l.add(arg(a)));
            return new Block(List.of(), l, false, Optional.empty(), false);
        }
        // args: tier key, then rows: bred_on, line, ancestry steps, mark
        Component tier = e.args().isEmpty() ? Component.empty() : arg(e.args().get(0));
        l.add(Component.translatable("wildroots.cultivar." + id).append(" · ").append(tier)
                .withStyle(s -> s.withBold(true)));
        List<Component> path = new ArrayList<>();
        for (DispCodec.Line r : e.rows()) {
            if (r.key().equals("wildroots.journal.cultivar.ancestry_step")) {
                r.args().forEach(a -> path.add(arg(a)));
            } else {
                l.add(line(r));
            }
        }
        if (!path.isEmpty()) {
            MutableComponent p = Component.empty();
            for (int i = 0; i < path.size(); i++) {
                if (i > 0) {
                    p.append(Component.translatable("wildroots.journal.arrow"));
                }
                p.append(path.get(i));
            }
            l.add(Component.translatable("wildroots.journal.cultivar.ancestry", p));
        }
        l.add(Component.translatable("wildroots.cultivar." + id + ".note"));
        return new Block(List.of(e.id()), l, e.is(JournalData.NEW), Optional.empty(), false);
    }

    static Block line(JournalData.Entry e) {
        String id = rest(e);
        List<Component> l = new ArrayList<>();
        l.add(heading("wildroots.cultivar." + id));
        e.rows().stream().filter(r -> r.key().equals("wildroots.journal.cultivar.line")).forEach(r -> l.add(line(r)));
        return new Block(List.of(), l, false, Optional.empty(), false);
    }

    static Block hint(JournalData.Entry e) {
        // per-line hints (stagnation, missing_allele) carry a line suffix: hint.stagnation.l1a2b
        String rest = rest(e);
        String id = rest.contains(".") ? rest.substring(0, rest.indexOf('.')) : rest;
        List<Component> l = new ArrayList<>();
        MutableComponent title = heading("wildroots.journal.hint." + id + ".title");
        if (e.is(JournalData.NEW)) {
            title.append(" ").append(Component.translatable("wildroots.journal.ui.new")
                    .withStyle(s -> s.withBold(false).withColor(0x2E7D32)));
        }
        l.add(title);
        Object[] args = e.args().stream().map(JournalPages::arg).toArray();
        l.add(Component.translatable("wildroots.journal.hint." + id, args));
        return new Block(List.of(e.id()), l, e.is(JournalData.NEW), Optional.of(e.id()), e.is(JournalData.PINNED));
    }

    static Block simple(JournalData.Entry e, String titleKey, String textKey) {
        return new Block(List.of(e.id()), List.of(heading(titleKey), Component.translatable(textKey)),
                e.is(JournalData.NEW), Optional.empty(), false);
    }
}
