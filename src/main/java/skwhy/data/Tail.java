package skwhy.data;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Queue animée composée d'un arbre de {@link TailNode}.
 *
 * Chaque segment suit un ressort angulaire amorti vers une rotation cible composée de :
 * la pose repos, une déflexion liée à la vélocité locale, une ondulation sinusoïdale
 * et un bruit aléatoire basse fréquence.
 *
 * Le yaw est géré par l'entité attachée ; toutes les rotations ici sont en espace
 * local joueur (yaw exclu). Appeler {@link #nextFrame} une fois par tick.
 */
public class Tail {

    // =========================================================================
    // PARAMÈTRES MODIFIABLES
    // =========================================================================

    // ── Ressort angulaire ─────────────────────────────────────────────────────
    private float rigidity          = 8f;    // Raideur (4=souple, 16=rigide)
    private float damping           = 5.5f;  // Amortissement (~2×√rigidity pour éviter les oscillations)
    private float velocitySmoothing = 0.85f; // Lissage EWMA de la vélocité [0–1]

    // ── Déflexion par vélocité ────────────────────────────────────────────────
    private float velocityInfluenceForward  = 0.30f; // Influence vitesse avant/arrière (local Z) en rad/(bloc·s⁻¹)
    private float velocityInfluenceLateral  = 0.30f; // Influence vitesse gauche/droite (local X)
    private float velocityInfluenceVertical = 0.20f; // Influence vitesse verticale (Y) → saut/chute
    private float velocityInfluenceYaw      = 0.07f; // Influence rotation yaw → faible car les virages peuvent être brusques
    private float maxDeflectionDeg          = 20f;   // Angle max déflexion horizontale (deg)
    private float depthDeflectionFactor     = 0.15f; // Amplification par niveau de profondeur (0=uniforme)

    // ── Chocs / impulsions (accélération brusque) ─────────────────────────────
    private float impulseInfluenceForward  = 0.4f; // Impulsion choc avant/arrière (local Z)
    private float impulseInfluenceLateral  = 0.4f; // Impulsion choc gauche/droite (local X)
    private float impulseInfluenceVertical = 0.7f; // Impulsion choc vertical (Y) → atterrissage/saut

    private float impulseSmoothing = 0.6f; // Lissage du choc [0-1] (0.6 = très étalé sur ~2-3 ticks)
    private float impulseDeadzone  = 0.4f; // Vélocité minimale (blocs/s) pour déclencher un choc (filtre le lag)
    // ── Ondulation sinusoïdale ────────────────────────────────────────────────
    private float undulationAmplitudeX  = 7.0f;  // Amplitude ondulation axe X (deg) → tangage
    private float undulationAmplitudeY  = 4.2f;  // Amplitude ondulation axe Y (deg) → lacet
    private float undulationAmplitudeZ  = 2.8f;  // Amplitude ondulation axe Z (deg) → roulis
    private float undulationFrequency   = 1.0f;  // Fréquence de l'ondulation (Hz)
    private float undulationPropagation = 0.45f; // Décalage de phase entre segments (rad) → effet de vague

    // ── Mouvement aléatoire basse fréquence ──────────────────────────────────
    private float randomAmplitudeDeg = 4f;    // Amplitude du bruit aléatoire par nœud (deg)
    private float randomFrequency    = 0.35f; // Fréquence du bruit aléatoire (Hz)

    // =========================================================================
    // ÉTAT INTERNE (calculs uniquement — ne pas modifier directement)
    // =========================================================================

    private float posLastX = Float.NaN, posLastY, posLastZ; // Position précédente pour dériver la vélocité
    private float lastYaw      = Float.NaN;                 // Yaw précédent pour dériver la vitesse angulaire
    private float yawVelocity  = 0f;                        // Vitesse angulaire de rotation (degrés/s, lissée)
    private float lastRawVelX  = Float.NaN;                 // Vélocité locale brute X du tick précédent
    private float lastRawVelY, lastRawVelZ;                 // Vélocité locale brute Y/Z du tick précédent

    private final Vector3f    smoothedLocalVel   = new Vector3f();    // Vélocité lissée en espace local (yaw retiré)
    private final Vector3f    deltaLocalVel      = new Vector3f();    // Delta de vélocité locale (détection choc)
    private final Quaternionf velocityTargetQuat = new Quaternionf(); // Quaternion cible calculé depuis la vélocité pondérée par les influences
    private       float       velocityImpact     = 0f;                // Poids du quaternion cible [0–1], proportionnel à la vitesse brute
    private final Quaternionf qTemp              = new Quaternionf(); // Buffer partagé — uniquement dans computeTargetRotation

    private static final Quaternionf IDENTITY = new Quaternionf();

    private final TailNode root;
    private final Random   rng  = new Random();
    private       float    time = 0f;
    // Temps de la dernière mise à jour pour cette instance (nano secondes)
    private       long     lastUpdateNanos = 0L;

    // =========================================================================
    // NŒUD
    // =========================================================================

    public final class TailNode {

        // ── Définition ───────────────────────────────────────────────────────
        public final DisplayGroupData display;
        public final List<TailNode>   children     = new ArrayList<>();
        public final int              depth;
        public       TailNode         parent;
        final        Vector3f         offset;       // Translation repos relative au parent
        final        Quaternionf      restRotation; // Pose neutre (yaw exclu)

        // ── Phases d'animation (fixes par nœud, initialisées aléatoirement) ─
        final float phaseUndX; // Phase ondulation axe X
        final float phaseUndY; // Phase ondulation axe Y (déphasée de π/2 pour effet 3D)
        final float phaseRndX; // Phase bruit aléatoire X
        final float phaseRndY; // Phase bruit aléatoire Y

        // ── État physique ────────────────────────────────────────────────────
        final Quaternionf currentRot;              // Rotation courante en espace local parent
        final Vector3f    angVel = new Vector3f(); // Vitesse angulaire (rad/s)

        // ── Buffers pré-alloués (zéro allocation par tick) ──────────────────
        final Quaternionf targetBuf      = new Quaternionf(); // Rotation cible calculée
        final Quaternionf errorBuf       = new Quaternionf(); // Quaternion d'erreur ressort
        final Quaternionf errPremul      = new Quaternionf(); // Buffer intermédiaire premul
        final Quaternionf diffBuf        = new Quaternionf(); // Différence rotation globale prospective → quaternion cible vélocité
        final Vector3f    springBuf      = new Vector3f();    // Force résultante du ressort
        final Quaternionf globalRot      = new Quaternionf(); // Rotation globale accumulée (parentRot × currentRot)
        final Vector3f    currentOrigin  = new Vector3f();    // Position globale du nœud
        final Vector3f    rotatedOffsetBuf = new Vector3f();  // Buffer pour l'offset tourné (calcul d'origine)

        private TailNode(DisplayGroupData display, Vec3 offset, int depth) {
            display.setInterpolationDuration(2);
            display.setTeleportationDuration(2);
            this.display      = display;
            this.depth        = depth;
            this.parent       = null;
            this.offset       = new Vector3f(offset.x, offset.y, offset.z);
            this.restRotation = new Quaternionf(0, 0, 0, 1);
            this.currentRot   = new Quaternionf(restRotation);
            this.phaseUndX    = depth * undulationPropagation;
            this.phaseUndY    = depth * undulationPropagation + (float) Math.PI * 0.5f;
            this.phaseRndX    = rng.nextFloat() * (float) (Math.PI * 2);
            this.phaseRndY    = rng.nextFloat() * (float) (Math.PI * 2);
        }

        /** Retourne l'instance Tail parente. */
        public Tail getTailFromNode() { return Tail.this; }

        @Override public String toString() { return toStringRecursive(0); }

        private String toStringRecursive(int indent) {
            StringBuilder sb  = new StringBuilder();
            String        pad = "  ".repeat(indent);
            sb.append(pad).append("TailNode[depth=").append(depth)
              .append(", offset=(")
              .append(String.format("%.2f", offset.x)).append(", ")
              .append(String.format("%.2f", offset.y)).append(", ")
              .append(String.format("%.2f", offset.z))
              .append("), children=").append(children.size()).append("]\n");
            for (TailNode child : children) sb.append(child.toStringRecursive(indent + 1));
            return sb.toString();
        }
    }

    // =========================================================================
    // CONSTRUCTEUR & ARBRE
    // =========================================================================

    public Tail(DisplayGroupData rootDisplay, Vec3 offset) {
        this.root = new TailNode(rootDisplay, offset, 0);
        root.globalRot.set(root.currentRot);
        applyNodeTransform(root);
    }

    /** Ajoute un segment enfant à un nœud existant. L'arbre peut être linéaire ou en fourche. */
    public TailNode addSegment(TailNode parent, DisplayGroupData display, Vec3 offset) {
        TailNode node = new TailNode(display, offset, parent.depth + 1);
        node.parent = parent;
        parent.children.add(node);
        node.globalRot.set(parent.globalRot).mul(node.currentRot);
        applyNodeTransform(node);
        return node;
    }

    public TailNode getRoot()         { return root; }
    public int      getDisplayCount() { return countDisplays(root); }

    private int countDisplays(TailNode node) {
        int c = 1;
        for (TailNode child : node.children) c += countDisplays(child);
        return c;
    }

    // =========================================================================
    // POINT D'ENTRÉE : nextFrame
    // =========================================================================

    /** Met à jour l'animation (calcul automatique du delta-time par instance). */
    public void nextFrame(Location location) {
        long now = System.nanoTime();
        float dt;
        if (lastUpdateNanos <= 0L) {
            dt = 0.05f; // première exécution -> valeur par défaut
        } else {
            dt = (now - lastUpdateNanos) / 1_000_000_000.0f;
            if (dt <= 0f) dt = 0.05f;
            if (dt > 0.5f) dt = 0.5f; // clamp pour éviter des sauts trop grands
        }
        lastUpdateNanos = now;
        nextFrame(location, dt);
    }

    /** Met à jour l'animation avec un deltaTime explicite. Utile pour un TPS variable. */
    public void nextFrame(Location location, float dt) {
        if (dt <= 0f) return;
        float x   = (float) location.getX();
        float y   = (float) location.getY();
        float z   = (float) location.getZ();
        float yaw = location.getYaw();
        time += dt;
        updateSmoothedVelocity(x, y, z, yaw, dt);
        updateNode(root, dt);
    }

    // =========================================================================
    // LOGIQUE INTERNE
    // =========================================================================

    /**
     * Transforme la vélocité monde en espace local (yaw retiré) et applique le lissage EWMA.
     * Convention Minecraft : yaw 0 = sud (+Z), croît dans le sens horaire vu du dessus.
     */
    private void updateSmoothedVelocity(float x, float y, float z, float yaw, float dt) {
        if (Float.isNaN(posLastX)) {
            posLastX = x; posLastY = y; posLastZ = z;
            lastYaw  = yaw;
            return;
        }

        // Vélocité monde brute (blocs/s)
        float vwx = (x - posLastX) / dt;
        float vwy = (y - posLastY) / dt;
        float vwz = (z - posLastZ) / dt;
        posLastX = x; posLastY = y; posLastZ = z;

        // Rotation vers l'espace local joueur : local X = droite, local Z = avant
        float yr  = (float) Math.toRadians(yaw);
        float cos = (float) Math.cos(yr);
        float sin = (float) Math.sin(yr);
        float lvx = -vwx * cos - vwz * sin;
        float lvz = -vwx * sin + vwz * cos;


        // Lissage exponentiel (EWMA)
        float keep = velocitySmoothing, take = 1f - keep;
        smoothedLocalVel.x = smoothedLocalVel.x * keep + lvx * take;
        smoothedLocalVel.y = smoothedLocalVel.y * keep + vwy * take;
        smoothedLocalVel.z = smoothedLocalVel.z * keep + lvz * take;

        // Delta de vélocité locale = choc détecté ce tick
        if (Float.isNaN(lastRawVelX)) {
            deltaLocalVel.zero();
        } else {
            // 1. Calcul du delta brut (accélération/choc instantané)
            float rawDeltaX = lvx - lastRawVelX;
            float rawDeltaY = vwy - lastRawVelY;
            float rawDeltaZ = lvz - lastRawVelZ;

            // 2. Application de la Deadzone (on ignore les variations dues aux micro-lags)
            if (Math.abs(rawDeltaX) < impulseDeadzone) rawDeltaX = 0f;
            if (Math.abs(rawDeltaY) < impulseDeadzone) rawDeltaY = 0f;
            if (Math.abs(rawDeltaZ) < impulseDeadzone) rawDeltaZ = 0f;

            // 3. Lissage du Delta (étalement de l'impulsion sur plusieurs frames)
            float iKeep = impulseSmoothing;
            float iTake = 1f - iKeep;
            
            deltaLocalVel.x = deltaLocalVel.x * iKeep + rawDeltaX * iTake;
            deltaLocalVel.y = deltaLocalVel.y * iKeep + rawDeltaY * iTake;
            deltaLocalVel.z = deltaLocalVel.z * iKeep + rawDeltaZ * iTake;
        }
        lastRawVelX = lvx; lastRawVelY = vwy; lastRawVelZ = lvz;

        // --- ABSORPTION D'INERTIE LORS D'UN FREINAGE OU D'UN CHOC ---
        // Si la force du choc (deltaLocalVel) s'oppose au mouvement actuel (smoothedLocalVel),
        // on draine la vélocité lissée pour éviter l'effet "mémoire/rebond".
        
        // Axe X (Latéral)
        if (smoothedLocalVel.x * deltaLocalVel.x < 0) {
            smoothedLocalVel.x += deltaLocalVel.x;
            // Si la soustraction a inversé le sens de la vélocité lissée, on la bloque à 0
            if (Math.signum(smoothedLocalVel.x) == Math.signum(deltaLocalVel.x)) smoothedLocalVel.x = 0f;
        }
        
        // Axe Y (Vertical : Chute et Atterrissage)
        if (smoothedLocalVel.y * deltaLocalVel.y < 0) {
            smoothedLocalVel.y += deltaLocalVel.y;
            if (Math.signum(smoothedLocalVel.y) == Math.signum(deltaLocalVel.y)) smoothedLocalVel.y = 0f;
        }
        
        // Axe Z (Frontal : Course et Collision contre un mur)
        if (smoothedLocalVel.z * deltaLocalVel.z < 0) {
            smoothedLocalVel.z += deltaLocalVel.z;
            if (Math.signum(smoothedLocalVel.z) == Math.signum(deltaLocalVel.z)) smoothedLocalVel.z = 0f;
        }

        // Vitesse angulaire (yaw) lissée — normalisation pour éviter le saut ±180°
        float deltaYaw = yaw - lastYaw;
        while (deltaYaw >  180f) deltaYaw -= 360f;
        while (deltaYaw < -180f) deltaYaw += 360f;
        yawVelocity = yawVelocity * keep + (deltaYaw / dt) * take;
        lastYaw = yaw;

        // Quaternion cible : On combine la vélocité continue (smoothedLocalVel) ET le choc (deltaLocalVel)
        float tx = (smoothedLocalVel.x * velocityInfluenceLateral)  + (deltaLocalVel.x * impulseInfluenceLateral);
        float ty = (smoothedLocalVel.y * velocityInfluenceVertical) + (deltaLocalVel.y * impulseInfluenceVertical);
        float tz = (smoothedLocalVel.z * velocityInfluenceForward)  + (deltaLocalVel.z * impulseInfluenceForward);
        float wLenSq = tx*tx + ty*ty + tz*tz;
        if (wLenSq > 1e-6f) {
            velocityTargetQuat.rotationTo(0f, 0f, 1f, -tx, ty, tz); // rotationTo normalise en interne
        } else {
            velocityTargetQuat.identity();
        }

        velocityImpact = Math.min((float) Math.log(1+wLenSq) * velocityInfluenceForward * velocityInfluenceLateral * velocityInfluenceVertical, 1f);
    }

    /** Mise à jour récursive du nœud et de ses enfants. */
    private void updateNode(TailNode node, float dt) {
        computeTargetRotation(node, node.targetBuf);

        applySpring(node, node.targetBuf, dt);

        // ── Application du quaternion cible vélocité ──────────────────────────
        // 1. Rotation globale prospective (après ressort, avant correction)
        Quaternionf parentGlobal = (node.parent == null) ? IDENTITY : node.parent.globalRot;
        node.globalRot.set(parentGlobal).mul(node.currentRot);

        // 2. Différence entre rotation globale prospective et quaternion cible :
        //    diff = globalProspective⁻¹ × velocityTargetQuat
        //    représente la rotation manquante pour atteindre l'objectif en espace global.
        node.diffBuf.set(node.globalRot).conjugate().mul(velocityTargetQuat);

        // 3. Fraction de la différence à appliquer, amplifiée par la profondeur.
        //    L'extrémité de la queue suit davantage la cible que la base.
        float nodeImpact = Math.min(velocityImpact * (1f + node.depth * depthDeflectionFactor), 1f);
        // slerp(diffBuf, IDENTITY, 1−nodeImpact) : à nodeImpact=0 → identité, à 1 → diff entière
        node.diffBuf.slerp(IDENTITY, 1f - nodeImpact);

        // 4. La correction est en espace global ; ramené en local :
        //    newGlobal = globalProspective × scaledDiff
        //    newCurrentRot = parentGlobal⁻¹ × newGlobal = currentRot × scaledDiff
        node.currentRot.mul(node.diffBuf).normalize();

        // --- 5. LIMITATION DE L'ANGLE LOCAL (SWING X/Y) ---
        // A. Calcul de la déviation par rapport à la pose de repos (Deviation = currentRot * restRotation^-1)
        node.errorBuf.set(node.restRotation).conjugate();
        node.errorBuf.premul(node.currentRot);

        // On s'assure que W est positif pour emprunter le chemin le plus court
        if (node.errorBuf.w < 0f) node.errorBuf.mul(-1f);

        // B. Décomposition Swing-Twist (Twist = rotation sur l'axe Z)
        float twistLen = (float) Math.sqrt(node.errorBuf.z * node.errorBuf.z + node.errorBuf.w * node.errorBuf.w);
        float twZ = 0f, twW = 1f;
        if (twistLen > 1e-6f) {
            twZ = node.errorBuf.z / twistLen;
            twW = node.errorBuf.w / twistLen;
        }

        // C. Extraction du Swing (Pliage X/Y) : Swing = Deviation * Twist^-1
        node.diffBuf.set(0f, 0f, -twZ, twW); // Twist^-1
        node.errPremul.set(node.errorBuf).mul(node.diffBuf); // errPremul = Swing

        // D. Vérification de l'angle du Swing (W représente le cosinus de la moitié de l'angle)
        float maxRad = (float) Math.toRadians(maxDeflectionDeg);
        float cosHalfMax = (float) Math.cos(maxRad * 0.5f);

        if (node.errPremul.w < cosHalfMax) {
            // L'angle de pliage dépasse maxDeflectionDeg ! On le bride (Clamp).
            float sinHalfMax = (float) Math.sin(maxRad * 0.5f);
            float currentSinHalf = (float) Math.sqrt(node.errPremul.x * node.errPremul.x + node.errPremul.y * node.errPremul.y);
            
            if (currentSinHalf > 1e-6f) {
                float scale = sinHalfMax / currentSinHalf;
                node.errPremul.x *= scale;
                node.errPremul.y *= scale;
                node.errPremul.z = 0f; // Sécurité pour garder un pur Swing
                node.errPremul.w = cosHalfMax;
            }
        }

        // E. Recombinaison : Nouvelle Déviation = Swing_Clamped * Twist
        node.diffBuf.set(0f, 0f, twZ, twW); // Twist normal
        node.errorBuf.set(node.errPremul).mul(node.diffBuf);

        // F. Application de la déviation bridée à la rotation courante
        node.currentRot.set(node.errorBuf).mul(node.restRotation).normalize();
        
        // G. Recalcul indispensable de la rotation globale avec ce nouveau currentRot sécurisé
        node.globalRot.set(parentGlobal).mul(node.currentRot);
        // ----------


        updateNodeOrigin(node);
        applyNodeTransform(node);

        for (TailNode child : node.children) updateNode(child, dt);
    }

    /**
     * Calcule la rotation cible du nœud (pose repos + ondulation + bruit + yaw).
     * La déflexion due à la vélocité est gérée séparément dans {@link #updateNode}
     * via le quaternion cible global. Utilise {@code qTemp} comme buffer interne.
     */
    private void computeTargetRotation(TailNode node, Quaternionf out) {
        out.set(node.restRotation);

        float depthFactor = 1f + node.depth * depthDeflectionFactor;

        // ── Ondulation sinusoïdale (3 axes, fréquences légèrement différentes) ─
        float undX = (float) Math.toRadians(undulationAmplitudeX)
                     * (float) Math.sin(time * undulationFrequency          + node.phaseUndX);
        float undY = (float) Math.toRadians(undulationAmplitudeY)
                     * (float) Math.sin(time * undulationFrequency * 0.73f  + node.phaseUndY);
        float undZ = (float) Math.toRadians(undulationAmplitudeZ)
                     * (float) Math.sin(time * undulationFrequency * 1.37f  + node.phaseUndX + node.phaseUndY);

        // ── Bruit aléatoire basse fréquence ──────────────────────────────────
        float rndAmp = (float) Math.toRadians(randomAmplitudeDeg);
        float rndX   = rndAmp * (float) Math.sin(time * randomFrequency         + node.phaseRndX);
        float rndY   = rndAmp * (float) Math.sin(time * randomFrequency * 1.37f + node.phaseRndY);
        float rndZ   = rndAmp * (float) Math.sin(time * randomFrequency * 0.73f + node.phaseRndX + node.phaseRndY);

        float totalX = undX + rndX;
        float totalY = undY + rndY + (float) Math.toRadians(yawVelocity * velocityInfluenceYaw) * depthFactor;
        float totalZ = undZ + rndZ;

        if (Math.abs(totalX) > 1e-4f || Math.abs(totalY) > 1e-4f || Math.abs(totalZ) > 1e-4f) {
            qTemp.identity()
                 .rotateAxis(totalX, 1f, 0f, 0f)
                 .rotateAxis(totalY, 0f, 1f, 0f)
                 .rotateAxis(totalZ, 0f, 0f, 1f);
            qTemp.mul(out, out);
        }
    }

    /**
     * Ressort angulaire amorti : intègre la rotation et la vitesse angulaire du nœud.
     * Erreur quaternion → axe-angle → force = rigidity×err − damping×angVel → intégration Euler.
     */
    private void applySpring(TailNode node, Quaternionf target, float dt) {
        // Erreur = target × current⁻¹
        node.errorBuf.set(node.currentRot).conjugate();
        node.errPremul.set(target).mul(node.errorBuf, node.errorBuf);

        // Extraction de l'axe-angle : |xyz| = sin(θ/2)
        float sinHalf = (float) Math.sqrt(
            node.errorBuf.x * node.errorBuf.x +
            node.errorBuf.y * node.errorBuf.y +
            node.errorBuf.z * node.errorBuf.z);

        if (sinHalf > 1e-6f) {
            float s = 2f * (float) Math.atan2(sinHalf, node.errorBuf.w) / sinHalf * rigidity;
            node.springBuf.set(node.errorBuf.x * s, node.errorBuf.y * s, node.errorBuf.z * s);
        } else {
            node.springBuf.zero();
        }

        // Force = ressort − amortissement
        node.springBuf.x -= node.angVel.x * damping;
        node.springBuf.y -= node.angVel.y * damping;
        node.springBuf.z -= node.angVel.z * damping;

        // Intégration Euler de la vitesse angulaire
        node.angVel.x += node.springBuf.x * dt;
        node.angVel.y += node.springBuf.y * dt;
        node.angVel.z += node.springBuf.z * dt;

        // Intégration du quaternion : dq/dt = ½ · (0,wx,wy,wz) · q
        float wx = node.angVel.x, wy = node.angVel.y, wz = node.angVel.z;
        float qw = node.currentRot.w, qx = node.currentRot.x,
              qy = node.currentRot.y, qz = node.currentRot.z;
        node.currentRot.set(
            qx + 0.5f * ( wx*qw + wz*qy - wy*qz) * dt,
            qy + 0.5f * ( wy*qw - wz*qx + wx*qz) * dt,
            qz + 0.5f * ( wz*qw + wy*qx - wx*qy) * dt,
            qw + 0.5f * (-wx*qx - wy*qy - wz*qz) * dt
        ).normalize();
    }

    /** Calcule la position globale du nœud à partir de son parent. */
    private void updateNodeOrigin(TailNode node) {
        if (node.parent == null) {
            node.currentOrigin.set(0f, -1f, -0.1f);
        } else {
            node.rotatedOffsetBuf.set(node.offset).rotate(node.parent.globalRot);
            node.currentOrigin.set(node.parent.currentOrigin).add(node.rotatedOffsetBuf);
        }
    }

    /** Envoie la position et la rotation courantes au DisplayGroupData. */
    private void applyNodeTransform(TailNode node) {
        Quaternionf r = node.globalRot;
        node.display.setTranslation(new Vec3(node.currentOrigin));
        node.display.setRotation(new Quat4(new Quaternionf(r.x, r.y, r.z, r.w)));
        node.display.updateMetadata();
    }

    // =========================================================================
    // GESTION DE L'ARBRE (délégation récursive)
    // =========================================================================

    public List<Player> getViewers()                   { return root.display.getViewers(); }
    public void addViewer(Player player)               { addViewerNode(root, player); }
    public void setViewers(List<Player> viewers)       { setViewersNode(root, viewers); }
    public void removeViewer(Player player)            { removeViewerNode(root, player); }
    public void clearViewers()                         { clearViewersNode(root); }
    public void delete()                               { deleteNode(root); }
    public void setScale(float scale)                  { setScaleNode(root, scale); }
    public void setInterpolationDuration(int duration) { setInterpolationDurationNode(root, duration); }
    public void setTeleportationDuration(int duration) { setTeleportationDurationNode(root, duration); }
    public void setAttachedEntity(org.bukkit.entity.Entity entity) { setAttachedEntityNode(root, entity); }

    public void setYawPitch(float yaw, float pitch) { setYawPitchNode(root, yaw, pitch); }

    private void addViewerNode(TailNode node, Player player) {
        node.display.addViewer(player);
        for (TailNode c : node.children) addViewerNode(c, player);
    }
    private void setViewersNode(TailNode node, List<Player> viewers) {
        node.display.setViewers(viewers);
        for (TailNode c : node.children) setViewersNode(c, viewers);
    }
    private void removeViewerNode(TailNode node, Player player) {
        node.display.removeViewer(player);
        for (TailNode c : node.children) removeViewerNode(c, player);
    }
    private void clearViewersNode(TailNode node) {
        node.display.clearViewers();
        for (TailNode c : node.children) clearViewersNode(c);
    }
    private void deleteNode(TailNode node) {
        node.display.delete();
        for (TailNode c : node.children) deleteNode(c);
    }
    private void setScaleNode(TailNode node, float scale) {
        node.display.setScale(scale);
        for (TailNode c : node.children) setScaleNode(c, scale);
    }
    private void setInterpolationDurationNode(TailNode node, int duration) {
        node.display.setInterpolationDuration(duration);
        for (TailNode c : node.children) setInterpolationDurationNode(c, duration);
    }
    private void setTeleportationDurationNode(TailNode node, int duration) {
        node.display.setTeleportationDuration(duration);
        for (TailNode c : node.children) setTeleportationDurationNode(c, duration);
    }
    private void setAttachedEntityNode(TailNode node, org.bukkit.entity.Entity entity) {
        node.display.setAttachedEntity(entity);
        for (TailNode c : node.children) setAttachedEntityNode(c, entity);
    }
    private void setYawPitchNode(TailNode node, float yaw, float pitch) {
        node.display.setYawPitch(yaw, pitch);
        for (TailNode c : node.children) setYawPitchNode(c, yaw, pitch);
    }

    // =========================================================================
    // ROTATIONS REPOS (parcours DFS)
    // =========================================================================

    /**
     * Applique une liste de quaternions (x,y,z,w par nœud, 4 floats chacun) en ordre DFS.
     * Les nœuds sans entrée correspondante conservent leur rotation actuelle.
     */
    public void setRestRotation(List<Quaternionf> rotations) {
        if (rotations == null || rotations.isEmpty()) return;
        int[] idx = {0};
        setRestRotationDFS(root, rotations, idx);
    }

    private void setRestRotationDFS(TailNode node, List<Quaternionf> rots, int[] idx) {
        if (idx[0] < rots.size()) {
            node.restRotation.set(rots.get(idx[0]++));
        }
        for (TailNode child : node.children) setRestRotationDFS(child, rots, idx);
    }

    // =========================================================================
    // RESET
    // =========================================================================

    /** Remet tous les segments à la pose repos. Appeler après téléportation ou changement de cosmétique. */
    public void reset() {
        time = 0f;
        posLastX = lastRawVelX = lastYaw = Float.NaN;
        smoothedLocalVel.zero();
        deltaLocalVel.zero();
        resetNode(root);
    }

    private void resetNode(TailNode node) {
        node.currentRot.set(node.restRotation);
        node.angVel.zero();
        node.globalRot.set(node.currentRot);
        applyNodeTransform(node);
        for (TailNode child : node.children) resetNode(child);
    }

    // =========================================================================
    // SETTERS FLUIDES
    // =========================================================================

    // Ressort
    public Tail setRigidity(float v)          { rigidity = v;          return this; }
    public Tail setDamping(float v)           { damping = v;           return this; }
    public Tail setVelocitySmoothing(float v) { velocitySmoothing = v; return this; }

    // Déflexion vélocité
    public Tail setVelocityInfluenceForward(float v)     { velocityInfluenceForward = v;  return this; }
    public Tail setVelocityInfluenceLateral(float v)     { velocityInfluenceLateral = v;  return this; }
    public Tail setVelocityInfluenceVertical(float v)    { velocityInfluenceVertical = v; return this; }
    public Tail setVelocityInfluenceYaw(float v)         { velocityInfluenceYaw = v;      return this; }
    public Tail setMaxDeflectionAngle(float deg)         { maxDeflectionDeg = deg;        return this; }
    public Tail setDepthDeflectionFactor(float v)        { depthDeflectionFactor = v;     return this; }

    // Impulsions
    public Tail setImpulseInfluenceForward(float v)  { impulseInfluenceForward = v;  return this; }
    public Tail setImpulseInfluenceLateral(float v)  { impulseInfluenceLateral = v;  return this; }
    public Tail setImpulseInfluenceVertical(float v) { impulseInfluenceVertical = v; return this; }

    // Ondulation
    public Tail setUndulationAmplitudeX(float deg)  { undulationAmplitudeX = deg;    return this; }
    public Tail setUndulationAmplitudeY(float deg)  { undulationAmplitudeY = deg;    return this; }
    public Tail setUndulationAmplitudeZ(float deg)  { undulationAmplitudeZ = deg;    return this; }
    public Tail setUndulationFrequency(float v)     { undulationFrequency = v;       return this; }
    public Tail setUndulationPropagation(float v)   { undulationPropagation = v;     return this; }

    // Bruit aléatoire
    public Tail setRandomAmplitude(float deg) { randomAmplitudeDeg = deg; return this; }
    public Tail setRandomFrequency(float v)   { randomFrequency = v;      return this; }

    // =========================================================================
    // GETTERS
    // =========================================================================

    public float getRigidity()                  { return rigidity; }
    public float getDamping()                   { return damping; }
    public float getVelocitySmoothing()         { return velocitySmoothing; }
    public float getVelocityInfluenceForward()  { return velocityInfluenceForward; }
    public float getVelocityInfluenceLateral()  { return velocityInfluenceLateral; }
    public float getVelocityInfluenceVertical() { return velocityInfluenceVertical; }
    public float getVelocityInfluenceYaw()      { return velocityInfluenceYaw; }
    public float getMaxDeflectionAngle()        { return maxDeflectionDeg; }
    public float getDepthDeflectionFactor()     { return depthDeflectionFactor; }
    public float getImpulseInfluenceForward()   { return impulseInfluenceForward; }
    public float getImpulseInfluenceLateral()   { return impulseInfluenceLateral; }
    public float getImpulseInfluenceVertical()  { return impulseInfluenceVertical; }
    public float getUndulationAmplitudeX()      { return undulationAmplitudeX; }
    public float getUndulationAmplitudeY()      { return undulationAmplitudeY; }
    public float getUndulationAmplitudeZ()      { return undulationAmplitudeZ; }
    public float getUndulationFrequency()       { return undulationFrequency; }
    public float getUndulationPropagation()     { return undulationPropagation; }
    public float getRandomAmplitude()           { return randomAmplitudeDeg; }
    public float getRandomFrequency()           { return randomFrequency; }
}