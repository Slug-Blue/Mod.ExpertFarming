package io.github.slugblue.wildroots.plant;

import io.github.slugblue.wildroots.core.safety.LoadedLevelAccess;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeHooks;

/**
 * Growth of a plant (docs/05-architecture.md §10.1; GDD §3.3, §3.5, §5.1,
 * §12.1; economy §3.1).
 *
 * <p>Chance of the next stage per random tick:
 * {@code p = 68.3 / (60·t_stage) · m · dry}, {@code m = 0.7 + 0.6·P_growth},
 * {@code dry = 1 − 0.5·(1 − P_drought)} on dry farmland, clamped to
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
        double dry = env.moisturized() ? 1 : 1 - 0.5 * (1 - p.pOr(Traits.DROUGHT_TOLERANCE, 0));
        double c = minutes <= 0 ? MAX_CHANCE : TICK_SECONDS / (60 * minutes) * m * dry;
        return Double.isFinite(c) ? Math.max(MIN_CHANCE, Math.min(MAX_CHANCE, c)) : MIN_CHANCE;
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

    public static Optional<Phenotype> phenotype(PlantBlockEntity.Live live, Environment env) {
        return live.genetics().phenotype(live.genome(), live.def(), env, live.noise());
    }

    /** Cached chance of the plant at {@code pos}, computed if missing. */
    public static double cachedChance(ServerLevel level, BlockPos pos, int stage, PlantBlockEntity be,
                                      PlantBlockEntity.Live live) {
        float c = be.growthChance();
        if (Float.isNaN(c)) {
            Environment env = environment(level, pos);
            c = (float) (double) phenotype(live, env).map(p -> chance(live.def(), p, env, stage,
                    dormancyMinutes(live.def(), p, pos, live))).orElse(0.0);
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
        double chance = cachedChance(level, pos, stage, be.get(), live.get());
        if (random.nextDouble() < chance && ForgeHooks.onCropsGrowPre(level, pos, state, true)) {
            advance(level, pos, state, be.get(), live.get(), stage + 1);
            ForgeHooks.onCropsGrowPost(level, pos, state);
        }
    }

    /** Bone meal (GDD §5.1): +1 stage with chance 0.35 on stages 1–3; nothing on flowering. */
    static void bonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        if (FieldPlantBlock.bonemealStage(FieldPlantBlock.stage(state)) && random.nextFloat() < BONEMEAL_CHANCE) {
            grow(level, pos, state);
        }
    }

    /** Advances one stage without a roll (bone meal, other mods' {@code growCrops}). */
    static void grow(ServerLevel level, BlockPos pos, BlockState state) {
        int stage = FieldPlantBlock.stage(state);
        if (stage >= FieldPlantBlock.MAX_STAGE) {
            return;
        }
        LoadedLevelAccess.blockEntity(level, pos, PlantBlockEntity.class)
                .ifPresent(be -> be.live(level).ifPresent(live -> advance(level, pos, state, be, live, stage + 1)));
    }

    /**
     * Stage change: recompute {@code E} and {@code P}, set stage, size,
     * ailing and variant in one {@code setBlock}, refresh the cached chance.
     * Entering flowering starts a new cycle (empty pending lot); flowering →
     * seeds is the seed set (WR-141, §10.2).
     */
    public static void advance(ServerLevel level, BlockPos pos, BlockState state, PlantBlockEntity be,
                               PlantBlockEntity.Live live, int newStage) {
        Environment env = environment(level, pos);
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
        level.setBlock(pos, next, 2);
        be.setGrowthChance((float) chance(live.def(), p.get(), env, newStage,
                dormancyMinutes(live.def(), p.get(), pos, live)));
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
     * Root colour of the carrot (GDD §7.3): 3 purple ({@code P_}, epistatic),
     * 2 orange ({@code y1y1 y2y2 pp}), 1 yellow ({@code y1y1}), 0 white.
     * Species without these loci stay 0.
     */
    public static int variant(SpeciesDef s, Phenotype p) {
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
