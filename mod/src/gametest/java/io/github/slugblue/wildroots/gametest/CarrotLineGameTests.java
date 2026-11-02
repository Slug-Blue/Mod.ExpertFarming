package io.github.slugblue.wildroots.gametest;

import io.github.slugblue.wildroots.WildRoots;
import io.github.slugblue.wildroots.data.Definitions;
import io.github.slugblue.wildroots.data.DefinitionsSnapshot;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** WR-129: the carrot line pack loads without problems and takes carrots off the villager seed tag. */
@GameTestHolder(WildRoots.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CarrotLineGameTests {

    private CarrotLineGameTests() {
    }

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3)
    public static void carrotDefinitionsLoadWithoutProblems(GameTestHelper helper) {
        DefinitionsSnapshot s = Definitions.current();
        helper.assertTrue(s.species("wildroots:wild_carrot").isPresent(), "wild_carrot is not loaded");
        helper.assertTrue(s.cultivar("wildroots:eastern_carrot").isPresent(), "eastern_carrot is not loaded");
        helper.assertTrue(s.cultivar("wildroots:carrot").isPresent(), "carrot is not loaded");
        helper.assertTrue(s.stats().errors() == 0 && s.stats().warnings() == 0,
                "the load had problems: " + s.stats() + " (see the log)");
        helper.assertTrue(s.layouts().lookup("wildroots:wild_carrot", -3539622408563365149L).isPresent(),
                "the layout history of wild_carrot is missing");
        helper.succeed();
    }

    /** {@code "remove"} of Forge tag files: carrot leaves the tag, entries of other packs stay. */
    @GameTest(template = SmokeGameTests.FLOOR_3X3X3)
    public static void villagerSeedTagLosesCarrotOnly(GameTestHelper helper) {
        helper.assertTrue(!new ItemStack(Items.CARROT).is(ItemTags.VILLAGER_PLANTABLE_SEEDS),
                "minecraft:carrot is still in #minecraft:villager_plantable_seeds");
        helper.assertTrue(new ItemStack(Items.WHEAT_SEEDS).is(ItemTags.VILLAGER_PLANTABLE_SEEDS),
                "vanilla wheat seeds were removed");
        helper.assertTrue(new ItemStack(Items.POTATO).is(ItemTags.VILLAGER_PLANTABLE_SEEDS),
                "vanilla potato was removed");
        // added by another pack (the GameTest sources stand in for another mod)
        helper.assertTrue(new ItemStack(Items.SWEET_BERRIES).is(ItemTags.VILLAGER_PLANTABLE_SEEDS),
                "the entry of another pack was lost");
        helper.succeed();
    }
}
