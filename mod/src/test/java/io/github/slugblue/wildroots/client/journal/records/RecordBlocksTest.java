package io.github.slugblue.wildroots.client.journal.records;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.slugblue.wildroots.core.safety.DispCodec;
import io.github.slugblue.wildroots.journal.JournalData;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * WR-181: the journal tabs appear by the conditions of narrative §4.2, and
 * the page builder draws any entry a server may send (ids and rows that
 * pass the {@code JournalSync} decoder) without throwing. Pure code: the
 * screen itself is checked by hand in {@code runClient}.
 */
class RecordBlocksTest {

    static JournalData.Entry e(String id, int flags, DispCodec.Line... rows) {
        return new JournalData.Entry(id, List.of(), List.of(rows), 0, flags);
    }

    static Set<RecordBlocks.Tab> visible(List<JournalData.Entry> entries) {
        Set<RecordBlocks.Tab> out = EnumSet.noneOf(RecordBlocks.Tab.class);
        for (RecordBlocks.Tab t : RecordBlocks.Tab.values()) {
            if (RecordBlocks.visible(t, entries)) {
                out.add(t);
            }
        }
        return out;
    }

    @Test
    void tabsAppearByTheirConditions() {
        assertEquals(Set.of(), visible(List.of()), "an empty journal has only its title page");
        assertEquals(EnumSet.of(RecordBlocks.Tab.NOTES), visible(List.of(e("hint.welcome", JournalData.NEW))));
        assertEquals(EnumSet.of(RecordBlocks.Tab.SPECIES, RecordBlocks.Tab.CULTIVARS),
                visible(List.of(e("species.wild_carrot", 0))), "species and cultivars come with the first species");
        assertEquals(EnumSet.noneOf(RecordBlocks.Tab.class), visible(List.of(e("cultivar.carrot", JournalData.LOCKED))),
                "a locked silhouette opens no tab");
        assertEquals(EnumSet.of(RecordBlocks.Tab.LINES), visible(List.of(e("cultivar.carrot", 0))),
                "lines come with the first bred cultivar");
        assertEquals(EnumSet.noneOf(RecordBlocks.Tab.class), visible(List.of(e("tool.hand_lens", 0))),
                "no Tools tab (В5): a tool opens a guide entry");
        assertEquals(EnumSet.of(RecordBlocks.Tab.BREEDER), visible(List.of(e("note.hut_page", 0))));
        List<RecordBlocks.Tab> order = List.of(RecordBlocks.Tab.values());
        assertEquals(List.of("species", "cultivars", "lines", "notes", "breeder"),
                order.stream().map(t -> t.key.substring("wildroots.journal.tab.".length())).toList(), "§4.2 order");
    }

    @Test
    void notesArePinnedFirstThenNewestFirstAndNewMarksTheTab() {
        List<JournalData.Entry> l = List.of(new JournalData.Entry("hint.a", List.of(), List.of(), 1, JournalData.READ),
                new JournalData.Entry("hint.b", List.of(), List.of(), 2, JournalData.READ | JournalData.PINNED),
                new JournalData.Entry("hint.c", List.of(), List.of(), 3, JournalData.NEW));
        List<RecordBlocks.Block> b = RecordBlocks.blocks(RecordBlocks.Tab.NOTES, l);
        assertEquals(List.of("hint.b", "hint.c", "hint.a"), b.stream().map(x -> x.ids().get(0)).toList());
        assertTrue(RecordBlocks.hasNew(RecordBlocks.Tab.NOTES, l));
        assertFalse(RecordBlocks.hasNew(RecordBlocks.Tab.NOTES, l.subList(0, 2)));
    }

    static final String[] KINDS = {"hint", "species", "cultivar", "tool", "note"};
    static final String[] ROW_KEYS = {"wildroots.journal.species.found_at", "wildroots.journal.goal", "wildroots.goal",
        "wildroots.journal.cultivar.goal", "wildroots.journal.cultivar..goal", "wildroots.journal.cultivar.carrot.goal",
        "wildroots.journal.cultivar.ancestry_step", "wildroots.journal.cultivar.line", "wildroots.x", "wildroots.tooltip.lens"};

    static String word(Random r, String alphabet, int max) {
        StringBuilder b = new StringBuilder();
        int n = 1 + r.nextInt(max);
        for (int i = 0; i < n; i++) {
            b.append(alphabet.charAt(r.nextInt(alphabet.length())));
        }
        return b.toString();
    }

    /** Entries of every kind with any rows the decoder lets through: every tab builds without throwing. */
    @Test
    void anyDecodableEntryBuildsWithoutThrowing() {
        Random r = new Random(0x0181);
        for (int i = 0; i < 5_000; i++) {
            List<JournalData.Entry> entries = new ArrayList<>();
            for (int k = 0; k < 1 + r.nextInt(6); k++) {
                String id = KINDS[r.nextInt(KINDS.length)] + "." + word(r, "abz_.09", 20);
                List<String> args = new ArrayList<>();
                for (int a = 0; a < r.nextInt(DispCodec.MAX_ARGS + 1); a++) {
                    args.add(r.nextBoolean() ? word(r, "aZ %s$1é", 10) : DispCodec.keyArg("wildroots.cultivar.carrot"));
                }
                List<DispCodec.Line> rows = new ArrayList<>();
                for (int k2 = 0; k2 < r.nextInt(JournalData.MAX_ROWS + 1); k2++) {
                    String key = r.nextInt(4) == 0 ? "wildroots." + word(r, "abc._", 30) : ROW_KEYS[r.nextInt(ROW_KEYS.length)];
                    rows.add(DispCodec.Line.of(key, args.toArray(String[]::new)));
                }
                if (!JournalData.validId(id) || !rows.stream().allMatch(DispCodec::valid)
                        || !DispCodec.valid(new DispCodec.Line("wildroots.journal", args))) {
                    continue; // the JournalSync decoder refuses it: never reaches the screen
                }
                entries.add(new JournalData.Entry(id, args, rows, r.nextInt(1000), r.nextInt(16)));
            }
            for (RecordBlocks.Tab t : RecordBlocks.Tab.values()) {
                List<RecordBlocks.Block> blocks = assertDoesNotThrow(() -> RecordBlocks.blocks(t, entries),
                        () -> t + " " + entries);
                blocks.forEach(b -> b.lines().forEach(c -> assertDoesNotThrow(() -> { c.getString(); })));
            }
        }
    }
}
