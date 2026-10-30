package io.github.slugblue.wildroots.archfixtures.plant;
/** Allowed: stands in for core.safety.LoadedLevelAccess. */
public class FakeLoadedLevelAccess {
    Object f(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos) {
        return level.getBlockEntity(pos);
    }
}
