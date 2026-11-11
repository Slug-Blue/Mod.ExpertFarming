package io.github.slugblue.wildroots.core.safety;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import io.github.slugblue.wildroots.data.CarrotLineDataTestAccess;
import io.github.slugblue.wildroots.data.DefinitionsSnapshot;
import io.github.slugblue.wildroots.genetics.codec.DecodeResult;
import io.github.slugblue.wildroots.genetics.codec.FatherRecord;
import io.github.slugblue.wildroots.genetics.codec.GenomeCodec;
import io.github.slugblue.wildroots.genetics.codec.GenomeRecord;
import io.github.slugblue.wildroots.genetics.model.GenomeLayout;
import io.github.slugblue.wildroots.item.LotNbt;
import io.github.slugblue.wildroots.item.LotNbtTestAccess;
import io.github.slugblue.wildroots.item.LotStacks;
import io.github.slugblue.wildroots.item.MotherNbt;
import io.github.slugblue.wildroots.item.PouchContents;
import io.github.slugblue.wildroots.journal.JournalData;
import io.github.slugblue.wildroots.plant.PlantNbt;
import io.github.slugblue.wildroots.pollination.BeePollen;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import org.junit.jupiter.api.Test;

/**
 * WR-197, SEC-09/SEC-10 (property): mutated NBT for every stored root the mod
 * decodes — genome, plant block entity, lot, pouch, journal, mother root and
 * bee pollen. Seeds are the frozen v1 fixtures ({@code fixtures/v1},
 * {@code fixtures/journal}); mutations come from {@link NbtMutator} with a
 * fixed seed per decoder. For every input the decoder returns without an
 * exception, and the result is either valid (then saving and reading it
 * again gives a valid result) or inert.
 *
 * <p>{@value #RUNS} inputs per decoder; {@code -Pwildroots.fuzzRuns=n}
 * changes it (the property is the same at any count).
 */
class NbtDecoderFuzzTest {

    static final int RUNS = 100_000;
    static final int runs = Integer.getInteger("wildroots.fuzzRuns", RUNS);

    static final DefinitionsSnapshot SNAP = CarrotLineDataTestAccess.snapshot();
    static final String SP = "wildroots:wild_carrot";
    static final GenomeLayout LAYOUT = SNAP.genetics().layout(CarrotLineDataTestAccess.wildCarrot()).orElseThrow();

    static CompoundTag fixture(String path) {
        try (InputStream in = NbtDecoderFuzzTest.class.getResourceAsStream("/fixtures/" + path)) {
            if (in == null) {
                throw new IOException("missing fixture " + path);
            }
            return TagParser.parseTag(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    enum Outcome { VALID, INERT }

    /** Counts of one decoder's run. */
    record Tally(int valid, int inert, long millis) {
    }

    /**
     * Runs {@code decoder} on {@link #runs} mutations of the seeds (round
     * robin); any exception fails with the input that caused it.
     */
    static Tally fuzz(String name, long seed, List<CompoundTag> seeds, Function<CompoundTag, Outcome> decoder) {
        NbtMutator m = new NbtMutator(seed);
        int valid = 0;
        int inert = 0;
        long t0 = System.nanoTime();
        for (CompoundTag s : seeds) {
            assertEquals(Outcome.VALID, decoder.apply(s.copy()), name + ": unmutated seed is not valid: " + s);
        }
        for (int i = 0; i < runs; i++) {
            CompoundTag in = m.mutate(seeds.get(i % seeds.size()));
            String snapshot = null;
            try {
                Outcome o = decoder.apply(in);
                if (o == Outcome.VALID) {
                    valid++;
                } else if (o == Outcome.INERT) {
                    inert++;
                } else {
                    fail(name + " #" + i + ": no outcome");
                }
            } catch (Throwable e) {
                snapshot = in.toString();
                fail(name + " #" + i + " (seed " + seed + ") threw " + e + " on "
                        + snapshot.substring(0, Math.min(2000, snapshot.length())), e);
            }
        }
        long ms = (System.nanoTime() - t0) / 1_000_000;
        System.out.printf("[WR-197] fuzz %s: %d inputs, %d valid, %d inert, %d ms%n", name, runs, valid, inert, ms);
        assertTrue(inert > 0, name + ": no mutation made the root inert, the mutator does not reach the decoder");
        assertTrue(valid >= runs / 100, name + ": only " + valid + " valid results, the mutator destroys every input");
        return new Tally(valid, inert, ms);
    }

    static Outcome genome(GenomeRecord r) {
        DecodeResult d = GenomeCodec.decode(r, SP, LAYOUT);
        if (d instanceof DecodeResult.Valid v) {
            // a valid genome re-encodes to the same bits
            assertTrue(java.util.Arrays.equals(GenomeCodec.encode(v.genome()).a(), r.a()) || v.migrated(),
                    "valid genome does not re-encode");
            return Outcome.VALID;
        }
        return Outcome.INERT;
    }

    /** The genome compound as every decoder reads it, then {@code GenomeCodec.decode} with the shipped layout. */
    @Test
    void genome() {
        CompoundTag g = fixture("v1/plant_v1.snbt").getCompound("g");
        fuzz("genome", 1971, List.of(g), t -> {
            // the raw getters never throw; GenomeCodec must judge whatever they return
            GenomeRecord r = new GenomeRecord(t.getInt("v"), t.getString("sp"), t.getLong("lh"), t.getLongArray("a"));
            Outcome o = genome(r);
            // and through the father path of a lot
            DecodeResult f = GenomeCodec.decodeFather(new FatherRecord(t.getLong("lh"), t.getLongArray("a")), t.getInt("v"),
                    SP, LAYOUT);
            assertTrue(f != null);
            return o;
        });
    }

    @Test
    void plantBlockEntity() {
        fuzz("plant BE", 1972, List.of(fixture("v1/plant_v1.snbt"), fixture("v1/plant_wild_v1.snbt")), t -> {
            PlantNbt.Result r = PlantNbt.read(t);
            if (r instanceof PlantNbt.Ok ok) {
                CompoundTag saved = PlantNbt.write(ok.state());
                assertTrue(PlantNbt.read(saved) instanceof PlantNbt.Ok again
                        && PlantNbt.write(again.state()).equals(saved), "saved plant does not round-trip");
                if (ok.state().genome() != null) {
                    genome(ok.state().genome());
                }
                return Outcome.VALID;
            }
            return Outcome.INERT;
        });
    }

    @Test
    void lot() {
        fuzz("lot", 1973, List.of(fixture("v1/lot_v1.snbt")), t -> {
            LotNbt.Result r = LotNbt.read(t);
            LotNbt.disp(t);
            if (r instanceof LotNbt.Lot l) {
                LotStacks.record(l, SNAP); // genomes of mother and fathers: decoded or empty, never thrown
                CompoundTag saved = LotNbtTestAccess.write(l, LotNbt.disp(t));
                assertTrue(LotNbt.read(saved) instanceof LotNbt.Lot again
                        && LotNbtTestAccess.write(again, LotNbt.disp(saved)).equals(saved), "saved lot does not round-trip");
                return Outcome.VALID;
            }
            if (r instanceof LotNbt.Lazy lazy) {
                CompoundTag saved = LotNbtTestAccess.write(lazy, LotNbt.disp(t));
                assertTrue(LotNbt.read(saved) instanceof LotNbt.Lazy again
                        && LotNbtTestAccess.write(again, LotNbt.disp(saved)).equals(saved), "saved lazy lot does not round-trip");
                return Outcome.VALID;
            }
            return Outcome.INERT;
        });
    }

    @Test
    void pouch() {
        fuzz("pouch", 1974, List.of(fixture("v1/pouch_v1.snbt")), t -> {
            Optional<PouchContents> p = PouchContents.read(t);
            if (p.isEmpty()) {
                return Outcome.INERT;
            }
            PouchContents c = p.get();
            CompoundTag saved = c.write();
            assertEquals(saved, PouchContents.read(saved).orElseThrow().write(), "saved pouch does not round-trip");
            while (c.used() > 0) {
                PouchContents.Extracted e = c.removeLast().orElseThrow();
                LotNbt.read(e.lot());
                c = e.pouch();
            }
            return Outcome.VALID;
        });
    }

    @Test
    void journal() {
        fuzz("journal", 1975, List.of(fixture("journal/journal_v1.snbt")), t -> {
            JournalData j = JournalData.load(t);
            if (j.inert()) {
                return Outcome.INERT;
            }
            CompoundTag saved = j.save();
            JournalData again = JournalData.load(saved);
            assertTrue(!again.inert() && again.save().equals(saved), "saved journal does not round-trip");
            return Outcome.VALID;
        });
    }

    @Test
    void motherRoot() {
        fuzz("mother root", 1976, List.of(fixture("v1/mother_v1.snbt")), t -> {
            Optional<MotherNbt.Mother> m = MotherNbt.read(t);
            if (m.isEmpty()) {
                return Outcome.INERT;
            }
            CompoundTag saved = MotherNbt.write(m.get());
            assertEquals(saved, MotherNbt.write(MotherNbt.read(saved).orElseThrow()), "saved mother root does not round-trip");
            genome(m.get().genome());
            return Outcome.VALID;
        });
    }

    @Test
    void beePollen() {
        BeePollen good = new BeePollen();
        CompoundTag g = fixture("v1/plant_v1.snbt").getCompound("g");
        good.collect(SP, 42L, new FatherRecord(g.getLong("lh"), g.getLongArray("a")));
        good.setCounters(1, 77);
        fuzz("bee pollen", 1977, List.of(good.save()), t -> {
            BeePollen p = new BeePollen();
            p.load(t);
            if (!p.hasPollen()) {
                return Outcome.INERT;
            }
            BeePollen again = new BeePollen();
            again.load(p.save());
            assertTrue(again.hasPollen() && again.save().equals(p.save()), "saved pollen does not round-trip");
            return Outcome.VALID;
        });
    }
}
