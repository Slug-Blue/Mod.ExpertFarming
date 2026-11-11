package io.github.slugblue.wildroots.item;

import io.github.slugblue.wildroots.core.safety.DispCodec;
import java.util.List;
import net.minecraft.nbt.CompoundTag;

/** The package-private lot writers for JUnit tests of other packages (WR-197 fuzz round-trip). */
public final class LotNbtTestAccess {

    private LotNbtTestAccess() {
    }

    public static CompoundTag write(LotNbt.Lot lot, List<DispCodec.Line> disp) {
        return LotNbt.write(lot, disp);
    }

    public static CompoundTag write(LotNbt.Lazy lazy, List<DispCodec.Line> disp) {
        return LotNbt.write(lazy, disp);
    }
}
