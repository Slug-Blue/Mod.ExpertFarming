package io.github.slugblue.wildroots.pollination;

import io.github.slugblue.wildroots.core.safety.EntryPoint;
import io.github.slugblue.wildroots.core.safety.Guarded;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;

/**
 * Keeps {@link BloomIndex} in step with the world (docs/05-architecture.md
 * §10.2; ADR-0008; GDD §4.1): flowering is entered and left on a stage
 * change ({@code FieldPlantBlock#onPlace}) and when the block entity of a
 * flowering plant loads ({@code onLoad}, also static wild plants of
 * worldgen §12.2); a removed block entity, an unloaded chunk and an
 * unloaded level take their entries away.
 */
public final class BloomTracker {

    private BloomTracker() {
    }

    /** Sets whether the plant of {@code species} at {@code pos} is in the index. */
    public static void set(Level level, BlockPos pos, String species, boolean flowering) {
        if (!(level instanceof ServerLevel server) || species == null) {
            return;
        }
        int sp = SpeciesIndex.of(species);
        if (flowering) {
            BloomIndex.of(server).add(sp, pos.asLong());
        } else {
            BloomIndex idx = BloomIndex.peek(server);
            if (idx != null) {
                idx.remove(sp, pos.asLong());
            }
        }
    }

    /** Whether the plant at {@code pos} is registered (tests, diagnostics). */
    public static boolean contains(ServerLevel level, BlockPos pos, String species) {
        BloomIndex idx = BloomIndex.peek(level);
        return idx != null && idx.contains(SpeciesIndex.of(species), pos.asLong());
    }

    /** Forge bus: the entries of an unloaded chunk go (the block entities also leave one by one). */
    @EntryPoint
    public static void onChunkUnload(ChunkEvent.Unload event) {
        LevelAccessor level = event.getLevel();
        if (level instanceof ServerLevel server) {
            Guarded.run("bloom chunk unload", event.getChunk().getPos(), () -> {
                BloomIndex idx = BloomIndex.peek(server);
                if (idx != null) {
                    idx.removeChunk(event.getChunk().getPos().toLong());
                }
            });
        }
    }

    /** Forge bus: the index of an unloaded level goes. */
    @EntryPoint
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel server) {
            Guarded.run("bloom level unload", server.dimension().location(), () -> BloomIndex.forget(server));
        }
    }
}
