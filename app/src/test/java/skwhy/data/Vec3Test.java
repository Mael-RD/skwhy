package skwhy.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

class Vec3Test {

    private static void assertVec(float x, float y, float z, Vec3 v) {
        assertEquals(x, v.x, 1e-5f);
        assertEquals(y, v.y, 1e-5f);
        assertEquals(z, v.z, 1e-5f);
    }

    @Test
    void constructors() {
        assertVec(0, 0, 0, new Vec3());
        assertVec(2, 2, 2, new Vec3(2f));
        assertVec(1, 2, 3, new Vec3(1f, 2f, 3f));
        assertVec(1, 2, 3, new Vec3(new Vector(1, 2, 3)));
        assertVec(1, 2, 3, new Vec3(new org.joml.Vector3f(1, 2, 3)));
        assertVec(1, 2, 3, new Vec3(new com.github.retrooper.packetevents.util.Vector3f(1, 2, 3)));
    }

    @Test
    void conversions() {
        Vec3 v = new Vec3(1f, 2f, 3f);
        assertEquals(new Vector(1, 2, 3), v.toVector());
        assertEquals(new org.joml.Vector3f(1, 2, 3), v.toJomlVector3f());
        assertEquals(2f, v.toVector3f().y);
    }

    @Test
    void arithmetic() {
        Vec3 a = new Vec3(1f, 2f, 3f), b = new Vec3(4f, 5f, 6f);
        assertVec(5, 7, 9, a.add(b));
        assertVec(-3, -3, -3, a.sub(b));
        assertVec(2, 4, 6, a.mul(2f));
        assertVec(0, 0, 0, a.mul(0f));
    }

    @Test
    void vectorTimesVectorIsCrossProduct() {
        assertVec(0, 0, 1, new Vec3(1f, 0f, 0f).mul(new Vec3(0f, 1f, 0f)));
        assertVec(0, 0, -1, new Vec3(0f, 1f, 0f).mul(new Vec3(1f, 0f, 0f)));
        Vec3 a = new Vec3(1f, 2f, 3f);
        assertVec(0, 0, 0, a.mul(a));
    }

    @Test
    void rotateByIdentityKeepsVector() {
        assertVec(1, 2, 3, new Vec3(1f, 2f, 3f).rotate(new Quat4(0f, 0f, 0f, 1f)));
    }

    @Test
    void rotate90AroundY() {
        float s = (float) Math.sin(Math.PI / 4), c = (float) Math.cos(Math.PI / 4);
        // +90 deg autour de Y : x -> -z
        assertVec(0, 0, -1, new Vec3(1f, 0f, 0f).rotate(new Quat4(0f, s, 0f, c)));
        assertVec(0, 1, 0, new Vec3(0f, 1f, 0f).rotate(new Quat4(0f, s, 0f, c)));
    }

    @Test
    void mirrorMutatesAndCloneDoesNot() {
        Vec3 v = new Vec3(1f, 2f, 3f);
        Vec3 m = v.clone(true, false, true);
        assertVec(-1, 2, -3, m);
        assertVec(1, 2, 3, v);
        assertNotSame(v, v.clone());
        v.mirror(false, true, false);
        assertVec(1, -2, 3, v);
    }

    @Test
    void toStringUsesTwoDecimals() {
        assertEquals(String.format("(%.2f,%.2f,%.2f)", 1f, 2.5f, -3f), new Vec3(1f, 2.5f, -3f).toString());
    }
}
