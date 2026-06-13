package skwhy.pathfinder;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.bukkit.Location;
import org.bukkit.World;
import org.jspecify.annotations.Nullable;

/**
 * A* pathfinder that operates on {@link Node} graphs produced by a {@link NodeEvaluator}.
 *
 * <p>Adapted from net.minecraft.world.level.pathfinder.PathFinder:
 * - net.minecraft.core.BlockPos            → plain int coordinates / org.bukkit.Location
 * - net.minecraft.world.level.PathNavigationRegion → org.bukkit.World
 * - net.minecraft.world.entity.Mob         → org.bukkit.entity.Mob
 * - net.minecraft.util.profiling.*         → removed (no Bukkit equivalent; instrument
 *   externally with your own timing if needed)
 * - BooleanSupplier captureDebug kept as-is (pure Java)
 */
public class PathFinder {
    private static final float FUDGING = 1.5F;
    private final Node[] neighbors = new Node[32];
    private int maxVisitedNodes;
    private final NodeEvaluator nodeEvaluator;
    private final BinaryHeap openSet = new BinaryHeap();
    private java.util.function.BooleanSupplier captureDebug = () -> false;

    public PathFinder(final NodeEvaluator nodeEvaluator, final int maxVisitedNodes) {
        this.nodeEvaluator = nodeEvaluator;
        this.maxVisitedNodes = maxVisitedNodes;
    }

    public void setCaptureDebug(final java.util.function.BooleanSupplier captureDebug) {
        this.captureDebug = captureDebug;
    }

    public void setMaxVisitedNodes(final int maxVisitedNodes) {
        this.maxVisitedNodes = maxVisitedNodes;
    }

    /**
     * Finds a path from the entity's current position toward one of the given target locations.
     *
     * @param world                    the world the entity lives in
     * @param entity                   the mob that will follow the path
     * @param targets                  candidate destination locations
     * @param maxPathLength            maximum allowed walked distance
     * @param reachRange               Manhattan-distance at which a target is considered reached
     * @param maxVisitedNodesMultiplier scales the node-visit budget
     * @return the best path found, or {@code null} if none is reachable
     */
    public @Nullable Path findPath(
            final World world,
            final Navigation navigation,
            final Set<Location> targets,
            final float maxPathLength,
            final int reachRange,
            final float maxVisitedNodesMultiplier
    ) {
        this.openSet.clear();
        this.nodeEvaluator.prepare(world, navigation);
        Node from = this.nodeEvaluator.getStart();
        if (from == null) {
            return null;
        }

        // Map each Target node → the original Location so we can reconstruct the path
        Map<Target, Location> targetMap = targets.stream()
                .collect(Collectors.toMap(
                        loc -> this.nodeEvaluator.getTarget(loc.getX(), loc.getY(), loc.getZ()),
                        Function.identity()
                ));

        Path path = this.findPath(from, targetMap, maxPathLength, reachRange, maxVisitedNodesMultiplier);
        this.nodeEvaluator.done();
        return path;
    }

    private @Nullable Path findPath(
            final Node from,
            final Map<Target, Location> targetMap,
            final float maxPathLength,
            final int reachRange,
            final float maxVisitedNodesMultiplier
    ) {
        Set<Target> targets = targetMap.keySet();
        from.g = 0.0F;
        from.h = this.getBestH(from, targets);
        from.f = from.h;
        this.openSet.clear();
        this.openSet.insert(from);

        boolean captureDebug = this.captureDebug.getAsBoolean();
        Set<Node> closedSet = captureDebug ? new HashSet<>() : Set.of();
        int count = 0;
        Set<Target> reachedTargets = Sets.newHashSetWithExpectedSize(targets.size());
        int maxVisitedNodesAdjusted = (int) (this.maxVisitedNodes * maxVisitedNodesMultiplier);

        while (!this.openSet.isEmpty()) {
            if (++count >= maxVisitedNodesAdjusted) {
                break;
            }

            Node current = this.openSet.pop();
            current.closed = true;

            for (Target target : targets) {
                if (current.distanceManhattan(target) <= reachRange) {
                    target.setReached();
                    reachedTargets.add(target);
                }
            }

            if (!reachedTargets.isEmpty()) {
                break;
            }

            if (captureDebug) {
                closedSet.add(current);
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
                        neighbor.h = this.getBestH(neighbor, targets) * FUDGING;
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

        Optional<Path> optPath = !reachedTargets.isEmpty()
                ? reachedTargets.stream()
                        .map(target -> this.reconstructPath(target.getBestNode(), targetMap.get(target), true))
                        .min(Comparator.comparingInt(Path::getNodeCount))
                : targets.stream()
                        .map(target -> this.reconstructPath(target.getBestNode(), targetMap.get(target), false))
                        .min(Comparator.comparingDouble(Path::getDistToTarget)
                                .thenComparingInt(Path::getNodeCount));

        if (optPath.isEmpty()) {
            return null;
        }

        Path path = optPath.get();
        if (captureDebug) {
            path.setDebug(this.openSet.getHeap(), closedSet.toArray(Node[]::new), targets);
        }
        return path;
    }

    protected float distance(final Node from, final Node to) {
        return from.distanceTo(to);
    }

    private float getBestH(final Node from, final Set<Target> targets) {
        float bestH = Float.MAX_VALUE;
        for (Target target : targets) {
            float h = from.distanceTo(target);
            target.updateBest(h, from);
            bestH = Math.min(h, bestH);
        }
        return bestH;
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