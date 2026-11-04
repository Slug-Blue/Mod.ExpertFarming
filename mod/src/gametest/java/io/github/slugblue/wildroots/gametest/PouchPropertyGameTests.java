package io.github.slugblue.wildroots.gametest;

import io.github.slugblue.wildroots.WildRoots;
import io.github.slugblue.wildroots.core.registry.ModItems;
import io.github.slugblue.wildroots.item.LotNbt;
import io.github.slugblue.wildroots.item.LotStacks;
import io.github.slugblue.wildroots.item.PouchContents;
import io.github.slugblue.wildroots.item.SeedPouchItem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * WR-138, SEC-17 property test on the real item path (batch 3 test pass):
 * random sequences of menu clicks (pouch on a lot, lot on the pouch, split
 * a stack, take out into a slot or onto the cursor) over many seeds. After
 * every click the seeds of every {@code lotId} are conserved between the
 * slots and the pouch, the cursor is empty, and the pouch entries equal a
 * reference stack (last in, first out, never merged).
 */
@GameTestHolder(WildRoots.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PouchPropertyGameTests {

    static final int POUCH = 26;
    static final int RUNS = 40;
    static final int OPS = 80;

    private PouchPropertyGameTests() {
    }

    static boolean isLot(ItemStack s) {
        return s.is(ModItems.SEED_LOT.get()) && LotStacks.read(s) instanceof LotNbt.Lot;
    }

    static int slotWithLot(SimpleContainer c, SplittableRandom r, int minCount) {
        List<Integer> l = new ArrayList<>();
        for (int i = 0; i < POUCH; i++) {
            if (isLot(c.getItem(i)) && c.getItem(i).getCount() >= minCount) {
                l.add(i);
            }
        }
        return l.isEmpty() ? -1 : l.get(r.nextInt(l.size()));
    }

    static int emptySlot(SimpleContainer c, SplittableRandom r) {
        List<Integer> l = new ArrayList<>();
        for (int i = 0; i < POUCH; i++) {
            if (c.getItem(i).isEmpty()) {
                l.add(i);
            }
        }
        return l.isEmpty() ? -1 : l.get(r.nextInt(l.size()));
    }

    static long id(ItemStack s) {
        return ((LotNbt.Lot) LotStacks.read(s)).lotId();
    }

    static List<Long> entries(ItemStack pouch) {
        return SeedPouchItem.contents(pouch).orElseThrow().entries().stream().map(e -> e.getLong("id")).toList();
    }

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3, timeoutTicks = 200)
    public static void randomClicksConserveSeedsAndKeepLastInFirstOut(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockSurvivalPlayer();
        int inserts = 0;
        int extracts = 0;
        for (long seed = 1; seed <= RUNS; seed++) {
            SplittableRandom r = new SplittableRandom(seed * 0x9E3779B97F4A7C15L);
            SimpleContainer c = new SimpleContainer(27);
            ChestMenu menu = SeedPouchGameTests.menu(player, c);
            c.setItem(POUCH, SeedPouchGameTests.pouch());
            Map<Long, Integer> total = new HashMap<>();
            for (int i = 0; i < 3; i++) {
                ItemStack l = PlantingGameTests.lot(level, 2 + r.nextInt(30));
                c.setItem(i, l);
                total.merge(id(l), l.getCount(), Integer::sum);
            }
            List<Long> model = new ArrayList<>();
            for (int op = 0; op < OPS; op++) {
                String what;
                switch (r.nextInt(5)) {
                    case 0 -> { // pouch on the cursor, right click on a lot slot
                        what = "pouch onto lot";
                        int i = slotWithLot(c, r, 1);
                        if (i < 0) {
                            break;
                        }
                        long lot = id(c.getItem(i));
                        int before = c.getItem(i).getCount();
                        menu.clicked(POUCH, 0, ClickType.PICKUP, player);
                        menu.clicked(i, 1, ClickType.PICKUP, player);
                        int moved = before - c.getItem(i).getCount();
                        for (int k = 0; k < moved; k++) {
                            model.add(lot);
                        }
                        inserts += moved;
                        menu.clicked(POUCH, 0, ClickType.PICKUP, player);
                    }
                    case 1 -> { // lot on the cursor, right click on the pouch
                        what = "lot onto pouch";
                        int i = slotWithLot(c, r, 1);
                        if (i < 0) {
                            break;
                        }
                        long lot = id(c.getItem(i));
                        menu.clicked(i, 0, ClickType.PICKUP, player);
                        int before = menu.getCarried().getCount();
                        menu.clicked(POUCH, 1, ClickType.PICKUP, player);
                        int moved = before - menu.getCarried().getCount();
                        for (int k = 0; k < moved; k++) {
                            model.add(lot);
                        }
                        inserts += moved;
                        if (!menu.getCarried().isEmpty()) {
                            menu.clicked(i, 0, ClickType.PICKUP, player);
                        }
                    }
                    case 2 -> { // split: right click picks up half, left click puts it into an empty slot
                        what = "split";
                        int i = slotWithLot(c, r, 2);
                        int j = emptySlot(c, r);
                        if (i < 0 || j < 0) {
                            break;
                        }
                        menu.clicked(i, 1, ClickType.PICKUP, player);
                        menu.clicked(j, 0, ClickType.PICKUP, player);
                    }
                    case 3 -> { // pouch on the cursor, right click on an empty slot: last seed into the slot
                        what = "take into slot";
                        int j = emptySlot(c, r);
                        if (j < 0) {
                            break;
                        }
                        menu.clicked(POUCH, 0, ClickType.PICKUP, player);
                        menu.clicked(j, 1, ClickType.PICKUP, player);
                        menu.clicked(POUCH, 0, ClickType.PICKUP, player);
                        if (!model.isEmpty()) {
                            long expected = model.remove(model.size() - 1);
                            helper.assertTrue(c.getItem(j).getCount() == 1 && id(c.getItem(j)) == expected,
                                    "seed " + seed + " op " + op + ": took " + c.getItem(j) + " expected lot " + expected);
                            extracts++;
                        } else {
                            helper.assertTrue(c.getItem(j).isEmpty(), "empty pouch gave " + c.getItem(j));
                        }
                    }
                    default -> { // empty cursor, right click on the pouch: last seed onto the cursor, put it down
                        what = "take onto cursor";
                        int j = emptySlot(c, r);
                        if (j < 0) {
                            break;
                        }
                        menu.clicked(POUCH, 1, ClickType.PICKUP, player);
                        if (!model.isEmpty()) {
                            long expected = model.remove(model.size() - 1);
                            helper.assertTrue(menu.getCarried().getCount() == 1 && id(menu.getCarried()) == expected,
                                    "seed " + seed + " op " + op + ": took " + menu.getCarried() + " expected " + expected);
                            extracts++;
                            menu.clicked(j, 0, ClickType.PICKUP, player);
                        } else {
                            helper.assertTrue(menu.getCarried().isEmpty(), "empty pouch gave " + menu.getCarried());
                        }
                    }
                }
                String at = "seed " + seed + " op " + op + " (" + what + ")";
                helper.assertTrue(menu.getCarried().isEmpty(), at + ": cursor not empty: " + menu.getCarried());
                ItemStack pouch = c.getItem(POUCH);
                helper.assertTrue(pouch.is(ModItems.SEED_POUCH.get()), at + ": pouch moved or lost: " + pouch);
                helper.assertTrue(entries(pouch).equals(model), at + ": entries " + entries(pouch) + " model " + model);
                helper.assertTrue(model.size() <= PouchContents.CAPACITY, at + ": over capacity");
                Map<Long, Integer> now = new HashMap<>();
                for (int i = 0; i < POUCH; i++) {
                    ItemStack s = c.getItem(i);
                    if (!s.isEmpty()) {
                        helper.assertTrue(isLot(s), at + ": foreign item " + s);
                        now.merge(id(s), s.getCount(), Integer::sum);
                    }
                }
                model.forEach(m -> now.merge(m, 1, Integer::sum));
                helper.assertTrue(now.equals(total), at + ": seeds " + now + " expected " + total);
                // persisting the pouch (chunk save, relog) changes nothing
                CompoundTag saved = pouch.save(new CompoundTag());
                helper.assertTrue(entries(ItemStack.of(saved)).equals(model), at + ": save/load changed the entries");
            }
        }
        helper.assertTrue(inserts > 200 && extracts > 100, "property test barely exercised: " + inserts + "/" + extracts);
        helper.succeed();
    }
}
