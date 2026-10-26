package skwhy.chunkpath.move.pathcalculator;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import skwhy.chunkpath.move.LongMob;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Lightable;
import org.bukkit.util.Vector;

/**
 * Abstract base class for node evaluators.
 *
 * <p>Adapted from net.minecraft.world.level.pathfinder.NodeEvaluator:
 * - net.minecraft.world.entity.LongMob   → org.bukkit.entity.LongMob
 * - net.minecraft.core.BlockPos      → plain int coordinates / org.bukkit.Location
 * - net.minecraft.world.level.PathNavigationRegion → org.bukkit.World
 * - net.minecraft.world.level.block.state.BlockState → org.bukkit.block.Block
 * - BlockTags / Blocks               → org.bukkit.Tag / org.bukkit.Material
 * - CampfireBlock.isLitCampfire()    → checked via Bukkit BlockState cast
 * - Mth.floor()                      → (int) Math.floor() / standard cast
 *
 * <p>Per-entity pathfinding penalties are stored in a plugin-side map keyed by PathType,
 * because Bukkit's LongMob has no getPathfindingMalus() equivalent. Use
 * {@link #setPathfindingMalus} / {@link #getPathfindingMalus} to manage them,
 * or pre-populate {@code pathfindingMalusMap} before calling {@link #prepare}.
 */
public abstract class NodeEvaluator {
    protected PathfindingContext currentContext;
    protected LongMob mob;
    protected final Int2ObjectMap<Node> nodes = new Int2ObjectOpenHashMap<>();
    protected int entityWidth;
    protected int entityHeight;
    protected int entityDepth;
    protected boolean canPassDoors = true;
    protected boolean canOpenDoors;
    protected boolean canFloat;
    protected boolean canWalkOverFences;
    /** Set when the search met a chunk that is not loaded (never read: that would load it synchronously). */
    protected boolean hitUnloadedChunk;
    private boolean neverLoadChunks;
    private final it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap loadedChunks = new it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap();

    /**
     * Per-entity overrides for path-type traversal cost.
     * Keyed by PathType ordinal. Values < 0 mean impassable.
     * Defaults to each PathType's built-in malus when not overridden.
     */
    private final java.util.EnumMap<PathType, Float> pathfindingMalusMap = new java.util.EnumMap<>(PathType.class);

    public void prepare(final LongMob mob) {
        this.currentContext = new PathfindingContext(mob);
        this.mob = mob;
        this.canOpenDoors = mob.canOpenDoors();
        this.nodes.clear();
        this.hitUnloadedChunk = false;
        this.loadedChunks.clear();
        Vector hitbox = mob.getHitbox();
        this.entityWidth  = (int) Math.floor(hitbox.getX() + 1.0);
        this.entityHeight = (int) Math.floor(hitbox.getY() + 1.0);
        this.entityDepth  = (int) Math.floor(hitbox.getZ() + 1.0);
    }

    public void done() {
        this.currentContext = null;
        this.mob = null;
    }

    // ------------------------------------------------------------------
    // Pathfinding malus helpers
    // ------------------------------------------------------------------

    public float getPathfindingMalus(final PathType type) {
        return pathfindingMalusMap.getOrDefault(type, type.getMalus());
    }

    public void setPathfindingMalus(final PathType type, final float malus) {
        pathfindingMalusMap.put(type, malus);
    }

    public void resetPathfindingMalus(final PathType type) {
        pathfindingMalusMap.remove(type);
    }

    /**
     * True when the chunk holding block column (x, z) is loaded; records the first unloaded one met. With
     * {@link #setNeverLoadChunks}, the chunks around the column must be loaded too (see there).
     */
    protected boolean isColumnLoaded(final int x, final int z) {
        boolean loaded = this.isChunkLoaded(x >> 4, z >> 4);
        if (loaded && this.neverLoadChunks) {
            for (int dx = -1; dx <= 1 && loaded; dx++) {
                for (int dz = -1; dz <= 1 && loaded; dz++) {
                    loaded = this.isChunkLoaded((x + dx) >> 4, (z + dz) >> 4);
                }
            }
        }
        if (!loaded) {
            this.hitUnloadedChunk = true;
        }
        return loaded;
    }

    private boolean isChunkLoaded(final int cx, final int cz) {
        final long key = ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
        if (this.loadedChunks.containsKey(key)) {
            return this.loadedChunks.get(key);
        }
        final boolean loaded = this.currentContext.world().isChunkLoaded(cx, cz);
        this.loadedChunks.put(key, loaded);
        return loaded;
    }

    /**
     * On: a column only counts as loaded when the chunks of the blocks around it are loaded as well. Reading the blocks
     * next to a node (the body's collisions, the neighbours that tell a cactus or a fire) would otherwise load, synchronously,
     * the chunk across the border; a search that must not load any chunk (the preview of a path) needs this.
     */
    public void setNeverLoadChunks(final boolean neverLoadChunks) {
        this.neverLoadChunks = neverLoadChunks;
    }

    public boolean hitUnloadedChunk() {
        return this.hitUnloadedChunk;
    }

    /** Cost added to the move from one node to the next on top of the distance and the cost of the next node (0 here). */
    public float stepCost(final Node from, final Node to) {
        return 0.0F;
    }

    // ------------------------------------------------------------------
    // Node cache
    // ------------------------------------------------------------------

    protected Node getNode(final int x, final int y, final int z) {
        return nodes.computeIfAbsent(Node.createHash(x, y, z), k -> new Node(x, y, z));
    }

    protected Node getNode(final Location pos) {
        return getNode(pos.getBlockX(), pos.getBlockY(), pos.getBlockZ());
    }

    // ------------------------------------------------------------------
    // Abstract interface
    // ------------------------------------------------------------------

    public abstract Node getStart();

    public abstract Target getTarget(double x, double y, double z);

    protected Target getTargetNodeAt(final double x, final double y, final double z) {
        return new Target(getNode((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)));
    }

    public abstract int getNeighbors(Node[] neighbors, Node pos);

    /** Straightens the nodes of a path found by the search (in place); nothing by default. */
    public void smooth(final java.util.List<Node> nodes) {}

    public abstract PathType getPathTypeOfMob(PathfindingContext context, int x, int y, int z, LongMob mob);

    public abstract PathType getPathType(PathfindingContext context, int x, int y, int z);

    public PathType getPathType(final LongMob mob, final Location pos) {
        return getPathType(new PathfindingContext(mob), pos.getBlockX(), pos.getBlockY(), pos.getBlockZ());
    }

    // ------------------------------------------------------------------
    // Capability flags
    // ------------------------------------------------------------------

    public void setCanPassDoors(final boolean canPassDoors)     { this.canPassDoors = canPassDoors; }
    public void setCanOpenDoors(final boolean canOpenDoors)     { this.canOpenDoors = canOpenDoors; }
    public void setCanFloat(final boolean canFloat)             { this.canFloat = canFloat; }
    public void setCanWalkOverFences(final boolean v)           { this.canWalkOverFences = v; }

    public boolean canPassDoors()       { return canPassDoors; }
    public boolean canOpenDoors()       { return canOpenDoors; }
    public boolean canFloat()           { return canFloat; }
    public boolean canWalkOverFences()  { return canWalkOverFences; }

    // ------------------------------------------------------------------
    // Static helpers
    // ------------------------------------------------------------------

    /**
     * Returns true if the block at the given location is actively burning/damaging.
     *
     * <p>Replaces NodeEvaluator.isBurningBlock(BlockState) which used NMS BlockTags.
     * Checks: fire, lava, magma block, lit campfire, lava cauldron.
     */
    public static boolean isBurningBlock(final Block block) {
        Material mat = block.getType();
        if (mat == Material.LAVA
                || mat == Material.MAGMA_BLOCK
                || mat == Material.LAVA_CAULDRON
                || Tag.FIRE.isTagged(mat)) {
            return true;
        }
        // Campfire: only burning if lit
        if (mat == Material.CAMPFIRE || mat == Material.SOUL_CAMPFIRE) {
            BlockState state = block.getState();
            if (state instanceof Lightable campfire) {
                return campfire.isLit();
            }
        }
        return false;
    }

}