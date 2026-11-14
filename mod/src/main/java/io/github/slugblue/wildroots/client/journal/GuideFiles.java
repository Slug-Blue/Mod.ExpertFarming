package io.github.slugblue.wildroots.client.journal;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Bounded reading of one folder of {@code assets/wildroots/journal/} for a
 * {@link GuideSection}: files in path order, each at most
 * {@code maxFileBytes}, at most {@code maxFiles}, at most
 * {@code maxTotalBytes} together. Whatever is over is skipped with one log
 * line; nothing throws.
 */
@OnlyIn(Dist.CLIENT)
public final class GuideFiles {

    private GuideFiles() {
    }

    public static SortedMap<ResourceLocation, byte[]> read(Map<ResourceLocation, IoSupplier<InputStream>> files,
                                                           String folder, int maxFileBytes, int maxFiles,
                                                           int maxTotalBytes, List<String> log) {
        String prefix = GuideLoader.ROOT + "/" + folder + "/";
        SortedMap<ResourceLocation, byte[]> out = new TreeMap<>();
        long total = 0;
        boolean countWarned = false;
        boolean totalWarned = false;
        int candidates = 0;
        for (Map.Entry<ResourceLocation, IoSupplier<InputStream>> f : new TreeMap<>(files).entrySet()) {
            ResourceLocation rl = f.getKey();
            if (!rl.getNamespace().equals(GuideLoader.NAMESPACE) || !rl.getPath().startsWith(prefix)
                    || !rl.getPath().endsWith(".json")) {
                continue;
            }
            // oversized and unreadable files count too: the work per reload is bounded (review M1)
            if (++candidates > maxFiles + GuideLimits.SECTION_SLACK) {
                log.add(rl + ": more than " + (maxFiles + GuideLimits.SECTION_SLACK) + " candidate files in " + prefix
                        + "; this and later files not read");
                break;
            }
            if (out.size() >= maxFiles) {
                if (!countWarned) {
                    log.add(rl + ": more than " + maxFiles + " files in " + prefix + "; this and later files skipped");
                    countWarned = true;
                }
                continue;
            }
            byte[] bytes;
            try (InputStream in = f.getValue().get()) {
                bytes = GuideLoader.readBounded(in, maxFileBytes);
            } catch (Exception | StackOverflowError e) {
                log.add(rl + ": cannot be read (" + e.getClass().getSimpleName() + ")");
                continue;
            }
            if (bytes == null) {
                log.add(rl + ": larger than " + maxFileBytes + " bytes, skipped");
                continue;
            }
            if (total + bytes.length > maxTotalBytes) {
                if (!totalWarned) {
                    log.add(rl + ": files in " + prefix + " are over " + maxTotalBytes
                            + " bytes together; this and later files skipped");
                    totalWarned = true;
                }
                continue;
            }
            total += bytes.length;
            out.put(rl, bytes);
        }
        return out;
    }
}
