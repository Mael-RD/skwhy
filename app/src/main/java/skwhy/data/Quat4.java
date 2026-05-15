package skwhy.data;

import com.github.retrooper.packetevents.util.Quaternion4f;

/**
 * Wrapper pour Quaternion4f de PacketEvents pour faciliter l'accès aux composants.
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
        // Essayer d'accéder aux composants si possibles, sinon utiliser des valeurs par défaut
        try {
            this.x = q.getClass().getDeclaredField("x").getFloat(q);
            this.y = q.getClass().getDeclaredField("y").getFloat(q);
            this.z = q.getClass().getDeclaredField("z").getFloat(q);
            this.w = q.getClass().getDeclaredField("w").getFloat(q);
        } catch (Exception e) {
            // Si on ne peut pas accéder par réflexion, utiliser quaternion identité
            this.x = 0f;
            this.y = 0f;
            this.z = 0f;
            this.w = 1f;
        }
    }

    public Quaternion4f toQuaternion4f() {
        return new Quaternion4f(x, y, z, w);
    }

    @Override
    public String toString() {
        return String.format("(%.2f,%.2f,%.2f,%.2f)", x, y, z, w);
    }
}
