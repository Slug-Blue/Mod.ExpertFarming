package io.github.slugblue.wildroots.journal;

import io.github.slugblue.wildroots.core.safety.EntryPoint;
import io.github.slugblue.wildroots.core.safety.Guarded;
import io.github.slugblue.wildroots.core.safety.LoadedLevelAccess;
import io.github.slugblue.wildroots.data.DefinitionsSnapshot;
import io.github.slugblue.wildroots.genetics.api.CultivarDef;
import io.github.slugblue.wildroots.genetics.api.Phenotype;
import io.github.slugblue.wildroots.genetics.model.TraitId;
import io.github.slugblue.wildroots.genetics.pollination.Brush;
import io.github.slugblue.wildroots.item.LotFlags;
import io.github.slugblue.wildroots.item.ProduceStacks;
import io.github.slugblue.wildroots.item.ProduceItem;
import io.github.slugblue.wildroots.plant.FieldPlantBlock;
import io.github.slugblue.wildroots.plant.GrowthLogic;
import io.github.slugblue.wildroots.plant.Passport;
import io.github.slugblue.wildroots.plant.PendingLot;
import io.github.slugblue.wildroots.plant.PlantBlockEntity;
import io.github.slugblue.wildroots.plant.PlantState;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.BonemealEvent;

/**
 * Triggers of the wave-1 hints of narrative §4.5 (WR-182): carrot, bees,
 * inbreeding, cultivars and the wave-1 tools. Each hint fires once per
 * player ({@link JournalData#unlock} is idempotent), {@code stagnation}
 * once per line with the three-generation repeat ({@link LineProgress}).
 * Hints of lines and features not in this build are refused by
 * {@link JournalHints}. "Own plant" = planted by this player (UUID in the
 * block entity, GDD §6.2, §9.5).
 */
public final class HintTriggers {

    /** Radius of "near" for lone_plant and small_population (narrative §4.5). */
    public static final int RADIUS = 8;
    /** small_population: at most this many own plants of the species in the radius (GDD §12.2). */
    public static final int SMALL_POPULATION = 9;
    /** select_quarter: the 16th own plant of one species harvested in one game day. */
    public static final int SELECT_QUARTER = 16;
    public static final long DAY = 24000;

    private HintTriggers() {
    }

    static boolean own(PlantState d, ServerPlayer player) {
        return d.hasPlanter() && d.planter().equals(player.getUUID());
    }

    static boolean biennial(PlantBlockEntity.Live live) {
        return "biennial".equals(live.species().info().lifeCycle());
    }

    static void hint(ServerPlayer p, String id, String... args) {
        Journals.unlock(p, "hint." + id, args);
    }

    // ------------------------------------------------------------------ harvest

    /** A mature plant harvested by {@code player} (called from {@link JournalTriggers#harvested}). */
    static void harvested(ServerLevel level, BlockPos pos, BlockState state, PlantBlockEntity be, ServerPlayer player,
                          JournalData j) {
        Optional<PlantBlockEntity.Live> live = be.live(level);
        if (live.isEmpty()) {
            return;
        }
        PlantState d = live.get().data();
        int stage = FieldPlantBlock.stage(state);
        if (stage == FieldPlantBlock.HARVEST_STAGE) {
            if (biennial(live.get())) {
                hint(player, "mother_plant");
            }
            switch (state.getValue(FieldPlantBlock.VARIANT)) {
                case 1 -> hint(player, "yellow_root");
                case 2 -> hint(player, "orange_root");
                case 3 -> hint(player, "purple_root");
                default -> { }
            }
        } else if (stage >= FieldPlantBlock.SEED_STAGE) {
            if (biennial(live.get()) && !d.has(PlantState.MOTHER)) {
                hint(player, "left_on_stalk");
            }
            PendingLot pending = d.pending();
            if (!live.get().def().pollination().bees().isEmpty() && pending != null && !pending.bees()
                    && pending.brush() == Brush.NONE) {
                hint(player, "no_pollinators");
            }
        }
        if (d.passport().origin() == Passport.Origin.WILD) {
            secondBiome(player, j, d.species(), d.passport().category());
        }
        if (own(d, player)) {
            selectQuarter(player, j, d.species(), level.getGameTime());
            if (d.lineName() != null && !d.lineName().isEmpty()) {
                GrowthLogic.phenotype(live.get(), GrowthLogic.environment(level, pos)).ifPresent(p -> {
                    double score = targetScore(live.get(), p);
                    if (LineProgress.observe(j, LineProgress.lineKey(d.lineName()), d.generation(), score)) {
                        Journals.unlockForLine(player, "stagnation", d.lineName(), d.generation());
                    }
                });
            }
        }
    }

    /** {@code second_biome}: wild patches of the species in two categories of biome (GDD §3.2). */
    static void secondBiome(ServerPlayer player, JournalData j, String species, String category) {
        if (category.isEmpty() || j.has("hint.second_biome")) {
            return;
        }
        String sp = JournalTriggers.speciesEntry(species).substring("species.".length());
        String k1 = "cat." + sp + ".1";
        String k2 = "cat." + sp + ".2";
        long h = io.github.slugblue.wildroots.genetics.random.Hash64.str(category) | 1L;
        if (j.counter(k1) == 0) {
            j.setCounter(k1, h);
        } else if (j.counter(k1) != h) {
            j.setCounter(k2, h);
            hint(player, "second_biome");
        }
    }

    /** {@code select_quarter}: the 16th own plant of a species in one game day. */
    static void selectQuarter(ServerPlayer player, JournalData j, String species, long gameTime) {
        if (j.has("hint.select_quarter")) {
            return;
        }
        String sp = JournalTriggers.speciesEntry(species).substring("species.".length());
        long day = gameTime / DAY + 1;
        String dk = "sq." + sp + ".day";
        String nk = "sq." + sp + ".n";
        if (j.counter(dk) != day) {
            j.setCounter(dk, day);
            j.setCounter(nk, 0);
        }
        long n = j.counter(nk) + 1;
        j.setCounter(nk, n);
        if (n >= SELECT_QUARTER) {
            hint(player, "select_quarter");
            j.clearCounters("sq." + sp + ".");
        }
    }

    /**
     * Score of a plant for {@code stagnation}: the mean {@code G_eff} over the
     * quantitative traits of the next cultivar of the species above the
     * plant's tier (GDD §14 "target profile"); all traits if there is none.
     */
    static double targetScore(PlantBlockEntity.Live live, Phenotype p) {
        DefinitionsSnapshot snap = live.snapshot();
        int tier = live.data().tier();
        Optional<CultivarDef> target = snap.profiles(live.data().species()).stream()
                .filter(c -> c.tier() > tier)
                .min(java.util.Comparator.comparingInt(CultivarDef::tier).thenComparing(c -> c.id().value()));
        List<TraitId> traits = target.map(c -> (List<TraitId>) new ArrayList<>(c.quantitative().keySet()))
                .filter(l -> !l.isEmpty()).orElse(p.traits());
        double sum = 0;
        int n = 0;
        for (TraitId t : traits) {
            double v = p.traits().contains(t) ? p.gEff(t) : Double.NaN;
            if (Double.isFinite(v)) {
                sum += v;
                n++;
            }
        }
        return n == 0 ? 0 : sum / n;
    }

    // ------------------------------------------------------------------ planting

    /**
     * A lot or a mother root planted by {@code player} at {@code pos}
     * (from {@code SeedLotItem} and {@code MotherItem}): {@code plant_together},
     * {@code lone_plant}, {@code mutation}, and {@code CultivarBred} for the
     * planter (GDD §6.2 item 4).
     */
    public static void planted(ServerLevel level, BlockPos pos, ServerPlayer player) {
        if (player instanceof FakePlayer) {
            return;
        }
        Optional<PlantBlockEntity> be = LoadedLevelAccess.blockEntity(level, pos, PlantBlockEntity.class);
        Optional<PlantBlockEntity.Live> live = be.flatMap(b -> b.live(level));
        Optional<JournalData> j = Journals.of(player);
        if (live.isEmpty() || j.isEmpty()) {
            return;
        }
        PlantState d = live.get().data();
        hint(player, "plant_together");
        if (!j.get().has("hint.lone_plant") && live.get().def().pollination().outcross() >= 0.3
                && ownNear(level, pos, d.species(), player.getUUID(), 1) == 0) {
            hint(player, "lone_plant");
        }
        if (d.has(PlantState.MUTANT)) {
            hint(player, "mutation");
        }
        if (d.cultivar() != null && d.tier() >= 1) {
            CultivarBred.bred(level, player, live.get());
        }
    }

    /**
     * Own plants of {@code species} within {@link #RADIUS} of {@code pos}
     * (not counting {@code pos}), counted up to {@code stopAt}. Reads only
     * loaded cells; ±2 blocks in height.
     */
    static int ownNear(ServerLevel level, BlockPos pos, String species, UUID planter, int stopAt) {
        int n = 0;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -RADIUS; dx <= RADIUS; dx++) {
                for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    m.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    Optional<BlockState> s = LoadedLevelAccess.blockState(level, m);
                    if (s.isEmpty() || !(s.get().getBlock() instanceof FieldPlantBlock f) || !f.species().equals(species)) {
                        continue;
                    }
                    Optional<PlantBlockEntity> be = LoadedLevelAccess.blockEntity(level, m, PlantBlockEntity.class);
                    if (be.isPresent() && be.get().data() != null && be.get().data().hasPlanter()
                            && be.get().data().planter().equals(planter) && ++n >= stopAt) {
                        return n;
                    }
                }
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ growth, bees

    /**
     * An own plant reached harvest ripeness (from {@code GrowthLogic.advance}):
     * {@code inbreeding} below the ailing threshold, {@code small_population}
     * with at most nine own plants of the species near it. Only for an
     * online planter; the radius scan runs only until the hint is unlocked.
     */
    public static void matured(ServerLevel level, BlockPos pos, PlantBlockEntity.Live live, Phenotype p) {
        PlantState d = live.data();
        if (!d.hasPlanter()) {
            return;
        }
        Optional<ServerPlayer> player = Journals.online(level.getServer(), d.planter());
        Optional<JournalData> j = player.flatMap(Journals::of);
        if (j.isEmpty()) {
            return;
        }
        if (p.vigor() < GrowthLogic.AILING_BELOW) {
            hint(player.get(), "inbreeding");
        }
        if (GrowthLogic.waitsForHarvest(live, FieldPlantBlock.HARVEST_STAGE)) {
            // D1: the cue that the root is ripe and waits to be dug up
            hint(player.get(), "root_ripe");
        }
        if (!j.get().has("hint.small_population")
                && ownNear(level, pos, d.species(), d.planter(), SMALL_POPULATION + 1) + 1 <= SMALL_POPULATION) {
            hint(player.get(), "small_population");
        }
    }

    /** A bee visited a flowering plant: {@code bees} for its online planter. */
    public static void beeVisited(ServerLevel level, PlantBlockEntity be) {
        PlantState d = be.data();
        if (d != null && d.hasPlanter()) {
            Journals.online(level.getServer(), d.planter()).ifPresent(p -> hint(p, "bees"));
        }
    }

    // ------------------------------------------------------------------ player events

    /** Forge bus: {@code bitter} after eating bitter produce of the mod. */
    @EntryPoint
    public static void onFinishEating(LivingEntityUseItemEvent.Finish event) {
        if (event.getEntity() instanceof ServerPlayer p && !(p instanceof FakePlayer)
                && event.getItem().getItem() instanceof ProduceItem) {
            ItemStack eaten = event.getItem();
            Guarded.run("journal eat", p.getUUID(), () -> {
                if (ProduceStacks.bitter(eaten.getTag())) {
                    hint(p, "bitter");
                }
            });
        }
    }

    /** Forge bus: {@code bone_meal} the first time a player uses bone meal on a plant of the mod. */
    @EntryPoint
    public static void onBonemeal(BonemealEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && !(p instanceof FakePlayer)
                && event.getBlock().getBlock() instanceof FieldPlantBlock) {
            Guarded.run("journal bone meal", p.getUUID(), () -> hint(p, "bone_meal"));
        }
    }

    /** A wave-1 tool first in the inventory: its tool page, and {@code seed_pouch} for the pouch. */
    public static void toolInInventory(ServerPlayer p, String tool) {
        Optional<JournalData> j = Journals.of(p);
        if (j.isEmpty() || j.get().has("tool." + tool)) {
            return;
        }
        Journals.unlock(p, "tool." + tool);
        if (tool.equals("seed_pouch")) {
            hint(p, "seed_pouch");
        }
    }

    /** Lot flag "from the wild" (for the cultivar mark). */
    static boolean fromWild(PlantState d) {
        return (d.lotFlags() & LotFlags.FROM_WILD) != 0;
    }
}
