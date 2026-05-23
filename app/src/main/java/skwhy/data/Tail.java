package skwhy.data;

import org.bukkit.entity.Player;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Queue animée composée d'un arbre de {@link TailNode}.
 *
 * <h3>Physique</h3>
 * Chaque segment suit un <b>ressort angulaire amorti</b> vers une rotation cible
 * composée de :
 * <ul>
 *   <li>la pose repos (quaternion défini à la création),</li>
 *   <li>une déflexion opposée à la vélocité locale du joueur,</li>
 *   <li>une ondulation lente sinusoïdale avec propagation le long de la chaîne,</li>
 *   <li>un mouvement aléatoire basse fréquence par nœud.</li>
 * </ul>
 *
 * <h3>Repère</h3>
 * Le yaw du joueur est géré par l'attache de l'entité ({@code setAttachedEntity}).
 * Toutes les rotations de cette classe sont donc en <b>espace local joueur, yaw exclu</b>.
 * La vélocité monde est transformée dans ce repère avant utilisation.
 *
 * <h3>Intégration dans CosmetiqueData</h3>
 * <pre>
 * // Dans CosmetiqueData, remplacer le champ :
 * //   private DisplayGroupData tail;
 * // par :
 * //   private Tail tail;
 * //
 * // Exemple de création :
 * Tail tail = new Tail(rootDisplay, restTrans, restRot)
 *     .setRigidity(8f)
 *     .setUndulationAmplitude(7f);
 * TailNode seg1 = tail.addSegment(tail.getRoot(), display1, trans1, rot1);
 * TailNode seg2 = tail.addSegment(seg1,           display2, trans2, rot2);
 *
 * // Chaque tick (ex. BukkitRunnable à 20 Hz) :
 * Location loc = entity.getLocation();
 * tail.nextFrame((float) loc.getX(), (float) loc.getY(), (float) loc.getZ(),
 *                loc.getYaw(), 0.05f);
 * </pre>
 *
 * <h3>Hypothèses sur Vec3 / Quat4</h3>
 * Le code accède aux composantes via des champs publics {@code x, y, z} (Vec3)
 * et {@code x, y, z, w} (Quat4). Adapter si ce sont des méthodes ({@code getX()}, etc.).
 */
public class Tail {

    // ─────────────────────────────────────────────────────────────────────────
    // Paramètres – modifiables via les setters fluides
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Raideur du ressort angulaire (rad·s⁻²·rad⁻¹).
     * Plus la valeur est élevée, plus la queue revient vite vers sa pose repos.
     * Valeurs typiques : 4 (souple) à 16 (rigide).
     */
    private float rigidity              = 8f;

    /**
     * Coefficient d'amortissement (rad·s⁻¹).
     * Pour éviter les oscillations infinies, viser environ 2 × √rigidity.
     * Défaut : 5.5 ≈ 2 × √8.
     */
    private float damping               = 5.5f;

    /**
     * Lissage exponentiel de la vélocité [0–1].
     * 0 = vélocité brute, proche de 1 = très lissée (réponse plus lente).
     */
    private float velocitySmoothing     = 0.85f;

    /**
     * Facteur de conversion vitesse → angle de déflexion (rad / (bloc/tick·20)).
     * Augmenter pour que la queue penche davantage lors des déplacements rapides.
     */
    private float velocityInfluence     = 0.30f;

    /**
     * Angle maximum de déflexion dû à la vélocité, en degrés.
     * Évite les postures absurdes à grande vitesse.
     */
    private float maxDeflectionDeg      = 40f;

    /**
     * Amplification de la déflexion par niveau de profondeur.
     * 0 = uniforme, 0.15 = l'extrémité penche 15 % de plus par niveau que son parent.
     */
    private float depthDeflectionFactor = 0.15f;

    /** Amplitude de l'ondulation principale, en degrés. */
    private float undulationAmplitudeDeg = 7f;

    /** Fréquence de l'ondulation principale, en Hz. */
    private float undulationFrequency    = 1.0f;

    /**
     * Décalage de phase de l'ondulation entre niveaux successifs (radians).
     * Crée l'effet de propagation de vague le long de la queue.
     */
    private float undulationPropagation  = 0.45f;

    /** Amplitude du mouvement aléatoire basse fréquence, en degrés. */
    private float randomAmplitudeDeg     = 4f;

    /** Fréquence du mouvement aléatoire, en Hz. */
    private float randomFrequency        = 0.35f;

    /**
     * Facteur de conversion de la vélocité verticale en angle de déflexion (rad / (bloc/s)).
     * Quand le joueur saute, la queue s'affaisse ; quand il tombe, elle se soulève.
     * Plus faible que {@code velocityInfluence} car les vitesses verticales Minecraft
     * sont plus élevées que les vitesses horizontales.
     */
    private float verticalVelocityInfluence  = 0.08f;

    /**
     * Angle maximum de déflexion verticale en degrés.
     * Évite que la queue parte à la verticale lors d'une chute libre.
     */
    private float maxVerticalDeflectionDeg   = 25f;

    // ─────────────────────────────────────────────────────────────────────────
    // État interne
    // ─────────────────────────────────────────────────────────────────────────

    private final TailNode  root;
    private final Random    rng  = new Random();
    private       float     time = 0f;

    // Suivi de la position pour le calcul de vélocité
    private float posLastX = Float.NaN, posLastY, posLastZ;

    // Vélocité lissée en espace local joueur (yaw retiré)
    private final Vector3f smoothedLocalVel = new Vector3f();

    // Buffer quaternion partagé — utilisé UNIQUEMENT dans computeTargetRotation
    // (méthode non récursive). Ne jamais l'utiliser dans applySpring ni updateNode.
    private final Quaternionf qTemp = new Quaternionf();

    // Quaternion identité immuable — ne jamais modifier
    private static final Quaternionf IDENTITY = new Quaternionf();

    // ─────────────────────────────────────────────────────────────────────────
    // Nœud de l'arbre
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Représente un segment de la queue dans l'arbre.
     *
     * <p>Chaque nœud possède :
     * <ul>
     *   <li>un {@link DisplayGroupData} (le rendu visuel),</li>
     *   <li>une translation repos relative à son parent,</li>
     *   <li>une rotation repos (pose neutre, yaw exclu),</li>
     *   <li>une liste d'enfants (arbre N-aire),</li>
     *   <li>un état physique propre (rotation courante + vitesse angulaire).</li>
     * </ul>
     *
     * <p>Tous les buffers internes sont pré-alloués à la construction pour
     * éviter toute allocation par tick.
     */
    public final class TailNode {

        // ── Définition ───────────────────────────────────────────────────────

        /** Display visuel associé à ce segment. */
        public final DisplayGroupData display;

        /**
         * Translation repos de ce segment par rapport à son parent
         * (ou au point d'attache pour la racine), en espace local parent.
         */
        final Vector3f restTranslation;

        /**
         * Rotation repos en espace local parent, yaw exclu.
         * Définit la pose neutre autour de laquelle toutes les animations oscillent.
         */
        final Quaternionf restRotation;

        /** Segments enfants dans l'arbre de la queue. */
        public final List<TailNode> children = new ArrayList<>();

        /** Profondeur dans l'arbre (0 = racine). */
        public final int depth;

        // ── Phases d'animation (aléatoires, fixes par nœud) ─────────────────

        final float phaseUndX; // phase ondulation axe X
        final float phaseUndZ; // phase ondulation axe Z (déphasée de π/2 pour mouvement 3D)
        final float phaseRndX; // phase aléatoire axe X
        final float phaseRndZ; // phase aléatoire axe Z

        // ── État physique ────────────────────────────────────────────────────

        /**
         * Rotation courante en espace local parent, yaw exclu.
         * Initialisée à la pose repos ; évolue sous l'effet du ressort.
         */
        final Quaternionf currentRot;

        /**
         * Vitesse angulaire courante (rad/s).
         * Représentée comme vecteur axe × vitesse en espace monde local.
         */
        final Vector3f angVel = new Vector3f();

        // ── Buffers pré-alloués (zéro allocation par tick) ──────────────────

        final Quaternionf targetBuf   = new Quaternionf(); // rotation cible calculée
        final Quaternionf errorBuf    = new Quaternionf(); // quaternion d'erreur ressort
        final Vector3f    springBuf   = new Vector3f();    // vecteur force résultante
        final Quaternionf errPremul   = new Quaternionf(); // buffer intermédiaire premul
        /**
         * Rotation globale accumulée depuis la racine (parentRot × currentRot).
         * Utilisée pour calculer la position des segments enfants.
         */
        final Quaternionf globalRot   = new Quaternionf();
        /**
         * Position monde locale de ce nœud (dans l'espace joueur, yaw exclu).
         * Mise à jour chaque frame ; passée aux enfants pour le chaînage.
         */
        final Vector3f    worldPosBuf = new Vector3f();

        // ─────────────────────────────────────────────────────────────────────

        private TailNode(DisplayGroupData display, Vec3 restTrans, int depth) {
            display.setInterpolationDuration(2);
            display.setTeleportationDuration(2);
            this.display = display;
            this.depth   = depth;

            // Vec3 / Quat4 → JOML (champs publics x, y, z, w supposés)
            this.restTranslation = new Vector3f(restTrans.x, restTrans.y, restTrans.z);
            this.restRotation    = new Quaternionf(0, 0, 0, 1);
            this.currentRot      = new Quaternionf(restRotation);

            // Phases : propagation le long de la chaîne + bruit aléatoire par nœud
            this.phaseUndX = depth * undulationPropagation;
            this.phaseUndZ = depth * undulationPropagation + (float) Math.PI * 0.5f;
            this.phaseRndX = rng.nextFloat() * (float) (Math.PI * 2);
            this.phaseRndZ = rng.nextFloat() * (float) (Math.PI * 2);
        }
        public Tail getTailFromNode() {
            return Tail.this; // Permet aux classes externes d'accéder à l'instance parente
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Constructeur
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Crée une queue avec un seul segment racine.
     *
     * <p>Le {@code DisplayGroupData} doit déjà avoir été configuré avec
     * {@code setCenter()} et {@code setAttachedEntity()} avant d'appeler
     * cette méthode (comme dans {@code CosmetiqueData.setTail()}).
     *
     * @param rootDisplay Display du segment racine.
     * @param restTrans   Offset repos depuis le point d'attache (ex. {@code Vec3(0, -0.12f, -0.25f)}).
     * @param restRot     Rotation repos de la racine, yaw exclu.
     */
    public Tail(DisplayGroupData rootDisplay, Vec3 restTrans) {
        this.root = new TailNode(rootDisplay, restTrans, 0);
        // Position et rotation initiales de la racine
        root.worldPosBuf.set(root.restTranslation);
        root.globalRot.set(root.currentRot); // parentRot = identité
        applyNodeTransform(root);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Ajout de segments
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Ajoute un segment enfant à un nœud existant.
     *
     * <p>L'arbre peut être linéaire (queue simple) ou en fourche (queue multiple).
     * Chaque segment possède son propre {@code DisplayGroupData} et sa propre pose repos.
     *
     * @param parent    Nœud auquel rattacher le nouveau segment.
     * @param display   Display du nouveau segment (déjà configuré).
     * @param restTrans Translation repos par rapport au parent (offset dans l'espace local parent).
     * @param restRot   Rotation repos (yaw exclu).
     * @return Le nouveau {@link TailNode} créé et ajouté à l'arbre.
     */
    public TailNode addSegment(TailNode parent, DisplayGroupData display,
                               Vec3 restTrans) {
        TailNode node = new TailNode(display, restTrans, parent.depth + 1);
        parent.children.add(node);
        // Initialisation : position et rotation au repos
        node.worldPosBuf.set(node.restTranslation).rotate(parent.globalRot).add(parent.worldPosBuf);
        node.globalRot.set(parent.globalRot).mul(node.currentRot);
        applyNodeTransform(node);
        return node;
    }

    /** Renvoie le nœud racine de l'arbre. */
    public TailNode getRoot() { return root; }

    /**
     * Compte récursivement le nombre total de displays (DisplayGroupData) dans l'arbre.
     * @return Le nombre total de nœuds/displays dans la queue.
     */
    public int getDisplayCount() {
        return countDisplaysRecursive(root);
    }

    private int countDisplaysRecursive(TailNode node) {
        int count = 1; // Le nœud courant
        for (TailNode child : node.children) {
            count += countDisplaysRecursive(child);
        }
        return count;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Setters fluides
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Raideur du ressort angulaire.
     * Valeurs typiques : 4 (souple/organique) – 16 (rigide/mécanique). Défaut : 8.
     */
    public Tail setRigidity(float v)              { rigidity = v;               return this; }

    /**
     * Coefficient d'amortissement.
     * Trop bas = oscillations parasites ; trop haut = mouvement mort. Défaut : 5.5.
     */
    public Tail setDamping(float v)               { damping = v;                return this; }

    /**
     * Lissage exponentiel de la vélocité [0–1].
     * 0.85 donne un comportement fluide sans trop de délai. Défaut : 0.85.
     */
    public Tail setVelocitySmoothing(float v)     { velocitySmoothing = v;      return this; }

    /**
     * Influence de la vitesse sur l'angle de déflexion (rad/(bloc/s)).
     * Augmenter pour une queue plus expressive. Défaut : 0.30.
     */
    public Tail setVelocityInfluence(float v)     { velocityInfluence = v;      return this; }

    /** Angle maximal de déflexion en degrés. Défaut : 40°. */
    public Tail setMaxDeflectionAngle(float deg)  { maxDeflectionDeg = deg;     return this; }

    /**
     * Amplification de la déflexion par niveau (0 = uniforme). Défaut : 0.15.
     * L'extrémité de la queue penche davantage que la base lors du mouvement.
     */
    public Tail setDepthDeflectionFactor(float v) { depthDeflectionFactor = v;  return this; }

    /** Amplitude de l'ondulation en degrés. Défaut : 7°. */
    public Tail setUndulationAmplitude(float deg) { undulationAmplitudeDeg = deg; return this; }

    /** Fréquence de l'ondulation en Hz. Défaut : 1.0 Hz. */
    public Tail setUndulationFrequency(float v)   { undulationFrequency = v;    return this; }

    /**
     * Décalage de phase entre segments successifs (radians).
     * Crée l'effet de vague qui se propage le long de la queue. Défaut : 0.45 rad.
     */
    public Tail setUndulationPropagation(float v) { undulationPropagation = v;  return this; }

    /** Amplitude du mouvement aléatoire en degrés. Défaut : 4°. */
    public Tail setRandomAmplitude(float deg)     { randomAmplitudeDeg = deg;   return this; }

    /** Fréquence du mouvement aléatoire en Hz. Défaut : 0.35 Hz. */
    public Tail setRandomFrequency(float v)       { randomFrequency = v;        return this; }

    /**
     * Influence de la vélocité verticale sur la déflexion (rad/(bloc/s)).
     * Saut → queue s'affaisse ; chute libre → queue se soulève. Défaut : 0.08.
     */
    public Tail setVerticalVelocityInfluence(float v) { verticalVelocityInfluence = v; return this; }

    /** Angle maximal de déflexion verticale en degrés. Défaut : 25°. */
    public Tail setMaxVerticalDeflectionAngle(float deg) { maxVerticalDeflectionDeg = deg; return this; }

    // ─────────────────────────────────────────────────────────────────────────
    // Getters publics des paramètres
    // ─────────────────────────────────────────────────────────────────────────

    public float getRigidity()                    { return rigidity; }
    public float getDamping()                     { return damping; }
    public float getVelocitySmoothing()           { return velocitySmoothing; }
    public float getVelocityInfluence()           { return velocityInfluence; }
    public float getMaxDeflectionAngle()          { return maxDeflectionDeg; }
    public float getDepthDeflectionFactor()       { return depthDeflectionFactor; }
    public float getUndulationAmplitude()         { return undulationAmplitudeDeg; }
    public float getUndulationFrequency()         { return undulationFrequency; }
    public float getUndulationPropagation()       { return undulationPropagation; }
    public float getRandomAmplitude()             { return randomAmplitudeDeg; }
    public float getRandomFrequency()             { return randomFrequency; }
    public float getVerticalVelocityInfluence()   { return verticalVelocityInfluence; }
    public float getMaxVerticalDeflectionAngle()  { return maxVerticalDeflectionDeg; }

    // ─────────────────────────────────────────────────────────────────────────
    // Scale management
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Applique un facteur d'échelle à tous les segments de la queue.
     *
     * @param scale Le facteur d'échelle à appliquer (1.0 = taille normale).
     */
    public void setScale(float scale) {
        setScaleNode(root, scale);
    }

    private void setScaleNode(TailNode node, float scale) {
        node.display.setScale(scale);
        for (TailNode child : node.children) setScaleNode(child, scale);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Modification des rotations repos en parcours profondeur d'abord (DFS)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Applique une liste de quaternions à tous les nœuds de la queue
     * en suivant un parcours profondeur d'abord (DFS).
     *
     * <p>La liste doit contenir 4 floats (x, y, z, w) par nœud, dans l'ordre DFS.
     * Si la liste est trop courte, les nœuds restants garderont leur rotation.
     * Si la liste est trop longue, les floats supplémentaires seront ignorés.
     *
     * @param rotations Une liste de floats contenant les composantes (x, y, z, w)
     *                  des quaternions pour chaque nœud, en ordre DFS.
     *
     * @example Pour une queue avec 3 nœuds (racine + 2 enfants directs) :
     *          List.of(
     *            0.1f, 0.2f, 0.3f, 0.9f,  // racine
     *            0.0f, 0.0f, 0.5f, 0.87f, // enfant 1
     *            0.1f, 0.1f, 0.2f, 0.96f  // enfant 2
     *          )
     */
    public void setRestRotation(List<Float> rotations) {
        if (rotations == null || rotations.isEmpty()) return;
        int[] index = { 0 }; // Conteneur mutable pour tracker la position
        setRestRotationDFS(root, rotations, index);
    }

    /**
     * Parcours DFS récursif pour appliquer les rotations aux nœuds.
     * Visite d'abord le nœud courant, puis récursivement tous ses enfants.
     *
     * @param node      Le nœud actuel à traiter.
     * @param rotations La liste complète des floats.
     * @param index     Un tableau contenant l'index courant [position].
     *                  Modifié à chaque consommation de floats.
     */
    private void setRestRotationDFS(TailNode node, List<Float> rotations, int[] index) {
        // Consommer 4 floats pour ce nœud (x, y, z, w)
        if (index[0] + 3 < rotations.size()) {
            float x = rotations.get(index[0]++);
            float y = rotations.get(index[0]++);
            float z = rotations.get(index[0]++);
            float w = rotations.get(index[0]++);

            // Appliquer la quaternion au restRotation du nœud
            node.restRotation.set(x, y, z, w);
            // Réinitialiser aussi la rotation courante pour qu'elle suive
            node.currentRot.set(node.restRotation);
        }

        // Récursivement traiter tous les enfants (parcours DFS)
        for (TailNode child : node.children) {
            setRestRotationDFS(child, rotations, index);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Point d'entrée principal : update par tick
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Calcule la frame suivante de l'animation et envoie les mises à jour
     * aux viewers de chaque segment.
     *
     * <p>Appeler une fois par tick (typiquement toutes les 50 ms à 20 TPS).
     * Aucune allocation heap n'est effectuée dans cette méthode ni dans les
     * méthodes qu'elle appelle, à l'exception des deux objets {@code Vec3} et
     * {@code Quat4} nécessaires aux setters de {@link DisplayGroupData}.
     *
     * @param x         Position monde X de l'entité porteuse.
     * @param y         Position monde Y (non utilisé pour la vélocité XZ,
     *                  conservé pour de futures extensions verticales).
     * @param z         Position monde Z de l'entité porteuse.
     * @param yaw       Yaw Minecraft de l'entité (degrés, 0 = sud +Z, croît horaire).
     * @param deltaTime Temps écoulé depuis le dernier appel, en secondes.
     *                  Passer {@code 0.05f} pour un tick à 20 TPS.
     */
    public void nextFrame(Location location) {
        nextFrame(location, 0.05f); // 20 TPS par défaut
    }

    /**
     * Variante avec deltaTime explicite, utile si le serveur tourne à un TPS variable.
     *
     * @param location  Position actuelle de l'entité porteuse.
     * @param deltaTime Temps écoulé en secondes depuis le dernier appel.
     */
    public void nextFrame(Location location, float deltaTime) {
        float x = (float) location.getX();
        float y = (float) location.getY();
        float z = (float) location.getZ();
        float yaw = location.getYaw();
        if (deltaTime <= 0f) return;
        time += deltaTime;

        // 1. Calcul de la vélocité locale lissée
        updateSmoothedVelocity(x, y, z, yaw, deltaTime);

        // 2. Mise à jour récursive de l'arbre
        //    La racine part de sa translation repos (offset depuis l'attache joueur)
        root.worldPosBuf.set(root.restTranslation);
        updateNode(root, IDENTITY, deltaTime);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Gestion des viewers (délégation vers tous les displays)
    // ─────────────────────────────────────────────────────────────────────────


    public List<Player> getViewers() {
        return root.display.getViewers();
    }

    /** Ajoute un viewer à tous les segments de la queue. */
    public void addViewer(Player player) {
        addViewerNode(root, player);
    }

    private void addViewerNode(TailNode node, Player player) {
        node.display.addViewer(player);
        for (TailNode child : node.children) addViewerNode(child, player);
    }

    /** Remplace les viewers de tous les segments de la queue. */
    public void setViewers(List<Player> viewers) {
        setViewersNode(root, viewers);
    }

    private void setViewersNode(TailNode node, List<Player> viewers) {
        node.display.setViewers(viewers);
        for (TailNode child : node.children) setViewersNode(child, viewers);
    }

    /** Retire un viewer de tous les segments de la queue. */
    public void removeViewer(Player player) {
        removeViewerNode(root, player);
    }

    private void removeViewerNode(TailNode node, Player player) {
        node.display.removeViewer(player);
        for (TailNode child : node.children) removeViewerNode(child, player);
    }

    public void clearViewers() {
        clearViewersNode(root);
    }

    private void clearViewersNode(TailNode node) {
        node.display.clearViewers();
        for (TailNode child : node.children) clearViewersNode(child);
    }

    /** Supprime tous les displays de la queue. */
    public void delete() {
        deleteNode(root);
    }

    private void deleteNode(TailNode node) {
        node.display.delete();
        for (TailNode child : node.children) deleteNode(child);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Logique interne — aucune allocation heap en dehors des Vec3/Quat4 finaux
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Transforme la vélocité monde en espace local joueur (yaw retiré)
     * et applique le lissage exponentiel.
     *
     * <p>Convention Minecraft : yaw 0 = sud (+Z), croît dans le sens horaire vu du dessus.
     * <ul>
     *   <li>Local +X = droite du joueur</li>
     *   <li>Local +Z = devant du joueur</li>
     * </ul>
     */
    private void updateSmoothedVelocity(float x, float y, float z, float yaw, float dt) {
        if (Float.isNaN(posLastX)) {
            posLastX = x; posLastY = y; posLastZ = z;
            return;
        }

        // Vélocité monde brute (blocs/s)
        float vwx = (x - posLastX) / dt;
        float vwy = (y - posLastY) / dt; // verticale : pas de rotation yaw à appliquer
        float vwz = (z - posLastZ) / dt;
        posLastX = x; posLastY = y; posLastZ = z;

        // Rotation inverse du yaw → espace local
        float yr  = (float) Math.toRadians(yaw);
        float cos = (float) Math.cos(yr);
        float sin = (float) Math.sin(yr);
        float lvx =  vwx * cos + vwz * sin; // composante droite/gauche
        float lvz = -vwx * sin + vwz * cos; // composante avant/arrière

        // Lissage exponentiel (EWMA)
        float keep = velocitySmoothing, take = 1f - keep;
        smoothedLocalVel.x = smoothedLocalVel.x * keep + lvx  * take;
        smoothedLocalVel.y = smoothedLocalVel.y * keep + vwy  * take; // vertical lissé
        smoothedLocalVel.z = smoothedLocalVel.z * keep + lvz  * take;
    }

    /**
     * Met à jour récursivement un nœud et ses enfants.
     *
     * <p>Ordre des opérations pour chaque nœud :
     * <ol>
     *   <li>Calcul de la rotation cible (repos + vélocité + ondulation).</li>
     *   <li>Intégration du ressort angulaire → mise à jour de {@code currentRot}.</li>
     *   <li>Calcul de la rotation globale accumulée.</li>
     *   <li>Envoi de la transformation au {@code DisplayGroupData}.</li>
     *   <li>Calcul de la position de chaque enfant puis récursion.</li>
     * </ol>
     *
     * <p><b>Buffers :</b> chaque nœud possède ses propres buffers ({@code targetBuf},
     * {@code errorBuf}, etc.) ce qui rend la récursion sans conflit d'alias.
     * Le seul buffer partagé ({@code qTemp}) n'est utilisé que dans
     * {@code computeTargetRotation}, qui est appelée avant toute récursion.
     *
     * @param node      Nœud courant.
     * @param parentRot Rotation globale accumulée du parent (yaw exclu).
     * @param dt        Delta-temps en secondes.
     */
    private void updateNode(TailNode node, Quaternionf parentRot, float dt) {

        // A. Rotation cible = repos + déflexion vélocité + ondulation
        computeTargetRotation(node, node.targetBuf);

        // B. Ressort angulaire amortisseur → node.currentRot et node.angVel mis à jour
        applySpring(node, node.targetBuf, dt);

        // C. Rotation globale de ce nœud = parentRot × currentRot
        node.globalRot.set(parentRot).mul(node.currentRot);

        // D. Envoi de la transformation au display
        applyNodeTransform(node);

        // E. Récursion : position des enfants déduite du chaînage
        for (TailNode child : node.children) {
            // Position enfant = position parent + globalRot × restTranslation enfant
            child.worldPosBuf
                .set(child.restTranslation)
                .rotate(node.globalRot)
                .add(node.worldPosBuf);
            updateNode(child, node.globalRot, dt);
        }
    }

    /**
     * Calcule la rotation cible pour un nœud et l'écrit dans {@code out}.
     *
     * <p>La rotation cible est la composition de :
     * <ol>
     *   <li><b>Pose repos</b> : quaternion défini à la création du segment.</li>
     *   <li><b>Déflexion horizontale</b> : rotation dont l'axe est perpendiculaire
     *       à la direction de déplacement dans le plan XZ, de sorte que l'extrémité
     *       de la queue traîne derrière ({@code vel × down = (nz, 0, -nx)}).</li>
     *   <li><b>Déflexion verticale</b> : rotation autour de l'axe +X local ;
     *       saut → queue s'affaisse, chute → queue se soulève.</li>
     *   <li><b>Ondulation sinusoïdale</b> : deux sinus déphasés de π/2 sur X et Z,
     *       avec décalage de phase progressif entre segments.</li>
     *   <li><b>Mouvement aléatoire</b> : deux sinus basse fréquence avec phases
     *       aléatoires propres à chaque nœud.</li>
     * </ol>
     *
     * <p>Utilise {@link #qTemp} comme buffer temporaire. Ne jamais appeler
     * de méthode récursive avant la fin de cette méthode.
     */
    private void computeTargetRotation(TailNode node, Quaternionf out) {

        // Partir de la pose repos
        out.set(node.restRotation);

        // ── 1. Déflexion due à la vélocité (plan XZ) ────────────────────────
        float speedXZ = (float) Math.sqrt(
            smoothedLocalVel.x * smoothedLocalVel.x
          + smoothedLocalVel.z * smoothedLocalVel.z);

        if (speedXZ > 0.05f) {
            float maxRad = (float) Math.toRadians(maxDeflectionDeg);
            // Amplification progressive le long de la chaîne
            float angle  = Math.min(speedXZ * velocityInfluence, maxRad)
                           * (1f + node.depth * depthDeflectionFactor);

            float nx = smoothedLocalVel.x / speedXZ; // direction normalisée X
            float nz = smoothedLocalVel.z / speedXZ; // direction normalisée Z

            // Axe = vel × down = (nx,0,nz) × (0,-1,0) = (nz, 0, -nx)
            // Une rotation positive autour de cet axe fait traîner l'extrémité
            // à l'opposé de la direction de déplacement (physique de pendule).
            qTemp.identity().rotateAxis(angle, nz, 0f, -nx);
            qTemp.mul(out, out); // out = qTemp × out
        }

        // ── 1b. Déflexion due à la vélocité verticale ────────────────────────
        // Saut (vy > 0) : la queue traîne en bas  → rotation +X (tip vers -Y).
        // Chute (vy < 0) : la queue flotte vers le haut → rotation -X (tip vers +Y).
        // L'axe +X local (droite joueur) est perpendiculaire au plan vertical de déplacement
        // et produit un affaissement/soulèvement symétrique quelle que soit la direction.
        float vy = smoothedLocalVel.y;
        if (Math.abs(vy) > 0.1f) {
            float maxVertRad = (float) Math.toRadians(maxVerticalDeflectionDeg);
            float vAngle = vy > 0f
                ? Math.min( vy * verticalVelocityInfluence,  maxVertRad)
                : Math.max( vy * verticalVelocityInfluence, -maxVertRad);
            vAngle *= (1f + node.depth * depthDeflectionFactor);
            qTemp.identity().rotateAxis(vAngle, 1f, 0f, 0f);
            qTemp.mul(out, out);
        }

        // ── 2. Ondulation lente (deux composantes déphasées) ─────────────────
        float undAmp = (float) Math.toRadians(undulationAmplitudeDeg);
        float undX   = undAmp        * (float) Math.sin(time * undulationFrequency         + node.phaseUndX);
        float undZ   = undAmp * 0.6f * (float) Math.sin(time * undulationFrequency * 0.73f + node.phaseUndZ);

        // ── 3. Mouvement aléatoire basse fréquence ───────────────────────────
        float rndAmp = (float) Math.toRadians(randomAmplitudeDeg);
        float rndX   = rndAmp * (float) Math.sin(time * randomFrequency          + node.phaseRndX);
        float rndZ   = rndAmp * (float) Math.sin(time * randomFrequency * 1.37f  + node.phaseRndZ);

        float totalX = undX + rndX;
        float totalZ = undZ + rndZ;

        if (Math.abs(totalX) > 1e-4f || Math.abs(totalZ) > 1e-4f) {
            qTemp.identity()
                 .rotateAxis(totalX, 1f, 0f, 0f)
                 .rotateAxis(totalZ, 0f, 0f, 1f);
            qTemp.mul(out, out);
        }
    }

    /**
     * Applique la dynamique de ressort angulaire amorti et intègre la rotation.
     *
     * <h3>Algorithme</h3>
     * <ol>
     *   <li>Calcul du quaternion d'erreur : {@code error = target × current⁻¹}.</li>
     *   <li>Extraction de l'axe-angle pour obtenir le vecteur angulaire d'erreur.</li>
     *   <li>Force = rigidity × axeAngle − damping × vitesseAngulaire.</li>
     *   <li>Intégration de la vitesse angulaire (Euler explicite).</li>
     *   <li>Intégration du quaternion : {@code dq/dt = ½ · Ω̂ · q} (espace monde local).</li>
     * </ol>
     *
     * <p>La formule d'intégration du quaternion dérive de la dérivée de la
     * contrainte unitaire : multiplier un quaternion pur {@code (0, wx, wy, wz)}
     * à gauche donne la variation dans le repère monde.
     */
    private void applySpring(TailNode node, Quaternionf target, float dt) {

        // Erreur : error = target × current⁻¹
        // Pour un quaternion unitaire, l'inverse = conjugué.
        node.errorBuf.set(node.currentRot).conjugate();        // errorBuf = current⁻¹
        node.errPremul.set(target).mul(node.errorBuf, node.errorBuf); // errorBuf = target × current⁻¹

        // Extraction du vecteur axe × angle depuis le quaternion d'erreur
        // error = (cos(θ/2),  sin(θ/2)·axis)  →  |xyz| = sin(θ/2)
        float sinHalf = (float) Math.sqrt(
            node.errorBuf.x * node.errorBuf.x
          + node.errorBuf.y * node.errorBuf.y
          + node.errorBuf.z * node.errorBuf.z);

        if (sinHalf > 1e-6f) {
            // θ = 2·atan2(sinHalf, w)    ;    axis·θ = xyz/sinHalf · θ
            float theta = 2f * (float) Math.atan2(sinHalf, node.errorBuf.w);
            float s     = theta / sinHalf * rigidity; // facteur de mise à l'échelle
            node.springBuf.set(
                node.errorBuf.x * s,
                node.errorBuf.y * s,
                node.errorBuf.z * s);
        } else {
            node.springBuf.zero(); // quasi-aligné : aucune force
        }

        // Soustraction de l'amortissement
        node.springBuf.x -= node.angVel.x * damping;
        node.springBuf.y -= node.angVel.y * damping;
        node.springBuf.z -= node.angVel.z * damping;

        // Intégration de la vitesse angulaire (Euler explicite)
        node.angVel.x += node.springBuf.x * dt;
        node.angVel.y += node.springBuf.y * dt;
        node.angVel.z += node.springBuf.z * dt;

        // Intégration du quaternion : dq/dt = ½ · (0,wx,wy,wz) · q
        // Produit du quaternion pur Ω = (0,wx,wy,wz) par q = (qw,qx,qy,qz) :
        //   Ω·q = ( -wx·qx - wy·qy - wz·qz,
        //            wx·qw + wz·qy - wy·qz,
        //            wy·qw - wz·qx + wx·qz,
        //            wz·qw + wy·qx - wx·qy )
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

    /**
     * Envoie la position et la rotation courantes du nœud au {@link DisplayGroupData}.
     *
     * <p>Crée deux objets {@code Vec3} et {@code Quat4} par appel — inévitable
     * car les setters de {@code DisplayGroupData} l'exigent. Pour 8 segments à
     * 20 TPS et 50 joueurs simultanés, cela représente ~8 000 petits objets/s,
     * largement dans les capacités du GC.
     */
    private void applyNodeTransform(TailNode node) {
        Vector3f   p = node.worldPosBuf;
        Quaternionf r = node.currentRot;
        node.display.setTranslation(new Vec3(p.x, p.y, p.z));
        node.display.setRotation(new Quat4(new Quaternionf(r.x, r.y, r.z, r.w)));
        node.display.updateMetadata();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Utilitaires
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Remet tous les segments à leur pose repos, annule les vitesses angulaires
     * et remet le compteur de temps à zéro.
     * Utile lors d'une téléportation ou d'un changement de cosmétique.
     */
    public void reset() {
        time = 0f;
        posLastX = Float.NaN;
        smoothedLocalVel.zero();
        resetNode(root);
    }

    private void resetNode(TailNode node) {
        node.currentRot.set(node.restRotation);
        node.angVel.zero();
        node.globalRot.set(node.currentRot);
        node.worldPosBuf.set(node.restTranslation);
        applyNodeTransform(node);
        for (TailNode child : node.children) resetNode(child);
    }
}