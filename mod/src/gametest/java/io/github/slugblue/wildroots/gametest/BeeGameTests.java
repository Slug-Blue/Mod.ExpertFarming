package io.github.slugblue.wildroots.gametest;

import io.github.slugblue.wildroots.WildRoots;
import io.github.slugblue.wildroots.core.registry.ModBlocks;
import io.github.slugblue.wildroots.genetics.codec.FatherRecord;
import io.github.slugblue.wildroots.plant.FieldPlantBlock;
import io.github.slugblue.wildroots.plant.PlantBlockEntity;
import io.github.slugblue.wildroots.plant.PlantNbt;
import io.github.slugblue.wildroots.pollination.BeeCropPollinateGoal;
import io.github.slugblue.wildroots.pollination.BeeEvents;
import io.github.slugblue.wildroots.pollination.BeePollen;
import io.github.slugblue.wildroots.pollination.BeePollenCapability;
import io.github.slugblue.wildroots.pollination.PollinationService;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** WR-142: bees, pollen, donors (docs/05-architecture.md §10.3; ADR-0009; GDD §4.3; S2). */
@GameTestHolder(WildRoots.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BeeGameTests {

    static final String FIELD = SeedSetGameTests.FIELD;
    static final long SETUP = SeedSetGameTests.SETUP;

    static final BlockPos[] HIVES = {new BlockPos(7, 2, 7), new BlockPos(9, 2, 7), new BlockPos(7, 2, 9),
            new BlockPos(9, 2, 9)};
    static final BlockPos[] PLANTS = {
            new BlockPos(5, 2, 5), new BlockPos(8, 2, 4), new BlockPos(11, 2, 5), new BlockPos(4, 2, 8),
            new BlockPos(12, 2, 8), new BlockPos(5, 2, 11), new BlockPos(8, 2, 12), new BlockPos(11, 2, 11)};
    static final BlockPos[] FLOWERS_NEAR = {
            new BlockPos(5, 2, 6), new BlockPos(8, 2, 5), new BlockPos(11, 2, 6), new BlockPos(5, 2, 8),
            new BlockPos(11, 2, 8), new BlockPos(5, 2, 10), new BlockPos(8, 2, 11), new BlockPos(11, 2, 10)};

    private BeeGameTests() {
    }

    static BeePollen pollen(Bee bee) {
        return bee.getCapability(BeePollenCapability.CAPABILITY).orElseThrow(IllegalStateException::new);
    }

    static long ourGoals(Bee bee) {
        return bee.goalSelector.getAvailableGoals().stream().filter(w -> w.getGoal() instanceof BeeCropPollinateGoal)
                .count();
    }

    /** Donor entries of the plant, read from its block entity NBT ("in the NBT of the BE"). */
    static int donors(PlantBlockEntity be) {
        CompoundTag root = be.saveWithoutMetadata().getCompound(PlantNbt.ROOT);
        ListTag f = root.getCompound("pl").getList("f", Tag.TAG_COMPOUND);
        int n = 0;
        for (int i = 0; i < f.size(); i++) {
            if (f.getCompound(i).getByte("d") == 1) {
                n++;
            }
        }
        return n;
    }

    static float donorWeight(PlantBlockEntity be) {
        ListTag f = be.saveWithoutMetadata().getCompound(PlantNbt.ROOT).getCompound("pl").getList("f",
                Tag.TAG_COMPOUND);
        return f.isEmpty() ? -1 : f.getCompound(f.size() - 1).getFloat("w");
    }

    /** The goal is added on every entry into the level, once, with the vanilla priority. */
    @GameTest(template = FIELD, setupTicks = SETUP)
    public static void goalAddedOnceWithVanillaPriority(GameTestHelper helper) {
        Bee bee = helper.spawn(EntityType.BEE, new BlockPos(3, 3, 3));
        helper.assertTrue(ourGoals(bee) == 1, "goals after spawn " + ourGoals(bee));
        int ours = -1;
        int vanilla = -2;
        for (WrappedGoal w : bee.goalSelector.getAvailableGoals()) {
            if (w.getGoal() instanceof BeeCropPollinateGoal) {
                ours = w.getPriority();
            } else if (w.getGoal().getClass().getName().endsWith("Bee$BeePollinateGoal")) {
                vanilla = w.getPriority();
            }
        }
        helper.assertTrue(ours == vanilla, "priority " + ours + " != vanilla " + vanilla);
        BeeEvents.onJoin(new EntityJoinLevelEvent(bee, helper.getLevel()));
        BeeEvents.onJoin(new EntityJoinLevelEvent(bee, helper.getLevel()));
        helper.assertTrue(ourGoals(bee) == 1, "duplicate goal after re-join: " + ourGoals(bee));
        bee.discard();
        helper.succeed();
    }

    /**
     * Visits: pollen of the same species from another plant (≤ 2 visits,
     * ≤ 1200 ticks outside the hive) becomes a donor of weight 2.0; stale
     * pollen, pollen of the same plant and pollen of another species do
     * not, and a visit replaces the pollen; the bee gets nectar.
     */
    @GameTest(template = FIELD, setupTicks = SETUP)
    public static void visitWritesDonorAndReplacesPollen(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos a = new BlockPos(3, 2, 3);
        BlockPos b = new BlockPos(6, 2, 3);
        BlockPos c = new BlockPos(9, 2, 3);
        PlantBlockEntity pa = SeedSetGameTests.flowering(helper, a, 401);
        PlantBlockEntity pb = SeedSetGameTests.flowering(helper, b, 402);
        PlantFixtures.plant(PlantFixtures.place(helper, c, FieldPlantBlock.HARVEST_STAGE), 403);
        Bee bee = helper.spawn(EntityType.BEE, new BlockPos(4, 4, 4));
        bee.setNoAi(true);
        BeePollen p = pollen(bee);
        BlockPos aa = helper.absolutePos(a);
        BlockPos ab = helper.absolutePos(b);

        helper.assertTrue(PollinationService.beeVisit(level, bee, helper.absolutePos(c)) == PollinationService.Visit.NONE,
                "a plant not in flower was visited");
        helper.assertTrue(PollinationService.beeVisit(level, bee, aa) == PollinationService.Visit.VISITED, "visit A");
        helper.assertTrue(bee.hasNectar(), "no nectar after the visit");
        helper.assertTrue(p.species().equals(PlantFixtures.SP) && p.source() == aa.asLong(), "pollen of A not taken");
        helper.assertTrue(donors(pa) == 0, "the first visit wrote a donor");
        helper.assertTrue(pa.data().pending() != null && pa.data().pending().bees(), "A not marked bee-visited");

        bee.setHasNectar(false);
        helper.assertTrue(PollinationService.beeVisit(level, bee, ab) == PollinationService.Visit.DONOR, "visit B");
        helper.assertTrue(donors(pb) == 1 && donorWeight(pb) == 2.0f, "B donor " + donors(pb) + " w " + donorWeight(pb));
        helper.assertTrue(p.source() == ab.asLong(), "pollen not replaced by B");

        // pollen of the same plant is no donor
        helper.assertTrue(PollinationService.beeVisit(level, bee, ab) == PollinationService.Visit.VISITED, "self");
        helper.assertTrue(donors(pb) == 1, "own pollen became a donor");

        // stale: 1201 ticks outside the hive
        p.setCounters(0, BeePollen.MAX_AGE_OUTSIDE + 1);
        helper.assertTrue(PollinationService.beeVisit(level, bee, aa) == PollinationService.Visit.VISITED, "stale");
        helper.assertTrue(donors(pa) == 0, "stale pollen became a donor");

        // two visits in between (vanilla flowers)
        p.setCounters(BeePollen.MAX_VISITS, 0);
        helper.assertTrue(PollinationService.beeVisit(level, bee, ab) == PollinationService.Visit.VISITED, "2 visits");
        helper.assertTrue(donors(pb) == 1, "pollen after 2 visits became a donor");

        // another species: no donor, and the visit replaces it
        p.collect("wildroots:gametest_other", aa.asLong() + 1, new FatherRecord(1L, new long[] {1}));
        helper.assertTrue(PollinationService.beeVisit(level, bee, aa) == PollinationService.Visit.VISITED, "other");
        helper.assertTrue(donors(pa) == 0 && p.species().equals(PlantFixtures.SP), "other species pollen kept");

        // fresh pollen of A after one vanilla visit still counts
        p.setCounters(1, 1200);
        helper.assertTrue(PollinationService.beeVisit(level, bee, ab) == PollinationService.Visit.DONOR, "1 visit");
        helper.assertTrue(donors(pb) == 2, "B donors " + donors(pb));
        bee.discard();
        helper.succeed();
    }

    /** At most 4 donors wait in the plant; the oldest goes first. */
    @GameTest(template = FIELD, setupTicks = SETUP)
    public static void atMostFourDonorsOldestEvicted(GameTestHelper helper) {
        PlantBlockEntity be = SeedSetGameTests.flowering(helper, new BlockPos(3, 2, 3), 451);
        for (int i = 0; i < 6; i++) {
            be.beeVisit(io.github.slugblue.wildroots.data.Definitions.current().genetics()
                    .encode(PlantFixtures.genome(460 + i)).toFather(), 2.0f);
        }
        helper.assertTrue(donors(be) == 4, "donors " + donors(be));
        PlantBlockEntity again = PlantFixtures.saveLoad(be);
        long[] first = again.data().pending().fathers().get(0).genome().a();
        long[] expected = io.github.slugblue.wildroots.data.Definitions.current().genetics()
                .encode(PlantFixtures.genome(462)).a();
        helper.assertTrue(java.util.Arrays.equals(first, expected), "oldest donors were not evicted first");
        helper.succeed();
    }

    /** Pollen survives the hive (ForgeCaps) and does not age inside it; the released bee gets the goal again. */
    @GameTest(template = FIELD, setupTicks = SETUP, timeoutTicks = 400)
    public static void pollenSurvivesHiveAndDoesNotAgeInside(GameTestHelper helper) {
        BlockPos hivePos = new BlockPos(8, 2, 8);
        helper.setBlock(hivePos, Blocks.BEEHIVE.defaultBlockState());
        Bee bee = helper.spawn(EntityType.BEE, new BlockPos(8, 3, 8));
        long marker = 0x5EED_0000_0001L;
        pollen(bee).collect(PlantFixtures.SP, marker, new FatherRecord(9L, new long[] {4, 5, 6}));
        pollen(bee).setCounters(1, 77);
        BeehiveBlockEntity hive = (BeehiveBlockEntity) helper.getBlockEntity(hivePos);
        hive.addOccupant(bee, false);
        helper.assertTrue(bee.isRemoved() && hive.getOccupantCount() == 1, "bee did not enter the hive");
        CompoundTag stored = hive.writeBees().getCompound(0).getCompound("EntityData").getCompound("ForgeCaps")
                .getCompound(BeePollen.ID.toString());
        helper.assertTrue(stored.getLong("src") == marker, "pollen not in the hive NBT: " + stored);
        helper.runAfterDelay(200, () -> {
            // save/load of the hive, as on a chunk reload
            hive.load(hive.saveWithoutMetadata());
            BlockState st = helper.getBlockState(hivePos);
            hive.emptyAllLivingFromHive(null, st, BeehiveBlockEntity.BeeReleaseStatus.EMERGENCY);
            AABB box = new AABB(helper.absolutePos(hivePos)).inflate(4);
            List<Bee> out = helper.getLevel().getEntitiesOfClass(Bee.class, box,
                    x -> pollen(x).hasPollen() && pollen(x).source() == marker);
            helper.assertTrue(out.size() == 1, "released bees with the pollen: " + out.size());
            Bee released = out.get(0);
            BeePollen q = pollen(released);
            helper.assertTrue(q.ageOutside() == 77, "pollen aged in the hive: " + q.ageOutside());
            helper.assertTrue(q.visits() == 1 && q.genome().equals(new FatherRecord(9L, new long[] {4, 5, 6})),
                    "pollen changed in the hive");
            helper.assertTrue(ourGoals(released) == 1, "released bee goals " + ourGoals(released));
            released.discard();
            helper.succeed();
        });
    }

    /** Review m2: a bee loaded with nectar does not count a vanilla visit on its first ticks. */
    @GameTest(template = FIELD, setupTicks = SETUP, timeoutTicks = 100)
    public static void reloadedBeeWithNectarCountsNoVisit(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Bee bee = helper.spawn(EntityType.BEE, new BlockPos(4, 4, 4));
        pollen(bee).collect(PlantFixtures.SP, 0x5EED_0000_0002L, new FatherRecord(9L, new long[] {1}));
        bee.setHasNectar(true);
        CompoundTag tag = new CompoundTag();
        bee.save(tag);
        bee.discard();
        Bee loaded = (Bee) EntityType.loadEntityRecursive(tag, level, e -> e);
        helper.assertTrue(loaded != null, "bee did not load");
        loaded.setUUID(java.util.UUID.randomUUID());
        loaded.setNoAi(true);
        level.addFreshEntity(loaded);
        helper.assertTrue(loaded.hasNectar() && pollen(loaded).hasPollen(), "nectar or pollen lost in the reload");
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(pollen(loaded).visits() == 0, "reload counted a visit: " + pollen(loaded).visits());
            loaded.discard();
            helper.succeed();
        });
    }

    /** Our blocks are not in {@code #minecraft:bee_growables}: the vanilla growth bonus does not apply. */
    @GameTest(template = SmokeGameTests.FLOOR_3X3X3)
    public static void beeGrowablesSkipsOurPlants(GameTestHelper helper) {
        for (BlockState s : ModBlocks.WILD_CARROT.get().getStateDefinition().getPossibleStates()) {
            helper.assertFalse(s.is(BlockTags.BEE_GROWABLES), "plant state in bee_growables: " + s);
        }
        helper.succeed();
    }

    /** Without flowering plants of a species with bees, the bees visit vanilla flowers as usual. */
    @GameTest(template = FIELD, setupTicks = SETUP, timeoutTicks = 6000, batch = "wr142_bees_vanilla")
    public static void noBloomBeesVisitVanillaFlowers(GameTestHelper helper) {
        helper.getLevel().setDayTime(1000);
        List<Bee> bees = field(helper, false, FLOWERS_NEAR);
        helper.succeedWhen(() -> {
            long nectar = bees.stream().filter(Bee::hasNectar).count();
            int honey = maxHoney(helper);
            for (BlockPos p : PLANTS) {
                PlantBlockEntity be = (PlantBlockEntity) helper.getBlockEntity(p);
                helper.assertTrue(be.data().pending() == null, "our goal visited a plant out of flower at " + p);
            }
            helper.assertTrue(nectar + honey >= 3, "vanilla nectar " + nectar + ", honey " + honey);
        });
    }

    /**
     * S2 field: 4 hives, 10 bees, 8 flowering carrots, 72 000 ticks (3 game
     * days; the GameTest server runs them in about 2 minutes): donors in the
     * block entity NBT ≥ 1 and honey in a hive ≥ 1. Own batch.
     */
    @GameTest(template = FIELD, setupTicks = SETUP, timeoutTicks = 72_000, batch = "wr142_bee_field")
    public static void beeFieldWritesDonorsAndMakesHoney(GameTestHelper helper) {
        helper.getLevel().setDayTime(1000);
        field(helper, true, new BlockPos[0]);
        helper.runAtTickTime(71_000, () -> {
            int donors = 0;
            for (BlockPos p : PLANTS) {
                donors += donors((PlantBlockEntity) helper.getBlockEntity(p));
            }
            int honey = maxHoney(helper);
            helper.assertTrue(donors >= 1, "no donors after 3 days");
            helper.assertTrue(honey >= 1, "no honey after 3 days");
            org.slf4j.LoggerFactory.getLogger(BeeGameTests.class).info("WR-142 bee field: donors {}, honey {}",
                    donors, honey);
            helper.succeed();
        });
    }

    static List<Bee> field(GameTestHelper helper, boolean flowering, BlockPos[] flowers) {
        for (BlockPos h : HIVES) {
            helper.setBlock(h, Blocks.BEEHIVE.defaultBlockState());
        }
        long key = 500;
        for (BlockPos p : PLANTS) {
            PlantFixtures.plant(PlantFixtures.place(helper, p,
                    flowering ? FieldPlantBlock.FLOWER_STAGE : FieldPlantBlock.HARVEST_STAGE), key++);
        }
        for (BlockPos f : flowers) {
            helper.setBlock(f, Blocks.POPPY.defaultBlockState());
        }
        List<Bee> bees = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) {
            bees.add(helper.spawn(EntityType.BEE, new BlockPos(6 + i % 5, 4, 7 + i / 5 * 2)));
        }
        return bees;
    }

    static int maxHoney(GameTestHelper helper) {
        int max = 0;
        for (BlockPos h : HIVES) {
            BlockState st = helper.getBlockState(h);
            if (st.hasProperty(BeehiveBlock.HONEY_LEVEL)) {
                max = Math.max(max, st.getValue(BeehiveBlock.HONEY_LEVEL));
            }
        }
        return max;
    }
}
