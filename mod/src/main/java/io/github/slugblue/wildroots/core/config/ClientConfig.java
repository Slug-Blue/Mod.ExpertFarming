package io.github.slugblue.wildroots.core.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * CLIENT config ({@code config/wildroots-client.toml}; journal v2 §5.2):
 * options of the field journal that only change what this client shows.
 * Read only on the client; a dedicated server never loads it.
 */
public final class ClientConfig {

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue SHOW_ALL_GUIDES;
    public static final ForgeConfigSpec.BooleanValue JOURNAL_SCENES;
    public static final ForgeConfigSpec.BooleanValue DEBUG_JOURNAL;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("journal");
        SHOW_ALL_GUIDES = b
                .comment("Open every guide entry of the field journal at once, instead of as you meet each topic.",
                        "Records (species, cultivars, notes) still come from the server.")
                .define("showAllGuides", false);
        JOURNAL_SCENES = b
                .comment("Draw the 3D scenes of the journal. false shows their flat 2D frame instead",
                        "(slow hardware, motion sickness, recording).")
                .define("journalScenes", true);
        DEBUG_JOURNAL = b
                .comment("Show the journal's debug counters (layout builds, scene meshes) in a corner of the screen.")
                .define("debugJournal", false);
        b.pop();
        SPEC = b.build();
    }

    private ClientConfig() {
    }

    /** A value, or its default before the config is loaded. */
    public static boolean get(ForgeConfigSpec.BooleanValue v) {
        return SPEC.isLoaded() ? v.get() : v.getDefault();
    }
}
