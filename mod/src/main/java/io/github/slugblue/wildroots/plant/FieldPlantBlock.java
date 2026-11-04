package io.github.slugblue.wildroots.plant;

import io.github.slugblue.wildroots.core.safety.EntryPoint;
import io.github.slugblue.wildroots.core.safety.Guarded;
import io.github.slugblue.wildroots.core.safety.LoadedLevelAccess;
import io.github.slugblue.wildroots.pollination.BloomTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Plant block of the {@code field} archetype (docs/05-architecture.md §7;
 * ADR-0004; ADR-0010 rev. 2): one block per built-in species, created with
 * the species id as parameter ({@code wildroots:wild_carrot}).
 *
 * <p>{@link BlockState} carries only what logic without the block entity and
 * the renderer need (§7.2): {@link #STAGE} 0–5, {@link #SIZE} 0–3,
 * {@link #VARIANT} 0–3, {@link #AILING}, {@link #VILLAGE}, {@link #ACTIVE};
 * 6 × 4 × 4 × 2 × 2 × 2 = 768 states. The vanilla {@code AGE} property is not
 * part of the state: {@link #getAgeProperty()} is {@link #STAGE}.
 *
 * <p>{@code CropBlock.isMaxAge} is {@code final} in 1.20.1, so the farmer's
 * view of ripeness lives in {@link #getAge(BlockState)} (S3); the methods of
 * {@code CropBlock} that grow or rebuild the state from {@code getAge} /
 * {@code defaultBlockState()} are overridden on the real stage, otherwise
 * they would reset {@code village}, {@code size} and {@code variant}.
 *
 * <p>No ticker: {@link #getTicker} is {@code null} (ADR-0003).
 */
public class FieldPlantBlock extends CropBlock implements EntityBlock {

    /** Growth stage (GDD §5.1): 0 seed, 1–2 vegetation, 3 harvest ripeness, 4 flowering, 5 seeds. */
    public static final IntegerProperty STAGE = IntegerProperty.create("stage", 0, 5);
    /** Model scale {@code 0.4 + 0.8·P_size} in four steps (GDD §3.3). */
    public static final IntegerProperty SIZE = IntegerProperty.create("size", 0, 3);
    /** Root or cob colour of species with colour loci. */
    public static final IntegerProperty VARIANT = IntegerProperty.create("variant", 0, 3);
    /** Vitality {@code V < 0.85} (GDD §3.7). */
    public static final BooleanProperty AILING = BooleanProperty.create("ailing");
    /** Village mark that the farmer sees (§11.6). */
    public static final BooleanProperty VILLAGE = BooleanProperty.create("village");
    /** {@code false}: static wild plant without random ticks (worldgen §12.2). */
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    public static final int MAX_STAGE = 5;
    /** Harvest ripeness of a biennial: food and mother root (GDD §5.1–5.2). */
    public static final int HARVEST_STAGE = 3;
    /** Flowering (bloom index, GDD §4.1). */
    public static final int FLOWER_STAGE = 4;
    /** Seeds ripe: the seed lot (GDD §5.3). */
    public static final int SEED_STAGE = 5;

    private final String species;

    /**
     * @param species species id of this block ({@code wildroots:wild_carrot})
     */
    public FieldPlantBlock(String species, Properties properties) {
        super(properties);
        this.species = species;
        registerDefaultState(stateDefinition.any().setValue(STAGE, 0).setValue(SIZE, 0).setValue(VARIANT, 0)
                .setValue(AILING, false).setValue(VILLAGE, false).setValue(ACTIVE, true));
    }

    /** Species id of the block (built-in species, §7.1). */
    public String species() {
        return species;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        // not super: the vanilla AGE property (0–7) is not part of our state
        builder.add(STAGE, SIZE, VARIANT, AILING, VILLAGE, ACTIVE);
    }

    /**
     * Farmland, or the wild soil of the species ({@code #wildroots:wild_soil/<species>},
     * worldgen §2.4): a wild patch stands on grass, dirt or podzol and must
     * not pop on the first neighbour update. Planting by players still
     * needs farmland ({@code PlantService#canPlantAt}).
     */
    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return super.mayPlaceOn(state, level, pos) || state.is(wildSoil());
    }

    private net.minecraft.tags.TagKey<Block> wildSoil;

    /** {@code #wildroots:wild_soil/<path of the species id>}. */
    public net.minecraft.tags.TagKey<Block> wildSoil() {
        if (wildSoil == null) {
            net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(species);
            String path = id == null ? "none" : id.getPath();
            wildSoil = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK,
                    io.github.slugblue.wildroots.core.Ids.mod("wild_soil/" + path));
        }
        return wildSoil;
    }

    @Override
    protected IntegerProperty getAgeProperty() {
        return STAGE;
    }

    @Override
    public int getMaxAge() {
        return MAX_STAGE;
    }

    public static int stage(BlockState state) {
        return state.getValue(STAGE);
    }

    /**
     * Farmer view (S3, ADR-0010): the maximum age only for a village plant at
     * harvest ripeness, otherwise at most {@code max − 1}, so that
     * {@code isMaxAge} is never true for a player's plant.
     */
    @Override
    public int getAge(BlockState state) {
        int stage = stage(state);
        return state.getValue(VILLAGE) && stage >= HARVEST_STAGE ? getMaxAge() : Math.min(stage, getMaxAge() - 1);
    }

    /** Keeps {@code active}; the other properties are set by the growth logic on stage change. */
    @Override
    public BlockState getStateForAge(int age) {
        return defaultBlockState().setValue(STAGE, Math.max(0, Math.min(MAX_STAGE, age)));
    }

    /** {@code active} and the stage is not final (§10.1). */
    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return state.getValue(ACTIVE) && stage(state) < MAX_STAGE;
    }

    /** Growth by the cached chance of the block entity (§10.1, {@link GrowthLogic}). */
    @Override
    @EntryPoint
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        Guarded.run("plant random tick", pos, () -> GrowthLogic.randomTick(state, level, pos, random),
                () -> fail(level, pos, "random tick failed"));
    }

    /** One stage without a roll (other mods); never past the final stage, keeps every property. */
    @Override
    @EntryPoint
    public void growCrops(Level level, BlockPos pos, BlockState state) {
        if (level instanceof ServerLevel server) {
            Guarded.run("plant grow", pos, () -> GrowthLogic.grow(server, pos, state),
                    () -> fail(server, pos, "growth failed"));
        }
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return true;
    }

    /** +1 stage with chance 0.35 on stages 1–3 (GDD §5.1); flowering always takes real time. */
    @Override
    @EntryPoint
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        Guarded.run("plant bone meal", pos, () -> GrowthLogic.bonemeal(level, random, pos, state),
                () -> fail(level, pos, "bone meal failed"));
    }

    /** SEC-34: a plant whose logic threw becomes inert, so it does not throw on every tick. */
    private static void fail(ServerLevel level, BlockPos pos, String reason) {
        LoadedLevelAccess.blockEntity(level, pos, PlantBlockEntity.class).ifPresent(be -> be.markFailed(reason));
    }

    /**
     * The real break path: once the plant block is really replaced by
     * another block, the {@code mutation} rare flag is consumed (SEC-21).
     * Vanilla calls this before the drops on a survival player's break
     * ({@code ServerPlayerGameMode#destroyBlock}: {@code removeBlock}, then
     * {@code playerDestroy}) and after them in {@code Level#destroyBlock},
     * explosions, pistons and fluids; the block entity remembers a claim
     * made here ({@code claimedByRemoval}) so the drops get the flag exactly
     * once in both orders. {@code getDrops} has no side effects. A stage
     * change ({@code setBlock} with the same block) claims nothing.
     */
    @Override
    @EntryPoint
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel server) {
            Guarded.run("plant removed", pos, () -> LoadedLevelAccess.blockEntity(server, pos, PlantBlockEntity.class)
                    .ifPresent(PlantBlockEntity::claimForRemoval));
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    /**
     * Entering and leaving flowering keeps the bloom index in step
     * (docs/05-architecture.md §10.2): every {@code setBlock} that changes
     * the stage passes here, whoever makes it (growth, bone meal, other
     * mods, commands). Removal of the block is handled by the block entity
     * ({@code setRemoved}).
     */
    @Override
    @EntryPoint
    @SuppressWarnings("deprecation")
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (level instanceof ServerLevel server) {
            boolean was = oldState.is(this) && stage(oldState) == FLOWER_STAGE;
            boolean now = stage(state) == FLOWER_STAGE;
            if (was != now) {
                Guarded.run("plant bloom", pos, () -> BloomTracker.set(server, pos, species, now));
            }
        }
    }

    /**
     * Bone meal works on stages 1–3, never on flowering (GDD §5.1); stage 5 is
     * final for the field archetype, so it is refused there too (GD question).
     */
    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state, boolean isClient) {
        return bonemealStage(stage(state));
    }

    /** Stages on which bone meal may advance the plant: 1–3; 4 (flowering) and the final 5 never. */
    public static boolean bonemealStage(int stage) {
        return stage >= 1 && stage <= HARVEST_STAGE;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[stage(state)];
    }

    private static final VoxelShape[] SHAPES = new VoxelShape[MAX_STAGE + 1];

    static {
        for (int i = 0; i <= MAX_STAGE; i++) {
            SHAPES[i] = Block.box(0, 0, 0, 16, 2 + 2 * i, 16);
        }
    }

    /** Drops from the block entity (HarvestService); vanilla loot tables are not used. */
    @Override
    @EntryPoint
    @SuppressWarnings("deprecation")
    public java.util.List<net.minecraft.world.item.ItemStack> getDrops(BlockState state,
            net.minecraft.world.level.storage.loot.LootParams.Builder params) {
        return Guarded.call("plant drops", "plant", () -> HarvestService.drops(state, params), java.util.List.of());
    }

    /** Pick block gives a seed lot item (empty lot: inert until filled by the server). */
    @Override
    protected net.minecraft.world.level.ItemLike getBaseSeedId() {
        return io.github.slugblue.wildroots.core.registry.ModItems.SEED_LOT.get();
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PlantBlockEntity(pos, state);
    }

    /** No ticker (ADR-0003): the plant works only in random ticks, interaction and saving. */
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        return null;
    }
}
