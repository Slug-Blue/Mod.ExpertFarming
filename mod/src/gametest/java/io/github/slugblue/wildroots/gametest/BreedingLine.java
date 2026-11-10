package io.github.slugblue.wildroots.gametest;

import com.mojang.authlib.GameProfile;
import io.github.slugblue.wildroots.core.registry.ModFeatures;
import io.github.slugblue.wildroots.core.safety.DispCodec;
import io.github.slugblue.wildroots.genetics.random.SplitMix64;
import io.github.slugblue.wildroots.item.MotherItem;
import io.github.slugblue.wildroots.item.SeedLotItem;
import io.github.slugblue.wildroots.lens.Lens;
import io.github.slugblue.wildroots.lens.LensReading;
import io.github.slugblue.wildroots.plant.FieldPlantBlock;
import io.github.slugblue.wildroots.plant.PendingLot;
import io.github.slugblue.wildroots.plant.PlantBlockEntity;
import io.github.slugblue.wildroots.plant.PlantState;
import io.github.slugblue.wildroots.pollination.PollinationService;
import io.github.slugblue.wildroots.worldgen.WildPatchConfig;
import io.github.slugblue.wildroots.worldgen.WildPatchFeature;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * WR-195 (M1a gate): one breeding line of a survival player, from a
 * starter patch to the {@code carrot} cultivar, through production paths
 * only (GDD §7.3, §11.3, §12.5).
 *
 * <ul>
 *   <li>Start: a starter patch placed by {@link WildPatchFeature} with the
 *   starter options of the starter step (Java-only {@code Options}), on a
 *   sky platform at a fixed origin. Its plants realise their genomes on the
 *   first touch from the world seed, as in a real world.</li>
 *   <li>A generation: the player inspects every root at harvest ripeness
 *   with the hand lens ({@link Lens#inspectPlant}) and sees its colour,
 *   breaks it ({@code ServerPlayerGameMode#destroyBlock}: real drops, real
 *   journal hooks), keeps the mother roots of the best {@value #MOTHERS} by
 *   what he saw, plants them ({@code MotherItem#useOn}), they flower, are
 *   pollinated (bees, neighbours), set seed ({@code randomTick} through
 *   {@code GrowthLogic}: seed set of the bloom index), the player harvests
 *   the seed lots and sows a bed of {@value #BED} ({@code SeedLotItem#useOn}:
 *   the seedling is realised and classified at planting).</li>
 *   <li>Acceleration only: growth by direct {@code randomTick} calls with a
 *   fixed RNG, and the flight of the bees: the first generation of a gate
 *   run has real bees from a hive; in the others a bee entity is carried
 *   from mother to mother and each visit is the production
 *   {@link PollinationService#beeVisit}. Genomes and genetics parameters are
 *   never touched.</li>
 * </ul>
 *
 * <p>The player's score is what he can see (GDD §12.5 without the analyser,
 * which wave 1 does not have): lens steps of root size and sweetness
 * (1–5 each), +1 if not bitter, root colour yellow +2, orange +5, purple
 * −2; ties by the number of roots harvested, then at random.
 */
final class BreedingLine {

    static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(BreedingLine.class);

    /** Seed bed: 8 × 6. */
    static final int BED_W = 8;
    static final int BED_D = 6;
    static final int BED = BED_W * BED_D;
    /** Mother bed: 4 × 4 (pollination radius 2 reaches the neighbours). */
    static final int MOTHERS = 16;
    static final int PLATFORM_RADIUS = 20;
    /** Starter options of the starter step (worldgen §6.2 item 5), not rich. */
    static final WildPatchFeature.Options STARTER = new WildPatchFeature.Options(true,
            Optional.of(UniformInt.of(10, 14)), Optional.of(new WildPatchConfig.Stages(0.45f, 0.40f, 0.15f)),
            "meadow", 96, WildPatchFeature.Rich.NEVER);

    final ServerLevel level;
    final BlockPos centre;
    final ServerPlayer breeder;
    final RandomSource ticks;
    final SplitMix64 choice;
    final List<BlockPos> bedCells = new ArrayList<>();
    final List<BlockPos> motherCells = new ArrayList<>();
    final BlockPos hive;

    /** Mother roots of the last harvest with the player's score. */
    List<Root> roots = new ArrayList<>();
    List<BlockPos> mothersPlanted = List.of();
    int generation;
    int starterPlants;
    int easternGen = -1;
    int carrotGen = -1;
    int orangeGen = -1;
    int yellowGen = -1;
    /** Plants sown per generation, seeds harvested per generation (trace). */
    final StringBuilder trace = new StringBuilder();

    /** A mother root the player keeps, and what he saw of its plant. */
    record Root(ItemStack mother, double score, int variant, boolean bitter, int size, int sweet) {
    }

    BreedingLine(ServerLevel level, BlockPos centre, long seed, String name) {
        this.level = level;
        this.centre = centre;
        this.ticks = RandomSource.create(seed);
        this.choice = new SplitMix64(seed ^ 0x6A7EL);
        this.breeder = new ServerPlayer(level.getServer(), level,
                new GameProfile(new UUID(0x195L, seed), name.length() > 16 ? name.substring(0, 16) : name));
        breeder.setPos(Vec3.atCenterOf(centre.above()));
        breeder.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        JournalGameTests.mute(breeder);
        for (int dz = 0; dz < BED_D; dz++) {
            for (int dx = 0; dx < BED_W; dx++) {
                bedCells.add(centre.offset(2 + dx, 1, 4 + dz));
            }
        }
        for (int dz = 0; dz < 4; dz++) {
            for (int dx = 0; dx < 4; dx++) {
                motherCells.add(centre.offset(6 + dx, 1, -10 + dz));
            }
        }
        hive = centre.offset(12, 1, -9);
    }

    // ------------------------------------------------------------------ start

    /** Grass platform, the starter patch at {@code origin}, farmland of both beds. */
    WildPatchFeature.Result start(SkyPlatform sky, BlockPos origin) {
        sky.fill(Blocks.GRASS_BLOCK.defaultBlockState());
        WildPatchFeature.Result r = ModFeatures.WILD_PATCH.get().placePatch(level, level.getChunkSource().getGenerator(),
                origin, WildPatchGameTests.carrotConfig(level), STARTER);
        if (!r.placed()) {
            throw new IllegalStateException("starter patch not placed at " + origin);
        }
        BlockState farm = Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7);
        List<BlockPos> cells = new ArrayList<>(bedCells);
        cells.addAll(motherCells);
        for (BlockPos c : cells) {
            if (r.plants().contains(c)) {
                throw new IllegalStateException("starter plant on a bed cell " + c);
            }
            level.setBlock(c.below(), farm, 2);
            for (int dy = 0; dy < SkyPlatform.HEIGHT; dy++) {
                level.setBlock(c.above(dy), Blocks.AIR.defaultBlockState(), 2);
            }
        }
        starterPlants = r.plants().size();
        for (BlockPos p : r.plants()) {
            PlantBlockEntity be = (PlantBlockEntity) level.getBlockEntity(p);
            if (be == null || be.data() == null || !be.data().passport().starter()) {
                throw new IllegalStateException("not a starter plant at " + p);
            }
        }
        // generation 0: the player digs the wild roots of the patch (stages 3–4 give a mother root)
        roots = new ArrayList<>();
        for (BlockPos p : r.plants()) {
            harvestRoot(p).ifPresent(roots::add);
        }
        trace.append("g0 wild ").append(r.plants().size()).append(" roots ").append(roots.size());
        noteColours(0);
        return r;
    }

    // ------------------------------------------------------------------ one generation

    /** Plants the best mother roots and grows them into flower; returns the mother positions. */
    List<BlockPos> plantMothersToFlower() {
        generation++;
        roots.sort(Comparator.comparingDouble(Root::score).reversed());
        List<BlockPos> planted = new ArrayList<>();
        for (int i = 0; i < Math.min(MOTHERS, roots.size()); i++) {
            BlockPos at = motherCells.get(i);
            if (!use(roots.get(i).mother(), at)) {
                throw new IllegalStateException("mother root not planted at " + at + " (gen " + generation + ")");
            }
            planted.add(at);
        }
        for (BlockPos p : planted) {
            growTo(p, FieldPlantBlock.FLOWER_STAGE);
        }
        mothersPlanted = planted;
        return planted;
    }

    /**
     * The flight of a bee, accelerated: a bee entity (not in the world, so no
     * AI) visits the flowering mothers in a random order, twice round; every
     * visit is the production {@link PollinationService#beeVisit} (donor from
     * the pollen of the plant before, the plant marked bee-visited, the
     * pollen replaced).
     */
    void beeRound(List<BlockPos> mothers) {
        Bee bee = EntityType.BEE.create(level);
        if (bee == null) {
            throw new IllegalStateException("no bee");
        }
        try {
            for (int round = 0; round < 2; round++) {
                List<BlockPos> order = new ArrayList<>(mothers);
                for (int i = order.size() - 1; i > 0; i--) {
                    int j = choice.nextInt(i + 1);
                    BlockPos t = order.get(i);
                    order.set(i, order.get(j));
                    order.set(j, t);
                }
                for (BlockPos p : order) {
                    bee.setPos(Vec3.atCenterOf(p));
                    PollinationService.beeVisit(level, bee, p);
                }
            }
        } finally {
            bee.discard();
        }
    }

    /** Mothers visited by a bee and bee donors among their pending lots. */
    int[] beeState(List<BlockPos> mothers) {
        int visited = 0;
        int donors = 0;
        for (BlockPos p : mothers) {
            if (level.getBlockEntity(p) instanceof PlantBlockEntity be && be.data() != null) {
                PendingLot l = be.data().pending();
                if (l != null && l.bees()) {
                    visited++;
                    donors += (int) l.fathers().stream().filter(f -> f.donor()).count();
                }
            }
        }
        return new int[] {visited, donors};
    }

    /** Seed set of the mothers (stage 4 → 5), the seed harvest, the bed sown, grown and dug. */
    void finishGeneration(List<BlockPos> mothers) {
        for (BlockPos p : mothers) {
            growTo(p, FieldPlantBlock.SEED_STAGE);
        }
        List<ItemStack> lots = new ArrayList<>();
        int seeds = 0;
        for (BlockPos p : mothers) {
            for (ItemStack s : harvest(p)) {
                if (s.getItem() instanceof SeedLotItem) {
                    lots.add(s);
                    seeds += s.getCount();
                }
            }
        }
        // sowing: one seed of each lot in turn, so every mother has about the same share of the bed
        List<BlockPos> sown = new ArrayList<>();
        int cell = 0;
        boolean any = true;
        while (cell < bedCells.size() && any) {
            any = false;
            for (ItemStack lot : lots) {
                if (cell >= bedCells.size()) {
                    break;
                }
                if (lot.isEmpty()) {
                    continue;
                }
                BlockPos at = bedCells.get(cell);
                if (!use(lot, at)) {
                    throw new IllegalStateException("lot not planted at " + at + " (gen " + generation + ")");
                }
                any = true;
                sown.add(at);
                cell++;
                observePlanted(at);
            }
        }
        for (BlockPos p : sown) {
            growTo(p, FieldPlantBlock.HARVEST_STAGE);
        }
        List<Root> next = new ArrayList<>();
        for (BlockPos p : sown) {
            harvestRoot(p).ifPresent(next::add);
        }
        roots = next;
        trace.append(" | g").append(generation).append(" mothers ").append(mothers.size()).append(" seeds ")
                .append(seeds).append(" sown ").append(sown.size());
        noteColours(generation);
    }

    /** Runs one whole generation with the accelerated bee round. */
    void generation() {
        List<BlockPos> m = plantMothersToFlower();
        beeRound(m);
        finishGeneration(m);
    }

    boolean carrot() {
        return carrotGen >= 0;
    }

    // ------------------------------------------------------------------ the player's hands

    /** {@code useOn} of {@code stack} on the top of the farmland under {@code plant}. */
    boolean use(ItemStack stack, BlockPos plant) {
        breeder.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos farm = plant.below();
        int before = stack.getCount();
        stack.useOn(new UseOnContext(breeder, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(farm).add(0, 0.5, 0), Direction.UP, farm, false)));
        breeder.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        return stack.getCount() < before && level.getBlockState(plant).getBlock() instanceof FieldPlantBlock;
    }

    /** Random ticks with the line's RNG until {@code stage} (GrowthLogic through the block). */
    void growTo(BlockPos p, int stage) {
        for (int i = 0; i < 20_000; i++) {
            BlockState s = level.getBlockState(p);
            if (!(s.getBlock() instanceof FieldPlantBlock) || FieldPlantBlock.stage(s) >= stage) {
                return;
            }
            s.randomTick(level, p, ticks);
        }
        throw new IllegalStateException("plant at " + p + " did not reach stage " + stage);
    }

    /** The player breaks the plant; returns what dropped (item entities picked up). */
    List<ItemStack> harvest(BlockPos p) {
        if (!breeder.gameMode.destroyBlock(p)) {
            throw new IllegalStateException("not broken at " + p);
        }
        List<ItemStack> out = new ArrayList<>();
        for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, new AABB(p).inflate(0.75))) {
            out.add(e.getItem().copy());
            e.discard();
        }
        return out;
    }

    /** Lens, colour, then the harvest of a plant at stage 3–4: its mother root with the score. */
    Optional<Root> harvestRoot(BlockPos p) {
        BlockState st = level.getBlockState(p);
        if (!(st.getBlock() instanceof FieldPlantBlock) || FieldPlantBlock.stage(st) >= FieldPlantBlock.SEED_STAGE) {
            if (st.getBlock() instanceof FieldPlantBlock) {
                harvest(p);
            }
            return Optional.empty();
        }
        List<DispCodec.Line> lines = Lens.inspectPlant(level, p, breeder).orElse(List.of());
        int size = 1;
        int sweet = 1;
        boolean bitter = true;
        for (DispCodec.Line l : lines) {
            if (l.key().equals(LensReading.TRAIT) && l.args().size() == 2) {
                String w = l.args().get(1);
                int step = Character.digit(w.charAt(w.length() - 1), 10);
                if (w.contains("tooltip.scale.size.")) {
                    size = step;
                } else if (w.contains("tooltip.scale.sweetness.")) {
                    sweet = step;
                }
            } else if (l.key().equals("wildroots.tooltip.qual_state.bitterness.domestic")) {
                bitter = false;
            }
        }
        int variant = st.getValue(FieldPlantBlock.VARIANT);
        ItemStack mother = ItemStack.EMPTY;
        int food = 0;
        for (ItemStack s : harvest(p)) {
            if (s.getItem() instanceof MotherItem) {
                mother = s;
            } else if (!(s.getItem() instanceof SeedLotItem)) {
                food += s.getCount();
            }
        }
        if (mother.isEmpty()) {
            return Optional.empty();
        }
        double colour = switch (variant) {
            case 1 -> 2;
            case 2 -> 5;
            case 3 -> -2;
            default -> 0;
        };
        double score = size + sweet + (bitter ? 0 : 1) + colour + 0.01 * food + 1e-4 * choice.nextDouble();
        return Optional.of(new Root(mother, score, variant, bitter, size, sweet));
    }

    void noteColours(int gen) {
        for (Root r : roots) {
            if (r.variant() == 2 && orangeGen < 0) {
                orangeGen = gen;
            }
            if (r.variant() >= 1 && r.variant() <= 2 && yellowGen < 0) {
                yellowGen = gen;
            }
        }
    }

    /** Classification at planting (the seedling's status), credited to the planter. */
    void observePlanted(BlockPos at) {
        if (!(level.getBlockEntity(at) instanceof PlantBlockEntity be) || be.data() == null) {
            return;
        }
        PlantState d = be.data();
        if (d.cultivar() == null || !d.hasPlanter() || !d.planter().equals(breeder.getUUID())) {
            return;
        }
        if (d.cultivar().equals("wildroots:eastern_carrot") && easternGen < 0) {
            easternGen = generation;
        }
        if (d.cultivar().equals("wildroots:carrot") && carrotGen < 0) {
            carrotGen = generation;
            if (easternGen < 0) {
                easternGen = generation;
            }
        }
    }

    String summary() {
        return String.format(java.util.Locale.ROOT, "starter %d plants, gens %d, eastern %d, carrot %d, yellow root %d,"
                + " orange root %d", starterPlants, generation, easternGen, carrotGen, yellowGen, orangeGen);
    }

    void close() {
        breeder.discard();
    }
}
