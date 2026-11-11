package io.github.slugblue.wildroots.gametest;

import io.github.slugblue.wildroots.WildRoots;
import io.github.slugblue.wildroots.animal.AnimalFeed;
import io.github.slugblue.wildroots.core.registry.ModItems;
import io.github.slugblue.wildroots.item.ProduceStacks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * WR-172 (architecture §12.4): pigs and rabbits take the carrot line's
 * produce from {@code #wildroots:feeds/<type>}: love, baby growth and the
 * tempt goal; the item is spent. A mother root is not feed.
 */
@GameTestHolder(WildRoots.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AnimalFeedGameTests {

    private AnimalFeedGameTests() {
    }

    static void feedsAndTempts(GameTestHelper helper, EntityType<? extends Animal> type, String name) {
        ServerPlayer p = JournalGameTests.survival(helper, name);
        Animal adult = helper.spawn(type, new BlockPos(1, 2, 1));
        Animal baby = helper.spawn(type, new BlockPos(1, 2, 1));
        try {
            baby.setAge(-24000);
            for (Item item : new Item[] {ModItems.WILD_CARROT_ROOT.get(), ModItems.EASTERN_CARROT.get(),
                    ModItems.CARROT.get()}) {
                helper.assertTrue(AnimalFeed.feedTag(type).map(t -> new ItemStack(item).is(t)).orElse(false),
                        item + " not in the feed tag of " + type);
            }
            // tempt goal, once
            helper.assertTrue(adult.goalSelector.getAvailableGoals().stream()
                    .filter(w -> w.getGoal() instanceof AnimalFeed.FeedTempt).count() == 1, "no single tempt goal");
            AnimalFeed.FeedTempt goal = (AnimalFeed.FeedTempt) adult.goalSelector.getAvailableGoals().stream()
                    .filter(w -> w.getGoal() instanceof AnimalFeed.FeedTempt).findFirst().orElseThrow().getGoal();
            helper.assertTrue(goal.items().test(new ItemStack(ModItems.WILD_CARROT_ROOT.get())), "tempt item");
            helper.assertTrue(!goal.items().test(new ItemStack(ModItems.MOTHER_ROOT.get())), "mother root tempts");

            // a mother root is not feed
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.MOTHER_ROOT.get()));
            p.interactOn(adult, InteractionHand.MAIN_HAND);
            helper.assertTrue(!adult.isInLove(), "fell in love with a mother root");

            ItemStack food = ProduceStacks.create(ModItems.WILD_CARROT_ROOT.get(), 1, false, 3);
            p.setItemInHand(InteractionHand.MAIN_HAND, food);
            helper.assertTrue(p.interactOn(adult, InteractionHand.MAIN_HAND).consumesAction(), "adult refused");
            helper.assertTrue(adult.isInLove(), "adult not in love");
            helper.assertTrue(p.getItemInHand(InteractionHand.MAIN_HAND).getCount() == 2, "item not spent");
            // in love already: the click is taken (no ride, no eating), nothing is spent
            helper.assertTrue(p.interactOn(adult, InteractionHand.MAIN_HAND).consumesAction()
                    && p.getItemInHand(InteractionHand.MAIN_HAND).getCount() == 2, "fed while in love");
            if (adult instanceof net.minecraft.world.entity.animal.Pig pig) {
                pig.equipSaddle(null);
                p.interactOn(pig, InteractionHand.MAIN_HAND);
                helper.assertTrue(p.getVehicle() == null, "feed on a saddled pig in love mounted it");
            }

            int before = baby.getAge();
            helper.assertTrue(p.interactOn(baby, InteractionHand.MAIN_HAND).consumesAction(), "baby refused");
            helper.assertTrue(baby.getAge() > before, "baby did not grow: " + before + " -> " + baby.getAge());
            helper.assertTrue(p.getItemInHand(InteractionHand.MAIN_HAND).getCount() == 1, "baby feed not spent");
        } finally {
            adult.discard();
            baby.discard();
            p.discard();
        }
        helper.succeed();
    }

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3, batch = "wr172_feed")
    public static void pigTakesCarrotLineProduce(GameTestHelper helper) {
        feedsAndTempts(helper, EntityType.PIG, "wr172_pig");
    }

    @GameTest(template = SmokeGameTests.FLOOR_3X3X3, batch = "wr172_feed")
    public static void rabbitTakesCarrotLineProduce(GameTestHelper helper) {
        feedsAndTempts(helper, EntityType.RABBIT, "wr172_rabbit");
    }
}
