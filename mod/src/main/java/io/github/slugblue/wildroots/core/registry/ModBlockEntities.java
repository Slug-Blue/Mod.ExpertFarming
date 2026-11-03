package io.github.slugblue.wildroots.core.registry;

import io.github.slugblue.wildroots.WildRoots;
import io.github.slugblue.wildroots.plant.PlantBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Block entity types (WR-120; docs/05-architecture.md §3, `core.registry`).
 *
 * <p>Registrations exist always, independent of waves and config (§9.1):
 * the entries of a line arrive in the wave that ships its pack.
 */
public final class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> REGISTER = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, WildRoots.MOD_ID);

    /** {@code wildroots:plant}: the block entity of every plant block (WR-131; ADR-0003). */
    @SuppressWarnings("DataFlowIssue") // the data fixer type is null for mod block entities
    public static final RegistryObject<BlockEntityType<PlantBlockEntity>> PLANT = REGISTER.register("plant",
            () -> BlockEntityType.Builder.of(PlantBlockEntity::new, ModBlocks.WILD_CARROT.get()).build(null));

    private ModBlockEntities() {
    }
}
