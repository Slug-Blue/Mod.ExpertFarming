package io.github.slugblue.wildroots.gametest;

import io.github.slugblue.wildroots.WildRoots;
import io.github.slugblue.wildroots.core.registry.ModItems;
import io.github.slugblue.wildroots.item.LotNbt;
import io.github.slugblue.wildroots.item.LotStacks;
import io.github.slugblue.wildroots.item.PouchContents;
import io.github.slugblue.wildroots.item.SeedPouchItem;
import io.github.slugblue.wildroots.plant.FieldPlantBlock;
import io.github.slugblue.wildroots.plant.PlantBlockEntity;
import io.github.slugblue.wildroots.plant.PlantService;
import io.github.slugblue.wildroots.plant.PlantState;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Batch 3 test pass, T-T1 / SEC-09 / SEC-21: stacks a modified client can
 * put into a slot through the creative set-slot packet. Every stack here
 * goes through {@link ServerboundSetCreativeModeSlotPacket} write and read,
 * so it has exactly the shape the server decodes (count is a signed byte,
 * any NBT within the packet quota). The server must neutralise them: no
 * duplication, no pouch inside a pouch, no item other than a lot out of a
 * pouch, no forged {@code rare_source} passed on.
 */
@GameTestHolder(WildRoots.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ForgedStackGameTests {

    private ForgedStackGameTests() {
    }

    /** {@code stack} as the server decodes it from a creative set-slot packet. */
    static ItemStack viaCreativePacket(ItemStack stack) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            new ServerboundSetCreativeModeSlotPacket(36, stack).write(buf);
            return new ServerboundSetCreativeModeSlotPacket(buf).getItem();
        } finally {
            buf.release();
        }
    }

    static CompoundTag entry(CompoundTag lotRoot) {
        CompoundTag e = new CompoundTag();
        e.put("l", lotRoot);
        e.putByte("c", (byte) 1);
        return e;
    }

    /** A pouch whose stored entries are exactly {@code roots}, first put in first. */
    static ItemStack forgedPouch(List<CompoundTag> roots) {
        ListTag e = new ListTag();
        roots.forEach(r -> e.add(entry(r)));
        CompoundTag stored = new CompoundTag();
        stored.putInt("v", 1);
        stored.put("e", e);
        ItemStack p = SeedPouchGameTests.pouch();
        p.getOrCreateTag().put(PouchContents.KEY, stored);
        return viaCreativePacket(p);
    }

    static CompoundTag root(ItemStack lot) {
        return lot.getTag().getCompound(LotNbt.KEY).copy();
    }

    static int seedLotsAround(GameTestHelper helper) {
        AABB area = new AABB(helper.absolutePos(new BlockPos(1, 2, 1))).inflate(4);
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, area).stream().map(ItemEntity::getItem)
                .filter(s -> s.is(ModItems.SEED_LOT.get())).mapToInt(ItemStack::getCount).sum();
    }

    /** A lot with count 127 (more than a stack): the pouch takes 16 at most, planting takes 1, nothing is created. */
    @GameTest(template = SmokeGameTests.FLOOR_3X3X3)
    public static void lotWithAnAbsurdCountIsBoundedByPouchAndPlanting(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockSurvivalPlayer();
        ItemStack big = PlantingGameTests.lot(level, 1);
        big.setCount(127);
        ItemStack forged = viaCreativePacket(big);
        helper.assertTrue(forged.getCount() == 127, "packet shape lost the count: " + forged.getCount());
        long id = SeedPouchGameTests.lotId(forged);

        SimpleContainer c = new SimpleContainer(27);
        ChestMenu menu = SeedPouchGameTests.menu(player, c);
        // the player inventory keeps the count as decoded (a SimpleContainer would clamp it to 64)
        player.getInventory().setItem(9, forged.copy());
        menu.setCarried(SeedPouchGameTests.pouch());
        menu.clicked(27, 1, ClickType.PICKUP, player); // menu slot 27 = inventory slot 9
        int in = SeedPouchGameTests.used(menu.getCarried());
        int left = player.getInventory().getItem(9).getCount();
        helper.assertTrue(in == PouchContents.CAPACITY, "pouch took " + in);
        helper.assertTrue(in + left == 127, "seeds not conserved: " + in + " + " + left);
        // the other path: the forged stack on the cursor onto the pouch in a slot
        c.setItem(1, SeedPouchGameTests.pouch());
        menu.setCarried(forged.copy());
        menu.clicked(1, 1, ClickType.PICKUP, player);
        helper.assertTrue(SeedPouchGameTests.used(c.getItem(1)) == 16 && menu.getCarried().getCount() == 111,
                "cursor path: " + SeedPouchGameTests.used(c.getItem(1)) + " / " + menu.getCarried().getCount());
        helper.assertTrue(SeedPouchItem.contents(c.getItem(1)).orElseThrow().entries().stream()
                .allMatch(e -> e.getLong("id") == id), "foreign entry");

        // planting takes exactly one seed, keyed by the forged count
        PlantFixtures.farmland(helper, PlantFixtures.PLANT);
        ItemStack hand = forged.copy();
        helper.assertTrue(PlantingGameTests.use(helper, player, hand, PlantFixtures.PLANT).consumesAction(), "not planted");
        helper.assertTrue(hand.getCount() == 126, "count after planting " + hand.getCount());
        PlantBlockEntity be = (PlantBlockEntity) helper.getBlockEntity(PlantFixtures.PLANT);
        LotNbt.Lot lot = SeedLotGameTests.lot(forged);
        PlantService.Seedling expected = PlantService.seedling(level, lot, 127,
                helper.absolutePos(PlantFixtures.PLANT)).orElseThrow();
        helper.assertTrue(be.live(level).orElseThrow().genome().equals(expected.genome()), "seedling not keyed by 127");
        // the packet decoder turns a non-positive count into an empty stack
        ItemStack zero = PlantingGameTests.lot(level, 1);
        zero.setCount(0);
        helper.assertTrue(viaCreativePacket(zero).isEmpty() && !SeedPouchItem.accepts(viaCreativePacket(zero)),
                "empty forged stack accepted");
        helper.succeed();
    }

    /** A pouch with 17 entries is inert: it neither takes nor gives, its tag never changes, it drops nothing. */
    @GameTest(template = SmokeGameTests.FLOOR_3X3X3, timeoutTicks = 100)
    public static void pouchWithSeventeenEntriesIsInertAndUntouched(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockSurvivalPlayer();
        CompoundTag lotRoot = root(PlantingGameTests.lot(level, 1));
        ItemStack forged = forgedPouch(java.util.Collections.nCopies(17, lotRoot));
        CompoundTag before = forged.getTag().copy();
        helper.assertTrue(SeedPouchItem.contents(forged).isEmpty(), "17 entries read");

        SimpleContainer c = new SimpleContainer(27);
        ChestMenu menu = SeedPouchGameTests.menu(player, c);
        menu.setCarried(forged);
        c.setItem(0, PlantingGameTests.lot(level, 2));
        menu.clicked(0, 1, ClickType.PICKUP, player); // insert
        menu.clicked(5, 1, ClickType.PICKUP, player); // take out into an empty slot
        helper.assertTrue(c.getItem(0).getCount() == 2 && c.getItem(5).isEmpty(), "inert pouch acted");
        helper.assertTrue(menu.getCarried().getTag().equals(before), "inert pouch rewritten");
        c.setItem(9, menu.getCarried());
        menu.setCarried(ItemStack.EMPTY);
        menu.clicked(9, 1, ClickType.PICKUP, player); // empty cursor on the pouch in a slot
        helper.assertTrue(menu.getCarried().isEmpty() && c.getItem(9).getTag().equals(before), "gave from inert pouch");
        helper.assertTrue(!ModItems.SEED_POUCH.get().isBarVisible(c.getItem(9)), "bar on an inert pouch");

        int around = seedLotsAround(helper);
        ItemEntity e = helper.spawnItem(ModItems.SEED_POUCH.get(), 1.5f, 2.5f, 1.5f);
        e.setItem(c.getItem(9).copy());
        e.getItem().getItem().onDestroyed(e);
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(seedLotsAround(helper) == around, "a destroyed inert pouch dropped lots");
            helper.succeed();
        });
    }

    /**
     * A pouch entry shaped like a whole pouch (item stack or pouch root) and
     * a lot stack that also carries a pouch payload: nothing ever comes out
     * as a pouch, and only the lot root goes in.
     */
    @GameTest(template = SmokeGameTests.FLOOR_3X3X3)
    public static void pouchInsidePouchNeverMaterialises(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockSurvivalPlayer();
        ItemStack inner = forgedPouch(List.of(root(PlantingGameTests.lot(level, 1))));
        CompoundTag asItem = inner.save(new CompoundTag()); // {id: wildroots:seed_pouch, Count, tag}
        CompoundTag asRoot = inner.getTag().getCompound(PouchContents.KEY).copy();
        CompoundTag good = root(PlantingGameTests.lot(level, 1));
        // a real pouch (as item or as root) nested in an entry breaks the depth limit 7: the outer pouch is inert
        for (CompoundTag nested : List.of(asItem, asRoot)) {
            ItemStack deep = forgedPouch(List.of(nested, good));
            helper.assertTrue(SeedPouchItem.contents(deep).isEmpty(), "nested pouch read: " + nested);
        }
        // a shallow pouch-shaped entry (no contents) stays readable and must still never come out as a pouch
        CompoundTag shallowItem = new ItemStack(ModItems.SEED_POUCH.get()).save(new CompoundTag());
        CompoundTag shallowRoot = new CompoundTag();
        shallowRoot.putInt("v", 1);
        shallowRoot.put("e", new ListTag());
        ItemStack outer = forgedPouch(List.of(shallowItem, shallowRoot, good));
        helper.assertTrue(SeedPouchGameTests.used(outer) == 3, "set-up");

        SimpleContainer c = new SimpleContainer(27);
        ChestMenu menu = SeedPouchGameTests.menu(player, c);
        c.setItem(0, outer);
        for (int i = 0; i < 3; i++) {
            menu.clicked(0, 1, ClickType.PICKUP, player); // empty cursor: take the last entry
            ItemStack out = menu.getCarried();
            helper.assertTrue(out.is(ModItems.SEED_LOT.get()) && out.getCount() == 1, "entry " + i + " came out as " + out);
            helper.assertTrue(!out.getTag().contains(PouchContents.KEY), "a pouch payload left the pouch: " + out.getTag());
            helper.assertTrue(SeedPouchItem.contents(out).map(PouchContents::used).orElse(0) == 0, "lot with contents");
            boolean valid = i == 0;
            helper.assertTrue(SeedPouchItem.accepts(out) == valid, "entry " + i + " acceptance");
            c.setItem(10 + i, out);
            menu.setCarried(ItemStack.EMPTY);
        }
        helper.assertTrue(SeedPouchGameTests.used(c.getItem(0)) == 0, "pouch not emptied");
        // the inert pouch-shaped lots cannot go back in, and cannot be planted
        menu.setCarried(c.getItem(11).copy());
        menu.clicked(0, 1, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().getCount() == 1 && SeedPouchGameTests.used(c.getItem(0)) == 0,
                "inert entry went back in");
        menu.setCarried(ItemStack.EMPTY);
        PlantFixtures.farmland(helper, PlantFixtures.PLANT);
        helper.assertTrue(!PlantingGameTests.use(helper, player, c.getItem(11).copy(), PlantFixtures.PLANT).consumesAction()
                && helper.getBlockState(PlantFixtures.PLANT).isAir(), "planted a pouch-shaped lot");

        // a valid lot stack carrying a pouch payload: only the lot root goes in
        ItemStack smuggler = PlantingGameTests.lot(level, 2);
        smuggler.getTag().put(PouchContents.KEY, asRoot.copy()); // fits: the item tag is not one of our roots
        smuggler = viaCreativePacket(smuggler);
        c.setItem(1, smuggler);
        menu.clicked(0, 1, ClickType.PICKUP, player); // empty cursor: nothing inside, nothing happens
        menu.setCarried(c.getItem(1).copy());
        c.setItem(1, ItemStack.EMPTY);
        menu.clicked(0, 1, ClickType.PICKUP, player);
        helper.assertTrue(SeedPouchGameTests.used(c.getItem(0)) == 2, "smuggler lot not taken");
        CompoundTag stored = SeedPouchItem.contents(c.getItem(0)).orElseThrow().entries().get(0);
        helper.assertTrue(!stored.contains(PouchContents.KEY) && stored.equals(smuggler.getTag().getCompound(LotNbt.KEY)),
                "stored entry is not exactly the lot root: " + stored);
        menu.setCarried(ItemStack.EMPTY);
        menu.clicked(0, 1, ClickType.PICKUP, player);
        helper.assertTrue(!menu.getCarried().getTag().contains(PouchContents.KEY), "payload came back out");
        helper.succeed();
    }

    /** Entries that are not lots come out as inert seed lots, never as the item they pretend to be. */
    @GameTest(template = SmokeGameTests.FLOOR_3X3X3)
    public static void nonLotEntriesComeOutAsInertLots(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockSurvivalPlayer();
        CompoundTag diamonds = new ItemStack(Items.DIAMOND, 64).save(new CompoundTag());
        CompoundTag empty = new CompoundTag();
        CompoundTag mother = new CompoundTag();
        mother.putInt("v", 1);
        mother.putString("sp", PlantFixtures.SP);
        ItemStack p = forgedPouch(List.of(diamonds, empty, mother));
        SimpleContainer c = new SimpleContainer(27);
        ChestMenu menu = SeedPouchGameTests.menu(player, c);
        menu.setCarried(p);
        for (int slot = 0; slot < 3; slot++) {
            menu.clicked(slot, 1, ClickType.PICKUP, player);
            ItemStack out = c.getItem(slot);
            helper.assertTrue(out.is(ModItems.SEED_LOT.get()) && out.getCount() == 1, "came out as " + out);
            helper.assertTrue(LotStacks.read(out) instanceof LotNbt.Inert, "a non-lot entry reads as a lot: " + out.getTag());
            helper.assertTrue(!SeedPouchItem.accepts(out), "inert lot accepted back");
        }
        helper.assertTrue(SeedPouchGameTests.used(menu.getCarried()) == 0, "entries left");
        // the other way: a non-lot item carrying a valid lot root is not pouchable
        ItemStack stick = new ItemStack(Items.STICK, 3);
        stick.getOrCreateTag().put(LotNbt.KEY, root(PlantingGameTests.lot(level, 1)));
        stick = viaCreativePacket(stick);
        c.setItem(5, stick);
        menu.clicked(5, 1, ClickType.PICKUP, player);
        helper.assertTrue(c.getItem(5).getCount() == 3 && SeedPouchGameTests.used(menu.getCarried()) == 0,
                "a stick with a lot root went into the pouch");
        helper.succeed();
    }

    /**
     * A forged {@code rare_source} on a lot is never passed on: the plant's
     * mutant flag and its seeds' flag depend only on its genome (economy
     * §6.1, SEC-21); an out-of-range value makes the lot inert.
     */
    @GameTest(template = SmokeGameTests.FLOOR_3X3X3)
    public static void forgedRareSourceIsNeverPassedOn(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockSurvivalPlayer();
        ItemStack clean = PlantingGameTests.lot(level, 4);
        ItemStack forged = clean.copy();
        forged.getTag().getCompound(LotNbt.KEY).putByte("rs", (byte) LotNbt.RareSource.MUTATION.ordinal());
        forged = viaCreativePacket(forged);
        helper.assertTrue(SeedLotGameTests.lot(forged).rare() == LotNbt.RareSource.MUTATION, "set-up");
        BlockPos abs = helper.absolutePos(PlantFixtures.PLANT);
        boolean mutantByGenome = PlantService.seedling(level, SeedLotGameTests.lot(clean), 4, abs).orElseThrow().mutant();

        PlantFixtures.farmland(helper, PlantFixtures.PLANT);
        helper.assertTrue(PlantingGameTests.use(helper, player, forged, PlantFixtures.PLANT).consumesAction(), "not planted");
        PlantBlockEntity be = (PlantBlockEntity) helper.getBlockEntity(PlantFixtures.PLANT);
        helper.assertTrue(be.data().has(PlantState.MUTANT) == mutantByGenome,
                "mutant flag " + be.data().has(PlantState.MUTANT) + " but genome says " + mutantByGenome);
        // seeds of the plant: the flag only if the plant itself is a mutant
        helper.getLevel().setBlock(abs, helper.getLevel().getBlockState(abs)
                .setValue(FieldPlantBlock.STAGE, FieldPlantBlock.SEED_STAGE), 2);
        helper.assertTrue(be.rareForDrops() == mutantByGenome, "drops would carry the flag: " + be.rareForDrops());
        LotNbt.RareSource expected = mutantByGenome ? LotNbt.RareSource.MUTATION : LotNbt.RareSource.NONE;
        for (ItemStack s : MotherRootGameTests.drops(helper, PlantFixtures.PLANT, player)) {
            if (LotStacks.read(s) instanceof LotNbt.Lot l) {
                helper.assertTrue(l.rare() == expected, "seed lot of the plant carries " + l.rare());
            }
        }

        // out of range: inert, not plantable, not pouchable
        ItemStack bad = clean.copy();
        bad.getTag().getCompound(LotNbt.KEY).putByte("rs", (byte) 9);
        bad = viaCreativePacket(bad);
        helper.assertTrue(LotStacks.read(bad) instanceof LotNbt.Inert && !SeedPouchItem.accepts(bad), "rs 9 accepted");
        BlockPos other = new BlockPos(0, 2, 0);
        PlantFixtures.farmland(helper, other);
        helper.assertTrue(!PlantingGameTests.use(helper, player, bad, other).consumesAction()
                && helper.getBlockState(other).isAir(), "rs 9 planted");
        helper.succeed();
    }
}
