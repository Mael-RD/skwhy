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

    /** True when the chunk holding block column (x, z) is loaded; records the first unloaded one met. */
    protected boolean isColumnLoaded(final int x, final int z) {
        final int cx = x >> 4;
        final int cz = z >> 4;
        final long key = ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
        boolean loaded;
        if (this.loadedChunks.containsKey(key)) {
            loaded = this.loadedChunks.get(key);
        } else {
            loaded = this.currentContext.world().isChunkLoaded(cx, cz);
            this.loadedChunks.put(key, loaded);
        }
        if (!loaded) {
            this.hitUnloadedChunk = true;
        }
        return loaded;
    }

    public boolean hitUnloadedChunk() {
        return this.hitUnloadedChunk;
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