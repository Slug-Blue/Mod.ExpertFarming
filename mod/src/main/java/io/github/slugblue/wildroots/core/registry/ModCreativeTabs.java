package io.github.slugblue.wildroots.core.registry;

import io.github.slugblue.wildroots.WildRoots;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * The {@code wildroots} creative tab (WR-120; docs/05-architecture.md §3).
 *
 * <p>The tab lists every item of {@link ModItems} that
 * {@link CreativeTabContents#isVisible(Item)} lets through: items of a
 * disabled line stay registered but are hidden (§9.1, WR-124).
 */
public final class ModCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> REGISTER =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, WildRoots.MOD_ID);

    /** Translation key of the tab title. */
    public static final String TITLE_KEY = "itemGroup.wildroots";

    public static final RegistryObject<CreativeModeTab> WILDROOTS = REGISTER.register(WildRoots.MOD_ID,
            () -> CreativeModeTab.builder()
                    .title(Component.translatable(TITLE_KEY))
                    .icon(() -> new ItemStack(ModItems.SEED_LOT.get()))
                    .displayItems((params, output) -> CreativeTabContents.visibleItems().forEach(output::accept))
                    .build());

    private ModCreativeTabs() {
    }
}
