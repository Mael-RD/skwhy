package skwhy.data;

/**
 * Classe représentant une transformation globale pour un groupe de displays.
 * Contient une translation, un centre de rotation et une rotation (quaternion).
 */
public class GlobalTransformation {
    
    private Vec3 translation;       // Translation globale (x, y, z)
    private Vec3 centreRotation;    // Centre de rotation (x, y, z)
    private Quat4 rotation;      // Rotation (quaternion x, y, z, w)
    private float scale;           // Échelle globale (uniforme)

    /**
     * Constructeur par défaut - initialise à l'identité.
     */
    public GlobalTransformation() {
        this.translation = new Vec3(0f, 0f, 0f);
        this.centreRotation = new Vec3(0f, 0f, 0f);
        this.rotation = new Quat4(0f, 0f, 0f, 1f); // Quaternion identité
        this.scale = 1f;
    }

    /**
     * Constructeur avec tous les paramètres.
     */
    public GlobalTransformation(Vec3 translation, Vec3 centreRotation, Quat4 rotation, float scale) {
        this.translation = translation != null ? translation : new Vec3(0f, 0f, 0f);
        this.centreRotation = centreRotation != null ? centreRotation : new Vec3(0f, 0f, 0f);
        this.rotation = rotation != null ? rotation : new Quat4(0f, 0f, 0f, 1f);
        this.scale = scale;
    }

    /**
     * Constructeur avec composants individuels.
     */
    public GlobalTransformation(
        float transX, float transY, float transZ,
        float centreX, float centreY, float centreZ,
        float rotX, float rotY, float rotZ, float rotW
    ) {
        this.translation = new Vec3(transX, transY, transZ);
        this.centreRotation = new Vec3(centreX, centreY, centreZ);
        this.rotation = new Quat4(rotX, rotY, rotZ, rotW);
    }

    // ── Getters et Setters ──

    public Vec3 getTranslation() {
        return translation;
    }

    public void setTranslation(Vec3 translation) {
        this.translation = translation != null ? translation : new Vec3(0f, 0f, 0f);
    }

    public void setTranslation(float x, float y, float z) {
        this.translation = new Vec3(x, y, z);
    }

    public Vec3 getCentreRotation() {
        return centreRotation;
    }

    public void setCentreRotation(Vec3 centreRotation) {
        this.centreRotation = centreRotation != null ? centreRotation : new Vec3(0f, 0f, 0f);
    }

    public void setCentreRotation(float x, float y, float z) {
        this.centreRotation = new Vec3(x, y, z);
    }

    public Quat4 getRotation() {
        return rotation;
    }

    public void setRotation(Quat4 rotation) {
        this.rotation = rotation != null ? rotation : new Quat4(0f, 0f, 0f, 1f);
    }

    public void setRotation(float x, float y, float z, float w) {
        this.rotation = new Quat4(x, y, z, w);
    }

    public float getScale() {
        return scale;
    }

    public void setScale(float scale) {
        this.scale = scale;
    }

    /**
     * Réinitialise la transformation à l'identité.
     */
    public void reset() {
        this.translation = new Vec3(0f, 0f, 0f);
        this.centreRotation = new Vec3(0f, 0f, 0f);
        this.rotation = new Quat4(0f, 0f, 0f, 1f);
    }

    @Override
    public String toString() {
        return String.format(
            "GlobalTransformation{translation=%s, centreRotation=%s, rotation=%s}",
            translation, centreRotation, rotation
        );
    }

    
    // ─────────────────────────────────────────────────────────────────────────
    // Calcul de la transformation finale à appliquer à une entité, en combinant la transformation globale et les transformations individuelles du display
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Applique la transformation globale à une translation de display individuelle, en tenant compte de la rotation globale.
     * @param displayTranslation
     * @return
     */
    public Vec3 getTranslation(Vec3 displayTranslation) {
        return displayTranslation.sub(centreRotation).rotate(rotation).add(translation).add(centreRotation);
    }

    /**
     * Applique la rotation globale à une rotation de display individuelle.
     * @param displayRotation
     * @return
     */    
    public Quat4 getRotation(Quat4 displayRotation) {
        // Multiplie la rotation globale par la rotation du display (ordre important)
        return rotation.mul(displayRotation);
    }

    /**
     * Applique la rotation globale à une échelle de display individuelle.
     * @param displayScale
     * @return
     */    
    public Vec3 getScale(Vec3 displayScale) {
        // Multiplie l'échelle globale par l'échelle du display
        return displayScale.mul(scale);
    }
}
