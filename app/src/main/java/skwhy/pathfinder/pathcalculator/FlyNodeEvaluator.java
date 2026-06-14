package skwhy.pathfinder.pathcalculator;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import skwhy.pathfinder.Mob;

import org.bukkit.Material;
import org.jspecify.annotations.Nullable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Node evaluator for flying entities (e.g. bees, phantoms, vexes).
 *
 * <p>Adapted from net.minecraft.world.level.pathfinder.FlyNodeEvaluator:
 * - net.minecraft.world.entity.Mob              → org.bukkit.entity.Mob
 * - net.minecraft.world.level.PathNavigationRegion → org.bukkit.World
 * - net.minecraft.core.BlockPos / MutableBlockPos → plain int coordinates
 * - net.minecraft.util.Mth.floor()              → (int) Math.floor()
 * - net.minecraft.world.level.block.Blocks.WATER → org.bukkit.Material.WATER
 * - net.minecraft.world.phys.AABB               → org.bukkit.util.BoundingBox
 * - mob.onPathfindingStart() / onPathfindingDone() → not exposed in Bukkit; omitted
 * - BlockPos.randomBetweenClosed()              → replaced with a bounded random helper
 * - context.mobPosition()                       → context.isMobPosition(x, y, z)
 * - context.level().getMinY()                   → World#getMinHeight()
 */
public class FlyNodeEvaluator extends WalkNodeEvaluator {

    private final Long2ObjectMap<PathType> pathTypeByPosCache = new Long2ObjectOpenHashMap<>();

    private static final float SMALL_MOB_SIZE = 1.0F;
    private static final float SMALL_MOB_INFLATED_START_NODE_BOUNDING_BOX = 1.1F;
    private static final int MAX_START_NODE_CANDIDATES = 10;

    @Override
    public void prepare(final Mob mob) {
        super.prepare(mob);
        this.pathTypeByPosCache.clear();
        // Note: NMS calls entity.onPathfindingStart() here.
        // Bukkit does not expose this hook; add it to your custom mob class if needed.
    }

    @Override
    public void done() {
        // Note: NMS calls mob.onPathfindingDone() here.
        // Bukkit does not expose this hook; add it to your custom mob class if needed.
        this.pathTypeByPosCache.clear();
        super.done();
    }

    @Override
    public Node getStart() {
        int startY;
        if (this.canFloat() && this.mob.isInWater()) {
            startY = this.mob.getLocation().getBlockY();
            // Rise until we leave the water surface
            while (this.currentContext.world().getBlockAt(
                    (int) Math.floor(this.mob.getLocation().getX()),
                    startY,
                    (int) Math.floor(this.mob.getLocation().getZ())
            ).getType() == Material.WATER) {
                startY++;
            }
        } else {
            startY = (int) Math.floor(this.mob.getLocation().getY() + 0.5);
        }

        int startX = (int) Math.floor(this.mob.getLocation().getX());
        int startZ = (int) Math.floor(this.mob.getLocation().getZ());

        if (!this.canStartAt(startX, startY, startZ)) {
            for (int[] candidate : iterateStartCandidates()) {
                if (this.canStartAt(candidate[0], candidate[1], candidate[2])) {
                    return super.getStartNode(candidate[0], candidate[1], candidate[2]);
                }
            }
        }

        return super.getStartNode(startX, startY, startZ);
    }

    @Override
    protected boolean canStartAt(final int x, final int y, final int z) {
        PathType blockPathType = this.getCachedPathType(x, y, z);
        return this.getPathfindingMalus(blockPathType) >= 0.0F;
    }

    @Override
    public Target getTarget(final double x, final double y, final double z) {
        return this.getTargetNodeAt(x, y, z);
    }

    @Override
    public int getNeighbors(final Node[] neighbors, final Node pos) {
        int count = 0;

        // Axis-aligned neighbours
        Node south = this.findAcceptedNode(pos.x,     pos.y,     pos.z + 1);
        Node west  = this.findAcceptedNode(pos.x - 1, pos.y,     pos.z);
        Node east  = this.findAcceptedNode(pos.x + 1, pos.y,     pos.z);
        Node north = this.findAcceptedNode(pos.x,     pos.y,     pos.z - 1);
        Node up    = this.findAcceptedNode(pos.x,     pos.y + 1, pos.z);
        Node down  = this.findAcceptedNode(pos.x,     pos.y - 1, pos.z);

        if (isOpen(south)) neighbors[count++] = south;
        if (isOpen(west))  neighbors[count++] = west;
        if (isOpen(east))  neighbors[count++] = east;
        if (isOpen(north)) neighbors[count++] = north;
        if (isOpen(up))    neighbors[count++] = up;
        if (isOpen(down))  neighbors[count++] = down;

        // Vertical diagonals
        Node southUp = this.findAcceptedNode(pos.x,     pos.y + 1, pos.z + 1);
        if (isOpen(southUp) && hasMalus(south) && hasMalus(up))   neighbors[count++] = southUp;

        Node westUp  = this.findAcceptedNode(pos.x - 1, pos.y + 1, pos.z);
        if (isOpen(westUp) && hasMalus(west) && hasMalus(up))     neighbors[count++] = westUp;

        Node eastUp  = this.findAcceptedNode(pos.x + 1, pos.y + 1, pos.z);
        if (isOpen(eastUp) && hasMalus(east) && hasMalus(up))     neighbors[count++] = eastUp;

        Node northUp = this.findAcceptedNode(pos.x,     pos.y + 1, pos.z - 1);
        if (isOpen(northUp) && hasMalus(north) && hasMalus(up))   neighbors[count++] = northUp;

        Node southDown = this.findAcceptedNode(pos.x,     pos.y - 1, pos.z + 1);
        if (isOpen(southDown) && hasMalus(south) && hasMalus(down)) neighbors[count++] = southDown;

        Node westDown  = this.findAcceptedNode(pos.x - 1, pos.y - 1, pos.z);
        if (isOpen(westDown) && hasMalus(west) && hasMalus(down))   neighbors[count++] = westDown;

        Node eastDown  = this.findAcceptedNode(pos.x + 1, pos.y - 1, pos.z);
        if (isOpen(eastDown) && hasMalus(east) && hasMalus(down))   neighbors[count++] = eastDown;

        Node northDown = this.findAcceptedNode(pos.x,     pos.y - 1, pos.z - 1);
        if (isOpen(northDown) && hasMalus(north) && hasMalus(down)) neighbors[count++] = northDown;

        // Horizontal diagonals
        Node northEast = this.findAcceptedNode(pos.x + 1, pos.y, pos.z - 1);
        if (isOpen(northEast) && hasMalus(north) && hasMalus(east)) neighbors[count++] = northEast;

        Node southEast = this.findAcceptedNode(pos.x + 1, pos.y, pos.z + 1);
        if (isOpen(southEast) && hasMalus(south) && hasMalus(east)) neighbors[count++] = southEast;

        Node northWest = this.findAcceptedNode(pos.x - 1, pos.y, pos.z - 1);
        if (isOpen(northWest) && hasMalus(north) && hasMalus(west)) neighbors[count++] = northWest;

        Node southWest = this.findAcceptedNode(pos.x - 1, pos.y, pos.z + 1);
        if (isOpen(southWest) && hasMalus(south) && hasMalus(west)) neighbors[count++] = southWest;

        // Full 3-D corners (up)
        Node northEastUp = this.findAcceptedNode(pos.x + 1, pos.y + 1, pos.z - 1);
        if (isOpen(northEastUp) && hasMalus(northEast) && hasMalus(north) && hasMalus(east)
                && hasMalus(up) && hasMalus(northUp) && hasMalus(eastUp)) {
            neighbors[count++] = northEastUp;
        }

        Node southEastUp = this.findAcceptedNode(pos.x + 1, pos.y + 1, pos.z + 1);
        if (isOpen(southEastUp) && hasMalus(southEast) && hasMalus(south) && hasMalus(east)
                && hasMalus(up) && hasMalus(southUp) && hasMalus(eastUp)) {
            neighbors[count++] = southEastUp;
        }

        Node northWestUp = this.findAcceptedNode(pos.x - 1, pos.y + 1, pos.z - 1);
        if (isOpen(northWestUp) && hasMalus(northWest) && hasMalus(north) && hasMalus(west)
                && hasMalus(up) && hasMalus(northUp) && hasMalus(westUp)) {
            neighbors[count++] = northWestUp;
        }

        Node southWestUp = this.findAcceptedNode(pos.x - 1, pos.y + 1, pos.z + 1);
        if (isOpen(southWestUp) && hasMalus(southWest) && hasMalus(south) && hasMalus(west)
                && hasMalus(up) && hasMalus(southUp) && hasMalus(westUp)) {
            neighbors[count++] = southWestUp;
        }

        // Full 3-D corners (down)
        Node northEastDown = this.findAcceptedNode(pos.x + 1, pos.y - 1, pos.z - 1);
        if (isOpen(northEastDown) && hasMalus(northEast) && hasMalus(north) && hasMalus(east)
                && hasMalus(down) && hasMalus(northDown) && hasMalus(eastDown)) {
            neighbors[count++] = northEastDown;
        }

        Node southEastDown = this.findAcceptedNode(pos.x + 1, pos.y - 1, pos.z + 1);
        if (isOpen(southEastDown) && hasMalus(southEast) && hasMalus(south) && hasMalus(east)
                && hasMalus(down) && hasMalus(southDown) && hasMalus(eastDown)) {
            neighbors[count++] = southEastDown;
        }

        Node northWestDown = this.findAcceptedNode(pos.x - 1, pos.y - 1, pos.z - 1);
        if (isOpen(northWestDown) && hasMalus(northWest) && hasMalus(north) && hasMalus(west)
                && hasMalus(down) && hasMalus(northDown) && hasMalus(westDown)) {
            neighbors[count++] = northWestDown;
        }

        Node southWestDown = this.findAcceptedNode(pos.x - 1, pos.y - 1, pos.z + 1);
        if (isOpen(southWestDown) && hasMalus(southWest) && hasMalus(south) && hasMalus(west)
                && hasMalus(down) && hasMalus(southDown) && hasMalus(westDown)) {
            neighbors[count++] = southWestDown;
        }

        return count;
    }

    private boolean hasMalus(final @Nullable Node node) {
        return node != null && node.costMalus >= 0.0F;
    }

    private boolean isOpen(final @Nullable Node node) {
        return node != null && !node.closed;
    }

    @Override
    protected @Nullable Node findAcceptedNode(final int x, final int y, final int z) {
        PathType pathType = this.getCachedPathType(x, y, z);
        float pathCost = this.getPathfindingMalus(pathType);
        if (pathCost < 0.0F) {
            return null;
        }
        Node best = this.getNode(x, y, z);
        best.type = pathType;
        best.costMalus = Math.max(best.costMalus, pathCost);
        if (pathType == PathType.WALKABLE) {
            best.costMalus++;
        }
        return best;
    }

    @Override
    protected PathType getCachedPathType(final int x, final int y, final int z) {
        return pathTypeByPosCache.computeIfAbsent(
            PathTypeCache.asLong(x, y, z),
            key -> this.getPathTypeOfMob(this.currentContext, x, y, z, this.mob)
        );
    }

    @Override
    public PathType getPathType(final PathfindingContext context, final int x, final int y, final int z) {
        PathType blockPathType = context.getPathTypeFromState(x, y, z);

        int minY = context.world().getMinHeight();
        if (blockPathType == PathType.OPEN && y >= minY + 1) {
            PathType belowType = context.getPathTypeFromState(x, y - 1, z);

            if (belowType == PathType.FIRE || belowType == PathType.LAVA) {
                blockPathType = PathType.FIRE;
            } else if (belowType == PathType.DAMAGING) {
                blockPathType = PathType.DAMAGING;
            } else if (belowType == PathType.COCOA) {
                blockPathType = PathType.COCOA;
            } else if (belowType == PathType.FENCE) {
                // Don't treat the mob's own standing position as a fence barrier
                if (!context.isMobPosition(x, y - 1, z)) {
                    blockPathType = PathType.FENCE;
                }
            } else if (belowType != PathType.WALKABLE
                    && belowType != PathType.OPEN
                    && belowType != PathType.WATER) {
                blockPathType = PathType.WALKABLE;
            }
            // else: belowType is WALKABLE / OPEN / WATER → keep OPEN
        }

        if (blockPathType == PathType.WALKABLE || blockPathType == PathType.OPEN) {
            blockPathType = checkNeighbourBlocks(context, x, y, z, blockPathType);
        }

        return blockPathType;
    }

    // ------------------------------------------------------------------
    // Start-node candidate helpers
    // ------------------------------------------------------------------

    /**
     * Returns a list of candidate block positions to try when the mob's feet position
     * is not a valid start node.
     *
     * <p>Mirrors NMS {@code iteratePathfindingStartNodeCandidatePositions}: corners of the
     * bounding box for large mobs, or up to {@value MAX_START_NODE_CANDIDATES} random
     * positions within a slightly inflated bounding box for small mobs.
     */
    private List<int[]> iterateStartCandidates() {
        Vector hitbox = this.mob.getHitbox();
        boolean isSmall = hitbox.getX()*hitbox.getY()*hitbox.getZ() < SMALL_MOB_SIZE;
        List<int[]> candidates = new ArrayList<>();

        if (!isSmall) {
            int y = this.mob.getLocation().getBlockY();
            candidates.add(new int[]{(int) Math.floor(this.mob.getMinX()), y, (int) Math.floor(this.mob.getMinZ())});
            candidates.add(new int[]{(int) Math.floor(this.mob.getMinX()), y, (int) Math.floor(this.mob.getMaxZ())});
            candidates.add(new int[]{(int) Math.floor(this.mob.getMaxX()), y, (int) Math.floor(this.mob.getMinZ())});
            candidates.add(new int[]{(int) Math.floor(this.mob.getMaxX()), y, (int) Math.floor(this.mob.getMaxZ())});
        } else {
            double xPad = Math.max(0.0, SMALL_MOB_INFLATED_START_NODE_BOUNDING_BOX - hitbox.getX());
            double yPad = Math.max(0.0, SMALL_MOB_INFLATED_START_NODE_BOUNDING_BOX - hitbox.getY());
            double zPad = Math.max(0.0, SMALL_MOB_INFLATED_START_NODE_BOUNDING_BOX - hitbox.getZ());

            int minX = (int) Math.floor(this.mob.getMinX() - xPad);
            int minY = (int) Math.floor(this.mob.getMinY() - yPad);
            int minZ = (int) Math.floor(this.mob.getMinZ() - zPad);
            int maxX = (int) Math.floor(this.mob.getMaxX() + xPad);
            int maxY = (int) Math.floor(this.mob.getMaxY() + yPad);
            int maxZ = (int) Math.floor(this.mob.getMaxZ() + zPad);

            ThreadLocalRandom rng = ThreadLocalRandom.current();
            for (int i = 0; i < MAX_START_NODE_CANDIDATES; i++) {
                int rx = (minX == maxX) ? minX : rng.nextInt(minX, maxX + 1);
                int ry = (minY == maxY) ? minY : rng.nextInt(minY, maxY + 1);
                int rz = (minZ == maxZ) ? minZ : rng.nextInt(minZ, maxZ + 1);
                candidates.add(new int[]{rx, ry, rz});
            }
        }

        return candidates;
    }
}