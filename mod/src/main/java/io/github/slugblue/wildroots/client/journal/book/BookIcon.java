package io.github.slugblue.wildroots.client.journal.book;

import io.github.slugblue.wildroots.client.journal.GuideIcon;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;

/** An icon on a page or tab: an item stack or a {@code gui:} sprite, drawn at 16 or 12 px. */
@OnlyIn(Dist.CLIENT)
public record BookIcon(ItemStack stack, String sprite) {

    public static final BookIcon NONE = new BookIcon(ItemStack.EMPTY, null);

    public static BookIcon of(GuideIcon icon) {
        if (icon.item().isPresent()) {
            return item(icon.item().get());
        }
        return icon.sprite().map(s -> new BookIcon(ItemStack.EMPTY, s)).orElse(NONE);
    }

    /** A {@code gui:} icon of {@link JournalAtlas#ICONS}. */
    public static BookIcon sprite(String name) {
        return new BookIcon(ItemStack.EMPTY, name);
    }

    public static BookIcon item(ResourceLocation id) {
        Item item = ForgeRegistries.ITEMS.getValue(id);
        return item == null || item == Items.AIR ? NONE : new BookIcon(new ItemStack(item), null);
    }

    public boolean isEmpty() {
        return stack.isEmpty() && sprite == null;
    }

    /** Draws the icon with its top-left at {@code (x, y)}, {@code size} px (16 or 12). */
    public void draw(GuiGraphics g, int x, int y, int size) {
        if (isEmpty()) {
            return;
        }
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        float s = size / 16f;
        g.pose().scale(s, s, 1f);
        if (!stack.isEmpty()) {
            g.renderItem(stack, 0, 0);
        } else {
            JournalAtlas.icon(g, sprite, 0, 0);
        }
        g.pose().popPose();
    }
}
