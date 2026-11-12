package io.github.slugblue.wildroots.client.journal.page;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * How much of a spread a laid-out page takes (journal v2 §3.0, §3.13). A
 * {@link #SPREAD} page always starts a new spread and fills it; a
 * {@link #HALF} page goes left, then right.
 */
@OnlyIn(Dist.CLIENT)
public enum PageSpan {
    HALF,
    SPREAD
}
