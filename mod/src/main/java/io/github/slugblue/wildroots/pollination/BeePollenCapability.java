package io.github.slugblue.wildroots.pollination;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;

/**
 * The {@link BeePollen} capability (docs/05-architecture.md §3 package
 * {@code pollination}). Kept apart from the record class so that the record
 * loads in plain JUnit, where the Forge capability transformer does not run.
 */
public final class BeePollenCapability {

    public static final Capability<BeePollen> CAPABILITY = CapabilityManager.get(new CapabilityToken<>() {
    });

    private BeePollenCapability() {
    }
}
