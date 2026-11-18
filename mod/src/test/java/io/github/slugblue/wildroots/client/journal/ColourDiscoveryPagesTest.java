package io.github.slugblue.wildroots.client.journal;

import static org.junit.jupiter.api.Assertions.*;

import io.github.slugblue.wildroots.client.journal.page.TextPage;
import io.github.slugblue.wildroots.core.Ids;
import io.github.slugblue.wildroots.journal.JournalData;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.IoSupplier;
import org.junit.jupiter.api.Test;

/** Tests the shipped pages, rather than a copy of their unlock configuration. */
class ColourDiscoveryPagesTest {
    static GuideBook shipped() throws Exception {
        var assets = GuideLoaderTest.modDir().resolve("src/main/resources/assets/wildroots");
        Map<ResourceLocation, IoSupplier<InputStream>> files = new LinkedHashMap<>();
        try (var walk = Files.walk(assets.resolve("journal"))) {
            for (var path : walk.filter(Files::isRegularFile).toList()) {
                files.put(Ids.mod(assets.relativize(path).toString().replace('\\', '/')),
                        () -> Files.newInputStream(path));
            }
        }
        var loaded = GuideLoader.load(files, id -> true);
        assertEquals(List.of(), loaded.log());
        return loaded.book();
    }

    @Test
    void eachColourOpensItsOwnBenefitsAndKeepsOtherColoursHidden() throws Exception {
        GuideBook book = shipped();
        var guide = book.entry("guide.colors").orElseThrow();
        for (String colour : List.of("white", "yellow", "purple", "red", "orange")) {
            String hint = "hint." + (colour.equals("white") ? "white_cold" : colour + "_root");
            var journal = List.of(new JournalData.Entry(hint, List.of(), List.of(), 0, JournalData.NEW));
            var unlocks = new GuideUnlocks(book, journal, false);
            assertTrue(unlocks.isOpen(guide), colour);
            for (String candidate : List.of("white", "yellow", "purple", "red", "orange")) {
                String key = "wildroots.journal.guide.colors." + candidate;
                assertEquals(colour.equals(candidate), unlocks.pages(guide).stream()
                        .anyMatch(p -> p.data() instanceof TextPage text && text.text().equals(key)), key);
            }
        }
    }

    @Test
    void eitherWiltingCauseOpensTheExplanationOfRescueAndPermanentDeath() throws Exception {
        GuideBook book = shipped();
        var guide = book.entry("guide.climate").orElseThrow();
        for (String hint : List.of("hint.heat_wilt", "hint.cold_wilt")) {
            var unlocks = new GuideUnlocks(book,
                    List.of(new JournalData.Entry(hint, List.of(), List.of(), 0, JournalData.NEW)), false);
            assertTrue(unlocks.pages(guide).stream().anyMatch(p -> p.data() instanceof TextPage text
                    && text.text().equals("wildroots.journal.guide.climate.5")), hint);
        }
    }
}
