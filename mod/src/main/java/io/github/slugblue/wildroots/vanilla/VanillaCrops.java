package io.github.slugblue.wildroots.vanilla;

import io.github.slugblue.wildroots.core.Ids;
import io.github.slugblue.wildroots.core.config.ServerConfig;
import io.github.slugblue.wildroots.core.line.LineManifest;
import io.github.slugblue.wildroots.core.line.LineRegistry;
import io.github.slugblue.wildroots.core.rng.RngKeys;
import io.github.slugblue.wildroots.core.safety.EntryPoint;
import io.github.slugblue.wildroots.core.safety.Guarded;
import io.github.slugblue.wildroots.core.safety.LoadedLevelAccess;
import io.github.slugblue.wildroots.data.Definitions;
import io.github.slugblue.wildroots.data.DefinitionsSnapshot;
import io.github.slugblue.wildroots.genetics.api.Classification;
import io.github.slugblue.wildroots.genetics.api.CultivarDef;
import io.github.slugblue.wildroots.genetics.api.GeneticsService;
import io.github.slugblue.wildroots.genetics.api.SpeciesDef;
import io.github.slugblue.wildroots.genetics.model.Genome;
import io.github.slugblue.wildroots.genetics.model.TraitId;
import io.github.slugblue.wildroots.genetics.phenotype.NoiseVector;
import io.github.slugblue.wildroots.genetics.population.VillageTemplate;
import io.github.slugblue.wildroots.genetics.random.Hash64;
import io.github.slugblue.wildroots.plant.FieldPlantBlock;
import io.github.slugblue.wildroots.plant.GrowthLogic;
import io.github.slugblue.wildroots.plant.Passport;
import io.github.slugblue.wildroots.plant.PlantBlockEntity;
import io.github.slugblue.wildroots.plant.PlantService;
import io.github.slugblue.wildroots.worldgen.WildPatchConfig;
import io.github.slugblue.wildroots.worldgen.WildPatchFeature;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.LevelEvent;

/**
 * Vanilla crops of the loaded lines (WR-162; GDD §9.1–9.2;
 * docs/05-architecture.md §11.2; SEC-23, SEC-35).
 *
 * <ul>
 *   <li><b>Planting ban</b> ({@code strict}): placing a block of
 *   {@code #wildroots:removed_vanilla_crop_blocks} by any entity, fake
 *   players included, is cancelled ({@code EntityPlaceEvent}); a right click
 *   on farmland with an item of {@code #wildroots:removed_vanilla_crops} is
 *   denied with the message "needs planting material". {@code soft} lets the
 *   player plant them; they grow as vanilla.</li>
 *   <li><b>Conversion</b> ({@code strict}): a vanilla crop of
 *   {@code #wildroots:vanilla_crop/<species>} that gets a growth tick
 *   ({@code CropGrowEvent.Pre}) is replaced by a live plant of the species.
 *   It is a <b>village landrace</b> ({@code village = true}) only if the
 *   chunk's {@link LineMarker} lacks the line <b>and</b> a
 *   {@code minecraft:meeting} POI (a bell) is within {@value #MEETING_RADIUS}
 *   blocks (cached per chunk for {@value #POI_CACHE_TICKS} ticks); otherwise a
 *   wild plant of the biome with the {@code wild} passport. At most
 *   {@value #MAX_PER_TICK} conversions per level per tick; the others wait
 *   for their next growth tick. No scan, no mixin.</li>
 * </ul>
 */
public final class VanillaCrops {

    public static final TagKey<Block> REMOVED_BLOCKS = TagKey.create(Registries.BLOCK,
            Ids.mod("removed_vanilla_crop_blocks"));
    public static final TagKey<Item> REMOVED_ITEMS = TagKey.create(Registries.ITEM, Ids.mod("removed_vanilla_crops"));
    public static final String NEEDS_MATERIAL = "wildroots.message.needs_planting_material";
    public static final int MAX_PER_TICK = 16;
    public static final int MEETING_RADIUS = 32;
    public static final long POI_CACHE_TICKS = 1200;
    /** "Key to the final" frequencies of village landraces (GDD §9.5): the carrot's {@code y2} 0.30. */
    static final Map<String, Map<TraitId, Double>> VILLAGE_KEY_FREQ = Map.of(
            "wildroots:wild_carrot", Map.of(TraitId.of("color_y2"), 0.30));

    private record PerLevel(TickBudget budget, TimedFlagCache meeting) {
    }

    private static final Map<ServerLevel, PerLevel> LEVELS = new WeakHashMap<>();

    private VanillaCrops() {
    }

    /** {@code strict} (the default) also before the server config is loaded (the client side of an event). */
    static boolean strict() {
        return !ServerConfig.SPEC.isLoaded() || ServerConfig.MODE.get() == ServerConfig.Mode.STRICT;
    }

    private static PerLevel perLevel(ServerLevel level) {
        return LEVELS.computeIfAbsent(level, l -> new PerLevel(new TickBudget(MAX_PER_TICK),
                new TimedFlagCache(POI_CACHE_TICKS)));
    }

    /** Tests: forget budgets and POI answers. */
    public static void clearCaches() {
        LEVELS.clear();
    }

    @EntryPoint
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel l) {
            Guarded.run("vanilla crop level unload", "level", () -> LEVELS.remove(l));
        }
    }

    // ------------------------------------------------------------------ planting ban

    @EntryPoint
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (placesRemovedCrop(event)) {
            // a failure keeps the ban: strict is the default
            if (Guarded.call("vanilla crop place", event.getPos(), VanillaCrops::strict, true)) {
                event.setCanceled(true);
            }
        }
    }

    /**
     * The placed block, or for a multi-place any of its blocks, is a removed
     * vanilla crop. Forge posts every snapshot captured since the last
     * placement as one multi-place event, so the crop need not be the first.
     */
    static boolean placesRemovedCrop(BlockEvent.EntityPlaceEvent event) {
        if (event.getPlacedBlock().is(REMOVED_BLOCKS)) {
            return true;
        }
        return event instanceof BlockEvent.EntityMultiPlaceEvent m
                && m.getReplacedBlockSnapshots().stream().anyMatch(s -> s.getCurrentBlock().is(REMOVED_BLOCKS));
    }

    @EntryPoint
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack stack = event.getItemStack();
        if (stack.is(REMOVED_ITEMS)) {
            Guarded.run("vanilla crop use", event.getPos(), () -> denyOnFarmland(event));
        }
    }

    static void denyOnFarmland(PlayerInteractEvent.RightClickBlock event) {
        if (!strict()) {
            return;
        }
        BlockState target = event.getLevel().getBlockState(event.getPos());
        if (!(target.getBlock() instanceof FarmBlock)) {
            return;
        }
        event.setUseItem(Event.Result.DENY);
        if (event.getEntity() instanceof ServerPlayer p && p.connection != null) {
            p.displayClientMessage(Component.translatable(NEEDS_MATERIAL), true);
        }
    }

    // ------------------------------------------------------------------ conversion

    @EntryPoint
    public static void onCropGrow(BlockEvent.CropGrowEvent.Pre event) {
        BlockState state = event.getState();
        if (!(event.getLevel() instanceof ServerLevel level) || state.getBlock() instanceof FieldPlantBlock
                || !state.is(REMOVED_BLOCKS)) {
            return;
        }
        BlockPos pos = event.getPos();
        Guarded.run("vanilla crop conversion", pos, () -> {
            if (!strict()) {
                return;
            }
            Optional<FieldPlantBlock> block = replacement(state);
            if (block.isEmpty()) {
                return;
            }
            // the crop does not grow as vanilla; it waits for its next tick when the budget is spent
            event.setResult(Event.Result.DENY);
            if (perLevel(level).budget().tryTake(level.getGameTime())) {
                convert(level, pos, state, block.get());
            }
        });
    }

    /** The plant block whose {@code #wildroots:vanilla_crop/<species>} holds this vanilla crop. */
    public static Optional<FieldPlantBlock> replacement(BlockState state) {
        for (FieldPlantBlock b : PlantService.blocks()) {
            ResourceLocation id = ResourceLocation.tryParse(b.species());
            if (id != null && state.is(TagKey.create(Registries.BLOCK, Ids.mod("vanilla_crop/" + id.getPath())))) {
                return Optional.of(b);
            }
        }
        return Optional.empty();
    }

    /** Stage of the plant for a vanilla age: ripe (7) is harvest ripeness, else 0–2 by age. */
    public static int stageOf(int age, int maxAge) {
        if (maxAge <= 0 || age >= maxAge) {
            return FieldPlantBlock.HARVEST_STAGE;
        }
        return Math.max(0, Math.min(FieldPlantBlock.HARVEST_STAGE - 1, age * FieldPlantBlock.HARVEST_STAGE / maxAge));
    }

    /** The line whose {@code vanilla_crops} holds the item of this vanilla crop. */
    static Optional<String> lineOf(BlockState state) {
        ResourceLocation item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(state.getBlock().asItem());
        return LineRegistry.get().enabled().stream().filter(l -> l.vanillaCrops().contains(item))
                .map(LineManifest.Line::id).findFirst();
    }

    /** Whether the field may become a village landrace (SEC-23): old chunk without the line, and a bell near. */
    public static boolean villageField(ServerLevel level, BlockPos pos, BlockState state) {
        Optional<String> line = lineOf(state);
        if (line.isEmpty() || !LoadedLevelAccess.isLoaded(level, pos)) {
            return false;
        }
        LevelChunk chunk = level.getChunkAt(pos);
        boolean generatedWithLine = LineMarker.of(chunk).map(m -> m.has(line.get())).orElse(false);
        if (generatedWithLine) {
            return false;
        }
        ChunkPos cp = chunk.getPos();
        return perLevel(level).meeting().get(cp.toLong(), level.getGameTime(), () -> meetingNear(level, pos));
    }

    /** A {@code minecraft:meeting} POI (a village bell) within {@value #MEETING_RADIUS} blocks. */
    public static boolean meetingNear(ServerLevel level, BlockPos pos) {
        return level.getPoiManager().getInRange(t -> t.is(PoiTypes.MEETING), pos, MEETING_RADIUS,
                PoiManager.Occupancy.ANY).findAny().isPresent();
    }

    /** Replaces the vanilla crop by a live plant: a village landrace or a wild plant of the biome. */
    static void convert(ServerLevel level, BlockPos pos, BlockState vanilla, FieldPlantBlock block) {
        DefinitionsSnapshot snap = Definitions.current();
        Optional<DefinitionsSnapshot.SpeciesEntry> e = snap.species(block.species());
        if (e.isEmpty()) {
            return;
        }
        int age = vanilla.getBlock() instanceof CropBlock c ? c.getAge(vanilla) : 0;
        int maxAge = vanilla.getBlock() instanceof CropBlock c ? c.getMaxAge() : 0;
        int stage = stageOf(age, maxAge);
        Optional<VillagePlant> village = villageField(level, pos, vanilla)
                ? villagePlant(level, pos, snap, e.get().def()) : Optional.empty();
        BlockState state = block.getStateForAge(stage).setValue(FieldPlantBlock.VILLAGE, village.isPresent());
        // flag 2 as vanilla growth: clients see it, no block updates; neighbour shapes update as on growth
        level.setBlock(pos, state, 2);
        Optional<PlantBlockEntity> be = LoadedLevelAccess.blockEntity(level, pos, PlantBlockEntity.class);
        if (be.isEmpty()) {
            return;
        }
        if (village.isPresent()) {
            VillagePlant v = village.get();
            be.get().init(snap.genetics(), new PlantBlockEntity.Init(block.species(), v.genome(), v.noise(),
                    v.passport(), null, v.status(), null, 0, 0, 0));
        } else {
            be.get().initWild(block.species(), Passport.wild(pos.asLong(), category(level, pos, block), false, false));
        }
        // realise now, so size, colour and the growth chance of the stage are right from the start
        be.get().live(level).ifPresent(live -> GrowthLogic.advance(level, pos, state, be.get(), live, stage));
    }

    /** Patch category of the biome at {@code pos} by the species' wild patch feature; {@code ""} if none. */
    static String category(ServerLevel level, BlockPos pos, FieldPlantBlock block) {
        ResourceLocation sp = ResourceLocation.tryParse(block.species());
        if (sp == null) {
            return "";
        }
        ResourceKey<ConfiguredFeature<?, ?>> key = ResourceKey.create(Registries.CONFIGURED_FEATURE,
                Ids.mod("wild_patch/" + sp.getPath()));
        return level.registryAccess().registry(Registries.CONFIGURED_FEATURE).flatMap(r -> r.getOptional(key))
                .filter(f -> f.config() instanceof WildPatchConfig)
                .map(f -> WildPatchFeature.category(level.getBiome(pos), ((WildPatchConfig) f.config()).categories()))
                .orElse("");
    }

    record VillagePlant(Genome genome, NoiseVector noise, Passport passport, Classification status) {
    }

    /**
     * The village landrace genome of a cell (GDD §9.5): the first tier-1
     * profile of the species (by id), its template with the species' key
     * frequencies, key {@code Hash64(worldSeed, cellPos, landraceId, 0)}.
     */
    static Optional<VillagePlant> villagePlant(ServerLevel level, BlockPos pos, DefinitionsSnapshot snap, SpeciesDef def) {
        List<CultivarDef> profiles = snap.profiles(def.id());
        Optional<CultivarDef> landrace = profiles.stream().filter(c -> c.tier() == 1)
                .min(Comparator.comparing(c -> c.id().value()));
        if (landrace.isEmpty()) {
            return Optional.empty();
        }
        GeneticsService gs = snap.genetics();
        String id = landrace.get().id().value();
        VillageTemplate t = VillageTemplate.fromLandrace(def, landrace.get(),
                VILLAGE_KEY_FREQ.getOrDefault(def.id(), Map.of()), Map.of());
        long seed = level.getSeed();
        Optional<Genome> g = gs.villagePlant(def, t, RngKeys.villagePlant(seed, pos.asLong(), id, 0));
        Optional<NoiseVector> n = gs.rollNoise(def, Hash64.of(seed, pos.asLong(), Hash64.str(id), Hash64.str("noise")));
        if (g.isEmpty() || n.isEmpty()) {
            return Optional.empty();
        }
        Classification c = gs.classify(g.get(), def, profiles, Optional.empty()).orElse(Classification.WILD);
        return Optional.of(new VillagePlant(g.get(), n.get(),
                new Passport(Passport.Origin.VILLAGE, 0, "", false, false, id, pos.asLong(), 0), c));
    }
}
