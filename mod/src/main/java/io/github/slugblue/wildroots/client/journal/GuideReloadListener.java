package io.github.slugblue.wildroots.client.journal;

import com.mojang.logging.LogUtils;
import io.github.slugblue.wildroots.core.safety.LogThrottle;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

/**
 * Client resource reload of the guide (journal v2 §5.2; WR-181a): reads
 * {@code assets/wildroots/journal/**} with {@link GuideLoader} and every
 * registered {@link GuideSection} on the reload worker, then swaps them in
 * on the render thread. F3+T reloads the book and the scenes.
 */
@OnlyIn(Dist.CLIENT)
public final class GuideReloadListener extends SimplePreparableReloadListener<GuideReloadListener.Prepared> {

    public static final GuideReloadListener INSTANCE = new GuideReloadListener();

    private static final Logger LOGGER = LogUtils.getLogger();
    /** One line per distinct problem per window (like {@code JournalTriggers.ID_WARN}). */
    private static final LogThrottle WARN = new LogThrottle(System::currentTimeMillis, 5 * 60 * 1000L);
    private static final List<GuideSection<?>> SECTIONS = new CopyOnWriteArrayList<>();

    /** What the worker prepared: the book, each section's value, the log lines. */
    public record Prepared(GuideBook book, Map<GuideSection<?>, Object> sections, List<String> log) {
    }

    private GuideReloadListener() {
    }

    /** Adds a folder that reloads with the guide (the scene engine's {@code scenes}); client setup only. */
    public static void addSection(GuideSection<?> section) {
        SECTIONS.add(section);
    }

    @Override
    protected Prepared prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, IoSupplier<InputStream>> files = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, Resource> e
                : manager.listResources(GuideLoader.ROOT, rl -> rl.getPath().endsWith(".json")).entrySet()) {
            files.put(e.getKey(), e.getValue()::open);
        }
        List<String> log = new ArrayList<>();
        GuideLoader.Registries reg = ForgeRegistries.ITEMS::containsKey;
        GuideLoader.Result result = GuideLoader.load(files, reg);
        log.addAll(result.log());
        Map<GuideSection<?>, Object> prepared = new LinkedHashMap<>();
        for (GuideSection<?> s : SECTIONS) {
            try {
                Map<ResourceLocation, byte[]> own = GuideFiles.read(files, s.folder(), s.maxFileBytes(), s.maxFiles(),
                        s.maxTotalBytes(), log);
                prepared.put(s, s.prepare(own, log));
            } catch (Exception | StackOverflowError e) {
                log.add("journal/" + s.folder() + ": failed (" + GuideLoader.message(e) + ")");
            }
        }
        return new Prepared(result.book(), prepared, GuideLoader.capLog(log, "journal/"));
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    protected void apply(Prepared prepared, ResourceManager manager, ProfilerFiller profiler) {
        for (String line : prepared.log()) {
            if (WARN.allow(line)) {
                LOGGER.warn("Wild Roots journal: {}", line);
            }
        }
        GuideBooks.set(prepared.book());
        for (Map.Entry<GuideSection<?>, Object> e : prepared.sections().entrySet()) {
            try {
                ((GuideSection) e.getKey()).apply(e.getValue());
            } catch (RuntimeException ex) {
                LOGGER.warn("Wild Roots journal: journal/{} could not be applied: {}", e.getKey().folder(),
                        GuideLoader.message(ex));
            }
        }
        LOGGER.debug("Wild Roots journal: {} categories, {} entries", prepared.book().categories().size(),
                prepared.book().entries().size());
        GuideBooks.fireReloaded();
    }
}
