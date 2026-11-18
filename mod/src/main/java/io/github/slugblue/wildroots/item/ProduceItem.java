package io.github.slugblue.wildroots.item;

import io.github.slugblue.wildroots.core.safety.EntryPoint;
import io.github.slugblue.wildroots.core.safety.Guarded;
import io.github.slugblue.wildroots.data.CultivarInfo;
import io.github.slugblue.wildroots.lens.LensColour;
import java.util.List;
import net.minecraft.ChatFormatting;
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

    /** "Root colour: %s", the colour of the stack in its tooltip and lens panel (WR-706). */
    public static final String ROOT_COLOUR_KEY = "wildroots.tooltip.root_colour";

    private final CultivarInfo.FoodReference fallback;
    private final boolean toxic;
    private final boolean rootColours;
    private final int defaultColour;

    public ProduceItem(Properties properties, CultivarInfo.FoodReference fallback, boolean toxic) {
        this(properties, fallback, toxic, false);
    }

    /** {@code rootColours}: the stacks carry a yellow / purple / red root colour (WR-136a, eastern carrot). */
    public ProduceItem(Properties properties, CultivarInfo.FoodReference fallback, boolean toxic, boolean rootColours) {
        this(properties, fallback, toxic, rootColours, ProduceStacks.YELLOW);
    }

    /** {@code defaultColour}: the colour a stack has without the colour key (yellow eastern carrot, orange carrot). */
    public ProduceItem(Properties properties, CultivarInfo.FoodReference fallback, boolean toxic, boolean rootColours,
                       int defaultColour) {
        super(properties.food(new FoodProperties.Builder().nutrition(fallback.nutrition())
                .saturationMod((float) fallback.saturation()).build()));
        this.fallback = fallback;
        this.toxic = toxic;
        this.rootColours = rootColours;
        this.defaultColour = defaultColour;
    }

    /** Colour of a stack without the colour key (WR-706). */
    public int defaultColour() {
        return defaultColour;
    }

    /** Root colour of a stack of this item. */
    public int colourOf(ItemStack stack) {
        return ProduceStacks.colour(stack.getTag(), defaultColour);
    }

    public boolean rootColours() {
        return rootColours;
    }

    /**
     * The saturation multiplier of the stack's root colour (the purple root's anthocyanins, WR-703); 1 without
     * colours. The colour name is one of the fixed ids yellow / orange / purple / red and must equal the colour ids of the
     * species JSON. Server and single player apply it; a remote client has no colour data and uses 1 (accepted:
     * the server is authoritative, only client-side predictions differ).
     */
    double colourSaturation(ItemStack stack) {
        ResourceLocation id = rootColours ? ForgeRegistries.ITEMS.getKey(this) : null;
        return id == null ? 1 : GradedFood.colourSaturation(id,
                ProduceStacks.colourName(colourOf(stack)));
    }

    public CultivarInfo.FoodReference reference() {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(this);
        return id == null ? fallback : GradedFood.referenceOf(id).orElse(fallback);
    }

    @Override
    @EntryPoint
    public FoodProperties getFoodProperties(ItemStack stack, LivingEntity entity) {
        return Guarded.call("produce food", "produce", () -> GradedFood.of(reference(),
                ProduceStacks.grade(stack.getTag()), ProduceStacks.bitter(stack.getTag()), toxic,
                colourSaturation(stack)), super.getFoodProperties(stack, entity));
    }

    /** D3: eating time by grade ({@link ProduceStacks#eatTicks}); bitterness does not change it. */
    @Override
    @EntryPoint
    public int getUseDuration(ItemStack stack) {
        return Guarded.call("produce eat time", "produce", () -> ProduceStacks.eatTicks(
                ProduceStacks.grade(stack.getTag())), super.getUseDuration(stack));
    }

    @Override
    @EntryPoint
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        Guarded.run("produce tooltip", "produce", () -> {
            if (rootColours) {
                String colour = ProduceStacks.colourName(colourOf(stack));
                tooltip.add(Component.translatable(ROOT_COLOUR_KEY, Component.translatable("wildroots.colour." + colour)));
                // what the colour does comes from the sign of the species' colour properties (WR-704): where the
                // client has no such data (a remote client, SyncedDefinitions) the name line stands alone
                ResourceLocation id = ForgeRegistries.ITEMS.getKey(this);
                if (id != null) {
                    for (String key : LensColour.propertyKeys(GradedFood.colourProps(id, colour))) {
                        tooltip.add(Component.translatable(key).withStyle(ChatFormatting.GRAY));
                    }
                }
            }
            tooltip.add(Component.translatable("wildroots.grade." + ProduceStacks.grade(stack.getTag())));
            if (ProduceStacks.bitter(stack.getTag())) {
                tooltip.add(Component.translatable("wildroots.grade.bitter"));
            }
        });
    }

    /** Colour pages unlock once when a real player obtains the crop, including an old saved stack. */
    @Override
    @EntryPoint
    public void inventoryTick(ItemStack stack, Level level, net.minecraft.world.entity.Entity entity, int slot,
                              boolean selected) {
        if (rootColours && level instanceof net.minecraft.server.level.ServerLevel
                && entity instanceof net.minecraft.server.level.ServerPlayer player
                && !(player instanceof net.minecraftforge.common.util.FakePlayer)) {
            Guarded.run("journal crop colour", player.getUUID(), () ->
                    io.github.slugblue.wildroots.journal.HintTriggers.produceObtained(player, stack));
        }
    }

    /**
     * Playtest 2, item 4: the styled lens panel replaces the long text tooltip
     * (the client keeps the text lines for Shift, {@code client.ClientLensPanel}).
     */
    @Override
    @EntryPoint
    public java.util.Optional<net.minecraft.world.inventory.tooltip.TooltipComponent> getTooltipImage(ItemStack stack) {
        return Guarded.call("lens panel image", "produceitem", () -> io.github.slugblue.wildroots.lens.LensPanel.tooltipImage(stack),
                java.util.Optional.empty());
    }
}
