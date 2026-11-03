package io.github.slugblue.wildroots.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.slugblue.wildroots.core.safety.LogThrottle;
import io.github.slugblue.wildroots.core.safety.NbtBudget;
import io.github.slugblue.wildroots.core.safety.NbtLimits;
import io.github.slugblue.wildroots.core.safety.WorldSecret;
import io.github.slugblue.wildroots.data.DefinitionsSnapshot;
import io.github.slugblue.wildroots.genetics.codec.LayoutDescriptor;
import io.github.slugblue.wildroots.genetics.codec.LayoutDictionary;
import io.github.slugblue.wildroots.network.ServerPacketGuard;
import io.github.slugblue.wildroots.network.c2s.NameSelectionPacket;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.Test;

/** Review 1 of batch 2: fixes outside the network and data test classes. */
class ReviewFixesTest {

    @Test
    void registerAcceptsOnlyC2sPacketTypes() {
        ServerPacketGuard.requireC2sType(NameSelectionPacket.class);
        assertThrows(IllegalArgumentException.class, () -> ServerPacketGuard.requireC2sType(String.class));
        assertThrows(IllegalArgumentException.class, () -> ServerPacketGuard.requireC2sType(ReviewFixesTest.class));
        // register checks the type before it touches the channel
        assertThrows(IllegalArgumentException.class, () -> ServerPacketGuard.register(null, 0, String.class,
                (m, b) -> { }, b -> "", "", (m, p) -> { }));
    }

    @Test
    void throttleAllowsOneLinePerKeyAndWindow() {
        AtomicLong now = new AtomicLong(1000);
        LogThrottle t = new LogThrottle(now::get, 100);
        assertTrue(t.allow("a"));
        assertFalse(t.allow("a"));
        assertTrue(t.allow("b"));
        now.addAndGet(100);
        assertTrue(t.allow("a"));
    }

    @Test
    void budgetStopsBeforeQueueingChildrenOfAHugeCompound() {
        CompoundTag root = new CompoundTag();
        for (int i = 0; i < 50_000; i++) {
            root.putByte("key_number_" + i, (byte) 1);
        }
        NbtBudget.Result r = NbtBudget.measure(root, NbtLimits.LOT_MAX_BYTES, 10);
        assertFalse(r.ok());
        assertTrue(r.bytes() <= NbtLimits.LOT_MAX_BYTES + 64, () -> "stopped at " + r.bytes());
        ListTag longList = new ListTag();
        root = new CompoundTag();
        root.put("l", longList);
        assertTrue(NbtBudget.measure(root, 20, 10).ok());
    }

    @Test
    void pouchEntriesMustBeCompounds() {
        CompoundTag pouch = new CompoundTag();
        ListTag e = new ListTag();
        e.add(StringTag.valueOf("not a lot"));
        pouch.put("e", e);
        assertFalse(NbtLimits.pouch(pouch).ok());
        pouch.putString("e", "not a list");
        assertFalse(NbtLimits.pouch(pouch).ok());
        pouch.put("e", new ListTag());
        assertTrue(NbtLimits.pouch(pouch).ok());
    }

    @Test
    void snapshotLayoutsCannotBeChangedFromOutside() {
        DefinitionsSnapshot s = new DefinitionsSnapshot(Map.of(), Map.of(), DefinitionsSnapshot.Stats.NONE);
        LayoutDictionary copy = s.layouts();
        copy.register(new LayoutDescriptor("x:y", 1L, "q:a:1"));
        assertTrue(s.layouts().entries().isEmpty(), "the snapshot dictionary changed");
    }

    @Test
    void damagedSecretFileGivesAFreshSecret() {
        WorldSecret a = WorldSecret.load(new CompoundTag());
        WorldSecret b = WorldSecret.load(new CompoundTag());
        long va = a.save(new CompoundTag()).getLong("s");
        long vb = b.save(new CompoundTag()).getLong("s");
        assertNotEquals(va, vb, "SecureRandom gave the same secret twice");
        assertTrue(a.isDirty(), "a recreated secret is saved on the next autosave");
        CompoundTag stored = new CompoundTag();
        stored.putLong("s", 42L);
        assertEquals(42L, WorldSecret.load(stored).save(new CompoundTag()).getLong("s"));
    }
}
