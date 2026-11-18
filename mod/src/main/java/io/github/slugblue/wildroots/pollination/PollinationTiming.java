package io.github.slugblue.wildroots.pollination;

import io.github.slugblue.wildroots.genetics.pollination.Brush;
import io.github.slugblue.wildroots.plant.PlantBlockEntity;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Flowering work in game-tick equivalents at randomTickSpeed 3; the gamerule scales its server ticker. */
public final class PollinationTiming {
    public static final long MAX_WORK = 9600L * 3;
    public enum Method {
        SELF(9600), NEIGHBOURS(6000), BUTTERFLIES(3600), BEES(2400), BRUSH(600);
        private final int ticks;
        Method(int ticks) { this.ticks = ticks; }
        public int ticks() { return ticks; }
    }

    private PollinationTiming() { }

    public static boolean elapsed(long started, long now, Method method) {
        return started >= 0 && now >= started && now - started >= method.ticks();
    }

    public static long nextWork(long work, int randomTickSpeed) {
        return Math.min(MAX_WORK, Math.min(MAX_WORK, Math.max(0, work)) + Math.max(0, randomTickSpeed));
    }

    public static boolean completed(long work, Method method) {
        return work >= method.ticks() * 3L;
    }

    /** One authoritative game tick. Paused worlds skip decoding; duplicate calls cannot give extra work. */
    public static boolean gameTick(ServerLevel level, PlantBlockEntity be) {
        int speed = level.getGameRules().getInt(net.minecraft.world.level.GameRules.RULE_RANDOMTICKING);
        if (speed <= 0 || be.data() == null || be.status() != PlantBlockEntity.Status.OK
                || !be.claimPollinationTick(level.getGameTime())) return false;
        be.setPollinationWork(nextWork(be.data().pollinationWork(), speed));
        return true;
    }

    public static Method method(ServerLevel level, BlockPos pos, PlantBlockEntity.Live live) {
        var pending = live.data().pending();
        if (pending != null) {
            if (pending.brush() != Brush.NONE) return Method.BRUSH;
            if (pending.bees()) return Method.BEES;
            if (pending.butterflies()) return Method.BUTTERFLIES;
            if (!pending.fathers().isEmpty()) return Method.NEIGHBOURS;
        }
        var candidates = PollinationService.candidates(level, pos, live.def(), pos.asLong()).stream()
                .map(PollinationService.Found::candidate).toList();
        var selection = live.genetics().chooseFathers(live.def(), live.genome(), candidates, List.of(), pos.asLong());
        if (selection.map(s -> s.compatibleNeighbours() > 0).orElse(false)) {
            // Pollen received now survives the neighbour being harvested or ripening first.
            var fathers = selection.get().fathers().stream().map(f -> new io.github.slugblue.wildroots.item.LotFathersNbt.Father(
                    live.genetics().encode(f.genome()).toFather(), (float) f.weight(), false)).toList();
            io.github.slugblue.wildroots.core.safety.LoadedLevelAccess.blockEntity(level, pos, PlantBlockEntity.class)
                    .ifPresent(be -> be.setPendingLot(new io.github.slugblue.wildroots.plant.PendingLot(
                            fathers, 0, Brush.NONE, false)));
            return Method.NEIGHBOURS;
        }
        return Method.SELF;
    }

    public static boolean ready(ServerLevel level, BlockPos pos, PlantBlockEntity be, PlantBlockEntity.Live live) {
        if (live.data().floweringSince() < 0 || live.data().floweringSince() > level.getGameTime()) {
            be.setFloweringSince(level.getGameTime()); // old flowering plants receive a full cycle
        }
        return completed(live.data().pollinationWork(), method(level, pos, live));
    }
}
