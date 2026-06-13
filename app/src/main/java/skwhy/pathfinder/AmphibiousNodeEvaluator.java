package skwhy.pathfinder;

import org.bukkit.World;
import org.bukkit.entity.Mob;
import org.jspecify.annotations.Nullable;

/**
 * Node evaluator for amphibious entities (e.g. axolotls, frogs) that can
 * walk on land and swim in water.
 *
 * <p>Adapted from net.minecraft.world.level.pathfinder.AmphibiousNodeEvaluator:
 * - net.minecraft.world.entity.Mob              → org.bukkit.entity.Mob
 * - net.minecraft.world.level.PathNavigationRegion → org.bukkit.World
 * - net.minecraft.core.BlockPos / MutableBlockPos → plain int coordinates
 * - net.minecraft.core.Direction                → int offset arrays (dx/dy/dz)
 * - net.minecraft.util.Mth.floor()              → (int) Math.floor()
 * - entity.setPathfindingMalus / getPathfindingMalus → NodeEvaluator#setPathfindingMalus / #getPathfindingMalus
 * - mob.level().getSeaLevel()                   → World#getSeaLevel()
 * - mob.isInWater()                             → isEntityInWater() helper
 * - mob.maxUpStep()                             → Bukkit has no equivalent; getMaxUpStep()
 *   returns 1.0 by default — override in a subclass for mobs with different step heights.
 *
 * <p>Note: this class calls the following methods on WalkNodeEvaluator whose signatures
 * must use plain int coords once WalkNodeEvaluator is itself adapted:
 *   - getStartNode(int x, int y, int z)
 *   - findAcceptedNode(int x, int y, int z, int jumpSize, double nodeHeight,
 *                      int dirDx, int dirDy, int dirDz, PathType blockPathTypeCurrent)
 *   - getFloorLevel(int x, int y, int z)
 *   - isNeighborValid(Node, Node)
 */
public class AmphibiousNodeEvaluator extends WalkNodeEvaluator {

    private final boolean prefersShallowSwimming;
    private float oldWalkableCost;
    private float oldWaterBorderCost;

    public AmphibiousNodeEvaluator(final boolean prefersShallowSwimming) {
        this.prefersShallowSwimming = prefersShallowSwimming;
    }

    @Override
    public void prepare(final World world, final Navigation navigation) {
        super.prepare(world, navigation);
        // Override malus costs for this session using NodeEvaluator's per-entity map
        this.setPathfindingMalus(PathType.WATER, 0.0F);
        this.oldWalkableCost = this.getPathfindingMalus(PathType.WALKABLE);
        this.setPathfindingMalus(PathType.WALKABLE, 6.0F);
        this.oldWaterBorderCost = this.getPathfindingMalus(PathType.WATER_BORDER);
        this.setPathfindingMalus(PathType.WATER_BORDER, 4.0F);
    }

    @Override
    public void done() {
        this.setPathfindingMalus(PathType.WALKABLE, this.oldWalkableCost);
        this.setPathfindingMalus(PathType.WATER_BORDER, this.oldWaterBorderCost);
        super.done();
    }

    @Override
    public Node getStart() {
        if (!this.navigation.isInWater()) {
            return super.getStart();
        }
        
        return this.getStartNode(
                (int) Math.floor(navigation.getMinX()),
                (int) Math.floor((navigation.getMinY() + navigation.getMaxY()) / 2.0),
                (int) Math.floor(navigation.getMinZ())
        );
    }

    @Override
    public Target getTarget(final double x, final double y, final double z) {
        return this.getTargetNodeAt(x, y + 0.5, z);
    }

    @Override
    public int getNeighbors(final Node[] neighbors, final Node pos) {
        int numValidNeighbors = super.getNeighbors(neighbors, pos);

        PathType blockPathTypeAbove = this.getCachedPathType(pos.x, pos.y + 1, pos.z);
        PathType blockPathTypeCurrent = this.getCachedPathType(pos.x, pos.y, pos.z);

        int jumpSize;
        if (this.getPathfindingMalus(blockPathTypeAbove) >= 0.0F
                && blockPathTypeCurrent != PathType.STICKY_HONEY) {
            jumpSize = (int) Math.floor(Math.max(1.0F, getMaxUpStep()));
        } else {
            jumpSize = 0;
        }

        double posHeight = this.getFloorLevel(pos.x, pos.y, pos.z);

        // Vertical neighbours (up/down), only admitted if they are WATER cells
        // Direction UP  = (0, +1, 0)
        Node upNode = this.findAcceptedNode(
                pos.x, pos.y + 1, pos.z,
                Math.max(0, jumpSize - 1), posHeight,
                0, 1, 0,
                blockPathTypeCurrent);
        // Direction DOWN = (0, -1, 0)
        Node downNode = this.findAcceptedNode(
                pos.x, pos.y - 1, pos.z,
                jumpSize, posHeight,
                0, -1, 0,
                blockPathTypeCurrent);

        if (isVerticalNeighborValid(upNode, pos)) {
            neighbors[numValidNeighbors++] = upNode;
        }
        if (isVerticalNeighborValid(downNode, pos) && blockPathTypeCurrent != PathType.TRAPDOOR) {
            neighbors[numValidNeighbors++] = downNode;
        }

        // Shallow-swimming penalty: discourage nodes deep below the sea level
        if (this.prefersShallowSwimming) {
            int seaLevel = this.navigation.getLocation().getWorld().getSeaLevel();
            for (int i = 0; i < numValidNeighbors; i++) {
                Node neighbor = neighbors[i];
                if (neighbor.type == PathType.WATER && neighbor.y < seaLevel - 10) {
                    neighbor.costMalus++;
                }
            }
        }

        return numValidNeighbors;
    }

    private boolean isVerticalNeighborValid(final @Nullable Node verticalNode, final Node pos) {
        return this.isNeighborValid(verticalNode, pos) && verticalNode.type == PathType.WATER;
    }

    @Override
    protected boolean isAmphibious() {
        return true;
    }

    @Override
    public PathType getPathType(final PathfindingContext context, final int x, final int y, final int z) {
        PathType blockPathType = context.getPathTypeFromState(x, y, z);
        if (blockPathType == PathType.WATER) {
            // Any BLOCKED neighbour turns this into a WATER_BORDER
            int[][] directions = {
                { 1,  0,  0}, {-1,  0,  0},
                { 0,  1,  0}, { 0, -1,  0},
                { 0,  0,  1}, { 0,  0, -1}
            };
            for (int[] d : directions) {
                if (context.getPathTypeFromState(x + d[0], y + d[1], z + d[2]) == PathType.BLOCKED) {
                    return PathType.WATER_BORDER;
                }
            }
            return PathType.WATER;
        }
        return super.getPathType(context, x, y, z);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------


    /**
     * Step height for this entity.
     * Bukkit's {@link Mob} has no direct {@code maxUpStep()} equivalent.
     * Override this in a subclass if the mob has a non-standard step height.
     */
    protected float getMaxUpStep() {
        return 1.0F;
    }
}