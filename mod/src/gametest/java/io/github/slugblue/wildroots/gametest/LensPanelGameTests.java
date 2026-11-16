package io.github.slugblue.wildroots.gametest;

import io.github.slugblue.wildroots.WildRoots;
import io.github.slugblue.wildroots.core.registry.ModItems;
import io.github.slugblue.wildroots.core.safety.NbtLimits;
import io.github.slugblue.wildroots.item.LotFlags;
import io.github.slugblue.wildroots.item.LotNbt;
import io.github.slugblue.wildroots.item.LotStacks;
import io.github.slugblue.wildroots.item.MotherItem;
import io.github.slugblue.wildroots.item.MotherNbt;
import io.github.slugblue.wildroots.item.ProduceStacks;
import io.github.slugblue.wildroots.lens.LensPanel;
import io.github.slugblue.wildroots.plant.FieldPlantBlock;
import io.github.slugblue.wildroots.plant.PlantBlockEntity;
import java.util.Arrays;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * D2 (owner, 2026-11-11): the hand lens on the mouse cursor. A click on a
 * seed lot or a mother root inspects it on the server through the vanilla
 * container click (no new packet), under the lens cooldown, and never
 * swaps; a click on produce stays vanilla. The panel reads only what the
 * stack carries.
 */
@GameTestHolder(WildRoots.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LensPanelGameTests {

    private LensPanelGameTests() {
    }

    /** A left click on menu slot {@code slot} of the inventory menu (9–35 are inventory slots 9–35). */
    static void click(ServerPlayer p, int slot) {
        p.inventoryMenu.clicked(slot, 0, ClickType.PICKUP, p);
    }

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3, batch = "d2_lens_panel")
    public static void lensOnTheCursorInspectsALotAndAMotherRoot(GameTestHelper helper) {
        ServerPlayer p = JournalGameTests.survival(helper, "d2_cursor");
        try {
            ServerLevel level = helper.getLevel();
            ItemStack lot = PlantingGameTests.lot(level, 5);
            p.getInventory().setItem(9, lot);
            p.inventoryMenu.setCarried(new ItemStack(ModItems.HAND_LENS.get()));
            helper.assertTrue(LensPanel.of(lot).orElseThrow().bars().isEmpty(), "uninspected lot has bars");

            click(p, 9);
            ItemStack after = p.getInventory().getItem(9);
            helper.assertTrue(p.inventoryMenu.getCarried().is(ModItems.HAND_LENS.get()), "lens left the cursor");
            helper.assertTrue(after.is(ModItems.SEED_LOT.get()) && after.getCount() == 5, "lot swapped or changed");
            helper.assertTrue(LotStacks.read(after) instanceof LotNbt.Lot l && (l.flags() & LotFlags.INSPECTED) != 0,
                    "lot not inspected by the click");
            LensPanel panel = LensPanel.of(after).orElseThrow();
            int traits = PlantFixtures.carrot().def().quantitative().size();
            helper.assertTrue(panel.bars().size() == traits, "bars " + panel.bars().size() + " of " + traits);
            helper.assertTrue(p.getCooldowns().isOnCooldown(ModItems.HAND_LENS.get()), "no cooldown");

            // a mother root during the cooldown: the click is taken, nothing is inspected
            PlantBlockEntity be = PlantFixtures.plant(PlantFixtures.place(helper, PlantFixtures.PLANT,
                    FieldPlantBlock.HARVEST_STAGE), 81);
            helper.assertTrue(be.live(level).isPresent(), "no plant");
            ItemStack mother = MotherRootGameTests.drops(helper, PlantFixtures.PLANT, p).stream()
                    .filter(s -> s.is(ModItems.MOTHER_ROOT.get())).findFirst().orElseThrow();
            MotherNbt.Mother before = MotherItem.read(mother).orElseThrow();
            helper.assertTrue(LensPanel.of(mother).orElseThrow().bars().isEmpty(), "uninspected root has bars");
            p.getInventory().setItem(10, mother);
            click(p, 10);
            helper.assertTrue(!LensPanel.inspected(p.getInventory().getItem(10)), "inspected during the cooldown");
            helper.assertTrue(p.getInventory().getItem(10).is(ModItems.MOTHER_ROOT.get())
                    && p.inventoryMenu.getCarried().is(ModItems.HAND_LENS.get()), "swapped during the cooldown");

            p.getCooldowns().removeCooldown(ModItems.HAND_LENS.get());
            click(p, 10);
            ItemStack root = p.getInventory().getItem(10);
            helper.assertTrue(LensPanel.inspected(root), "mother root not inspected");
            helper.assertTrue(NbtLimits.mother(root.getTag().get(MotherNbt.KEY)).ok(), "mother root over 512 B");
            MotherNbt.Mother m = MotherItem.read(root).orElseThrow();
            helper.assertTrue(Arrays.equals(m.genome().a(), before.genome().a())
                    && Arrays.equals(m.noise(), before.noise()) && m.grade() == before.grade(),
                    "inspection changed the root");
            helper.assertTrue(LensPanel.of(root).orElseThrow().bars().size() == traits, "mother root bars");
            // an inspected stack: the click is still taken, nothing changes
            p.getCooldowns().removeCooldown(ModItems.HAND_LENS.get());
            ItemStack copy = root.copy();
            click(p, 10);
            helper.assertTrue(ItemStack.matches(p.getInventory().getItem(10), copy)
                    && p.inventoryMenu.getCarried().is(ModItems.HAND_LENS.get()), "second click changed the root");
        } finally {
            p.discard();
        }
        helper.succeed();
    }

    private static ItemStack mother(GameTestHelper helper, ServerPlayer p) {
        PlantBlockEntity be = PlantFixtures.plant(PlantFixtures.place(helper, PlantFixtures.PLANT,
                FieldPlantBlock.HARVEST_STAGE), 81);
        helper.assertTrue(be.live(helper.getLevel()).isPresent(), "no plant");
        return MotherRootGameTests.drops(helper, PlantFixtures.PLANT, p).stream()
                .filter(s -> s.is(ModItems.MOTHER_ROOT.get())).findFirst().orElseThrow();
    }

    /** B12: the carried lens over a lot and a mother root in a hotbar slot, and in a chest, inspects on the server. */
    @GameTest(template = SmokeGameTests.FLOOR_3X3X3, batch = "d2_lens_panel")
    public static void lensClickWorksInTheHotbarAndInAChest(GameTestHelper helper) {
        ServerPlayer p = JournalGameTests.survival(helper, "b12_hotbar");
        try {
            ItemStack lens = new ItemStack(ModItems.HAND_LENS.get());
            // hotbar: menu slots 36-44 of the inventory menu
            p.getInventory().setItem(0, mother(helper, p));
            p.inventoryMenu.setCarried(lens.copy());
            click(p, 36);
            helper.assertTrue(LensPanel.inspected(p.getInventory().getItem(0)), "hotbar mother root not inspected");
            helper.assertTrue(p.inventoryMenu.getCarried().is(ModItems.HAND_LENS.get()), "lens left the cursor");
            p.getCooldowns().removeCooldown(ModItems.HAND_LENS.get());
            p.getInventory().setItem(8, PlantingGameTests.lot(helper.getLevel(), 4));
            click(p, 44);
            helper.assertTrue(LensPanel.inspected(p.getInventory().getItem(8))
                    && p.getInventory().getItem(8).getCount() == 4, "hotbar lot not inspected");

            // a chest (menu id 1): the stack is in the chest container
            p.getCooldowns().removeCooldown(ModItems.HAND_LENS.get());
            net.minecraft.world.SimpleContainer box = new net.minecraft.world.SimpleContainer(27);
            box.setItem(3, mother(helper, p));
            box.setItem(4, PlantingGameTests.lot(helper.getLevel(), 2));
            net.minecraft.world.inventory.ChestMenu chest = net.minecraft.world.inventory.ChestMenu.threeRows(1,
                    p.getInventory(), box);
            chest.setCarried(lens.copy());
            chest.clicked(3, 0, ClickType.PICKUP, p);
            helper.assertTrue(LensPanel.inspected(box.getItem(3)), "chest mother root not inspected");
            helper.assertTrue(chest.getCarried().is(ModItems.HAND_LENS.get()), "chest click swapped");
            p.getCooldowns().removeCooldown(ModItems.HAND_LENS.get());
            chest.clicked(4, 0, ClickType.PICKUP, p);
            helper.assertTrue(LensPanel.inspected(box.getItem(4)), "chest lot not inspected");
            // the same click in the player's own part of the chest screen
            p.getCooldowns().removeCooldown(ModItems.HAND_LENS.get());
            p.getInventory().setItem(20, mother(helper, p));
            chest.clicked(27 + 11, 0, ClickType.PICKUP, p);
            helper.assertTrue(LensPanel.inspected(p.getInventory().getItem(20)), "inventory part of a chest");
        } finally {
            p.discard();
        }
        helper.succeed();
    }

    /**
     * B12: a creative player on the server inspects too. The creative inventory screen never sends a
     * container click (it is client-only), so the real way there is the lens in one hand and the root in
     * the other.
     */
    @GameTest(template = SmokeGameTests.FLOOR_3X3X3, batch = "d2_lens_panel")
    public static void creativeServerClickAndTheOtherHandInspectARoot(GameTestHelper helper) {
        net.minecraft.world.InteractionHand main = net.minecraft.world.InteractionHand.MAIN_HAND;
        net.minecraft.world.InteractionHand off = net.minecraft.world.InteractionHand.OFF_HAND;
        ServerPlayer p = JournalGameTests.survival(helper, "b12_creative");
        try {
            p.gameMode.changeGameModeForPlayer(net.minecraft.world.level.GameType.CREATIVE);
            ItemStack root = mother(helper, p);
            // the server side of a click is not blocked in creative (only the client's creative screen is)
            p.getInventory().setItem(9, root.copy());
            p.inventoryMenu.setCarried(new ItemStack(ModItems.HAND_LENS.get()));
            click(p, 9);
            helper.assertTrue(LensPanel.inspected(p.getInventory().getItem(9)), "creative server click");

            // the lens in the main hand, the root in the other: a right-click
            p.getCooldowns().removeCooldown(ModItems.HAND_LENS.get());
            p.setItemInHand(main, new ItemStack(ModItems.HAND_LENS.get()));
            p.setItemInHand(off, root.copy());
            p.getItemInHand(main).use(helper.getLevel(), p, main);
            ItemStack after = p.getItemInHand(off);
            helper.assertTrue(LensPanel.inspected(after), "mother root in the other hand not inspected");
            helper.assertTrue(NbtLimits.mother(after.getTag().get(MotherNbt.KEY)).ok(), "mother root over 512 B");
            helper.assertTrue(LensPanel.of(after).orElseThrow().bars().size()
                    == PlantFixtures.carrot().def().quantitative().size(), "bars of the inspected root");
            // the lens in the off hand, the root in the main hand
            p.getCooldowns().removeCooldown(ModItems.HAND_LENS.get());
            p.setItemInHand(off, new ItemStack(ModItems.HAND_LENS.get()));
            p.setItemInHand(main, root.copy());
            p.getItemInHand(off).use(helper.getLevel(), p, off);
            helper.assertTrue(LensPanel.inspected(p.getItemInHand(main)), "root in the main hand not inspected");
        } finally {
            p.discard();
        }
        helper.succeed();
    }

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3, batch = "d2_lens_panel")
    public static void produceClickStaysVanillaAndPanelShowsGradeAndBitterness(GameTestHelper helper) {
        ServerPlayer p = JournalGameTests.survival(helper, "d2_produce");
        try {
            ItemStack food = ProduceStacks.create(ModItems.WILD_CARROT_ROOT.get(), 2, true, 3);
            LensPanel panel = LensPanel.of(food).orElseThrow();
            helper.assertTrue(panel.bars().size() == 1 && panel.bars().get(0).max() == 4
                    && panel.bars().get(0).step() == ProduceStacks.grade(food.getTag()), "grade bar " + panel.bars());
            helper.assertTrue(panel.sheet().flags().size() == 1, "bitter flag: " + panel.sheet().flags());
            helper.assertTrue(LensPanel.of(new ItemStack(ModItems.HAND_LENS.get())).isEmpty(), "panel of the lens");
            p.getInventory().setItem(9, food);
            p.inventoryMenu.setCarried(new ItemStack(ModItems.HAND_LENS.get()));
            click(p, 9);
            helper.assertTrue(p.getInventory().getItem(9).is(ModItems.HAND_LENS.get())
                    && p.inventoryMenu.getCarried().is(ModItems.WILD_CARROT_ROOT.get()), "produce click not vanilla");
            helper.assertTrue(!p.getCooldowns().isOnCooldown(ModItems.HAND_LENS.get()), "cooldown on produce");
        } finally {
            p.discard();
        }
        helper.succeed();
    }
}
