package io.github.slugblue.wildroots.client.journal;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** The colours of the journal v2 (§3.0, §5.3), in one place; text is drawn without shadow. */
@OnlyIn(Dist.CLIENT)
public final class JournalColors {

    /** Ink on paper. */
    public static final int INK = 0xFF3A2A1A;
    /** Faded ink: page numbers, placeholders, disabled rows. */
    public static final int INK_FADED = 0xFF8A7A62;
    /** Links (underlined on hover). */
    public static final int LINK = 0xFF2B5C8A;
    /** "New" (as in v1). */
    public static final int NEW = 0xFF2E7D32;
    /** Hover and keyboard focus frame. */
    public static final int FOCUS = 0xFF2B5C8A;
    /** Light ink on the cover (navigation strip, tabs). */
    public static final int COVER_INK = 0xFFF3E9D2;

    private JournalColors() {
    }
}
