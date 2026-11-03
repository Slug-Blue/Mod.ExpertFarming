package io.github.slugblue.wildroots.plant;

import io.github.slugblue.wildroots.genetics.api.Environment;
import io.github.slugblue.wildroots.genetics.api.GeneticsService;
import io.github.slugblue.wildroots.genetics.api.Phenotype;
import io.github.slugblue.wildroots.genetics.pollination.Brush;
import io.github.slugblue.wildroots.genetics.pollination.FatherSelector;
import io.github.slugblue.wildroots.item.LotFathersNbt;
import io.github.slugblue.wildroots.item.LotFlags;
import io.github.slugblue.wildroots.item.LotStacks;
import io.github.slugblue.wildroots.item.MotherItem;
import io.github.slugblue.wildroots.core.registry.ModBlocks;
import io.github.slugblue.wildroots.core.registry.ModItems;
import io.github.slugblue.wildroots.genetics.api.Traits;
import io.github.slugblue.wildroots.item.ProduceItem;
import io.github.slugblue.wildroots.item.ProduceStacks;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/**
 * What a plant drops when its block goes (docs/05-architecture.md §10.2,
 * §6.3; GDD §5.1–5.3, §6.4, §14). Drops are computed on the server from the
 * block entity, before the block becomes air ({@code Level#destroyBlock},
 * S3); an inert or sleeping plant drops nothing, so its data is never
 * copied (§6.1).
 *
 * <ul>
 *   <li>stages 0–2 (young): one realised seedling lot, the same genome
 *   and noise when planted again (SEC-26, default of STATUS.md);</li>
 *   <li>stage 3 (harvest ripeness): {@code round_prob(food_mult·(1 + 4·P_yield))}
 *   harvest items of one grade and one mother root (GDD §5.2); a planted
 *   mother gives only its mother root back;</li>
 *   <li>stage 4 (flowering): the mother root;</li>
 *   <li>stage 5 (seeds ripe): one seed lot, {@code (3 + 9·P_yield)·seed_set}
 *   seeds rounded at random, with the fathers and {@code p_out} of the
 *   pending lot only when a real player harvests; a piston, water, an
 *   explosion or a {@code FakePlayer} give a lot without fathers (GDD §14).</li>
 * </ul>
 * A cultivar drops its own items: its harvest item and lots with its id
 * (GDD §6.2 item 6).
 */
public final class HarvestService {

    private HarvestService() {
    }

    /** {@code getDrops} of a plant block. */
    public static List<ItemStack> drops(BlockState state, LootParams.Builder params) {
        return drops(state, params, params.getLevel().random);
    }

    /**
     * Drops with a given RNG (tests compare harvests). No side effects: the
     * {@code mutation} claim is only looked at here and consumed when the
     * block is really removed ({@code FieldPlantBlock#onRemove}).
     */
    public static List<ItemStack> drops(BlockState state, LootParams.Builder params, RandomSource random) {
        BlockEntity raw = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        ServerLevel level = params.getLevel();
        if (!(raw instanceof PlantBlockEntity be)) {
            return List.of();
        }
        Optional<PlantBlockEntity.Live> live = be.live(level);
        if (live.isEmpty()) {
            return List.of();
        }
        BlockPos pos = be.getBlockPos();
        Environment env = GrowthLogic.environment(level, pos);
        Optional<Phenotype> p = GrowthLogic.phenotype(live.get(), env);
        if (p.isEmpty()) {
            return List.of();
        }
        int stage = FieldPlantBlock.stage(state);
        Entity breaker = params.getOptionalParameter(LootContextParams.THIS_ENTITY);
        // an explosion carries its source as THIS_ENTITY: with a player source it is still automation
        boolean player = breaker instanceof Player && !(breaker instanceof FakePlayer)
                && params.getOptionalParameter(LootContextParams.EXPLOSION_RADIUS) == null;
        List<ItemStack> out = new ArrayList<>(3);
        if (stage >= FieldPlantBlock.SEED_STAGE) {
            seeds(level, be, live.get(), p.get(), random, player).ifPresent(out::add);
        } else if (stage >= FieldPlantBlock.HARVEST_STAGE) {
            int grade = grade(live.get(), p.get());
            if (stage == FieldPlantBlock.HARVEST_STAGE && !live.get().data().has(PlantState.MOTHER)) {
                food(live.get(), p.get(), grade, random).ifPresent(out::add);
            }
            out.add(MotherItem.of(live.get(), grade, p.get().bitter()));
        } else {
            out.add(LotStacks.seedling(level, live.get()));
        }
        return out;
    }

    /** Grade 1–4 by the plant's cultivar axes, by the line's axes for wild plants (GDD §6.4). */
    public static int grade(PlantBlockEntity.Live live, Phenotype p) {
        GeneticsService gs = live.genetics();
        String cv = live.data().cultivar();
        return Optional.ofNullable(cv).flatMap(live.snapshot()::cultivar)
                .map(c -> gs.grade(p, c.def()))
                .orElseGet(() -> gs.grade(p, live.def()));
    }

    /** Wild harvest item of a built-in species (step 0). */
    private static final java.util.Map<String, java.util.function.Supplier<? extends Item>> WILD_PRODUCE =
            java.util.Map.of(ModBlocks.WILD_CARROT_SPECIES, ModItems.WILD_CARROT_ROOT);

    /** Harvest item of the plant: its cultivar's item, else the wild item of the species. */
    public static Optional<Item> produceItem(PlantBlockEntity.Live live) {
        Optional<Item> cv = Optional.ofNullable(live.data().cultivar()).flatMap(live.snapshot()::cultivar)
                .flatMap(c -> c.info().produceItem()).map(ForgeRegistries.ITEMS::getValue)
                .filter(i -> i instanceof ProduceItem);
        if (cv.isPresent()) {
            return cv;
        }
        var wild = WILD_PRODUCE.get(live.data().species());
        return wild == null ? Optional.empty() : Optional.of(wild.get());
    }

    /** {@code round_prob(food_mult·(1 + 4·P_yield))} items of one grade (GDD §5.2, §6.4). */
    static Optional<ItemStack> food(PlantBlockEntity.Live live, Phenotype p, int grade, RandomSource random) {
        Optional<Item> item = produceItem(live);
        if (item.isEmpty()) {
            return Optional.empty();
        }
        double mult = Optional.ofNullable(live.data().cultivar()).flatMap(live.snapshot()::cultivar)
                .map(c -> c.info().foodMult()).orElse(1.0);
        int n = roundProb(mult * (1 + 4 * p.pOr(Traits.YIELD, 0)), random);
        return n <= 0 ? Optional.empty() : Optional.of(ProduceStacks.create(item.get(), grade, p.bitter(), n));
    }

    /** Probabilistic rounding: {@code floor(x)} plus one with chance {@code frac(x)}. */
    public static int roundProb(double x, RandomSource random) {
        double e = Double.isFinite(x) ? Math.max(0, x) : 0;
        int base = (int) Math.floor(e);
        return base + (random.nextDouble() < e - base ? 1 : 0);
    }

    /** The seed lot of a plant at stage 5, with or without the fathers of its pending lot. */
    static Optional<ItemStack> seeds(ServerLevel level, PlantBlockEntity be, PlantBlockEntity.Live live, Phenotype p,
                                     RandomSource random, boolean withFathers) {
        // the seed count follows the real pollination; automation only loses the fathers (GDD §14)
        PendingLot pending = live.data().pending();
        List<LotFathersNbt.Father> fathers = pending == null ? List.of() : pending.fathers();
        int donors = (int) fathers.stream().filter(LotFathersNbt.Father::donor).count();
        Brush brush = pending == null ? Brush.NONE : pending.brush();
        boolean bees = pending != null && pending.bees();
        FatherSelector.Selection sel = new FatherSelector.Selection(List.of(), 0, fathers.size() - donors, donors);
        double expected = live.genetics().expectedSeeds(live.def(), p, sel, bees || brush != Brush.NONE, brush);
        int count = roundProb(expected, random);
        if (count <= 0) {
            return Optional.empty();
        }
        if (!withFathers) {
            return Optional.of(LotStacks.seeds(level, live, List.of(), 0, 0, be::rareAvailable, count));
        }
        int flags = (bees ? LotFlags.BEES : 0) | (brush != Brush.NONE ? LotFlags.BRUSH : 0);
        return Optional.of(LotStacks.seeds(level, live, fathers, pending == null ? 0 : pending.pOut(), flags, be::rareAvailable,
                count));
    }
}
