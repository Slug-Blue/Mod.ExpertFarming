package io.github.slugblue.wildroots.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import org.junit.jupiter.api.Test;

/** WR-137, SEC-09: the mother-root decoder (regression of the batch 3 test pass). */
class MotherNbtTest {

    /** A non-string {@code ln} was read as the line name {@code ""} and then planted into plant and lots. */
    @Test
    void wrongTypedLineNameIsRefusedNotReadAsEmpty() {
        CompoundTag root = ItemRootBoundaryTest.fullMother();
        assertEquals(Optional.of("Golden Line"), MotherNbt.read(root).orElseThrow().lineName());
        root.put("ln", IntTag.valueOf(7));
        assertTrue(MotherNbt.read(root).isEmpty(), "wrong-typed ln accepted");
        root.remove("ln");
        assertTrue(MotherNbt.read(root).orElseThrow().lineName().isEmpty(), "absent ln = no line name");
    }
}
