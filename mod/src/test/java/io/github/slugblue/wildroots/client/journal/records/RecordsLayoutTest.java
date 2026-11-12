package io.github.slugblue.wildroots.client.journal.records;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.slugblue.wildroots.client.journal.GuideBook;
import io.github.slugblue.wildroots.core.Ids;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** WR-181e: pure parts of the records pages ("More →" link of a hint, data id → journal id). */
class RecordsLayoutTest {

    @Test
    void moreLinkUsesTheBaseIdOfPerLineHints() {
        GuideBook book = new GuideBook(List.of(), List.of(), Map.of("hint.stagnation", "guide.inbreeding",
                "hint.mother_plant", "guide.cycle"));
        assertEquals(Optional.of("guide.cycle"), RecordsLayout.moreLink(book, "hint.mother_plant"));
        assertEquals(Optional.of("guide.inbreeding"), RecordsLayout.moreLink(book, "hint.stagnation.l0123456789abcdef"));
        assertEquals(Optional.empty(), RecordsLayout.moreLink(book, "hint.welcome"));
    }

    @Test
    void dataIdsMapToJournalIdsAsTheServerWritesThem() {
        assertEquals("wild_carrot", RecordsLayout.rest(Ids.mod("wild_carrot")));
        assertEquals("othermod.sub.wild_bean", RecordsLayout.rest(Ids.of("othermod", "sub/wild-bean")));
    }
}
