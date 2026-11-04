package io.github.slugblue.wildroots.pollination;

import io.github.slugblue.wildroots.core.safety.EntryPoint;
import io.github.slugblue.wildroots.core.safety.Guarded;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Bee;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingEvent;

/**
 * Forge hooks of bee pollination (docs/05-architecture.md §10.3; ADR-0009;
 * S2): the pollen capability, our goal on every entry of a bee into the
 * level (also a bee leaving a hive, which is a new entity) without
 * duplicates, and the pollen age, which grows only while the bee is a
 * living entity, that is outside the hive.
 */
public final class BeeEvents {

    private BeeEvents() {
    }

    /** Mod bus. */
    public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        event.register(BeePollen.class);
    }

    /** Forge bus: every bee carries pollen. */
    @EntryPoint
    public static void onAttach(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Bee) {
            Guarded.run("bee pollen attach", "bee", () -> event.addCapability(BeePollen.ID, new BeePollen.Provider()));
        }
    }

    /** Forge bus: our goal, once per bee entity (S2: {@code Mob#goalSelector} is public). */
    @EntryPoint
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Bee bee)) {
            return;
        }
        Guarded.run("bee goal", "bee", () -> addGoal(bee));
    }

    /** Adds our goal unless the bee has it already; returns whether it was added. */
    public static boolean addGoal(Bee bee) {
        boolean present = bee.goalSelector.getAvailableGoals().stream()
                .anyMatch(w -> w.getGoal() instanceof BeeCropPollinateGoal);
        if (!present) {
            bee.goalSelector.addGoal(BeeCropPollinateGoal.PRIORITY, new BeeCropPollinateGoal(bee));
        }
        return !present;
    }

    /**
     * Forge bus: pollen ages one tick per tick of the living bee; nectar
     * that did not come from our goal is a visit to a vanilla flower, which
     * counts towards the ≤ 2 visits of the pollen (GDD §4.3).
     */
    @EntryPoint
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Bee bee) || bee.level().isClientSide()) {
            return;
        }
        Guarded.run("bee pollen tick", "bee", () -> bee.getCapability(BeePollenCapability.CAPABILITY).ifPresent(p -> {
            p.tickOutside();
            boolean now = bee.hasNectar();
            if (now && !p.hadNectar) {
                if (!p.nectarFromUs) {
                    p.countVisit();
                }
                p.nectarFromUs = false;
            }
            p.hadNectar = now;
        }));
    }
}
