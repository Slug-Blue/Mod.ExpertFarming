package io.github.slugblue.wildroots.gametest;

import io.github.slugblue.wildroots.WildRoots;
import io.github.slugblue.wildroots.item.LotNbt;
import io.github.slugblue.wildroots.item.LotStacks;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * WR-186 (TAE), SEC-14, SEC-17: 10 000 random click sequences (with shift,
 * double click, drag and number keys) on pouches in the player inventory;
 * the total of seeds per {@code lotId} and the set of {@code lotId}s never
 * change. Fixed seed; a failure prints the sequence. The seed analyser and
 * the seed bank join with their own {@link MenuFuzz.Target} (WR-184, WR-185).
 */
@GameTestHolder(WildRoots.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MenuFuzzGameTests {

    static final Logger LOG = LoggerFactory.getLogger(MenuFuzzGameTests.class);
    static final int SEQUENCES = 10_000;
    static final int MAX_CLICKS = 8;
    static final long SEED = 0x5EC14_0186L;

    private MenuFuzzGameTests() {
    }

    /** Six distinct realised lots of the carrot. */
    static List<ItemStack> lots(GameTestHelper helper) {
        List<ItemStack> l = new ArrayList<>();
        Set<Long> ids = new HashSet<>();
        while (l.size() < 6) {
            ItemStack s = PlantingGameTests.lot(helper.getLevel(), 1);
            if (LotStacks.read(s) instanceof LotNbt.Lot lot && ids.add(lot.lotId())) {
                l.add(s);
            }
        }
        return l;
    }

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3, timeoutTicks = 400)
    public static void tenThousandClickSequencesOnPouchesKeepEverySeed(GameTestHelper helper) {
        MenuFuzz.Result r = MenuFuzz.run(helper.getLevel(), MenuFuzz.PLAYER_INVENTORY, lots(helper), SEED, SEQUENCES,
                MAX_CLICKS, helper::fail);
        helper.assertTrue(r.sequences() == SEQUENCES, "ran " + r.sequences() + " sequences");
        LOG.info("MenuFuzz: {} sequences, {} clicks on the player inventory in {} ms", r.sequences(), r.clicks(),
                r.millis());
        helper.succeed();
    }

    /** The harness itself catches a broken invariant (a lost seed). */
    @GameTest(template = SmokeGameTests.FLOOR_3X3X3)
    public static void harnessReportsALostSeed(GameTestHelper helper) {
        List<String> failures = new ArrayList<>();
        MenuFuzz.Target losing = new MenuFuzz.Target() {
            @Override
            public net.minecraft.world.inventory.AbstractContainerMenu open(net.minecraft.server.level.ServerPlayer p) {
                return p.inventoryMenu;
            }

            @Override
            public List<Integer> fillable(net.minecraft.world.inventory.AbstractContainerMenu menu) {
                return MenuFuzz.PLAYER_INVENTORY.fillable(menu);
            }

            @Override
            public void finish(net.minecraft.server.level.ServerPlayer p, net.minecraft.world.inventory.AbstractContainerMenu menu) {
                // a buggy menu: one seed of the first lot stack vanishes
                for (var slot : menu.slots) {
                    if (LotStacks.read(slot.getItem()) instanceof LotNbt.Lot) {
                        slot.getItem().shrink(1);
                        return;
                    }
                }
                if (LotStacks.read(menu.getCarried()) instanceof LotNbt.Lot) {
                    menu.getCarried().shrink(1);
                }
            }
        };
        MenuFuzz.run(helper.getLevel(), losing, lots(helper), 7, 50, 3, failures::add);
        helper.assertTrue(failures.size() == 1 && failures.get(0).contains("clicks ["), "failures " + failures);
        helper.succeed();
    }
}
