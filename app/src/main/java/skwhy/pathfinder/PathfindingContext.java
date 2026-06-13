package skwhy.pathfinder;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Mob;

/**
 * Holds the world reference and per-pathfinding-session cache used by node evaluators.
 *
 * <p>Adapted from net.minecraft.world.level.pathfinder.PathfindingContext:
 * - PathNavigationRegion / CollisionGetter replaced by org.bukkit.World
 * - ServerLevel.getPathTypeCache() replaced by a locally owned PathTypeCache
 *   (Bukkit has no built-in equivalent; the cache is created fresh each session
 *   and discarded after NodeEvaluator#done() is called)
 * - MutableBlockPos replaced by plain int parameters to avoid allocation
 */
public class PathfindingContext {
    private final World world;
    private final PathTypeCache cache;
    /** Block position of the mob at the start of pathfinding (used to skip fence checks at the mob's own position). */
    private final int mobX;
    private final int mobY;
    private final int mobZ;

    public PathfindingContext(final World world, final Mob mob) {
        this.world = world;
        this.cache = new PathTypeCache();
        Location pos = mob.getLocation();
        this.mobX = pos.getBlockX();
        this.mobY = pos.getBlockY();
        this.mobZ = pos.getBlockZ();
    }

    /**
     * Returns the PathType for a single block position, using the session cache.
     */
    public PathType getPathTypeFromState(final int x, final int y, final int z) {
        return this.cache.getOrCompute(this.world, x, y, z);
    }

    /** Returns the Bukkit Block at the given coordinates. */
    public Block getBlock(final int x, final int y, final int z) {
        return this.world.getBlockAt(x, y, z);
    }

    public World world() {
        return this.world;
    }

    public int mobX() { return this.mobX; }
    public int mobY() { return this.mobY; }
    public int mobZ() { return this.mobZ; }

    /** Returns true if the given coordinates are the mob's own starting position. */
    public boolean isMobPosition(final int x, final int y, final int z) {
        return x == this.mobX && y == this.mobY && z == this.mobZ;
    }
}