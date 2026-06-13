package skwhy.pathfinder;

/**
 * Represents a pathfinding target node.
 *
 * <p>Adapted from net.minecraft.world.level.pathfinder.Target:
 * - FriendlyByteBuf serialization removed (Bukkit has no network buffer API).
 *   Implement your own serialization via a plugin messaging channel if needed.
 */
public class Target extends Node {
    private float bestHeuristic = Float.MAX_VALUE;
    private Node bestNode;
    private boolean reached;

    public Target(final Node node) {
        super(node.x, node.y, node.z);
    }

    public Target(final int x, final int y, final int z) {
        super(x, y, z);
    }

    public void updateBest(final float heuristic, final Node node) {
        if (heuristic < this.bestHeuristic) {
            this.bestHeuristic = heuristic;
            this.bestNode = node;
        }
    }

    public Node getBestNode() {
        return this.bestNode;
    }

    public void setReached() {
        this.reached = true;
    }

    public boolean isReached() {
        return this.reached;
    }
}