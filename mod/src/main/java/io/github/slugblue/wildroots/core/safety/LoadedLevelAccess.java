package io.github.slugblue.wildroots.core.safety;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Reads blocks and block entities only in chunks that are already loaded
 * (SEC-32; docs/05-architecture.md §3 {@code core.safety}, {@code 09-security.md} Δ8).
 *
 * <p>Pollination, bees, trunk search, POI queries, Jade and the conversion
 * of old crops read neighbour positions through this class only: a position
 * in an unloaded chunk gives "empty" and never loads or generates the chunk.
 * Direct {@code Level#getBlockState}/{@code getBlockEntity} on computed
 * positions is forbidden in {@code plant}, {@code pollination},
 * {@code village} and {@code compat} (ArchUnit
 * {@code levelReadsThroughLoadedLevelAccess}).
 */
public final class LoadedLevelAccess {

    private LoadedLevelAccess() {
    }

    /** The loaded chunk of {@code pos}, or {@code null}; never loads a chunk. */
    private static LevelChunk loadedChunk(Level level, BlockPos pos) {
        if (level == null || pos == null || level.isOutsideBuildHeight(pos)) {
            return null;
        }
        return level.getChunkSource().getChunkNow(SectionPos.blockToSectionCoord(pos.getX()),
                SectionPos.blockToSectionCoord(pos.getZ()));
    }

    /** Whether {@code pos} lies in a loaded chunk. */
    public static boolean isLoaded(Level level, BlockPos pos) {
        return loadedChunk(level, pos) != null;
    }

    /** Block state at {@code pos} if its chunk is loaded. */
    public static Optional<BlockState> blockState(Level level, BlockPos pos) {
        LevelChunk chunk = loadedChunk(level, pos);
        return chunk == null ? Optional.empty() : Optional.of(chunk.getBlockState(pos));
    }

    /** Block entity at {@code pos} if its chunk is loaded and it is not removed. */
    public static Optional<BlockEntity> blockEntity(Level level, BlockPos pos) {
        if (loadedChunk(level, pos) == null) {
            return Optional.empty();
        }
        BlockEntity be = level.getBlockEntity(pos);
        return be == null || be.isRemoved() ? Optional.empty() : Optional.of(be);
    }

    /** Block entity of the expected type at {@code pos} if its chunk is loaded. */
    public static <T extends BlockEntity> Optional<T> blockEntity(Level level, BlockPos pos, Class<T> type) {
        return blockEntity(level, pos).filter(type::isInstance).map(type::cast);
    }
}
