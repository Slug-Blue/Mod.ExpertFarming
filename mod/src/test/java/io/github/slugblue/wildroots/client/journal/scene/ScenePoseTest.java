package io.github.slugblue.wildroots.client.journal.scene;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

/**
 * Owner playtest 2, missing faces (4.png - 6.png): the scene pose winds
 * quads like vanilla's GUI items, so back-face culling keeps the faces
 * turned to the reader: the top of the farmland is seen from above, and
 * the front row (z = 0) is nearer than the back row.
 */
class ScenePoseTest {

    static Matrix4f pose(float pitch, float yaw) {
        PoseStack p = new PoseStack();
        SceneRenderer.pose(p, 20, pitch, yaw, 132, 56, 6, 2, 3);
        return new Matrix4f(p.last().pose());
    }

    @Test
    void windingIsTheSameAsVanillaGuiItems() {
        // vanilla draws block items in the GUI with scale(16, -16, 16): determinant < 0 under the GUI projection
        for (float pitch : new float[] {10, 30, 80}) {
            for (float yaw = 0; yaw < 360; yaw += 45) {
                float det = new Matrix3f().set(pose(pitch, yaw)).determinant();
                assertTrue(det < 0, "pitch " + pitch + " yaw " + yaw + ": determinant " + det
                        + " - quads would wind the other way and culling would drop the near faces");
            }
        }
    }

    @Test
    void theTopFacesTheReaderAndTheFrontRowIsNearer() {
        for (float pitch : new float[] {10, 30, 80}) {
            Matrix4f m = pose(pitch, 200);
            Vector3f up = m.transformDirection(new Vector3f(0, 1, 0));
            assertTrue(up.z() > 0, "the top face points away from the reader at pitch " + pitch);
            assertTrue(up.y() < 0, "up is not up on the screen (GUI y runs down)");
            float front = m.transform(new Vector4f(3, 1, 0, 1)).z();
            float back = m.transform(new Vector4f(3, 1, 3, 1)).z();
            assertTrue(front > back, "the front row is behind the back row at pitch " + pitch);
        }
    }
}
