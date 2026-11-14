package io.github.slugblue.wildroots.client.journal.page;

import io.github.slugblue.wildroots.client.journal.GuideFormatException;
import io.github.slugblue.wildroots.client.journal.GuideLoader;
import io.github.slugblue.wildroots.client.journal.GuideSection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The 2D fallback frames of every scene (journal v2 §3.13), reloaded with
 * the guide from {@code journal/scenes/} under the scene file limits of
 * §5.8. Only the fallback fields are read ({@link SceneFrame}); the 3D
 * scene is the engine's (WR-181k), which loads the same files on its own.
 */
@OnlyIn(Dist.CLIENT)
public final class SceneFrames implements GuideSection<Map<String, SceneFrame>> {

    public static final SceneFrames INSTANCE = new SceneFrames();
    private static volatile Map<String, SceneFrame> frames = Map.of();

    private SceneFrames() {
    }

    /** The fallback frame of a scene id, if its file loaded. */
    public static Optional<SceneFrame> get(String sceneId) {
        return Optional.ofNullable(frames.get(sceneId));
    }

    @Override
    public String folder() {
        return "scenes";
    }

    @Override
    public int maxFileBytes() {
        return 16 * 1024;
    }

    @Override
    public int maxFiles() {
        return 32;
    }

    @Override
    public int maxTotalBytes() {
        return 256 * 1024;
    }

    @Override
    public Map<String, SceneFrame> prepare(Map<ResourceLocation, byte[]> files, List<String> log) {
        return parseAll(files, log);
    }

    /** Pure: each file parsed alone; a broken one is one log line and is left out. */
    public static Map<String, SceneFrame> parseAll(Map<ResourceLocation, byte[]> files, List<String> log) {
        Map<String, SceneFrame> out = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, byte[]> f : files.entrySet()) {
            try {
                SceneFrame s = SceneFrame.parse(GuideLoader.parseObject(f.getValue()));
                if (out.putIfAbsent(s.id(), s) != null) {
                    log.add(f.getKey() + ": duplicate scene " + s.id() + ", 2D frame skipped");
                }
            } catch (GuideFormatException e) {
                log.add(f.getKey() + ": 2D frame: " + e.getMessage());
            } catch (Exception | StackOverflowError e) {
                log.add(f.getKey() + ": 2D frame: not a JSON object");
            }
        }
        return Map.copyOf(out);
    }

    @Override
    public void apply(Map<String, SceneFrame> prepared) {
        frames = prepared;
    }
}
