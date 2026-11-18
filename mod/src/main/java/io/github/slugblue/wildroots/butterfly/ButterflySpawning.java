package io.github.slugblue.wildroots.butterfly;

import io.github.slugblue.wildroots.core.registry.ModEntities;
import io.github.slugblue.wildroots.core.safety.EntryPoint;
import io.github.slugblue.wildroots.core.safety.Guarded;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;

/** Bounded natural spawning: at most four loaded chunks per level every ten seconds. */
public final class ButterflySpawning {
    public static final int MIN_FLOWERS = 4;
    public static final int MAX_PER_CHUNK = 3;
    public static final float FLOWER_CHANCE = .25f;
    private ButterflySpawning() { }

    public static boolean eligible(boolean birch, int flowers) { return birch || flowers >= MIN_FLOWERS; }

    public static int countFlowers(LevelChunk chunk) {
        int found = 0;
        for (var section : chunk.getSections()) {
            if (section.hasOnlyAir() || !section.maybeHas(s -> s.is(BlockTags.FLOWERS))) continue;
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
                var state = section.getBlockState(x, y, z);
                if (state.is(BlockTags.FLOWERS) && (!state.hasProperty(DoublePlantBlock.HALF)
                        || state.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.LOWER) && ++found >= MIN_FLOWERS)
                    return found;
            }
        }
        return found;
    }

    @EntryPoint
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)
                || level.getGameTime() % 200 != 0 || !level.isDay() || level.isRaining()
                || !level.dimensionType().hasSkyLight() || !level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)
                || level.players().isEmpty()) return;
        Guarded.run("butterfly spawning", level.dimension().location(), () -> {
            var players = level.players();
            int start = (int) ((level.getGameTime() / 200) % players.size());
            for (int i = 0; i < Math.min(4, players.size() * 2); i++) {
                var player = players.get((start + i) % players.size());
                if (player.isSpectator()) continue;
                ChunkPos p = player.chunkPosition();
                tryChunk(level, new ChunkPos(p.x + level.random.nextInt(9) - 4, p.z + level.random.nextInt(9) - 4), level.random);
            }
        });
    }

    /** Does not load a chunk or spawn at night/in rain; exposed for deterministic server tests. */
    public static int tryChunk(ServerLevel level, ChunkPos pos, RandomSource random) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x, pos.z);
        if (chunk == null || !level.isDay() || level.isRaining()) return 0;
        int x = pos.getMiddleBlockX(), z = pos.getMiddleBlockZ();
        BlockPos middle = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
        var biome = level.getBiome(middle);
        boolean birch = biome.is(Biomes.BIRCH_FOREST) || biome.is(Biomes.OLD_GROWTH_BIRCH_FOREST);
        if (!eligible(birch, countFlowers(chunk)) || !birch && random.nextFloat() >= FLOWER_CHANCE) return 0;
        int existing = level.getEntitiesOfClass(ButterflyEntity.class,
                new AABB(pos.getMinBlockX(), level.getMinBuildHeight(), pos.getMinBlockZ(),
                        pos.getMaxBlockX() + 1, level.getMaxBuildHeight(), pos.getMaxBlockZ() + 1)).size();
        int count = Math.min(MAX_PER_CHUNK - existing, birch ? 1 + random.nextInt(3) : 1);
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            int sx = pos.getMinBlockX() + random.nextInt(16), sz = pos.getMinBlockZ() + random.nextInt(16);
            BlockPos at = new BlockPos(sx, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sx, sz) + 1, sz);
            if (!level.isEmptyBlock(at) || !level.getFluidState(at.below()).isEmpty()) continue;
            ButterflyEntity butterfly = ModEntities.BUTTERFLY.get().create(level);
            if (butterfly != null) {
                butterfly.moveTo(at.getX() + .5, at.getY(), at.getZ() + .5, random.nextFloat() * 360, 0);
                if (level.addFreshEntity(butterfly)) spawned++;
            }
        }
        return spawned;
    }
}
