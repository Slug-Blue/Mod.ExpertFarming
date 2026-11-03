package io.github.slugblue.wildroots.core.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * SERVER (per world) config (WR-120; docs/05-architecture.md §3, §9.2, §14,
 * §16; SEC-02, SEC-45, SEC-48). Every number is declared with
 * {@code defineInRange} with the bounds of {@code 09-security.md}; Forge
 * clamps an out-of-range number to the nearest bound
 * ({@code ForgeConfigSpec.Range#correct}) and resets a value of the wrong
 * type to its default.
 *
 * <p>{@code strictErrors} is deliberately not here: it is the JVM property
 * {@code -Dwildroots.strictErrors=true} of dev and GameTest runs (SEC-34,
 * SEC-48).
 */
public final class ServerConfig {

    /** Vanilla crops: {@code strict} forbids planting them, {@code soft} keeps them (GDD §10, §9.2). */
    public enum Mode {
        STRICT,
        SOFT
    }

    /** Starter patch near spawn (worldgen §6): on, off, or forced even when spawn has no fitting biome. */
    public enum StarterPatch {
        TRUE,
        FALSE,
        FORCE
    }

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.EnumValue<Mode> MODE;
    public static final ForgeConfigSpec.EnumValue<StarterPatch> STARTER_PATCH;
    public static final ForgeConfigSpec.BooleanValue GIVE_JOURNAL_ON_FIRST_HARVEST;

    public static final ForgeConfigSpec.IntValue PACKET_BUCKET_CAPACITY;
    public static final ForgeConfigSpec.IntValue PACKET_REFILL_PER_SECOND;
    public static final ForgeConfigSpec.IntValue NAME_SELECTION_PER_MINUTE;
    public static final ForgeConfigSpec.IntValue MENU_BUTTONS_PER_SECOND;
    public static final ForgeConfigSpec.IntValue PACKET_FLOOD_DISCONNECT_THRESHOLD;

    public static final ForgeConfigSpec.IntValue BREEDER_RARE_FIND_DAILY_CAP;

    public static final ForgeConfigSpec.BooleanValue MIXIN_VILLAGER_FOOD;
    public static final ForgeConfigSpec.BooleanValue MIXIN_GRADE_FOOD;
    public static final ForgeConfigSpec.BooleanValue MIXIN_VILLAGER_TRADES;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.push("gameplay");
        MODE = b.comment("strict: vanilla crops cannot be planted; soft: vanilla crops keep working.")
                .defineEnum("mode", Mode.STRICT);
        STARTER_PATCH = b.comment("Starter patch of wild carrots near spawn: TRUE, FALSE or FORCE.")
                .defineEnum("starterPatch", StarterPatch.TRUE);
        GIVE_JOURNAL_ON_FIRST_HARVEST = b.comment("Give the field journal on the first harvest of a wild plant.")
                .define("giveJournalOnFirstHarvest", true);
        b.pop();

        b.push("network");
        PACKET_BUCKET_CAPACITY = b.comment("Token bucket per (player, packet type): capacity (SEC-02).")
                .defineInRange("packetBucketCapacity", 5, 1, 100);
        PACKET_REFILL_PER_SECOND = b.comment("Token bucket refill, tokens per second (SEC-02).")
                .defineInRange("packetRefillPerSecond", 5, 1, 100);
        NAME_SELECTION_PER_MINUTE = b.comment("Extra limit for naming a seedling variety, per minute (SEC-02).")
                .defineInRange("nameSelectionPerMinute", 3, 1, 60);
        MENU_BUTTONS_PER_SECOND = b.comment("Menu button clicks per second per menu (SEC-02).")
                .defineInRange("menuButtonsPerSecond", 10, 1, 100);
        PACKET_FLOOD_DISCONNECT_THRESHOLD = b
                .comment("Dropped packets within 10 s that disconnect the player; 0 disables (SEC-02).")
                .defineInRange("packetFloodDisconnectThreshold", 200, 0, 10_000);
        b.pop();

        b.push("economy");
        BREEDER_RARE_FIND_DAILY_CAP = b.comment("Rare finds a player can sell to the plant breeder per day (SEC-22).")
                .defineInRange("breederRareFindDailyCap", 6, 0, 64);
        b.pop();

        b.comment("Switches of the mixins M1, M2, M4 (docs/05-architecture.md §16). The flag is read at",
                "run time: false gives the vanilla behaviour (SEC-45).").push("mixins");
        MIXIN_VILLAGER_FOOD = b.comment("M1: villagers eat and share graded landrace produce.")
                .define("villagerFood", true);
        MIXIN_GRADE_FOOD = b.comment("M2: the grade of vanilla-id products changes nutrition and saturation.")
                .define("gradeFood", true);
        MIXIN_VILLAGER_TRADES = b.comment("M4: farmer and plant breeder offer 3 trades per level.")
                .define("villagerTrades", true);
        b.pop();

        SPEC = b.build();
    }

    private ServerConfig() {
    }
}
