package io.github.slugblue.wildroots.item;

import net.minecraft.nbt.CompoundTag;

/** A valid mother root for JUnit tests of other packages. */
public final class MotherNbtTestRoots {

    private MotherNbtTestRoots() {
    }

    public static CompoundTag full() {
        return ItemRootBoundaryTest.fullMother();
    }
}
