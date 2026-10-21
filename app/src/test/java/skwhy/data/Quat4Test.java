package skwhy.data;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;

class Quat4Test {

    private static void assertQuat(float x, float y, float z, float w, Quat4 q) {
        assertEquals(x, q.x, 1e-5f);
        assertEquals(y, q.y, 1e-5f);
        assertEquals(z, q.z, 1e-5f);
        assertEquals(w, q.w, 1e-5f);
    }

    @Test
    void addSubScale() {
        Quat4 a = new Quat4(1f, 2f, 3f, 4f), b = new Quat4(4f, 3f, 2f, 1f);
        assertQuat(5, 5, 5, 5, a.add(b));
        assertQuat(-3, -1, 1, 3, a.sub(b));
        assertQuat(2, 4, 6, 8, a.mul(2f));
    }

    @Test
    void identityIsNeutral() {
        Quat4 id = new Quat4(0f, 0f, 0f, 1f), q = new Quat4(0.1f, 0.2f, 0.3f, 0.9f);
        assertQuat(q.x, q.y, q.z, q.w, id.mul(q));
        assertQuat(q.x, q.y, q.z, q.w, q.mul(id));
    }

    @Test
    void productMatchesJoml() {
        Quaternionf a = new Quaternionf().rotateY(0.7f).rotateX(0.3f);
        Quaternionf b = new Quaternionf().rotateZ(1.1f);
        Quaternionf expected = new Quaternionf(a).mul(b);
        Quat4 r = new Quat4(a).mul(new Quat4(b));
        assertQuat(expected.x, expected.y, expected.z, expected.w, r);
    }

    @Test
    void conversionsRoundTrip() {
        Quat4 q = new Quat4(0.1f, 0.2f, 0.3f, 0.4f);
        assertQuat(0.1f, 0.2f, 0.3f, 0.4f, new Quat4(q.toQuaternionf()));
        assertQuat(0.1f, 0.2f, 0.3f, 0.4f, new Quat4(q.toQuaternion4f()));
    }

    @Test
    void mirrorAxes() {
        assertQuat(1, -2, -3, 4, new Quat4(1f, 2f, 3f, 4f).clone(true, false, false));
        assertQuat(-1, 2, -3, 4, new Quat4(1f, 2f, 3f, 4f).clone(false, true, false));
        assertQuat(-1, -2, 3, 4, new Quat4(1f, 2f, 3f, 4f).clone(false, false, true));
        assertQuat(1, 2, 3, 4, new Quat4(1f, 2f, 3f, 4f).clone(false, false, false));
    }

    @Test
    void mirrorTwiceIsIdentity() {
        Quat4 q = new Quat4(1f, 2f, 3f, 4f);
        q.mirror(true, false, false);
        q.mirror(true, false, false);
        assertQuat(1, 2, 3, 4, q);
    }

    @Test
    void cloneForDisplaySwapsComponents() {
        // X : x<->z et y<->w
        assertQuat(3, 4, 1, 2, new Quat4(1f, 2f, 3f, 4f).clone_for_display(true, false, false));
        // Y : x<->w, y=-z, z=-y
        assertQuat(4, -3, -2, 1, new Quat4(1f, 2f, 3f, 4f).clone_for_display(false, true, false));
        // Z : x et y inverses
        assertQuat(-1, -2, 3, 4, new Quat4(1f, 2f, 3f, 4f).clone_for_display(false, false, true));
    }
}
