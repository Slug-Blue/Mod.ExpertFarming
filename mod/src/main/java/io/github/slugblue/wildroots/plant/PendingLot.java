package io.github.slugblue.wildroots.plant;

import io.github.slugblue.wildroots.genetics.pollination.Brush;
import io.github.slugblue.wildroots.item.LotFathersNbt;
import java.util.List;

/**
 * Lot waiting in the plant from seed set to harvest (docs/05-architecture.md
 * §6.1, §10.2): at most 4 fathers and 4 donors copied by value, {@code p_out},
 * brush and bee flags. Seed set fills it (WR-141); harvest turns it into the
 * seed lot (WR-134).
 */
public record PendingLot(List<LotFathersNbt.Father> fathers, float pOut, Brush brush, boolean bees) {

    public PendingLot {
        fathers = List.copyOf(fathers);
        brush = brush == null ? Brush.NONE : brush;
    }
}
