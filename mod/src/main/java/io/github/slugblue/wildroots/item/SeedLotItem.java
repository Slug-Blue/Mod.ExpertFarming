package io.github.slugblue.wildroots.item;

import io.github.slugblue.wildroots.core.safety.DispCodec;
import io.github.slugblue.wildroots.core.safety.EntryPoint;
import io.github.slugblue.wildroots.core.safety.Guarded;
import io.github.slugblue.wildroots.plant.PlantService;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * {@code wildroots:seed_lot} (GDD §5.3; docs/05-architecture.md §6.1;
 * ADR-0007): the seeds of one mother plant. Stacks only with the same lot.
 * The tooltip shows only the {@code disp} lines the server wrote (SEC-07);
 * the client never reads the genome. A lazy lot is realised on the server in
 * {@code inventoryTick}, at most 8 per player per tick (SEC-35).
 */
public class SeedLotItem extends Item {

    static final RealiseBudget BUDGET = new RealiseBudget();

    public SeedLotItem(Properties properties) {
        super(properties);
    }

    @Override
    @EntryPoint
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level instanceof ServerLevel server && entity instanceof Player player) {
            Guarded.run("lot inventory tick", player.getUUID(), () -> {
                if (LotStacks.read(stack) instanceof LotNbt.Lazy
                        && BUDGET.tryTake(player.getUUID(), server.getGameTime())) {
                    LotStacks.realise(stack, server);
                }
            });
        }
    }

    /**
     * Plants one seed on the top of farmland (WR-133; GDD §3.8, §5.3): a lazy
     * lot is realised first, an inert lot is refused. The stack shrinks by 1
     * (not in creative).
     */
    @Override
    @EntryPoint
    public InteractionResult useOn(UseOnContext ctx) {
        if (ctx.getClickedFace() != Direction.UP) {
            return InteractionResult.PASS;
        }
        if (!(ctx.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        BlockPos pos = ctx.getClickedPos().above();
        return Guarded.call("lot planting", pos, () -> plant(level, pos, ctx), InteractionResult.FAIL);
    }

    private static InteractionResult plant(ServerLevel level, BlockPos pos, UseOnContext ctx) {
        ItemStack stack = ctx.getItemInHand();
        if (!PlantService.canPlantAt(level, pos)) {
            return InteractionResult.PASS;
        }
        LotStacks.realise(stack, level);
        if (!(LotStacks.read(stack) instanceof LotNbt.Lot lot)) {
            return InteractionResult.FAIL;
        }
        Player player = ctx.getPlayer();
        if (!PlantService.plant(level, pos, lot, stack.getCount(), player == null ? null : player.getUUID())) {
            return InteractionResult.FAIL;
        }
        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
            io.github.slugblue.wildroots.journal.HintTriggers.planted(level, pos, sp);
        }
        if (player == null || !player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    @EntryPoint
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        Guarded.run("lot tooltip", "lot", () -> {
            LotNbt.Result r = LotNbt.read(LotNbt.root(stack.getTag()));
            if (r instanceof LotNbt.Inert) {
                tooltip.add(Component.translatable("wildroots.tooltip.lot.damaged"));
                return;
            }
            for (DispCodec.Line line : LotNbt.disp(LotNbt.root(stack.getTag()))) {
                tooltip.add(DispCodec.toComponent(line));
            }
        });
    }
}
