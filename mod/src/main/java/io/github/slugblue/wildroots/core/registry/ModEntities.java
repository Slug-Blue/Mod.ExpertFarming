package io.github.slugblue.wildroots.core.registry;

import io.github.slugblue.wildroots.WildRoots;
import io.github.slugblue.wildroots.butterfly.ButterflyEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> REGISTER = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, WildRoots.MOD_ID);
    public static final RegistryObject<EntityType<ButterflyEntity>> BUTTERFLY = REGISTER.register("butterfly", () ->
            EntityType.Builder.of(ButterflyEntity::new, MobCategory.AMBIENT).sized(.45f, .35f)
                    .clientTrackingRange(8).build("wildroots:butterfly"));
    private ModEntities() { }
    public static void attributes(EntityAttributeCreationEvent event) { event.put(BUTTERFLY.get(), ButterflyEntity.attributes().build()); }
}
