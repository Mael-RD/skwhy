package skwhy.data;

import com.github.retrooper.packetevents.util.Vector3f;

/**
 * Wrapper pour Vector3f de PacketEvents pour faciliter l'accès aux composants.
 */
public class Vec3 {
    public float x, y, z;

    public Vec3(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Vec3(Vector3f v) {
        this.x = v.x;
        this.y = v.y;
        this.z = v.z;
    }

    public Vector3f toVector3f() {
        return new Vector3f(x, y, z);
    }

    @Override
    public String toString() {
        return String.format("(%.2f,%.2f,%.2f)", x, y, z);
    }
}
