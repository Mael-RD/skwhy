package skwhy.pathfinder.pathcalculator;

import com.google.common.collect.Lists;

import skwhy.pathfinder.Mob;
import skwhy.pathfinder.Navigation.PathfindingType;

import org.bukkit.Location;
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * A* pathfinder that operates on {@link Node} graphs produced by a {@link NodeEvaluator}.
 *
 * <p>Simplified: computes a single path toward a single target location at construction time.
 */
public class PathFinder {
    private static final float FUDGING = 1.5F;

    private final Node[] neighbors = new Node[32];
    private NodeEvaluator nodeEvaluator;
    private final BinaryHeap openSet = new BinaryHeap();
    private @Nullable Path path;

    public PathFinder(final Mob mob, PathfindingType type) {
        setPathfindingType(type);
    }

    public void setPathfindingType(PathfindingType type) {
        this.nodeEvaluator = switch (type) {
            case WALK -> new WalkNodeEvaluator();
            case WALK_WATER -> new AmphibiousNodeEvaluator(false);
            case SWIM -> new SwimNodeEvaluator(false);
            case FLY, FLY_GROUND -> new FlyNodeEvaluator();
            case CLIMB -> new WalkNodeEvaluator();
            case NONE -> null;
        };
    }

    public @Nullable Path getPath() {
        return this.path;
    }

    public @Nullable Path findPath(Mob mob, Location target, float maxPathLength) {
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

        while (!this.openSet.isEmpty()) {
            if (++count >= maxVisitedNodes) {
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