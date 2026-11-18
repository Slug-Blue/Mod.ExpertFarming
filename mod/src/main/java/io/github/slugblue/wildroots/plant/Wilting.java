package io.github.slugblue.wildroots.plant;

import io.github.slugblue.wildroots.core.config.ServerConfig;
import io.github.slugblue.wildroots.core.safety.LoadedLevelAccess;
import io.github.slugblue.wildroots.genetics.api.ClimateStress;
import io.github.slugblue.wildroots.genetics.api.Environment;
import io.github.slugblue.wildroots.genetics.api.Phenotype;
import io.github.slugblue.wildroots.genetics.api.SpeciesDef;
import io.github.slugblue.wildroots.worldgen.StarterPatch;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Wilting of young plants in extreme climate (docs/design/carrot-colours-climate.md §4; WR-711, WR-712, WR-714).
 *
 * <p>The rule: on every random tick of a plant of the player at growth stage 1–2 the stress {@code S}
 * ({@link ClimateStress}) is compared with the margin {@code W}. At {@code S ≥ W} a counter rises (one per tick,
 * {@code K} = {@link ClimateStress#graceTicks()}), below it the counter falls; the counter is the block property
 * {@link FieldPlantBlock#STRAIN} (0 normal, 1 drooping while the counter runs, 2 wilted at {@code K}). A wilted
 * plant does not grow, is no bone meal target and drops nothing ({@link HarvestService}); it stays wilted. When
 * {@code climate.wilting} is false nothing wilts and a wilted plant stands up on its next tick.
 *
 * <p>Never wilt: wild (worldgen) and village plants, a mother root and every plant of stage 0 or 3–5 (a ripe root
 * waits, §4.2), plants in a dimension without sky light (§4.5), plants of the player near a starter patch that had
 * to be placed outside the window ({@link StarterPatch.Data#exempts}), and a plant of an old save for the first
 * {@value #OLD_PLANT_GRACE_TICKS} ticks after its chunk loaded (§4.6).
 *
 * <p>Protection (§4.5): a cell whose sky light is at most {@link ClimateStress#shelterSkyLight()} is sheltered,
 * farmland with {@code moisture = 7} below the plant is irrigated; each adds its {@code buffer}. Only ordinary
 * blocks count, no block of its own. {@link GrowthLogic#environment(ServerLevel, BlockPos, SpeciesDef)} uses the
 * same protection for {@code E}.
 *
 * <p>Growth chance: the cached chance of the block entity (and so the {@code E} behind it) takes shelter and
 * irrigation as they were at the last stage change. A roof put up or taken away later changes the wilting at once
 * (this class reads the cell on every tick that is at the margin) but the growth penalty only at the next stage
 * change: a known, accepted lag (stages last minutes; a refresh here would cost a phenotype per tick).
 *
 * <p>Grace of an old plant: it starts at the first random tick after the chunk loaded and is held in memory
 * only, so it restarts with every load until the plant gets {@link PlantState#CLIMATE_AWARE} (after one
 * uninterrupted grace). Persisting the start would add a key to every old root for a case that ends after one
 * long enough session; documented in the design (§4.6) instead.
 *
 * <p>Cost: the tick reads the config flag, the passport, the cached limits of the block entity, one biome
 * temperature and compares. The light and the farmland below are read only when the unprotected stress is at the
 * margin, which is rare: a protection only moves the temperature towards the middle of the window, so a plant that
 * does not wilt unprotected cannot wilt protected. Server thread only.
 */
public final class Wilting {

    /** A plant of an old save does not wilt for this long after loading: 10 minutes {@code [PLACEHOLDER]}. */
    public static final int OLD_PLANT_GRACE_TICKS = 10 * 60 * 20;

    private Wilting() {
    }

    /** The server switch {@code climate.wilting}. */
    public static boolean enabled() {
        return ServerConfig.value(ServerConfig.CLIMATE_WILTING);
    }

    /** Whether the state is a wilted young plant that counts as wilted now (the config is on). */
    public static boolean wilted(BlockState state) {
        return isWilted(state) && enabled();
    }

    /**
     * The state is a wilted young plant, whatever the config says (a pure function of the state, for the client).
     * Prepared for WR-713 (lens and journal wording).
     */
    public static boolean isWilted(BlockState state) {
        return state.getBlock() instanceof FieldPlantBlock && youngStage(FieldPlantBlock.stage(state))
                && FieldPlantBlock.strain(state) == FieldPlantBlock.WILTED;
    }

    /** Stages 1 and 2: the only ones that wilt (§4.2). */
    public static boolean youngStage(int stage) {
        return stage >= 1 && stage <= FieldPlantBlock.HARVEST_STAGE - 1;
    }

    /** {@code strain} of a plant that has {@code ticks} counted: 0 below one, wilted at {@code grace}, else drooping. */
    public static int strainOf(int ticks, int grace) {
        return ticks <= 0 ? 0 : ticks >= grace ? FieldPlantBlock.WILTED : 1;
    }

    /** GameTests only: the grace of a plant from an old save ends now (waiting 10 minutes in a test is no option). */
    public static void endGraceForTests(PlantBlockEntity be) {
        requireGameTestServer(be.getLevel() == null ? null : be.getLevel().getServer());
        be.setGraceUntil(0);
    }

    /** The test seams do nothing outside a GameTest server: a mod or a command cannot reach them by accident. */
    private static void requireGameTestServer(net.minecraft.server.MinecraftServer server) {
        if (!(server instanceof net.minecraft.gametest.framework.GameTestServer)) {
            throw new IllegalStateException("test seam outside the GameTest server");
        }
    }

    // ------------------------------------------------------------------ exemptions

    /**
     * Why a plant does not wilt in principle, or {@code null} when it can: {@code "species"} (no climate stress),
     * {@code "config"}, {@code "origin"} (wild or village), {@code "mother"}, {@code "stage"}, {@code "dimension"}.
     * Cheap checks only; the starter radius and the old-save grace need more and are in {@link #tick}.
     */
    public static String exemption(ServerLevel level, BlockState state, PlantBlockEntity.Live live) {
        if (!live.def().climateStress().enabled()) {
            return "species";
        }
        if (!enabled()) {
            return "config";
        }
        if (live.data().passport().origin() != Passport.Origin.PLAYER) {
            return "origin";
        }
        if (live.data().has(PlantState.MOTHER)) {
            return "mother";
        }
        if (!youngStage(FieldPlantBlock.stage(state))) {
            return "stage";
        }
        if (!level.dimensionType().hasSkyLight()) {
            return "dimension";
        }
        return null;
    }

    /** Warning uses a typical uncoloured plant, never the seed lot's hidden traits (WR-713). */
    public static Optional<String> plantingWarning(ServerLevel level, BlockPos pos,
                                                   io.github.slugblue.wildroots.genetics.api.SpeciesDef def) {
        ClimateStress cs = def.climateStress();
        if (!enabled() || !cs.enabled() || !level.dimensionType().hasSkyLight() || startersPlant(level, pos)) {
            return Optional.empty();
        }
        Holder<Biome> biome = level.getBiome(pos);
        double t = ClimateStress.effectiveTemperature(ClimateStress.mid(def), ClimateService.temperature(biome, pos),
                cs.humid(humidBiome(cs, biome)), cs.buffer(sheltered(level, pos, cs), irrigated(level, pos)));
        double hot = ClimateStress.hotLimit(def, 0.32, io.github.slugblue.wildroots.genetics.api.ColourProps.ZERO);
        double cold = ClimateStress.coldLimit(def, 0.32, io.github.slugblue.wildroots.genetics.api.ColourProps.ZERO);
        return cs.wilts(ClimateStress.stress(t, hot, cold))
                ? Optional.of("wildroots.message.planting." + (t - hot >= cold - t ? "heat" : "cold"))
                : Optional.empty();
    }

    // ------------------------------------------------------------------ measuring

    /**
     * The stress of a plant now (§3.1) with what feeds it; {@code stress} is the signed {@code S} (negative: margin
     * to the nearest limit; {@code E} uses {@code max(0, S)}). Used by the lens and journal (WR-713).
     */
    public record Reading(double stress, double unprotectedStress, double margin, boolean sheltered, boolean irrigated,
                          String exemption, boolean hot, int minutes) {

        /** Whether this plant would wilt at this stress (before the grace counter and the exemptions). */
        public boolean atMargin() {
            return stress >= margin;
        }
    }

    /**
     * The stress of the plant at {@code pos}, with protection, for the lens and the journal (WR-713). Empty while the
     * species has no climate stress or the plant is not readable. Does not advance grace or strain counters.
     */
    public static Optional<Reading> read(ServerLevel level, BlockPos pos, BlockState state, PlantBlockEntity be,
                                         PlantBlockEntity.Live live) {
        ClimateStress cs = live.def().climateStress();
        if (!cs.enabled() || !limits(be, live)) {
            return Optional.empty();
        }
        Holder<Biome> biome = level.getBiome(pos);
        double t = ClimateService.temperature(biome, pos);
        double humid = cs.humid(humidBiome(cs, biome));
        double mid = ClimateStress.mid(live.def());
        double raw = ClimateStress.stress(ClimateStress.effectiveTemperature(mid, t, humid, 0), be.limitHot(),
                be.limitCold());
        boolean sheltered = sheltered(level, pos, cs);
        boolean irrigated = irrigated(level, pos);
        double s = ClimateStress.stress(ClimateStress.effectiveTemperature(mid, t, humid, cs.buffer(sheltered, irrigated)),
                be.limitHot(), be.limitCold());
        double effective = ClimateStress.effectiveTemperature(mid, t, humid, cs.buffer(sheltered, irrigated));
        boolean hot = effective - be.limitHot() >= be.limitCold() - effective;
        String exempt = exemption(level, state, live);
        if (exempt == null && startersPlant(level, pos)) {
            exempt = "starter";
        }
        if (exempt == null && !live.data().has(PlantState.CLIMATE_AWARE)) {
            exempt = "old_save";
        }
        int speed = level.getGameRules().getInt(net.minecraft.world.level.GameRules.RULE_RANDOMTICKING);
        int ticks = be.strainTicks() >= 0 ? be.strainTicks() : FieldPlantBlock.strain(state);
        int minutes = speed <= 0 ? 0 : (int) Math.ceil(Math.max(0, cs.graceTicks() - ticks) * 4096.0 / speed / 1200);
        return Optional.of(new Reading(s, raw, cs.wiltMargin(), sheltered, irrigated, exempt, hot, minutes));
    }

    /** Fills the cached limits of the block entity; false when the phenotype cannot be computed. */
    private static boolean limits(PlantBlockEntity be, PlantBlockEntity.Live live) {
        if (!Float.isNaN(be.limitHot())) {
            return true;
        }
        Optional<Phenotype> p = GrowthLogic.phenotype(live, Environment.neutral(ClimateStress.mid(live.def())));
        if (p.isEmpty()) {
            return false;
        }
        double[] l = ClimateStress.limits(live.def(), p.get());
        be.setLimits(l[0], l[1]);
        return true;
    }

    static boolean humidBiome(ClimateStress cs, Holder<Biome> biome) {
        return cs.humidPenalty() > 0 && biome.is(BiomeTags.IS_JUNGLE);
    }

    /** Sheltered: the sky light of the cell is at most the limit (raw sky light, independent of the time of day). */
    static boolean sheltered(ServerLevel level, BlockPos pos, ClimateStress cs) {
        return level.getBrightness(LightLayer.SKY, pos) <= cs.shelterSkyLight();
    }

    /** Irrigated: the farmland below is fully moist (water next to it). */
    static boolean irrigated(ServerLevel level, BlockPos pos) {
        return LoadedLevelAccess.blockState(level, pos.below())
                .filter(b -> b.hasProperty(FarmBlock.MOISTURE))
                .map(b -> b.getValue(FarmBlock.MOISTURE) >= FarmBlock.MAX_MOISTURE)
                .orElse(false);
    }

    // ------------------------------------------------------------------ the random tick

    /**
     * One random tick of a plant (stages 1–2 reach here): moves the counter and the block's {@code strain}.
     *
     * @return {@code null} when the plant is wilted now (the caller does not let it grow), else the block state the
     *         caller continues with: {@code state}, or the new one if the tick changed the strain (no second read of
     *         the level, no allocation on the common path)
     */
    static BlockState tick(BlockState state, ServerLevel level, BlockPos pos, PlantBlockEntity be,
                           PlantBlockEntity.Live live) {
        int strain = FieldPlantBlock.strain(state);
        if (exemption(level, state, live) != null) {
            return strain != 0 ? setStrain(level, pos, state, be, 0, 0) : state; // switched off or exempt now: stands up
        }
        ClimateStress cs = live.def().climateStress();
        if (!aware(level, be, live)) {
            if (live.data().hasPlanter()) {
                io.github.slugblue.wildroots.core.safety.Guarded.run("journal climate update", pos, () ->
                        io.github.slugblue.wildroots.journal.HintTriggers.climateUpdate(level, live.data()));
            }
            return state; // an old plant in its grace
        }
        if (!limits(be, live)) {
            return state;
        }
        int ticks = be.strainTicks() >= 0 ? be.strainTicks() : strain == FieldPlantBlock.WILTED ? cs.graceTicks() : strain;
        if (ticks >= cs.graceTicks()) {
            be.setStrainTicks(ticks);
            return null; // wilted: for good
        }
        Holder<Biome> biome = level.getBiome(pos);
        double t = ClimateService.temperature(biome, pos);
        double mid = ClimateStress.mid(live.def());
        double humid = cs.humid(humidBiome(cs, biome));
        double raw = ClimateStress.stress(ClimateStress.effectiveTemperature(mid, t, humid, 0), be.limitHot(),
                be.limitCold());
        boolean stressed = false;
        if (cs.wilts(raw)) {
            // the protection moves the temperature towards the middle of the window: only now is it worth reading
            double s = ClimateStress.stress(ClimateStress.effectiveTemperature(mid, t, humid,
                    cs.buffer(sheltered(level, pos, cs), irrigated(level, pos))), be.limitHot(), be.limitCold());
            stressed = cs.wilts(s) && !startersPlant(level, pos);
        }
        if (raw > 0 && io.github.slugblue.wildroots.journal.HintTriggers.climateNeeded(level, live.data(),
                stressed && ticks + 1 >= cs.graceTicks())) {
            io.github.slugblue.wildroots.core.safety.Guarded.run("journal climate", pos, () ->
                    read(level, pos, state, be, live).ifPresent(r ->
                            io.github.slugblue.wildroots.journal.HintTriggers.climate(level, live.data(), r,
                                    r.exemption() == null && r.atMargin() && ticks + 1 >= cs.graceTicks())));
        }
        int next = stressed ? ticks + 1 : Math.max(0, ticks - 1);
        BlockState out = state;
        if (next != ticks || strain != strainOf(next, cs.graceTicks())) {
            out = setStrain(level, pos, state, be, next, strainOf(next, cs.graceTicks()));
        }
        return next >= cs.graceTicks() ? null : out;
    }

    /**
     * Whether the plant is climate-aware: a plant of this version always is; one of an old save is not for the
     * first {@value #OLD_PLANT_GRACE_TICKS} ticks after it loaded, and gets the flag then (§4.6).
     */
    private static boolean aware(ServerLevel level, PlantBlockEntity be, PlantBlockEntity.Live live) {
        if (live.data().has(PlantState.CLIMATE_AWARE)) {
            return true;
        }
        long now = level.getGameTime();
        if (be.graceUntil() < 0) {
            be.setGraceUntil(now + OLD_PLANT_GRACE_TICKS);
        }
        if (now < be.graceUntil()) {
            return false;
        }
        be.setPlantFlag(PlantState.CLIMATE_AWARE, true);
        return true;
    }

    /** A starter patch placed outside the window shields the plants of the player around it (§4.6). */
    private static boolean startersPlant(ServerLevel level, BlockPos pos) {
        java.util.function.BiPredicate<ServerLevel, BlockPos> shield = starterShield;
        if (shield != null) {
            return shield.test(level, pos);
        }
        // the starter patch is a fact of the overworld; its radius means nothing in another dimension
        return level.dimension() == net.minecraft.world.level.Level.OVERWORLD
                && StarterPatch.data(level.getServer()).map(d -> d.exempts(pos)).orElse(false);
    }

    /** GameTests only: replaces the answer of the world's starter-patch data; {@code null} restores it. */
    private static volatile java.util.function.BiPredicate<ServerLevel, BlockPos> starterShield;

    /**
     * GameTests only: the random seed of the test world decides whether the real starter patch lies outside the
     * window and so shields the test area; a test says what the shield answers for its span, {@code null} restores
     * the world's data.
     */
    public static void overrideStarterShieldForTests(java.util.function.BiPredicate<ServerLevel, BlockPos> shield) {
        requireGameTestServer(net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer());
        starterShield = shield;
    }

    private static BlockState setStrain(ServerLevel level, BlockPos pos, BlockState state, PlantBlockEntity be,
                                        int ticks, int strain) {
        be.setStrainTicks(ticks);
        if (FieldPlantBlock.strain(state) == strain) {
            return state;
        }
        BlockState next = state.setValue(FieldPlantBlock.STRAIN, strain);
        level.setBlock(pos, next, 2);
        return next;
    }
}
