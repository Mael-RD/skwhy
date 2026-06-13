package skwhy.pathfinder;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Lightable;
import org.bukkit.util.Vector;

/**
 * Abstract base class for node evaluators.
 *
 * <p>Adapted from net.minecraft.world.level.pathfinder.NodeEvaluator:
 * - net.minecraft.world.entity.Mob   → org.bukkit.entity.Mob
 * - net.minecraft.core.BlockPos      → plain int coordinates / org.bukkit.Location
 * - net.minecraft.world.level.PathNavigationRegion → org.bukkit.World
 * - net.minecraft.world.level.block.state.BlockState → org.bukkit.block.Block
 * - BlockTags / Blocks               → org.bukkit.Tag / org.bukkit.Material
 * - CampfireBlock.isLitCampfire()    → checked via Bukkit BlockState cast
 * - Mth.floor()                      → (int) Math.floor() / standard cast
 *
 * <p>Per-entity pathfinding penalties are stored in a plugin-side map keyed by PathType,
 * because Bukkit's Mob has no getPathfindingMalus() equivalent. Use
 * {@link #setPathfindingMalus} / {@link #getPathfindingMalus} to manage them,
 * or pre-populate {@code pathfindingMalusMap} before calling {@link #prepare}.
 */
public abstract class NodeEvaluator {
    protected PathfindingContext currentContext;
    protected Navigation navigation;
    protected final Int2ObjectMap<Node> nodes = new Int2ObjectOpenHashMap<>();
    protected int entityWidth;
    protected int entityHeight;
    protected int entityDepth;
    protected boolean canPassDoors = true;
    protected boolean canOpenDoors;
    protected boolean canFloat;
    protected boolean canWalkOverFences;

    /**
     * Per-entity overrides for path-type traversal cost.
     * Keyed by PathType ordinal. Values < 0 mean impassable.
     * Defaults to each PathType's built-in malus when not overridden.
     */
    private final java.util.EnumMap<PathType, Float> pathfindingMalusMap = new java.util.EnumMap<>(PathType.class);

    public void prepare(final World world, final Navigation navigation) {
        this.currentContext = new PathfindingContext(world, navigation);
        this.navigation = navigation;
        this.nodes.clear();
        Vector hitbox = navigation.getHitbox();
        this.entityWidth  = (int) Math.floor(hitbox.getX() + 1.0);
        this.entityHeight = (int) Math.floor(hitbox.getY() + 1.0);
        this.entityDepth  = (int) Math.floor(hitbox.getZ() + 1.0);
    }

    public void done() {
        this.currentContext = null;
        this.navigation = null;
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

    public abstract PathType getPathTypeOfMob(PathfindingContext context, int x, int y, int z, Navigation navigation);

    public abstract PathType getPathType(PathfindingContext context, int x, int y, int z);

    public PathType getPathType(final Navigation navigation, final Location pos) {
        return getPathType(new PathfindingContext(pos.getWorld(), navigation), pos.getBlockX(), pos.getBlockY(), pos.getBlockZ());
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