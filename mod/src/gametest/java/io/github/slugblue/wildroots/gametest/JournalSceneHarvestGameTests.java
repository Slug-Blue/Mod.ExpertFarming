package io.github.slugblue.wildroots.gametest;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.slugblue.wildroots.WildRoots;
import io.github.slugblue.wildroots.genetics.pollination.Brush;
import io.github.slugblue.wildroots.plant.FieldPlantBlock;
import io.github.slugblue.wildroots.plant.PendingLot;
import io.github.slugblue.wildroots.plant.PlantBlockEntity;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * WR-181l (journal v2 §3.3, §3.6, §5.7): the items that pop out of the
 * plants in the two carrot scenes are the real drops of {@code HarvestService}.
 * The scene files are read here with plain Gson (the scene engine is client
 * code, never loaded on a server): every {@code pop_item} is grouped by step
 * and cell, the plant in that cell just before the pop is found by
 * replaying {@code set_block}, and the group's item ids must equal the drop
 * ids of a real plant of that stage, harvested by a player (stages 0–3 a
 * plant from seed, 4–5 a planted mother root). Also: a plant that is not a
 * mother root does not leave stage 3 (В2, В6).
 */
@GameTestHolder(WildRoots.MOD_ID)
@PrefixGameTestTemplate(false)
public final class JournalSceneHarvestGameTests {

    static final List<String> SCENES = List.of("carrot_stages.json", "carrot_cycle.json");
    static final Pattern STAGE = Pattern.compile("wildroots:wild_carrot\\[.*?stage=(\\d).*]");

    private JournalSceneHarvestGameTests() {
    }

    static JsonObject scene(String file) {
        try {
            Path p = ModList.get().getModFileById(WildRoots.MOD_ID).getFile()
                    .findResource("assets", WildRoots.MOD_ID, "journal", "scenes", file);
            return JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            throw new IllegalStateException("cannot read scene " + file + ": " + e);
        }
    }

    static String key(JsonArray pos) {
        return pos.get(0).getAsInt() + "," + pos.get(1).getAsInt() + "," + pos.get(2).getAsInt();
    }

    /** Stage of each harvested plant → item ids that popped out of it, over every step (and every variant). */
    static Map<String, Map.Entry<Integer, Set<String>>> harvests(JsonObject o) {
        Map<String, String> cells = new HashMap<>();
        JsonObject palette = o.getAsJsonObject("palette");
        JsonArray layers = o.getAsJsonArray("layers");
        for (int y = 0; y < layers.size(); y++) {
            JsonArray rows = layers.get(y).getAsJsonArray();
            for (int z = 0; z < rows.size(); z++) {
                String row = rows.get(z).getAsString();
                for (int x = 0; x < row.length(); x++) {
                    cells.put(x + "," + y + "," + z, palette.get(String.valueOf(row.charAt(x))).getAsString());
                }
            }
        }
        Map<String, Map.Entry<Integer, Set<String>>> out = new LinkedHashMap<>();
        JsonArray steps = o.getAsJsonArray("steps");
        for (int n = 0; n < steps.size(); n++) {
            JsonArray actions = steps.get(n).getAsJsonObject().getAsJsonArray("actions");
            List<JsonObject> sorted = actions.asList().stream().map(JsonElement::getAsJsonObject)
                    .sorted(java.util.Comparator.comparingInt(a -> a.get("at").getAsInt())).toList();
            Map<String, String> now = new HashMap<>(cells);
            for (JsonObject a : sorted) {
                if (!a.get("type").getAsString().equals("pop_item")) {
                    continue;
                }
                int at = a.get("at").getAsInt();
                String cell = key(a.getAsJsonArray("pos"));
                Map<String, String> before = new HashMap<>(cells);
                for (JsonObject b : sorted) {
                    if (b.get("at").getAsInt() < at && b.get("type").getAsString().equals("set_block")) {
                        before.put(key(b.getAsJsonArray("pos")), b.get("state").getAsString());
                    }
                }
                Matcher m = STAGE.matcher(before.getOrDefault(cell, "minecraft:air"));
                if (!m.matches()) {
                    throw new IllegalStateException("step " + (n + 1) + ": pop at " + cell + " from " + before.get(cell));
                }
                out.computeIfAbsent((n + 1) + "@" + cell,
                        k -> Map.entry(Integer.parseInt(m.group(1)), new TreeSet<>()))
                        .getValue().add(a.get("item").getAsString());
            }
            for (JsonObject b : sorted) {
                if (b.get("type").getAsString().equals("set_block")) {
                    now.put(key(b.getAsJsonArray("pos")), b.get("state").getAsString());
                }
            }
            // variants (unless / unlock) set the same cells, so either order leaves the same blocks
            cells = now;
        }
        return out;
    }

    /** Item ids a player gets from a real plant at {@code stage} (a planted mother root from stage 4). */
    static Set<String> realDrops(GameTestHelper helper, int stage, boolean mother, Player player, long key) {
        helper.setBlock(PlantFixtures.PLANT, Blocks.AIR);
        PlantBlockEntity be = PlantFixtures.place(helper, PlantFixtures.PLANT, stage);
        if (mother) {
            PlantFixtures.mother(be, key);
        } else {
            PlantFixtures.plant(be, key);
        }
        if (stage == FieldPlantBlock.SEED_STAGE) {
            // pollinated by bees, as in the scenes
            be.setPendingLot(new PendingLot(HarvestGameTests.fathers(3), 0.6f, Brush.NONE, true));
        }
        List<ItemStack> d = MotherRootGameTests.drops(helper, PlantFixtures.PLANT, player);
        return d.stream().map(s -> ForgeRegistries.ITEMS.getKey(s.getItem()).toString())
                .collect(Collectors.toCollection(TreeSet::new));
    }

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3)
    public static void scenePopsAreTheRealDrops(GameTestHelper helper) {
        Player player = helper.makeMockSurvivalPlayer();
        Map<Integer, Set<String>> real = new HashMap<>();
        for (int stage = 0; stage <= FieldPlantBlock.MAX_STAGE; stage++) {
            real.put(stage, realDrops(helper, stage, stage > FieldPlantBlock.HARVEST_STAGE, player, 9100 + stage));
        }
        // §3.3 table: a planted mother root at stage 3 gives only itself back
        Set<String> motherAt3 = realDrops(helper, FieldPlantBlock.HARVEST_STAGE, true, player, 9110);
        helper.assertTrue(motherAt3.equals(Set.of("wildroots:mother_root")), "mother at stage 3: " + motherAt3);
        int checked = 0;
        for (String file : SCENES) {
            for (Map.Entry<String, Map.Entry<Integer, Set<String>>> h : harvests(scene(file)).entrySet()) {
                int stage = h.getValue().getKey();
                Set<String> pops = h.getValue().getValue();
                helper.assertTrue(pops.equals(real.get(stage)), file + " step@cell " + h.getKey() + ", stage " + stage
                        + ": scene pops " + pops + ", real drops " + real.get(stage));
                checked++;
            }
        }
        helper.assertTrue(checked >= 8, "only " + checked + " harvests in the scenes");
        helper.succeed();
    }

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3, timeoutTicks = 200)
    public static void plantFromSeedWaitsAtStageThree(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        PlantFixtures.plant(PlantFixtures.place(helper, PlantFixtures.PLANT, FieldPlantBlock.HARVEST_STAGE), 9120);
        BlockPos abs = helper.absolutePos(PlantFixtures.PLANT);
        RandomSource r = RandomSource.create(9121);
        for (int i = 0; i < 5000; i++) {
            level.getBlockState(abs).randomTick(level, abs, r);
        }
        helper.assertTrue(PlantFixtures.stage(helper, PlantFixtures.PLANT) == FieldPlantBlock.HARVEST_STAGE,
                "left stage 3: " + PlantFixtures.stage(helper, PlantFixtures.PLANT));
        helper.succeed();
    }
}
