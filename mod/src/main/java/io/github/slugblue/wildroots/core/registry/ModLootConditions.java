package io.github.slugblue.wildroots.core.registry;

import io.github.slugblue.wildroots.WildRoots;
import io.github.slugblue.wildroots.vanilla.StrictModeCondition;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** Loot item conditions (WR-162): {@code wildroots:strict_mode}. */
public final class ModLootConditions {

    public static final DeferredRegister<LootItemConditionType> REGISTER =
            DeferredRegister.create(Registries.LOOT_CONDITION_TYPE, WildRoots.MOD_ID);

    public static final RegistryObject<LootItemConditionType> STRICT_MODE = REGISTER.register("strict_mode",
            () -> new LootItemConditionType(new StrictModeCondition.Serializer()));

    private ModLootConditions() {
    }
}
