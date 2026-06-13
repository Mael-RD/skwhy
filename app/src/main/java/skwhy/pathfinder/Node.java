package skwhy.pathfinder;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;
import org.jspecify.annotations.Nullable;

/**
 * Represents a single node in the pathfinding graph.
 *
 * <p>Adapted from net.minecraft.world.level.pathfinder.Node:
 * - BlockPos replaced by int coordinates
 * - Vec3 replaced by org.bukkit.util.Vector
 * - Custom Octile 3D heuristic reintegrated from PathNode
 */
public class Node {
    public final int x;
    public final int y;
    public final int z;
    private final int hash;
    
    public int heapIdx = -1;
    public float g;
    public float h;
    public float f;
    public @Nullable Node cameFrom;
    public boolean closed;
    
    public float walkedDistance;
    public float costMalus; // Remplace l'ancien "malus" de PathNode
    public PathType type = PathType.BLOCKED;

    public Node(final int x, final int y, final int z) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.hash = createHash(x, y, z);
    }

    // =========================================================================
    // L'HEURISTIQUE (Réintégrée de PathNode)
    // =========================================================================

    /**
     * Heuristique Octile 3D (identique à Minecraft Vanilla).
     * Essentielle pour l'algorithme A* avec des déplacements diagonaux.
     */
    public float heuristic(final Node target) {
        float dx = Math.abs(this.x - target.x);
        float dy = Math.abs(this.y - target.y);
        float dz = Math.abs(this.z - target.z);
        float max = Math.max(dx, Math.max(dy, dz));
        float min = Math.min(dx, Math.min(dy, dz));
        float mid = dx + dy + dz - max - min;
        return max + (float)(Math.sqrt(3) - 2) * min + (float)(Math.sqrt(2) - 1) * mid;
    }

    // =========================================================================
    // UTILITAIRES DE NOEUDS
    // =========================================================================

    public Node cloneAndMove(final int x, final int y, final int z) {
        Node node = new Node(x, y, z);
        node.heapIdx = this.heapIdx;
        node.g = this.g;
        node.h = this.h;
        node.f = this.f;
        node.cameFrom = this.cameFrom;
        node.closed = this.closed;
        node.walkedDistance = this.walkedDistance;
        node.costMalus = this.costMalus;
        node.type = this.type;
        return node;
    }

    public static int createHash(final int x, final int y, final int z) {
        return y & 0xFF | (x & 32767) << 8 | (z & 32767) << 24 | (x < 0 ? Integer.MIN_VALUE : 0) | (z < 0 ? 32768 : 0);
    }

    // =========================================================================
    // DISTANCES
    // =========================================================================

    public float distanceTo(final Node to) {
        float xd = to.x - this.x;
        float yd = to.y - this.y;
        float zd = to.z - this.z;
        return (float) Math.sqrt(xd * xd + yd * yd + zd * zd);
    }

    public float distanceToXZ(final Node to) {
        float xd = to.x - this.x;
        float zd = to.z - this.z;
        return (float) Math.sqrt(xd * xd + zd * zd);
    }

    /** Distance to a Bukkit Location (world is ignored). */
    public float distanceTo(final Location pos) {
        float xd = pos.getBlockX() - this.x;
        float yd = pos.getBlockY() - this.y;
        float zd = pos.getBlockZ() - this.z;
        return (float) Math.sqrt(xd * xd + yd * yd + zd * zd);
    }

    public float distanceToSqr(final Node to) {
        float xd = to.x - this.x;
        float yd = to.y - this.y;
        float zd = to.z - this.z;
        return xd * xd + yd * yd + zd * zd;
    }

    public float distanceToSqr(final Location pos) {
        float xd = pos.getBlockX() - this.x;
        float yd = pos.getBlockY() - this.y;
        float zd = pos.getBlockZ() - this.z;
        return xd * xd + yd * yd + zd * zd;
    }

    public float distanceManhattan(final Node to) {
        float xd = Math.abs(to.x - this.x);
        float yd = Math.abs(to.y - this.y);
        float zd = Math.abs(to.z - this.z);
        return xd + yd + zd;
    }

    public float distanceManhattan(final Location pos) {
        float xd = Math.abs(pos.getBlockX() - this.x);
        float yd = Math.abs(pos.getBlockY() - this.y);
        float zd = Math.abs(pos.getBlockZ() - this.z);
        return xd + yd + zd;
    }

    // =========================================================================
    // CONVERSIONS BUKKIT
    // =========================================================================

    /**
     * Returns this node as a Bukkit Location in the given world.
     * The world parameter is required because Bukkit Locations carry world references.
     */
    public Location asLocation(final World world) {
        return new Location(world, this.x, this.y, this.z);
    }

    /** Returns a direction-less Vector for this node's coordinates. */
    public Vector asVector() {
        return new Vector(this.x, this.y, this.z);
    }

    // =========================================================================
    // OVERRIDES HASH & EQUALS
    // =========================================================================

    @Override
    public boolean equals(final Object o) {
        if (!(o instanceof Node no)) return false;
        // La comparaison rapide par hash permet d'économiser beaucoup de CPU
        return this.hash == no.hash && this.x == no.x && this.y == no.y && this.z == no.z;
    }

    @Override
    public int hashCode() {
        return this.hash;
    }

    // =========================================================================
    // HELPERS D'ÉTAT
    // =========================================================================

    public boolean inOpenSet() {
        return this.heapIdx >= 0;
    }

    @Override
    public String toString() {
        return "Node{x=" + this.x + ", y=" + this.y + ", z=" + this.z + "}";
    }
}