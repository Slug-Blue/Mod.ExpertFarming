package io.github.slugblue.wildroots.worldgen;

import com.mojang.logging.LogUtils;
import io.github.slugblue.wildroots.core.config.ServerConfig;
import io.github.slugblue.wildroots.core.registry.ModBlocks;
import io.github.slugblue.wildroots.core.registry.ModFeatures;
import io.github.slugblue.wildroots.core.rng.RngKeys;
import io.github.slugblue.wildroots.core.safety.EntryPoint;
import io.github.slugblue.wildroots.core.safety.Guarded;
import io.github.slugblue.wildroots.core.safety.LoadedLevelAccess;
import io.github.slugblue.wildroots.data.Definitions;
import io.github.slugblue.wildroots.genetics.api.SpeciesDef;
import io.github.slugblue.wildroots.genetics.random.Hash64;
import io.github.slugblue.wildroots.genetics.random.SplitMix64;
import io.github.slugblue.wildroots.plant.ClimateService;
import io.github.slugblue.wildroots.plant.FieldPlantBlock;
import io.github.slugblue.wildroots.plant.Passport;
import io.github.slugblue.wildroots.plant.PlantBlockEntity;
import io.github.slugblue.wildroots.worldgen.StarterPatchRules.Outcome;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;

/**
 * Starter patch of wild carrots (worldgen §6; GDD §11.2): once per world,
 * after the spawn area is prepared ({@code ServerStartedEvent}); the flag
 * {@code starter_patch_done} lives in the world {@code SavedData}
 * {@value #DATA}. A world played before the mod ({@code gameTime > 0} and
 * no {@code SavedData}) gets no patch unless {@code starterPatch = FORCE};
 * the log says so.
 *
 * <ol>
 *   <li>An existing carrot patch of ≥ 5 plants in the ring 24–128 is marked
 *   {@code starter} (generous genetics, §6.4) and gets a nest if it has
 *   none; nothing new is placed.</li>
 *   <li>Otherwise columns on a grid of 4 in the ring 24–96 are scored:
 *   grass surface, 5×5 within 1 block, no water, lava or leaves in the
 *   5×5, open sky are required; carrot category +3, temperature window +2,
 *   openness +1; nearer wins a tie.</li>
 *   <li>Fallback 1 (no column with the category): the best grass column in
 *   24–128. Fallback 2: the best dry column in 24–128, its 5×5 top made
 *   grass ({@code podzol} on snow, the snow layer removed). An island:
 *   the nearest loaded dry land beyond 128, with a warning.</li>
 *   <li>The patch is the carrot {@code wild_patch} with 10–14 plants,
 *   stages 0.45 / 0.40 / 0.15, never rich, {@code starter = true},
 *   category by biome or {@code meadow}, and a guaranteed nest (lone birch
 *   when no tree is near).</li>
 * </ol>
 * Only loaded chunks are read (the spawn area); nothing is loaded or
 * generated.
 *
 * <p>A search that finds no place ({@code NOT_FOUND}, or {@code NOT_LOADED}
 * when fewer than half of the ring columns were loaded) does not set the
 * flag (review 2, T1): the step runs again on the next server start and once
 * per server run on the first player login, when the spawn ring is loaded
 * (it never loads a chunk itself). The attempts are counted in the
 * {@code SavedData}; after {@value StarterPatchRules#MAX_ATTEMPTS} failed
 * attempts a warning is logged once and the flag is set. Every failure logs
 * the rejected columns by reason (unloaded, water, leaves, slope), so LD can
 * read a failed seed.
 */
public final class StarterPatch {

    public static final String DATA = "wildroots";
    public static final ResourceLocation CARROT_PATCH = io.github.slugblue.wildroots.core.Ids.mod("wild_patch/wild_carrot");
    static final int RING_MIN = 24;
    static final int RING_MAX = 96;
    static final int FALLBACK_MAX = 128;
    static final int ISLAND_MAX = 160;
    static final int STEP = 4;
    static final int MIN_EXISTING = 5;
    /** Reach of everything the placement at a column reads or writes (review M2). */
    static final int AREA = 22;
    static final WildPatchConfig.Stages STAGES = new WildPatchConfig.Stages(0.45f, 0.40f, 0.15f);

    private static final Logger LOGGER = LogUtils.getLogger();


    /** Result of {@link #place}: the outcome, the patch origin, if any, and the survey of the ring. */
    public record Placement(Outcome outcome, BlockPos origin, int plants, Survey survey) {

        public Placement(Outcome outcome, BlockPos origin, int plants) {
            this(outcome, origin, plants, Survey.EMPTY);
        }
    }

    /**
     * Grid columns of the ring 24–128 and why they were refused (review 2,
     * T1): their chunks not all loaded, water or lava, leaves (under a crown
     * or in the 5×5), a slope over 1 block in the 5×5, or no ground.
     */
    public record Survey(int columns, int unloaded, int water, int leaves, int slope, int other) {

        static final Survey EMPTY = new Survey(0, 0, 0, 0, 0, 0);

        /** Columns that passed every check. */
        public int accepted() {
            return columns - unloaded - water - leaves - slope - other;
        }

        @Override
        public String toString() {
            return "columns " + columns + ", accepted " + accepted() + ", rejected: unloaded " + unloaded
                    + ", water " + water + ", leaves " + leaves + ", slope " + slope + ", other " + other;
        }
    }

    /** Mutable counters of {@link #columns} (one search). */
    static final class Tally {
        int columns;
        int unloaded;
        int water;
        int leaves;
        int slope;
        int other;

        Survey survey() {
            return new Survey(columns, unloaded, water, leaves, slope, other);
        }
    }

    /** What started an attempt. */
    public enum Trigger { START, LOGIN }

    private static volatile Placement last;
    private static volatile Placement atStart;

    private StarterPatch() {
    }

    /** The last placement of this server run (tests, diagnostics). */
    public static Optional<Placement> last() {
        return Optional.ofNullable(last);
    }

    /** The placement of the server-start step of this run (tests, diagnostics). */
    public static Optional<Placement> atStart() {
        return Optional.ofNullable(atStart);
    }

    /** The world flag and the failed attempts (review 2, T1). */
    public static final class Data extends SavedData {

        private boolean done;
        private int attempts;
        /** Transient: the login retry of this server run was made (not saved). */
        private boolean loginTried;

        public Data() {
        }

        static Data load(CompoundTag tag) {
            Data d = new Data();
            d.done = tag.getBoolean("starter_patch_done");
            d.attempts = Math.max(0, Math.min(StarterPatchRules.MAX_ATTEMPTS, tag.getInt("starter_patch_attempts")));
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            tag.putBoolean("starter_patch_done", done);
            tag.putInt("starter_patch_attempts", attempts);
            return tag;
        }

        public boolean done() {
            return done;
        }

        /** Failed attempts so far. */
        public int attempts() {
            return attempts;
        }

        /** A copy through NBT, as after a restart (tests). */
        public Data reloaded() {
            return load(save(new CompoundTag()));
        }
    }

    /** The world flag, if the world has the data file. */
    public static Optional<Data> data(MinecraftServer server) {
        return Optional.ofNullable(server.overworld().getDataStorage().get(Data::load, DATA));
    }

    /** Forge bus. */
    @EntryPoint
    public static void onServerStarted(ServerStartedEvent event) {
        Guarded.run("starter patch", "overworld", () -> run(event.getServer()));
    }

    /**
     * Forge bus: the retry of a failed starter search on the first login of
     * this server run, once the spawn ring is loaded (review 2, T1).
     */
    @EntryPoint
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        MinecraftServer server = event.getEntity().getServer();
        if (server != null) {
            Guarded.run("starter patch login", "overworld", () -> retryOnLogin(server));
        }
    }

    static Placement run(MinecraftServer server) {
        return run(server, Trigger.START);
    }

    /** One starter step of {@code trigger} on the overworld around the world spawn. */
    static Placement run(MinecraftServer server, Trigger trigger) {
        ServerLevel level = server.overworld();
        Optional<Data> d = data(server);
        Outcome skip = StarterPatchRules.decide(ServerConfig.STARTER_PATCH.get(), d.isPresent(),
                d.map(Data::done).orElse(false),
                level.getGameTime());
        if (skip != null) {
            if (skip == Outcome.EXISTING_WORLD) {
                LOGGER.info("Wild Roots: the world was played before the mod (game time {}); no starter patch. "
                        + "Set starterPatch = FORCE in the server config to place one.", level.getGameTime());
            }
            last = new Placement(skip, null, 0);
            if (trigger == Trigger.START) {
                atStart = last;
            }
            return last;
        }
        Placement p = attempt(level, () -> level.getDataStorage().computeIfAbsent(Data::load, Data::new, DATA),
                level.getSharedSpawnPos(), q -> true, trigger);
        last = p;
        if (trigger == Trigger.START) {
            atStart = p;
        }
        return p;
    }

    /**
     * The login retry: only a world whose earlier attempt failed (the data
     * file exists, the flag is not set), once per server run, and only when
     * at least half of the spawn ring is loaded; otherwise a later login
     * tries. Returns the placement, or empty when nothing ran.
     */
    public static Optional<Placement> retryOnLogin(MinecraftServer server) {
        Optional<Data> d = data(server);
        if (d.isEmpty() || d.get().done || d.get().loginTried) {
            return Optional.empty();
        }
        ServerLevel level = server.overworld();
        if (loadedShare(level, level.getSharedSpawnPos()) < StarterPatchRules.MIN_LOADED_SHARE) {
            return Optional.empty();
        }
        d.get().loginTried = true;
        return Optional.of(run(server, Trigger.LOGIN));
    }

    /**
     * One attempt with the bookkeeping of review 2, T1: a placed or marked
     * patch sets the flag; a failure counts an attempt, and failure number
     * {@value StarterPatchRules#MAX_ATTEMPTS} sets the flag with a warning;
     * without the line nothing changes. {@code data} is asked for only when
     * the flag or the counter changes.
     */
    public static Placement attempt(ServerLevel level, java.util.function.Supplier<Data> data, BlockPos centre,
                                    java.util.function.Predicate<BlockPos> area, Trigger trigger) {
        Placement p = place(level, centre, area);
        if (p.outcome().success()) {
            Data d = data.get();
            d.done = true;
            d.setDirty();
            LOGGER.info("Wild Roots: starter patch ({}): {} at {} ({} plants)", trigger, p.outcome(), p.origin(),
                    p.plants());
        } else if (p.outcome().failure()) {
            Data d = data.get();
            d.attempts = Math.min(StarterPatchRules.MAX_ATTEMPTS, d.attempts + 1);
            if (StarterPatchRules.giveUp(d.attempts)) {
                d.done = true;
                LOGGER.warn("Wild Roots: no starter patch after {} attempts ({} near spawn {}; {}); giving up",
                        d.attempts, p.outcome(), centre, p.survey());
            } else {
                LOGGER.warn("Wild Roots: starter patch ({}): {} near spawn {} (attempt {} of {}; {}); "
                                + "retrying on the next server start or player login",
                        trigger, p.outcome(), centre, d.attempts, StarterPatchRules.MAX_ATTEMPTS, p.survey());
            }
            d.setDirty();
        } else {
            LOGGER.info("Wild Roots: starter patch ({}): {}", trigger, p.outcome());
        }
        return p;
    }

    /** Share of the grid columns of the ring 24–128 whose {@value #AREA}-box is loaded (reads nothing else). */
    public static double loadedShare(ServerLevel level, BlockPos centre) {
        int all = 0;
        int loaded = 0;
        for (int dx = -FALLBACK_MAX; dx <= FALLBACK_MAX; dx += STEP) {
            for (int dz = -FALLBACK_MAX; dz <= FALLBACK_MAX; dz += STEP) {
                double d = Math.sqrt((double) dx * dx + (double) dz * dz);
                if (d < RING_MIN || d > FALLBACK_MAX) {
                    continue;
                }
                all++;
                if (loadedBox(level, centre.offset(dx, 0, dz), AREA)) {
                    loaded++;
                }
            }
        }
        return all == 0 ? 0 : (double) loaded / all;
    }

    /** The scored columns of the ring 24–128 around {@code centre} and their refusals (tests, LD). */
    public static Survey survey(ServerLevel level, BlockPos centre) {
        Optional<WildPatchConfig> config = carrotConfig(level);
        Optional<SpeciesDef> def = Definitions.current().species(ModBlocks.WILD_CARROT_SPECIES).map(e -> e.def());
        if (config.isEmpty() || def.isEmpty()) {
            return Survey.EMPTY;
        }
        Tally t = new Tally();
        columns(level, centre, def.get(), config.get(), RING_MIN, FALLBACK_MAX, t);
        return t.survey();
    }

    // ------------------------------------------------------------------ the steps

    /** The whole algorithm around {@code spawn} (tests call it with another centre). */
    public static Placement place(ServerLevel level, BlockPos spawn) {
        return place(level, spawn, p -> true);
    }

    /** Same, only columns accepted by {@code area} (a test restricts the search to its platform). */
    public static Placement place(ServerLevel level, BlockPos spawn, java.util.function.Predicate<BlockPos> area) {
        Optional<WildPatchConfig> config = carrotConfig(level);
        Optional<SpeciesDef> def = Definitions.current().species(ModBlocks.WILD_CARROT_SPECIES).map(e -> e.def());
        if (config.isEmpty() || def.isEmpty()) {
            return new Placement(Outcome.NO_LINE, null, 0);
        }
        List<List<BlockPos>> existing = existingPatches(level, spawn, RING_MIN, FALLBACK_MAX).stream()
                .filter(l -> area.test(l.get(0))).toList();
        if (!existing.isEmpty()) {
            List<BlockPos> patch = existing.get(0);
            BlockPos origin = LoadedLevelAccess.blockEntity(level, patch.get(0), PlantBlockEntity.class)
                    .map(be -> BlockPos.of(be.data().passport().patchOrigin())).orElse(patch.get(0));
            // the ring search sees only the plants 24–128 from spawn; a patch on the inner edge of the
            // ring has more plants nearer spawn: the whole patch (same patch origin) is marked
            List<BlockPos> whole = new ArrayList<>(patch);
            for (List<BlockPos> g : existingPatches(level, origin, 0, AREA)) {
                for (BlockPos q : g) {
                    if (!whole.contains(q) && LoadedLevelAccess.blockEntity(level, q, PlantBlockEntity.class)
                            .map(be -> be.data().passport().patchOrigin() == origin.asLong()).orElse(false)) {
                        whole.add(q);
                    }
                }
            }
            patch = whole;
            for (BlockPos p : patch) {
                LoadedLevelAccess.blockEntity(level, p, PlantBlockEntity.class).ifPresent(PlantBlockEntity::markStarter);
            }
            config.get().beeNest().filter(n -> loadedBox(level, origin, AREA)).ifPresent(n -> {
                SplitMix64 rng = new SplitMix64(Hash64.of(RngKeys.patchSeed(level.getSeed(), origin.asLong()),
                        Hash64.str("starter_nest")));
                BeeNests.place(level, level.getChunkSource().getGenerator(), origin, n, false, true, rng,
                        RandomSource.create(rng.nextLong()));
            });
            return new Placement(Outcome.MARKED_EXISTING, origin, patch.size());
        }
        Tally tally = new Tally();
        List<Column> columns = columns(level, spawn, def.get(), config.get(), RING_MIN, FALLBACK_MAX, tally).stream()
                .filter(c -> area.test(c.surface)).toList();
        Survey survey = tally.survey();
        // step 2: in 24–96 with a carrot category
        Optional<Placement> p = tryPlace(level, config.get(), columns.stream()
                .filter(c -> c.dist <= RING_MAX && c.grass && c.score >= 3), Outcome.PLACED, false, survey);
        if (p.isPresent()) {
            return p.get();
        }
        // fallback 1: any grass column in 24–128
        p = tryPlace(level, config.get(), columns.stream().filter(c -> c.grass), Outcome.FALLBACK, false, survey);
        if (p.isPresent()) {
            return p.get();
        }
        // fallback 2: any dry column in 24–128, made grass
        p = tryPlace(level, config.get(), columns.stream(), Outcome.FALLBACK, true, survey);
        if (p.isPresent()) {
            return p.get();
        }
        // an island: the nearest loaded dry land beyond 128
        List<Column> far = columns(level, spawn, def.get(), config.get(), FALLBACK_MAX + STEP, ISLAND_MAX, new Tally())
                .stream().filter(c -> area.test(c.surface)).toList();
        p = tryPlace(level, config.get(), far.stream(), Outcome.ISLAND, true, survey);
        if (p.isPresent()) {
            LOGGER.warn("Wild Roots: no dry land within 128 blocks of spawn; starter patch at {}", p.get().origin());
            return p.get();
        }
        return new Placement(StarterPatchRules.miss(survey.columns(), survey.unloaded()), null, 0, survey);
    }

    static Optional<WildPatchConfig> carrotConfig(ServerLevel level) {
        ConfiguredFeature<?, ?> cf = level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE)
                .get(CARROT_PATCH);
        return cf != null && cf.config() instanceof WildPatchConfig c ? Optional.of(c) : Optional.empty();
    }

    private static Optional<Placement> tryPlace(ServerLevel level, WildPatchConfig config,
                                                java.util.stream.Stream<Column> candidates, Outcome outcome,
                                                boolean makeGrass, Survey survey) {
        List<Column> sorted = candidates.sorted(Comparator.comparingInt((Column c) -> -c.score)
                .thenComparingDouble(c -> c.dist)).limit(8).toList();
        WildPatchFeature.Options opts = new WildPatchFeature.Options(true, Optional.of(UniformInt.of(10, 14)),
                Optional.of(STAGES), "meadow", 96);
        for (Column c : sorted) {
            List<Map.Entry<BlockPos, BlockState>> pad = makeGrass ? grassPad(level, c.surface) : List.of();
            WildPatchFeature.Result r = ModFeatures.WILD_PATCH.get().placePatch(level,
                    level.getChunkSource().getGenerator(), c.surface.above(), config, opts);
            if (r.placed()) {
                return Optional.of(new Placement(outcome, c.surface.above(), r.plants().size(), survey));
            }
            // the patch did not fit: the terrain goes back as it was (review m1)
            for (int i = pad.size() - 1; i >= 0; i--) {
                level.setBlock(pad.get(i).getKey(), pad.get(i).getValue(), 2);
            }
        }
        return Optional.empty();
    }

    /** Fallback 2: the 5×5 top becomes grass; on snow podzol, the snow layer goes (worldgen §6.2 item 4). */
    public static List<Map.Entry<BlockPos, BlockState>> grassPad(ServerLevel level, BlockPos surface) {
        List<Map.Entry<BlockPos, BlockState>> changed = new ArrayList<>();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                BlockPos col = surface.offset(dx, 0, dz);
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, col.getX(), col.getZ()) - 1;
                BlockPos t = col.atY(top);
                boolean snow = false;
                if (level.getBlockState(t).is(Blocks.SNOW)) {
                    // a snow layer counted by the heightmap: it goes, the block under it is the surface
                    changed.add(Map.entry(t, level.getBlockState(t)));
                    level.setBlock(t, Blocks.AIR.defaultBlockState(), 2);
                    t = t.below();
                    snow = true;
                }
                if (level.getBlockState(t.above()).is(Blocks.SNOW)) {
                    changed.add(Map.entry(t.above(), level.getBlockState(t.above())));
                    level.setBlock(t.above(), Blocks.AIR.defaultBlockState(), 2);
                    snow = true;
                }
                snow |= level.getBlockState(t).is(Blocks.SNOW_BLOCK) || level.getBlockState(t).is(Blocks.POWDER_SNOW);
                if (level.getBlockState(t).getFluidState().isEmpty()) {
                    changed.add(Map.entry(t, level.getBlockState(t)));
                    level.setBlock(t, (snow ? Blocks.PODZOL : Blocks.GRASS_BLOCK).defaultBlockState(), 2);
                }
            }
        }
        return changed;
    }

    /** One candidate column: its surface block, score and whether it is already grass. */
    record Column(BlockPos surface, int score, boolean grass, double dist) {
    }

    /** Scored dry columns on a grid of {@value #STEP} in the ring, loaded chunks only. */
    static List<Column> columns(ServerLevel level, BlockPos spawn, SpeciesDef def, WildPatchConfig config, int min,
                                int max, Tally tally) {
        List<Column> out = new ArrayList<>();
        for (int dx = -max; dx <= max; dx += STEP) {
            for (int dz = -max; dz <= max; dz += STEP) {
                double d = Math.sqrt((double) dx * dx + (double) dz * dz);
                if (d < min || d > max) {
                    continue;
                }
                tally.columns++;
                Column c = column(level, spawn.offset(dx, 0, dz), d, def, config, tally);
                if (c != null) {
                    out.add(c);
                }
            }
        }
        return out;
    }

    static Column column(ServerLevel level, BlockPos at, double dist, SpeciesDef def, WildPatchConfig config,
                         Tally tally) {
        if (!loadedBox(level, at, AREA)) {
            tally.unloaded++;
            return null;
        }
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ()) - 1;
        BlockPos surface = at.atY(top);
        BlockState ground = level.getBlockState(surface);
        if (!ground.getFluidState().isEmpty()) {
            tally.water++;
            return null; // water or lava
        }
        if (ground.isAir()) {
            tally.other++;
            return null; // no ground: the void, or the build limit
        }
        if (level.getHeight(Heightmap.Types.MOTION_BLOCKING, at.getX(), at.getZ()) - 1 != top) {
            tally.leaves++;
            return null; // under leaves
        }
        int lo = top;
        int hi = top;
        boolean grass = true;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX() + dx, at.getZ() + dz) - 1;
                BlockPos p = new BlockPos(at.getX() + dx, h, at.getZ() + dz);
                BlockState s = level.getBlockState(p);
                if (!s.getFluidState().isEmpty() || !level.getBlockState(p.above()).getFluidState().isEmpty()) {
                    tally.water++;
                    return null;
                }
                if (s.is(BlockTags.LEAVES)) {
                    tally.leaves++;
                    return null;
                }
                lo = Math.min(lo, h);
                hi = Math.max(hi, h);
                grass &= s.is(Blocks.GRASS_BLOCK);
            }
        }
        if (hi - lo > 1) {
            tally.slope++;
            return null;
        }
        int score = 0;
        Holder<Biome> biome = level.getBiome(surface.above());
        if (WildPatchFeature.category(biome, config.categories()) != null) {
            score += 3;
        }
        double t = ClimateService.temperature(level, surface.above());
        if (ClimateWindow.fits(t, def.tempMin(), def.tempMax(), 0, false)) {
            score += 2;
        }
        if (open(level, surface)) {
            score += 1;
        }
        return new Column(surface, score, grass, dist);
    }

    /**
     * Whether every chunk of the box {@code at ± r} is loaded. The patch,
     * its nest search (16 + crown 2 + side 1), the lone tree (12 + 3) and the
     * openness check all read inside {@value #AREA} blocks of the column:
     * on a {@code ServerLevel} a read of an unloaded chunk would load it
     * (review M2).
     */
    static boolean loadedBox(ServerLevel level, BlockPos at, int r) {
        for (int cx = (at.getX() - r) >> 4; cx <= (at.getX() + r) >> 4; cx++) {
            for (int cz = (at.getZ() - r) >> 4; cz <= (at.getZ() + r) >> 4; cz++) {
                if (level.getChunkSource().getChunkNow(cx, cz) == null) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Openness: within 8 blocks nothing stands 4+ above the surface, except one tree. */
    static boolean open(ServerLevel level, BlockPos surface) {
        int tall = 0;
        for (int dx = -8; dx <= 8; dx += 2) {
            for (int dz = -8; dz <= 8; dz += 2) {
                int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING, surface.getX() + dx, surface.getZ() + dz) - 1;
                if (h >= surface.getY() + 4) {
                    tall++;
                }
            }
        }
        return tall <= 4;
    }

    /**
     * Wild carrot patches in the ring around {@code center}: carrots on the
     * surface (one block read per column) grouped by their patch origin,
     * patches of ≥ {@value #MIN_EXISTING} plants, nearest first.
     */
    public static List<List<BlockPos>> existingPatches(ServerLevel level, BlockPos center, int min, int max) {
        Map<Long, List<BlockPos>> byOrigin = new LinkedHashMap<>();
        long r2min = (long) min * min;
        long r2max = (long) max * max;
        for (int cx = (center.getX() - max) >> 4; cx <= (center.getX() + max) >> 4; cx++) {
            for (int cz = (center.getZ() - max) >> 4; cz <= (center.getZ() + max) >> 4; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (int lx = 0; lx < 16; lx++) {
                    for (int lz = 0; lz < 16; lz++) {
                        int x = (cx << 4) + lx;
                        int z = (cz << 4) + lz;
                        long dx = x - center.getX();
                        long dz = z - center.getZ();
                        long d2 = dx * dx + dz * dz;
                        if (d2 < r2min || d2 > r2max) {
                            continue;
                        }
                        int y = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, lx, lz) + 1;
                        BlockPos p = new BlockPos(x, y, z);
                        if (!(chunk.getBlockState(p).getBlock() instanceof FieldPlantBlock b)
                                || !b.species().equals(ModBlocks.WILD_CARROT_SPECIES)
                                || !(chunk.getBlockEntity(p) instanceof PlantBlockEntity be) || be.data() == null
                                || be.data().passport().origin() != Passport.Origin.WILD) {
                            continue;
                        }
                        byOrigin.computeIfAbsent(be.data().passport().patchOrigin(), k -> new ArrayList<>()).add(p);
                    }
                }
            }
        }
        List<List<BlockPos>> out = new ArrayList<>();
        for (Map.Entry<Long, List<BlockPos>> e : byOrigin.entrySet()) {
            if (e.getValue().size() >= MIN_EXISTING) {
                out.add(e.getValue());
            }
        }
        out.sort(Comparator.comparingDouble(l -> l.get(0).distSqr(center)));
        return out;
    }
}
