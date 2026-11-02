package io.github.slugblue.wildroots.core.registry;

import io.github.slugblue.wildroots.core.line.LineContent;
import io.github.slugblue.wildroots.core.line.LineRegistry;
import java.util.List;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.RegistryObject;

/** What the {@code wildroots} creative tab shows (WR-120, WR-124; docs/05-architecture.md §9.1). */
public final class CreativeTabContents {

    private CreativeTabContents() {
    }

    /** Items of {@link ModItems} in registration order that the tab shows. */
    public static List<Item> visibleItems() {
        return ModItems.REGISTER.getEntries().stream()
                .filter(RegistryObject::isPresent)
                .map(RegistryObject::get)
                .filter(CreativeTabContents::isVisible)
                .toList();
    }

    /** Whether the tab shows {@code item}: items of a disabled line are hidden (§9.1). */
    public static boolean isVisible(Item item) {
        return LineContent.visible(LineContent.lineOf(item), LineRegistry.get());
    }
}
