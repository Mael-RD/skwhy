package skwhy.chunkpath.move.pathcalculator;

import com.google.common.collect.Lists;

import skwhy.chunkpath.block.PathCostRules;
import skwhy.chunkpath.move.LongMob;

import org.bukkit.Location;
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * A* pathfinder that operates on {@link Node} graphs produced by a {@link NodeEvaluator}.
 *
 * <p>Simplified: computes a single path toward a single target location at construction time.
 *
 * <p>Ground-only, cost-rule aware copy of {@code skwhy.pathfinder.pathcalculator.PathFinder}: the
 * node evaluator is always a {@link CostRuleWalkNodeEvaluator} weighted by the world's
 * {@link PathCostRules}, and the search additionally aborts once a wall-clock deadline is hit (on
 * top of the existing node-count cutoff) so a single call never risks exceeding the ~10ms/execution
 * budget this system is called under, however far the target waypoint is.
 */
public class LongPathFinder {
    private static final float FUDGING = 1.5F;
    /** Wall-clock budget per {@link #findPath} call. Checked every {@link #DEADLINE_CHECK_INTERVAL} nodes. */
    private static final long DEADLINE_NANOS = 8_000_000L; // 8ms, leaves headroom under the 10ms budget
    private static final int DEADLINE_CHECK_INTERVAL = 32;

    private final Node[] neighbors = new Node[32];
    private final NodeEvaluator nodeEvaluator;
    private final BinaryHeap openSet = new BinaryHeap();
    private @Nullable Path path;

    public LongPathFinder(final LongMob mob, final PathCostRules costRules) {
        this.nodeEvaluator = new CostRuleWalkNodeEvaluator(costRules);
    }

    public @Nullable Path getPath() {
        return this.path;
    }

    public @Nullable Path findPath(LongMob mob, Location target, float maxPathLength) {
        this.nodeEvaluator.prepare(mob);
        Node from = this.nodeEvaluator.getStart();
        if (from == null) {
            this.path = null;
        } else {
            Target targetNode = this.nodeEvaluator.getTarget(target.getX(), target.getY(), target.getZ());
            this.path = this.findPath(from, targetNode, target, maxPathLength);
        }
        this.nodeEvaluator.done();
        return path;
    }

    private @Nullable Path findPath(
            final Node from,
            final Target target,
            final Location targetLocation,
            final float maxPathLength
    ) {
        from.g = 0.0F;
        from.h = from.distanceTo(target);
        target.updateBest(from.h, from);
        from.f = from.h;
        this.openSet.clear();
        this.openSet.insert(from);

        boolean reached = false;
        int count = 0;
        int maxVisitedNodes = (int) (maxPathLength * 16.0F);
        long deadline = System.nanoTime() + DEADLINE_NANOS;

        while (!this.openSet.isEmpty()) {
            if (++count >= maxVisitedNodes) {
                break;
            }
            if (count % DEADLINE_CHECK_INTERVAL == 0 && System.nanoTime() >= deadline) {
                break;
            }

            Node current = this.openSet.pop();
            current.closed = true;

            if (current.distanceManhattan(target) == 0) {
                target.setReached();
                reached = true;
                break;
            }

            if (!(current.distanceTo(from) >= maxPathLength)) {
                int neighborCount = this.nodeEvaluator.getNeighbors(this.neighbors, current);

                for (int i = 0; i < neighborCount; i++) {
                    Node neighbor = this.neighbors[i];
                    float distance = this.distance(current, neighbor);
                    neighbor.walkedDistance = current.walkedDistance + distance;
                    float tentativeGScore = current.g + distance + neighbor.costMalus;
                    if (neighbor.walkedDistance < maxPathLength && (!neighbor.inOpenSet() || tentativeGScore < neighbor.g)) {
                        neighbor.cameFrom = current;
                        neighbor.g = tentativeGScore;
                        float h = neighbor.distanceTo(target);
                        neighbor.h = h * FUDGING;
                        target.updateBest(h, neighbor);
                        if (neighbor.inOpenSet()) {
                            this.openSet.changeCost(neighbor, neighbor.g + neighbor.h);
                        } else {
                            neighbor.f = neighbor.g + neighbor.h;
                            this.openSet.insert(neighbor);
                        }
                    }
                }
            }
        }

        if (target.getBestNode() == null) {
            return null;
        }

        return this.reconstructPath(target.getBestNode(), targetLocation, reached);
    }

    protected float distance(final Node from, final Node to) {
        return from.distanceTo(to);
    }

    private Path reconstructPath(final Node closest, final Location target, final boolean reached) {
        List<Node> nodes = Lists.newArrayList();
        Node node = closest;
        nodes.add(0, closest);

        while (node.cameFrom != null) {
            node = node.cameFrom;
            nodes.add(0, node);
        }

        return new Path(
                nodes,
                target.getBlockX(),
                target.getBlockY(),
                target.getBlockZ(),
                reached
        );
    }
}