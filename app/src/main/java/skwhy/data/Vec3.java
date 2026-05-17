package skwhy.data;

import org.bukkit.util.Vector;
import com.github.retrooper.packetevents.util.Vector3f;

/**
 * Wrapper pour Vector3f de PacketEvents pour faciliter l'accès aux composants.
 */
public class Vec3 {
    public float x, y, z;

    public Vec3() {
        this(0f, 0f, 0f);
    }

    public Vec3(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Vec3(float v) {
        this.x = v;
        this.y = v;
        this.z = v;
    }

    public Vec3(Vector3f v) {
        this.x = v.x;
        this.y = v.y;
        this.z = v.z;
    }

    public Vec3(org.joml.Vector3f v) {
        this.x = v.x;
        this.y = v.y;
        this.z = v.z;
    }

    public Vec3(Vector v) {
        this.x = (float) v.getX();
        this.y = (float) v.getY();
        this.z = (float) v.getZ();
    }

    public Vector3f toVector3f() {
        return new Vector3f(x, y, z);
    }

    public org.joml.Vector3f toJomlVector3f() {
        return new org.joml.Vector3f(x, y, z);
    }

    public Vector toVector() {
        return new Vector(x, y, z);
    }

    @Override
    public String toString() {
        return String.format("(%.2f,%.2f,%.2f)", x, y, z);
    }

    // ── Addition ──
    public Vec3 add(Vec3 v) {
        return new Vec3(this.x + v.x, this.y + v.y, this.z + v.z);
    }

    // ── Soustraction ──
    public Vec3 sub(Vec3 v) {
        return new Vec3(this.x - v.x, this.y - v.y, this.z - v.z);
    }

    // ── Multiplication (Vecteur * Nombre) ──
    public Vec3 mul(float n) {
        return new Vec3(this.x * n, this.y * n, this.z * n);
    }

    // ── Multiplication (Vecteur * Vecteur) ──
    public Vec3 mul(Vec3 v) {
        return new Vec3(this.y * v.z - this.z * v.y, this.z * v.x - this.x * v.z, this.x * v.y - this.y * v.x);
    }

    public Vec3 rotate(Quat4 q) {
        Quat4 p = new Quat4(this.x, this.y, this.z, 0f);
        Quat4 rotatedP = q.mul(p).mul(new Quat4(-q.x, -q.y, -q.z, q.w));
        return new Vec3(rotatedP.x, rotatedP.y, rotatedP.z);
    }

    /**
     * Applique un effet miroir directement sur ce vecteur selon les axes spécifiés.
     * Modifie l'état interne de cette instance.
     *
     * @param x true pour inverser la composante X
     * @param y true pour inverser la composante Y
     * @param z true pour inverser la composante Z
     */
    public void mirror(boolean x, boolean y, boolean z) {
        if (x) this.x = -this.x;
        if (y) this.y = -this.y;
        if (z) this.z = -this.z;
    }
}
