package io.github.slugblue.wildroots.client.journal.scene;

import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * A view of the scene (journal v2 §5.8): {@code yaw} in degrees, any value
 * (kept in 0…360), {@code pitch} 10…80 (looking down), {@code zoom}
 * 0.6…2.0; out-of-range values are clamped, never rejected.
 *
 * <p>Yaw 180 shows the structure's {@code z = 0} side to the reader;
 * larger yaw turns the scene clockwise seen from above (yaw 200 is the
 * three-quarter view of §3.3, yaw 225 the diagonal of §3.6).
 */
@OnlyIn(Dist.CLIENT)
public record SceneCamera(float yaw, float pitch, float zoom) {

    public static final SceneCamera DEFAULT = new SceneCamera(200, 30, 1);

    public SceneCamera {
        yaw = wrap(yaw);
        pitch = Mth.clamp(Float.isFinite(pitch) ? pitch : 30, SceneLimits.MIN_PITCH, SceneLimits.MAX_PITCH);
        zoom = Mth.clamp(Float.isFinite(zoom) ? zoom : 1, SceneLimits.MIN_ZOOM, SceneLimits.MAX_ZOOM);
    }

    static float wrap(float yaw) {
        if (!Float.isFinite(yaw)) {
            return 0;
        }
        float y = yaw % 360f;
        return y < 0 ? y + 360f : y;
    }

    /** {@code a → b} at {@code t} (0…1), yaw along the shorter way round. */
    public static SceneCamera lerp(SceneCamera a, SceneCamera b, float t) {
        float k = Mth.clamp(t, 0, 1);
        float dy = Mth.wrapDegrees(b.yaw - a.yaw);
        return new SceneCamera(a.yaw + dy * k, Mth.lerp(k, a.pitch, b.pitch), Mth.lerp(k, a.zoom, b.zoom));
    }

    public SceneCamera turned(float dYaw, float dPitch) {
        return new SceneCamera(yaw + dYaw, pitch + dPitch, zoom);
    }

    public SceneCamera zoomed(float factor) {
        return new SceneCamera(yaw, pitch, zoom * factor);
    }
}
