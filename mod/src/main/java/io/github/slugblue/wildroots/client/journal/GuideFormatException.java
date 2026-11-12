package io.github.slugblue.wildroots.client.journal;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** A guide file, entry or page that breaks the format of journal v2 §5.2; the message is one log line. */
@OnlyIn(Dist.CLIENT)
public final class GuideFormatException extends Exception {

    private static final long serialVersionUID = 1L;

    public GuideFormatException(String message) {
        super(message, null, false, false);
    }
}
