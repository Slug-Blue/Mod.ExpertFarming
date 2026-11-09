package io.github.slugblue.wildroots.item;

import io.github.slugblue.wildroots.core.registry.ModItems;
import io.github.slugblue.wildroots.core.rng.LotIds;
import io.github.slugblue.wildroots.core.rng.RngKeys;
import io.github.slugblue.wildroots.core.safety.DispCodec;
import io.github.slugblue.wildroots.core.safety.NbtLimits;
import io.github.slugblue.wildroots.core.safety.WorldSecret;
import io.github.slugblue.wildroots.data.Definitions;
import io.github.slugblue.wildroots.data.DefinitionsSnapshot;
import io.github.slugblue.wildroots.genetics.api.FatherPick;
import io.github.slugblue.wildroots.genetics.api.GeneticsService;
import io.github.slugblue.wildroots.genetics.api.LotRecord;
import io.github.slugblue.wildroots.genetics.api.PatchPassport;
import io.github.slugblue.wildroots.genetics.api.SpeciesDef;
import io.github.slugblue.wildroots.genetics.codec.DecodeResult;
import io.github.slugblue.wildroots.genetics.codec.GenomeRecord;
import io.github.slugblue.wildroots.genetics.model.Genome;
import io.github.slugblue.wildroots.genetics.random.Hash64;
import io.github.slugblue.wildroots.plant.PlantBlockEntity;
import io.github.slugblue.wildroots.plant.PlantState;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

/**
 * The only factory of seed lots (docs/05-architecture.md §6.1, §6.3;
 * ADR-0007; SEC-13, SEC-21). Every lot item, its {@code lotId}, its
 * {@code disp} block and its {@code rare_source} come from here:
 *
 * <ul>
 *   <li>{@code lotId} is assigned when the item is created, lazy lots
 *   included ({@code tpl.id}); realising a lazy lot keeps it (SEC-13);</li>
 *   <li>{@code rare_source} is set only here: {@code mutation} once per
 *   mutant plant, guarded by {@link RareClaim} ({@code rareClaimed} in the
 *   block entity), {@code chest} and {@code trade} only on lazy lots of
 *   loot and trades; seeds of a plant never inherit it (economy §6.1);</li>
 *   <li>line name, generation and the "from the wild" mark follow the
 *   mother (GDD §5.3).</li>
 * </ul>
 */
public final class LotStacks {

    /** Gives the {@code mutation} flag at most once per plant (SEC-21). */
    public interface RareClaim {
        /** {@code true} exactly once for a mutant plant, then never again. */
        boolean claimRare();

        RareClaim NONE = () -> false;
    }

    /** What a lot takes from its mother plant. */
    public record Parent(String species, Optional<String> cultivar, GenomeRecord genome, Optional<String> lineName,
                         int generation, int flags) {
    }

    private LotStacks() {
    }

    // ------------------------------------------------------------------ tags (pure, JUnit)

    /**
     * Seeds of a mother plant: line name and "from the wild" by the mother,
     * generation + 1, cultivar id of the mother, pollination flags of this
     * cycle, {@code mutation} only if {@code claim} grants it.
     */
    public static CompoundTag seedsTag(Parent mother, List<LotFathersNbt.Father> fathers, float pOut,
                                       int pollinationFlags, long lotId, RareClaim claim) {
        int flags = (mother.flags() & LotFlags.INHERITED) | (pollinationFlags & (LotFlags.BRUSH | LotFlags.BEES));
        LotNbt.RareSource rare = claim.claimRare() ? LotNbt.RareSource.MUTATION : LotNbt.RareSource.NONE;
        LotNbt.Lot lot = new LotNbt.Lot(lotId, mother.species(), mother.cultivar(), mother.genome(), fathers,
                clampPOut(pOut), Math.min(NbtLimits.LOT_MAX_GENERATION, mother.generation() + 1), mother.lineName(),
                flags, rare, Optional.empty());
        return LotNbt.write(lot, disp(lot));
    }

    /**
     * A young plant broken before ripeness: one seed that plants exactly the
     * already realised genome and noise again (SEC-26, default of STATUS.md).
     */
    public static CompoundTag seedlingTag(Parent plant, byte[] noise, long lotId) {
        LotNbt.Lot lot = new LotNbt.Lot(lotId, plant.species(), plant.cultivar(), plant.genome(), List.of(), 0,
                plant.generation(), plant.lineName(), plant.flags() & LotFlags.INHERITED, LotNbt.RareSource.NONE,
                Optional.of(noise.clone()));
        return LotNbt.write(lot, disp(lot));
    }

    /** A lazy lot of the wild patch category {@code category}; {@code rare} only {@code chest} or {@code trade}. */
    public static CompoundTag lazyTag(String species, String category, long salt, long lotId,
                                      LotNbt.RareSource rare) {
        if (rare == LotNbt.RareSource.MUTATION) {
            throw new IllegalArgumentException("a lazy lot is never a mutant");
        }
        LotNbt.Lazy lazy = new LotNbt.Lazy(species, category, salt, lotId, rare);
        return LotNbt.write(lazy, disp(lazy));
    }

    /** The realised form of {@code lazy}: same {@code lotId} and rare source, "from the wild". */
    public static CompoundTag realisedTag(LotNbt.Lazy lazy, GenomeRecord mother, List<LotFathersNbt.Father> fathers,
                                          float pOut) {
        LotNbt.Lot lot = new LotNbt.Lot(lazy.lotId(), lazy.species(), Optional.empty(), mother, fathers,
                clampPOut(pOut), 0, Optional.empty(), LotFlags.FROM_WILD, lazy.rare(), Optional.empty());
        return LotNbt.write(lot, disp(lot));
    }

    /**
     * Hand lens (WR-183): the same lot with {@code inspected} and a
     * {@code disp} of the base lines plus {@code tierOne}, written into
     * {@code stack}. If the lot would pass its 1 KiB, tier-1 lines are
     * dropped from the end until it fits. {@code false} if not even the
     * base lines fit (nothing is written).
     */
    public static boolean inspect(ItemStack stack, LotNbt.Lot lot, List<DispCodec.Line> tierOne) {
        LotNbt.Lot marked = new LotNbt.Lot(lot.lotId(), lot.species(), lot.cultivar(), lot.mother(), lot.fathers(),
                lot.pOut(), lot.generation(), lot.lineName(), lot.flags() | LotFlags.INSPECTED, lot.rare(),
                lot.seedlingNoise());
        List<DispCodec.Line> base = disp(marked);
        for (int keep = Math.min(tierOne.size(), DispCodec.MAX_LINES - base.size()); keep >= 0; keep--) {
            List<DispCodec.Line> d = new ArrayList<>(base);
            d.addAll(tierOne.subList(0, keep));
            CompoundTag root = LotNbt.write(marked, d);
            if (NbtLimits.lot(root).ok()) {
                stack.getOrCreateTag().put(LotNbt.KEY, root);
                return true;
            }
        }
        return false;
    }

    private static float clampPOut(float p) {
        return Float.isFinite(p) ? Math.max(0, Math.min(1, p)) : 0;
    }

    /** Translation key of a species or cultivar name (narrative §2). */
    static String nameKey(String kind, String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) {
            return "wildroots.tooltip.lot.unknown";
        }
        return "wildroots." + kind + "." + (rl.getNamespace().equals("wildroots") ? rl.getPath()
                : rl.getNamespace() + "." + rl.getPath());
    }

    /** At most 6 lines for an unexamined lot (§6.1); the lens and the analyser add the tiers. */
    static List<DispCodec.Line> disp(LotNbt.Lot lot) {
        List<DispCodec.Line> d = new ArrayList<>(6);
        d.add(DispCodec.Line.of(lot.cultivar().map(c -> nameKey("cultivar", c))
                .orElse(nameKey("species", lot.species()))));
        if (lot.seedling()) {
            d.add(DispCodec.Line.of("wildroots.tooltip.lot.seedling"));
        }
        if (lot.generation() > 0) {
            d.add(DispCodec.Line.of("wildroots.tooltip.lot.generation", Integer.toString(lot.generation())));
        }
        lot.lineName().ifPresent(n -> d.add(DispCodec.Line.of("wildroots.tooltip.lot.line_name", n)));
        if ((lot.flags() & LotFlags.FROM_WILD) != 0) {
            d.add(DispCodec.Line.of("wildroots.tooltip.lot.from_wild"));
        }
        if (!lot.seedling()) {
            d.add(DispCodec.Line.of((lot.flags() & LotFlags.BEES) != 0 ? "wildroots.tooltip.lot.pollination.bees"
                    : (lot.flags() & LotFlags.BRUSH) != 0 ? "wildroots.tooltip.lot.pollination.brush"
                    : lot.fathers().isEmpty() ? "wildroots.tooltip.lot.pollination.selfed"
                    : "wildroots.tooltip.lot.pollination.open"));
        }
        return d;
    }

    static List<DispCodec.Line> disp(LotNbt.Lazy lazy) {
        return List.of(DispCodec.Line.of(nameKey("species", lazy.species())),
                DispCodec.Line.of("wildroots.tooltip.lot.from_wild"));
    }

    // ------------------------------------------------------------------ stacks (server)

    /** A seed lot stack holding {@code root}. */
    public static ItemStack stack(CompoundTag root, int count) {
        ItemStack s = new ItemStack(ModItems.SEED_LOT.get(), Math.max(1, Math.min(64, count)));
        s.getOrCreateTag().put(LotNbt.KEY, root);
        return s;
    }

    /** A fresh {@code lotId} (SEC-13). */
    public static long newLotId(ServerLevel level) {
        return LotIds.next(WorldSecret.get(level.getServer()), level.getGameTime());
    }

    /** The mother facts of a live plant. */
    public static Parent parent(PlantBlockEntity.Live live) {
        PlantState d = live.data();
        return new Parent(d.species(), Optional.ofNullable(d.cultivar()), live.genetics().encode(live.genome()),
                Optional.ofNullable(d.lineName()), d.generation(), d.lotFlags());
    }

    /** Seeds of a live plant (harvest, WR-134). */
    public static ItemStack seeds(ServerLevel level, PlantBlockEntity.Live live, List<LotFathersNbt.Father> fathers,
                                  float pOut, int pollinationFlags, RareClaim claim, int count) {
        return stack(seedsTag(parent(live), fathers, pOut, pollinationFlags, newLotId(level), claim), count);
    }

    /** One realised seedling of a young plant (SEC-26). */
    public static ItemStack seedling(ServerLevel level, PlantBlockEntity.Live live) {
        return stack(seedlingTag(parent(live), live.noise().bytes(), newLotId(level)), 1);
    }

    /** A lazy lot; {@code lotId} is assigned now (SEC-13). */
    public static ItemStack lazy(ServerLevel level, String species, String category, int count,
                                 LotNbt.RareSource rare) {
        long id = newLotId(level);
        return stack(lazyTag(species, category, Hash64.of(id, Hash64.str("salt")), id, rare), count);
    }

    /** Decoded lot of {@code stack}. */
    public static LotNbt.Result read(ItemStack stack) {
        return stack.is(ModItems.SEED_LOT.get()) ? LotNbt.read(LotNbt.root(stack.getTag()))
                : new LotNbt.Inert("not a lot");
    }

    /**
     * Realises a lazy lot in place: genome from {@code Hash64(secret, lotId)},
     * the same for every half of a split stack (SEC-13). The mother is a
     * wild plant of the template's patch, the fathers two more plants of it
     * (GDD §5.3). Returns {@code false} if there is nothing to do or the
     * species is not loaded (the lot stays lazy).
     */
    public static boolean realise(ItemStack stack, ServerLevel level) {
        if (!(read(stack) instanceof LotNbt.Lazy lazy)) {
            return false;
        }
        DefinitionsSnapshot snap = Definitions.current();
        Optional<DefinitionsSnapshot.SpeciesEntry> e = snap.species(lazy.species());
        if (e.isEmpty()) {
            return false;
        }
        SpeciesDef def = e.get().def();
        GeneticsService gs = snap.genetics();
        long k = RngKeys.lazyLot(WorldSecret.get(level.getServer()), lazy.lotId());
        PatchPassport p = new PatchPassport(lazy.species(), lazy.category(), false, lazy.salt(), false,
                level.getSeed());
        Optional<Genome> mother = gs.wildPlant(def, p, Hash64.of(k, 0));
        if (mother.isEmpty()) {
            return false;
        }
        List<LotFathersNbt.Father> fathers = new ArrayList<>(2);
        for (int i = 1; i <= 2; i++) {
            gs.wildPlant(def, p, Hash64.of(k, i))
                    .ifPresent(f -> fathers.add(new LotFathersNbt.Father(gs.encode(f).toFather(), 1f, false)));
        }
        CompoundTag root = realisedTag(lazy, gs.encode(mother.get()), fathers, (float) def.pollination().outcross());
        stack.getOrCreateTag().put(LotNbt.KEY, root);
        return true;
    }

    /**
     * The genetic part of a lot for planting: mother and fathers decoded in
     * the current layout (migrated if the layout changed, §5.3). Empty if the
     * species is unknown or a genome cannot be used (the lot is inert).
     */
    public static Optional<LotRecord> record(LotNbt.Lot lot, DefinitionsSnapshot snap) {
        Optional<DefinitionsSnapshot.SpeciesEntry> e = snap.species(lot.species());
        if (e.isEmpty()) {
            return Optional.empty();
        }
        GeneticsService gs = snap.genetics();
        DecodeResult m = gs.decode(lot.mother(), e.get().def());
        if (!(m instanceof DecodeResult.Valid mv)) {
            return Optional.empty();
        }
        List<FatherPick> fathers = new ArrayList<>(lot.fathers().size());
        for (LotFathersNbt.Father f : lot.fathers()) {
            DecodeResult r = gs.decode(f.genome().withLot(lot.mother().v(), lot.species()), e.get().def());
            if (!(r instanceof DecodeResult.Valid fv)) {
                return Optional.empty();
            }
            fathers.add(new FatherPick(fv.genome(), f.weight(), f.donor()));
        }
        return Optional.of(new LotRecord(mv.genome(), fathers, lot.pOut()));
    }
}
