package bad;

import org.spongepowered.asm.mixin.Overwrite;

public class BadMixin {
    @Overwrite
    public void tick() { }
}
