package io.github.slugblue.wildroots.gametest;

import io.github.slugblue.wildroots.WildRoots;
import io.github.slugblue.wildroots.core.registry.ModItems;
import io.github.slugblue.wildroots.data.Definitions;
import io.github.slugblue.wildroots.genetics.api.Classification;
import io.github.slugblue.wildroots.genetics.api.CultivarId;
import io.github.slugblue.wildroots.item.ProduceStacks;
import io.github.slugblue.wildroots.plant.FieldPlantBlock;
import io.github.slugblue.wildroots.plant.Passport;
import io.github.slugblue.wildroots.plant.PlantBlockEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * WR-136a: the eastern carrot carries its root colour (yellow by default,
 * {@code wildroots:colour: 1b} for purple), taken from the plant's
 * {@code variant}; different colours do not stack, the same colour does.
 */
@GameTestHolder(WildRoots.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RootColourGameTests {

    private RootColourGameTests() {
    }

    /** The eastern carrot harvest of the same plant (genome {@code key}) with root colour {@code variant}. */
    static ItemStack harvest(GameTestHelper helper, Player player, long key, int variant) {
        PlantBlockEntity be = PlantFixtures.place(helper, PlantFixtures.PLANT, FieldPlantBlock.HARVEST_STAGE);
        be.init(Definitions.current().genetics(), new PlantBlockEntity.Init(PlantFixtures.SP, PlantFixtures.genome(key),
                PlantFixtures.noise(key + 1), Passport.PLAYER, new UUID(3, 4),
                new Classification(Optional.of(CultivarId.of("wildroots:eastern_carrot")), 1, false), null, 0, 0, 0));
        helper.setBlock(PlantFixtures.PLANT, helper.getBlockState(PlantFixtures.PLANT).setValue(FieldPlantBlock.VARIANT, variant));
        List<ItemStack> drops = MotherRootGameTests.drops(helper, PlantFixtures.PLANT, player);
        return drops.stream().filter(s -> s.is(ModItems.EASTERN_CARROT.get())).findFirst()
                .orElseThrow(() -> new IllegalStateException("no eastern carrot in " + drops));
    }

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3)
    public static void yellowAndPurpleEasternCarrotsDoNotStackTheSameColourDoes(GameTestHelper helper) {
        Player player = helper.makeMockSurvivalPlayer();
        ItemStack yellow = harvest(helper, player, 610, 1);
        ItemStack yellow2 = harvest(helper, player, 610, 1);
        ItemStack purple = harvest(helper, player, 610, 3);
        ItemStack purple2 = harvest(helper, player, 610, 3);
        helper.assertTrue(ProduceStacks.colour(yellow.getTag()) == ProduceStacks.YELLOW
                && !yellow.getTag().contains(ProduceStacks.COLOUR), "yellow writes the key: " + yellow.getTag());
        helper.assertTrue(ProduceStacks.colour(purple.getTag()) == ProduceStacks.PURPLE
                && purple.getTag().getByte(ProduceStacks.COLOUR) == 1, "purple tag " + purple.getTag());
        helper.assertTrue(!ItemStack.isSameItemSameTags(yellow, purple), "yellow and purple stack together");
        helper.assertTrue(ItemStack.isSameItemSameTags(yellow, yellow2), "two yellow harvests do not stack");
        helper.assertTrue(ItemStack.isSameItemSameTags(purple, purple2), "two purple harvests do not stack");
        // only colour-variant items carry the key: a wild root of a purple wild plant does not
        PlantBlockEntity be = PlantFixtures.plant(PlantFixtures.place(helper, PlantFixtures.PLANT, 3), 611);
        helper.setBlock(PlantFixtures.PLANT, helper.getBlockState(PlantFixtures.PLANT).setValue(FieldPlantBlock.VARIANT, 3));
        List<ItemStack> wild = MotherRootGameTests.drops(helper, PlantFixtures.PLANT, player).stream()
                .filter(s -> s.is(ModItems.WILD_CARROT_ROOT.get())).toList();
        helper.assertTrue(!wild.isEmpty(), "no wild root dropped");
        wild.forEach(s -> helper.assertTrue(!s.getTag().contains(ProduceStacks.COLOUR), "wild root got a colour"));
        helper.assertTrue(be != null, "plant");
        helper.succeed();
    }
}
