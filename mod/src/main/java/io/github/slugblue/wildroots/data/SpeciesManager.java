package io.github.slugblue.wildroots.data;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * Reload listener of {@code data/<ns>/crop_species/*.json}
 * (docs/05-architecture.md §8.1; ADR-0005). {@code prepare} reads and maps
 * the files in the background; {@code apply} only stages the result: the
 * {@link CultivarManager}, registered right after it, validates both sets
 * together and publishes the snapshot.
 */
public final class SpeciesManager extends SimplePreparableReloadListener<List<DefinitionLoader.Parsed<SpeciesSchema.Mapped>>> {

    /** Folder under {@code data/<ns>/}. */
    public static final String FOLDER = "crop_species";

    private volatile List<DefinitionLoader.Parsed<SpeciesSchema.Mapped>> staged = List.of();

    SpeciesManager() {
    }

    /** {@code .json} files of {@code folder} in {@code rm}. */
    static Map<ResourceLocation, IoSupplier<InputStream>> files(ResourceManager rm, String folder) {
        Map<ResourceLocation, IoSupplier<InputStream>> out = new LinkedHashMap<>();
        rm.listResources(folder, rl -> rl.getPath().endsWith(".json")).forEach((rl, res) -> out.put(rl, res::open));
        return out;
    }

    /** Parsed species of {@code rm} (public for GameTests that load a test pack). */
    public List<DefinitionLoader.Parsed<SpeciesSchema.Mapped>> read(ResourceManager rm) {
        return DefinitionLoader.parseSpecies(files(rm, FOLDER));
    }

    @Override
    protected List<DefinitionLoader.Parsed<SpeciesSchema.Mapped>> prepare(ResourceManager rm, ProfilerFiller profiler) {
        return read(rm);
    }

    @Override
    protected void apply(List<DefinitionLoader.Parsed<SpeciesSchema.Mapped>> parsed, ResourceManager rm,
                         ProfilerFiller profiler) {
        staged = parsed;
    }

    /** Result of the last {@code apply}. */
    List<DefinitionLoader.Parsed<SpeciesSchema.Mapped>> staged() {
        return staged;
    }

    @Override
    public String getName() {
        return "wildroots:species";
    }
}
