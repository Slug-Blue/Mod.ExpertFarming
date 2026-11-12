package io.github.slugblue.wildroots.client.journal;

import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Another folder of {@code assets/wildroots/journal/} that reloads together
 * with the guide, in the same resource reload (F3+T): the scene engine's
 * {@code journal/scenes/} (journal v2 §5.8, WR-181k). The guide reload
 * listener reads the folder's {@code *.json} files under the section's
 * file limits (each file over {@link #maxFileBytes()}, files past
 * {@link #maxFiles()} or past {@link #maxTotalBytes()} together are
 * skipped, one log line each), hands the bytes to {@link #prepare} on the
 * reload worker thread, then calls {@link #apply} on the render thread
 * right after the new {@link GuideBook} is set and before an open journal
 * rebuilds its layout.
 *
 * <p>Register with {@link GuideReloadListener#addSection} during client
 * setup (before the first reload).
 *
 * @param <T> what {@link #prepare} builds, immutable
 */
@OnlyIn(Dist.CLIENT)
public interface GuideSection<T> {

    /** Folder under {@code journal/}, e.g. {@code scenes}. */
    String folder();

    int maxFileBytes();

    int maxFiles();

    int maxTotalBytes();

    /**
     * Parses the files (sorted by path, each within the limits). Runs off
     * the render thread; must not throw (a throw is caught, logged once,
     * and {@link #apply} is not called for this reload). Lines added to
     * {@code log} are written to the game log, throttled per line.
     */
    T prepare(Map<ResourceLocation, byte[]> files, List<String> log);

    /** Installs the prepared value; render thread. */
    void apply(T prepared);
}
