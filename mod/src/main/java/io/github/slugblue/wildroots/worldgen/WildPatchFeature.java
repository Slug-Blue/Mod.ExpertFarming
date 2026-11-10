package io.github.slugblue.wildroots.worldgen;

import io.github.slugblue.wildroots.core.rng.RngKeys;
import io.github.slugblue.wildroots.core.safety.EntryPoint;
import io.github.slugblue.wildroots.core.safety.Guarded;
import io.github.slugblue.wildroots.data.Definitions;
import io.github.slugblue.wildroots.genetics.random.SplitMix64;
import io.github.slugblue.wildroots.plant.FieldPlantBlock;
import io.github.slugblue.wildroots.plant.Passport;
import io.github.slugblue.wildroots.plant.PlantBlockEntity;
import io.github.slugblue.wildroots.plant.PlantService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;

/**
 * {@code wildroots:wild_patch} (worldgen §3): one wild population of a
 * species, placed as a whole or not at all.
 *
 * <ol>
 *   <li>The origin comes from the placed feature (chunk band, surface,
 *   biome, climate). {@code patch_seed = Hash64(worldSeed, origin)} (no world
 *   secret: a seed reproduces the world); the category is the first of the
 *   species' categories whose biome tag holds the origin biome.</li>
 *   <li>Up to {@code tries} positions, triangular offsets (denser to the
 *   centre), each: air or replaceable, no fluid, soil of the species below,
 *   nothing motion-blocking above (no caves, no dense canopy; worldgen
 *   features run before the light engine, so the heightmap stands for
 *   "sees the sky").</li>
 *   <li>Fewer than {@code min_plants}: nothing is placed (positions are
 *   chosen first, so there is nothing to roll back).</li>
 *   <li>Stages by the shares; every plant is a <b>static</b> wild plant
 *   ({@code active = false}, no random ticks) whose block entity holds only
 *   the passport: origin, category, {@code rich}, {@code starter}. The
 *   genome is not generated here; it is realised on the first server access
 *   (worldgen §3.3).</li>
 *   <li>Rich (worldgen §5): the patch roll against {@code rich.chance},
 *   never the starter patch and never closer than 256 blocks to the world
 *   spawn ({@link RichRules}); a rich patch has 14–20 plants, +50 % tries,
 *   a landmark at its edge and a certain nest.</li>
 *   <li>The bee nest ({@link BeeNests}, worldgen §7), from the same patch
 *   RNG.</li>
 * </ol>
 */
public class WildPatchFeature extends Feature<WildPatchConfig> {

    /** What a call placed (tests, starter patch). */
    public record Result(boolean placed, List<BlockPos> plants, String category, boolean rich,
                         BeeNests.Result nest) {

        static final Result NONE = new Result(false, List.of(), "", false, BeeNests.Result.NONE);
    }

    /**
     * Whether a patch is rich: {@link #AUTO} rolls the rule of worldgen §5
     * (chance and distance from spawn); {@link #NEVER} and {@link #ALWAYS}
     * fix it. Java callers only (GameTests): {@link Options} is no part of
     * the feature codec, data packs and commands cannot set it, and worldgen
     * always uses {@link Options#NATURAL} (AUTO).
     */
    public enum Rich {
        AUTO, NEVER, ALWAYS
    }

    /** Overrides of the starter patch (worldgen §6.2 item 5) and tests; {@link #NATURAL} for worldgen. */
    public record Options(boolean starter, Optional<IntProvider> plants, Optional<WildPatchConfig.Stages> stages,
                          String fallbackCategory, int tries, Rich rich) {

        public static final Options NATURAL = new Options(false, Optional.empty(), Optional.empty(), "", 0, Rich.AUTO);

        public Options {
            rich = rich == null ? Rich.AUTO : rich;
        }

        /** The rich rule of worldgen §5 (AUTO). */
        public Options(boolean starter, Optional<IntProvider> plants, Optional<WildPatchConfig.Stages> stages,
                       String fallbackCategory, int tries) {
            this(starter, plants, stages, fallbackCategory, tries, Rich.AUTO);
        }

        public Options withRich(Rich r) {
            return new Options(starter, plants, stages, fallbackCategory, tries, r);
        }
    }

    public WildPatchFeature() {
        super(WildPatchConfig.CODEC);
    }

    /**
     * Worldgen entry point: a failure skips this patch instead of crashing
     * the chunk generation (review 1, m4; SEC-34).
     */
    @Override
    @EntryPoint
    public boolean place(FeaturePlaceContext<WildPatchConfig> context) {
        return Guarded.call("wild_patch", context.origin(), () -> placePatch(context.level(), context.chunkGenerator(),
                context.origin(), context.config(), Options.NATURAL).placed(), false);
    }

    /** Places a patch of {@code config} at {@code origin}. */
    public Result placePatch(WorldGenLevel level, ChunkGenerator generator, BlockPos origin, WildPatchConfig config,
                             Options options) {
        if (Definitions.current().species(config.species()).isEmpty()) {
            return Result.NONE;
        }
        Optional<FieldPlantBlock> block = PlantService.blockFor(config.species());
        if (block.isEmpty()) {
            return Result.NONE;
        }
        long patchSeed = RngKeys.patchSeed(level.getSeed(), origin.asLong());
        SplitMix64 rng = new SplitMix64(patchSeed);
        RandomSource random = RandomSource.create(rng.nextLong());
        String category = category(level.getBiome(origin), config.categories());
        if (category == null) {
            if (options.fallbackCategory().isEmpty()) {
                return Result.NONE;
            }
            category = options.fallbackCategory();
        }
        double roll = rng.nextDouble();
        BlockPos spawn = new BlockPos(level.getLevelData().getXSpawn(), 0, level.getLevelData().getZSpawn());
        boolean rich = config.rich().isPresent() && switch (options.rich()) {
            case AUTO -> RichRules.rich(roll, config.rich().get().chance(), options.starter(),
                    origin.getX() - spawn.getX(), origin.getZ() - spawn.getZ(), config.rich().get().minFromSpawn());
            case NEVER -> false;
            case ALWAYS -> true;
        };
        IntProvider plants = options.plants().orElse(rich ? config.rich().get().plants() : config.plants());
        int target = Math.min(WildPatchConfig.MAX_PLANTS, plants.sample(random));
        int tries = options.tries() > 0 ? options.tries() : config.tries();
        if (rich) {
            tries += tries / 2;
        }
        List<BlockPos> spots = spots(level, origin, config, rng, target, tries);
        if (spots.size() < Math.min(config.minPlants(), target)) {
            return Result.NONE;
        }
        int[] stages = stages(spots.size(), options.stages().orElse(config.stages()), rng);
        Passport passport = Passport.wild(origin.asLong(), category, rich, options.starter());
        for (int i = 0; i < spots.size(); i++) {
            BlockPos at = spots.get(i);
            BlockState state = block.get().getStateForAge(stages[i]).setValue(FieldPlantBlock.ACTIVE, false);
            level.setBlock(at, state, 2);
            BlockEntity be = level.getBlockEntity(at);
            if (be instanceof PlantBlockEntity plant) {
                plant.initWild(config.species(), passport);
            }
        }
        if (rich) {
            landmark(level, generator, origin, config, spots, rng, random);
        }
        BeeNests.Result nest = config.beeNest()
                .map(n -> BeeNests.place(level, generator, origin, n, rich, options.starter(), rng, random))
                .orElse(BeeNests.Result.NONE);
        return new Result(true, List.copyOf(spots), category, rich, nest);
    }

    /** The landmark stands this far beyond the spread (a blob of radius ≈ 1 stays clear of the plants). */
    static final int LANDMARK_OFFSET = 4;

    /** See {@link RichRules#LANDMARK_CLEARANCE}. */
    static final int LANDMARK_CLEARANCE = RichRules.LANDMARK_CLEARANCE;

    /**
     * The landmark of a rich patch (worldgen §5.3): its configured feature
     * (a mossy boulder) just outside the patch, {@code xz_spread + 4}
     * blocks from the centre, on the surface and more than
     * {@value #LANDMARK_CLEARANCE} blocks from every plant; up to 8 angles
     * are tried, nothing if none fits.
     */
    static void landmark(WorldGenLevel level, ChunkGenerator generator, BlockPos origin, WildPatchConfig c,
                         List<BlockPos> plants, SplitMix64 rng, RandomSource random) {
        c.rich().flatMap(RichConfig::landmark).ifPresent(f -> {
            int d = Math.min(WildPatchConfig.MAX_SPREAD, c.xzSpread()) + LANDMARK_OFFSET;
            double a0 = rng.nextDouble() * 2 * Math.PI;
            for (int k = 0; k < 8; k++) {
                double a = a0 + k * Math.PI / 4;
                int x = origin.getX() + (int) Math.round(StrictMath.cos(a) * d);
                int z = origin.getZ() + (int) Math.round(StrictMath.sin(a) * d);
                BlockPos at = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
                if (RichRules.landmarkClear(plants, x, z) && Math.abs(at.getY() - origin.getY()) <= 3) {
                    f.value().place(level, generator, random, at);
                    return;
                }
            }
        });
    }

    /** First category of {@code categories} whose tag {@code wildroots:patch_category/<id>} holds {@code biome}. */
    static String category(Holder<Biome> biome, List<String> categories) {
        for (String c : categories) {
            ResourceLocation id = ResourceLocation.tryBuild("wildroots", "patch_category/" + c);
            if (id != null && biome.is(TagKey.create(Registries.BIOME, id))) {
                return c;
            }
        }
        return null;
    }

    /**
     * Distinct valid positions, at most {@code target}, from at most
     * {@code tries} columns (review 1, M4): a try draws a column by the
     * triangular offset and takes the highest cell of
     * {@code origin.y ± y_spread} that fits, so uneven ground does not
     * waste tries; a column drawn again does not count as a try (at most
     * {@value #DRAW_FACTOR}× {@code tries} draws in all).
     */
    static List<BlockPos> spots(WorldGenLevel level, BlockPos origin, WildPatchConfig c, SplitMix64 rng, int target,
                                int tries) {
        List<BlockPos> out = new ArrayList<>(target);
        Set<Long> columns = new HashSet<>();
        int xz = Math.min(WildPatchConfig.MAX_SPREAD, c.xzSpread());
        int y = Math.min(WildPatchConfig.MAX_SPREAD, c.ySpread());
        int used = 0;
        for (int draw = 0; draw < tries * DRAW_FACTOR && used < tries && out.size() < target; draw++) {
            int dx = rng.nextInt(xz + 1) - rng.nextInt(xz + 1);
            int dz = rng.nextInt(xz + 1) - rng.nextInt(xz + 1);
            if (!columns.add(((long) dx << 32) ^ (dz & 0xFFFFFFFFL))) {
                continue;
            }
            used++;
            for (int dy = y; dy >= -y; dy--) {
                BlockPos p = origin.offset(dx, dy, dz);
                if (fits(level, p, c)) {
                    out.add(p);
                    break;
                }
            }
        }
        return out;
    }

    /** Draw limit per try when columns repeat. */
    static final int DRAW_FACTOR = 4;

    /** Air or replaceable, no fluid, soil below, open to the sky (worldgen §3.2 item 3). */
    static boolean fits(WorldGenLevel level, BlockPos p, WildPatchConfig c) {
        if (level.isOutsideBuildHeight(p)) {
            return false;
        }
        BlockState at = level.getBlockState(p);
        if (!(at.isAir() || c.replaceable().contains(at.getBlockHolder())) || !at.getFluidState().isEmpty()) {
            return false;
        }
        if (!c.soil().contains(level.getBlockState(p.below()).getBlockHolder())) {
            return false;
        }
        return p.getY() >= level.getHeight(Heightmap.Types.MOTION_BLOCKING, p.getX(), p.getZ());
    }

    /** Stage of every plant: {@code round(n·flowering)} at 4, {@code round(n·seeding)} at 5, the rest at 3; shuffled. */
    static int[] stages(int n, WildPatchConfig.Stages s, SplitMix64 rng) {
        int flowering = Math.min(n, Math.round(n * s.flowering()));
        int seeding = Math.min(n - flowering, Math.round(n * s.seeding()));
        int[] out = new int[n];
        for (int i = 0; i < n; i++) {
            out[i] = i < flowering ? FieldPlantBlock.FLOWER_STAGE
                    : i < flowering + seeding ? FieldPlantBlock.SEED_STAGE : FieldPlantBlock.HARVEST_STAGE;
        }
        for (int i = n - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int tmp = out[i];
            out[i] = out[j];
            out[j] = tmp;
        }
        return out;
    }
}
