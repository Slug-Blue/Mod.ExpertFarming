package io.github.slugblue.wildroots.client.journal.search;

import com.mojang.logging.LogUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

/**
 * The local bookmarks of guide entries for the current world or server
 * (journal v2 §3.10), in {@code config/wildroots-journal-client.json}.
 */
@OnlyIn(Dist.CLIENT)
public final class JournalBookmarks {

    private static final Logger LOGGER = LogUtils.getLogger();
    static final String FILE = "wildroots-journal-client.json";
    private static LocalBookmarks loaded;

    private JournalBookmarks() {
    }

    static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve(FILE);
    }

    static LocalBookmarks store() {
        if (loaded == null) {
            byte[] b = null;
            try {
                Path f = file();
                if (Files.isRegularFile(f) && Files.size(f) <= LocalBookmarks.MAX_FILE_BYTES) {
                    b = Files.readAllBytes(f);
                }
            } catch (Exception e) {
                LOGGER.warn("Wild Roots journal: bookmarks could not be read: {}", e.toString());
            }
            loaded = LocalBookmarks.read(b);
        }
        return loaded;
    }

    /** The key of this world: the save folder in single player, the server address otherwise. */
    public static String worldKey() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSingleplayerServer() != null) {
            return "sp:" + mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT).normalize().getFileName();
        }
        ServerData s = mc.getCurrentServer();
        return s != null ? "mp:" + s.ip : "unknown";
    }

    public static List<String> list() {
        return store().get(worldKey());
    }

    public static boolean has(String id) {
        return store().has(worldKey(), id);
    }

    /** Adds or removes; {@code false} when the world already has 16. */
    public static boolean toggle(String id) {
        LocalBookmarks s = store();
        String w = worldKey();
        boolean ok = true;
        if (s.has(w, id)) {
            s.remove(w, id);
        } else {
            ok = s.add(w, id);
        }
        try {
            Files.write(file(), s.write());
        } catch (Exception e) {
            LOGGER.warn("Wild Roots journal: bookmarks could not be saved: {}", e.toString());
        }
        return ok;
    }
}
