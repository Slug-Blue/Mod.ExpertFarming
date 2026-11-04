package io.github.slugblue.wildroots.worldgen;

import io.github.slugblue.wildroots.core.rng.RngKeys;
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

    /** Overrides of the starter patch (worldgen §6.2 item 5); {@link #NATURAL} for worldgen. */
    public record Options(boolean starter, Optional<IntProvider> plants, Optional<WildPatchConfig.Stages> stages,
                          String fallbackCategory, int tries) {

        public static final Options NATURAL = new Options(false, Optional.empty(), Optional.empty(), "", 0);
    }

    public WildPatchFeature() {
        super(WildPatchConfig.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<WildPatchConfig> context) {
        return placePatch(context.level(), context.chunkGenerator(), context.origin(), context.config(),
                Options.NATURAL).placed();
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
        boolean rich = config.rich().isPresent() && RichRules.rich(roll, config.rich().get().chance(),
                options.starter(), origin.getX() - spawn.getX(), origin.getZ() - spawn.getZ(),
                config.rich().get().minFromSpawn());
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
            landmark(level, generator, origin, config, rng, random);
        }
        BeeNests.Result nest = config.beeNest()
                .map(n -> BeeNests.place(level, generator, origin, n, rich, options.starter(), rng, random))
                .orElse(BeeNests.Result.NONE);
        return new Result(true, List.copyOf(spots), category, rich, nest);
    }

    /**
     * The landmark of a rich patch (worldgen §5.3): its configured feature
     * (a mossy boulder) at the edge of the patch, {@code xz_spread + 2}
     * blocks from the centre, on the surface; nothing if it does not fit.
     */
    static void landmark(WorldGenLevel level, ChunkGenerator generator, BlockPos origin, WildPatchConfig c,
                         SplitMix64 rng, RandomSource random) {
        c.rich().flatMap(RichConfig::landmark).ifPresent(f -> {
            double a = rng.nextDouble() * 2 * Math.PI;
            int d = Math.min(WildPatchConfig.MAX_SPREAD, c.xzSpread()) + 2;
            int x = origin.getX() + (int) Math.round(StrictMath.cos(a) * d);
            int z = origin.getZ() + (int) Math.round(StrictMath.sin(a) * d);
            BlockPos at = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            if (Math.abs(at.getY() - origin.getY()) <= 3) {
                f.value().place(level, generator, random, at);
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

    /** Distinct valid positions, at most {@code target}, from at most {@code tries} attempts. */
    static List<BlockPos> spots(WorldGenLevel level, BlockPos origin, WildPatchConfig c, SplitMix64 rng, int target,
                                int tries) {
        List<BlockPos> out = new ArrayList<>(target);
        Set<BlockPos> seen = new HashSet<>();
        int xz = Math.min(WildPatchConfig.MAX_SPREAD, c.xzSpread());
        int y = Math.min(WildPatchConfig.MAX_SPREAD, c.ySpread());
        for (int t = 0; t < tries && out.size() < target; t++) {
            int dx = rng.nextInt(xz + 1) - rng.nextInt(xz + 1);
            int dz = rng.nextInt(xz + 1) - rng.nextInt(xz + 1);
            int dy = rng.nextInt(2 * y + 1) - y;
            BlockPos p = origin.offset(dx, dy, dz);
            if (seen.add(p) && fits(level, p, c)) {
                out.add(p);
            }
        }
        return out;
    }

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
