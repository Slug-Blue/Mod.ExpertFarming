package io.github.slugblue.wildroots.butterfly;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Server-thread-only leases. No entity/world references in values; both size and lifetime are bounded. */
final class ButterflyVisits {
    private static final int MAX_CLAIMS = 2048;
    private static final long LEASE_TICKS = 200;
    private record Claim(int owner, long until) { }
    private static final Map<ServerLevel, Map<Long, Claim>> LEVELS = new WeakHashMap<>();
    private ButterflyVisits() { }

    static boolean available(ServerLevel level, BlockPos pos, int owner) {
        var claims = LEVELS.get(level);
        if (claims == null) return true;
        var claim = claims.get(pos.asLong());
        return claim == null || claim.owner() == owner || claim.until() <= level.getGameTime();
    }

    static boolean acquire(ServerLevel level, BlockPos pos, int owner) {
        if (!available(level, pos, owner)) return false;
        var claims = LEVELS.computeIfAbsent(level, key -> new HashMap<>());
        if (claims.size() >= MAX_CLAIMS) claims.values().removeIf(c -> c.until() <= level.getGameTime());
        if (claims.size() >= MAX_CLAIMS && !claims.containsKey(pos.asLong())) return false;
        claims.put(pos.asLong(), new Claim(owner, level.getGameTime() + LEASE_TICKS));
        return true;
    }

    static void release(ServerLevel level, BlockPos pos, int owner) {
        var claims = LEVELS.get(level);
        if (claims == null || pos == null) return;
        var claim = claims.get(pos.asLong());
        if (claim != null && claim.owner() == owner) claims.remove(pos.asLong());
    }
}
