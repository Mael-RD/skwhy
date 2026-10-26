package skwhy.chunkpath.move.pathcalculator;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Openable;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.Gate;
import org.bukkit.block.data.type.TrapDoor;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.jspecify.annotations.Nullable;

import skwhy.chunkpath.block.PathCostRules;
import skwhy.chunkpath.block.ThinBlocks;
import skwhy.chunkpath.move.LongMob;

import java.util.EnumSet;
import java.util.Set;

/**
 * Ground node evaluator adapted from {@code skwhy.pathfinder.pathcalculator.WalkNodeEvaluator}: all
 * {@link PathType} classification (passability/hazards — doors, fences, lava, rails, water...) is
 * unchanged, but the cost assigned to a normal {@link PathType#WALKABLE} floor tile comes from the
 * registered {@link PathCostRules} instead of the flat vanilla malus (always 0 for WALKABLE), via
 * {@link #costFor}. This is the same {@code costRules.costOf(blockName)} lookup
 * {@code ChunkNavMesh.buildWalkableNodes} performs for the coarse gate graph, so both layers agree on
 * cost for a given rule set.
 */
public class CostRuleWalkNodeEvaluator extends NodeEvaluator {

    public static final double SPACE_BETWEEN_WALL_POSTS = 0.5;
    private static final double DEFAULT_MOB_JUMP_HEIGHT = 1.125;

    private volatile PathCostRules costRules;

    public CostRuleWalkNodeEvaluator(final PathCostRules costRules) {
        this.costRules = costRules;
    }

    /** Change les règles de coût pour les prochaines recherches (une recherche en cours garde celles qu'elle a lues). */
    public void setCostRules(final PathCostRules costRules) {
        this.costRules = costRules;
    }

    /**
     * Cost of standing on (x, y, z), already classified as {@code pathType}. A hazard or passability malus
     * (vanilla) is kept for every type; a cell the mob stands in (plain floor, but also an open trapdoor, a door, a rail,
     * the cell above a trapdoor...) also costs what the registered cost rules give to the block below: a malus of 0
     * must not make a trapdoor cheaper than the road next to it (the entity would detour to walk through it). A cell
     * that is not floor at all (open air, water...) keeps its malus alone.
     */
    protected float costFor(final int x, final int y, final int z, final PathType pathType) {
        final float vanillaMalus = this.getPathfindingMalus(pathType);
        if (vanillaMalus < 0.0F || (pathType != PathType.WALKABLE && !isStandingType(pathType))) {
            return vanillaMalus;
        }
        final long key = PathTypeCache.asLong(x, y, z);
        final float cached = this.costCache.get(key);
        if (!Float.isNaN(cached)) return cached;
        Block below = this.currentContext.world().getBlockAt(x, y - 1, z);
        int cost = this.costRules.costOf(below.getType());
        float result;
        if (cost < 0) {
            // no rule covers this block: not walkable; a cell that is not plain floor keeps its malus
            result = pathType == PathType.WALKABLE ? -1.0F : vanillaMalus;
        } else {
            if (this.smoothHalf > 0) {
                final int opened = this.opened(x, y, z);
                if (opened >= 0 && opened < cost) cost = opened;
            }
            result = vanillaMalus + cost + this.edgePenalty(x, y, z, cost);
        }
        this.costCache.put(key, result); // a cell is looked at from each of its neighbours
        return result;
    }

    /**
     * Climbing or dropping a whole block (or more) costs the dearer of the two floors on top of the move: a staircase of
     * expensive blocks, or a ledge of a road, is not free to take. A half block or less (slabs, stairs the right way up)
     * stays free. The two costs are the registered ones, <em>not</em> the smoothed ones: the small-scale smoothing hides a
     * narrow expensive patch inside a cheap area, but it must not make a step onto or off that patch cheaper.
     */
    @Override
    public float stepCost(final Node from, final Node to) {
        final int dy = Math.abs(to.y - from.y);
        if (dy == 0) return 0.0F; // same cell height: at most a half block between a slab and a full block
        if (dy == 1) {
            final double a = this.levelOf(from), b = this.levelOf(to);
            if (Math.abs(a - b) <= 0.5 + BODY_EPSILON) return 0.0F;
        }
        final World world = this.currentContext.world();
        final int costFrom = this.costRules.costOf(world.getType(from.x, from.y - 1, from.z));
        final int costTo = this.costRules.costOf(world.getType(to.x, to.y - 1, to.z));
        return Math.max(0, Math.max(costFrom, costTo));
    }

    private double levelOf(final Node node) {
        final double level = this.standLevel(node.x, node.y, node.z);
        return Double.isNaN(level) ? getFloorLevel(this.currentContext.world(), node.x, node.y, node.z) : level;
    }

    // ------------------------------------------------------------------
    // Small-scale smoothing of the custom costs
    // ------------------------------------------------------------------
    //
    // The custom costs describe the large scale (a road, a field, a plaza). Seen block by block they make the
    // entity slalom around a single grass block in the middle of a dirt path, or hug the edge of a road. So:
    //  - a morphological opening of the cost field removes the patches more expensive than their surroundings
    //    that are narrower than the window (erosion = minimum over the window, then dilation = maximum of the
    //    eroded values over the same window). A road, a field or any wide area is left untouched.
    //  - cells next to a wall, a drop or a much more expensive floor cost a little more (fading out with the
    //    distance), which keeps the entity in the middle of the path.
    // Both only change the cost of cells the leg search visits; the gate graph is not concerned. A square window
    // is separable, so each stage reads (2h + 1) cached values of the previous one.

    private static final int NONE = Integer.MIN_VALUE; // unknown cell (no floor, unloaded chunk)
    private static final int NO_VALUE = Integer.MAX_VALUE; // "no value" for the minimum stages
    private static final int NOT_CACHED = Integer.MIN_VALUE + 1;

    private static final float NOT_CACHED_COST = Float.NaN;

    private int smoothHalf;
    private final it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap costCache = newCostCache();
    private final it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap rawCache = newIntCache();
    private final it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap rowMinCache = newIntCache();
    private final it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap erodedCache = newIntCache();
    private final it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap rowMaxCache = newIntCache();
    private final it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap openedCache = newIntCache();
    private final it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap standable = new it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap();

    private static it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap newCostCache() {
        final it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap map = new it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap();
        map.defaultReturnValue(NOT_CACHED_COST);
        return map;
    }

    private static it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap newIntCache() {
        final it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap map = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();
        map.defaultReturnValue(NOT_CACHED);
        return map;
    }

    private void clearSmoothingCaches() {
        this.costCache.clear();
        this.rawCache.clear();
        this.rowMinCache.clear();
        this.erodedCache.clear();
        this.rowMaxCache.clear();
        this.openedCache.clear();
    }

    /** A block that is floor or wall: solid, with a collision box, and not a thin one (carpet, snow, closed trapdoor: see {@link ThinBlocks}). */
    private static boolean solidAt(final World world, final int x, final int y, final int z) {
        return hasCollision(world.getType(x, y, z)) && !ThinBlocks.isThin(world.getBlockAt(x, y, z));
    }

    /** A floor: a solid block, or a closed trapdoor of the upper half (thin, but its top is walked on). */
    private static boolean floorAt(final World world, final int x, final int y, final int z) {
        return solidAt(world, x, y, z) || ThinBlocks.isCeilingTrapdoor(world.getBlockAt(x, y, z));
    }

    /** Solid with a collision box: pressure plates and buttons are {@code isSolid()} but are neither floor nor wall. */
    private static boolean hasCollision(final Material material) {
        return material.isSolid() && !skwhy.chunkpath.block.BlockClassifier.noCollisionBlocks().contains(material);
    }

    /** Custom cost of the floor of the cell (x, y, z) as a neighbour sees it: {@code -1} blocked or a wall, {@link #NONE} no floor. */
    private int raw(final int x, final int y, final int z) {
        final long key = PathTypeCache.asLong(x, y, z);
        final int cached = this.rawCache.get(key);
        if (cached != NOT_CACHED) return cached;
        int result = NONE;
        if (this.isColumnLoaded(x, z)) {
            final World world = this.currentContext.world();
            final Material feet = world.getType(x, y, z);
            if (solidAt(world, x, y, z)) {
                result = solidAt(world, x, y + 1, z) ? -1 : this.costRules.costOf(feet); // a wall, or one step up
            } else {
                final Material below = world.getType(x, y - 1, z);
                if (floorAt(world, x, y - 1, z)) {
                    result = this.costRules.costOf(below);
                } else {
                    final Material below2 = world.getType(x, y - 2, z);
                    if (floorAt(world, x, y - 2, z)) result = this.costRules.costOf(below2); // one step down
                }
            }
        }
        this.rawCache.put(key, result);
        return result;
    }

    private int rowMin(final int x, final int y, final int z) {
        final long key = PathTypeCache.asLong(x, y, z);
        final int cached = this.rowMinCache.get(key);
        if (cached != NOT_CACHED) return cached;
        int min = NO_VALUE;
        for (int dx = -this.smoothHalf; dx <= this.smoothHalf; dx++) {
            final int value = this.raw(x + dx, y, z);
            if (value >= 0 && value < min) min = value;
        }
        this.rowMinCache.put(key, min);
        return min;
    }

    private int eroded(final int x, final int y, final int z) {
        final long key = PathTypeCache.asLong(x, y, z);
        final int cached = this.erodedCache.get(key);
        if (cached != NOT_CACHED) return cached;
        int min = NO_VALUE;
        for (int dz = -this.smoothHalf; dz <= this.smoothHalf; dz++) {
            min = Math.min(min, this.rowMin(x, y, z + dz));
        }
        this.erodedCache.put(key, min);
        return min;
    }

    private int rowMax(final int x, final int y, final int z) {
        final long key = PathTypeCache.asLong(x, y, z);
        final int cached = this.rowMaxCache.get(key);
        if (cached != NOT_CACHED) return cached;
        int max = -1;
        for (int dx = -this.smoothHalf; dx <= this.smoothHalf; dx++) {
            final int value = this.eroded(x + dx, y, z);
            if (value != NO_VALUE && value > max) max = value;
        }
        this.rowMaxCache.put(key, max);
        return max;
    }

    /** Cost of the floor of the cell once the patches narrower than the window are removed; {@code -1} when there is no value. */
    private int opened(final int x, final int y, final int z) {
        final long key = PathTypeCache.asLong(x, y, z);
        final int cached = this.openedCache.get(key);
        if (cached != NOT_CACHED) return cached;
        int max = -1;
        for (int dz = -this.smoothHalf; dz <= this.smoothHalf; dz++) {
            max = Math.max(max, this.rowMax(x, y, z + dz));
        }
        this.openedCache.put(key, max);
        return max;
    }

    /** Smoothed cost of a neighbouring cell, as the centring sees it; negative = wall, blocked or no floor. */
    private int neighbourCost(final int x, final int y, final int z) {
        final int raw = this.raw(x, y, z);
        if (raw < 0) return raw;
        if (this.smoothHalf > 0) {
            final int opened = this.opened(x, y, z);
            if (opened >= 0 && opened < raw) return opened;
        }
        return raw;
    }

    /** Cost added next to a worse cell (wall, drop, a floor costing more than twice as much), fading out with the distance. */
    private float edgePenalty(final int x, final int y, final int z, final int cost) {
        final double weight = skwhy.chunkpath.debug.ChunkPathTuning.edgeWeight;
        final int reach = skwhy.chunkpath.debug.ChunkPathTuning.edgeDistance;
        if (weight <= 0.0 || reach <= 0) return 0.0F;
        final int limit = 2 * cost + 1;
        for (int r = 1; r <= reach; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) != r && Math.abs(dz) != r) continue; // only the ring at distance r
                    final int n = this.neighbourCost(x + dx, y, z + dz);
                    if (n < 0 || n > limit) {
                        return (float) (weight * (reach - r + 1) / reach);
                    }
                }
            }
        }
        return 0.0F;
    }

    /**
     * Straightens a path found by the search: nodes are dropped when the mob can walk straight from one node to a
     * node further on, on the same level, over cells it can stand on, for no more than the cost of the nodes it
     * skips (so a detour around an expensive area stays). Nodes that are not plain floor (doors...) are kept.
     */
    @Override
    public void smooth(final java.util.List<Node> nodes) {
        if (!skwhy.chunkpath.debug.ChunkPathTuning.smooth || nodes.size() < 3) return;
        final int span = Math.max(2, skwhy.chunkpath.debug.ChunkPathTuning.smoothSpan);
        final java.util.List<Node> result = new java.util.ArrayList<>(nodes.size());
        int i = 0;
        result.add(nodes.get(0));
        while (i < nodes.size() - 1) {
            int best = i + 1;
            int failures = 0;
            for (int j = i + 2; j < nodes.size() && j <= i + span && failures < 3; j++) {
                final Node between = nodes.get(j - 1);
                if (between.type != PathType.WALKABLE || between.y != nodes.get(i).y) break;
                if (this.canWalkStraight(nodes, i, j)) {
                    best = j;
                    failures = 0;
                } else {
                    failures++;
                }
            }
            result.add(nodes.get(best));
            i = best;
        }
        if (result.size() < nodes.size()) {
            nodes.clear();
            nodes.addAll(result);
        }
    }

    private boolean canWalkStraight(final java.util.List<Node> nodes, final int from, final int to) {
        final Node a = nodes.get(from);
        final Node b = nodes.get(to);
        if (a.y != b.y || a.type != PathType.WALKABLE) return false;
        final double stand = this.standLevel(a.x, a.y, a.z);
        if (Double.isNaN(stand)) return false;
        double original = 0.0;
        for (int k = from + 1; k <= to; k++) {
            final Node node = nodes.get(k);
            if (node.y != a.y || node.costMalus < 0.0F) return false;
            original += nodes.get(k - 1).distanceTo(node) + node.costMalus;
        }
        final double dx = b.x - a.x, dz = b.z - a.z;
        final double length = Math.sqrt(dx * dx + dz * dz);
        final double half = this.mob.getHitbox().getX() / 2.0 + BODY_MARGIN;
        final int samples = (int) Math.ceil(length * 4.0);
        int lastX = a.x, lastZ = a.z;
        double straight = length;
        Node fromCell = new Node(a.x, a.y, a.z);
        for (int s = 1; s <= samples; s++) {
            final double t = (double) s / samples;
            final double px = a.x + 0.5 + dx * t, pz = a.z + 0.5 + dz * t;
            for (int corner = 0; corner < 4; corner++) {
                final int cx = (int) Math.floor(px + ((corner & 1) == 0 ? -half : half));
                final int cz = (int) Math.floor(pz + ((corner & 2) == 0 ? -half : half));
                if (!this.standableAt(cx, a.y, cz, stand)) return false;
            }
            final int centerX = (int) Math.floor(px), centerZ = (int) Math.floor(pz);
            if (centerX != lastX || centerZ != lastZ) {
                final Node cell = new Node(centerX, a.y, centerZ);
                if (!this.stepClear(fromCell, cell)) return false;
                final float cost = this.costFor(centerX, a.y, centerZ, PathType.WALKABLE);
                if (cost < 0.0F) return false;
                straight += cost;
                if (straight > original + 0.01) return false;
                fromCell = cell;
                lastX = centerX;
                lastZ = centerZ;
            }
        }
        return straight <= original + 0.01;
    }

    /** True when the cell is plain floor at the given standing height (flat ground, no door, room for the body). */
    private boolean standableAt(final int x, final int y, final int z, final double stand) {
        if (!this.isColumnLoaded(x, z)) return false;
        final long key = PathTypeCache.asLong(x, y, z);
        final byte cached = this.standable.getOrDefault(key, (byte) 0);
        if (cached != 0) return cached > 0;
        boolean ok = this.getCachedPathType(x, y, z) == PathType.WALKABLE;
        if (ok) {
            final double level = this.standLevel(x, y, z);
            ok = !Double.isNaN(level) && Math.abs(level - stand) < 1.0E-3 && this.costFor(x, y, z, PathType.WALKABLE) >= 0.0F;
        }
        this.standable.put(key, (byte) (ok ? 1 : -1));
        return ok;
    }

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
    public void prepare(final LongMob mob) {
        super.prepare(mob);
        this.cellBoxes.clear();
        this.standLevels.clear();
        this.standable.clear();
        this.clearSmoothingCaches();
        this.smoothHalf = Math.max(0, mob.costSmoothing() / 2);
        // NMS calls navigation.onPathfindingStart() here – not available in Bukkit
    }

    @Override
    public void done() {
        this.cellBoxes.clear();
        this.standLevels.clear();
        this.standable.clear();
        this.clearSmoothingCaches();
        // NMS calls navigation.onPathfindingDone() here – not available in Bukkit
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
            if (this.canFloat() && mob.isInWater()) {
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
            } else if (this.mob.onGround()) {
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
            if (this.canStartAt((int) Math.floor(mob.getLocation().getX()), startY, (int) Math.floor(mob.getLocation().getZ()))
                    || this.canStartAt((int) Math.floor(mob.getLocation().getX()), startY, (int) Math.floor(mob.getLocation().getZ()))
                    || this.canStartAt((int) Math.floor(mob.getLocation().getX()), startY, (int) Math.floor(mob.getLocation().getZ()))
                    || this.canStartAt((int) Math.floor(mob.getLocation().getX()), startY, (int) Math.floor(mob.getLocation().getZ()))) {
                return this.getStartNode(
                        (int) Math.floor(mob.getLocation().getX()), startY, (int) Math.floor(mob.getLocation().getZ()));
            }
        }

        return this.getStartNode(startX, startY, startZ);
    }

    protected Node getStartNode(final int x, final int y, final int z) {
        Node node = this.getNode(x, y, z);
        node.type = this.getCachedPathType(x, y, z);
        node.costMalus = this.costFor(x, y, z, node.type);
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
            if (node != null && !node.closed && !this.stepClear(pos, node)) {
                node = null;
            }
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
                if (this.isDiagonalValid(diagonalNode) && this.stepClear(pos, diagonalNode)) {
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
        double bbWidth = this.mob.getHitbox().getX();
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
     * Returns the Y-coordinate of the top surface of the block below (x, y, z), from its
     * <em>collision</em> shape: a pressure plate, a flower or a tuft of grass has a selection box
     * ({@link Block#getBoundingBox()}) but nothing to stand on, and using that box made the search
     * believe a mob could stand a block higher than the real floor.
     */
    public static double getFloorLevel(final World world, final int x, final int y, final int z) {
        int belowY = y - 1;
        Block below = world.getBlockAt(x, belowY, z);
        double top = 0.0;
        for (BoundingBox box : below.getCollisionShape().getBoundingBoxes()) {
            top = Math.max(top, box.getMaxY());
        }
        return belowY + top; // a fence or a wall is 1.5 high: the cell above it is no floor a mob can step onto
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
        Node found = this.findAcceptedNodeUnchecked(x, y, z, jumpSize, nodeHeight, dirDx, dirDy, dirDz, blockPathTypeCurrent);
        if (geometryChecks && found != null && !found.closed && found.costMalus >= 0.0F && isStandingType(found.type)
                && !this.hasRoomToStand(found.x, found.y, found.z)) {
            return null; // a trapdoor, a shelf, a low ceiling over a carpet... fills the space the mob needs
        }
        return found;
    }

    private static boolean isStandingType(final PathType type) {
        return switch (type) {
            case WALKABLE, WALKABLE_DOOR, DOOR_OPEN, TRAPDOOR, ON_TOP_OF_TRAPDOOR, RAIL,
                 FIRE_IN_NEIGHBOR, DAMAGING_IN_NEIGHBOR, DAMAGE_CAUTIOUS, STICKY_HONEY, COCOA -> true;
            default -> false;
        };
    }

    /** Switch for the body-volume checks ({@link #hasRoomToStand}, {@link #stepClear}); {@code /cpdebug geometry on|off}. */
    public static volatile boolean geometryChecks = true;

    /** Highest obstacle (above the floor) a mob walks onto without jumping. */
    private static final double STEP_HEIGHT = 0.6;
    private static final double BODY_EPSILON = 1.0E-3;
    /** The body is a little narrower than its hitbox when squeezing past: 0.0125 of play is enough in vanilla too. */
    private static final double BODY_MARGIN = 0.02;
    private static final BoundingBox[] NO_BOXES = new BoundingBox[0];

    // per search caches: collision boxes of a cell (world coordinates) and the height a mob stands at in a node
    private final Long2ObjectMap<BoundingBox[]> cellBoxes = new Long2ObjectOpenHashMap<>();
    private final it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap standLevels = new it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap();

    /**
     * Collision boxes of a cell, in world coordinates. Hand-openable doors count for nothing when the mob can open
     * them (the door opens in front of it).
     */
    private BoundingBox[] boxesOf(final int x, final int y, final int z) {
        final long key = PathTypeCache.asLong(x, y, z);
        BoundingBox[] cached = this.cellBoxes.get(key);
        if (cached != null) return cached;
        final Block block = this.currentContext.world().getBlockAt(x, y, z);
        final Material mat = block.getType();
        BoundingBox[] result = NO_BOXES;
        if (!mat.isAir() && !(this.canOpenDoors && Tag.DOORS.isTagged(mat) && mat != Material.IRON_DOOR)) {
            final java.util.Collection<BoundingBox> local = block.getCollisionShape().getBoundingBoxes();
            if (!local.isEmpty()) {
                result = new BoundingBox[local.size()];
                int i = 0;
                for (BoundingBox box : local) result[i++] = box.clone().shift(x, y, z);
            }
        }
        this.cellBoxes.put(key, result);
        return result;
    }

    private boolean overlapsColumn(final BoundingBox box, final double minX, final double maxX, final double minZ, final double maxZ) {
        return box.getMaxX() > minX && box.getMinX() < maxX && box.getMaxZ() > minZ && box.getMinZ() < maxZ;
    }

    /**
     * True when a mob of this evaluator's size can stand at the node: its body volume, standing on the floor
     * (raised by a low step in the cell itself: carpet, snow layer, trapdoor lying flat...), is free of any collision
     * shape. The cell-based path type classification cannot see partial blocks (a trapdoor used as a chair, a shelf at
     * head height, a lintel too low for a mob standing on a carpet), and the mob would then push against them forever.
     */
    private boolean hasRoomToStand(final int x, final int y, final int z) {
        return !Double.isNaN(this.standLevel(x, y, z));
    }

    /** Height the mob stands at in the node's cell, or NaN when its body does not fit there. */
    private double standLevel(final int x, final int y, final int z) {
        final long key = PathTypeCache.asLong(x, y, z);
        if (this.standLevels.containsKey(key)) return this.standLevels.get(key);
        final double result = this.computeStandLevel(x, y, z);
        this.standLevels.put(key, result);
        return result;
    }

    private double computeStandLevel(final int x, final int y, final int z) {
        final double width = this.mob.getHitbox().getX();
        final double height = this.mob.getHitbox().getY();
        final double minX = x + 0.5 - width / 2.0 + BODY_MARGIN;
        final double maxX = x + 0.5 + width / 2.0 - BODY_MARGIN;
        final double minZ = z + 0.5 - width / 2.0 + BODY_MARGIN;
        final double maxZ = z + 0.5 + width / 2.0 - BODY_MARGIN;
        final double floor = getFloorLevel(this.currentContext.world(), x, y, z);

        // collision boxes of the column over the footprint, from the node's cell up to the head (and a step more)
        final java.util.List<BoundingBox> boxes = new java.util.ArrayList<>();
        final int topCell = (int) Math.ceil(floor + STEP_HEIGHT + height) + 1;
        for (int cellY = y; cellY <= topCell; cellY++) {
            for (BoundingBox box : this.boxesOf(x, cellY, z)) {
                if (this.overlapsColumn(box, minX, maxX, minZ, maxZ)) boxes.add(box);
            }
        }
        if (boxes.isEmpty()) return floor;

        // low boxes resting on the floor are stepped onto, raising the body
        double stand = floor;
        boolean raised = true;
        while (raised) {
            raised = false;
            for (BoundingBox box : boxes) {
                if (box.getMinY() <= stand + BODY_EPSILON && box.getMaxY() > stand + BODY_EPSILON) {
                    if (box.getMaxY() - floor > STEP_HEIGHT + BODY_EPSILON) return Double.NaN; // too tall to step onto
                    stand = box.getMaxY();
                    raised = true;
                }
            }
        }
        for (BoundingBox box : boxes) {
            if (box.getMinY() > stand + BODY_EPSILON && box.getMinY() < stand + height - BODY_EPSILON) return Double.NaN;
        }
        return stand;
    }

    /**
     * Whether the mob can actually move from one node to the next: the body, swept along the way, meets no
     * collision shape that is not a low step. A node can be fine to stand on and still be closed off from one side
     * (an open trapdoor or a door leaf along the cell's edge, a shelf sticking out...).
     */
    private boolean stepClear(final Node from, final Node to) {
        if (!geometryChecks) return true;
        double standFrom = this.standLevel(from.x, from.y, from.z);
        if (Double.isNaN(standFrom)) {
            standFrom = getFloorLevel(this.currentContext.world(), from.x, from.y, from.z); // e.g. the start node, wedged somewhere
        }
        final double standTo = this.standLevel(to.x, to.y, to.z);
        if (Double.isNaN(standTo)) return true; // not a node the mob can stand in: findAcceptedNode dealt with it
        final double width = this.mob.getHitbox().getX() - 2.0 * BODY_MARGIN;
        final double height = this.mob.getHitbox().getY();
        // going up, the body rises as soon as its front meets the step; going down, it keeps its height until it has
        // fully left the ledge (a wide body is still above the edge, so it is not falling yet)
        final boolean rising = standTo > standFrom;
        for (double t : new double[] { 0.25, 0.5, 0.75, 1.0 }) {
            final double cx = from.x + 0.5 + (to.x - from.x) * t;
            final double cz = from.z + 0.5 + (to.z - from.z) * t;
            final double feet = rising || t >= 1.0 ? standTo : standFrom;
            final double minX = cx - width / 2.0;
            final double maxX = cx + width / 2.0;
            final double minZ = cz - width / 2.0;
            final double maxZ = cz + width / 2.0;
            for (int bx = (int) Math.floor(minX); bx <= (int) Math.floor(maxX); bx++) {
                for (int bz = (int) Math.floor(minZ); bz <= (int) Math.floor(maxZ); bz++) {
                    for (int by = (int) Math.floor(feet); by <= (int) Math.floor(feet + height); by++) {
                        for (BoundingBox box : this.boxesOf(bx, by, bz)) {
                            if (!this.overlapsColumn(box, minX, maxX, minZ, maxZ)) continue;
                            if (box.getMaxY() <= feet + BODY_EPSILON || box.getMinY() >= feet + height - BODY_EPSILON) continue;
                            if (box.getMinY() <= feet + BODY_EPSILON && box.getMaxY() - feet <= STEP_HEIGHT + BODY_EPSILON) continue;
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }

    protected @Nullable Node findAcceptedNodeUnchecked(
            final int x, final int y, final int z,
            final int jumpSize, final double nodeHeight,
            final int dirDx, final int dirDy, final int dirDz,
            final PathType blockPathTypeCurrent
    ) {
        if (!this.isColumnLoaded(x, z)) {
            return null; // never read a block of an unloaded chunk (it would be loaded synchronously)
        }
        Node best = null;
        double maxYTarget = this.getFloorLevel(x, y, z);
        if (maxYTarget - nodeHeight > this.getMobJumpHeight()) {
            return null;
        }

        PathType pathType = this.getCachedPathType(x, y, z);
        float pathCost = this.costFor(x, y, z, pathType);
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

        double bbWidth = this.mob.getHitbox().getX();
        if (bbWidth >= 1.0F) return nodeAbove;
        if (nodeAbove.type != PathType.OPEN && nodeAbove.type != PathType.WALKABLE) return nodeAbove;

        double centerX = x - dirDx + 0.5;
        double centerZ = z - dirDz + 0.5;
        double halfWidth = bbWidth / 2.0;
        double floorAtStep   = this.getFloorLevel((int) Math.floor(centerX), y + 1, (int) Math.floor(centerZ));
        double floorAtTarget = this.getFloorLevel(nodeAbove.x, nodeAbove.y, nodeAbove.z);
        BoundingBox grow = BoundingBox.of(
                new Vector(centerX - halfWidth, floorAtStep   + 0.001, centerZ - halfWidth),
                new Vector(centerX + halfWidth, this.mob.getHitbox().getY() + floorAtTarget - 0.002, centerZ + halfWidth)
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
                    this.costFor(x, y, z, pathTypeLocal));
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
            float pathCost = this.costFor(x, currentY, z, pathType);
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
                    for (BoundingBox local : block.getCollisionShape().getBoundingBoxes()) {
                        BoundingBox blockBB = local.clone().shift(bx, by, bz);
                        if (blockBB.overlaps(bb) && blockBB.getVolume() > 0) return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean canReachWithoutCollision(final Node posTo) {
        Vector hitbox = this.mob.getHitbox();
        Location mobLoc = this.mob.getLocation();

        double dx = posTo.x - mobLoc.getX() + hitbox.getX() / 2.0;
        double dy = posTo.y - mobLoc.getY() + hitbox.getY()  / 2.0;
        double dz = posTo.z - mobLoc.getZ() + hitbox.getZ()  / 2.0;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double size = hitbox.getX(); // approximate "size" as width

        int steps = (int) Math.ceil(len / size);
        if (steps == 0) return true;

        double stepX = dx / steps;
        double stepY = dy / steps;
        double stepZ = dz / steps;

        double hx = hitbox.getX()  / 2.0;
        double hy = hitbox.getY()  / 2.0;
        double hz = hitbox.getZ()  / 2.0;
        double cx = mobLoc.getX();
        double cy = mobLoc.getY() + hy;
        double cz = mobLoc.getZ();

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
                                      final LongMob mob) {
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
            PathType below = context.getPathTypeFromState(x, y - 1, z);
            // the top of a closed trapdoor of the upper half is a floor for the cell above it
            if (below == PathType.OPEN && ThinBlocks.isCeilingTrapdoor(context.world().getBlockAt(x, y - 1, z))) {
                below = PathType.BLOCKED;
            }
            return switch (below) {
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
        // a trapdoor is not a type of its own: closed it is thin (see ThinBlocks), open it is a panel along one side of its
        // cell, which the body geometry (hasRoomToStand / stepClear) treats as the obstacle it is
        if (mat == Material.LILY_PAD || mat == Material.BIG_DRIPLEAF) {
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
    public static boolean isLandPathfindable(final Block block) {
        Material mat = block.getType();
        if (mat.isAir() || !mat.isSolid()) return true;
        // solid on paper but nothing to collide with (pressure plate, button...): a mob walks over it
        final java.util.Collection<BoundingBox> shape = block.getCollisionShape().getBoundingBoxes();
        if (shape.isEmpty()) return true;
        // carpet, snow, closed trapdoor...: not an obstacle (the body geometry still checks steps and headroom)
        if (ThinBlocks.isThin(shape)) return true;
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
    protected static boolean isInWater(final LongMob mob) {
        Block block = mob.getLocation().getBlock();
        if (block.getType() == Material.WATER) return true;
        BlockData data = block.getBlockData();
        return data instanceof Waterlogged wl && wl.isWaterlogged();
    }

    /**
     * Returns true if the given door material can be opened by hand (every door but the iron one,
     * which needs redstone).
     */
    private static boolean isWoodenDoor(final Material mat) {
        return mat != Material.IRON_DOOR;
    }
}
