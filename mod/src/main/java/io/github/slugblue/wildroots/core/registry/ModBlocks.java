package io.github.slugblue.wildroots.core.registry;

import io.github.slugblue.wildroots.WildRoots;
import io.github.slugblue.wildroots.plant.FieldPlantBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Blocks (WR-120; docs/05-architecture.md §3, `core.registry`).
 *
 * <p>Registrations exist always, independent of waves and config (§9.1):
 * the entries of a line arrive in the wave that ships its pack.
 */
public final class ModBlocks {

    public static final DeferredRegister<Block> REGISTER = DeferredRegister.create(ForgeRegistries.BLOCKS, WildRoots.MOD_ID);

    /** Species id of the wild carrot (GDD §3.9, line pack {@code lines/carrot}). */
    public static final String WILD_CARROT_SPECIES = "wildroots:wild_carrot";

    /** {@code wildroots:wild_carrot}, archetype {@code field} (WR-130; §7.1, narrative §7.9). */
    public static final RegistryObject<FieldPlantBlock> WILD_CARROT = REGISTER.register("wild_carrot",
            () -> new FieldPlantBlock(WILD_CARROT_SPECIES, BlockBehaviour.Properties.copy(Blocks.CARROTS)));

    private ModBlocks() {
    }
}
