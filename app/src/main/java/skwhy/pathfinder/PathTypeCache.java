package skwhy.pathfinder;

import it.unimi.dsi.fastutil.HashCommon;
import org.bukkit.Location;
import org.bukkit.World;
import org.jspecify.annotations.Nullable;

/**
 * Thread-unsafe flat hash map cache from block position to PathType.
 * Used to avoid recomputing path types for blocks visited multiple times per tick.
 *
 * <p>Adapted from net.minecraft.world.level.pathfinder.PathTypeCache:
 * - net.minecraft.core.BlockPos replaced by int coordinates
 * - net.minecraft.world.level.BlockGetter replaced by org.bukkit.World
 * - WalkNodeEvaluator.getPathTypeFromState is the static lookup delegate (unchanged contract)
 */
public class PathTypeCache {
    private static final int SIZE = 4096;
    private static final int MASK = 4095;
    private final long[] positions = new long[SIZE];
    private final PathType[] pathTypes = new PathType[SIZE];

    public PathType getOrCompute(final World world, final int x, final int y, final int z) {
        long key = asLong(x, y, z);
        int index = index(key);
        PathType cached = get(index, key);
        return cached != null ? cached : compute(world, x, y, z, index, key);
    }

    private @Nullable PathType get(final int index, final long key) {
        return this.positions[index] == key ? this.pathTypes[index] : null;
    }

    private PathType compute(final World world, final int x, final int y, final int z, final int index, final long key) {
        PathType pathType = WalkNodeEvaluator.getPathTypeFromState(world, x, y, z);
        this.positions[index] = key;
        this.pathTypes[index] = pathType;
        return pathType;
    }

    public void invalidate(final int x, final int y, final int z) {
        long key = asLong(x, y, z);
        int index = index(key);
        if (this.positions[index] == key) {
            this.pathTypes[index] = null;
        }
    }

    /** Packs block coordinates into a single long (same scheme as BlockPos.asLong). */
    public static long asLong(final int x, final int y, final int z) {
        return ((long) x & 0x3FFFFFFL) | (((long) y & 0xFFFL) << 26) | (((long) z & 0x3FFFFFFL) << 38);
    }

    private static int index(final long pos) {
        return (int) HashCommon.mix(pos) & MASK;
    }
}