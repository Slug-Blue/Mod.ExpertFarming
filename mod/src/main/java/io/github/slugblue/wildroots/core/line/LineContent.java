package io.github.slugblue.wildroots.core.line;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import net.minecraft.world.item.Item;

/**
 * Which line an item belongs to (docs/05-architecture.md §9.1). Items and
 * blocks of every line are always registered; when the line is disabled
 * they stay inert (no species in the definitions) and hidden from the
 * creative tab and JEI. The item tasks of a line call {@link #assign} next
 * to the registration; items of the core have no line and are always shown.
 */
public final class LineContent {

    private static final Map<Supplier<? extends Item>, String> ASSIGNED = new ConcurrentHashMap<>();

    private LineContent() {
    }

    /** Marks {@code item} as content of {@code line}. */
    public static void assign(Supplier<? extends Item> item, String line) {
        ASSIGNED.put(item, line);
    }

    /** Line of {@code item}, if it belongs to one. */
    public static Optional<String> lineOf(Item item) {
        for (Map.Entry<Supplier<? extends Item>, String> e : ASSIGNED.entrySet()) {
            if (e.getKey().get() == item) {
                return Optional.of(e.getValue());
            }
        }
        return Optional.empty();
    }

    /** Whether content of {@code line} (empty: core content) is visible with {@code registry}. */
    public static boolean visible(Optional<String> line, LineRegistry registry) {
        return line.map(registry::isEnabled).orElse(true);
    }
}
