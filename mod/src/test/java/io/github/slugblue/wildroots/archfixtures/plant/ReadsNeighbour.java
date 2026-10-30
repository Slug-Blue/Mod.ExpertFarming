package io.github.slugblue.wildroots.archfixtures.plant;
/** SEC-32 violation: reads a computed position directly from the level. */
public class ReadsNeighbour {
    Object f(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos) {
        return level.getBlockState(pos.above());
    }
}
