package io.github.slugblue.wildroots.journal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.slugblue.wildroots.core.safety.DispCodec;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Review 1, m2: long or unusual data-pack ids never refuse a whole journal entry. */
class LongIdsTest {

    @Test
    void entryIdsOfAnyDataIdAreValid() {
        String[] ids = {"wildroots:wild_carrot", "some-pack:crops/wild_bean", "pack:" + "a".repeat(120),
            "a-b:c/d-e/" + "x".repeat(70)};
        for (String id : ids) {
            String e = JournalTriggers.speciesEntry(id);
            assertTrue(JournalData.validId(e), id + " -> " + e);
        }
        assertEquals("species.wild_carrot", JournalTriggers.speciesEntry("wildroots:wild_carrot"));
        assertEquals("species.some_pack.crops.wild_bean", JournalTriggers.speciesEntry("some-pack:crops/wild_bean"));
        // two long ids with the same start stay apart
        assertTrue(!JournalTriggers.speciesEntry("p:" + "a".repeat(80) + "1")
                .equals(JournalTriggers.speciesEntry("p:" + "a".repeat(80) + "2")));
    }

    @Test
    void nameArgFallsBackToALiteralWhenTheKeyIsTooLong() {
        String shortArg = JournalTriggers.nameArg("species", "wildroots:wild_carrot");
        assertEquals("wildroots.species.wild_carrot", DispCodec.argKey(shortArg));
        String longArg = JournalTriggers.nameArg("species", "pack:" + "a".repeat(120));
        assertTrue(DispCodec.argKey(longArg) == null && longArg.length() <= DispCodec.MAX_ARG_LENGTH, longArg);
        assertTrue(DispCodec.valid(DispCodec.Line.of("wildroots.journal", longArg)));
    }

    @Test
    void speciesRowsCapGoalsAndKeepFoundAt() {
        List<String> cultivars = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            cultivars.add("pack:cv_" + i);
        }
        cultivars.add("pack:" + "z".repeat(150)); // a goal key too long for a row
        List<DispCodec.Line> found = new ArrayList<>();
        for (int i = 0; i < JournalTriggers.MAX_FOUND_AT; i++) {
            found.add(DispCodec.Line.of("wildroots.journal.species.found_at", "plains", Integer.toString(i), "0"));
        }
        List<DispCodec.Line> rows = JournalTriggers.speciesRows(cultivars, found);
        assertTrue(rows.size() <= JournalData.MAX_ROWS, "rows " + rows.size());
        assertTrue(rows.containsAll(found), "found-at rows dropped");
        assertTrue(rows.stream().allMatch(DispCodec::valid));
        JournalData j = new JournalData();
        assertEquals(JournalData.Unlock.NEW, j.unlock(JournalTriggers.speciesEntry("pack:" + "b".repeat(90)), List.of(), rows));
    }
}
