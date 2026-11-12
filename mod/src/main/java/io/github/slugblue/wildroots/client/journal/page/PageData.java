package io.github.slugblue.wildroots.client.journal.page;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The parsed fields of one guide page (journal v2 §5.2), without its
 * {@code type} and {@code unlock}, which {@code GuidePage} holds. Pure
 * data: no client state, so the loader and its tests need no game.
 * Each type has a {@link PageParser} in {@link PageParsers} and, on the
 * client, a {@code PageType} in {@code PageTypes} that lays it out.
 */
@OnlyIn(Dist.CLIENT)
public interface PageData {
}
