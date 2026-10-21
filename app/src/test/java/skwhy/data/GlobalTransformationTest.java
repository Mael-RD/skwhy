package skwhy.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class GlobalTransformationTest {

    private static void assertVec(float x, float y, float z, Vec3 v) {
        assertEquals(x, v.x, 1e-4f);
        assertEquals(y, v.y, 1e-4f);
        assertEquals(z, v.z, 1e-4f);
    }

    @Test
    void defaultIsIdentity() {
        GlobalTransformation g = new GlobalTransformation();
        assertVec(1, 2, 3, g.getTranslation(new Vec3(1f, 2f, 3f)));
        assertEquals(1f, g.getScale());
        assertEquals(1f, g.getRotation().w);
    }

    @Test
    void nullArgumentsFallBackToIdentity() {
        GlobalTransformation g = new GlobalTransformation(null, null, null, 2f);
        assertNotNull(g.getTranslation());
        assertEquals(1f, g.getRotation().w);
        g.setTranslation((Vec3) null);
        g.setCentreRotation((Vec3) null);
        g.setRotation((Quat4) null);
        assertVec(0, 0, 0, g.getTranslation());
        assertVec(0, 0, 0, g.getCentreRotation());
        assertEquals(1f, g.getRotation().w);
    }

    @Test
    void translationIsAppliedThenScaled() {
        GlobalTransformation g = new GlobalTransformation(new Vec3(10f, 0f, 0f), null, null, 2f);
        assertVec(22, 4, 6, g.getTranslation(new Vec3(1f, 2f, 3f)));
    }

    @Test
    void rotationPivotsAroundCentre() {
        float s = (float) Math.sin(Math.PI / 4), c = (float) Math.cos(Math.PI / 4);
        GlobalTransformation g = new GlobalTransformation(new Vec3(), new Vec3(1f, 0f, 0f), new Quat4(0f, s, 0f, c), 1f);
        // un point au centre de rotation ne bouge pas
        assertVec(1, 0, 0, g.getTranslation(new Vec3(1f, 0f, 0f)));
        // (2,0,0) est a 1 du centre : tourne de 90 deg autour de Y => (1,0,-1)
        assertVec(1, 0, -1, g.getTranslation(new Vec3(2f, 0f, 0f)));
    }

    @Test
    void rotationAndScaleCompose() {
        GlobalTransformation g = new GlobalTransformation();
        g.setRotation(0f, 1f, 0f, 0f);
        g.setScale(3f);
        Quat4 r = g.getRotation(new Quat4(0f, 0f, 0f, 1f));
        assertEquals(1f, r.y, 1e-6f);
        assertVec(3, 6, 9, g.getScale(new Vec3(1f, 2f, 3f)));
    }

    @Test
    void resetRestoresIdentityButKeepsScale() {
        GlobalTransformation g = new GlobalTransformation(1f, 2f, 3f, 4f, 5f, 6f, 0f, 1f, 0f, 0f);
        g.setScale(5f);
        g.reset();
        assertVec(0, 0, 0, g.getTranslation());
        assertVec(0, 0, 0, g.getCentreRotation());
        assertEquals(1f, g.getRotation().w);
        assertEquals(5f, g.getScale());
    }

    @Test
    void mirrorAppliesToAllComponents() {
        GlobalTransformation g = new GlobalTransformation(new Vec3(1f, 2f, 3f), new Vec3(4f, 5f, 6f), new Quat4(1f, 2f, 3f, 4f), 1f);
        g.mirror(true, false, false);
        assertVec(-1, 2, 3, g.getTranslation());
        assertVec(-4, 5, 6, g.getCentreRotation());
        assertEquals(-2f, g.getRotation().y);
        assertEquals(-3f, g.getRotation().z);
        GlobalTransformation unchanged = new GlobalTransformation(new Vec3(1f, 2f, 3f), null, null, 1f);
        unchanged.mirror(false, false, false);
        assertVec(1, 2, 3, unchanged.getTranslation());
    }
}
