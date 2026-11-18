package io.github.slugblue.wildroots.client.journal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.slugblue.wildroots.client.journal.page.CraftingPage;
import io.github.slugblue.wildroots.client.journal.page.LinksPage;
import io.github.slugblue.wildroots.client.journal.page.ScenePage;
import io.github.slugblue.wildroots.client.journal.page.SpotlightPage;
import io.github.slugblue.wildroots.client.journal.page.TextPage;
import io.github.slugblue.wildroots.client.journal.page.TraitLegendPage;
import io.github.slugblue.wildroots.core.Ids;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.IoSupplier;
import org.junit.jupiter.api.Test;

/**
 * WR-181a: the guide loader (journal v2 §5.2). Every limit: a file, entry
 * or page over it is skipped with exactly one log line and nothing throws;
 * unknown page types and items are skipped. Pure code, no game.
 */
class GuideLoaderTest {

    static final Set<String> ITEMS = Set.of("wildroots:hand_lens", "wildroots:mother_root", "wildroots:seed_lot",
            "wildroots:field_journal", "minecraft:beehive");
    static final GuideLoader.Registries REG = rl -> ITEMS.contains(rl.toString());

    static final String CAT = "{\"id\": \"plant\", \"icon\": \"wildroots:mother_root\", \"group\": \"guide\", \"order\": 20}";

    static Map<ResourceLocation, IoSupplier<InputStream>> files(Map<String, String> byPath) {
        Map<ResourceLocation, IoSupplier<InputStream>> out = new LinkedHashMap<>();
        byPath.forEach((path, text) -> out.put(rl(path),
                () -> new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8))));
        return out;
    }

    static ResourceLocation rl(String path) {
        return path.contains(":") ? ResourceLocation.tryParse(path) : Ids.mod(path);
    }

    static String entry(String id, String extra, String pages) {
        return "{\"id\": \"" + id + "\", \"category\": \"plant\"" + extra + ", \"pages\": [" + pages + "]}";
    }

    static final String TEXT = "{\"type\": \"text\", \"text\": \"wildroots.journal.guide.x.1\"}";

    static GuideLoader.Result load(Map<String, String> byPath) {
        return GuideLoader.load(files(byPath), REG);
    }

    static Map<String, String> base() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("journal/categories/plant.json", CAT);
        return m;
    }

    @Test
    void aValidBookLoadsEveryPageType() {
        Map<String, String> m = base();
        m.put("journal/categories/first.json", "{\"id\": \"first_steps\", \"icon\": \"gui:first_steps\", \"order\": 10}");
        m.put("journal/entries/cycle.json", "{\"id\": \"guide.cycle\", \"category\": \"plant\", \"icon\": \"wildroots:mother_root\","
                + " \"order\": 30, \"unlock\": [\"hint.mother_plant\"], \"items\": [\"wildroots:mother_root\"],"
                + " \"see_also\": [\"guide.stages\"], \"pages\": ["
                + "{\"type\": \"scene\", \"scene\": \"scene.carrot_cycle\"},"
                + "{\"type\": \"spotlight\", \"item\": \"wildroots:mother_root\", \"variant\": \"colour=yellow\","
                + " \"text\": \"wildroots.journal.hint.mother_plant\", \"owned_badge\": true, \"unlock\": [\"species.*\"]},"
                + "{\"type\": \"crafting\", \"recipe\": \"wildroots:hand_lens\", \"text\": \"wildroots.journal.guide.lens.2\"},"
                + "{\"type\": \"trait_legend\", \"example\": [4, 3, 2]},"
                + "{\"type\": \"links\", \"links\": [\"guide.stages\", \"guide.pollination\"]},"
                + TEXT + "]}");
        m.put("journal/entries/stages.json", entry("guide.stages", ", \"order\": 10", TEXT));
        m.put("journal/hint_links.json", "{\"hint.mother_plant\": \"guide.cycle\"}");
        GuideLoader.Result r = load(m);
        assertEquals(List.of(), r.log());
        GuideBook b = r.book();
        assertEquals(List.of("first_steps", "plant"), b.categories().stream().map(GuideCategory::id).toList());
        assertEquals(GuideIcon.sprite("first_steps"), b.categories().get(0).icon());
        assertEquals(List.of("guide.stages", "guide.cycle"), b.entriesOf("plant").stream().map(GuideEntry::id).toList(),
                "entries by order");
        GuideEntry cycle = b.entry("guide.cycle").orElseThrow();
        assertEquals("wildroots.journal.guide.cycle.title", cycle.titleKey());
        assertEquals(List.of("hint.mother_plant"), cycle.unlock());
        assertEquals(List.of(Ids.mod("mother_root")), cycle.items());
        assertEquals(List.of("scene", "spotlight", "crafting", "trait_legend", "links", "text"),
                cycle.pages().stream().map(GuidePage::type).toList());
        assertEquals(new ScenePage("scene.carrot_cycle"), cycle.pages().get(0).data());
        assertEquals(new SpotlightPage(Ids.mod("mother_root"), Optional.of("colour=yellow"),
                Optional.empty(), Optional.of("wildroots.journal.hint.mother_plant"), true), cycle.pages().get(1).data());
        assertEquals(List.of("species.*"), cycle.pages().get(1).unlock());
        assertEquals(new CraftingPage(Ids.mod("hand_lens"),
                Optional.of("wildroots.journal.guide.lens.2")), cycle.pages().get(2).data());
        assertEquals(new TraitLegendPage(Ids.mod("wild_carrot"), List.of(4, 3, 2)),
                cycle.pages().get(3).data());
        assertEquals(new LinksPage(Optional.empty(), List.of("guide.stages", "guide.pollination")),
                cycle.pages().get(4).data());
        assertEquals(new TextPage(Optional.empty(), "wildroots.journal.guide.x.1"), cycle.pages().get(5).data());
        assertEquals(Optional.of("guide.cycle"), b.hintLink("hint.mother_plant"));
        assertEquals(List.of(cycle), b.entriesForItem(Ids.mod("mother_root")));
    }

    /** Loads {@code m}, expects exactly one log line containing {@code what}, returns the book. */
    static GuideBook oneLine(Map<String, String> m, String what) {
        GuideLoader.Result r = load(m);
        assertEquals(1, r.log().size(), () -> "one log line expected: " + r.log());
        assertTrue(r.log().get(0).contains(what), () -> r.log() + " should mention " + what);
        return r.book();
    }

    @Test
    void aFileOverItsLimitIsSkipped() {
        Map<String, String> m = base();
        String pad = " ".repeat(GuideLimits.MAX_FILE_BYTES);
        m.put("journal/entries/big.json", entry("guide.big", "", TEXT) + pad);
        m.put("journal/entries/ok.json", entry("guide.ok", "", TEXT));
        GuideBook b = oneLine(m, "big.json");
        assertEquals(List.of("guide.ok"), b.entries().stream().map(GuideEntry::id).toList());
    }

    @Test
    void filesOverTheTotalAreSkippedWithOneLine() {
        Map<String, String> m = base();
        String pad = " ".repeat(GuideLimits.MAX_FILE_BYTES - 200);
        int n = GuideLimits.MAX_TOTAL_BYTES / GuideLimits.MAX_FILE_BYTES + 3;
        for (int i = 0; i < n; i++) {
            m.put("journal/entries/e" + (char) ('a' + i) + ".json", entry("guide.e" + (char) ('a' + i), "", TEXT) + pad);
        }
        GuideBook b = oneLine(m, "together");
        int kept = b.entries().size();
        assertTrue(kept > 0 && kept < n, "some entries before the total, none after: " + kept);
    }

    @Test
    void categoriesOverTheLimitAreSkipped() {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i <= GuideLimits.MAX_CATEGORIES; i++) {
            m.put(String.format("journal/categories/c%02d.json", i), "{\"id\": \"c" + i + "\"}");
        }
        GuideBook b = oneLine(m, "categories");
        assertEquals(GuideLimits.MAX_CATEGORIES, b.categories().size());
    }

    @Test
    void entriesOverTheLimitAreSkipped() {
        Map<String, String> m = base();
        for (int i = 0; i <= GuideLimits.MAX_ENTRIES; i++) {
            m.put(String.format("journal/entries/e%03d.json", i), entry("guide.e" + i, "", TEXT));
        }
        GuideBook b = oneLine(m, "entries");
        assertEquals(GuideLimits.MAX_ENTRIES, b.entries().size());
    }

    @Test
    void anEntryWithTooManyPagesIsSkipped() {
        Map<String, String> m = base();
        String pages = IntStream.rangeClosed(0, GuideLimits.MAX_PAGES).mapToObj(i -> TEXT).collect(Collectors.joining(","));
        m.put("journal/entries/long.json", entry("guide.long", "", pages));
        m.put("journal/entries/max.json", entry("guide.max", "",
                IntStream.range(0, GuideLimits.MAX_PAGES).mapToObj(i -> TEXT).collect(Collectors.joining(","))));
        GuideBook b = oneLine(m, "pages");
        assertEquals(List.of("guide.max"), b.entries().stream().map(GuideEntry::id).toList());
    }

    static String nine(String prefix) {
        return IntStream.rangeClosed(0, GuideLimits.MAX_LIST)
                .mapToObj(i -> "\"" + prefix + i + "\"").collect(Collectors.joining(",", "[", "]"));
    }

    @Test
    void listsOverTheLimitSkipTheEntryOrPage() {
        for (String field : List.of("unlock", "see_also")) {
            Map<String, String> m = base();
            m.put("journal/entries/x.json", entry("guide.x", ", \"" + field + "\": " + nine("hint.a"), TEXT));
            assertEquals(List.of(), oneLine(m, field).entries(), field);
        }
        Map<String, String> m = base();
        m.put("journal/entries/x.json", entry("guide.x", ", \"items\": "
                + IntStream.rangeClosed(0, GuideLimits.MAX_LIST).mapToObj(i -> "\"wildroots:hand_lens\"")
                .collect(Collectors.joining(",", "[", "]")), TEXT));
        assertEquals(List.of(), oneLine(m, "items").entries());
        m = base();
        m.put("journal/entries/x.json", entry("guide.x", "", TEXT + ", {\"type\": \"links\", \"links\": " + nine("guide.a") + "}"));
        assertEquals(1, oneLine(m, "page 2").entries().get(0).pages().size(), "the page goes, the entry stays");
        m = base();
        m.put("journal/entries/x.json", entry("guide.x", "", "{\"type\": \"text\", \"text\": \"a.b\", \"unlock\": "
                + nine("hint.a") + "}," + TEXT));
        assertEquals(1, oneLine(m, "page 1").entries().get(0).pages().size());
    }

    @Test
    void stringsAndIdsOutOfShapeAreSkipped() {
        String longKey = "wildroots." + "a".repeat(GuideLimits.MAX_STRING);
        Map<String, String> m = base();
        m.put("journal/entries/x.json", entry("guide.x", "", "{\"type\": \"text\", \"text\": \"" + longKey + "\"}," + TEXT));
        assertEquals(1, oneLine(m, "longer than").entries().get(0).pages().size(), "the page goes, the entry stays");
        for (String badId : List.of("guide.X", "guide.a b", "guide." + "a".repeat(64), "plant.x", "guide.")) {
            m = base();
            m.put("journal/entries/x.json", entry(badId, "", TEXT));
            assertEquals(List.of(), oneLine(m, "x.json").entries(), badId);
        }
        m = base();
        m.put("journal/entries/x.json", entry("guide.x", ", \"unlock\": [\"*\"]", TEXT));
        assertEquals(List.of(), oneLine(m, "unlock").entries(), "a bare wildcard opens nothing");
    }

    @Test
    void brokenJsonIsSkipped() {
        for (String bad : List.of("{", "[]", "", "{'id': 'x'}", "{\"id\": NaN}", "{} {}",
                "[".repeat(GuideLimits.MAX_JSON_DEPTH + 1) + "]".repeat(GuideLimits.MAX_JSON_DEPTH + 1))) {
            Map<String, String> m = base();
            m.put("journal/entries/bad.json", bad);
            m.put("journal/entries/ok.json", entry("guide.ok", "", TEXT));
            GuideBook b = oneLine(m, "bad.json");
            assertEquals(1, b.entries().size(), bad);
        }
        Map<String, String> m = base();
        m.put("journal/entries/bom.json", "﻿" + entry("guide.ok", "", TEXT));
        assertEquals(List.of(), load(m).log(), "a UTF-8 byte order mark is fine");
    }

    @Test
    void anUnknownPageTypeOrItemSkipsThePage() {
        Map<String, String> m = base();
        m.put("journal/entries/x.json", entry("guide.x", "", TEXT + ", {\"type\": \"stage_strip\"}"));
        assertEquals(1, oneLine(m, "unknown page type").entries().get(0).pages().size());
        m = base();
        m.put("journal/entries/x.json", entry("guide.x", "", TEXT + ", {\"type\": \"spotlight\", \"item\": \"wildroots:nope\"}"));
        assertEquals(1, oneLine(m, "unknown item").entries().get(0).pages().size());
        m = base();
        m.put("journal/entries/x.json", entry("guide.x", "", TEXT
                + ", {\"type\": \"spotlight\", \"item\": \"wildroots:mother_root\", \"variant\": \"colour=green\"}"));
        assertEquals(1, oneLine(m, "variant").entries().get(0).pages().size());
        m = base();
        m.put("journal/entries/x.json", entry("guide.x", "", "{\"type\": \"stage_strip\"}"));
        GuideLoader.Result r = load(m);
        assertEquals(List.of(), r.book().entries(), "no page left: the entry goes");
        assertEquals(2, r.log().size(), "a line for the page and one for the entry: " + r.log());
    }

    @Test
    void anUnknownItemIconOrCategorySkipsTheEntry() {
        Map<String, String> m = base();
        m.put("journal/entries/x.json", entry("guide.x", ", \"icon\": \"wildroots:nope\"", TEXT));
        assertEquals(List.of(), oneLine(m, "unknown item").entries());
        m = base();
        m.put("journal/entries/x.json", entry("guide.x", ", \"items\": [\"wildroots:nope\"]", TEXT));
        assertEquals(List.of(), oneLine(m, "unknown item").entries());
        m = base();
        m.put("journal/entries/x.json", "{\"id\": \"guide.x\", \"category\": \"nope\", \"pages\": [" + TEXT + "]}");
        assertEquals(List.of(), oneLine(m, "unknown category").entries());
        m = base();
        m.put("journal/categories/odd.json", "{\"id\": \"odd\", \"group\": \"records\"}");
        assertEquals(1, oneLine(m, "group").categories().size(), "only guide categories come from JSON");
    }

    @Test
    void duplicatesAreSkippedAndOtherFilesIgnored() {
        Map<String, String> m = base();
        m.put("journal/entries/a.json", entry("guide.x", "", TEXT));
        m.put("journal/entries/b.json", entry("guide.x", "", TEXT));
        m.put("journal/categories/plant2.json", CAT);
        m.put("othermod:journal/entries/c.json", entry("guide.y", "", TEXT));
        m.put("journal/scenes/scene.carrot_cycle.json", "not even json");
        m.put("journal/readme.json", "{");
        GuideLoader.Result r = load(m);
        assertEquals(2, r.log().size(), r.log()::toString);
        assertEquals(List.of("guide.x"), r.book().entries().stream().map(GuideEntry::id).toList());
    }

    @Test
    void hintLinksKeepOnlyHintsToKnownEntries() {
        Map<String, String> m = base();
        m.put("journal/entries/x.json", entry("guide.x", "", TEXT));
        m.put("journal/hint_links.json", "{\"hint.a\": \"guide.x\", \"hint.b\": \"guide.nope\", \"species.c\": \"guide.x\","
                + " \"hint.d\": 4}");
        GuideLoader.Result r = load(m);
        assertEquals(Map.of("hint.a", "guide.x"), r.book().hintLinks());
        assertEquals(3, r.log().size(), r.log()::toString);
    }

    @Test
    void anUnreadableFileIsOneLine() {
        Map<ResourceLocation, IoSupplier<InputStream>> f = files(base());
        f.put(rl("journal/entries/io.json"), () -> {
            throw new IOException("disk");
        });
        GuideLoader.Result r = GuideLoader.load(f, REG);
        assertEquals(1, r.log().size());
        assertTrue(r.log().get(0).contains("io.json"));
    }

    static java.nio.file.Path modDir() {
        java.nio.file.Path p = java.nio.file.Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (java.nio.file.Path q = p; q != null; q = q.getParent()) {
            if (java.nio.file.Files.isDirectory(q.resolve("src/main/resources/assets/wildroots"))) {
                return q;
            }
            if (java.nio.file.Files.isDirectory(q.resolve("mod/src/main/resources/assets/wildroots"))) {
                return q.resolve("mod");
            }
        }
        throw new IllegalStateException("mod sources not found from " + p);
    }

    /** The guide shipped in the jar loads without a single log line (items: those with an item model). */
    @Test
    void theShippedGuideLoadsCleanly() throws IOException {
        java.nio.file.Path assets = modDir()
                .resolve("src/main/resources/assets/wildroots");
        Map<ResourceLocation, IoSupplier<InputStream>> f = new LinkedHashMap<>();
        try (var s = java.nio.file.Files.walk(assets.resolve("journal"))) {
            for (java.nio.file.Path p : s.filter(java.nio.file.Files::isRegularFile).toList()) {
                String rel = assets.relativize(p).toString().replace('\\', '/');
                f.put(Ids.mod(rel), () -> java.nio.file.Files.newInputStream(p));
            }
        }
        Set<String> models;
        try (var s = java.nio.file.Files.list(assets.resolve("models/item"))) {
            models = s.map(p -> "wildroots:" + p.getFileName().toString().replace(".json", ""))
                    .collect(Collectors.toSet());
        }
        GuideLoader.Result r = GuideLoader.load(f, rl -> models.contains(rl.toString())
                || rl.getNamespace().equals("minecraft"));
        assertEquals(List.of(), r.log());
        assertTrue(r.book().entry("guide.start").isPresent());
        assertEquals(16, r.book().entries().size(), "M1a entries plus WR-713 climate guide");
        assertTrue(r.book().entry("guide.climate").isPresent());
        // unlock only on ids the server can send (§5.7): known hints, species/cultivar kinds, tools with an item
        List<String> unlocks = new ArrayList<>();
        for (GuideEntry e : r.book().entries()) {
            unlocks.addAll(e.unlock());
            e.pages().forEach(p -> unlocks.addAll(p.unlock()));
            for (String s : e.seeAlso()) {
                assertTrue(r.book().entry(s).isPresent(), e.id() + " sees also unknown " + s);
            }
        }
        for (String u : unlocks) {
            boolean ok = u.startsWith("hint.") && io.github.slugblue.wildroots.journal.JournalHints
                    .line(u.substring(5)).isPresent()
                    || u.equals("species.*") || u.equals("cultivar.*")
                    || u.startsWith("tool.") && models.contains("wildroots:" + u.substring(5));
            assertTrue(ok, "unlock id the server never sends: " + u);
        }
        for (String h : r.book().hintLinks().keySet()) {
            assertTrue(io.github.slugblue.wildroots.journal.JournalHints.line(h.substring(5)).isPresent(), h);
        }
    }

    /** Review M1: a pack with 10 000 broken or oversized files costs a bounded number of reads and log lines. */
    @Test
    void aFloodOfBadFilesIsReadAndLoggedBoundedly() {
        int[] reads = {0};
        Map<ResourceLocation, IoSupplier<InputStream>> flood = new LinkedHashMap<>();
        for (int i = 0; i < 10_000; i++) {
            flood.put(rl(String.format("journal/entries/e%05d.json", i)), () -> {
                reads[0]++;
                return new ByteArrayInputStream("x".getBytes(StandardCharsets.UTF_8));
            });
        }
        GuideLoader.Result r = GuideLoader.load(flood, REG);
        assertTrue(reads[0] <= GuideLimits.MAX_CANDIDATES, "reads " + reads[0]);
        assertEquals(GuideLimits.MAX_LOG_LINES + 1, r.log().size(), "16 lines and one summary");
        assertTrue(r.log().get(GuideLimits.MAX_LOG_LINES).contains("more problems"), r.log()::toString);

        reads[0] = 0;
        Map<ResourceLocation, IoSupplier<InputStream>> big = new LinkedHashMap<>();
        byte[] oversized = new byte[100];
        for (int i = 0; i < 10_000; i++) {
            big.put(rl(String.format("journal/scenes/s%05d.json", i)), () -> {
                reads[0]++;
                return new ByteArrayInputStream(oversized);
            });
        }
        List<String> log = new ArrayList<>();
        SortedMap<ResourceLocation, byte[]> got = GuideFiles.read(big, "scenes", 10, 32, 1000, log);
        assertEquals(0, got.size());
        assertTrue(reads[0] <= 32 + GuideLimits.SECTION_SLACK, "oversized files count as candidates: " + reads[0]);
        assertTrue(GuideLoader.capLog(log, "journal/scenes/").size() <= GuideLimits.MAX_LOG_LINES + 1);
    }

    @Test
    void sectionFilesFollowTheirOwnLimits() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("journal/scenes/a.json", "x".repeat(10));
        m.put("journal/scenes/b.json", "x".repeat(30));
        m.put("journal/scenes/c.json", "x".repeat(10));
        m.put("journal/scenes/d.json", "x".repeat(10));
        m.put("journal/scenes/e.json", "x".repeat(10));
        m.put("journal/entries/z.json", "x");
        List<String> log = new ArrayList<>();
        SortedMap<ResourceLocation, byte[]> got = GuideFiles.read(files(m), "scenes", 20, 3, 25, log);
        assertEquals(List.of(rl("journal/scenes/a.json"), rl("journal/scenes/c.json")), List.copyOf(got.keySet()));
        assertEquals(2, log.size(), log::toString);
        assertTrue(log.get(0).contains("b.json") && log.get(1).contains("together"), log::toString);
        log.clear();
        got = GuideFiles.read(files(m), "scenes", 100, 2, 1000, log);
        assertEquals(2, got.size());
        assertEquals(1, log.size(), "one line for all files past the count");
    }
}
