package io.github.slugblue.wildroots.gametest;

import io.github.slugblue.wildroots.WildRoots;
import io.github.slugblue.wildroots.core.Ids;
import io.github.slugblue.wildroots.core.registry.ModItems;
import io.github.slugblue.wildroots.item.ProduceItem;
import io.github.slugblue.wildroots.item.ProduceStacks;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** WR-136: harvest items and {@code ProduceStacks} (GDD §6.4, §3.6; economy §2; docs/05-architecture.md §6.3, §12.3). */
@GameTestHolder(WildRoots.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ProduceGameTests {

    private ProduceGameTests() {
    }

    static boolean tagged(ItemStack s, String tag) {
        return s.is(ItemTags.create(new ResourceLocation(tag)));
    }

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3)
    public static void carrotItemsCarryTheLineTags(GameTestHelper helper) {
        ItemStack wild = new ItemStack(ModItems.WILD_CARROT_ROOT.get());
        ItemStack eastern = new ItemStack(ModItems.EASTERN_CARROT.get());
        ItemStack carrot = new ItemStack(ModItems.CARROT.get());
        helper.assertTrue(Ids.mod("carrot").equals(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(carrot.getItem())),
                "id of the cultivar item");
        for (ItemStack s : List.of(wild, eastern, carrot)) {
            helper.assertTrue(tagged(s, "forge:crops/carrot") && tagged(s, "forge:vegetables/carrot"), "forge tags " + s);
        }
        helper.assertTrue(tagged(carrot, "wildroots:cultivar_produce"), "cultivar_produce");
        helper.assertTrue(tagged(eastern, "wildroots:landrace_produce"), "landrace_produce");
        helper.assertTrue(tagged(wild, "wildroots:wild_produce"), "wild_produce");
        helper.assertTrue(!tagged(wild, "wildroots:cultivar_produce"), "wild root is not a cultivar");
        helper.succeed();
    }

    /** GDD §6.4: carrot grades 1/2/3/4 → 1/0.24, 2/0.42, 3/0.6, 4/0.72; bitter ×0.5 saturation and nausea. */
    @GameTest(template = SmokeGameTests.FLOOR_3X3X3)
    public static void carrotFoodByGradeAndBitterness(GameTestHelper helper) {
        Player player = helper.makeMockSurvivalPlayer();
        int[] n = {0, 1, 2, 3, 4};
        float[] s = {0, 0.24f, 0.42f, 0.6f, 0.72f};
        for (ProduceItem item : List.of(ModItems.WILD_CARROT_ROOT.get(), ModItems.EASTERN_CARROT.get(),
                ModItems.CARROT.get())) {
            Set<FoodProperties> seen = Collections.newSetFromMap(new IdentityHashMap<>());
            for (int round = 0; round < 2; round++) {
                for (int g = 1; g <= 4; g++) {
                    FoodProperties f = ProduceStacks.create(item, g, false).getFoodProperties(player);
                    helper.assertTrue(f.getNutrition() == n[g] && Math.abs(f.getSaturationModifier() - s[g]) < 1e-5,
                            item + " grade " + g + ": " + f.getNutrition() + "/" + f.getSaturationModifier());
                    helper.assertTrue(f.getEffects().isEmpty(), "effect on a sweet item");
                    seen.add(f);
                }
                for (int g = 1; g <= 2; g++) {
                    if (item.rootColours()) {
                        // ADR-0007 revision 5: a colour item is never bitter, the factory drops the key
                        ItemStack plain = ProduceStacks.create(item, g, true);
                        helper.assertTrue(!plain.getTag().contains(ProduceStacks.BITTER), item + " grade " + g + " bitter");
                        FoodProperties fs = plain.getFoodProperties(player);
                        helper.assertTrue(fs.getNutrition() == n[g] && Math.abs(fs.getSaturationModifier() - s[g]) < 1e-5
                                && fs.getEffects().isEmpty(), item + " grade " + g + " after a bitter request");
                        seen.add(fs);
                        continue;
                    }
                    FoodProperties f = ProduceStacks.create(item, g, true).getFoodProperties(player);
                    helper.assertTrue(f.getNutrition() == n[g] && Math.abs(f.getSaturationModifier() - s[g] / 2) < 1e-5,
                            item + " bitter grade " + g);
                    helper.assertTrue(f.getEffects().size() == 1
                            && f.getEffects().get(0).getFirst().getEffect() == MobEffects.CONFUSION, "bitter effect");
                    seen.add(f);
                }
            }
            helper.assertTrue(seen.size() <= 6, item + ": " + seen.size() + " FoodProperties objects");
        }
        // eating applies them: grade 4 gives 4 food points
        player.getFoodData().setFoodLevel(0);
        player.getFoodData().eat(ModItems.CARROT.get(), ProduceStacks.create(ModItems.CARROT.get(), 4, false), player);
        helper.assertTrue(player.getFoodData().getFoodLevel() == 4, "food after eating " + player.getFoodData().getFoodLevel());
        helper.succeed();
    }

    /**
     * ADR-0007 revision 5 (review m3): every {@code ProduceStacks.create} overload, over grade (also out of range) x
     * bitter x colour, gives at most 12 different stacks ({@code ItemStack#isSameItemSameTags}) for the eastern
     * carrot and never a bitter key; the wild root has at most 6 and no colour key. A carrot of the cultivar
     * {@code wildroots:carrot} is never bitter because its profile requires bitterness domestic (the classifier,
     * not the factory, enforces that), so its 4 variants are not asserted here.
     */
    @GameTest(template = SmokeGameTests.FLOOR_3X3X3)
    public static void everyCreateOverloadStaysWithinTheStackVariantBound(GameTestHelper helper) {
        java.util.List<ItemStack> eastern = new java.util.ArrayList<>();
        java.util.List<ItemStack> wild = new java.util.ArrayList<>();
        for (int g = -2; g <= 6; g++) {
            for (boolean b : new boolean[] {false, true}) {
                eastern.add(ProduceStacks.create(ModItems.EASTERN_CARROT.get(), g, b));
                eastern.add(ProduceStacks.create(ModItems.EASTERN_CARROT.get(), g, b, 3));
                wild.add(ProduceStacks.create(ModItems.WILD_CARROT_ROOT.get(), g, b));
                wild.add(ProduceStacks.create(ModItems.WILD_CARROT_ROOT.get(), g, b, 2));
                for (int c = -1; c <= 4; c++) {
                    eastern.add(ProduceStacks.create(ModItems.EASTERN_CARROT.get(), g, b, c, 1));
                    wild.add(ProduceStacks.create(ModItems.WILD_CARROT_ROOT.get(), g, b, c, 1));
                }
            }
        }
        helper.assertTrue(eastern.stream().noneMatch(s -> s.getTag().contains(ProduceStacks.BITTER)), "bitter eastern carrot");
        helper.assertTrue(wild.stream().noneMatch(s -> s.getTag().contains(ProduceStacks.COLOUR)), "coloured wild root");
        int e = distinct(eastern);
        int w = distinct(wild);
        helper.assertTrue(e <= 12, "eastern carrot variants " + e);
        helper.assertTrue(w <= 6, "wild root variants " + w);
        helper.succeed();
    }

    static int distinct(java.util.List<ItemStack> stacks) {
        java.util.List<ItemStack> seen = new java.util.ArrayList<>();
        for (ItemStack s : stacks) {
            if (seen.stream().noneMatch(o -> ItemStack.isSameItemSameTags(o, s))) {
                seen.add(s);
            }
        }
        return seen.size();
    }

    /**
     * WR-703: the purple eastern carrot is more filling by the colour's {@code saturation_mult} of the species JSON
     * (x1.15), the yellow and the red one are not; nutrition and effects are the same.
     */
    @GameTest(template = SmokeGameTests.FLOOR_3X3X3)
    public static void purpleEasternCarrotIsMoreFillingByTheDataMultiplier(GameTestHelper helper) {
        Player player = helper.makeMockSurvivalPlayer();
        double mult = io.github.slugblue.wildroots.data.Definitions.current().species(PlantFixtures.SP).orElseThrow().def()
                .colours().props(3).saturationMult(); // 3 = purple in the carrot's colours.ids
        helper.assertTrue(Math.abs(mult - 1.15) < 1e-9, "purple saturation_mult " + mult);
        for (int g = 1; g <= 4; g++) {
            FoodProperties yellow = ProduceStacks.create(ModItems.EASTERN_CARROT.get(), g, false, ProduceStacks.YELLOW, 1)
                    .getFoodProperties(player);
            FoodProperties purple = ProduceStacks.create(ModItems.EASTERN_CARROT.get(), g, false, ProduceStacks.PURPLE, 1)
                    .getFoodProperties(player);
            FoodProperties red = ProduceStacks.create(ModItems.EASTERN_CARROT.get(), g, false, ProduceStacks.RED, 1)
                    .getFoodProperties(player);
            helper.assertTrue(Math.abs(purple.getSaturationModifier() - yellow.getSaturationModifier() * mult) < 1e-5,
                    "grade " + g + ": purple " + purple.getSaturationModifier() + " yellow " + yellow.getSaturationModifier());
            helper.assertTrue(red.getSaturationModifier() == yellow.getSaturationModifier(), "red is not more filling");
            helper.assertTrue(purple.getNutrition() == yellow.getNutrition() && purple.getEffects().isEmpty(),
                    "purple changed more than the saturation");
        }
        // the other cultivars have no colours: unchanged
        helper.assertTrue(Math.abs(ProduceStacks.create(ModItems.CARROT.get(), 3, false).getFoodProperties(player)
                .getSaturationModifier() - 0.6f) < 1e-5, "carrot saturation");
        helper.succeed();
    }

    /**
     * D3 (owner, 2026-11-11): coarse produce takes longer to eat. Grades 1–4
     * eat in 48 / 40 / 32 / 29 ticks, bitter or not; a mother root by its
     * own grade; the real use timer of a player follows it.
     */
    @net.minecraft.gametest.framework.GameTest(template = SmokeGameTests.FLOOR_3X3X3)
    public static void eatingTimeFollowsTheGrade(net.minecraft.gametest.framework.GameTestHelper helper) {
        int[] ticks = {0, 48, 40, 32, 29};
        for (var item : java.util.List.of(io.github.slugblue.wildroots.core.registry.ModItems.WILD_CARROT_ROOT.get(),
                io.github.slugblue.wildroots.core.registry.ModItems.EASTERN_CARROT.get(),
                io.github.slugblue.wildroots.core.registry.ModItems.CARROT.get())) {
            for (int g = 1; g <= 4; g++) {
                ItemStack s = io.github.slugblue.wildroots.item.ProduceStacks.create(item, g, false, 1);
                helper.assertTrue(s.getUseDuration() == ticks[g], item + " grade " + g + ": " + s.getUseDuration());
            }
            ItemStack bitter = io.github.slugblue.wildroots.item.ProduceStacks.create(item, 2, true, 1);
            helper.assertTrue(bitter.getUseDuration() == ticks[2], "bitter changed the time: " + bitter.getUseDuration());
        }
        net.minecraft.world.entity.player.Player player = helper.makeMockSurvivalPlayer();
        io.github.slugblue.wildroots.plant.PlantBlockEntity be = PlantFixtures.plant(PlantFixtures.place(helper,
                PlantFixtures.PLANT, io.github.slugblue.wildroots.plant.FieldPlantBlock.HARVEST_STAGE), 91);
        ItemStack mother = MotherRootGameTests.drops(helper, PlantFixtures.PLANT, player).stream()
                .filter(s -> s.is(io.github.slugblue.wildroots.core.registry.ModItems.MOTHER_ROOT.get()))
                .findFirst().orElseThrow();
        int grade = io.github.slugblue.wildroots.item.MotherItem.read(mother).orElseThrow().grade();
        helper.assertTrue(mother.getUseDuration() == ticks[grade], "mother grade " + grade + ": " + mother.getUseDuration());
        // the player's use timer starts from it
        ItemStack coarse = io.github.slugblue.wildroots.item.ProduceStacks.create(
                io.github.slugblue.wildroots.core.registry.ModItems.WILD_CARROT_ROOT.get(), 1, false, 1);
        player.getFoodData().setFoodLevel(10);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, coarse);
        player.startUsingItem(net.minecraft.world.InteractionHand.MAIN_HAND);
        helper.assertTrue(player.getUseItemRemainingTicks() == 48, "use timer " + player.getUseItemRemainingTicks());
        helper.assertTrue(be != null, "plant");
        helper.succeed();
    }
}
