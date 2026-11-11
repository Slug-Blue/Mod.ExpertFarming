package io.github.slugblue.wildroots.animal;

import io.github.slugblue.wildroots.core.Ids;
import io.github.slugblue.wildroots.core.safety.EntryPoint;
import io.github.slugblue.wildroots.core.safety.Guarded;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

/**
 * Animal feed (WR-172; docs/05-architecture.md §12.4; GDD §6.3): in 1.20.1
 * {@code Animal#isFood} is hard-coded per class, so the produce of the
 * mod's lines feeds animals through events, without a mixin.
 *
 * <ul>
 *   <li>Breeding and growth: {@link PlayerInteractEvent.EntityInteract}.
 *   An item of {@code #wildroots:feeds/<type>} given to an adult that can
 *   fall in love puts it in love; given to a baby it grows the baby as
 *   vanilla food does. The item is spent (not in creative), the event is
 *   cancelled with success. On the client only the success is predicted.</li>
 *   <li>Tempting: {@link EntityJoinLevelEvent} adds a {@link FeedTempt} goal
 *   with {@code Ingredient.of(tag)} beside the vanilla one, with the vanilla
 *   priority and speed of the species.</li>
 * </ul>
 * Vanilla pigs and rabbits do not heal from food, so neither does feed.
 */
public final class AnimalFeed {

    /** A species fed by a tag: its tag and the vanilla tempt goal's priority and speed. */
    record Feed(TagKey<Item> tag, int priority, double speed) {
    }

    public static final TagKey<Item> PIG = TagKey.create(Registries.ITEM, Ids.mod("feeds/pig"));
    public static final TagKey<Item> RABBIT = TagKey.create(Registries.ITEM, Ids.mod("feeds/rabbit"));

    static final Map<EntityType<?>, Feed> FEEDS = Map.of(
            EntityType.PIG, new Feed(PIG, 4, 1.2),
            EntityType.RABBIT, new Feed(RABBIT, 3, 1.0));

    private AnimalFeed() {
    }

    /** The feed of an entity type, if the mod feeds it. */
    public static Optional<TagKey<Item>> feedTag(EntityType<?> type) {
        return Optional.ofNullable(FEEDS.get(type)).map(Feed::tag);
    }

    /** The tempt goal of the mod; a type of its own so it is added once and can be found. */
    public static final class FeedTempt extends TemptGoal {

        private final Ingredient items;

        FeedTempt(PathfinderMob mob, double speed, Ingredient items) {
            super(mob, speed, items, false);
            this.items = items;
        }

        public Ingredient items() {
            return items;
        }
    }

    @EntryPoint
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Animal a)) {
            return;
        }
        Feed f = FEEDS.get(a.getType());
        if (f != null) {
            Guarded.run("feed tempt goal", "animal", () -> addTempt(a, f));
        }
    }

    /** Adds the tempt goal unless the animal has it; returns whether it was added. */
    static boolean addTempt(Animal a, Feed f) {
        boolean present = a.goalSelector.getAvailableGoals().stream().anyMatch(w -> w.getGoal() instanceof FeedTempt);
        if (!present) {
            a.goalSelector.addGoal(f.priority(), new FeedTempt(a, f.speed(), Ingredient.of(f.tag())));
        }
        return !present;
    }

    @EntryPoint
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof Animal a)) {
            return;
        }
        Feed f = FEEDS.get(a.getType());
        ItemStack stack = event.getItemStack();
        if (f == null || stack.isEmpty() || !stack.is(f.tag())) {
            return;
        }
        Player player = event.getEntity();
        InteractionResult r = Guarded.call("animal feed", "animal", () -> feed(a, player, stack),
                InteractionResult.PASS);
        if (r != InteractionResult.PASS) {
            event.setCanceled(true);
            event.setCancellationResult(r);
        }
    }

    /** Vanilla {@code Animal#mobInteract} for food, with the tag in place of {@code isFood}. */
    static InteractionResult feed(Animal a, Player player, ItemStack stack) {
        int age = a.getAge();
        boolean client = a.level().isClientSide();
        if (age == 0 && a.canFallInLove()) {
            if (!client) {
                spend(player, stack);
                a.setInLove(player);
                a.playSound(SoundEvents.GENERIC_EAT, 1.0f, 1.0f);
            }
            return InteractionResult.sidedSuccess(client);
        }
        if (a.isBaby()) {
            if (!client) {
                spend(player, stack);
                a.ageUp(Animal.getSpeedUpSecondsWhenFeeding(-age), true);
                a.playSound(SoundEvents.GENERIC_EAT, 1.0f, 1.0f);
            }
            return InteractionResult.sidedSuccess(client);
        }
        return InteractionResult.PASS;
    }

    static void spend(Player player, ItemStack stack) {
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
    }
}
