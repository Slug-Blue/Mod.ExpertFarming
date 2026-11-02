package io.github.slugblue.wildroots.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraftforge.common.ForgeConfigSpec;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * WR-120, SEC-48: every numeric parameter has a range. Forge clamps an
 * out-of-range number to the nearest bound ({@code ForgeConfigSpec.Range#correct})
 * and resets a value of the wrong type to the default.
 */
class ConfigSpecTest {

    record Leaf(String path, ForgeConfigSpec.ValueSpec spec) {
    }

    static List<Leaf> leaves(UnmodifiableConfig config, String prefix) {
        List<Leaf> out = new ArrayList<>();
        for (Map.Entry<String, Object> e : config.valueMap().entrySet()) {
            String path = prefix.isEmpty() ? e.getKey() : prefix + "." + e.getKey();
            if (e.getValue() instanceof UnmodifiableConfig sub) {
                out.addAll(leaves(sub, path));
            } else if (e.getValue() instanceof ForgeConfigSpec.ValueSpec v) {
                out.add(new Leaf(path, v));
            }
        }
        return out;
    }

    static Stream<Leaf> all() {
        return Stream.concat(leaves(CommonConfig.SPEC.getSpec(), "common").stream(),
                leaves(ServerConfig.SPEC.getSpec(), "server").stream());
    }

    @TestFactory
    Stream<DynamicTest> everyNumberIsDefinedInRange() {
        List<Leaf> numeric = all().filter(l -> l.spec().getDefault() instanceof Number).toList();
        assertFalse(numeric.isEmpty(), "the specs declare numbers");
        return numeric.stream().map(l -> DynamicTest.dynamicTest(l.path(), () -> {
            ForgeConfigSpec.Range<?> range = l.spec().getRange();
            assertNotNull(range, l.path() + " is not declared with defineInRange");
            assertTrue(l.spec().test(l.spec().getDefault()), l.path() + ": default outside its range");
        }));
    }

    @Test
    void boundsFromSecurityDocument() {
        assertRange(ServerConfig.PACKET_FLOOD_DISCONNECT_THRESHOLD, 0, 10_000, 200);
        assertRange(ServerConfig.BREEDER_RARE_FIND_DAILY_CAP, 0, 64, 6);
        assertRange(ServerConfig.PACKET_BUCKET_CAPACITY, 1, 100, 5);
        assertRange(ServerConfig.PACKET_REFILL_PER_SECOND, 1, 100, 5);
        assertEquals(ServerConfig.Mode.STRICT, ServerConfig.MODE.getDefault());
        assertEquals(ServerConfig.StarterPatch.TRUE, ServerConfig.STARTER_PATCH.getDefault());
        assertEquals(List.of(), CommonConfig.DISABLED_LINES.getDefault());
    }

    @Test
    void outOfRangeValuesAreCorrected() {
        CommentedConfig cfg = CommentedConfig.inMemory();
        ServerConfig.SPEC.correct(cfg);
        cfg.set(List.of("network", "packetFloodDisconnectThreshold"), 10_001);
        cfg.set(List.of("economy", "breederRareFindDailyCap"), -1);
        cfg.set(List.of("gameplay", "mode"), "creative");
        assertFalse(ServerConfig.SPEC.isCorrect(cfg));
        ServerConfig.SPEC.correct(cfg);
        assertEquals(10_000, (int) cfg.<Integer>get(List.of("network", "packetFloodDisconnectThreshold")));
        assertEquals(0, (int) cfg.<Integer>get(List.of("economy", "breederRareFindDailyCap")));
        cfg.set(List.of("economy", "breederRareFindDailyCap"), "many");
        ServerConfig.SPEC.correct(cfg);
        assertEquals(6, (int) cfg.<Integer>get(List.of("economy", "breederRareFindDailyCap")));
        assertEquals("STRICT", String.valueOf(cfg.<Object>get(List.of("gameplay", "mode"))));
    }

    @Test
    void disabledLinesRejectsMalformedIds() {
        CommentedConfig cfg = CommentedConfig.inMemory();
        CommonConfig.SPEC.correct(cfg);
        cfg.set(List.of("lines", "disabledLines"), List.of("carrot", "Bad Id!"));
        CommonConfig.SPEC.correct(cfg);
        assertEquals(List.of("carrot"), cfg.get(List.of("lines", "disabledLines")));
    }

    @Test
    void lowerCaseModeIsAccepted() {
        CommentedConfig cfg = CommentedConfig.inMemory();
        ServerConfig.SPEC.correct(cfg);
        cfg.set(List.of("gameplay", "mode"), "soft");
        assertTrue(ServerConfig.SPEC.isCorrect(cfg), "enum values are read ignoring case");
    }

    private static void assertRange(ForgeConfigSpec.IntValue v, int min, int max, int def) {
        ForgeConfigSpec.ValueSpec spec = all().filter(l -> l.path().endsWith("." + v.getPath().get(v.getPath().size() - 1)))
                .findFirst().orElseThrow().spec();
        ForgeConfigSpec.Range<Integer> r = spec.getRange();
        assertEquals(min, r.getMin());
        assertEquals(max, r.getMax());
        assertEquals(def, v.getDefault());
    }
}
