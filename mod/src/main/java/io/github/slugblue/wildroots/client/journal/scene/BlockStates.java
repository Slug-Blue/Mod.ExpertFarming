package io.github.slugblue.wildroots.client.journal.scene;

import io.github.slugblue.wildroots.core.safety.SceneStateRules;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Block states of a scene from the block registry (journal v2 §5.8). The
 * rules (no NBT, block entities and fluids to air, plants keep only their
 * look) live in {@link SceneStateRules}, plain game code a GameTest checks
 * against the real registry (review M4).
 */
@OnlyIn(Dist.CLIENT)
final class BlockStates implements StateResolver<BlockState> {

    private final HolderLookup<Block> blocks = SceneStateRules.blocks();

    @Override
    public BlockState air() {
        return Blocks.AIR.defaultBlockState();
    }

    @Override
    public BlockState resolve(String state, String where, List<String> log) {
        return SceneStateRules.resolve(blocks, state, where, log);
    }
}
