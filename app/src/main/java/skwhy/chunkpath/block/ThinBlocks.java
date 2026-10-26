package skwhy.chunkpath.block;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.TrapDoor;
import org.bukkit.util.BoundingBox;

import java.util.Collection;

/**
 * Blocks with a collision shape at most {@link #MAX_THICKNESS} thick (carpet, snow layers, a closed trapdoor, a
 * repeater...) lying on the floor of their cell, or stuck to its ceiling. The pathfinding does not see them as
 * floor, wall or hole: the cell stays walkable and costs what the floor under it costs. They are only taken into
 * account by the geometry of the body (a step that is too high, a ceiling that leaves too little room).
 *
 * <p>The thickness is the vertical extent: an open trapdoor or a fence gate is a panel one block high, a real obstacle.
 */
public final class ThinBlocks {

    /** Inclusive: a closed trapdoor (0.1875), snow up to 3 layers (0.25). */
    public static final double MAX_THICKNESS = 0.25;
    private static final double EPSILON = 1.0E-3;

    private ThinBlocks() {
    }

    /** True when every box of the shape is thin and rests on the bottom of the cell or hangs from its top. */
    public static boolean isThin(final Collection<BoundingBox> boxes) {
        if (boxes.isEmpty()) return false;
        for (BoundingBox box : boxes) {
            if (box.getMaxY() - box.getMinY() > MAX_THICKNESS + EPSILON) return false;
            final boolean onFloor = box.getMinY() <= EPSILON;
            final boolean onCeiling = box.getMaxY() >= 1.0 - EPSILON;
            if (!onFloor && !onCeiling) return false;
        }
        return true;
    }

    /** True when the block has a collision shape and that shape is thin. */
    public static boolean isThin(final Block block) {
        final Material material = block.getType();
        if (material.isAir()) return false;
        return isThin(block.getCollisionShape().getBoundingBoxes());
    }

    /**
     * A closed trapdoor in the top half of its cell: it hangs from the ceiling of the cell (see {@link #isThin}) and its top
     * is a floor the entity can walk on in the cell above.
     */
    public static boolean isCeilingTrapdoor(final Block block) {
        if (!Tag.TRAPDOORS.isTagged(block.getType())) return false;
        final BlockData data = block.getBlockData();
        return data instanceof TrapDoor trapDoor && !trapDoor.isOpen() && trapDoor.getHalf() == Bisected.Half.TOP;
    }

    /** Carpets, by name: the stored chunk analysis only has the block name, not its state. */
    public static boolean isCarpet(final Material material) {
        return material.name().endsWith("_CARPET");
    }
}
