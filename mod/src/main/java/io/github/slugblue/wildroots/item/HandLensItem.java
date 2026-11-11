package io.github.slugblue.wildroots.item;

import io.github.slugblue.wildroots.core.registry.ModItems;
import io.github.slugblue.wildroots.core.safety.DispCodec;
import io.github.slugblue.wildroots.core.safety.EntryPoint;
import io.github.slugblue.wildroots.core.safety.Guarded;
import io.github.slugblue.wildroots.journal.HintTriggers;
import io.github.slugblue.wildroots.lens.Lens;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.util.FakePlayer;

/**
 * {@code wildroots:hand_lens} (GDD §10 tier 1; narrative §7.8, §9.6): use on
 * a plant shows its tier-1 reading in chat; use with seeds in the other hand
 * inspects the lot ({@code inspected}, tier 1 in the tooltip for good).
 * Everything is computed on the server; no durability, no cost.
 *
 * <p>On the mouse cursor in a container screen (D2, 2026-11-11): a click
 * with the lens on a seed lot or a mother root inspects it in place
 * ({@link #overrideStackedOnOther}); the click never swaps the lens with
 * the stack. Hovering the lens over a lot, a mother root or produce shows
 * the lens panel (client, {@code lens.LensPanel}).
 */
public class HandLensItem extends Item {

    public static final String NOTHING = "wildroots.message.lens.nothing";
    public static final String INSPECTED = "wildroots.message.lens.inspected";
    public static final String INSPECTED_ROOT = "wildroots.message.lens.inspected_root";

    /** Ticks between two uses (review 1, M2): the server enforces it in {@code ServerPlayerGameMode}. */
    public static final int COOLDOWN_TICKS = 10;

    public HandLensItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    @EntryPoint
    public InteractionResult useOn(UseOnContext ctx) {
        if (ctx.getPlayer() != null) {
            ctx.getPlayer().getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        }
        if (!(ctx.getLevel() instanceof ServerLevel level) || !(ctx.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;
        }
        return Guarded.call("lens on block", ctx.getClickedPos(), () -> {
            Optional<List<DispCodec.Line>> lines = Lens.inspectPlant(level, ctx.getClickedPos(), player);
            if (lines.isPresent()) {
                tell(player, lines.get());
                return InteractionResult.CONSUME;
            }
            return inspectOtherHand(level, player, ctx.getHand()) ? InteractionResult.CONSUME
                    : nothing(player);
        }, InteractionResult.FAIL);
    }

    @Override
    @EntryPoint
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack lens = player.getItemInHand(hand);
        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp)) {
            return InteractionResultHolder.success(lens);
        }
        boolean done = Guarded.call("lens on seeds", sp.getUUID(), () -> inspectOtherHand(server, sp, hand), false);
        if (!done) {
            nothing(sp);
        }
        return InteractionResultHolder.consume(lens);
    }

    /** The lot in the other hand, inspected; {@code false} if there is none. */
    static boolean inspectOtherHand(ServerLevel level, ServerPlayer player, InteractionHand lensHand) {
        ItemStack other = player.getItemInHand(lensHand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND
                : InteractionHand.MAIN_HAND);
        if (!other.is(ModItems.SEED_LOT.get()) || !Lens.inspectLot(other, level, player)) {
            return false;
        }
        if (player.connection != null) {
            player.displayClientMessage(Component.translatable(INSPECTED), true);
        }
        return true;
    }

    static InteractionResult nothing(ServerPlayer player) {
        if (player.connection != null) {
            player.displayClientMessage(Component.translatable(NOTHING), true);
        }
        return InteractionResult.PASS;
    }

    static void tell(ServerPlayer player, List<DispCodec.Line> lines) {
        if (player.connection == null) {
            return;
        }
        for (DispCodec.Line l : lines) {
            player.sendSystemMessage(DispCodec.toComponent(l));
        }
    }

    /**
     * D2: the lens carried on the cursor and clicked on a seed lot or a
     * mother root in a slot. Runs on both sides (vanilla
     * {@code AbstractContainerMenu#doClick}); the server inspects the stack
     * through the same path as {@link #use} ({@link Lens#inspectLot},
     * {@link Lens#inspectMother}) under the lens cooldown, the client only
     * predicts that the click is taken. No new packet: the vanilla container
     * click carries it, and the server's slot sync wins. A stack already
     * inspected is left alone; the click is still taken, so it never swaps.
     */
    @Override
    @EntryPoint
    public boolean overrideStackedOnOther(ItemStack lens, net.minecraft.world.inventory.Slot slot,
                                          net.minecraft.world.inventory.ClickAction action, Player player) {
        return Guarded.call("lens on a stack", player.getUUID(), () -> stackedOn(lens, slot, player), false);
    }

    static boolean stackedOn(ItemStack lens, net.minecraft.world.inventory.Slot slot, Player player) {
        ItemStack target = slot.getItem();
        if (!io.github.slugblue.wildroots.lens.LensPanel.inspectable(target) || !slot.allowModification(player)) {
            return false;
        }
        // the creative inventory screen handles clicks on the client only (SetCreativeModeSlot), so the
        // server never inspects there: the click stays a vanilla swap (review 1, minor 1); the panel works
        if (player.level().isClientSide() && player.isCreative() && player.containerMenu == player.inventoryMenu) {
            return false;
        }
        if (io.github.slugblue.wildroots.lens.LensPanel.inspected(target)
                || player.getCooldowns().isOnCooldown(lens.getItem())) {
            return true;
        }
        player.getCooldowns().addCooldown(lens.getItem(), COOLDOWN_TICKS);
        if (player.level() instanceof ServerLevel level) {
            ServerPlayer sp = player instanceof ServerPlayer s && !(s instanceof FakePlayer) ? s : null;
            boolean done = target.is(ModItems.SEED_LOT.get()) ? Lens.inspectLot(target, level, sp)
                    : Lens.inspectMother(target, level, sp);
            if (done) {
                slot.setChanged();
                if (sp != null && sp.connection != null) {
                    sp.displayClientMessage(Component.translatable(target.is(ModItems.SEED_LOT.get()) ? INSPECTED
                            : INSPECTED_ROOT), true);
                }
            }
        }
        return true;
    }

    /** First time in the inventory: the lens tool page (narrative §4.6). */
    @Override
    @EntryPoint
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (entity instanceof ServerPlayer p && !(p instanceof FakePlayer)) {
            Guarded.run("lens tool page", p.getUUID(), () -> HintTriggers.toolInInventory(p, "hand_lens"));
        }
    }

    @Override
    @EntryPoint
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        Guarded.run("lens tooltip", "lens", () -> {
            tooltip.add(Component.translatable("item.wildroots.hand_lens.desc"));
            tooltip.add(Component.translatable("item.wildroots.hand_lens.desc.cursor"));
        });
    }
}
