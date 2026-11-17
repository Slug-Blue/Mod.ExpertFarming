package io.github.slugblue.wildroots.plant;

import io.github.slugblue.wildroots.core.safety.LoadedLevelAccess;
import io.github.slugblue.wildroots.genetics.api.ClimateStress;
import io.github.slugblue.wildroots.genetics.api.Environment;
import io.github.slugblue.wildroots.genetics.api.Phenotype;
import io.github.slugblue.wildroots.genetics.api.SpeciesDef;
import io.github.slugblue.wildroots.genetics.api.Timing;
import io.github.slugblue.wildroots.genetics.api.Traits;
import io.github.slugblue.wildroots.genetics.model.TraitId;
import io.github.slugblue.wildroots.genetics.random.Hash64;
import io.github.slugblue.wildroots.pollination.PollinationService;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeHooks;

/**
 * Growth of a plant (docs/05-architecture.md §10.1; GDD §3.3, §3.5, §5.1,
 * §12.1; economy §3.1).
 *
 * <p>Chance of the next stage per random tick:
 * {@code p = 68.3 / (60·t_stage) · m · dry}, {@code m = 0.7 + 0.6·P_growth},
 * {@code dry = 1 − 0.5·(1 − P_drought)} on dry farmland ({@code P_drought} plus the colour's
 * water bonus, at most 1: yellow and purple need less water, WR-703), clamped to
 * {@code [1e-6, 0.95]}. {@code P_growth} already carries {@code E}
 * (temperature out of the window, dryness, darkness; GDD §3.5), so a plant
 * far out of its window grows slower but never dies. The chance is cached in
 * the block entity and recomputed on planting and on every stage change
 * (§10.1, R9), together with {@code size}, {@code ailing} and
 * {@code variant}, which change only there, in one {@code setBlock}.
 *
 * <p>Stage times: {@code to_harvest_min} over stages 1–3 as 4 : 4 : 5 and
 * {@code seed_stage_min} over 4–5 as 1 : 1 (economy §3.1, carrot 4/4/5 +
 * 2.5/2.5). A seed with the wild (dormant) phenotype of {@code dormancy}
 * needs {@code U(2, 6)} minutes more to sprout, drawn once per plant (GDD §3.6).
 *
 * <p>Biennials (GDD §5.1–5.2, owner decision D1 of 2026-11-11,
 * {@code 00-vision.md} item 32): a plant that is not a planted mother root
 * <b>waits</b> at harvest ripeness (stage 3) until it is harvested; its
 * chance is 0 and neither bone meal nor {@code growCrops} moves it on. Only a
 * planted mother root flowers and sets seed (3 → 4 → 5). Wild plants placed
 * by worldgen keep their generated stage (flowering wild plants exist).
 *
 * <p>Climate (carrot-colours-climate §3.1, §4; {@link Wilting}): {@link #environment(ServerLevel, BlockPos, SpeciesDef)}
 * hands {@code E} the effective temperature {@code T_eff} (humid biome, shelter, irrigation); a young plant of the
 * player that stays at the stress margin wilts: its chance is 0 and it drops nothing.
 */
public final class GrowthLogic {

    /** Mean seconds between random ticks of one block at {@code randomTickSpeed = 3} (4096 / 3 / 20). */
    public static final double TICK_SECONDS = 68.3;
    public static final double MAX_CHANCE = 0.95;
    public static final double MIN_CHANCE = 1e-6;
    /** Bone meal: +1 stage with this chance on stages 1–3 (GDD §5.1, economy П4). */
    public static final float BONEMEAL_CHANCE = 0.35f;
    /** {@code V} below this makes the plant ailing (GDD §3.7). */
    public static final double AILING_BELOW = 0.85;
    static final double[] HARVEST_SPLIT = {4 / 13.0, 4 / 13.0, 5 / 13.0};
    static final TraitId DORMANCY = TraitId.of("dormancy");
    static final double DORMANCY_MIN = 2;
    static final double DORMANCY_SPAN = 4;

    private GrowthLogic() {
    }

    /** Minutes from stage {@code to − 1} to stage {@code to} (1–5). */
    public static double stageMinutes(Timing t, int to) {
        if (to >= 1 && to <= FieldPlantBlock.HARVEST_STAGE) {
            return t.toHarvestMin() * HARVEST_SPLIT[to - 1];
        }
        if (to == FieldPlantBlock.FLOWER_STAGE || to == FieldPlantBlock.SEED_STAGE) {
            return t.seedStageMin() / 2;
        }
        return 0;
    }

    /**
     * Chance per random tick to leave {@code stage}; 0 on the final stage.
     *
     * @param dormancyMinutes extra minutes of a dormant seed on stage 0
     */
    public static double chance(SpeciesDef s, Phenotype p, Environment env, int stage, double dormancyMinutes) {
        if (stage >= FieldPlantBlock.MAX_STAGE) {
            return 0;
        }
        double minutes = stageMinutes(s.timing(), stage + 1) + (stage == 0 ? dormancyMinutes : 0);
        double m = 0.7 + 0.6 * p.pOr(Traits.GROWTH_SPEED, 0.3);
        double dry = env.moisturized() ? 1
                : 1 - 0.5 * (1 - Math.min(1, p.pOr(Traits.DROUGHT_TOLERANCE, 0) + p.colourProps().drought()));
        double c = minutes <= 0 ? MAX_CHANCE : TICK_SECONDS / (60 * minutes) * m * dry;
        return Double.isFinite(c) ? Math.max(MIN_CHANCE, Math.min(MAX_CHANCE, c)) : MIN_CHANCE;
    }

    /**
     * D1: a biennial that is not a planted mother root stays at harvest
     * ripeness until it is harvested (GDD §5.2).
     */
    public static boolean waitsForHarvest(PlantBlockEntity.Live live, int stage) {
        return stage == FieldPlantBlock.HARVEST_STAGE && biennial(live)
                && !live.data().has(PlantState.MOTHER);
    }

    /** {@code biennial} life cycle of the species (definitions, {@code life_cycle}). */
    public static boolean biennial(PlantBlockEntity.Live live) {
        return "biennial".equals(live.species().info().lifeCycle());
    }

    /** Growth chance of the live plant at {@code stage}: 0 while it waits for the harvest (D1). */
    static double chance(PlantBlockEntity.Live live, Phenotype p, Environment env, BlockPos pos, int stage) {
        return waitsForHarvest(live, stage) ? 0
                : chance(live.def(), p, env, stage, dormancyMinutes(live.def(), p, pos, live));
    }

    /** {@code U(2, 6)} minutes for a dormant seed (wild phenotype of {@code dormancy}), 0 otherwise. */
    static double dormancyMinutes(SpeciesDef s, Phenotype p, BlockPos pos, PlantBlockEntity.Live live) {
        if (s.qual(DORMANCY) == null || p.domestic(DORMANCY)) {
            return 0;
        }
        long h = Hash64.of(pos.asLong(), live.genome().hashCode(), Hash64.str("dormancy"));
        return DORMANCY_MIN + DORMANCY_SPAN * ((h >>> 11) * 0x1.0p-53);
    }

    /** Environment of the cell (GDD §3.5): temperature, moist farmland below, light. */
    public static Environment environment(ServerLevel level, BlockPos pos) {
        boolean moist = LoadedLevelAccess.blockState(level, pos.below())
                .filter(b -> b.hasProperty(FarmBlock.MOISTURE))
                .map(b -> b.getValue(FarmBlock.MOISTURE) > 0)
                .orElse(false);
        return new Environment(ClimateService.temperature(level, pos), moist, level.getRawBrightness(pos, 0), 0);
    }

    /**
     * Environment of a plant of species {@code def} (carrot-colours-climate §3.1, §4.5): like
     * {@link #environment(ServerLevel, BlockPos)}, but the temperature is the effective one,
     * {@code T_eff = T_mid + (T + humid − T_mid)·(1 − buffer)}: a jungle adds its humid penalty, a sheltered cell
     * (sky light at most the species' limit) and irrigated farmland (moisture 7) each add a buffer. A species
     * without climate stress, and any cell in the middle of the window, keep the plain temperature of the cell.
     */
    public static Environment environment(ServerLevel level, BlockPos pos, SpeciesDef def) {
        ClimateStress cs = def.climateStress();
        if (!cs.enabled()) {
            return environment(level, pos);
        }
        Holder<Biome> biome = level.getBiome(pos);
        double t = ClimateService.temperature(biome, pos);
        double humid = cs.humid(Wilting.humidBiome(cs, biome));
        double buffer = cs.buffer(Wilting.sheltered(level, pos, cs), Wilting.irrigated(level, pos));
        boolean moist = LoadedLevelAccess.blockState(level, pos.below())
                .filter(b -> b.hasProperty(FarmBlock.MOISTURE))
                .map(b -> b.getValue(FarmBlock.MOISTURE) > 0)
                .orElse(false);
        double eff = humid == 0 && buffer == 0 ? t : ClimateStress.effectiveTemperature(ClimateStress.mid(def), t, humid, buffer);
        return new Environment(eff, moist, level.getRawBrightness(pos, 0), 0);
    }

    public static Optional<Phenotype> phenotype(PlantBlockEntity.Live live, Environment env) {
        return live.genetics().phenotype(live.genome(), live.def(), env, live.noise());
    }

    /** Cached chance of the plant at {@code pos}, computed if missing. */
    public static double cachedChance(ServerLevel level, BlockPos pos, int stage, PlantBlockEntity be,
                                      PlantBlockEntity.Live live) {
        float c = be.growthChance();
        if (Float.isNaN(c)) {
            Environment env = environment(level, pos, live.def());
            c = (float) (double) phenotype(live, env).map(p -> chance(live, p, env, pos, stage)).orElse(0.0);
            be.setGrowthChance(c);
        }
        return c;
    }

    /** One random tick: roll the cached chance, ask Forge, advance one stage. */
    static void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int stage = FieldPlantBlock.stage(state);
        if (stage >= FieldPlantBlock.MAX_STAGE) {
            return;
        }
        Optional<PlantBlockEntity> be = LoadedLevelAccess.blockEntity(level, pos, PlantBlockEntity.class);
        if (be.isEmpty()) {
            return;
        }
        Optional<PlantBlockEntity.Live> live = be.get().live(level);
        if (live.isEmpty()) {
            return;
        }
        if (Wilting.youngStage(stage)) {
            if (Wilting.tick(state, level, pos, be.get(), live.get())) {
                return; // wilted: no growth (§4.3)
            }
            // the tick may have set the block's strain: a stage change builds on the current state, not the old one
            BlockState old = state;
            state = LoadedLevelAccess.blockState(level, pos).filter(s -> s.is(old.getBlock())).orElse(state);
        }
        double chance = cachedChance(level, pos, stage, be.get(), live.get());
        if (chance > 0 && random.nextDouble() < chance && ForgeHooks.onCropsGrowPre(level, pos, state, true)) {
            advance(level, pos, state, be.get(), live.get(), stage + 1);
            ForgeHooks.onCropsGrowPost(level, pos, state);
        }
    }

    /**
     * Bone meal (GDD §5.1): +1 stage with chance 0.35 on stages 1–3; nothing
     * on flowering, nothing on a plant that waits for the harvest (D1; the
     * block refuses it as a target, so the bone meal is not spent).
     */
    static void bonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        if (FieldPlantBlock.bonemealStage(FieldPlantBlock.stage(state)) && !Wilting.wilted(state) && !waits(level, pos, state)
                && random.nextFloat() < BONEMEAL_CHANCE) {
            grow(level, pos, state);
        }
    }

    /** Whether the plant at {@code pos} waits at harvest ripeness for the harvest (D1). */
    public static boolean waits(ServerLevel level, BlockPos pos, BlockState state) {
        int stage = FieldPlantBlock.stage(state);
        return stage == FieldPlantBlock.HARVEST_STAGE && LoadedLevelAccess.blockEntity(level, pos, PlantBlockEntity.class)
                .flatMap(be -> be.live(level)).map(live -> waitsForHarvest(live, stage)).orElse(false);
    }

    /**
     * Advances one stage without a roll (bone meal, other mods'
     * {@code growCrops}); never moves a plant that waits for the harvest (D1).
     */
    static void grow(ServerLevel level, BlockPos pos, BlockState state) {
        int stage = FieldPlantBlock.stage(state);
        if (stage >= FieldPlantBlock.MAX_STAGE || Wilting.wilted(state)) {
            return;
        }
        LoadedLevelAccess.blockEntity(level, pos, PlantBlockEntity.class)
                .ifPresent(be -> be.live(level).filter(live -> !waitsForHarvest(live, stage))
                        .ifPresent(live -> advance(level, pos, state, be, live, stage + 1)));
    }

    /**
     * Stage change: recompute {@code E} and {@code P}, set stage, size,
     * ailing and variant in one {@code setBlock}, refresh the cached chance.
     * Entering flowering starts a new cycle (empty pending lot); flowering →
     * seeds is the seed set (WR-141, §10.2).
     */
    public static void advance(ServerLevel level, BlockPos pos, BlockState state, PlantBlockEntity be,
                               PlantBlockEntity.Live live, int newStage) {
        Environment env = environment(level, pos, live.def());
        Optional<Phenotype> p = phenotype(live, env);
        if (p.isEmpty()) {
            return;
        }
        int oldStage = FieldPlantBlock.stage(state);
        if (newStage == FieldPlantBlock.FLOWER_STAGE && oldStage < FieldPlantBlock.FLOWER_STAGE) {
            // a new flowering cycle: donors and fathers of an earlier cycle do not carry over
            be.setPendingLot(null);
        } else if (newStage == FieldPlantBlock.SEED_STAGE && oldStage == FieldPlantBlock.FLOWER_STAGE) {
            // seed set, once per cycle, while the plant is still in the bloom index (§10.2)
            PollinationService.seedSet(level, pos, be, live);
        }
        BlockState next = look(state.setValue(FieldPlantBlock.STAGE, newStage), live.def(), p.get());
        if (!Wilting.youngStage(newStage)) {
            // a ripe root waits and never wilts (§4.2): the strain of the young plant ends with the stage
            next = next.setValue(FieldPlantBlock.STRAIN, 0);
            be.setStrainTicks(0);
        }
        level.setBlock(pos, next, 2);
        if (newStage == FieldPlantBlock.HARVEST_STAGE && oldStage < FieldPlantBlock.HARVEST_STAGE) {
            Phenotype ph = p.get();
            io.github.slugblue.wildroots.core.safety.Guarded.run("journal matured", pos, () ->
                    io.github.slugblue.wildroots.journal.HintTriggers.matured(level, pos, live, ph));
        }
        be.setGrowthChance((float) chance(live, p.get(), env, pos, newStage));
    }

    /** {@code size}, {@code ailing} and {@code variant} from the phenotype (§7.2). */
    public static BlockState look(BlockState state, SpeciesDef s, Phenotype p) {
        int size = Math.max(0, Math.min(3, (int) (p.pOr(Traits.FRUIT_SIZE, 0) * 4)));
        return state.setValue(FieldPlantBlock.SIZE, size)
                .setValue(FieldPlantBlock.AILING, p.vigor() < AILING_BELOW)
                .setValue(FieldPlantBlock.VARIANT, variant(s, p));
    }

    static final TraitId Y1 = TraitId.of("color_y1");
    static final TraitId Y2 = TraitId.of("color_y2");
    static final TraitId PURPLE = TraitId.of("color_p");

    /**
     * The block's {@code variant} (the colour the model draws): a thin wrapper over {@link Phenotype#colour()},
     * the colour rules of the species' JSON (WR-701; carrot: 0 white, 1 yellow, 2 orange, 3 purple, 4 red). No
     * matching rule gives 0, a number above {@link FieldPlantBlock#MAX_VARIANT} the last one. A species without
     * colour rules keeps the rule of M1a (GDD §7.3): 3 purple ({@code P_}, epistatic), 2 orange
     * ({@code y1y1 y2y2}), 1 yellow ({@code y1y1}), 0 white; without those loci 0.
     */
    public static int variant(SpeciesDef s, Phenotype p) {
        if (!s.colours().isNone()) {
            return Math.max(0, Math.min(FieldPlantBlock.MAX_VARIANT, p.colour()));
        }
        if (s.qual(Y1) == null || s.qual(Y2) == null || s.qual(PURPLE) == null) {
            return 0;
        }
        if (p.domestic(PURPLE)) {
            return 3;
        }
        if (p.domestic(Y1)) {
            return p.domestic(Y2) ? 2 : 1;
        }
        return 0;
    }
}
