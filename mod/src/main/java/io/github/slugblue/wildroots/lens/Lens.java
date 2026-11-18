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

    /**
     * The tier-1 reading of a plant for the lens window (playtest 2, item 3).
     *
     * @param header  name · stage
     * @param lines   the same reading as lines (header first; tests and the old chat form)
     * @param row     the compact row the window draws
     * @param env     active environment modifiers ({@link EnvModifiers})
     * @param species species id
     * @param model   the plant's block state id when it stands on farmland, else {@link LensWindowPacket#NO_MODEL}
     * @param colour  the colour lines ({@link LensColour}): only from stage 3, when the root shows its colour
     */
    public record PlantReading(DispCodec.Line header, List<DispCodec.Line> lines, LensRow row, int env,
                               String species, int model, List<DispCodec.Line> colour, LensClimate climate) {

        /** The window, or empty if it cannot be sent (the caller falls back to chat). */
        public Optional<LensWindowPacket> packet() {
            return LensWindowPacket.of(LensWindowPacket.Kind.PLANT, species, header, row.line(), env, model, colour, climate);
        }
    }

    /** The lines of a plant inspection, or empty if there is nothing to look at. */
    public static Optional<List<DispCodec.Line>> inspectPlant(ServerLevel level, BlockPos pos, ServerPlayer player) {
        return readPlant(level, pos, player).map(PlantReading::lines);
    }

    /** The reading of a plant, or empty if there is nothing to look at; discovers the species for {@code player}. */
    public static Optional<PlantReading> readPlant(ServerLevel level, BlockPos pos, ServerPlayer player) {
        Optional<BlockState> state = LoadedLevelAccess.blockState(level, pos);
        if (state.isEmpty() || !(state.get().getBlock() instanceof FieldPlantBlock)) {
            return Optional.empty();
        }
        Optional<PlantBlockEntity.Live> live = LoadedLevelAccess.blockEntity(level, pos, PlantBlockEntity.class)
                .flatMap(be -> be.live(level));
        if (live.isEmpty()) {
            return Optional.empty();
        }
        Environment env = GrowthLogic.environment(level, pos, live.get().def());
        Optional<Phenotype> p = GrowthLogic.phenotype(live.get(), env);
        if (p.isEmpty()) {
            return Optional.empty();
        }
        PlantState d = live.get().data();
        SpeciesDef def = live.get().def();
        String species = def.id();
        String name = d.cultivar() != null ? JournalTriggers.nameKey("cultivar", d.cultivar())
                : JournalTriggers.nameKey("species", species);
        DispCodec.Line header = DispCodec.Line.of(HEADER, DispCodec.keyArg(name), DispCodec.keyArg(LensReading.stageKey(
                FieldPlantBlock.stage(state.get()), live.get().species().info(), LensReading.path(species))));
        List<DispCodec.Line> out = new ArrayList<>();
        out.add(header);
        LensReading.Pollination how = plantPollination(d.pending());
        Optional<CultivarDef> looks = tierOne(out, live.get().snapshot(), def, live.get().species().info(), p.get(),
                how, Optional.ofNullable(d.cultivar()), d.tier());
        LensRow row = LensRow.of(def, live.get().species().info(), p.get(), how, looks);
        // the colour is seen on the root's shoulder from stage 3 (00-vision item 27): the lens says nothing before
        List<DispCodec.Line> colour = FieldPlantBlock.stage(state.get()) >= FieldPlantBlock.HARVEST_STAGE
                ? LensColour.lines(def, p.get()) : List.of();
        out.addAll(colour);
        LensClimate climate = LoadedLevelAccess.blockEntity(level, pos, PlantBlockEntity.class)
                .flatMap(be -> io.github.slugblue.wildroots.plant.Wilting.read(level, pos, state.get(), be, live.get()))
                .map(r -> {
                    if (player != null) {
                        io.github.slugblue.wildroots.journal.HintTriggers.climate(level, d, r, false);
                    }
                    return LensClimate.of(r.stress(), r.margin(), r.hot(), r.exemption(), r.minutes(),
                            io.github.slugblue.wildroots.plant.Wilting.isWilted(state.get()), r.sheltered(), r.irrigated());
                }).orElse(LensClimate.NONE);
        out.addAll(climate.lines());
        boolean farmland = LoadedLevelAccess.blockState(level, pos.below())
                .map(b -> b.getBlock() instanceof net.minecraft.world.level.block.FarmBlock).orElse(false);
        int model = farmland ? net.minecraft.world.level.block.Block.getId(state.get()) : LensWindowPacket.NO_MODEL;
        if (player != null) {
            JournalTriggers.discoverSpecies(player, species, Optional.empty());
            looks.ifPresent(c -> Journals.unlock(player, "hint.looks_like",
                    DispCodec.keyArg(JournalTriggers.nameKey("cultivar", c.id().value()))));
        }
        return Optional.of(new PlantReading(header, List.copyOf(out), row, EnvModifiers.of(def, env, p.get()),
                species, model, colour, climate));
    }

    /** Header key of the lot window: "%s · seeds". */
    public static final String LOT_HEADER = "wildroots.window.lens.lot";
    /** Header key of the window of a realised seedling lot: "%s · seedling" (review 1, m4). */
    public static final String SEEDLING_HEADER = "wildroots.window.lens.seedling";

    /**
     * The lens window of an inspected lot (item 3): its name and the row the
     * inspection wrote; empty if the stack is no inspected lot.
     */
    public static Optional<LensWindowPacket> lotWindow(ItemStack stack) {
        if (!(LotStacks.read(stack) instanceof LotNbt.Lot lot)) {
            return Optional.empty();
        }
        Optional<LensRow> row = LensPanel.row(LotNbt.disp(LotNbt.root(stack.getTag())));
        if (row.isEmpty()) {
            return Optional.empty();
        }
        String name = lot.cultivar().map(c -> JournalTriggers.nameKey("cultivar", c))
                .orElse(JournalTriggers.nameKey("species", lot.species()));
        return LensWindowPacket.of(LensWindowPacket.Kind.LOT, lot.species(),
                DispCodec.Line.of(lot.seedling() ? SEEDLING_HEADER : LOT_HEADER, DispCodec.keyArg(name)),
                row.get().line(), 0, LensWindowPacket.NO_MODEL);
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
        if (pending.brush() == Brush.CROSS) return LensReading.Pollination.HAND;
        if (pending.brush() == Brush.SELF) return LensReading.Pollination.SELF;
        if (pending.butterflies() && !pending.bees()) {
            return LensReading.Pollination.BUTTERFLIES;
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
        if ((lot.flags() & LotFlags.BRUSH) != 0) return LensReading.Pollination.HAND;
        if ((lot.flags() & LotFlags.BUTTERFLIES) != 0 && (lot.flags() & LotFlags.BEES) == 0) {
            return LensReading.Pollination.BUTTERFLIES;
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
     * Inspects a mother root in place (D2): its tier-1 row, read like a
     * seedling lot from its own genome and noise in an ideal cell of the
     * species' climate, goes into its {@code disp}. A root is not
     * pollinated, so the row has no pollination. {@code false} if the stack
     * is no usable mother root or the row does not fit.
     */
    public static boolean inspectMother(ItemStack stack, ServerLevel level, ServerPlayer player) {
        Optional<io.github.slugblue.wildroots.item.MotherNbt.Mother> m = io.github.slugblue.wildroots.item.MotherItem
                .read(stack);
        if (m.isEmpty()) {
            return false;
        }
        DefinitionsSnapshot snap = Definitions.current();
        Optional<DefinitionsSnapshot.SpeciesEntry> e = snap.species(m.get().species());
        if (e.isEmpty()) {
            return false;
        }
        SpeciesDef def = e.get().def();
        if (!(snap.genetics().decode(m.get().genome(), def)
                instanceof io.github.slugblue.wildroots.genetics.codec.DecodeResult.Valid valid)) {
            return false;
        }
        Optional<io.github.slugblue.wildroots.genetics.model.Genome> g = Optional.of(valid.genome());
        NoiseVector noise = Optional.of(new NoiseVector(m.get().noise()))
                .filter(n -> n.bytes().length == def.quantitative().size())
                .orElse(NoiseVector.zero(def.quantitative().size()));
        Environment env = Environment.neutral((def.tempMin() + def.tempMax()) / 2);
        Optional<Phenotype> p = snap.genetics().phenotype(g.get(), def, env, noise);
        if (p.isEmpty()) {
            return false;
        }
        Optional<CultivarDef> looks = LensReading.looksLike(snap, def, p.get(), m.get().cultivar(), m.get().tier());
        // a planted root keeps its own genome: its band is the planting noise only (item 6)
        LensRow row = LensRow.of(def, e.get().info(), p.get(), LensReading.Pollination.NONE, looks,
                OffspringBand.noiseOnly(def));
        if (!io.github.slugblue.wildroots.item.MotherNbt.inspect(stack,
                row.degradations().stream().map(LensRow::line).toList())) {
            return false;
        }
        if (player != null) {
            JournalTriggers.discoverSpecies(player, def.id(), Optional.empty());
            looks.ifPresent(c -> Journals.unlock(player, "hint.looks_like",
                    DispCodec.keyArg(JournalTriggers.nameKey("cultivar", c.id().value()))));
        }
        return true;
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
        int tier = lot.cultivar().flatMap(snap::cultivar).map(c -> c.def().tier()).orElse(0);
        Optional<CultivarDef> looks = LensReading.looksLike(snap, def, p.get(), lot.cultivar(), tier);
        // the seeds spread around the mother's value: the band of item 6 (segregation and noise)
        // a realised seedling keeps its own genome and noise: it does not spread
        double[] sd = lot.seedling() ? null : OffspringBand.ofLot(rec.get(), def, p.get().vigor());
        LensRow row = LensRow.of(def, e.get().info(), p.get(), lotPollination(lot), looks, sd);
        if (!LotStacks.inspect(stack, lot, row.degradations().stream().map(LensRow::line).toList())) {
            return false;
        }
        if (player != null) {
            looks.ifPresent(c -> Journals.unlock(player, "hint.looks_like",
                    DispCodec.keyArg(JournalTriggers.nameKey("cultivar", c.id().value()))));
        }
        return true;
    }
}
