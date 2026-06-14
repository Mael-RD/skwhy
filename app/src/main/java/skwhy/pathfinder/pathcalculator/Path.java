package skwhy.pathfinder.pathcalculator;

import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.jspecify.annotations.Nullable;

import skwhy.pathfinder.Mob;

import java.util.List;
import java.util.Set;

/**
 * A computed path as an ordered list of Nodes from start to (near) target.
 *
 * <p>Adapted from net.minecraft.world.level.pathfinder.Path:
 * - BlockPos replaced by int triplets / Bukkit Location
 * - Vec3 replaced by Bukkit Location
 * - Entity.getBbWidth() replaced by BoundingBox.getWidthX()
 * - FriendlyByteBuf / StreamCodec serialization removed
 *   (Bukkit exposes no network buffer API; use a custom packet if needed)
 * - VisibleForDebug annotation removed (not available outside Minecraft internals)
 */
public final class Path {
    private final List<Node> nodes;
    private @Nullable DebugData debugData;
    private int nextNodeIndex;
    /** Target block position (int coordinates). */
    private final int targetX;
    private final int targetY;
    private final int targetZ;
    private final float distToTarget;
    private final boolean reached;

    public Path(final List<Node> nodes, final int targetX, final int targetY, final int targetZ, final boolean reached) {
        this.nodes = nodes;
        this.targetX = targetX;
        this.targetY = targetY;
        this.targetZ = targetZ;
        this.distToTarget = nodes.isEmpty()
                ? Float.MAX_VALUE
                : nodes.get(nodes.size() - 1).distanceManhattan(new Location(null, targetX, targetY, targetZ));
        this.reached = reached;
    }

    public void advance() {
        this.nextNodeIndex++;
    }

    public boolean notStarted() {
        return this.nextNodeIndex <= 0;
    }

    public boolean isDone() {
        return this.nextNodeIndex >= this.nodes.size();
    }

    public @Nullable Node getEndNode() {
        return !this.nodes.isEmpty() ? this.nodes.get(this.nodes.size() - 1) : null;
    }

    public Node getNode(final int i) {
        return this.nodes.get(i);
    }

    public void truncateNodes(final int index) {
        if (this.nodes.size() > index) {
            this.nodes.subList(index, this.nodes.size()).clear();
        }
    }

    public void replaceNode(final int index, final Node replaceWith) {
        this.nodes.set(index, replaceWith);
    }

    public int getNodeCount() {
        return this.nodes.size();
    }

    public int getNextNodeIndex() {
        return this.nextNodeIndex;
    }

    public void setNextNodeIndex(final int nextNodeIndex) {
        this.nextNodeIndex = nextNodeIndex;
    }

    /**
     * Returns the world position the entity should move toward for the given path node index.
     * The x/z are offset by half the entity's bounding-box width to centre the entity on the block.
     *
     * @param world  the world the entity lives in (required by Bukkit Location)
     * @param entity the entity following the path
     * @param index  node index
     */
    public Vector getEntityPosAtNode(final Mob mob, final int index) {
        Node node = this.nodes.get(index);
        double halfWidth = (int) (mob.getHitbox().getX() + 1.0) * 0.5;
        double x = node.x + halfWidth;
        double y = node.y;
        double z = node.z + halfWidth;
        return new Vector(x, y, z);
    }

    public Vector getNodePos(final int index) {
        Node n = this.nodes.get(index);
        return new Vector(n.x, n.y, n.z);
    }

    public Vector getNextEntityPos(final Mob mob) {
        return getEntityPosAtNode(mob, this.nextNodeIndex);
    }

    public Vector getNextNodePos() {
        return getNodePos(this.nextNodeIndex);
    }

    public Node getNextNode() {
        return this.nodes.get(this.nextNodeIndex);
    }

    public @Nullable Node getPreviousNode() {
        return this.nextNodeIndex > 0 ? this.nodes.get(this.nextNodeIndex - 1) : null;
    }

    public boolean sameAs(final @Nullable Path path) {
        return path != null && this.nodes.equals(path.nodes);
    }

    @Override
    public boolean equals(final Object obj) {
        if (!(obj instanceof Path path)) return false;
        return this.nextNodeIndex == path.nextNodeIndex
                && this.reached == path.reached
                && this.targetX == path.targetX
                && this.targetY == path.targetY
                && this.targetZ == path.targetZ
                && this.nodes.equals(path.nodes);
    }

    @Override
    public int hashCode() {
        return this.nextNodeIndex + this.nodes.hashCode() * 31;
    }

    public boolean canReach() {
        return this.reached;
    }

    void setDebug(final Node[] openSet, final Node[] closedSet, final Set<Target> targets) {
        this.debugData = new DebugData(openSet, closedSet, targets);
    }

    public @Nullable DebugData debugData() {
        return this.debugData;
    }

    public Vector getTarget() { return new Vector(targetX, targetY, targetZ); }
    public int getTargetX() { return this.targetX; }
    public int getTargetY() { return this.targetY; }
    public int getTargetZ() { return this.targetZ; }

    public float getDistToTarget() {
        return this.distToTarget;
    }

    public Path copy() {
        Path result = new Path(this.nodes, this.targetX, this.targetY, this.targetZ, this.reached);
        result.debugData = this.debugData;
        result.nextNodeIndex = this.nextNodeIndex;
        return result;
    }

    public record DebugData(Node[] openSet, Node[] closedSet, Set<Target> targetNodes) {}

    // ════════════════════════════════════════════════════════════════════════
    // ░░ DEBUG / AFFICHAGE ░░
    // ════════════════════════════════════════════════════════════════════════

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        
        sb.append("┌────────────────────────────────────────────\n");
        sb.append("│ PATH INFO (Chemin A*)\n");
        sb.append("├────────────────────────────────────────────\n");
        
        // --- Cible & Statut ---
        sb.append("│ [Objectif]\n");
        sb.append(String.format("│ Cible exacte    : X:%d | Y:%d | Z:%d\n", targetX, targetY, targetZ));
        sb.append("│ Est atteignable : ").append(reached ? "Oui (Chemin complet)" : "Non (Chemin partiel)").append("\n");
        if (distToTarget == Float.MAX_VALUE) {
            sb.append("│ Distance finale : Inconnue (Pas de noeuds)\n");
        } else {
            sb.append(String.format("│ Distance finale : %.2f blocs\n", distToTarget));
        }

        // --- Progression ---
        sb.append("│\n│ [Progression]\n");
        sb.append("│ Longueur totale : ").append(nodes.size()).append(" noeuds\n");
        sb.append("│ Index actuel    : ").append(nextNodeIndex).append(" / ").append(nodes.size());
        
        if (nodes.isEmpty()) {
            sb.append(" (Chemin Vide)\n");
        } else if (isDone()) {
            sb.append(" (Terminé)\n");
        } else {
            sb.append(" (En cours)\n");
            Node next = getNextNode();
            sb.append(String.format("│ Prochain noeud  : X:%d | Y:%d | Z:%d\n", next.x, next.y, next.z));
        }

        // --- Données de Debug (Si activées) ---
        if (debugData != null) {
            sb.append("│\n│ [Debug Data]\n");
            sb.append("│ OpenSet évalués : ").append(debugData.openSet().length).append("\n");
            sb.append("│ ClosedSet total : ").append(debugData.closedSet().length).append("\n");
        }
        
        sb.append("└────────────────────────────────────────────");
        
        return sb.toString();
    }
}