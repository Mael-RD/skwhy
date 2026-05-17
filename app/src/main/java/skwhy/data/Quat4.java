package skwhy.data;

import org.joml.Quaternionf;
import com.github.retrooper.packetevents.util.Quaternion4f;

/**
 * Wrapper pour Quaternion4f avec opérations mathématiques.
 */
public class Quat4 {
    public float x, y, z, w;

    public Quat4(float x, float y, float z, float w) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.w = w;
    }

    public Quat4(Quaternion4f q) {
        // PacketEvents Quaternion4f possède généralement des getters ou des champs publics
        // La réflexion est coûteuse, on privilégie l'assignation directe si possible
        this.x = q.getX();
        this.y = q.getY();
        this.z = q.getZ();
        this.w = q.getW();
    }

    public Quat4(Quaternionf q) {
        this.x = q.x;
        this.y = q.y;
        this.z = q.z;
        this.w = q.w;
    }

    // ── Addition (+) ──
    public Quat4 add(Quat4 q) {
        return new Quat4(this.x + q.x, this.y + q.y, this.z + q.z, this.w + q.w);
    }

    // ── Soustraction (-) ──
    public Quat4 sub(Quat4 q) {
        return new Quat4(this.x - q.x, this.y - q.y, this.z - q.z, this.w - q.w);
    }

    // ── Multiplication par un nombre (*) ──
    public Quat4 mul(float n) {
        return new Quat4(this.x * n, this.y * n, this.z * n, this.w * n);
    }

    // ── Multiplication de Quaternions (Combinaison de rotations) ──
    public Quat4 mul(Quat4 q) {
        return new Quat4(
            this.w * q.x + this.x * q.w + this.y * q.z - this.z * q.y,
            this.w * q.y - this.x * q.z + this.y * q.w + this.z * q.x,
            this.w * q.z + this.x * q.y - this.y * q.x + this.z * q.w,
            this.w * q.w - this.x * q.x - this.y * q.y - this.z * q.z
        );
    }

    public Quaternion4f toQuaternion4f() {
        return new Quaternion4f(x, y, z, w);
    }

    public Quaternionf toQuaternionf() {
        return new Quaternionf(x, y, z, w);
    }

    @Override
    public String toString() {
        return String.format("(%.2f, %.2f, %.2f, %.2f)", x, y, z, w);
    }

    /**
     * Applique un effet miroir directement sur ce quaternion selon les axes spécifiés.
     * Modifie l'état interne de cette instance.
     *
     * @param x true pour appliquer un miroir sur l'axe X (Plan YZ)
     * @param y true pour appliquer un miroir sur l'axe Y (Plan XZ)
     * @param z true pour appliquer un miroir sur l'axe Z (Plan XY)
     */
    public void mirror(boolean x, boolean y, boolean z) {
        if (x) {
            this.y = -this.y;
            this.z = -this.z;
        }
        if (y) {
            this.x = -this.x;
            this.z = -this.z;
        }
        if (z) {
            this.x = -this.x;
            this.y = -this.y;
        }
    }
}