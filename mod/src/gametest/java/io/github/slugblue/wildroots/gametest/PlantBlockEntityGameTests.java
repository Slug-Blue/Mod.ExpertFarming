package io.github.slugblue.wildroots.gametest;

import io.github.slugblue.wildroots.WildRoots;
import io.github.slugblue.wildroots.data.Definitions;
import io.github.slugblue.wildroots.data.DefinitionsSnapshot;
import io.github.slugblue.wildroots.genetics.api.QuantTrait;
import io.github.slugblue.wildroots.genetics.api.SpeciesDef;
import io.github.slugblue.wildroots.genetics.codec.LayoutDescriptor;
import io.github.slugblue.wildroots.genetics.model.Genome;
import io.github.slugblue.wildroots.genetics.model.GenomeLayout;
import io.github.slugblue.wildroots.genetics.model.TraitId;
import io.github.slugblue.wildroots.genetics.pollination.Brush;
import io.github.slugblue.wildroots.item.LotFathersNbt;
import io.github.slugblue.wildroots.plant.Passport;
import io.github.slugblue.wildroots.plant.PendingLot;
import io.github.slugblue.wildroots.plant.PlantBlockEntity;
import io.github.slugblue.wildroots.plant.PlantNbt;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** WR-131: {@code PlantBlockEntity} without a ticker (docs/05-architecture.md §6.1–6.2, §5.2–5.3; SEC-09). */
@GameTestHolder(WildRoots.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PlantBlockEntityGameTests {

    private PlantBlockEntityGameTests() {
    }

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3)
    public static void wildPlantKeepsOnlyItsPassportUntilTouched(GameTestHelper helper) {
        PlantBlockEntity be = PlantFixtures.place(helper, PlantFixtures.PLANT, 0);
        be.initWild(PlantFixtures.SP, Passport.wild(12345L, "meadow", false, true));
        CompoundTag before = be.saveWithoutMetadata().getCompound(PlantNbt.ROOT);
        helper.assertTrue(before.getAllKeys().equals(java.util.Set.of("v", "sp", "pp")), "untouched root " + before);
        helper.assertTrue(!be.genomeDecoded(), "decoded before any access");

        // an identical untouched copy, realised later, gives the same plant
        PlantBlockEntity copy = PlantFixtures.saveLoad(be);
        Genome g = be.live(helper.getLevel()).orElseThrow().genome();
        CompoundTag after = be.saveWithoutMetadata().getCompound(PlantNbt.ROOT);
        helper.assertTrue(after.contains("g") && after.contains("e"), "genome not saved after realisation " + after);

        PlantBlockEntity reloaded = PlantFixtures.saveLoad(be);
        helper.assertTrue(!reloaded.genomeDecoded(), "reload decodes eagerly (ADR-0003: lazy)");
        PlantBlockEntity.Live r = reloaded.live(helper.getLevel()).orElseThrow();
        helper.assertTrue(r.genome().equals(g), "genome changed after reload");
        helper.assertTrue(Arrays.equals(r.noise().bytes(), after.getByteArray("e")), "noise changed after reload");
        helper.assertTrue(copy.live(helper.getLevel()).orElseThrow().genome().equals(g), "realisation not deterministic");
        helper.succeed();
    }

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3)
    public static void futureVersionIsInertAndKeepsItsRawTag(GameTestHelper helper) {
        PlantBlockEntity be = PlantFixtures.plant(PlantFixtures.place(helper, PlantFixtures.PLANT, 1), 7);
        CompoundTag tag = be.saveWithFullMetadata();
        CompoundTag root = tag.getCompound(PlantNbt.ROOT);
        root.putInt("v", PlantNbt.CURRENT + 1);
        root.putString("newer_field", "kept");
        inertKeepsRaw(helper, be, tag, root);
        helper.succeed();
    }

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3)
    public static void oversizedRootIsInertAndKeepsItsRawTag(GameTestHelper helper) {
        PlantBlockEntity be = PlantFixtures.plant(PlantFixtures.place(helper, PlantFixtures.PLANT, 1), 8);
        CompoundTag tag = be.saveWithFullMetadata();
        CompoundTag root = tag.getCompound(PlantNbt.ROOT);
        root.putString("junk", "x".repeat(3000));
        inertKeepsRaw(helper, be, tag, root);
        helper.succeed();
    }

    private static void inertKeepsRaw(GameTestHelper helper, PlantBlockEntity placed, CompoundTag tag, CompoundTag root) {
        PlantBlockEntity be = (PlantBlockEntity) BlockEntity.loadStatic(placed.getBlockPos(), placed.getBlockState(), tag);
        helper.assertTrue(be.status() == PlantBlockEntity.Status.INERT, "status " + be.status());
        helper.assertTrue(be.live(helper.getLevel()).isEmpty(), "inert plant gives live data");
        helper.assertTrue(be.data() == null, "inert plant exposes data to copy");
        PlantBlockEntity again = PlantFixtures.saveLoad(be);
        helper.assertTrue(again.saveWithoutMetadata().getCompound(PlantNbt.ROOT).equals(root), "raw root changed");
        helper.assertTrue(again.getUpdateTag().isEmpty(), "update tag of an inert built-in plant");
        // inert in the world: no growth
        placed.load(tag);
        net.minecraft.core.BlockPos abs = placed.getBlockPos();
        net.minecraft.util.RandomSource random = net.minecraft.util.RandomSource.create(1312);
        int stage = PlantFixtures.stage(helper, PlantFixtures.PLANT);
        for (int i = 0; i < 300; i++) {
            helper.getLevel().getBlockState(abs).randomTick(helper.getLevel(), abs, random);
        }
        helper.assertTrue(PlantFixtures.stage(helper, PlantFixtures.PLANT) == stage, "an inert plant grew");
    }

    /** Unknown species: sleeps, keeps the raw tag; when the species returns it lives again. */
    @GameTest(template = SmokeGameTests.FLOOR_3X3X3, batch = "wr131_species", timeoutTicks = 200)
    public static void unknownSpeciesSleepsAndWakes(GameTestHelper helper) {
        String lost = "wildroots:gt_lost_species";
        PlantBlockEntity be = PlantFixtures.plant(PlantFixtures.place(helper, PlantFixtures.PLANT, 1), 9);
        CompoundTag tag = be.saveWithFullMetadata();
        CompoundTag root = tag.getCompound(PlantNbt.ROOT);
        root.putString("sp", lost);
        root.getCompound("g").putString("sp", lost);
        PlantBlockEntity sleeping = (PlantBlockEntity) BlockEntity.loadStatic(be.getBlockPos(), be.getBlockState(), tag);
        helper.assertTrue(sleeping.status() == PlantBlockEntity.Status.SLEEPING, "status " + sleeping.status());
        helper.assertTrue(sleeping.live(helper.getLevel()).isEmpty(), "sleeping plant is live");
        PlantBlockEntity again = PlantFixtures.saveLoad(sleeping);
        helper.assertTrue(again.saveWithoutMetadata().getCompound(PlantNbt.ROOT).equals(root), "raw root changed");

        // the plant in the world sleeps too: random ticks do nothing
        be.load(tag);
        net.minecraft.core.BlockPos abs = helper.absolutePos(PlantFixtures.PLANT);
        net.minecraft.util.RandomSource random = net.minecraft.util.RandomSource.create(1311);
        for (int i = 0; i < 300; i++) {
            helper.getLevel().getBlockState(abs).randomTick(helper.getLevel(), abs, random);
        }
        helper.assertTrue(PlantFixtures.stage(helper, PlantFixtures.PLANT) == 1, "a sleeping plant grew");

        DefinitionsSnapshot original = Definitions.current();
        try {
            Definitions.publish(PlantFixtures.withSpecies(original, PlantFixtures.carrotAs(lost, q -> q)));
            helper.assertTrue(GrowthGameTests.tickUntilGrown(helper.getLevel(), abs, random, 3000) > 0,
                    "the plant does not grow after its species returned");
            PlantBlockEntity.Live live = again.live(helper.getLevel()).orElse(null);
            helper.assertTrue(live != null, "species returned but the plant still sleeps");
            helper.assertTrue(again.status() == PlantBlockEntity.Status.OK, "status after return " + again.status());
            helper.assertTrue(Arrays.equals(live.genome().bits(), PlantFixtures.genome(9).bits()), "genome bits changed");
        } finally {
            Definitions.publish(original);
        }
        helper.succeed();
    }

    /**
     * WR-111 transfer: every genome of the root migrates through save/load of
     * the block entity, fathers of the pending lot included (§5.3; S10).
     */
    @GameTest(template = SmokeGameTests.FLOOR_3X3X3, batch = "wr131_migration", timeoutTicks = 200)
    public static void layoutChangeMigratesEveryGenomeOfTheRoot(GameTestHelper helper) {
        String sp = "wildroots:gt_migrating_carrot";
        SpeciesDef oldDef = PlantFixtures.carrotAs(sp, q -> q);
        SpeciesDef newDef = PlantFixtures.carrotAs(sp, q -> q.stream()
                .map(t -> t.id().equals(TraitId.of("yield")) ? new QuantTrait(t.id(), 8, t.sigmaEnv(), t.vigorAffected()) : t)
                .toList());
        GenomeLayout oldLayout = GenomeLayout.of(oldDef);
        GenomeLayout newLayout = GenomeLayout.of(newDef);
        helper.assertTrue(oldLayout.hash() != newLayout.hash(), "layouts equal");

        // a plant written with the old layout: carrot bits, species renamed
        PlantBlockEntity be = PlantFixtures.plant(PlantFixtures.place(helper, PlantFixtures.PLANT, 2), 11);
        Genome mother = PlantFixtures.genome(11);
        List<LotFathersNbt.Father> fathers = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            fathers.add(new LotFathersNbt.Father(Definitions.current().genetics().encode(PlantFixtures.genome(100 + i))
                    .toFather(), 1f + i, i == 2));
        }
        be.setPendingLot(new PendingLot(fathers, 0.6f, Brush.NONE, true));
        CompoundTag tag = be.saveWithFullMetadata();
        CompoundTag root = tag.getCompound(PlantNbt.ROOT);
        root.putString("sp", sp);
        root.getCompound("g").putString("sp", sp);
        helper.assertTrue(root.getCompound("g").getLong("lh") == oldLayout.hash(), "fixture not in the old layout");

        DefinitionsSnapshot original = Definitions.current();
        try {
            Definitions.publish(PlantFixtures.withSpecies(original, newDef,
                    new LayoutDescriptor(sp, oldLayout.hash(), oldLayout.canonical())));
            PlantBlockEntity loaded = (PlantBlockEntity) BlockEntity.loadStatic(be.getBlockPos(), be.getBlockState(), tag);
            PlantBlockEntity.Live live = loaded.live(helper.getLevel()).orElseThrow();
            helper.assertTrue(live.genome().layout().equals(newLayout), "genome not migrated");
            // shared loci keep their alleles
            for (TraitId t : List.of(TraitId.of("flavor"), TraitId.of("yield"))) {
                for (int l = 0; l < 6; l++) {
                    for (int a = 0; a < 2; a++) {
                        helper.assertTrue(live.genome().allele(t, l, a) == mother.allele(t, l, a), "allele lost " + t + l);
                    }
                }
            }
            CompoundTag saved = PlantFixtures.saveLoad(loaded).saveWithoutMetadata().getCompound(PlantNbt.ROOT);
            helper.assertTrue(saved.getCompound("g").getLong("lh") == newLayout.hash(), "saved lh " + saved);
            helper.assertTrue(saved.getByteArray("e").length == newDef.quantitative().size(), "noise not migrated");
            var f = saved.getCompound("pl").getList("f", 10);
            helper.assertTrue(f.size() == 3, "fathers " + f.size());
            for (int i = 0; i < f.size(); i++) {
                CompoundTag fc = f.getCompound(i);
                helper.assertTrue(!fc.contains("lh"), "father kept an lh other than the mother's: " + fc);
                helper.assertTrue(((LongArrayTag) fc.get("a")).getAsLongArray().length == newLayout.longCount(),
                        "father not migrated");
            }
        } finally {
            Definitions.publish(original);
        }
        helper.succeed();
    }

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3)
    public static void updateTagIsEmptyAndDamagedSpeciesIdIsInert(GameTestHelper helper) {
        PlantBlockEntity be = PlantFixtures.plant(PlantFixtures.place(helper, PlantFixtures.PLANT, 1), 13);
        helper.assertTrue(be.getUpdateTag().isEmpty(), "update tag " + be.getUpdateTag());
        CompoundTag tag = be.saveWithFullMetadata();
        tag.getCompound(PlantNbt.ROOT).put("sp", StringTag.valueOf("Bad Id!"));
        PlantBlockEntity bad = (PlantBlockEntity) BlockEntity.loadStatic(be.getBlockPos(), be.getBlockState(), tag);
        helper.assertTrue(bad.status() == PlantBlockEntity.Status.INERT, "status " + bad.status());
        helper.succeed();
    }
}
