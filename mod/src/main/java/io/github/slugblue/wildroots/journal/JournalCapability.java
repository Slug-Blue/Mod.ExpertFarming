package io.github.slugblue.wildroots.journal;

import io.github.slugblue.wildroots.core.Ids;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;

/**
 * The player capability holding {@link JournalData} (docs/05-architecture.md
 * §6.1). Saved with the player ({@code ForgeCaps}), so it survives a relog;
 * {@link Journals#onClone} copies it on death and on the return from the End.
 * Kept apart from {@link JournalData} so that the model loads in plain JUnit.
 */
public final class JournalCapability {

    public static final ResourceLocation ID = Ids.mod("journal");

    public static final Capability<JournalData> CAPABILITY = CapabilityManager.get(new CapabilityToken<>() {
    });

    private JournalCapability() {
    }

    /** Provider attached to every player. */
    public static final class Provider implements ICapabilitySerializable<CompoundTag> {

        private JournalData data = new JournalData();
        private final LazyOptional<JournalData> self = LazyOptional.of(() -> data);

        @Override
        public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
            return CAPABILITY.orEmpty(cap, self);
        }

        @Override
        public CompoundTag serializeNBT() {
            return data.save();
        }

        @Override
        public void deserializeNBT(CompoundTag nbt) {
            data.copyFrom(JournalData.load(nbt));
        }
    }
}
