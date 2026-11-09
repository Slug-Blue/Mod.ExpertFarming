package io.github.slugblue.wildroots.item;

import io.github.slugblue.wildroots.core.safety.EntryPoint;
import io.github.slugblue.wildroots.core.safety.Guarded;
import io.github.slugblue.wildroots.journal.Journals;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * {@code wildroots:field_journal} (narrative §4.1, §7.8): only a window. The
 * entries live in the player capability, so the book has no NBT and any
 * copy (a new one crafted from a book and a lot) opens the same entries. Use
 * sends the whole journal with "open" and the client shows the screen.
 */
public class FieldJournalItem extends Item {

    public FieldJournalItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    @EntryPoint
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer sp) {
            Guarded.run("journal open", sp.getUUID(), () -> Journals.syncFull(sp, true));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    /** First time in the inventory: the journal's own tool page (narrative §4.6). */
    @Override
    @EntryPoint
    public void inventoryTick(ItemStack stack, Level level, net.minecraft.world.entity.Entity entity, int slot,
                              boolean selected) {
        if (entity instanceof ServerPlayer p && !(p instanceof net.minecraftforge.common.util.FakePlayer)) {
            Guarded.run("journal tool page", p.getUUID(),
                    () -> io.github.slugblue.wildroots.journal.HintTriggers.toolInInventory(p, "field_journal"));
        }
    }

    @Override
    @EntryPoint
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        Guarded.run("journal tooltip", "journal",
                () -> tooltip.add(Component.translatable("item.wildroots.field_journal.desc")));
    }
}
