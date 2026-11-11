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
