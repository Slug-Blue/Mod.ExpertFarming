package io.github.slugblue.wildroots.core;

import io.github.slugblue.wildroots.WildRoots;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/**
 * Builds {@link ResourceLocation}s without the constructors that Forge 47.4
 * marks for removal.
 */
public final class Ids {

    private Ids() {
    }

    /** {@code wildroots:<path>}. */
    public static ResourceLocation mod(String path) {
        return of(WildRoots.MOD_ID, path);
    }

    /** {@code <namespace>:<path>}; throws on an invalid id (programming error). */
    public static ResourceLocation of(String namespace, String path) {
        return Objects.requireNonNull(ResourceLocation.tryBuild(namespace, path),
                () -> "invalid id " + namespace + ":" + path);
    }

    /** Parses {@code ns:path} (default namespace {@code minecraft}); throws on an invalid id. */
    public static ResourceLocation parse(String id) {
        return Objects.requireNonNull(ResourceLocation.tryParse(id), () -> "invalid id " + id);
    }
}
