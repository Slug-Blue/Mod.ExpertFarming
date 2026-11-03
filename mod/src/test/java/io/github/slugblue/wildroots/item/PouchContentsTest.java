package io.github.slugblue.wildroots.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.slugblue.wildroots.core.safety.NbtLimits;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.SplittableRandom;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

/**
 * WR-138, SEC-17: the pure pouch model. Every operation is atomic (a new
 * state or nothing); for any sequence of operations the seeds of every
 * {@code lotId} are conserved between the player and the pouch.
 */
class PouchContentsTest {

    static CompoundTag lot(long id) {
        CompoundTag t = LotNbtTest.typical();
        t.putLong("id", id);
        return t;
    }

    @Test
    void lifoNoMergingAndCapacity() {
        PouchContents p = PouchContents.EMPTY;
        PouchContents.Inserted a = p.insert(lot(1), 3).orElseThrow();
        assertEquals(3, a.moved());
        PouchContents.Inserted b = a.pouch().insert(lot(1), 2).orElseThrow();
        assertEquals(5, b.pouch().used(), "identical lots are separate entries");
        PouchContents.Inserted c = b.pouch().insert(lot(2), 40).orElseThrow();
        assertEquals(11, c.moved(), "only the free places are filled, the rest stays with the player");
        assertEquals(16, c.pouch().used());
        assertTrue(c.pouch().insert(lot(3), 1).isEmpty(), "a full pouch takes nothing");
        assertEquals(2L, c.pouch().removeLast().orElseThrow().lot().getLong("id"), "last in, first out");
        assertEquals(5, b.pouch().used(), "operations never change the old state");
    }

    @Test
    void roundTripKeepsOrderAndUnknownKeys() {
        PouchContents p = PouchContents.EMPTY.insert(lot(1), 1).orElseThrow().pouch()
                .insert(lot(2), 1).orElseThrow().pouch();
        CompoundTag stored = p.write();
        stored.putString("extra", "kept");
        PouchContents back = PouchContents.read(stored).orElseThrow();
        assertEquals(stored, back.write());
        assertEquals(2L, back.removeLast().orElseThrow().lot().getLong("id"));
    }

    /** Review minor 1: a pouch from a future version is inert and never rewritten as v1. */
    @Test
    void futureOrDamagedVersionIsInert() {
        CompoundTag stored = PouchContents.EMPTY.insert(lot(1), 1).orElseThrow().pouch().write();
        stored.putInt("v", 2);
        assertTrue(PouchContents.read(stored).isEmpty(), "v2 pouch read as v1");
        stored.putInt("v", 0);
        assertTrue(PouchContents.read(stored).isEmpty(), "v0 pouch read");
        stored.putString("v", "1");
        assertTrue(PouchContents.read(stored).isEmpty(), "string version read");
    }

    @Test
    void sixteenLotsAtTheLimitAlwaysFit() {
        PouchContents p = PouchContents.EMPTY;
        for (int i = 0; i < 16; i++) {
            CompoundTag big = lot(i);
            int pad = 0;
            while (NbtLimits.lot(big).ok()) {
                big.putString("pad", "x".repeat(++pad));
            }
            big.putString("pad", "x".repeat(pad - 1));
            assertTrue(NbtLimits.lot(big).bytes() >= NbtLimits.LOT_MAX_BYTES - 2, () -> "lot " + NbtLimits.lot(big));
            p = p.insert(big, 1).orElseThrow(() -> new AssertionError("a valid lot did not fit")).pouch();
        }
        assertEquals(16, p.used());
        assertTrue(NbtLimits.pouch(p.write()).ok());
    }

    @Test
    void insertionOverTheProtectiveLimitIsRefusedAndThePouchStaysUsable() {
        CompoundTag stored = PouchContents.EMPTY.insert(lot(1), 1).orElseThrow().pouch().write();
        stored.putString("junk", "j".repeat(NbtLimits.POUCH_MAX_BYTES - 700));
        PouchContents p = PouchContents.read(stored).orElseThrow();
        assertTrue(p.insert(lot(2), 1).isEmpty(), "insertion over 17 KiB accepted");
        assertEquals(1L, p.removeLast().orElseThrow().lot().getLong("id"), "pouch became unusable");
    }

    @Test
    void randomSequencesConserveSeedsPerLot() {
        SplittableRandom r = new SplittableRandom(138);
        for (int run = 0; run < 200; run++) {
            Map<Long, Integer> outside = new HashMap<>();
            for (long id = 1; id <= 4; id++) {
                outside.put(id, 1 + r.nextInt(20));
            }
            Map<Long, Integer> total = new HashMap<>(outside);
            PouchContents p = PouchContents.EMPTY;
            for (int op = 0; op < 60; op++) {
                if (r.nextBoolean()) {
                    long id = 1 + r.nextInt(4);
                    int have = outside.get(id);
                    Optional<PouchContents.Inserted> in = p.insert(lot(id), have);
                    if (in.isPresent()) {
                        assertTrue(in.get().moved() <= have);
                        outside.put(id, have - in.get().moved());
                        p = in.get().pouch();
                    }
                } else {
                    Optional<PouchContents.Extracted> ex = p.removeLast();
                    if (ex.isPresent()) {
                        long id = ex.get().lot().getLong("id");
                        outside.merge(id, 1, Integer::sum);
                        p = ex.get().pouch();
                    }
                }
                assertTrue(p.used() <= PouchContents.CAPACITY);
                Map<Long, Integer> inside = new HashMap<>();
                p.entries().forEach(e -> inside.merge(e.getLong("id"), 1, Integer::sum));
                for (long id = 1; id <= 4; id++) {
                    assertEquals(total.get(id), outside.get(id) + inside.getOrDefault(id, 0), "seeds of lot " + id);
                }
            }
        }
    }
}
