package io.github.slugblue.wildroots.gametest;

import io.github.slugblue.wildroots.WildRoots;
import io.github.slugblue.wildroots.core.registry.ModItems;
import io.github.slugblue.wildroots.data.Definitions;
import io.github.slugblue.wildroots.genetics.api.Classification;
import io.github.slugblue.wildroots.genetics.api.CultivarId;
import io.github.slugblue.wildroots.genetics.model.Genome;
import io.github.slugblue.wildroots.genetics.model.GenomeLayout;
import io.github.slugblue.wildroots.genetics.model.Locus;
import io.github.slugblue.wildroots.genetics.model.TraitId;
import io.github.slugblue.wildroots.plant.GrowthLogic;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import java.util.Arrays;
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
 * WR-136a, WR-702: the eastern carrot carries its root colour (yellow by default,
 * {@code wildroots:colour: 1b} for purple, {@code 2b} for red), taken from the plant's
 * {@code variant}; different colours do not stack, the same colour does. The variant itself
 * comes from the colour rules of the species ({@code colours} in the JSON, WR-701).
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
    /** The carrot genome of {@code key} with the four colour loci set to {@code copies} of the domestic allele (0, 1 or 2). */
    static Genome colourGenome(long key, int p, int y1, int y2, int r) {
        Genome g = PlantFixtures.genome(key);
        GenomeLayout layout = g.layout();
        Genome.Builder b = g.toBuilder();
        int[] copies = {p, y1, y2, r};
        String[] traits = {"color_p", "color_y1", "color_y2", "color_r"};
        for (int i = 0; i < traits.length; i++) {
            Locus s = layout.section(TraitId.of(traits[i]));
            b.set(s, 0, 0, copies[i] >= 1 ? 1 : 0).set(s, 0, 1, copies[i] >= 2 ? 1 : 0);
        }
        return b.build();
    }

    /**
     * WR-702: a plant that grows into stage 3 shows the colour of the rules ({@code variant} 0 white, 1 yellow, 2 orange,
     * 3 purple, 4 red), and the harvest of the eastern carrot carries it: nothing for white, yellow and orange,
     * {@code 1b} for purple, {@code 2b} for red. Red covers yellow and orange whatever {@code y2} is, purple covers red.
     */
    @GameTest(template = SmokeGameTests.FLOOR_3X3X3, timeoutTicks = 200)
    public static void everyColourOfTheRulesShowsItsVariantAndHarvestsItsColour(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockSurvivalPlayer();
        // p, y1, y2, r (copies of the domestic allele), expected variant, expected colour of the harvest
        int[][] cases = {
            {0, 0, 0, 0, 0, ProduceStacks.YELLOW}, {0, 1, 2, 2, 0, ProduceStacks.YELLOW},
            {0, 2, 0, 0, 1, ProduceStacks.YELLOW}, {0, 2, 0, 1, 1, ProduceStacks.YELLOW},
            {0, 2, 2, 0, 2, ProduceStacks.YELLOW}, {0, 2, 2, 1, 2, ProduceStacks.YELLOW},
            {1, 0, 0, 0, 3, ProduceStacks.PURPLE}, {2, 2, 2, 2, 3, ProduceStacks.PURPLE},
            {0, 2, 0, 2, 4, ProduceStacks.RED}, {0, 2, 2, 2, 4, ProduceStacks.RED}, {0, 2, 1, 2, 4, ProduceStacks.RED},
        };
        BlockPos abs = helper.absolutePos(PlantFixtures.PLANT);
        for (int i = 0; i < cases.length; i++) {
            int[] c = cases[i];
            PlantBlockEntity be = PlantFixtures.place(helper, PlantFixtures.PLANT, 2);
            be.init(Definitions.current().genetics(), new PlantBlockEntity.Init(PlantFixtures.SP,
                    colourGenome(640 + i, c[0], c[1], c[2], c[3]), PlantFixtures.noise(641 + i), Passport.PLAYER,
                    new UUID(3, 4), new Classification(Optional.of(CultivarId.of("wildroots:eastern_carrot")), 1, false),
                    null, 0, 0, 0));
            GrowthLogic.advance(level, abs, level.getBlockState(abs), be, be.live(level).orElseThrow(), 3);
            int variant = helper.getBlockState(PlantFixtures.PLANT).getValue(FieldPlantBlock.VARIANT);
            helper.assertTrue(variant == c[4], "case " + Arrays.toString(c) + ": variant " + variant);
            ItemStack eastern = MotherRootGameTests.drops(helper, PlantFixtures.PLANT, player).stream()
                    .filter(s -> s.is(ModItems.EASTERN_CARROT.get())).findFirst()
                    .orElseThrow(() -> new IllegalStateException("no eastern carrot"));
            helper.assertTrue(ProduceStacks.colour(eastern.getTag()) == c[5], "case " + Arrays.toString(c) + ": colour "
                    + eastern.getTag());
            helper.assertTrue(c[5] == ProduceStacks.YELLOW ? !eastern.getTag().contains(ProduceStacks.COLOUR)
                    : eastern.getTag().getByte(ProduceStacks.COLOUR) == c[5], "case " + Arrays.toString(c) + ": key");
            helper.assertTrue(!eastern.getTag().contains(ProduceStacks.BITTER), "a colour item is never bitter");
            helper.setBlock(PlantFixtures.PLANT, Blocks.AIR);
        }
        helper.succeed();
    }

    /** WR-702: red eastern carrots stack with red, not with yellow or purple; a forged red key on the wild root is not a colour. */
    @GameTest(template = SmokeGameTests.FLOOR_3X3X3)
    public static void redEasternCarrotsStackOnlyWithRed(GameTestHelper helper) {
        Player player = helper.makeMockSurvivalPlayer();
        ItemStack red = harvest(helper, player, 650, 4);
        ItemStack red2 = harvest(helper, player, 650, 4);
        ItemStack yellow = harvest(helper, player, 650, 1);
        ItemStack purple = harvest(helper, player, 650, 3);
        helper.assertTrue(ProduceStacks.colour(red.getTag()) == ProduceStacks.RED && red.getTag().getByte(ProduceStacks.COLOUR) == 2,
                "red tag " + red.getTag());
        helper.assertTrue(ItemStack.isSameItemSameTags(red, red2), "two red harvests do not stack");
        helper.assertTrue(!ItemStack.isSameItemSameTags(red, yellow) && !ItemStack.isSameItemSameTags(red, purple),
                "red stacks with another colour");
        helper.assertTrue(ProduceStacks.create(ModItems.WILD_CARROT_ROOT.get(), 3, false, ProduceStacks.RED, 1).getTag()
                .equals(ProduceStacks.tag(3, false)), "the wild root has no colour");
        helper.succeed();
    }

    /**
     * Owner decision (two eastern-carrot textures): an orange-rooted eastern
     * carrot (variant 2) and a white-variant one (0) are yellow items, write
     * no colour key and stack with the yellow harvest.
     */
    @GameTest(template = SmokeGameTests.FLOOR_3X3X3)
    public static void orangeAndWhiteVariantEasternCarrotsAreYellowAndStackWithYellow(GameTestHelper helper) {
        Player player = helper.makeMockSurvivalPlayer();
        ItemStack yellow = harvest(helper, player, 620, 1);
        for (int variant : new int[] {0, 2}) {
            ItemStack other = harvest(helper, player, 620, variant);
            helper.assertTrue(other.getTag() != null && !other.getTag().contains(ProduceStacks.COLOUR),
                    "variant " + variant + " writes a colour: " + other.getTag());
            helper.assertTrue(ProduceStacks.colour(other.getTag()) == ProduceStacks.YELLOW, "variant " + variant);
            helper.assertTrue(ItemStack.isSameItemSameTags(yellow, other), "variant " + variant + " does not stack with yellow");
        }
        helper.succeed();
    }
}
