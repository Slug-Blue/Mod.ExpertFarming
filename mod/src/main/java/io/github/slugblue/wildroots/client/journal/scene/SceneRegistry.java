package io.github.slugblue.wildroots.client.journal.scene;

import com.mojang.logging.LogUtils;
import io.github.slugblue.wildroots.client.journal.GuideSection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

/**
 * The loaded scenes (journal v2 §5.8): {@code journal/scenes/*.json},
 * reloaded with the guide (F3+T). Each file is checked by
 * {@link SceneParser}; a file over a limit is skipped with one log line.
 * A reload closes the live scene's GPU buffers. A scene whose rendering
 * threw stays in its 2D frame until the game restarts (§3.13).
 */
@OnlyIn(Dist.CLIENT)
public final class SceneRegistry implements GuideSection<Map<String, SceneDef<BlockState>>> {

    public static final SceneRegistry INSTANCE = new SceneRegistry();
    private static final Logger LOGGER = LogUtils.getLogger();

    private volatile Map<String, SceneDef<BlockState>> scenes = Map.of();
    private final Set<String> failed = ConcurrentHashMap.newKeySet();
    private volatile long lastLoadNanos;

    private SceneRegistry() {
    }

    @Override
    public String folder() {
        return SceneLimits.FOLDER;
    }

    @Override
    public int maxFileBytes() {
        return SceneLimits.MAX_FILE_BYTES;
    }

    @Override
    public int maxFiles() {
        return SceneLimits.MAX_FILES;
    }

    @Override
    public int maxTotalBytes() {
        return SceneLimits.MAX_TOTAL_BYTES;
    }

    @Override
    public Map<String, SceneDef<BlockState>> prepare(Map<ResourceLocation, byte[]> files, List<String> log) {
        long t0 = System.nanoTime();
        Map<String, SceneDef<BlockState>> out = SceneParser.loadAll(files, new BlockStates(), ForgeRegistries.ITEMS::containsKey, log);
        lastLoadNanos = System.nanoTime() - t0;
        return out;
    }

    @Override
    public void apply(Map<String, SceneDef<BlockState>> prepared) {
        SceneRenderer.closeLive();
        scenes = prepared;
        LOGGER.debug("Wild Roots journal: {} scenes loaded in {} µs", prepared.size(), lastLoadNanos / 1000);
    }

    public Optional<SceneDef<BlockState>> get(String id) {
        return Optional.ofNullable(scenes.get(id));
    }

    /** Rendering of {@code id} threw: 2D frame for the rest of the session; logs once. */
    public void markFailed(String id, Throwable e) {
        if (failed.add(id)) {
            LOGGER.warn("Wild Roots journal: scene {} could not be drawn, showing its 2D frame: {}", id, e.toString());
        }
    }

    public boolean failed(String id) {
        return failed.contains(id);
    }

    /** Time of the last load and check of all scenes (budget ≤ 20 ms, §5.8). */
    public long lastLoadNanos() {
        return lastLoadNanos;
    }
}
