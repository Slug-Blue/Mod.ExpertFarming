package io.github.slugblue.wildroots.client.journal.page;

import io.github.slugblue.wildroots.client.journal.GuideUnlocks;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * What the open book offers to page types and views (journal v2 §2.4,
 * §3): the font, the opening rules, navigation and tooltips. One context
 * per layout; views may keep it.
 */
@OnlyIn(Dist.CLIENT)
public interface PageContext {

    Font font();

    /** Opening rules of this layout (§2.3): which entries, pages and ids are open. */
    GuideUnlocks unlocks();

    /** Opens a guide entry, pushing the current page to history; a closed or unknown id does nothing. */
    void open(String entryId);

    /** Opens the first open entry that lists {@code item} in {@code items} (П3); whether one exists. */
    boolean openItem(ResourceLocation item);

    /** Whether some open entry lists {@code item} (to show the icon as clickable). */
    boolean hasEntryFor(ResourceLocation item);

    /** Shows a tooltip at the pointer this frame. */
    void tooltip(List<Component> lines);

    /** Shows the vanilla tooltip of a stack at the pointer this frame. */
    void itemTooltip(ItemStack stack);

    /** Client option {@code journalScenes} (§5.2): {@code false} = every scene shows its 2D frame. */
    boolean scenesEnabled();

    /** Client option {@code debugJournal}: debug counters on screen. */
    boolean debug();
}
