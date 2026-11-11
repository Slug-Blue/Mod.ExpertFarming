package io.github.slugblue.wildroots.vanilla;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import io.github.slugblue.wildroots.core.registry.ModLootConditions;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;

/**
 * Loot condition {@code wildroots:strict_mode} (WR-162): true while the
 * server's vanilla-crop mode is {@code strict} (the default). The carrot
 * line's {@code minecraft:blocks/carrots} drops line produce under it and
 * the vanilla carrot otherwise, so an unconverted ripe crop gives no
 * vanilla item in strict and stays vanilla in soft.
 */
public final class StrictModeCondition implements LootItemCondition {

    static final StrictModeCondition INSTANCE = new StrictModeCondition();

    @Override
    public LootItemConditionType getType() {
        return ModLootConditions.STRICT_MODE.get();
    }

    @Override
    public boolean test(LootContext context) {
        return VanillaCrops.strict();
    }

    /** JSON: {@code {"condition": "wildroots:strict_mode"}}, no fields. */
    public static final class Serializer implements net.minecraft.world.level.storage.loot.Serializer<StrictModeCondition> {

        @Override
        public void serialize(JsonObject json, StrictModeCondition value, JsonSerializationContext context) {
        }

        @Override
        public StrictModeCondition deserialize(JsonObject json, JsonDeserializationContext context) {
            return INSTANCE;
        }
    }
}
