package io.github.slugblue.wildroots.core.line;

import com.mojang.logging.LogUtils;
import io.github.slugblue.wildroots.core.config.CommonConfig;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import org.slf4j.Logger;

/**
 * Lines of this installation (docs/05-architecture.md §9.2; ADR-0006): the
 * manifest in the jar minus {@code CommonConfig.disabledLines}. Immutable
 * once built; a change of the config needs a restart.
 *
 * <p>The registry is built on first use after the COMMON config is loaded
 * (Forge loads COMMON configs right after mod construction, before
 * {@code FMLCommonSetupEvent}; {@link #freeze()} runs there). Data packs
 * of the server are collected later, so {@link LinePackFinder} always sees
 * the frozen registry. An unknown id in {@code disabledLines} is a WARN, not
 * a crash (SEC-48).
 */
public final class LineRegistry {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile LineRegistry current;

    private final List<LineManifest.Line> all;
    private final Set<String> enabled;

    private LineRegistry(List<LineManifest.Line> all, Set<String> enabled) {
        this.all = List.copyOf(all);
        this.enabled = Set.copyOf(enabled);
    }

    /** Lines of {@code manifest} without {@code disabled}; unknown disabled ids go to {@code warn}. */
    public static LineRegistry build(LineManifest manifest, Collection<String> disabled, Consumer<String> warn) {
        Set<String> known = new LinkedHashSet<>();
        manifest.lines().forEach(l -> known.add(l.id()));
        for (String d : disabled) {
            if (!known.contains(d)) {
                warn.accept("Wild Roots: unknown line '" + d + "' in disabledLines, ignored; known lines: " + known);
            }
        }
        Set<String> on = new LinkedHashSet<>(known);
        on.removeAll(disabled);
        return new LineRegistry(manifest.lines(), on);
    }

    /** The registry of this installation, built on first use. */
    public static LineRegistry get() {
        LineRegistry r = current;
        if (r == null) {
            synchronized (LineRegistry.class) {
                r = current;
                if (r == null) {
                    r = build(LineManifest.fromClasspath(), disabledFromConfig(), LOGGER::warn);
                    LOGGER.info("Wild Roots: lines enabled {} of {}", r.enabled,
                            r.all.stream().map(LineManifest.Line::id).toList());
                    current = r;
                }
            }
        }
        return r;
    }

    /** Builds the registry now (common setup), so the log line and warnings come early. */
    public static void freeze() {
        get();
    }

    private static List<String> disabledFromConfig() {
        try {
            return new ArrayList<>(CommonConfig.DISABLED_LINES.get());
        } catch (IllegalStateException notLoaded) {
            LOGGER.warn("Wild Roots: COMMON config not loaded yet, all lines enabled");
            return List.of();
        }
    }

    /** Every line in the manifest, in manifest order. */
    public List<LineManifest.Line> all() {
        return all;
    }

    /** Enabled lines, in manifest order. */
    public List<LineManifest.Line> enabled() {
        return all.stream().filter(l -> enabled.contains(l.id())).toList();
    }

    public boolean isEnabled(String line) {
        return enabled.contains(line);
    }

    public Optional<LineManifest.Line> line(String id) {
        return all.stream().filter(l -> l.id().equals(id)).findFirst();
    }
}
