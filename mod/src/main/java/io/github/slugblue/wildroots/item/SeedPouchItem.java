package io.github.slugblue.wildroots.item;

import io.github.slugblue.wildroots.core.Ids;
import io.github.slugblue.wildroots.core.safety.EntryPoint;
import io.github.slugblue.wildroots.core.safety.Guarded;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * {@code wildroots:seed_pouch} (GDD §5.3; docs/05-architecture.md §6.3;
 * ADR-0007; SEC-09, SEC-11, SEC-17): behaves like the vanilla bundle of
 * 1.20.1. Right click with the pouch on a lot (or with a lot on the pouch)
 * puts seeds in; right click on an empty slot (or with an empty cursor)
 * takes the last seed out. 16 places of one seed each, entries never merge,
 * the rest of a lot stays with the player. Only {@code #wildroots:pouchable}
 * that is a readable lot; never a pouch. All changes happen on the server
 * side of the menu (the client only shows the server's result). The pouch
 * does not fit into container items (shulker box, bundle), and a destroyed
 * dropped pouch drops its contents once.
 */
public class SeedPouchItem extends Item {

    public static final TagKey<Item> POUCHABLE = ItemTags.create(Ids.mod("pouchable"));
    private static final int BAR_COLOR = Mth.color(0.4F, 0.4F, 1.0F);

    public SeedPouchItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    /** Contents of {@code pouch}; empty for a pouch whose data breaks the limits. */
    public static Optional<PouchContents> contents(ItemStack pouch) {
        return PouchContents.read(pouch.getTag() == null ? null : pouch.getTag().get(PouchContents.KEY));
    }

    static void store(ItemStack pouch, PouchContents c) {
        if (c.used() == 0 && c.write().size() <= 2) {
            CompoundTag t = pouch.getTag();
            if (t != null) {
                t.remove(PouchContents.KEY);
                if (t.isEmpty()) {
                    pouch.setTag(null);
                }
            }
            return;
        }
        pouch.getOrCreateTag().put(PouchContents.KEY, c.write());
    }

    /** Whether {@code stack} may go into a pouch: a pouchable, readable lot, not a pouch. */
    public static boolean accepts(ItemStack stack) {
        if (stack.isEmpty() || stack.getItem() instanceof SeedPouchItem || !stack.is(POUCHABLE)) {
            return false;
        }
        LotNbt.Result r = LotStacks.read(stack);
        return r instanceof LotNbt.Lot || r instanceof LotNbt.Lazy;
    }

    /**
     * Inserts up to the free places of {@code from}; the caller removes
     * exactly the returned number from the source. 0 = nothing changed.
     */
    static int insert(ItemStack pouch, ItemStack from) {
        if (!accepts(from)) {
            return 0;
        }
        Optional<PouchContents> c = contents(pouch);
        if (c.isEmpty()) {
            return 0;
        }
        Optional<PouchContents.Inserted> in = c.get().insert(from.getTag().getCompound(LotNbt.KEY), from.getCount());
        if (in.isEmpty()) {
            return 0;
        }
        store(pouch, in.get().pouch());
        return in.get().moved();
    }

    /** Takes the last seed out as a one-seed lot, or empty. */
    static ItemStack extract(ItemStack pouch) {
        Optional<PouchContents.Extracted> e = contents(pouch).flatMap(PouchContents::removeLast);
        if (e.isEmpty()) {
            return ItemStack.EMPTY;
        }
        store(pouch, e.get().pouch());
        return LotStacks.stack(e.get().lot(), 1);
    }

    /** No pouch in a shulker box or a bundle (SEC-11, S14). */
    @Override
    public boolean canFitInsideContainerItems() {
        return false;
    }

    /** Pouch on the cursor, right click on a slot: take a lot in, or put the last seed into an empty slot. */
    @Override
    @EntryPoint
    public boolean overrideStackedOnOther(ItemStack pouch, Slot slot, ClickAction action, Player player) {
        if (action != ClickAction.SECONDARY || pouch.getCount() != 1) {
            return false;
        }
        if (player.level().isClientSide) {
            return true; // the server decides, the menu sync shows the result (SEC-17)
        }
        Guarded.run("pouch click", player.getUUID(), () -> {
            ItemStack inSlot = slot.getItem();
            if (inSlot.isEmpty()) {
                ItemStack out = extract(pouch);
                if (!out.isEmpty()) {
                    ItemStack rest = slot.safeInsert(out);
                    if (!rest.isEmpty()) {
                        // the slot refused it: put the seed back, nothing changes
                        insert(pouch, rest);
                    } else {
                        player.playSound(SoundEvents.BUNDLE_REMOVE_ONE, 0.8F, 0.8F + player.level().getRandom().nextFloat() * 0.4F);
                    }
                }
            } else if (accepts(inSlot) && slot.mayPickup(player)) {
                ItemStack probe = pouch.copy();
                int n = insert(probe, inSlot);
                if (n > 0) {
                    ItemStack taken = slot.safeTake(inSlot.getCount(), n, player);
                    if (taken.getCount() == n) {
                        pouch.setTag(probe.getTag());
                        player.playSound(SoundEvents.BUNDLE_INSERT, 0.8F, 0.8F + player.level().getRandom().nextFloat() * 0.4F);
                    } else {
                        slot.safeInsert(taken); // could not take exactly n: undo
                    }
                }
            }
        });
        return true;
    }

    /** Pouch in a slot, right click on it with a lot (insert) or with an empty cursor (take the last seed). */
    @Override
    @EntryPoint
    public boolean overrideOtherStackedOnMe(ItemStack pouch, ItemStack other, Slot slot, ClickAction action,
                                            Player player, SlotAccess access) {
        if (action != ClickAction.SECONDARY || !slot.allowModification(player) || pouch.getCount() != 1) {
            return false;
        }
        if (player.level().isClientSide) {
            return true;
        }
        Guarded.run("pouch click", player.getUUID(), () -> {
            if (other.isEmpty()) {
                ItemStack out = extract(pouch);
                if (!out.isEmpty()) {
                    access.set(out);
                    player.playSound(SoundEvents.BUNDLE_REMOVE_ONE, 0.8F, 0.8F + player.level().getRandom().nextFloat() * 0.4F);
                }
            } else {
                int n = insert(pouch, other);
                if (n > 0) {
                    other.shrink(n);
                    player.playSound(SoundEvents.BUNDLE_INSERT, 0.8F, 0.8F + player.level().getRandom().nextFloat() * 0.4F);
                }
            }
        });
        return true;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return contents(stack).map(PouchContents::used).orElse(0) > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.min(1 + 12 * contents(stack).map(PouchContents::used).orElse(0) / PouchContents.CAPACITY, 13);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return BAR_COLOR;
    }

    /** Tooltip image: the lots inside, last one first (client component in {@code client}). */
    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
        return contents(stack).map(c -> {
            List<ItemStack> items = new ArrayList<>(c.used());
            List<CompoundTag> e = c.entries();
            for (int i = e.size() - 1; i >= 0; i--) {
                ItemStack s = new ItemStack(io.github.slugblue.wildroots.core.registry.ModItems.SEED_LOT.get());
                s.getOrCreateTag().put(LotNbt.KEY, e.get(i).copy());
                items.add(s);
            }
            return new PouchTooltip(items, c.used());
        });
    }

    @Override
    @EntryPoint
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        Guarded.run("pouch tooltip", "pouch", () -> {
            Optional<PouchContents> c = contents(stack);
            tooltip.add(c.isPresent()
                    ? Component.translatable("wildroots.tooltip.pouch.fullness", c.get().used(), PouchContents.CAPACITY)
                    : Component.translatable("wildroots.tooltip.lot.damaged"));
        });
    }

    /** A destroyed dropped pouch drops its contents once: the tag is cleared before the drop. */
    @Override
    @EntryPoint
    public void onDestroyed(ItemEntity entity) {
        Guarded.run("pouch destroyed", entity.getUUID(), () -> {
            ItemStack pouch = entity.getItem();
            Optional<PouchContents> c = contents(pouch);
            if (c.isEmpty() || c.get().used() == 0) {
                return;
            }
            List<ItemStack> out = new ArrayList<>();
            for (CompoundTag lot : c.get().entries()) {
                out.add(LotStacks.stack(lot, 1));
            }
            pouch.removeTagKey(PouchContents.KEY);
            ItemUtils.onContainerDestroyed(entity, out.stream());
        });
    }
}
