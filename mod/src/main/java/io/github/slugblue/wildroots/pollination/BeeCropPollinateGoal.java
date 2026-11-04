package io.github.slugblue.wildroots.pollination;

import io.github.slugblue.wildroots.core.safety.EntryPoint;
import io.github.slugblue.wildroots.core.safety.Guarded;
import io.github.slugblue.wildroots.core.safety.LoadedLevelAccess;
import io.github.slugblue.wildroots.data.Definitions;
import io.github.slugblue.wildroots.plant.FieldPlantBlock;
import io.github.slugblue.wildroots.plant.PlantBlockEntity;
import java.util.EnumSet;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Our bee pollination goal (docs/05-architecture.md §10.3; ADR-0009; S2),
 * next to the vanilla {@code Bee.BeePollinateGoal} with the same priority
 * ({@value #PRIORITY}) and flag {@code MOVE}: the one that starts first runs,
 * they do not push each other out (S2).
 *
 * <p>Target without scanning the world: when the bee needs nectar, the goal
 * asks {@link BloomIndex} for flowering plants of species with {@code bees}
 * within {@value #RANGE} blocks (at most once per 20 ticks per bee, ≤ 64 bee
 * queries per level and tick, {@link BeeQueryLimiter}). Without such a
 * plant the goal does not start and the vanilla goal visits flowers as usual.
 * Flight and hover follow the vanilla goal (hover {@value #HOVER_TICKS}
 * ticks, give up after {@value #GIVE_UP_TICKS}). {@link #tick()} is
 * idempotent after the visit: {@code GoalSelector} rechecks
 * {@code canContinueToUse} every 2 ticks, so a {@code tick()} can come
 * before {@code stop()} (S2 found an NPE there). The goal keeps no reference
 * to other entities.
 */
public final class BeeCropPollinateGoal extends Goal {

    /** Priority of the vanilla pollinate goal of {@code Bee#registerGoals} (S2). */
    public static final int PRIORITY = 4;
    /** Search radius around the bee, as the vanilla flower search (~5 blocks). */
    public static final int RANGE = 5;
    static final double RANGE_SQ = 5.5 * 5.5;
    static final int HOVER_TICKS = 400;
    static final int GIVE_UP_TICKS = 1200;
    static final int MAX_TARGETS = 16;

    private final Bee bee;
    private final BeeQueryLimiter.Gate gate = new BeeQueryLimiter.Gate();
    private BlockPos target;
    private int ticks;
    private int hoverTicks;

    public BeeCropPollinateGoal(Bee bee) {
        this.bee = bee;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    @EntryPoint
    public boolean canUse() {
        return Guarded.call("bee goal canUse", "bee", this::pickIfNeeded, false);
    }

    private boolean pickIfNeeded() {
        if (!(bee.level() instanceof ServerLevel level) || bee.hasNectar() || bee.isAngry() || level.isRaining()) {
            return false;
        }
        if (!gate.tryQuery(level.getGameTime(), BeeQueryLimiter.of(level))) {
            return false;
        }
        target = pickTarget(level);
        return target != null;
    }

    private BlockPos pickTarget(ServerLevel level) {
        BloomIndex idx = BloomIndex.peek(level);
        if (idx == null) {
            return null;
        }
        BlockPos at = bee.blockPosition();
        long avoid = bee.getCapability(BeePollenCapability.CAPABILITY).map(p -> p.hasPollen() ? p.source() : Long.MIN_VALUE)
                .orElse(Long.MIN_VALUE);
        long[] found = new long[MAX_TARGETS];
        int[] n = {0};
        idx.forEachNear(at, RANGE + 1, BeeCropPollinateGoal::beesPollinate, (sp, pos) -> {
            if (pos != avoid && BlockPos.of(pos).distToCenterSqr(bee.position()) <= RANGE_SQ) {
                found[n[0]++] = pos;
            }
            return n[0] < MAX_TARGETS;
        });
        if (n[0] == 0) {
            return null;
        }
        BlockPos pick = BlockPos.of(found[bee.getRandom().nextInt(n[0])]);
        return flowering(level, pick).isPresent() ? pick : null;
    }

    /** Whether species number {@code sp} has {@code bees} in its JSON. */
    static boolean beesPollinate(int sp) {
        return SpeciesIndex.name(sp).flatMap(Definitions.current()::species)
                .map(e -> e.def().pollination().bees().isPresent()).orElse(false);
    }

    /** The flowering plant at {@code pos}, read only if its chunk is loaded. */
    static Optional<PlantBlockEntity> flowering(ServerLevel level, BlockPos pos) {
        Optional<BlockState> st = LoadedLevelAccess.blockState(level, pos);
        if (st.isEmpty() || !(st.get().getBlock() instanceof FieldPlantBlock)
                || FieldPlantBlock.stage(st.get()) != FieldPlantBlock.FLOWER_STAGE) {
            return Optional.empty();
        }
        return LoadedLevelAccess.blockEntity(level, pos, PlantBlockEntity.class);
    }

    @Override
    public boolean canContinueToUse() {
        return target != null && !bee.hasNectar() && !bee.isAngry() && bee.level() instanceof ServerLevel level
                && !level.isRaining() && ticks < GIVE_UP_TICKS && flowering(level, target).isPresent();
    }

    @Override
    public void start() {
        ticks = 0;
        hoverTicks = 0;
    }

    @Override
    public void stop() {
        target = null;
        bee.getNavigation().stop();
        gate.delay(bee.level().getGameTime(), BeeQueryLimiter.BEE_INTERVAL + bee.getRandom().nextInt(40));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    @EntryPoint
    public void tick() {
        ticks++;
        BlockPos t = target;
        if (t == null) {
            return; // the visit is done; canContinueToUse() ends the goal on the next check
        }
        Vec3 v = Vec3.atBottomCenterOf(t).add(0, 0.6, 0);
        double d = bee.position().distanceTo(v);
        if (d > 2.0) {
            if (ticks % 20 == 1 || bee.getNavigation().isDone()) {
                bee.getNavigation().moveTo(v.x, v.y, v.z, 1.0);
            }
            return;
        }
        bee.getNavigation().stop();
        bee.getMoveControl().setWantedPosition(v.x, v.y, v.z, 0.35);
        bee.getLookControl().setLookAt(v.x, v.y - 0.6, v.z);
        if (d < 1.0 && ++hoverTicks >= HOVER_TICKS) {
            target = null;
            if (bee.level() instanceof ServerLevel level) {
                Guarded.run("bee visit", t, () -> PollinationService.beeVisit(level, bee, t));
            }
        }
    }
}
