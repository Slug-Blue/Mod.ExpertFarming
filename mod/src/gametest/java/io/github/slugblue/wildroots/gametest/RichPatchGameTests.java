package io.github.slugblue.wildroots.gametest;

import io.github.slugblue.wildroots.WildRoots;
import io.github.slugblue.wildroots.plant.PlantBlockEntity;
import io.github.slugblue.wildroots.worldgen.RichConfig;
import io.github.slugblue.wildroots.worldgen.WildPatchConfig;
import io.github.slugblue.wildroots.worldgen.WildPatchFeature;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** WR-153: rich patches (worldgen §5.1–5.3; GDD §3.2). */
@GameTestHolder(WildRoots.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RichPatchGameTests {

    static final String FLOOR = SmokeGameTests.FLOOR_3X3X3;
    static final ResourceLocation BOULDER = new ResourceLocation("wildroots", "patch_landmark/boulder");

    private RichPatchGameTests() {
    }

    /** The carrot config with {@code rich.chance = 1} and the given distance from spawn. */
    static WildPatchConfig alwaysRich(ServerLevel level, int minFromSpawn) {
        var boulder = level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE)
                .getHolderOrThrow(ResourceKey.create(Registries.CONFIGURED_FEATURE, BOULDER));
        return WildPatchGameTests.carrotConfig(level).withRich(Optional.of(new RichConfig(1.0f, minFromSpawn,
                UniformInt.of(14, 20), Optional.of(boulder))));
    }

    static boolean allRich(ServerLevel level, WildPatchFeature.Result r, boolean expected) {
        return r.plants().stream().allMatch(p -> level.getBlockEntity(p) instanceof PlantBlockEntity be
                && be.data().passport().rich() == expected);
    }

    /** A rich patch: the passport flag on every plant, more plants, a landmark boulder and a nest. */
    @GameTest(template = FLOOR, batch = "wr153_sky_rich")
    public static void richPatchIsMarkedBiggerWithLandmark(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SkyPlatform sky = SkyPlatform.build(helper, 16);
        try {
            BlockPos origin = sky.surface();
            WildPatchFeature.Result r = WildPatchGameTests.place(level, origin, alwaysRich(level, 0),
                    WildPatchGameTests.TEST);
            helper.assertTrue(r.placed() && r.rich(), "rich patch not placed: " + r);
            helper.assertTrue(allRich(level, r, true), "a plant without the rich flag");
            helper.assertTrue(r.plants().size() >= 14 && r.plants().size() <= 20,
                    "rich patch with " + r.plants().size() + " plants");
            boolean boulder = false;
            for (BlockPos q : BlockPos.betweenClosed(origin.offset(-9, -1, -9), origin.offset(9, 4, 9))) {
                if (level.getBlockState(q).is(Blocks.MOSSY_COBBLESTONE)) {
                    boulder = true;
                    break;
                }
            }
            helper.assertTrue(boulder, "no landmark boulder at the rich patch");
            helper.assertTrue(r.nest().kind() != io.github.slugblue.wildroots.worldgen.BeeNests.Kind.NONE,
                    "rich patch without a nest");
            PlantBlockEntity be = (PlantBlockEntity) level.getBlockEntity(r.plants().get(0));
            helper.assertTrue(be.live(level).isPresent(), "rich wild genome not realised");
        } finally {
            sky.clear();
        }
        helper.succeed();
    }

    /** Review M4: 5 rich patches (5 seeds) on flat ground each have 14–20 plants. */
    @GameTest(template = FLOOR, batch = "wr153_sky_counts")
    public static void richCountsMatchTheConfig(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SkyPlatform sky = SkyPlatform.build(helper, 12);
        try {
            WildPatchConfig c = alwaysRich(level, 0).withBeeNest(Optional.empty());
            for (int i = 0; i < 5; i++) {
                sky.fill(Blocks.GRASS_BLOCK.defaultBlockState());
                WildPatchFeature.Result r = WildPatchGameTests.place(level, sky.surface().offset(i - 2, 0, 0), c,
                        WildPatchGameTests.TEST);
                int k = r.plants().size();
                helper.assertTrue(r.placed() && r.rich() && k >= 14 && k <= 20, "rich patch " + i + ": " + k);
            }
        } finally {
            sky.clear();
        }
        helper.succeed();
    }

    /** Closer than {@code min_distance_from_spawn} the patch is ordinary (worldgen §5.2). */
    @GameTest(template = FLOOR, batch = "wr153_sky_spawn")
    public static void nearSpawnIsNeverRich(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SkyPlatform sky = SkyPlatform.build(helper, 8);
        try {
            WildPatchFeature.Result r = WildPatchGameTests.place(level, sky.surface(), alwaysRich(level, 1_000_000),
                    WildPatchGameTests.TEST);
            helper.assertTrue(r.placed() && !r.rich() && allRich(level, r, false), "rich near spawn: " + r);
        } finally {
            sky.clear();
        }
        helper.succeed();
    }

    /** The starter patch is never rich (worldgen §5.2). */
    @GameTest(template = FLOOR, batch = "wr153_sky_starter")
    public static void starterIsNeverRich(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SkyPlatform sky = SkyPlatform.build(helper, 14);
        try {
            WildPatchFeature.Result r = WildPatchGameTests.place(level, sky.surface(), alwaysRich(level, 0),
                    BeeNestGameTests.STARTER);
            helper.assertTrue(r.placed() && !r.rich() && allRich(level, r, false), "rich starter: " + r);
        } finally {
            sky.clear();
        }
        helper.succeed();
    }

    /** The carrot line ships rich patches: 8 %, 256 from spawn, 14–20 plants, boulder landmark. */
    @GameTest(template = FLOOR)
    public static void carrotRichConfig(GameTestHelper helper) {
        RichConfig r = WildPatchGameTests.carrotConfig(helper.getLevel()).rich().orElseThrow();
        helper.assertTrue(r.chance() == 0.08f && r.minFromSpawn() == 256 && r.plants().getMinValue() == 14
                && r.plants().getMaxValue() == 20 && r.landmark().isPresent(), "carrot rich " + r);
        helper.succeed();
    }
}
