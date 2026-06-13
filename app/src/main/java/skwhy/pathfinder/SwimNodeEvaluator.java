package skwhy.pathfinder;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Mob;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/**
 * Node evaluator for swimming entities.
 *
 * <p>Adapted from net.minecraft.world.level.pathfinder.SwimNodeEvaluator:
 * - net.minecraft.world.entity.Mob              → org.bukkit.entity.Mob
 * - net.minecraft.world.level.PathNavigationRegion → org.bukkit.World
 * - net.minecraft.core.BlockPos / MutableBlockPos → plain int coordinates
 * - net.minecraft.core.Direction / Direction.Plane → manual 6-directional / 4-directional loops
 * - net.minecraft.tags.FluidTags.WATER          → checked via Material / Waterlogged BlockData
 * - net.minecraft.util.Mth.floor()              → (int) Math.floor()
 * - BlockState / FluidState                     → org.bukkit.block.Block / BlockData
 * - PathNavigationRegion#getFluidState().isEmpty() → isWaterBlock() helper
 */
public class SwimNodeEvaluator extends NodeEvaluator {

    // Cardinal + vertical directions as (dx, dy, dz) offsets
    private static final int[][] DIRECTIONS = {
        { 1,  0,  0}, {-1,  0,  0},
        { 0,  1,  0}, { 0, -1,  0},
        { 0,  0,  1}, { 0,  0, -1}
    };
    // Horizontal only: N/S/E/W pairs for diagonal checks (indices into DIRECTIONS)
    private static final int[] HORIZONTAL_DIRS = {0, 1, 4, 5}; // +X, -X, +Z, -Z

    private final boolean allowBreaching;
    private final Long2ObjectMap<PathType> pathTypesByPosCache = new Long2ObjectOpenHashMap<>();

    public SwimNodeEvaluator(final boolean allowBreaching) {
        this.allowBreaching = allowBreaching;
    }

    @Override
    public void prepare(final World world, final Mob entity) {
        super.prepare(world, entity);
        this.pathTypesByPosCache.clear();
    }

    @Override
    public void done() {
        super.done();
        this.pathTypesByPosCache.clear();
    }

    @Override
    public Node getStart() {
        org.bukkit.util.BoundingBox bb = this.mob.getBoundingBox();
        return this.getNode(
                (int) Math.floor(bb.getMinX()),
                (int) Math.floor(bb.getMinY() + 0.5),
                (int) Math.floor(bb.getMinZ())
        );
    }

    @Override
    public Target getTarget(final double x, final double y, final double z) {
        return this.getTargetNodeAt(x, y, z);
    }

    @Override
    public int getNeighbors(final Node[] neighbors, final Node pos) {
        int count = 0;

        // Nodes for all 6 axis-aligned directions
        Node[] axisNodes = new Node[6];
        for (int i = 0; i < DIRECTIONS.length; i++) {
            int[] d = DIRECTIONS[i];
            Node node = this.findAcceptedNode(pos.x + d[0], pos.y + d[1], pos.z + d[2]);
            axisNodes[i] = node;
            if (isNodeValid(node)) {
                neighbors[count++] = node;
            }
        }

        // Horizontal diagonal nodes (only if both adjacent cardinal nodes are passable)
        // Pairs: (+X,+Z), (+X,-Z), (-X,+Z), (-X,-Z)
        int[][] diagonalPairs = {{0, 4}, {0, 5}, {1, 4}, {1, 5}};
        for (int[] pair : diagonalPairs) {
            Node a = axisNodes[pair[0]];
            Node b = axisNodes[pair[1]];
            if (hasMalus(a) && hasMalus(b)) {
                int[] da = DIRECTIONS[pair[0]];
                int[] db = DIRECTIONS[pair[1]];
                Node diag = this.findAcceptedNode(pos.x + da[0] + db[0], pos.y, pos.z + da[2] + db[2]);
                if (isNodeValid(diag)) {
                    neighbors[count++] = diag;
                }
            }
        }

        return count;
    }

    protected boolean isNodeValid(final @Nullable Node node) {
        return node != null && !node.closed;
    }

    private static boolean hasMalus(final @Nullable Node node) {
        return node != null && node.costMalus >= 0.0F;
    }

    protected @Nullable Node findAcceptedNode(final int x, final int y, final int z) {
        Node best = null;
        PathType pathType = this.getCachedBlockType(x, y, z);
        if ((this.allowBreaching && pathType == PathType.BREACH) || pathType == PathType.WATER) {
            float pathCost = this.getPathfindingMalus(pathType);
            if (pathCost >= 0.0F) {
                best = this.getNode(x, y, z);
                best.type = pathType;
                best.costMalus = Math.max(best.costMalus, pathCost);
                // Extra cost when the block itself holds no water (e.g. air in a BREACH cell)
                if (!isWaterBlock(this.currentContext.world(), x, y, z)) {
                    best.costMalus += 8.0F;
                }
            }
        }
        return best;
    }

    protected PathType getCachedBlockType(final int x, final int y, final int z) {
        return pathTypesByPosCache.computeIfAbsent(
                PathTypeCache.asLong(x, y, z),
                k -> this.getPathType(this.currentContext, x, y, z)
        );
    }

    @Override
    public PathType getPathType(final PathfindingContext context, final int x, final int y, final int z) {
        return this.getPathTypeOfMob(context, x, y, z, this.mob);
    }

    @Override
    public PathType getPathTypeOfMob(final PathfindingContext context, final int x, final int y, final int z, final Mob mob) {
        World world = context.world();

        for (int xx = x; xx < x + this.entityWidth; xx++) {
            for (int yy = y; yy < y + this.entityHeight; yy++) {
                for (int zz = z; zz < z + this.entityDepth; zz++) {
                    Block block = world.getBlockAt(xx, yy, zz);
                    Block below = world.getBlockAt(xx, yy - 1, zz);
                    boolean blockIsAir = block.getType().isAir();
                    boolean belowIsPathfindable = isWaterPathfindable(below);

                    // BREACH: the mob can surface here (air above water-pathfindable floor)
                    if (!isWaterBlock(world, xx, yy, zz) && belowIsPathfindable && blockIsAir) {
                        return PathType.BREACH;
                    }

                    if (!isWaterBlock(world, xx, yy, zz)) {
                        return PathType.BLOCKED;
                    }
                }
            }
        }

        Block last = world.getBlockAt(x, y, z);
        return isWaterPathfindable(last) ? PathType.WATER : PathType.BLOCKED;
    }

    // ------------------------------------------------------------------
    // Bukkit water helpers
    // ------------------------------------------------------------------

    /**
     * Returns true if the block at the given position counts as water for swimming pathing.
     * This covers source/flowing water and waterlogged blocks.
     */
    private static boolean isWaterBlock(final World world, final int x, final int y, final int z) {
        Block block = world.getBlockAt(x, y, z);
        Material mat = block.getType();
        if (mat == Material.WATER) {
            return true;
        }
        // Waterlogged blocks also contain water
        BlockData data = block.getBlockData();
        if (data instanceof Waterlogged wl) {
            return wl.isWaterlogged();
        }
        return false;
    }

    /**
     * Returns true if a swimming entity can pathfind through this block from below
     * (mirrors PathComputationType.WATER in NMS).
     */
    private static boolean isWaterPathfindable(final Block block) {
        Material mat = block.getType();
        // Water source/flowing is always passable for swimmers
        if (mat == Material.WATER) {
            return true;
        }
        // Waterlogged blocks let water through
        BlockData data = block.getBlockData();
        if (data instanceof Waterlogged wl && wl.isWaterlogged()) {
            return true;
        }
        // Air is passable but not a water block
        return mat.isAir();
    }
}