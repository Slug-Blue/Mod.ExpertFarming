package io.github.slugblue.wildroots.core.safety;

import java.security.SecureRandom;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.level.LevelEvent;

/**
 * The world secret (SEC-25; docs/05-architecture.md §4.3, ADR-0001 rev. 1):
 * a {@code long} from {@link SecureRandom}, kept in the overworld
 * {@code SavedData} {@value #NAME}, created on the first load of the
 * overworld and never changed afterwards.
 *
 * <p>It enters the RNG keys of planting, planting noise, father choice and
 * lazy lots ({@code core.rng.RngKeys}), so a modified client that knows a
 * lot's genome cannot compute the seedling of a given cell. It never goes to
 * a client, never into a log, never out of a command: {@link #toString()}
 * hides it, and the class is not reachable from {@code network},
 * {@code client} or {@code compat} (ArchUnit {@code worldSecretHidden}).
 * Worldgen and village keys stay on {@code worldSeed}.
 */
public final class WorldSecret extends SavedData {

    public static final String NAME = "wildroots_secret";
    private static final String KEY = "s";

    private final long value;

    private WorldSecret(long value) {
        this.value = value;
    }

    private static WorldSecret create() {
        WorldSecret s = new WorldSecret(new SecureRandom().nextLong());
        s.setDirty();
        return s;
    }

    /** Reads the stored secret; a damaged file gets a fresh secret, saved on the next autosave. */
    public static WorldSecret load(CompoundTag tag) {
        if (tag.contains(KEY, Tag.TAG_LONG)) {
            return new WorldSecret(tag.getLong(KEY));
        }
        return create();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putLong(KEY, value);
        return tag;
    }

    /** The secret of the server's world, created if the overworld has none yet. */
    public static long get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(WorldSecret::load, WorldSecret::create, NAME).value;
    }

    /** The stored secret without creating one (tests and diagnostics). */
    public static Optional<Long> peek(MinecraftServer server) {
        WorldSecret s = server.overworld().getDataStorage().get(WorldSecret::load, NAME);
        return s == null ? Optional.empty() : Optional.of(s.value);
    }

    /** Creates the secret on the first load of the overworld (Forge event bus). */
    public static void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension() == Level.OVERWORLD) {
            Guarded.run("world secret", NAME, () -> get(level.getServer()));
        }
    }

    @Override
    public String toString() {
        return "WorldSecret[hidden]";
    }
}
