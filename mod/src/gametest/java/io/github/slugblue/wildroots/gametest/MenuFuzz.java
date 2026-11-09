package io.github.slugblue.wildroots.gametest;

import com.mojang.authlib.GameProfile;
import io.github.slugblue.wildroots.core.registry.ModItems;
import io.github.slugblue.wildroots.item.LotNbt;
import io.github.slugblue.wildroots.item.LotStacks;
import io.github.slugblue.wildroots.item.PouchContents;
import io.github.slugblue.wildroots.item.SeedPouchItem;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.TreeMap;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

/**
 * The {@code MenuFuzz} harness (SEC-14, SEC-17; docs/09-security.md §1.6.3;
 * WR-186): random click sequences over a menu, from a random start state,
 * with every {@link ClickType} (PICKUP, QUICK_MOVE, SWAP 0–8 and 40, CLONE
 * in survival, THROW, QUICK_CRAFT drags of all three kinds, PICKUP_ALL).
 * After each sequence the seeds of every {@code lotId} — in the menu slots,
 * inside pouches, on the cursor and thrown out — must equal the start, and
 * no pouch may appear or vanish. A failure names the seed, the sequence
 * number and the clicks.
 *
 * <p>Built to be reused: a new menu (seed analyser WR-184, seed bank WR-185)
 * passes its own {@link Target}; the player inventory menu is the first.
 */
public final class MenuFuzz {

    /** A menu under test for one sequence: built on the fuzz player, filled by {@link #fill}. */
    public interface Target {
        AbstractContainerMenu open(ServerPlayer player);

        /** Slots of {@code menu} that the start state may fill. */
        List<Integer> fillable(AbstractContainerMenu menu);

        /** Called after a sequence, before counting (e.g. close the menu). */
        default void finish(ServerPlayer player, AbstractContainerMenu menu) {
        }
    }

    /** A survival player whose thrown items are kept in a list instead of entering the world. */
    public static final class FuzzPlayer extends ServerPlayer {
        final List<ItemStack> thrown = new ArrayList<>();

        FuzzPlayer(ServerLevel level, String name) {
            super(level.getServer(), level, new GameProfile(java.util.UUID.randomUUID(), name));
            gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
            // menu sync sends slot packets: a listener over a channel-less connection that drops them
            new net.minecraft.server.network.ServerGamePacketListenerImpl(level.getServer(),
                    new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND), this) {
                @Override
                public void send(net.minecraft.network.protocol.Packet<?> packet) {
                }

                @Override
                public void send(net.minecraft.network.protocol.Packet<?> packet,
                                 net.minecraft.network.PacketSendListener listener) {
                }
            };
        }

        @Override
        public ItemEntity drop(ItemStack stack, boolean dropAround, boolean includeThrower) {
            if (!stack.isEmpty()) {
                thrown.add(stack.copy());
            }
            return null;
        }
    }

    /** Outcome of a run. */
    public record Result(int sequences, int clicks, long millis) {
    }

    private MenuFuzz() {
    }

    /** Seeds per lotId in a set of stacks; pouches count their entries; {@code pouches[0]} counts pouches. */
    static Map<Long, Integer> seeds(List<ItemStack> stacks, int[] pouches, List<String> foreign) {
        Map<Long, Integer> m = new TreeMap<>();
        for (ItemStack s : stacks) {
            if (s.isEmpty()) {
                continue;
            }
            if (s.is(ModItems.SEED_POUCH.get())) {
                pouches[0] += s.getCount();
                PouchContents c = SeedPouchItem.contents(s).orElse(null);
                if (c == null) {
                    foreign.add("unreadable pouch " + s.getTag());
                    continue;
                }
                for (CompoundTag e : c.entries()) {
                    m.merge(e.getLong("id"), 1, Integer::sum);
                }
            } else if (LotStacks.read(s) instanceof LotNbt.Lot lot) {
                m.merge(lot.lotId(), s.getCount(), Integer::sum);
            } else {
                foreign.add(s.toString());
            }
        }
        return m;
    }

    static List<ItemStack> everything(AbstractContainerMenu menu, FuzzPlayer p) {
        List<ItemStack> all = new ArrayList<>();
        for (int i = 0; i < menu.slots.size(); i++) {
            all.add(menu.slots.get(i).getItem());
        }
        all.add(menu.getCarried());
        all.addAll(p.thrown);
        return all;
    }

    /** A pouch holding the first {@code n} seeds of {@code lots} (one entry per seed, LIFO order). */
    static ItemStack pouch(List<ItemStack> lots, SplittableRandom r, int n) {
        PouchContents c = PouchContents.EMPTY;
        for (int i = 0; i < n; i++) {
            ItemStack lot = lots.get(r.nextInt(lots.size()));
            var ins = c.insert((CompoundTag) LotNbt.root(lot.getTag()), 1);
            if (ins.isPresent()) {
                c = ins.get().pouch();
            }
        }
        ItemStack pouch = new ItemStack(ModItems.SEED_POUCH.get());
        if (c.used() > 0) {
            pouch.getOrCreateTag().put(PouchContents.KEY, c.write());
        }
        return pouch;
    }

    /** One random click (or one whole drag) on {@code menu}; appends its description to {@code log}. */
    static void click(AbstractContainerMenu menu, FuzzPlayer p, SplittableRandom r, List<String> log) {
        int n = menu.slots.size();
        int slot = r.nextInt(n);
        switch (r.nextInt(9)) {
            case 0, 1 -> {
                int target = r.nextInt(12) == 0 ? AbstractContainerMenu.SLOT_CLICKED_OUTSIDE : slot;
                int button = r.nextInt(2);
                log.add("PICKUP " + target + "/" + button);
                menu.clicked(target, button, ClickType.PICKUP, p);
            }
            case 2 -> {
                int button = r.nextInt(2);
                log.add("QUICK_MOVE " + slot + "/" + button);
                menu.clicked(slot, button, ClickType.QUICK_MOVE, p);
            }
            case 3 -> {
                int button = r.nextInt(10) == 0 ? 40 : r.nextInt(9);
                log.add("SWAP " + slot + "/" + button);
                menu.clicked(slot, button, ClickType.SWAP, p);
            }
            case 4 -> {
                log.add("CLONE " + slot);
                menu.clicked(slot, 2, ClickType.CLONE, p);
            }
            case 5 -> {
                int button = r.nextInt(2);
                log.add("THROW " + slot + "/" + button);
                menu.clicked(slot, button, ClickType.THROW, p);
            }
            case 6 -> {
                log.add("PICKUP_ALL " + slot);
                menu.clicked(slot, 0, ClickType.PICKUP_ALL, p);
            }
            default -> {
                int type = r.nextInt(3);
                int k = 1 + r.nextInt(4);
                StringBuilder sb = new StringBuilder("QUICK_CRAFT type " + type + " slots");
                menu.clicked(AbstractContainerMenu.SLOT_CLICKED_OUTSIDE, AbstractContainerMenu.getQuickcraftMask(0, type),
                        ClickType.QUICK_CRAFT, p);
                for (int i = 0; i < k; i++) {
                    int s = r.nextInt(n);
                    sb.append(' ').append(s);
                    menu.clicked(s, AbstractContainerMenu.getQuickcraftMask(1, type), ClickType.QUICK_CRAFT, p);
                }
                menu.clicked(AbstractContainerMenu.SLOT_CLICKED_OUTSIDE, AbstractContainerMenu.getQuickcraftMask(2, type),
                        ClickType.QUICK_CRAFT, p);
                log.add(sb.toString());
            }
        }
    }

    /**
     * Runs {@code sequences} sequences of 1–{@code maxClicks} clicks on
     * {@code target} with seed {@code seed}; {@code lots} is a pool of
     * distinct lots. Calls {@code fail} with a message on the first broken
     * invariant and stops.
     */
    public static Result run(ServerLevel level, Target target, List<ItemStack> lots, long seed, int sequences,
                             int maxClicks, Consumer<String> fail) {
        long t0 = System.nanoTime();
        FuzzPlayer p = new FuzzPlayer(level, "wr186_fuzz");
        int clicks = 0;
        try {
            SplittableRandom r = new SplittableRandom(seed);
            for (int seq = 0; seq < sequences; seq++) {
                p.thrown.clear();
                p.getInventory().clearContent();
                AbstractContainerMenu menu = target.open(p);
                menu.setCarried(ItemStack.EMPTY);
                for (net.minecraft.world.inventory.Slot slot : menu.slots) {
                    slot.set(ItemStack.EMPTY);
                }
                List<Integer> slots = new ArrayList<>(target.fillable(menu));
                // start state: 1–2 pouches with 0–16 seeds, 1–5 lot stacks of 1–64 seeds, in random slots
                int pouches = 1 + r.nextInt(2);
                int stacks = 1 + r.nextInt(5);
                for (int i = 0; i < pouches + stacks && !slots.isEmpty(); i++) {
                    int at = slots.remove(r.nextInt(slots.size()));
                    ItemStack s = i < pouches ? pouch(lots, r, r.nextInt(PouchContents.CAPACITY + 1))
                            : lots.get(r.nextInt(lots.size())).copyWithCount(1 + r.nextInt(64));
                    menu.slots.get(at).set(s);
                }
                int[] pouchesBefore = {0};
                List<String> foreign = new ArrayList<>();
                Map<Long, Integer> before = seeds(everything(menu, p), pouchesBefore, foreign);
                List<String> log = new ArrayList<>();
                int k = 1 + r.nextInt(maxClicks);
                for (int c = 0; c < k; c++) {
                    click(menu, p, r, log);
                    clicks++;
                }
                target.finish(p, menu);
                int[] pouchesAfter = {0};
                Map<Long, Integer> after = seeds(everything(menu, p), pouchesAfter, foreign);
                if (!after.equals(before) || pouchesAfter[0] != pouchesBefore[0] || !foreign.isEmpty()) {
                    fail.accept("MenuFuzz seed " + seed + " sequence " + seq + ": seeds " + before + " -> " + after
                            + ", pouches " + pouchesBefore[0] + " -> " + pouchesAfter[0] + ", foreign " + foreign
                            + ", clicks " + log);
                    return new Result(seq, clicks, (System.nanoTime() - t0) / 1_000_000);
                }
            }
        } finally {
            p.discard();
        }
        return new Result(sequences, clicks, (System.nanoTime() - t0) / 1_000_000);
    }

    /** The player inventory menu: every slot but the crafting result (index 0). */
    public static final Target PLAYER_INVENTORY = new Target() {
        @Override
        public AbstractContainerMenu open(ServerPlayer player) {
            return player.inventoryMenu;
        }

        @Override
        public List<Integer> fillable(AbstractContainerMenu menu) {
            List<Integer> l = new ArrayList<>();
            for (int i = 9; i < menu.slots.size(); i++) {
                l.add(i); // main inventory, hotbar, off hand: armour slots refuse lots anyway
            }
            for (int i = 1; i <= 4; i++) {
                l.add(i); // the 2×2 crafting grid
            }
            return l;
        }
    };
}
