package io.github.slugblue.wildroots.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.Codec;
import io.github.slugblue.wildroots.core.Ids;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.IoSupplier;
import org.junit.jupiter.api.Test;

/** WR-126, SEC-27: oversized, deep, garbage, BOM and empty files are problems, never exceptions. */
class RobustLoaderTest {

    static IoSupplier<InputStream> bytes(byte[] b) {
        return () -> new ByteArrayInputStream(b);
    }

    static IoSupplier<InputStream> text(String s) {
        return bytes(s.getBytes(StandardCharsets.UTF_8));
    }

    static ResourceLocation file(String name) {
        return Ids.mod("crop_species/" + name + ".json");
    }

    static List<String> linesOf(String name, DefinitionLoader.LoadResult r) {
        return r.lines().stream().map(DefinitionLoader.LogLine::text)
                .filter(l -> l.startsWith(file(name) + ": ")).toList();
    }

    @Test
    void hostileFilesBecomeProblemsAndTheGoodOneLoads() {
        Map<ResourceLocation, IoSupplier<InputStream>> files = new LinkedHashMap<>();
        byte[] big = new byte[1024 * 1024];
        java.util.Arrays.fill(big, (byte) ' ');
        files.put(file("huge"), bytes(big));
        files.put(file("deep"), text("[".repeat(10_000) + "]".repeat(10_000)));
        files.put(file("deep_object"), text("{\"a\":".repeat(10_000) + "1" + "}".repeat(10_000)));
        files.put(file("garbage"), text("\u0000\u0001 not json {{{"));
        files.put(file("empty"), text(""));
        files.put(file("blank"), text("  \n\t "));
        files.put(file("not_object"), text("[1, 2, 3]"));
        files.put(file("bom"), text("﻿" + DefinitionLoaderTest.CARROT));
        DefinitionLoader.LoadResult r = DefinitionLoader.load(files, Map.of());

        assertTrue(linesOf("huge", r).get(0).contains("larger than 256 KiB"), linesOf("huge", r)::toString);
        assertTrue(linesOf("deep", r).get(0).contains("deeper than 32"), linesOf("deep", r)::toString);
        assertTrue(linesOf("deep_object", r).get(0).contains("deeper than 32"));
        assertEquals(1, linesOf("garbage", r).size());
        assertTrue(linesOf("empty", r).get(0).contains("empty file"));
        assertTrue(linesOf("blank", r).get(0).contains("empty file"));
        assertTrue(linesOf("not_object", r).get(0).contains("object expected"), linesOf("not_object", r)::toString);
        assertEquals(List.of(), linesOf("bom", r), "a BOM is not an error");
        assertTrue(r.snapshot().species("wildroots:wild_carrot").isPresent(), "the good file did not load");
        assertEquals(7, r.snapshot().stats().speciesSkipped());
    }

    @Test
    void fileAtTheSizeLimitIsRead() {
        String carrot = DefinitionLoaderTest.CARROT;
        String padded = carrot + " ".repeat(DefinitionLoader.MAX_FILE_BYTES - carrot.getBytes(StandardCharsets.UTF_8).length);
        assertEquals(DefinitionLoader.MAX_FILE_BYTES, padded.getBytes(StandardCharsets.UTF_8).length);
        DefinitionLoader.LoadResult r = DefinitionLoader.load(Map.of(file("wild_carrot"), text(padded)), Map.of());
        assertEquals(List.of(), r.lines());
        DefinitionLoader.LoadResult over = DefinitionLoader.load(Map.of(file("wild_carrot"), text(padded + " ")), Map.of());
        assertEquals(1, over.lines().size());
    }

    @Test
    void depthCountIgnoresBracketsInStrings() {
        assertEquals(1, DefinitionLoader.jsonDepth("{\"a\": \"[[[[{{{{\\\"]]]\"}", 32));
        assertEquals(3, DefinitionLoader.jsonDepth("{\"a\": [ {\"b\": 1} ]}", 32));
        assertEquals(33, DefinitionLoader.jsonDepth("[".repeat(100), 32));
        assertEquals(32, DefinitionLoader.jsonDepth("[".repeat(32) + "]".repeat(32), 32));
        // Gson in lenient mode reads single-quoted strings: brackets inside them are not nesting
        assertEquals(1, DefinitionLoader.jsonDepth("{'a': '" + "}}}{{{[[[".repeat(20) + "'}", 32));
        assertEquals(1, DefinitionLoader.jsonDepth("{'a\\'b': 'it\\'s [[['}", 32));
    }

    @Test
    void stackOverflowInsideOneRecordIsCaught() {
        Codec<String> overflowing = Codec.STRING.flatXmap(s -> {
            throw new StackOverflowError("deep mapper");
        }, com.mojang.serialization.DataResult::success);
        DefinitionLoader.Parsed<String> p = DefinitionLoader.parseOne(file("so"), text("\"x\""), overflowing,
                s -> s, s -> List.of());
        assertTrue(p.mapped().isEmpty());
        assertTrue(p.problems().get(0).message().contains("StackOverflowError"), p.problems()::toString);

        DefinitionLoader.Parsed<String> fromMapper = DefinitionLoader.parseOne(file("so2"), text("\"x\""), Codec.STRING,
                s -> {
                    throw new StackOverflowError("mapper");
                }, s -> List.of());
        assertTrue(fromMapper.problems().get(0).message().contains("StackOverflowError"));
    }

    @Test
    void atMostTwoHundredProblemLinesThenAndMore() {
        Map<ResourceLocation, IoSupplier<InputStream>> files = new LinkedHashMap<>();
        for (int i = 0; i < 500; i++) {
            files.put(file("bad_" + i), text("{}"));
        }
        DefinitionLoader.LoadResult r = DefinitionLoader.load(files, Map.of());
        assertTrue(r.lines().size() >= 500);
        List<DefinitionLoader.LogLine> logged = CultivarManager.limited(r.lines());
        assertEquals(201, logged.size());
        assertEquals("Wild Roots: ... and " + (r.lines().size() - 200) + " more problems", logged.get(200).text());
        List<DefinitionLoader.LogLine> few = new ArrayList<>(r.lines().subList(0, 200));
        assertEquals(few, CultivarManager.limited(few));
    }
}
