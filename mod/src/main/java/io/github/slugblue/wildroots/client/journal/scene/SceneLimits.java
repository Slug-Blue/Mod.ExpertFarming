package io.github.slugblue.wildroots.client.journal.scene;

import io.github.slugblue.wildroots.client.journal.GuideLimits;
import java.util.regex.Pattern;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Hard limits of a scene file (journal v2 §5.8). A server resource pack may
 * replace the scenes, so every file is checked against these before any
 * mesh is built; a scene over any of them is not loaded (one log line) and
 * its page shows the fallback.
 */
@OnlyIn(Dist.CLIENT)
public final class SceneLimits {

    public static final String FOLDER = "scenes";
    public static final int MAX_FILE_BYTES = 16 * 1024;
    public static final int MAX_FILES = 32;
    public static final int MAX_TOTAL_BYTES = 256 * 1024;

    /** Each axis of the structure is 1…{@value}. */
    public static final int MAX_AXIS = 7;
    /** At most 7 × 5 × 7 cells. */
    public static final int MAX_VOLUME = 7 * 5 * 7;
    public static final int MAX_PALETTE = 16;
    public static final int MAX_STATE_CHARS = 128;

    public static final int MAX_STEPS = 16;
    public static final int MIN_DURATION = 10;
    public static final int MAX_DURATION = 400;
    /** The whole scene: 2 minutes of screen ticks. */
    public static final int MAX_TOTAL_TICKS = 2400;

    public static final int MAX_ACTIONS_PER_STEP = 16;
    public static final int MAX_ACTIONS = 128;
    public static final int MAX_SET_BLOCKS = 64;
    public static final int MAX_PATH = 8;

    /** At once in the frame (a pop item and a label stay until the end of their step). */
    public static final int MAX_POPS = 12;
    public static final int MAX_BEES = 4;
    public static final int MAX_ICONS = 4;
    public static final int MAX_LABELS = 8;
    /** Not in the §5.8 table; the same order as the labels. */
    public static final int MAX_HIGHLIGHTS = 8;
    /** Icons of one step's 2D frame (§3.13). */
    public static final int MAX_FRAME = 8;
    public static final int MAX_UNLOCK = GuideLimits.MAX_LIST;

    public static final float MIN_PITCH = 10;
    public static final float MAX_PITCH = 80;
    public static final float MIN_ZOOM = 0.6f;
    public static final float MAX_ZOOM = 2.0f;
    /** Easing of a step camera, in ticks. */
    public static final int MAX_EASE = 200;

    /**
     * Shape of a block state string: {@code ns:path} with optional
     * {@code [key=value,…]}. Anything else (NBT, tags, spaces) is replaced by
     * air with one log line.
     */
    public static final Pattern STATE = Pattern.compile(
            "([a-z0-9_.-]{1,64}:)?[a-z0-9_./-]{1,100}(\\[[a-z0-9_]{1,32}=[a-z0-9_]{1,32}(,[a-z0-9_]{1,32}=[a-z0-9_]{1,32}){0,15}])?");

    private SceneLimits() {
    }
}
