package io.github.slugblue.wildroots.gametest;

import io.github.slugblue.wildroots.WildRoots;
import io.github.slugblue.wildroots.core.registry.ModItems;
import io.github.slugblue.wildroots.core.safety.NbtLimits;
import io.github.slugblue.wildroots.item.LotNbt;
import io.github.slugblue.wildroots.item.LotStacks;
import io.github.slugblue.wildroots.item.PouchContents;
import io.github.slugblue.wildroots.item.SeedPouchItem;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;

/** WR-138: the seed pouch (GDD §5.3; docs/05-architecture.md §6.3; ADR-0007; SEC-09, SEC-11, SEC-17). */
@GameTestHolder(WildRoots.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SeedPouchGameTests {

    private SeedPouchGameTests() {
    }

    static ItemStack pouch() {
        return new ItemStack(ModItems.SEED_POUCH.get());
    }

    static int used(ItemStack pouch) {
        return SeedPouchItem.contents(pouch).map(PouchContents::used).orElse(-1);
    }

    static long lotId(ItemStack lot) {
        return ((LotNbt.Lot) LotStacks.read(lot)).lotId();
    }

    /** A chest menu over a fresh 27-slot container for {@code player}. */
    static ChestMenu menu(Player player, SimpleContainer c) {
        return ChestMenu.threeRows(1, player.getInventory(), c);
    }

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3)
    public static void putAndTakeLikeABundleLastInFirstOut(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockSurvivalPlayer();
        SimpleContainer c = new SimpleContainer(27);
        ChestMenu menu = menu(player, c);
        ItemStack a = PlantingGameTests.lot(level, 3);
        ItemStack b = PlantingGameTests.lot(level, 20);
        long idA = lotId(a);
        long idB = lotId(b);
        c.setItem(0, a);
        c.setItem(1, b);
        // pouch on the cursor, right click on a lot (overrideStackedOnOther)
        menu.setCarried(pouch());
        menu.clicked(0, 1, ClickType.PICKUP, player);
        helper.assertTrue(used(menu.getCarried()) == 3 && c.getItem(0).isEmpty(), "lot A: " + used(menu.getCarried()));
        menu.clicked(1, 1, ClickType.PICKUP, player);
        helper.assertTrue(used(menu.getCarried()) == 16, "capacity " + used(menu.getCarried()));
        helper.assertTrue(c.getItem(1).getCount() == 7, "rest of lot B stays: " + c.getItem(1).getCount());
        // full pouch: nothing more goes in
        c.setItem(2, PlantingGameTests.lot(level, 1));
        menu.clicked(2, 1, ClickType.PICKUP, player);
        helper.assertTrue(used(menu.getCarried()) == 16 && c.getItem(2).getCount() == 1, "overfull pouch took a seed");
        // entries never merge, even of one lot
        List<CompoundTag> e = SeedPouchItem.contents(menu.getCarried()).orElseThrow().entries();
        helper.assertTrue(e.stream().filter(x -> x.getLong("id") == idA).count() == 3, "entries of A merged");
        // right click on an empty slot takes the LAST put in
        menu.clicked(5, 1, ClickType.PICKUP, player);
        helper.assertTrue(lotId(c.getItem(5)) == idB && c.getItem(5).getCount() == 1, "LIFO into a slot");
        helper.assertTrue(used(menu.getCarried()) == 15, "after taking one");
        // pouch in a slot, empty cursor: take the last onto the cursor (overrideOtherStackedOnMe)
        c.setItem(10, menu.getCarried());
        menu.setCarried(ItemStack.EMPTY);
        menu.clicked(10, 1, ClickType.PICKUP, player);
        helper.assertTrue(lotId(menu.getCarried()) == idB && used(c.getItem(10)) == 14, "LIFO onto the cursor");
        // lot on the cursor onto the pouch in the slot: goes in
        menu.clicked(10, 1, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().isEmpty() && used(c.getItem(10)) == 15, "lot onto the pouch");
        helper.succeed();
    }

    /** 16 lots at the 1 KiB limit always fit; over the 17 KiB guard (only bad data) the insertion is refused. */
    @GameTest(template = SmokeGameTests.FLOOR_3X3X3)
    public static void sixteenMaximalLotsFitAndTheGuardRefusesWithoutBreakingThePouch(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockSurvivalPlayer();
        SimpleContainer c = new SimpleContainer(27);
        ChestMenu menu = menu(player, c);
        menu.setCarried(pouch());
        for (int i = 0; i < 16; i++) {
            ItemStack lot = PlantingGameTests.lot(level, 1);
            CompoundTag root = lot.getTag().getCompound(LotNbt.KEY);
            ListTag disp = root.getList("disp", 8);
            while (NbtLimits.lot(root).ok()) {
                disp.add(StringTag.valueOf("tooltip.lot.pad\u001Fxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"));
            }
            disp.remove(disp.size() - 1);
            helper.assertTrue(NbtLimits.lot(root).bytes() > NbtLimits.LOT_MAX_BYTES - 50, "lot not at the limit");
            c.setItem(0, lot);
            menu.clicked(0, 1, ClickType.PICKUP, player);
            helper.assertTrue(c.getItem(0).isEmpty(), "maximal lot " + i + " refused");
        }
        helper.assertTrue(used(menu.getCarried()) == 16, "used " + used(menu.getCarried()));

        // bad data: a pouch padded to just under 17 KiB refuses a lot, and still gives its entry back
        ItemStack bad = pouch();
        menu.setCarried(bad);
        c.setItem(0, PlantingGameTests.lot(level, 1));
        menu.clicked(0, 1, ClickType.PICKUP, player);
        CompoundTag stored = menu.getCarried().getTag().getCompound(PouchContents.KEY);
        stored.putString("junk", "j".repeat(NbtLimits.POUCH_MAX_BYTES - 600));
        c.setItem(1, PlantingGameTests.lot(level, 1));
        menu.clicked(1, 1, ClickType.PICKUP, player);
        helper.assertTrue(c.getItem(1).getCount() == 1 && used(menu.getCarried()) == 1, "insertion over the guard accepted");
        menu.clicked(5, 1, ClickType.PICKUP, player);
        helper.assertTrue(!c.getItem(5).isEmpty() && used(menu.getCarried()) == 0, "pouch became inert");
        helper.succeed();
    }

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3)
    public static void onlyPouchableLotsNeverAPouch(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockSurvivalPlayer();
        SimpleContainer c = new SimpleContainer(27);
        ChestMenu menu = menu(player, c);
        menu.setCarried(pouch());
        ItemStack inert = PlantingGameTests.lot(level, 2);
        inert.getTag().getCompound(LotNbt.KEY).putInt("v", 99);
        ItemStack mother = new ItemStack(ModItems.MOTHER_ROOT.get());
        for (ItemStack s : List.of(new ItemStack(Items.DIRT, 5), new ItemStack(Items.WHEAT_SEEDS, 5), inert, mother, pouch())) {
            c.setItem(0, s.copy());
            menu.clicked(0, 1, ClickType.PICKUP, player);
            helper.assertTrue(used(menu.getCarried()) == 0 && c.getItem(0).getCount() == s.getCount(), "took " + s);
        }
        // a pouch on the cursor onto a pouch in a slot
        c.setItem(3, pouch());
        menu.setCarried(pouch());
        menu.clicked(3, 1, ClickType.PICKUP, player);
        helper.assertTrue(SeedPouchItem.contents(c.getItem(3)).map(PouchContents::used).orElse(0) == 0
                && SeedPouchItem.contents(menu.getCarried()).map(PouchContents::used).orElse(0) == 0, "pouch in pouch");
        helper.succeed();
    }

    // ------------------------------------------------------------------ SEC-11 (S14 paths)

    static boolean isEmpty(Container c) {
        for (int i = 0; i < c.getContainerSize(); i++) {
            if (!c.getItem(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** Menu paths into a shulker box: PICKUP left/right, QUICK_MOVE, SWAP hotbar/offhand, QUICK_CRAFT. */
    static String shulkerMenuPaths(GameTestHelper helper, ItemStack probe) {
        StringBuilder placed = new StringBuilder();
        Player player = helper.makeMockSurvivalPlayer();
        for (int button : new int[] {0, 1}) {
            SimpleContainer box = new SimpleContainer(27);
            ShulkerBoxMenu menu = new ShulkerBoxMenu(1, player.getInventory(), box);
            menu.setCarried(probe.copy());
            menu.clicked(0, button, ClickType.PICKUP, player);
            if (!isEmpty(box)) {
                placed.append("PICKUP").append(button).append(' ');
            }
            if (menu.getCarried().isEmpty() && isEmpty(box)) {
                placed.append("LOST ");
            }
        }
        player.getInventory().clearContent();
        SimpleContainer box = new SimpleContainer(27);
        ShulkerBoxMenu menu = new ShulkerBoxMenu(1, player.getInventory(), box);
        player.getInventory().setItem(9, probe.copy());
        menu.clicked(27, 0, ClickType.QUICK_MOVE, player);
        if (!isEmpty(box)) {
            placed.append("QUICK_MOVE ");
        }
        player.getInventory().clearContent();
        box = new SimpleContainer(27);
        menu = new ShulkerBoxMenu(1, player.getInventory(), box);
        player.getInventory().setItem(0, probe.copy());
        menu.clicked(0, 0, ClickType.SWAP, player);
        if (!isEmpty(box)) {
            placed.append("SWAP ");
        }
        player.getInventory().clearContent();
        box = new SimpleContainer(27);
        menu = new ShulkerBoxMenu(1, player.getInventory(), box);
        player.getInventory().offhand.set(0, probe.copy());
        menu.clicked(0, 40, ClickType.SWAP, player);
        if (!isEmpty(box)) {
            placed.append("SWAP_OFFHAND ");
        }
        player.getInventory().clearContent();
        box = new SimpleContainer(27);
        menu = new ShulkerBoxMenu(1, player.getInventory(), box);
        menu.setCarried(probe.copy());
        menu.clicked(-999, AbstractContainerMenu.getQuickcraftMask(0, 0), ClickType.QUICK_CRAFT, player);
        menu.clicked(0, AbstractContainerMenu.getQuickcraftMask(1, 0), ClickType.QUICK_CRAFT, player);
        menu.clicked(1, AbstractContainerMenu.getQuickcraftMask(1, 0), ClickType.QUICK_CRAFT, player);
        menu.clicked(-999, AbstractContainerMenu.getQuickcraftMask(2, 0), ClickType.QUICK_CRAFT, player);
        if (!isEmpty(box)) {
            placed.append("QUICK_CRAFT ");
        }
        return placed.toString().trim();
    }

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3)
    public static void pouchNeverEntersAShulkerBoxMenu(GameTestHelper helper) {
        String pouch = shulkerMenuPaths(helper, pouch());
        String dirt = shulkerMenuPaths(helper, new ItemStack(Items.DIRT, 16));
        helper.assertTrue(pouch.isEmpty(), "pouch entered the shulker menu via " + pouch);
        helper.assertTrue(dirt.split(" ").length == 6, "control: dirt only via " + dirt);
        helper.succeed();
    }

    static void hopperInto(GameTestHelper helper, ItemStack probe, boolean expectInserted) {
        BlockPos boxPos = new BlockPos(1, 1, 1);
        BlockPos hopperPos = new BlockPos(1, 2, 1);
        helper.setBlock(boxPos, Blocks.SHULKER_BOX);
        helper.setBlock(hopperPos, Blocks.HOPPER);
        HopperBlockEntity hopper = (HopperBlockEntity) helper.getBlockEntity(hopperPos);
        hopper.setItem(0, probe);
        helper.runAfterDelay(40, () -> {
            ShulkerBoxBlockEntity box = (ShulkerBoxBlockEntity) helper.getBlockEntity(boxPos);
            helper.assertTrue(box.isEmpty() != expectInserted, "hopper inserted=" + !box.isEmpty());
            if (!expectInserted) {
                helper.assertTrue(!hopper.isEmpty(), "pouch vanished from the hopper");
            }
            helper.succeed();
        });
    }

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3, timeoutTicks = 100)
    public static void hopperDoesNotPutAPouchIntoAShulkerBox(GameTestHelper helper) {
        hopperInto(helper, pouch(), false);
    }

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3, timeoutTicks = 100)
    public static void hopperControlWithDirt(GameTestHelper helper) {
        hopperInto(helper, new ItemStack(Items.DIRT), true);
    }

    static void dropperInto(GameTestHelper helper, ItemStack probe, boolean expectInserted) {
        BlockPos dropperPos = new BlockPos(0, 1, 1);
        BlockPos boxPos = new BlockPos(1, 1, 1);
        helper.setBlock(boxPos, Blocks.SHULKER_BOX);
        helper.setBlock(dropperPos, Blocks.DROPPER.defaultBlockState().setValue(DispenserBlock.FACING, Direction.EAST));
        DispenserBlockEntity dropper = (DispenserBlockEntity) helper.getBlockEntity(dropperPos);
        dropper.setItem(0, probe);
        helper.setBlock(new BlockPos(0, 2, 1), Blocks.REDSTONE_BLOCK);
        helper.runAfterDelay(20, () -> {
            ShulkerBoxBlockEntity box = (ShulkerBoxBlockEntity) helper.getBlockEntity(boxPos);
            helper.assertTrue(box.isEmpty() != expectInserted, "dropper inserted=" + !box.isEmpty());
            helper.succeed();
        });
    }

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3, timeoutTicks = 100)
    public static void dropperDoesNotPutAPouchIntoAShulkerBox(GameTestHelper helper) {
        dropperInto(helper, pouch(), false);
    }

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3, timeoutTicks = 100)
    public static void dropperControlWithDirt(GameTestHelper helper) {
        dropperInto(helper, new ItemStack(Items.DIRT), true);
    }

    /**
     * Capabilities of the shulker block entity (every side) and of the
     * shulker item refuse the pouch. {@code Container#canPlaceItem} and a
     * third-party {@code InvWrapper} are the known residual risk (S14) and
     * are not asserted as refusals.
     */
    @GameTest(template = SmokeGameTests.FLOOR_3X3X3)
    public static void shulkerCapabilitiesRefuseThePouch(GameTestHelper helper) {
        BlockPos boxPos = new BlockPos(1, 1, 1);
        helper.setBlock(boxPos, Blocks.SHULKER_BOX);
        ShulkerBoxBlockEntity box = (ShulkerBoxBlockEntity) helper.getBlockEntity(boxPos);
        for (Direction d : new Direction[] {null, Direction.UP, Direction.DOWN, Direction.NORTH}) {
            IItemHandler h = box.getCapability(ForgeCapabilities.ITEM_HANDLER, d).orElseThrow(IllegalStateException::new);
            helper.assertTrue(!h.insertItem(0, pouch(), true).isEmpty(), "block capability accepted the pouch, side " + d);
        }
        IItemHandler item = new ItemStack(Items.SHULKER_BOX).getCapability(ForgeCapabilities.ITEM_HANDLER)
                .orElseThrow(IllegalStateException::new);
        helper.assertTrue(!item.insertItem(0, pouch(), true).isEmpty(), "item capability accepted the pouch");
        helper.assertTrue(item.insertItem(0, new ItemStack(Items.DIRT), true).isEmpty(), "control: dirt refused");
        helper.succeed();
    }

    /** Bundle in both directions: the pouch does not go in, and it is not lost. */
    @GameTest(template = SmokeGameTests.FLOOR_3X3X3)
    public static void bundleAndPouchDoNotNest(GameTestHelper helper) {
        Player player = helper.makeMockSurvivalPlayer();
        SimpleContainer c = new SimpleContainer(27);
        ChestMenu menu = menu(player, c);
        // pouch on the cursor onto a bundle
        c.setItem(0, new ItemStack(Items.BUNDLE));
        menu.setCarried(pouch());
        menu.clicked(0, 1, ClickType.PICKUP, player);
        CompoundTag t = c.getItem(0).getTag();
        helper.assertTrue(t == null || t.getList("Items", 10).isEmpty(), "pouch went into the bundle");
        helper.assertTrue(menu.getCarried().is(ModItems.SEED_POUCH.get()) || c.getItem(0).is(ModItems.SEED_POUCH.get()),
                "pouch lost");
        // bundle on the cursor onto a pouch
        c.clearContent();
        c.setItem(0, pouch());
        menu.setCarried(new ItemStack(Items.BUNDLE));
        menu.clicked(0, 1, ClickType.PICKUP, player);
        CompoundTag bt = menu.getCarried().is(Items.BUNDLE) ? menu.getCarried().getTag() : c.getItem(0).getTag();
        helper.assertTrue(bt == null || bt.getList("Items", 10).isEmpty(), "bundle took the pouch");
        helper.assertTrue(c.getItem(0).is(ModItems.SEED_POUCH.get()) || menu.getCarried().is(ModItems.SEED_POUCH.get()),
                "pouch lost");
        // and the bundle never goes into the pouch
        helper.assertTrue(SeedPouchItem.contents(c.getItem(0).is(ModItems.SEED_POUCH.get()) ? c.getItem(0)
                : menu.getCarried()).map(PouchContents::used).orElse(0) == 0, "bundle in the pouch");
        helper.succeed();
    }

    /** A dropped pouch destroyed (lava, fire, cactus) drops its contents exactly once. */
    @GameTest(template = SmokeGameTests.FLOOR_3X3X3, timeoutTicks = 100)
    public static void destroyedDroppedPouchDropsItsContentsOnce(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockSurvivalPlayer();
        SimpleContainer c = new SimpleContainer(27);
        ChestMenu menu = menu(player, c);
        menu.setCarried(pouch());
        c.setItem(0, PlantingGameTests.lot(level, 3));
        menu.clicked(0, 1, ClickType.PICKUP, player);
        helper.assertTrue(used(menu.getCarried()) == 3, "set-up");
        ItemEntity e = helper.spawnItem(ModItems.SEED_POUCH.get(), 1.5f, 2.5f, 1.5f);
        e.setItem(menu.getCarried().copy());
        e.getItem().getItem().onDestroyed(e);
        e.getItem().getItem().onDestroyed(e);
        e.hurt(level.damageSources().inFire(), 100);
        helper.runAfterDelay(2, () -> {
            AABB area = new AABB(helper.absolutePos(new BlockPos(1, 2, 1))).inflate(3);
            int seeds = level.getEntitiesOfClass(ItemEntity.class, area).stream().map(ItemEntity::getItem)
                    .filter(s -> s.is(ModItems.SEED_LOT.get())).mapToInt(ItemStack::getCount).sum();
            helper.assertTrue(seeds == 3, "dropped seeds " + seeds);
            helper.assertTrue(e.isRemoved(), "pouch entity survived");
            helper.succeed();
        });
    }

    /** WR-111 transfer: pouch entries keep their genomes through save/load and migrate when taken out (§5.3). */
    @GameTest(template = SmokeGameTests.FLOOR_3X3X3, batch = "wr138_migration", timeoutTicks = 200)
    public static void pouchEntriesMigrateAfterALayoutChange(GameTestHelper helper) {
        String sp = "wildroots:gt_migrating_pouch";
        var oldDef = PlantFixtures.carrotAs(sp, q -> q);
        var newDef = PlantFixtures.carrotAs(sp, q -> q.stream()
                .map(t -> t.id().equals(io.github.slugblue.wildroots.genetics.model.TraitId.of("yield"))
                        ? new io.github.slugblue.wildroots.genetics.api.QuantTrait(t.id(), 8, t.sigmaEnv(), t.vigorAffected()) : t)
                .toList());
        var oldLayout = io.github.slugblue.wildroots.genetics.model.GenomeLayout.of(oldDef);
        var newLayout = io.github.slugblue.wildroots.genetics.model.GenomeLayout.of(newDef);
        var gs = io.github.slugblue.wildroots.data.Definitions.current().genetics();
        var m = gs.encode(PlantFixtures.genome(138));
        var mother = new io.github.slugblue.wildroots.genetics.codec.GenomeRecord(m.v(), sp, m.lh(), m.a());
        var fathers = HarvestGameTests.fathers(2);
        CompoundTag root = LotStacks.seedsTag(new LotStacks.Parent(sp, java.util.Optional.empty(), mother,
                java.util.Optional.empty(), 0, 0), fathers, 0.5f, 0, 9L, LotStacks.RareClaim.NONE);
        Player player = helper.makeMockSurvivalPlayer();
        SimpleContainer c = new SimpleContainer(27);
        ChestMenu menu = menu(player, c);
        menu.setCarried(pouch());
        c.setItem(0, LotStacks.stack(root, 2));
        menu.clicked(0, 1, ClickType.PICKUP, player);
        ItemStack saved = ItemStack.of(menu.getCarried().save(new CompoundTag()));
        var original = io.github.slugblue.wildroots.data.Definitions.current();
        try {
            io.github.slugblue.wildroots.data.Definitions.publish(PlantFixtures.withSpecies(original, newDef,
                    new io.github.slugblue.wildroots.genetics.codec.LayoutDescriptor(sp, oldLayout.hash(), oldLayout.canonical())));
            menu.setCarried(saved);
            menu.clicked(5, 1, ClickType.PICKUP, player);
            LotNbt.Lot lot = (LotNbt.Lot) LotStacks.read(c.getItem(5));
            var rec = LotStacks.record(lot, io.github.slugblue.wildroots.data.Definitions.current()).orElseThrow();
            helper.assertTrue(rec.mother().layout().equals(newLayout), "pouch entry mother not migrated");
            rec.fathers().forEach(f -> helper.assertTrue(f.genome().layout().equals(newLayout), "father not migrated"));
            helper.assertTrue(rec.fathers().size() == 2 && used(menu.getCarried()) == 1, "entries");
        } finally {
            io.github.slugblue.wildroots.data.Definitions.publish(original);
        }
        helper.succeed();
    }

    /** A slot that refuses everything (armour, fuel, output). */
    static final class RefusingSlot extends net.minecraft.world.inventory.Slot {
        RefusingSlot(Container c) {
            super(c, 0, 0, 0);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }

    /** M2: an entry that accepts() would not take back (inert) stays in the pouch when the slot refuses it. */
    @GameTest(template = SmokeGameTests.FLOOR_3X3X3)
    public static void refusingSlotKeepsAnInertEntry(GameTestHelper helper) {
        Player player = helper.makeMockSurvivalPlayer();
        CompoundTag lot = PlantingGameTests.lot(helper.getLevel(), 1).getTag().getCompound(LotNbt.KEY).copy();
        lot.putInt("v", 2);
        CompoundTag entry = new CompoundTag();
        entry.put("l", lot);
        entry.putByte("c", (byte) 1);
        ListTag e = new ListTag();
        e.add(entry);
        CompoundTag stored = new CompoundTag();
        stored.putInt("v", 1);
        stored.put("e", e);
        ItemStack p = pouch();
        p.getOrCreateTag().put(PouchContents.KEY, stored);
        CompoundTag before = p.getTag().copy();
        SimpleContainer c = new SimpleContainer(1);
        RefusingSlot slot = new RefusingSlot(c);
        SeedPouchItem.stackedOnOther(p, slot, net.minecraft.world.inventory.ClickAction.SECONDARY, player, false);
        helper.assertTrue(p.getTag().equals(before), "pouch changed: " + p.getTag());
        helper.assertTrue(c.getItem(0).isEmpty(), "refusing slot got an item");
        helper.succeed();
    }

    /** The 17 KiB guard also holds on the cursor path (lot on the cursor onto the pouch in a slot). */
    @GameTest(template = SmokeGameTests.FLOOR_3X3X3)
    public static void guardHoldsOnTheCursorPath(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockSurvivalPlayer();
        SimpleContainer c = new SimpleContainer(27);
        ChestMenu menu = menu(player, c);
        menu.setCarried(pouch());
        c.setItem(0, PlantingGameTests.lot(level, 1));
        menu.clicked(0, 1, ClickType.PICKUP, player);
        ItemStack p = menu.getCarried();
        p.getTag().getCompound(PouchContents.KEY).putString("junk", "j".repeat(NbtLimits.POUCH_MAX_BYTES - 600));
        CompoundTag before = p.getTag().copy();
        c.setItem(4, p);
        menu.setCarried(PlantingGameTests.lot(level, 1));
        menu.clicked(4, 1, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().getCount() == 1, "the lot left the cursor");
        helper.assertTrue(c.getItem(4).getTag().equals(before), "the pouch changed");
        helper.succeed();
    }

    /** SEC-17: on the client a pouch click only claims the click; pouch, slot and cursor stay as they are. */
    @GameTest(template = SmokeGameTests.FLOOR_3X3X3)
    public static void clientSidePouchCallsChangeNothing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockSurvivalPlayer();
        SimpleContainer c = new SimpleContainer(27);
        ChestMenu menu = menu(player, c);
        menu.setCarried(pouch());
        c.setItem(0, PlantingGameTests.lot(level, 2));
        menu.clicked(0, 1, ClickType.PICKUP, player);
        ItemStack p = menu.getCarried().copy();
        CompoundTag before = p.getTag().copy();
        net.minecraft.world.inventory.Slot empty = menu.getSlot(3);
        net.minecraft.world.inventory.Slot full = menu.getSlot(5);
        c.setItem(5, PlantingGameTests.lot(level, 3));
        ItemStack fullBefore = c.getItem(5).copy();
        helper.assertTrue(SeedPouchItem.stackedOnOther(p, empty, net.minecraft.world.inventory.ClickAction.SECONDARY, player, true),
                "client did not claim the click");
        helper.assertTrue(SeedPouchItem.stackedOnOther(p, full, net.minecraft.world.inventory.ClickAction.SECONDARY, player, true),
                "client did not claim the click");
        ItemStack[] cursor = {PlantingGameTests.lot(level, 2)};
        ItemStack cursorBefore = cursor[0].copy();
        net.minecraft.world.entity.SlotAccess access = new net.minecraft.world.entity.SlotAccess() {
            @Override
            public ItemStack get() {
                return cursor[0];
            }

            @Override
            public boolean set(ItemStack s) {
                cursor[0] = s;
                return true;
            }
        };
        SeedPouchItem.otherStackedOnMe(p, cursor[0], full, net.minecraft.world.inventory.ClickAction.SECONDARY, player, access, true);
        SeedPouchItem.otherStackedOnMe(p, ItemStack.EMPTY, full, net.minecraft.world.inventory.ClickAction.SECONDARY, player, access, true);
        helper.assertTrue(p.getTag().equals(before), "client changed the pouch");
        helper.assertTrue(empty.getItem().isEmpty() && ItemStack.matches(c.getItem(5), fullBefore), "client changed a slot");
        helper.assertTrue(ItemStack.matches(cursor[0], cursorBefore), "client changed the cursor");
        helper.succeed();
    }

    /** mayPlace says yes, but the insert itself refuses (review 2, item 4): the restore branch. */
    static final class RefusingInsertSlot extends net.minecraft.world.inventory.Slot {
        RefusingInsertSlot(Container c) {
            super(c, 0, 0, 0);
        }

        @Override
        public ItemStack safeInsert(ItemStack stack) {
            return stack;
        }
    }

    static byte[] bytes(CompoundTag t) throws java.io.IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        net.minecraft.nbt.NbtIo.write(t, new java.io.DataOutputStream(out));
        return out.toByteArray();
    }

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3)
    public static void refusedInsertRestoresThePouchByteForByte(GameTestHelper helper) throws java.io.IOException {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockSurvivalPlayer();
        SimpleContainer c = new SimpleContainer(27);
        ChestMenu menu = menu(player, c);
        menu.setCarried(pouch());
        c.setItem(0, PlantingGameTests.lot(level, 2));
        menu.clicked(0, 1, ClickType.PICKUP, player);
        ItemStack p = menu.getCarried();
        byte[] before = bytes(p.getTag());
        SimpleContainer one = new SimpleContainer(1);
        RefusingInsertSlot slot = new RefusingInsertSlot(one);
        SeedPouchItem.stackedOnOther(p, slot, net.minecraft.world.inventory.ClickAction.SECONDARY, player, false);
        helper.assertTrue(java.util.Arrays.equals(before, bytes(p.getTag())), "pouch tag not restored byte for byte");
        helper.assertTrue(one.getItem(0).isEmpty(), "slot changed");
        helper.assertTrue(used(p) == 2, "entries " + used(p));
        helper.succeed();
    }

    /** A slot whose take throws in the middle of an insertion. */
    static final class ThrowingTakeSlot extends net.minecraft.world.inventory.Slot {
        ThrowingTakeSlot(Container c) {
            super(c, 0, 0, 0);
        }

        @Override
        public ItemStack safeTake(int count, int decrement, Player player) {
            throw new IllegalStateException("test failure in the middle of a pouch click");
        }
    }

    /** Review 2, item 2: a failing click restores the pouch and the slot (and claims the click). */
    @GameTest(template = SmokeGameTests.FLOOR_3X3X3)
    public static void failingClickRestoresPouchAndSlot(GameTestHelper helper) throws java.io.IOException {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockSurvivalPlayer();
        ItemStack p = pouch();
        SimpleContainer one = new SimpleContainer(1);
        one.setItem(0, PlantingGameTests.lot(level, 3));
        ItemStack slotBefore = one.getItem(0).copy();
        ThrowingTakeSlot slot = new ThrowingTakeSlot(one);
        boolean handled = false;
        try {
            handled = p.getItem().overrideStackedOnOther(p, slot, net.minecraft.world.inventory.ClickAction.SECONDARY, player);
        } catch (RuntimeException e) {
            handled = true; // strict mode rethrows after onFailure; the server fallback is true
        }
        helper.assertTrue(handled, "the click was given back to vanilla");
        helper.assertTrue(used(p) == 0 && p.getTag() == null, "pouch not restored: " + p.getTag());
        helper.assertTrue(ItemStack.matches(one.getItem(0), slotBefore), "slot not restored");
        helper.succeed();
    }
}
