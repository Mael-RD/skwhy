package skwhy.pathfinder;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Openable;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.Gate;
import org.bukkit.block.data.type.TrapDoor;
import org.bukkit.entity.Mob;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;
import java.util.Set;

/**
 * Node evaluator for land-walking entities.
 *
 * <p>Adapted from net.minecraft.world.level.pathfinder.WalkNodeEvaluator:
 * - net.minecraft.world.entity.Mob              → org.bukkit.entity.Mob
 * - net.minecraft.world.level.PathNavigationRegion/BlockGetter → org.bukkit.World
 * - net.minecraft.core.BlockPos / MutableBlockPos → plain int coordinates
 * - net.minecraft.core.Direction / Direction.Plane → inner enum HDir (4 horizontal dirs)
 * - net.minecraft.util.Mth                      → standard Java math
 * - net.minecraft.world.phys.AABB               → org.bukkit.util.BoundingBox
 * - net.minecraft.world.phys.Vec3               → org.bukkit.util.Vector
 * - net.minecraft.world.phys.shapes.VoxelShape  → Block#getBoundingBox() (Y-max only)
 * - BlockTags / Blocks                          → org.bukkit.Tag / org.bukkit.Material
 * - BlockState / FluidState                     → org.bukkit.block.Block / BlockData
 * - mob.onPathfindingStart/Done()               → not exposed by Bukkit; omitted
 * - mob.canStandOnFluid()                       → not exposed; approximated via water check
 * - mob.onGround()                              → mob.isOnGround()
 * - mob.getBbWidth() / getBbHeight()            → BoundingBox.getWidthX() / getHeight()
 * - mob.maxUpStep()                             → getMaxUpStep() returning 1.0F (override as needed)
 * - mob.getMaxFallDistance()                    → getMaxFallDistance() returning 3 (override as needed)
 * - context.level().noCollision()               → hasCollisions() using per-block bounding boxes
 * - context.level().getMinY()                   → World#getMinHeight()
 * - DoorBlock / FenceGateBlock / BaseRailBlock / LeavesBlock → Material / Tag / BlockData checks
 */
public class WalkNodeEvaluator extends NodeEvaluator {

    public static final double SPACE_BETWEEN_WALL_POSTS = 0.5;
    private static final double DEFAULT_MOB_JUMP_HEIGHT = 1.125;

    // 4 horizontal directions as (dx, dz, 2D-index) – mirrors Direction.Plane.HORIZONTAL
    protected enum HDir {
        SOUTH ( 0,  1, 0),
        WEST  (-1,  0, 1),
        NORTH ( 0, -1, 2),
        EAST  ( 1,  0, 3);

        public final int dx, dz, idx2d;

        HDir(int dx, int dz, int idx2d) {
            this.dx    = dx;
            this.dz    = dz;
            this.idx2d = idx2d;
        }

        public HDir clockWise() {
            return switch (this) {
                case SOUTH -> WEST;
                case WEST  -> NORTH;
                case NORTH -> EAST;
                case EAST  -> SOUTH;
            };
        }

        public static final HDir[] VALUES = values();
    }

    private final Long2ObjectMap<PathType> pathTypesByPosCacheByMob = new Long2ObjectOpenHashMap<>();
    // Key: BoundingBox encoded as a long-pair; we use the BoundingBox itself as key (equals/hashCode work)
    private final Object2BooleanMap<BoundingBox> collisionCache = new Object2BooleanOpenHashMap<>();
    private final Node[] reusableNeighbors = new Node[HDir.VALUES.length];

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    @Override
    public void prepare(final World world, final Mob entity) {
        super.prepare(world, entity);
        // NMS calls entity.onPathfindingStart() here – not available in Bukkit
    }

    @Override
    public void done() {
        // NMS calls mob.onPathfindingDone() here – not available in Bukkit
        this.pathTypesByPosCacheByMob.clear();
        this.collisionCache.clear();
        super.done();
    }

    // ------------------------------------------------------------------
    // Start node
    // ------------------------------------------------------------------

    @Override
    public Node getStart() {
        int startY = this.mob.getLocation().getBlockY();
        double mobX = this.mob.getLocation().getX();
        double mobZ = this.mob.getLocation().getZ();
        World world = this.currentContext.world();

        Block startBlock = world.getBlockAt((int) Math.floor(mobX), startY, (int) Math.floor(mobZ));

        // canStandOnFluid: Bukkit mobs generally can't walk on fluids unless specially tagged.
        // We approximate: if the current block is a fluid (water/lava) surface, handle it.
        boolean standingOnFluid = isSurfaceFluid(startBlock);

        if (!standingOnFluid) {
            if (this.canFloat() && isInWater(this.mob)) {
                // Float up to find the water surface
                while (true) {
                    Block b = world.getBlockAt((int) Math.floor(mobX), startY, (int) Math.floor(mobZ));
                    if (b.getType() != Material.WATER) {
                        startY--;
                        break;
                    }
                    // Check for flowing vs source – in Bukkit we simply check Material.WATER
                    // and stop when we leave water
                    Block above = world.getBlockAt((int) Math.floor(mobX), startY + 1, (int) Math.floor(mobZ));
                    if (above.getType() != Material.WATER) {
                        break;
                    }
                    startY++;
                }
            } else if (this.mob.isOnGround()) {
                startY = (int) Math.floor(this.mob.getLocation().getY() + 0.5);
            } else {
                // Scan downward to find a solid landing point
                int scanY = (int) Math.floor(this.mob.getLocation().getY() + 1.0);
                int minY = world.getMinHeight();
                while (scanY > minY) {
                    startY = scanY;
                    scanY--;
                    Block belowBlock = world.getBlockAt((int) Math.floor(mobX), scanY, (int) Math.floor(mobZ));
                    if (!belowBlock.getType().isAir() && !isLandPathfindable(belowBlock)) {
                        break;
                    }
                }
            }
        } else {
            // Standing on fluid surface: rise until no longer on a fluid
            while (isSurfaceFluid(world.getBlockAt((int) Math.floor(mobX), startY, (int) Math.floor(mobZ)))) {
                startY++;
            }
            startY--;
        }

        int startX = (int) Math.floor(mobX);
        int startZ = (int) Math.floor(mobZ);

        if (!this.canStartAt(startX, startY, startZ)) {
            BoundingBox mobBB = this.mob.getBoundingBox();
            if (this.canStartAt((int) Math.floor(mobBB.getMinX()), startY, (int) Math.floor(mobBB.getMinZ()))
                    || this.canStartAt((int) Math.floor(mobBB.getMinX()), startY, (int) Math.floor(mobBB.getMaxZ()))
                    || this.canStartAt((int) Math.floor(mobBB.getMaxX()), startY, (int) Math.floor(mobBB.getMinZ()))
                    || this.canStartAt((int) Math.floor(mobBB.getMaxX()), startY, (int) Math.floor(mobBB.getMaxZ()))) {
                return this.getStartNode(
                        (int) Math.floor(mobBB.getMinX()), startY, (int) Math.floor(mobBB.getMinZ()));
            }
        }

        return this.getStartNode(startX, startY, startZ);
    }

    protected Node getStartNode(final int x, final int y, final int z) {
        Node node = this.getNode(x, y, z);
        node.type = this.getCachedPathType(x, y, z);
        node.costMalus = this.getPathfindingMalus(node.type);
        return node;
    }

    protected boolean canStartAt(final int x, final int y, final int z) {
        PathType blockPathType = this.getCachedPathType(x, y, z);
        return blockPathType != PathType.OPEN && this.getPathfindingMalus(blockPathType) >= 0.0F;
    }

    // ------------------------------------------------------------------
    // Target
    // ------------------------------------------------------------------

    @Override
    public Target getTarget(final double x, final double y, final double z) {
        return this.getTargetNodeAt(x, y, z);
    }

    // ------------------------------------------------------------------
    // Neighbours
    // ------------------------------------------------------------------

    @Override
    public int getNeighbors(final Node[] neighbors, final Node pos) {
        int p = 0;
        int jumpSize = 0;
        PathType blockPathTypeAbove   = this.getCachedPathType(pos.x, pos.y + 1, pos.z);
        PathType blockPathTypeCurrent = this.getCachedPathType(pos.x, pos.y,     pos.z);
        if (this.getPathfindingMalus(blockPathTypeAbove) >= 0.0F
                && blockPathTypeCurrent != PathType.STICKY_HONEY) {
            jumpSize = (int) Math.floor(Math.max(1.0F, getMaxUpStep()));
        }

        double posHeight = this.getFloorLevel(pos.x, pos.y, pos.z);

        // Cardinal neighbours
        for (HDir dir : HDir.VALUES) {
            Node node = this.findAcceptedNode(
                    pos.x + dir.dx, pos.y, pos.z + dir.dz,
                    jumpSize, posHeight,
                    dir.dx, 0, dir.dz,
                    blockPathTypeCurrent);
            this.reusableNeighbors[dir.idx2d] = node;
            if (this.isNeighborValid(node, pos)) {
                neighbors[p++] = node;
            }
        }

        // Diagonal neighbours
        for (HDir dir : HDir.VALUES) {
            HDir cw = dir.clockWise();
            if (this.isDiagonalValid(pos, this.reusableNeighbors[dir.idx2d], this.reusableNeighbors[cw.idx2d])) {
                Node diagonalNode = this.findAcceptedNode(
                        pos.x + dir.dx + cw.dx, pos.y, pos.z + dir.dz + cw.dz,
                        jumpSize, posHeight,
                        dir.dx, 0, dir.dz,
                        blockPathTypeCurrent);
                if (this.isDiagonalValid(diagonalNode)) {
                    neighbors[p++] = diagonalNode;
                }
            }
        }

        return p;
    }

    protected boolean isNeighborValid(final @Nullable Node neighbor, final Node current) {
        return neighbor != null && !neighbor.closed
                && (neighbor.costMalus >= 0.0F || current.costMalus < 0.0F);
    }

    protected boolean isDiagonalValid(final Node pos, final @Nullable Node ew, final @Nullable Node ns) {
        if (ns == null || ew == null || ns.y > pos.y || ew.y > pos.y) {
            return false;
        }
        if (ew.type == PathType.WALKABLE_DOOR || ns.type == PathType.WALKABLE_DOOR) {
            return false;
        }
        double bbWidth = this.mob.getBoundingBox().getWidthX();
        if (bbWidth > 1.0 && (ew.costMalus > 0.0F || ns.costMalus > 0.0F)) {
            return false;
        }
        boolean canPassBetweenPosts = ns.type == PathType.FENCE
                && ew.type == PathType.FENCE
                && bbWidth < 0.5;
        return (ns.y < pos.y || ns.costMalus >= 0.0F || canPassBetweenPosts)
                && (ew.y < pos.y || ew.costMalus >= 0.0F || canPassBetweenPosts);
    }

    protected boolean isDiagonalValid(final @Nullable Node diagonal) {
        if (diagonal == null || diagonal.closed) return false;
        return diagonal.type != PathType.WALKABLE_DOOR && diagonal.costMalus >= 0.0F;
    }

    // ------------------------------------------------------------------
    // Floor level
    // ------------------------------------------------------------------

    /**
     * Returns the Y-coordinate of the surface below the given block position, accounting
     * for water when the mob can float or is amphibious.
     */
    protected double getFloorLevel(final int x, final int y, final int z) {
        World world = this.currentContext.world();
        if ((this.canFloat() || this.isAmphibious())
                && world.getBlockAt(x, y, z).getType() == Material.WATER) {
            return y + 0.5;
        }
        return getFloorLevel(world, x, y, z);
    }

    /**
     * Returns the Y-coordinate of the top surface of the block below (x, y, z).
     * Uses {@link Block#getBoundingBox()} to get the collision height.
     */
    public static double getFloorLevel(final World world, final int x, final int y, final int z) {
        int belowY = y - 1;
        Block below = world.getBlockAt(x, belowY, z);
        BoundingBox shape = below.getBoundingBox();
        // If the block has no collision box (e.g. air, flowers), height = 0
        double topY = shape.getMaxY();
        // getBoundingBox returns coords in world space; we want the fractional height
        double fractionalTop = topY - belowY;
        return belowY + Math.min(1.0, Math.max(0.0, fractionalTop));
    }

    protected boolean isAmphibious() {
        return false;
    }

    // ------------------------------------------------------------------
    // findAcceptedNode
    // ------------------------------------------------------------------

    /**
     * Finds an acceptable neighbour node at (x, y, z).
     *
     * @param dirDx / dirDy / dirDz  travel direction unit vector (replaces NMS Direction)
     */
    protected @Nullable Node findAcceptedNode(
            final int x, final int y, final int z,
            final int jumpSize, final double nodeHeight,
            final int dirDx, final int dirDy, final int dirDz,
            final PathType blockPathTypeCurrent
    ) {
        Node best = null;
        double maxYTarget = this.getFloorLevel(x, y, z);
        if (maxYTarget - nodeHeight > this.getMobJumpHeight()) {
            return null;
        }

        PathType pathType = this.getCachedPathType(x, y, z);
        float pathCost = this.getPathfindingMalus(pathType);
        if (pathCost >= 0.0F) {
            best = this.getNodeAndUpdateCostToMax(x, y, z, pathType, pathCost);
        }

        if (doesBlockHavePartialCollision(blockPathTypeCurrent)
                && best != null && best.costMalus >= 0.0F
                && !this.canReachWithoutCollision(best)) {
            best = null;
        }

        if (pathType != PathType.WALKABLE && (!this.isAmphibious() || pathType != PathType.WATER)) {
            if ((best == null || best.costMalus < 0.0F)
                    && jumpSize > 0
                    && (pathType != PathType.FENCE || this.canWalkOverFences())
                    && pathType != PathType.UNPASSABLE_RAIL
                    && pathType != PathType.TRAPDOOR
                    && pathType != PathType.POWDER_SNOW) {
                best = this.tryJumpOn(x, y, z, jumpSize, nodeHeight,
                        dirDx, dirDy, dirDz, blockPathTypeCurrent);
            } else if (!this.isAmphibious() && pathType == PathType.WATER && !this.canFloat()) {
                best = this.tryFindFirstNonWaterBelow(x, y, z, best);
            } else if (pathType == PathType.OPEN) {
                best = this.tryFindFirstGroundNodeBelow(x, y, z);
            } else if (doesBlockHavePartialCollision(pathType) && best == null) {
                best = this.getClosedNode(x, y, z, pathType);
            }
            return best;
        } else {
            return best;
        }
    }

    // Overload used by FlyNodeEvaluator (no direction / height parameters needed)
    protected @Nullable Node findAcceptedNode(final int x, final int y, final int z) {
        return this.findAcceptedNode(x, y, z, 0, 0.0, 0, 0, 0, PathType.OPEN);
    }

    private double getMobJumpHeight() {
        return Math.max(DEFAULT_MOB_JUMP_HEIGHT, getMaxUpStep());
    }

    // ------------------------------------------------------------------
    // Node helpers
    // ------------------------------------------------------------------

    private Node getNodeAndUpdateCostToMax(final int x, final int y, final int z,
                                            final PathType pathType, final float cost) {
        Node node = this.getNode(x, y, z);
        node.type = pathType;
        node.costMalus = Math.max(node.costMalus, cost);
        return node;
    }

    private Node getBlockedNode(final int x, final int y, final int z) {
        Node node = this.getNode(x, y, z);
        node.type = PathType.BLOCKED;
        node.costMalus = -1.0F;
        return node;
    }

    private Node getClosedNode(final int x, final int y, final int z, final PathType pathType) {
        Node node = this.getNode(x, y, z);
        node.closed = true;
        node.type = pathType;
        node.costMalus = pathType.getMalus();
        return node;
    }

    // ------------------------------------------------------------------
    // Jump / fall helpers
    // ------------------------------------------------------------------

    private @Nullable Node tryJumpOn(
            final int x, final int y, final int z,
            final int jumpSize, final double nodeHeight,
            final int dirDx, final int dirDy, final int dirDz,
            final PathType blockPathTypeCurrent
    ) {
        Node nodeAbove = this.findAcceptedNode(x, y + 1, z, jumpSize - 1, nodeHeight,
                dirDx, dirDy, dirDz, blockPathTypeCurrent);
        if (nodeAbove == null) return null;

        double bbWidth = this.mob.getBoundingBox().getWidthX();
        if (bbWidth >= 1.0F) return nodeAbove;
        if (nodeAbove.type != PathType.OPEN && nodeAbove.type != PathType.WALKABLE) return nodeAbove;

        double centerX = x - dirDx + 0.5;
        double centerZ = z - dirDz + 0.5;
        double halfWidth = bbWidth / 2.0;
        double floorAtStep   = this.getFloorLevel((int) Math.floor(centerX), y + 1, (int) Math.floor(centerZ));
        double floorAtTarget = this.getFloorLevel(nodeAbove.x, nodeAbove.y, nodeAbove.z);
        BoundingBox grow = BoundingBox.of(
                new Vector(centerX - halfWidth, floorAtStep   + 0.001, centerZ - halfWidth),
                new Vector(centerX + halfWidth, this.mob.getBoundingBox().getHeight() + floorAtTarget - 0.002, centerZ + halfWidth)
        );
        return this.hasCollisions(grow) ? null : nodeAbove;
    }

    private @Nullable Node tryFindFirstNonWaterBelow(final int x, int y, final int z,
                                                      @Nullable Node best) {
        y--;
        int minY = this.currentContext.world().getMinHeight();
        while (y > minY) {
            PathType pathTypeLocal = this.getCachedPathType(x, y, z);
            if (pathTypeLocal != PathType.WATER) return best;
            best = this.getNodeAndUpdateCostToMax(x, y, z, pathTypeLocal,
                    this.getPathfindingMalus(pathTypeLocal));
            y--;
        }
        return best;
    }

    private Node tryFindFirstGroundNodeBelow(final int x, final int y, final int z) {
        int minY = this.currentContext.world().getMinHeight();
        for (int currentY = y - 1; currentY >= minY; currentY--) {
            if (y - currentY > getMaxFallDistance()) {
                return this.getBlockedNode(x, currentY, z);
            }
            PathType pathType = this.getCachedPathType(x, currentY, z);
            float pathCost = this.getPathfindingMalus(pathType);
            if (pathType != PathType.OPEN) {
                if (pathCost >= 0.0F) {
                    return this.getNodeAndUpdateCostToMax(x, currentY, z, pathType, pathCost);
                }
                return this.getBlockedNode(x, currentY, z);
            }
        }
        return this.getBlockedNode(x, y, z);
    }

    // ------------------------------------------------------------------
    // Collision detection
    // ------------------------------------------------------------------

    /**
     * Returns true if any block overlapping the given bounding box has a solid collision shape.
     *
     * <p>Replaces {@code context.level().noCollision(mob, aabb)}.
     * We scan every block position whose bounding box intersects the test AABB.
     */
   private boolean hasCollisions(final BoundingBox aabb) {
        // 1. On vérifie le cache manuellement
        if (this.collisionCache.containsKey(aabb)) {
            return this.collisionCache.getBoolean(aabb);
        }

        // 2. Si pas en cache, on calcule
        boolean result = calculateCollisions(aabb);

        // 3. On sauvegarde et on retourne
        this.collisionCache.put(aabb, result);
        return result;
    }

    // Extraction de la logique dans une méthode privée pour la lisibilité
    private boolean calculateCollisions(final BoundingBox bb) {
        World world = this.currentContext.world();
        int x0 = (int) Math.floor(bb.getMinX());
        int y0 = (int) Math.floor(bb.getMinY());
        int z0 = (int) Math.floor(bb.getMinZ());
        int x1 = (int) Math.ceil(bb.getMaxX());
        int y1 = (int) Math.ceil(bb.getMaxY());
        int z1 = (int) Math.ceil(bb.getMaxZ());

        for (int bx = x0; bx < x1; bx++) {
            for (int by = y0; by < y1; by++) {
                for (int bz = z0; bz < z1; bz++) {
                    Block block = world.getBlockAt(bx, by, bz);
                    if (block.getType().isAir()) continue;
                    BoundingBox blockBB = block.getBoundingBox();
                    if (!blockBB.overlaps(bb)) continue;
                    
                    if (blockBB.getVolume() > 0) return true;
                }
            }
        }
        return false;
    }

    private boolean canReachWithoutCollision(final Node posTo) {
        BoundingBox bb = this.mob.getBoundingBox();
        Location mobLoc = this.mob.getLocation();

        double dx = posTo.x - mobLoc.getX() + bb.getWidthX() / 2.0;
        double dy = posTo.y - mobLoc.getY() + bb.getHeight()  / 2.0;
        double dz = posTo.z - mobLoc.getZ() + bb.getWidthZ()  / 2.0;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double size = bb.getWidthX(); // approximate "size" as width

        int steps = (int) Math.ceil(len / size);
        if (steps == 0) return true;

        double stepX = dx / steps;
        double stepY = dy / steps;
        double stepZ = dz / steps;

        double cx = bb.getCenterX();
        double cy = bb.getCenterY();
        double cz = bb.getCenterZ();
        double hx = bb.getWidthX() / 2.0;
        double hy = bb.getHeight()  / 2.0;
        double hz = bb.getWidthZ()  / 2.0;

        for (int i = 1; i <= steps; i++) {
            BoundingBox stepped = BoundingBox.of(
                    new Vector(cx + stepX * i - hx, cy + stepY * i - hy, cz + stepZ * i - hz),
                    new Vector(cx + stepX * i + hx, cy + stepY * i + hy, cz + stepZ * i + hz)
            );
            if (this.hasCollisions(stepped)) return false;
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Path type cache
    // ------------------------------------------------------------------

    protected PathType getCachedPathType(final int x, final int y, final int z) {
        return pathTypesByPosCacheByMob.computeIfAbsent(
                PathTypeCache.asLong(x, y, z),
                k -> this.getPathTypeOfMob(this.currentContext, x, y, z, this.mob));
    }

    // ------------------------------------------------------------------
    // PathType resolution
    // ------------------------------------------------------------------

    @Override
    public PathType getPathTypeOfMob(final PathfindingContext context, final int x, final int y, final int z,
                                      final Mob mob) {
        Set<PathType> blockTypes = this.getPathTypeWithinMobBB(context, x, y, z);

        if (blockTypes.size() == 1) {
            return blockTypes.iterator().next();
        } else if (blockTypes.contains(PathType.FENCE)) {
            return PathType.FENCE;
        } else if (blockTypes.contains(PathType.UNPASSABLE_RAIL)) {
            return PathType.UNPASSABLE_RAIL;
        } else {
            PathType highestMalusType = PathType.BLOCKED;
            float highestMalus = this.getPathfindingMalus(highestMalusType);

            for (PathType pathType : blockTypes) {
                float malus = this.getPathfindingMalus(pathType);
                if (malus < 0.0F) return pathType;
                if (malus >= highestMalus) {
                    highestMalus = malus;
                    highestMalusType = pathType;
                }
            }

            PathType currentNodePathType = this.getPathType(context, x, y, z);
            boolean isLargeMob = this.entityWidth > 1;
            if (isLargeMob) {
                boolean isCheaper = this.getPathfindingMalus(currentNodePathType) < highestMalus;
                boolean capMalus  = isCheaper
                        && this.getPathfindingMalus(PathType.BIG_MOBS_CLOSE_TO_DANGER) < highestMalus;
                return capMalus ? PathType.BIG_MOBS_CLOSE_TO_DANGER : highestMalusType;
            } else {
                return currentNodePathType == PathType.OPEN
                        && highestMalusType != PathType.OPEN
                        && highestMalus == 0.0F
                        ? PathType.OPEN
                        : highestMalusType;
            }
        }
    }

    public Set<PathType> getPathTypeWithinMobBB(final PathfindingContext context,
                                                  final int x, final int y, final int z) {
        EnumSet<PathType> blockTypes = EnumSet.noneOf(PathType.class);
        Location mobLoc = this.mob.getLocation();
        int mobX = mobLoc.getBlockX();
        int mobY = mobLoc.getBlockY();
        int mobZ = mobLoc.getBlockZ();

        for (int dx = 0; dx < this.entityWidth; dx++) {
            for (int dy = 0; dy < this.entityHeight; dy++) {
                for (int dz = 0; dz < this.entityDepth; dz++) {
                    int xx = dx + x;
                    int yy = dy + y;
                    int zz = dz + z;
                    PathType blockType = this.getPathType(context, xx, yy, zz);

                    if (blockType == PathType.DOOR_WOOD_CLOSED
                            && this.canOpenDoors() && this.canPassDoors()) {
                        blockType = PathType.WALKABLE_DOOR;
                    }
                    if (blockType == PathType.DOOR_OPEN && !this.canPassDoors()) {
                        blockType = PathType.BLOCKED;
                    }
                    if (blockType == PathType.RAIL
                            && this.getPathType(context, mobX, mobY,     mobZ) != PathType.RAIL
                            && this.getPathType(context, mobX, mobY - 1, mobZ) != PathType.RAIL) {
                        blockType = PathType.UNPASSABLE_RAIL;
                    }

                    blockTypes.add(blockType);
                }
            }
        }
        return blockTypes;
    }

    @Override
    public PathType getPathType(final PathfindingContext context, final int x, final int y, final int z) {
        return getPathTypeStatic(context, x, y, z);
    }

    /**
     * Resolves the PathType for a single block, considering what is below it
     * (for OPEN blocks that are above a surface).
     */
    public static PathType getPathTypeStatic(final PathfindingContext context,
                                              final int x, final int y, final int z) {
        PathType blockPathType = context.getPathTypeFromState(x, y, z);
        if (blockPathType == PathType.OPEN && y >= context.world().getMinHeight() + 1) {
            return switch (context.getPathTypeFromState(x, y - 1, z)) {
                case OPEN, WATER, LAVA, WALKABLE -> PathType.OPEN;
                case FIRE                         -> PathType.FIRE;
                case DAMAGING                     -> PathType.DAMAGING;
                case STICKY_HONEY                 -> PathType.STICKY_HONEY;
                case POWDER_SNOW                  -> PathType.ON_TOP_OF_POWDER_SNOW;
                case DAMAGE_CAUTIOUS              -> PathType.DAMAGE_CAUTIOUS;
                case TRAPDOOR                     -> PathType.ON_TOP_OF_TRAPDOOR;
                default -> checkNeighbourBlocks(context, x, y, z, PathType.WALKABLE);
            };
        }
        return blockPathType;
    }

    /**
     * Checks the 3×3×3 neighbourhood (excluding the column above/below the block itself)
     * for hazardous path types that would "contaminate" an otherwise safe block.
     */
    public static PathType checkNeighbourBlocks(final PathfindingContext context,
                                                 final int x, final int y, final int z,
                                                 final PathType blockPathType) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    PathType pathType = context.getPathTypeFromState(x + dx, y + dy, z + dz);
                    if (pathType == PathType.DAMAGING)              return PathType.DAMAGING_IN_NEIGHBOR;
                    if (pathType == PathType.FIRE || pathType == PathType.LAVA) return PathType.FIRE_IN_NEIGHBOR;
                    if (pathType == PathType.WATER)                 return PathType.WATER_BORDER;
                    if (pathType == PathType.DAMAGE_CAUTIOUS)       return PathType.DAMAGE_CAUTIOUS;
                }
            }
        }
        return blockPathType;
    }

    /**
     * Classifies a single block's material/data into a {@link PathType}.
     *
     * <p>Replaces NMS {@code getPathTypeFromState(BlockGetter, BlockPos)} which used
     * BlockState, BlockTags, and NMS block sub-classes. Bukkit equivalents:
     * <ul>
     *   <li>BlockTags.TRAPDOORS → {@link Tag#TRAPDOORS}</li>
     *   <li>Blocks.LILY_PAD     → {@link Material#LILY_PAD}</li>
     *   <li>Blocks.BIG_DRIPLEAF → {@link Material#BIG_DRIPLEAF}</li>
     *   <li>DoorBlock           → {@link Door} BlockData (Openable / wooden vs iron)</li>
     *   <li>FenceGateBlock      → {@link Gate} BlockData</li>
     *   <li>BaseRailBlock       → {@link Tag#RAILS}</li>
     *   <li>LeavesBlock         → {@link Tag#LEAVES}</li>
     *   <li>BlockTags.FENCES    → {@link Tag#FENCES}</li>
     *   <li>BlockTags.WALLS     → {@link Tag#WALLS}</li>
     *   <li>FluidTags.WATER     → {@link Material#WATER} / Waterlogged</li>
     *   <li>FluidTags.LAVA      → {@link Material#LAVA}</li>
     * </ul>
     */
    public static PathType getPathTypeFromState(final World world, final int x, final int y, final int z) {
        Block block = world.getBlockAt(x, y, z);
        Material mat = block.getType();
        BlockData data = block.getBlockData();

        if (mat.isAir()) {
            return PathType.OPEN;
        }

        // Trapdoors, lily pad, big dripleaf
        if (Tag.TRAPDOORS.isTagged(mat) || mat == Material.LILY_PAD || mat == Material.BIG_DRIPLEAF) {
            return PathType.TRAPDOOR;
        }
        if (mat == Material.POWDER_SNOW) {
            return PathType.POWDER_SNOW;
        }
        if (mat == Material.CACTUS || mat == Material.SWEET_BERRY_BUSH) {
            return PathType.DAMAGING;
        }
        if (mat == Material.HONEY_BLOCK) {
            return PathType.STICKY_HONEY;
        }
        if (mat == Material.COCOA) {
            return PathType.COCOA;
        }
        if (mat == Material.WITHER_ROSE || mat == Material.POINTED_DRIPSTONE) {
            return PathType.DAMAGE_CAUTIOUS;
        }

        // Lava
        if (mat == Material.LAVA) {
            return PathType.LAVA;
        }

        // Fire / burning
        if (NodeEvaluator.isBurningBlock(block)) {
            return PathType.FIRE;
        }

        // Doors
        if (data instanceof Door door) {
            if (door.isOpen()) return PathType.DOOR_OPEN;
            // Wooden doors can be opened by hand; iron cannot
            return isWoodenDoor(mat) ? PathType.DOOR_WOOD_CLOSED : PathType.DOOR_IRON_CLOSED;
        }

        // Fence gates
        if (data instanceof Gate gate) {
            return gate.isOpen() ? PathType.OPEN : PathType.FENCE;
        }

        // Rails
        if (Tag.RAILS.isTagged(mat)) {
            return PathType.RAIL;
        }

        // Leaves
        if (Tag.LEAVES.isTagged(mat)) {
            return PathType.LEAVES;
        }

        // Fences and walls
        if (Tag.FENCES.isTagged(mat) || Tag.WALLS.isTagged(mat)) {
            return PathType.FENCE;
        }

        // Water (source block or waterlogged)
        if (mat == Material.WATER) {
            return PathType.WATER;
        }
        if (data instanceof Waterlogged wl && wl.isWaterlogged()) {
            return PathType.WATER;
        }

        // Generic solid / passable check
        if (!isLandPathfindable(block)) {
            return PathType.BLOCKED;
        }

        return PathType.OPEN;
    }

    // ------------------------------------------------------------------
    // Overridable behaviour hooks
    // ------------------------------------------------------------------

    /**
     * Maximum step height in blocks.
     * NMS uses {@code mob.maxUpStep()}; Bukkit does not expose this directly.
     * Defaults to {@code 1.0F}. Override in a subclass for mobs with different step heights.
     */
    protected float getMaxUpStep() {
        return 1.0F;
    }

    /**
     * Maximum distance (in blocks) the mob can fall without taking damage / pathfinding being
     * interrupted.  NMS uses {@code mob.getMaxFallDistance()}; Bukkit does not expose this.
     * Defaults to {@code 3}. Override as needed.
     */
    protected int getMaxFallDistance() {
        return 3;
    }

    // ------------------------------------------------------------------
    // Static helpers
    // ------------------------------------------------------------------

    private static boolean doesBlockHavePartialCollision(final PathType type) {
        return type == PathType.FENCE
                || type == PathType.DOOR_WOOD_CLOSED
                || type == PathType.DOOR_IRON_CLOSED;
    }

    /**
     * Returns true if the block is passable for land pathfinding (i.e. a mob can walk
     * through it). Mirrors {@code BlockState.isPathfindable(PathComputationType.LAND)}.
     */
    static boolean isLandPathfindable(final Block block) {
        Material mat = block.getType();
        if (mat.isAir() || !mat.isSolid()) return true;
        // Open doors and gates are passable
        BlockData data = block.getBlockData();
        if (data instanceof Openable openable && openable.isOpen()) return true;
        // Open trapdoors are passable
        if (data instanceof TrapDoor td && td.isOpen()) return true;
        return false;
    }

    /**
     * Returns true if the block is a fluid surface the mob is standing on top of
     * (water or lava at the block's own position, i.e. the mob's feet are in fluid).
     */
    private static boolean isSurfaceFluid(final Block block) {
        Material mat = block.getType();
        return mat == Material.WATER || mat == Material.LAVA;
    }

    /**
     * Returns true if the entity's current location contains water.
     * Approximates NMS {@code LivingEntity#isInWater()}.
     */
    protected static boolean isInWater(final Mob mob) {
        Block block = mob.getLocation().getBlock();
        if (block.getType() == Material.WATER) return true;
        BlockData data = block.getBlockData();
        return data instanceof Waterlogged wl && wl.isWaterlogged();
    }

    /**
     * Returns true if the given door material is a wooden (player-openable) door.
     * Iron doors and other non-wood doors require redstone.
     */
    private static boolean isWoodenDoor(final Material mat) {
        return mat == Material.OAK_DOOR
                || mat == Material.SPRUCE_DOOR
                || mat == Material.BIRCH_DOOR
                || mat == Material.JUNGLE_DOOR
                || mat == Material.ACACIA_DOOR
                || mat == Material.DARK_OAK_DOOR
                || mat == Material.MANGROVE_DOOR
                || mat == Material.CHERRY_DOOR
                || mat == Material.BAMBOO_DOOR
                || mat == Material.CRIMSON_DOOR
                || mat == Material.WARPED_DOOR
                || mat == Material.COPPER_DOOR
                || mat == Material.EXPOSED_COPPER_DOOR
                || mat == Material.WEATHERED_COPPER_DOOR
                || mat == Material.OXIDIZED_COPPER_DOOR
                || mat == Material.WAXED_COPPER_DOOR
                || mat == Material.WAXED_EXPOSED_COPPER_DOOR
                || mat == Material.WAXED_WEATHERED_COPPER_DOOR
                || mat == Material.WAXED_OXIDIZED_COPPER_DOOR;
        // Iron door is the only non-openable-by-hand door in vanilla — all others above are wood/copper
    }
}