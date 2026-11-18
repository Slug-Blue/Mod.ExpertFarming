package io.github.slugblue.wildroots.item;

import io.github.slugblue.wildroots.core.safety.EntryPoint;
import io.github.slugblue.wildroots.core.safety.Guarded;
import io.github.slugblue.wildroots.core.safety.LoadedLevelAccess;
import io.github.slugblue.wildroots.genetics.codec.DecodeResult;
import io.github.slugblue.wildroots.genetics.pollination.Brush;
import io.github.slugblue.wildroots.genetics.pollination.Donor;
import io.github.slugblue.wildroots.plant.FieldPlantBlock;
import io.github.slugblue.wildroots.plant.PendingLot;
import io.github.slugblue.wildroots.plant.PlantBlockEntity;
import io.github.slugblue.wildroots.pollination.BeePollen;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/** The first click collects pollen, another applies it; Shift collects a new sample. */
public final class PollinationBrushItem extends Item {
    public PollinationBrushItem(Properties properties) { super(properties.durability(128)); }

    @Override
    @EntryPoint
    public InteractionResult useOn(UseOnContext ctx) {
        if (!(ctx.getLevel() instanceof ServerLevel level) || !(ctx.getPlayer() instanceof ServerPlayer player))
            return InteractionResult.SUCCESS;
        if (player.getCooldowns().isOnCooldown(this)) return InteractionResult.FAIL;
        player.getCooldowns().addCooldown(this, 10);
        return Guarded.call("pollination brush", ctx.getClickedPos(), () -> apply(ctx, level, player), InteractionResult.FAIL);
    }

    private InteractionResult apply(UseOnContext ctx, ServerLevel level, ServerPlayer player) {
        var pos = ctx.getClickedPos();
        var be = LoadedLevelAccess.blockEntity(level, pos, PlantBlockEntity.class);
        var live = be.flatMap(b -> b.live(level));
        if (live.isEmpty() || FieldPlantBlock.stage(level.getBlockState(pos)) != FieldPlantBlock.FLOWER_STAGE)
            return InteractionResult.PASS;
        ItemStack stack = ctx.getItemInHand();
        BeePollen pollen = new BeePollen();
        if (stack.hasTag()) pollen.load(stack.getTag().getCompound("pollen"));
        if (!pollen.hasPollen() || player.isShiftKeyDown()) {
            pollen.collect(live.get().def().id(), pos.asLong(), live.get().genetics().encode(live.get().genome()).toFather());
            stack.getOrCreateTag().put("pollen", pollen.save());
            stack.getOrCreateTag().putString("pollen_dimension", level.dimension().location().toString());
            tell(player, "collected");
            return InteractionResult.CONSUME;
        }
        boolean self = pollen.source() == pos.asLong()
                && level.dimension().location().toString().equals(stack.getOrCreateTag().getString("pollen_dimension"))
                && java.util.Arrays.equals(pollen.genome().a(), live.get().data().genome().a());
        if (!pollen.species().equals(live.get().def().id())) { tell(player, "incompatible"); return InteractionResult.FAIL; }
        List<LotFathersNbt.Father> fathers = List.of();
        if (!self) {
            var decoded = live.get().genetics().decode(pollen.genome().withLot(live.get().data().genome().v(),
                    pollen.species()), live.get().def());
            if (!(decoded instanceof DecodeResult.Valid valid) || live.get().genetics().chooseFathers(live.get().def(),
                    live.get().genome(), List.of(), List.of(new Donor(valid.genome(), 1, true)), pos.asLong())
                    .map(s -> s.compatibleDonors() == 0).orElse(true)) {
                tell(player, "incompatible"); return InteractionResult.FAIL;
            }
            fathers = List.of(new LotFathersNbt.Father(pollen.genome(), 1, true));
        }
        be.get().setPendingLot(new PendingLot(fathers, self ? 0 : 1, self ? Brush.SELF : Brush.CROSS, false));
        if (live.get().data().floweringSince() < 0) be.get().setFloweringSince(level.getGameTime());
        // A manually pollinated static wild flower must be able to finish its seed cycle too.
        level.setBlock(pos, level.getBlockState(pos).setValue(FieldPlantBlock.ACTIVE, true), 2);
        stack.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(ctx.getHand()));
        Guarded.run("journal brushed", pos, () -> io.github.slugblue.wildroots.journal.Journals.unlock(player,
                self ? "hint.forced_self" : "hint.hand_pollination"));
        tell(player, self ? "self" : "applied");
        return InteractionResult.CONSUME;
    }

    private static void tell(ServerPlayer player, String key) {
        if (player.connection != null) player.displayClientMessage(Component.translatable("wildroots.brush." + key), true);
    }

    @Override
    @EntryPoint
    public void appendHoverText(ItemStack stack, Level level, List<Component> text, TooltipFlag flag) {
        Guarded.run("brush tooltip", "brush", () -> text.add(Component.translatable("item.wildroots.pollination_brush.desc")));
    }
}
