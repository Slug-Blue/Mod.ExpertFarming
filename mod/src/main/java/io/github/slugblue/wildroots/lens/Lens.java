package io.github.slugblue.wildroots.lens;

import io.github.slugblue.wildroots.core.safety.DispCodec;
import io.github.slugblue.wildroots.core.safety.LoadedLevelAccess;
import io.github.slugblue.wildroots.data.Definitions;
import io.github.slugblue.wildroots.data.DefinitionsSnapshot;
import io.github.slugblue.wildroots.genetics.api.CultivarDef;
import io.github.slugblue.wildroots.genetics.api.Environment;
import io.github.slugblue.wildroots.genetics.api.LotRecord;
import io.github.slugblue.wildroots.genetics.api.Phenotype;
import io.github.slugblue.wildroots.genetics.api.SpeciesDef;
import io.github.slugblue.wildroots.genetics.phenotype.NoiseVector;
import io.github.slugblue.wildroots.genetics.pollination.Brush;
import io.github.slugblue.wildroots.item.LotFlags;
import io.github.slugblue.wildroots.item.LotNbt;
import io.github.slugblue.wildroots.item.LotStacks;
import io.github.slugblue.wildroots.journal.Journals;
import io.github.slugblue.wildroots.journal.JournalTriggers;
import io.github.slugblue.wildroots.plant.FieldPlantBlock;
import io.github.slugblue.wildroots.plant.GrowthLogic;
import io.github.slugblue.wildroots.plant.PendingLot;
import io.github.slugblue.wildroots.plant.PlantBlockEntity;
import io.github.slugblue.wildroots.plant.PlantState;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Server side of the hand lens (WR-183; GDD §10 tier 1; narrative §9.2–9.4,
 * §9.6): inspecting a plant gives a header (name · stage) and the tier-1
 * lines; inspecting a lot marks it {@code inspected} and rewrites its
 * {@code disp} with the same lines, so the tooltip shows tier 1 for good.
 * A first inspection of a species discovers it (narrative §4.3), and a
 * "looks like" line unlocks the {@code looks_like} hint.
 */
public final class Lens {

    public static final String HEADER = "wildroots.message.lens.header";

    private Lens() {
    }

    /** The lines of a plant inspection, or empty if there is nothing to look at. */
    public static Optional<List<DispCodec.Line>> inspectPlant(ServerLevel level, BlockPos pos, ServerPlayer player) {
        Optional<BlockState> state = LoadedLevelAccess.blockState(level, pos);
        if (state.isEmpty() || !(state.get().getBlock() instanceof FieldPlantBlock)) {
            return Optional.empty();
        }
        Optional<PlantBlockEntity.Live> live = LoadedLevelAccess.blockEntity(level, pos, PlantBlockEntity.class)
                .flatMap(be -> be.live(level));
        if (live.isEmpty()) {
            return Optional.empty();
        }
        Optional<Phenotype> p = GrowthLogic.phenotype(live.get(), GrowthLogic.environment(level, pos));
        if (p.isEmpty()) {
            return Optional.empty();
        }
        PlantState d = live.get().data();
        SpeciesDef def = live.get().def();
        String species = def.id();
        String name = d.cultivar() != null ? JournalTriggers.nameKey("cultivar", d.cultivar())
                : JournalTriggers.nameKey("species", species);
        List<DispCodec.Line> out = new ArrayList<>();
        out.add(DispCodec.Line.of(HEADER, DispCodec.keyArg(name), DispCodec.keyArg(LensReading.stageKey(
                FieldPlantBlock.stage(state.get()), live.get().species().info(), LensReading.path(species)))));
        Optional<CultivarDef> looks = tierOne(out, live.get().snapshot(), def, live.get().species().info(), p.get(),
                plantPollination(d.pending()), Optional.ofNullable(d.cultivar()), d.tier());
        if (player != null) {
            JournalTriggers.discoverSpecies(player, species, Optional.empty());
            looks.ifPresent(c -> Journals.unlock(player, "hint.looks_like",
                    DispCodec.keyArg(JournalTriggers.nameKey("cultivar", c.id().value()))));
        }
        return Optional.of(out);
    }

    /** Appends the tier-1 lines; returns the cultivar the plant looks like, if any. */
    static Optional<CultivarDef> tierOne(List<DispCodec.Line> out, DefinitionsSnapshot snap, SpeciesDef def,
                                         io.github.slugblue.wildroots.data.SpeciesInfo info, Phenotype p,
                                         LensReading.Pollination how, Optional<String> status, int tier) {
        out.addAll(LensReading.traits(def, info, p));
        LensReading.bitterness(def, p).ifPresent(out::add);
        LensReading.pollination(how).ifPresent(out::add);
        LensReading.ailing(p).ifPresent(out::add);
        Optional<CultivarDef> looks = LensReading.looksLike(snap, def, p, status, tier);
        looks.ifPresent(c -> out.add(LensReading.looksLikeLine(c)));
        return looks;
    }

    static LensReading.Pollination plantPollination(PendingLot pending) {
        if (pending == null) {
            return LensReading.Pollination.NONE;
        }
        if (pending.bees()) {
            return LensReading.Pollination.BEES;
        }
        if (pending.brush() == Brush.CROSS) {
            return LensReading.Pollination.HAND;
        }
        return pending.fathers().isEmpty() || pending.brush() != Brush.NONE ? LensReading.Pollination.SELF
                : LensReading.Pollination.NEIGHBOURS;
    }

    static LensReading.Pollination lotPollination(LotNbt.Lot lot) {
        if (lot.seedling()) {
            return LensReading.Pollination.NONE;
        }
        if ((lot.flags() & LotFlags.BEES) != 0) {
            return LensReading.Pollination.BEES;
        }
        if ((lot.flags() & LotFlags.BRUSH) != 0) {
            return LensReading.Pollination.HAND;
        }
        return lot.fathers().isEmpty() ? LensReading.Pollination.SELF : LensReading.Pollination.NEIGHBOURS;
    }

    /**
     * Inspects a lot in place: a lazy lot is realised first; the lot gets
     * {@code inspected} and its tier-1 lines in {@code disp}. The mother's
     * {@code P} is read in an ideal cell of the species' climate without
     * planting noise (a lot keeps no noise of its mother; a seedling lot
     * uses its own). {@code false} if the stack is no usable lot.
     */
    public static boolean inspectLot(ItemStack stack, ServerLevel level, ServerPlayer player) {
        LotStacks.realise(stack, level);
        if (!(LotStacks.read(stack) instanceof LotNbt.Lot lot)) {
            return false;
        }
        DefinitionsSnapshot snap = Definitions.current();
        Optional<DefinitionsSnapshot.SpeciesEntry> e = snap.species(lot.species());
        Optional<LotRecord> rec = LotStacks.record(lot, snap);
        if (e.isEmpty() || rec.isEmpty()) {
            return false;
        }
        SpeciesDef def = e.get().def();
        NoiseVector noise = lot.seedlingNoise().map(NoiseVector::new)
                .filter(n -> n.bytes().length == def.quantitative().size())
                .orElse(NoiseVector.zero(def.quantitative().size()));
        Environment env = Environment.neutral((def.tempMin() + def.tempMax()) / 2);
        Optional<Phenotype> p = snap.genetics().phenotype(rec.get().mother(), def, env, noise);
        if (p.isEmpty()) {
            return false;
        }
        List<DispCodec.Line> lines = new ArrayList<>();
        int tier = lot.cultivar().flatMap(snap::cultivar).map(c -> c.def().tier()).orElse(0);
        Optional<CultivarDef> looks = tierOne(lines, snap, def, e.get().info(), p.get(), lotPollination(lot),
                lot.cultivar(), tier);
        if (!LotStacks.inspect(stack, lot, lines)) {
            return false;
        }
        if (player != null) {
            looks.ifPresent(c -> Journals.unlock(player, "hint.looks_like",
                    DispCodec.keyArg(JournalTriggers.nameKey("cultivar", c.id().value()))));
        }
        return true;
    }
}
