package io.github.slugblue.wildroots.client.journal;

import io.github.slugblue.wildroots.client.journal.page.PageData;
import java.util.List;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * One page of a guide entry (journal v2 §5.2): its type, its own
 * {@code unlock} (empty = shown whenever the entry is) and the fields of
 * the type.
 */
@OnlyIn(Dist.CLIENT)
public record GuidePage(String type, List<String> unlock, PageData data) {

    public GuidePage {
        unlock = List.copyOf(unlock);
    }
}
