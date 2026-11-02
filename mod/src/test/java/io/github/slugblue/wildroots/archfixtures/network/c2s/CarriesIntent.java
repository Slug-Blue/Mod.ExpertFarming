package io.github.slugblue.wildroots.archfixtures.network.c2s;

import net.minecraft.resources.ResourceLocation;

/** Fixture: a C→S packet with allowed field types only. */
public record CarriesIntent(long id, int action, boolean flag, String name, ResourceLocation entry) {

    public static final CarriesIntent MALFORMED = new CarriesIntent(0, 0, false, "", null);
}
