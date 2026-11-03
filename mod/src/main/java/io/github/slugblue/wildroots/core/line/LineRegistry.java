package io.github.slugblue.wildroots.core.line;

import com.mojang.logging.LogUtils;
import io.github.slugblue.wildroots.core.config.CommonConfig;
import io.github.slugblue.wildroots.core.safety.Guarded;
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

    /** Outcome of a lookup: the registry and whether it may be kept for the rest of the run. */
    record Lookup(LineRegistry registry, boolean cacheable) {
    }

    /**
     * The registry for {@code disabled} (empty: COMMON config not loaded yet).
     * Without the config the result is all lines enabled and is <b>not</b>
     * cached, so the real list is read once the config is there; in strict
     * mode (dev, GameTest) such an early use is a bug and throws.
     */
    static Lookup lookup(LineManifest manifest, Optional<List<String>> disabled, boolean strict, Consumer<String> warn) {
        if (disabled.isEmpty()) {
            if (strict) {
                throw new IllegalStateException("LineRegistry used before the COMMON config was loaded");
            }
            warn.accept("Wild Roots: COMMON config not loaded yet, all lines enabled for now");
            return new Lookup(build(manifest, List.of(), warn), false);
        }
        return new Lookup(build(manifest, disabled.get(), warn), true);
    }

    /** The registry of this installation, built on first use after the COMMON config is loaded. */
    public static LineRegistry get() {
        LineRegistry r = current;
        if (r != null) {
            return r;
        }
        synchronized (LineRegistry.class) {
            if (current != null) {
                return current;
            }
            Lookup l = lookup(LineManifest.fromClasspath(), disabledFromConfig(), Guarded.shared().strict(),
                    LOGGER::warn);
            if (l.cacheable()) {
                current = l.registry();
                LOGGER.info("Wild Roots: lines enabled {} of {}", l.registry().enabled,
                        l.registry().all.stream().map(LineManifest.Line::id).toList());
            }
            return l.registry();
        }
    }

    /** Builds the registry now (common setup), so the log line and warnings come early. */
    public static void freeze() {
        get();
    }

    private static Optional<List<String>> disabledFromConfig() {
        try {
            return Optional.of(new ArrayList<>(CommonConfig.DISABLED_LINES.get()));
        } catch (IllegalStateException notLoaded) {
            return Optional.empty();
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
