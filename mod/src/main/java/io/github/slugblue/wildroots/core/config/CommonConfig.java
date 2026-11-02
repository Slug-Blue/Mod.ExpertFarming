package io.github.slugblue.wildroots.core.config;

import java.util.List;
import java.util.regex.Pattern;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * COMMON config (WR-120; docs/05-architecture.md §3 {@code core.config}, §9.2):
 * the list of disabled lines and debug switches.
 *
 * <p>{@code disabledLines} lives here and not in the SERVER config: the pack
 * list is built before the world's server config is loaded. Changing it needs
 * a restart (§9.2). An unknown line id is a WARN in the log, not a crash
 * (SEC-48, WR-124).
 */
public final class CommonConfig {

    /** A line id: lower-case ASCII, as in {@code resourcepacks/lines/<id>/}. */
    public static final Pattern LINE_ID = Pattern.compile("[a-z0-9_]{1,32}");

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> DISABLED_LINES;
    public static final ForgeConfigSpec.BooleanValue DEBUG_LOG_DEFINITIONS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("lines");
        DISABLED_LINES = b
                .comment("Lines (crop waves) that are switched off: their built-in data pack is not added,",
                        "their blocks and items stay registered but inert and hidden. Needs a restart.",
                        "Example: [\"carrot\"]")
                .defineListAllowEmpty(List.of("disabledLines"), List::of,
                        o -> o instanceof String s && LINE_ID.matcher(s).matches());
        b.pop();
        b.push("debug");
        DEBUG_LOG_DEFINITIONS = b
                .comment("Log every loaded species and cultivar id after a data reload.")
                .define("logDefinitions", false);
        b.pop();
        SPEC = b.build();
    }

    private CommonConfig() {
    }
}
