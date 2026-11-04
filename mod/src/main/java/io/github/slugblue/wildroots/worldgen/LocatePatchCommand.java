package io.github.slugblue.wildroots.worldgen;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import io.github.slugblue.wildroots.core.Ids;
import io.github.slugblue.wildroots.core.safety.EntryPoint;
import io.github.slugblue.wildroots.core.safety.Guarded;
import io.github.slugblue.wildroots.data.Definitions;
import io.github.slugblue.wildroots.genetics.api.SpeciesDef;
import io.github.slugblue.wildroots.plant.ClimateService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraftforge.event.RegisterCommandsEvent;

/**
 * {@code /wildroots locate_patch <species> [radius]} (worldgen §4.2, §13.2;
 * architecture §16 "Commands"; SEC-46): the nearest candidate chunk of a
 * species without generating anything — the chunk band of its placed
 * feature ({@code hash(worldSeed, chunk)}), then the biome of the chunk
 * centre from the biome source (no chunk is loaded) against the tag
 * {@code wildroots:spawns/<species>} and the species temperature window.
 * A candidate is where a patch <i>may</i> generate; the surface, the
 * proximity and the plant count decide later.
 *
 * <p>The root node {@code wildroots} requires {@code permission ≥ 2}.
 * Arguments are validated: a radius out of {@code 16…}{@value #MAX_RADIUS}
 * is refused by the parser, an unknown species gets a message, never an
 * exception.
 */
public final class LocatePatchCommand {

    public static final int DEFAULT_RADIUS = 1024;
    public static final int MAX_RADIUS = 6400;

    private LocatePatchCommand() {
    }

    /** Forge bus. */
    @EntryPoint
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        Guarded.run("register commands", "wildroots", () -> register(event.getDispatcher()));
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("wildroots")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("locate_patch")
                        .then(Commands.argument("species", ResourceLocationArgument.id())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                        Definitions.current().species().keySet(), b))
                                .executes(c -> run(c, DEFAULT_RADIUS))
                                .then(Commands.argument("radius", IntegerArgumentType.integer(16, MAX_RADIUS))
                                        .executes(c -> run(c, IntegerArgumentType.getInteger(c, "radius")))))));
    }

    @EntryPoint
    static int run(CommandContext<CommandSourceStack> c, int radius) {
        CommandSourceStack src = c.getSource();
        ResourceLocation id = c.getArgument("species", ResourceLocation.class);
        return Guarded.call("locate_patch", id, () -> locate(src, id, radius), 0);
    }

    /** A candidate chunk and its distance. */
    public record Found(int chunkX, int chunkZ, BlockPos center, int distance) {
    }

    static int locate(CommandSourceStack src, ResourceLocation id, int radius) {
        Optional<SpeciesDef> def = Definitions.current().species(id.toString()).map(e -> e.def());
        List<Band> bands = def.isEmpty() ? List.of() : bands(src.getLevel(), id.toString());
        if (def.isEmpty() || bands.isEmpty()) {
            src.sendFailure(Component.translatable("commands.wildroots.locate_patch.unknown", id.toString()));
            return 0;
        }
        BlockPos from = BlockPos.containing(src.getPosition());
        Optional<Found> f = nearest(src.getLevel(), from, def.get(), bands, radius,
                TagKey.create(Registries.BIOME, Ids.of(id.getNamespace(), "spawns/" + id.getPath())));
        if (f.isEmpty()) {
            src.sendFailure(Component.translatable("commands.wildroots.locate_patch.not_found", id.toString(), radius));
            return 0;
        }
        BlockPos p = f.get().center();
        Component coords = ComponentUtils.wrapInSquareBrackets(Component.translatable("chat.coordinates", p.getX(), "~",
                p.getZ())).withStyle(s -> s.withColor(ChatFormatting.GREEN)
                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                        "/tp @s " + p.getX() + " ~ " + p.getZ()))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.translatable("chat.coordinates.tooltip"))));
        src.sendSuccess(() -> Component.translatable("commands.wildroots.locate_patch.success", id.toString(), coords,
                f.get().distance()), false);
        return Math.max(1, f.get().distance());
    }

    /** The chunk band and climate tolerance of one placed feature of the species. */
    record Band(ChunkBandPlacement band, double tolerance) {
    }

    /** Placed features whose {@code wild_patch} configuration is of {@code species} and that have a chunk band. */
    public static List<Band> bands(ServerLevel level, String species) {
        List<Band> out = new ArrayList<>();
        for (PlacedFeature pf : level.registryAccess().registryOrThrow(Registries.PLACED_FEATURE)) {
            if (!(pf.feature().value().config() instanceof WildPatchConfig c) || !c.species().equals(species)) {
                continue;
            }
            ChunkBandPlacement band = null;
            double tolerance = 0;
            for (PlacementModifier m : pf.placement()) {
                if (m instanceof ChunkBandPlacement b) {
                    band = b;
                } else if (m instanceof SpeciesClimatePlacement s) {
                    tolerance = s.tolerance();
                }
            }
            if (band != null) {
                out.add(new Band(band, tolerance));
            }
        }
        return out;
    }

    /**
     * The Euclidean nearest candidate chunk within {@code radius} blocks
     * (review 1, m7). Square rings of chunks are walked outwards; after the
     * first hit the walk goes on while a ring can still hold a nearer chunk
     * (its nearest centre is at least {@code 16·r − 16} blocks away). The
     * biome is sampled at the surface the noise generator predicts for the
     * chunk centre ({@code getBaseHeight}, pure noise, no chunk access), at
     * least at sea level, so cave biomes under hills do not hide a
     * candidate. Only chunks that pass the band hash (≈ 3.6 % for carrot)
     * pay for that height.
     */
    static Optional<Found> nearest(ServerLevel level, BlockPos from, SpeciesDef def, List<Band> bands, int radius,
                                   TagKey<Biome> spawns) {
        long seed = level.getSeed();
        int cx0 = from.getX() >> 4;
        int cz0 = from.getZ() >> 4;
        int rings = Math.max(0, radius >> 4);
        var generator = level.getChunkSource().getGenerator();
        var randomState = level.getChunkSource().randomState();
        var source = generator.getBiomeSource();
        var sampler = randomState.sampler();
        int sea = generator.getSeaLevel();
        Found best = null;
        for (int r = 0; r <= rings; r++) {
            if (best != null && 16L * r - 16 > best.distance()) {
                break;
            }
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    int cx = cx0 + dx;
                    int cz = cz0 + dz;
                    Band band = null;
                    for (Band b : bands) {
                        if (b.band().candidate(seed, cx, cz)) {
                            band = b;
                            break;
                        }
                    }
                    if (band == null) {
                        continue;
                    }
                    int x = (cx << 4) + 8;
                    int z = (cz << 4) + 8;
                    int d = (int) Math.round(Math.sqrt(Math.pow(x - from.getX(), 2) + Math.pow(z - from.getZ(), 2)));
                    if (d > radius || (best != null && d >= best.distance())) {
                        continue;
                    }
                    int y = Math.max(sea, generator.getBaseHeight(x, z,
                            net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG, level, randomState));
                    BlockPos c = new BlockPos(x, y, z);
                    Holder<Biome> biome = source.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y),
                            QuartPos.fromBlock(z), sampler);
                    if (!biome.is(spawns) || !ClimateWindow.fits(ClimateService.temperature(biome, c), def.tempMin(),
                            def.tempMax(), band.tolerance(), SpeciesClimatePlacement.noisy(biome, c))) {
                        continue;
                    }
                    best = new Found(cx, cz, c, d);
                }
            }
        }
        return Optional.ofNullable(best);
    }
}
