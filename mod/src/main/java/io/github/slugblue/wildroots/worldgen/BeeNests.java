package io.github.slugblue.wildroots.worldgen;

import io.github.slugblue.wildroots.genetics.random.SplitMix64;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Step 7 of {@code wild_patch}: the bee nest (worldgen §7; GDD §4.3).
 * Deterministic from the patch RNG; the search stays within
 * {@value BeeNestConfig#MAX_DISTANCE} blocks of the centre, inside the 3×3
 * write region of a decoration step.
 *
 * <p>No nest when a {@code bee_nest} or {@code beehive} already stands within
 * 16 blocks (vanilla nests count, the bees are the same). Otherwise the
 * cascade of {@code fallbacks}:
 * <ul>
 *   <li>{@code tree} — up to 24 random columns in the ring; under the first
 *   column topped with leaves the trunk is searched (≤ 10 blocks down,
 *   ≤ 2 to the side: the column rarely hits the trunk itself) for a log
 *   with an air neighbour (south first); the nest goes into that air cell,
 *   facing away from the trunk, as the vanilla decorator does;</li>
 *   <li>{@code cliff} — a column of stone, terracotta or sandstone standing
 *   ≥ 2 above a neighbour column: the nest replaces the face block;</li>
 *   <li>{@code lone_tree} — only for the patch kinds in
 *   {@code lone_tree_only_for} (starter, rich): a flat grass or dirt cell
 *   in the ring 6–12 gets the configured lone tree (vanilla tree with the
 *   beehive decorator at probability 1, data only, §8.8);</li>
 *   <li>otherwise nothing (GDD §4.3).</li>
 * </ul>
 * The nest holds 2–3 bees as block entity occupants, not as entities.
 */
public final class BeeNests {

    /** Columns tried per cascade step (worldgen §7.2). */
    static final int COLUMNS = 24;
    /** Radius of the check for an existing nest (worldgen §7.4). */
    static final int EXISTING_RADIUS = 16;
    static final int TRUNK_DESCENT = 10;
    /** Horizontal reach from a leafy column to its trunk. */
    static final int CROWN_RADIUS = 2;
    /** Crowns searched per patch: one, ≤ 250 block reads (worldgen §12.2, nest ≈ 300). */
    static final int MAX_CROWNS = 1;
    /** Vertical reach of a nest from the patch centre. */
    static final int MAX_DY = 12;
    /** A lone tree stands on ground at most this far above or below the patch centre. */
    static final int LONE_TREE_DY = 4;
    /** Crown radius plus the hive of the tree decorator: the lone tree box that must stay in the region. */
    static final int LONE_TREE_MARGIN = 3;

    private BeeNests() {
    }

    /** Where the nest went. */
    public enum Kind { NONE, EXISTING, TREE, CLIFF, LONE_TREE }

    public record Result(Kind kind, BlockPos pos) {

        static final Result NONE = new Result(Kind.NONE, null);
    }

    /** Rolls the chance of the patch kind and runs the cascade. */
    public static Result place(WorldGenLevel level, ChunkGenerator generator, BlockPos center, BeeNestConfig c,
                               boolean rich, boolean starter, SplitMix64 rng, RandomSource random) {
        double chance = starter ? 1.0 : rich ? c.richChance() : c.chance();
        if (rng.nextDouble() >= chance) {
            return Result.NONE;
        }
        Optional<BlockPos> existing = existingNest(level, center);
        if (existing.isPresent()) {
            return new Result(Kind.EXISTING, existing.get());
        }
        int min = Math.max(0, c.minDistance());
        int max = Math.max(min, Math.min(BeeNestConfig.MAX_DISTANCE, c.maxDistance()));
        for (String step : c.fallbacks()) {
            Result r = switch (step) {
                case "tree" -> tree(level, center, c, min, max, rng, random);
                case "cliff" -> cliff(level, center, c, min, max, rng, random);
                case "lone_tree" -> loneTreeAllowed(c, rich, starter)
                        ? loneTree(level, generator, center, c, min, rng, random) : Result.NONE;
                default -> Result.NONE; // driftwood, snag: lines of later waves
            };
            if (r.kind() != Kind.NONE) {
                return r;
            }
        }
        return Result.NONE;
    }

    static boolean loneTreeAllowed(BeeNestConfig c, boolean rich, boolean starter) {
        return (starter && c.loneTreeOnlyFor().contains("starter")) || (rich && c.loneTreeOnlyFor().contains("rich"));
    }

    /** A nest or hive within 16 blocks, found through the block entity positions of the nearby chunks. */
    static Optional<BlockPos> existingNest(WorldGenLevel level, BlockPos center) {
        int r2 = EXISTING_RADIUS * EXISTING_RADIUS;
        for (int cx = (center.getX() - EXISTING_RADIUS) >> 4; cx <= (center.getX() + EXISTING_RADIUS) >> 4; cx++) {
            for (int cz = (center.getZ() - EXISTING_RADIUS) >> 4; cz <= (center.getZ() + EXISTING_RADIUS) >> 4; cz++) {
                if (!level.hasChunk(cx, cz)) {
                    continue;
                }
                ChunkAccess chunk = level.getChunk(cx, cz);
                for (BlockPos p : chunk.getBlockEntitiesPos()) {
                    if (p.distSqr(center) <= r2 && chunk.getBlockState(p).is(BlockTags.BEEHIVES)) {
                        return Optional.of(p.immutable());
                    }
                }
            }
        }
        return Optional.empty();
    }

    /** A column {@code (x, z)} at horizontal distance {@code min..max} from the centre. */
    static BlockPos column(BlockPos center, int min, int max, SplitMix64 rng) {
        double a = rng.nextDouble() * 2 * Math.PI;
        double d = min + rng.nextDouble() * (max - min);
        return center.offset((int) Math.round(StrictMath.cos(a) * d), 0, (int) Math.round(StrictMath.sin(a) * d));
    }

    static final Direction[] SIDES = {Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.NORTH};

    static Result tree(WorldGenLevel level, BlockPos center, BeeNestConfig c, int min, int max, SplitMix64 rng,
                       RandomSource random) {
        int crowns = 0;
        for (int i = 0; i < COLUMNS && crowns < MAX_CROWNS; i++) {
            BlockPos col = column(center, min, max, rng);
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, col.getX(), col.getZ()) - 1;
            BlockPos p = col.atY(top);
            if (!level.getBlockState(p).is(BlockTags.LEAVES)) {
                continue;
            }
            crowns++;
            // under the crown: the trunk is the log nearest to the column, within the crown radius
            for (int d = 1; d <= TRUNK_DESCENT; d++) {
                for (int r = 0; r <= CROWN_RADIUS; r++) {
                    for (int dx = -r; dx <= r; dx++) {
                        for (int dz = -r; dz <= r; dz++) {
                            if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                                continue;
                            }
                            BlockPos log = p.offset(dx, -d, dz);
                            if (!within(center, log) || !level.getBlockState(log).is(BlockTags.LOGS)) {
                                continue;
                            }
                            for (Direction side : SIDES) {
                                BlockPos air = log.relative(side);
                                if (within(center, air) && level.getBlockState(air).isAir()
                                        && setNest(level, air, side, c, random)) {
                                    return new Result(Kind.TREE, air);
                                }
                            }
                        }
                    }
                }
            }
        }
        return Result.NONE;
    }

    static Result cliff(WorldGenLevel level, BlockPos center, BeeNestConfig c, int min, int max, SplitMix64 rng,
                        RandomSource random) {
        for (int i = 0; i < COLUMNS; i++) {
            BlockPos col = column(center, min, max, rng);
            int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, col.getX(), col.getZ()) - 1;
            BlockPos p = col.atY(top);
            BlockState st = level.getBlockState(p);
            if (!(st.is(BlockTags.BASE_STONE_OVERWORLD) || st.is(BlockTags.TERRACOTTA) || st.is(Blocks.SANDSTONE))) {
                continue;
            }
            for (Direction side : SIDES) {
                BlockPos n = p.relative(side);
                int below = level.getHeight(Heightmap.Types.WORLD_SURFACE, n.getX(), n.getZ()) - 1;
                if (top - below >= 2 && within(center, p) && level.getBlockState(n).isAir()
                        && setNest(level, p, side, c, random)) {
                    return new Result(Kind.CLIFF, p);
                }
            }
        }
        return Result.NONE;
    }

    static Result loneTree(WorldGenLevel level, ChunkGenerator generator, BlockPos center, BeeNestConfig c, int min,
                           SplitMix64 rng, RandomSource random) {
        if (c.loneTreeFeature().isEmpty()) {
            return Result.NONE;
        }
        int max = Math.max(min, Math.min(BeeNestConfig.LONE_TREE_MAX_DISTANCE, c.maxDistance()));
        for (int i = 0; i < COLUMNS; i++) {
            BlockPos col = column(center, min, max, rng);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, col.getX(), col.getZ());
            BlockPos at = col.atY(y);
            BlockState ground = level.getBlockState(at.below());
            if (!WriteRegion.containsBox(center.getX(), center.getZ(), at.getX(), at.getZ(), LONE_TREE_MARGIN)
                    || Math.abs(at.getY() - center.getY()) > LONE_TREE_DY
                    || !(ground.is(Blocks.GRASS_BLOCK) || ground.is(Blocks.DIRT)) || !level.getBlockState(at).canBeReplaced()
                    || !flat(level, at)) {
                continue;
            }
            if (c.loneTreeFeature().get().value().place(level, generator, random, at)) {
                return new Result(Kind.LONE_TREE, at);
            }
        }
        return Result.NONE;
    }

    /** The 3×3 around {@code at} is within one block of its height. */
    static boolean flat(WorldGenLevel level, BlockPos at) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX() + dx, at.getZ() + dz);
                if (Math.abs(h - at.getY()) > 1) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * At most 16 blocks from the centre horizontally, inside the 3×3 write
     * region of the decoration step ({@link WriteRegion}) and at most
     * {@value #MAX_DY} blocks above or below the centre.
     */
    static boolean within(BlockPos center, BlockPos p) {
        return Math.abs(p.getY() - center.getY()) <= MAX_DY
                && WriteRegion.nestSpot(center.getX(), center.getZ(), p.getX(), p.getZ());
    }

    /**
     * A vanilla {@code bee_nest} facing {@code facing} with 2–3 bees stored
     * as occupants; {@code false} when the level refused the block.
     */
    static boolean setNest(WorldGenLevel level, BlockPos pos, Direction facing, BeeNestConfig c, RandomSource random) {
        if (!level.setBlock(pos, Blocks.BEE_NEST.defaultBlockState().setValue(BeehiveBlock.FACING, facing), 2)) {
            return false;
        }
        if (level.getBlockEntity(pos) instanceof BeehiveBlockEntity hive) {
            int bees = c.bees().sample(random);
            for (int i = 0; i < bees; i++) {
                CompoundTag tag = new CompoundTag();
                tag.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(EntityType.BEE).toString());
                hive.storeBee(tag, random.nextInt(599), false);
            }
        }
        return true;
    }
}
