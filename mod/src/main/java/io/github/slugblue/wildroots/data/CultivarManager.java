package io.github.slugblue.wildroots.data;

import com.mojang.logging.LogUtils;
import io.github.slugblue.wildroots.core.config.CommonConfig;
import java.util.List;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import org.slf4j.Logger;

/**
 * Reload listener of {@code data/<ns>/cultivars/*.json}
 * (docs/05-architecture.md §8.1–8.2; ADR-0005). Registered after
 * {@link SpeciesManager}: vanilla runs the {@code apply} steps of reload
 * listeners one after another in registration order, so the species are
 * staged when this {@code apply} validates both sets, logs the problems and
 * the summary and swaps the {@link DefinitionsSnapshot}.
 *
 * <p>{@code /reload} changes thresholds and layouts lazily: plants and items
 * pick the new snapshot at their next check (planting, ripening), nothing
 * scans the world (§8.2).
 */
public final class CultivarManager extends SimplePreparableReloadListener<List<DefinitionLoader.Parsed<CultivarSchema.Mapped>>> {

    /** Folder under {@code data/<ns>/}. */
    public static final String FOLDER = "cultivars";

    private static final Logger LOGGER = LogUtils.getLogger();

    private final SpeciesManager species;

    CultivarManager(SpeciesManager species) {
        this.species = species;
    }

    /** Parsed cultivars of {@code rm}. */
    public List<DefinitionLoader.Parsed<CultivarSchema.Mapped>> read(ResourceManager rm) {
        return DefinitionLoader.parseCultivars(SpeciesManager.files(rm, FOLDER));
    }

    @Override
    protected List<DefinitionLoader.Parsed<CultivarSchema.Mapped>> prepare(ResourceManager rm, ProfilerFiller profiler) {
        return read(rm);
    }

    @Override
    protected void apply(List<DefinitionLoader.Parsed<CultivarSchema.Mapped>> parsed, ResourceManager rm,
                         ProfilerFiller profiler) {
        SpeciesManager.Prepared staged = species.staged();
        DefinitionLoader.LoadResult result = DefinitionLoader.assemble(staged.species(), staged.layouts(), parsed);
        Definitions.publish(result.snapshot());
        log(result, LOGGER);
    }

    /** At most this many problem lines per load go to the log (SEC-27). */
    public static final int MAX_LOGGED_PROBLEMS = 200;

    /** The first {@value #MAX_LOGGED_PROBLEMS} lines, then one line {@code … and N more problems}. */
    public static List<DefinitionLoader.LogLine> limited(List<DefinitionLoader.LogLine> lines) {
        if (lines.size() <= MAX_LOGGED_PROBLEMS) {
            return lines;
        }
        List<DefinitionLoader.LogLine> out = new java.util.ArrayList<>(lines.subList(0, MAX_LOGGED_PROBLEMS));
        out.add(new DefinitionLoader.LogLine(true,
                "Wild Roots: ... and " + (lines.size() - MAX_LOGGED_PROBLEMS) + " more problems"));
        return out;
    }

    /** Writes at most {@value #MAX_LOGGED_PROBLEMS} problem lines and the summary. */
    public static void log(DefinitionLoader.LoadResult result, Logger logger) {
        for (DefinitionLoader.LogLine line : limited(result.lines())) {
            if (line.error()) {
                logger.error(line.text());
            } else {
                logger.warn(line.text());
            }
        }
        String summary = result.summary();
        logger.info(result.snapshot().stats().hadProblems() ? summary + " — see above" : summary);
        if (debugLogging()) {
            logger.info("Wild Roots: species {}, cultivars {}", result.snapshot().species().keySet(),
                    result.snapshot().cultivars().keySet());
        }
    }

    private static boolean debugLogging() {
        try {
            return CommonConfig.DEBUG_LOG_DEFINITIONS.get();
        } catch (IllegalStateException notLoaded) {
            return false;
        }
    }

    @Override
    public String getName() {
        return "wildroots:cultivars";
    }

    /** Registers both listeners, species first (Forge event bus). */
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(Definitions.SPECIES);
        event.addListener(Definitions.CULTIVARS);
    }
}
