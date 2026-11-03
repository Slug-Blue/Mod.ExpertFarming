package io.github.slugblue.wildroots.item;

import java.util.List;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

/** Tooltip data of the seed pouch: the lots inside (last put in first) and the places used. */
public record PouchTooltip(List<ItemStack> items, int used) implements TooltipComponent {

    public PouchTooltip {
        items = List.copyOf(items);
    }
}
