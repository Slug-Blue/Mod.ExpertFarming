package io.github.slugblue.wildroots.item;

import io.github.slugblue.wildroots.core.safety.EntryPoint;
import io.github.slugblue.wildroots.core.safety.Guarded;
import io.github.slugblue.wildroots.data.CultivarInfo;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Harvest item of one step and cultivar ({@code wild_carrot_root},
 * {@code eastern_carrot}, {@code wildroots:carrot}; docs/05-architecture.md
 * §6.3, §12.3; GDD §6.4). The cultivar is the item id, the stack NBT is only
 * grade and bitterness ({@link ProduceStacks}). Food comes from the
 * cultivar's {@code food_reference} in the server definitions; without one
 * (wild step, client without the data) the reference given at registration
 * is used. The base food of the properties only makes the item edible.
 */
public class ProduceItem extends Item {

    private final CultivarInfo.FoodReference fallback;
    private final boolean toxic;

    public ProduceItem(Properties properties, CultivarInfo.FoodReference fallback, boolean toxic) {
        super(properties.food(new FoodProperties.Builder().nutrition(fallback.nutrition())
                .saturationMod((float) fallback.saturation()).build()));
        this.fallback = fallback;
        this.toxic = toxic;
    }

    public CultivarInfo.FoodReference reference() {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(this);
        return id == null ? fallback : GradedFood.referenceOf(id).orElse(fallback);
    }

    @Override
    @EntryPoint
    public FoodProperties getFoodProperties(ItemStack stack, LivingEntity entity) {
        return Guarded.call("produce food", "produce", () -> GradedFood.of(reference(),
                ProduceStacks.grade(stack.getTag()), ProduceStacks.bitter(stack.getTag()), toxic),
                super.getFoodProperties(stack, entity));
    }

    @Override
    @EntryPoint
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        Guarded.run("produce tooltip", "produce", () -> {
            tooltip.add(Component.translatable("wildroots.grade." + ProduceStacks.grade(stack.getTag())));
            if (ProduceStacks.bitter(stack.getTag())) {
                tooltip.add(Component.translatable("wildroots.grade.bitter"));
            }
        });
    }
}
