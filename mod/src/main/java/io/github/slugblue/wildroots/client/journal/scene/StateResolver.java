package io.github.slugblue.wildroots.client.journal.scene;

import java.util.List;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Turns a block state string of a scene into a state (journal v2 §5.8):
 * in the game through {@code BlockStateParser} over the block registry
 * ({@link BlockStates}), in tests over a fake table. The string already has
 * the shape of {@link SceneLimits#STATE}. Never throws: an unknown block or
 * property, a block with a block entity or a fluid gives {@link #air()} and
 * one line in {@code log}.
 *
 * @param <S> the state type
 */
@OnlyIn(Dist.CLIENT)
public interface StateResolver<S> {

    S air();

    /** @param where the file and field, for the log line */
    S resolve(String state, String where, List<String> log);
}
