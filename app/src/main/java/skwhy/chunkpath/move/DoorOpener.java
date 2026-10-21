package skwhy.chunkpath.move;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Door;

import skwhy.chunkpath.move.pathcalculator.Node;
import skwhy.chunkpath.move.pathcalculator.Path;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Opens the (hand-openable) doors on the path of a {@link LongMob} just before it walks through them
 * and closes them again once it has passed — like a villager would. Only real doors are handled:
 * iron doors need redstone, fence gates and trapdoors are never opened (the path finder routes
 * around those, see {@code CostRuleWalkNodeEvaluator}).
 */
public final class DoorOpener {

    /** Horizontal distance (blocks, door block centre) under which a door on the path is opened. */
    private static final double OPEN_RANGE = 2.0;
    /** The mob has "reached" the door when it comes this close to it. */
    private static final double REACH_RANGE = 1.3;
    /** Once reached, the door closes as soon as the mob is further than this. */
    private static final double CLOSE_RANGE = 1.8;
    /** A door the mob never reached (path changed...) is closed after this many ticks once the mob is away. */
    private static final long GIVE_UP_TICKS = 200L;
    private static final double OCCUPIED_RANGE = 0.8;
    /** How many upcoming path nodes are inspected for closed doors. */
    private static final int LOOK_AHEAD_NODES = 2;

    private static final class Opened {
        final Block lower;
        final long since;
        boolean reached;

        Opened(Block lower, long since) {
            this.lower = lower;
            this.since = since;
        }
    }

    private final LongMob mob;
    private final List<Opened> opened = new ArrayList<>();

    public DoorOpener(LongMob mob) {
        this.mob = mob;
    }

    /** Door the entity itself can open: any door but the iron one. */
    public static boolean isHandDoor(Block block) {
        return block.getBlockData() instanceof Door && block.getType() != Material.IRON_DOOR;
    }

    public int openedCount() {
        return opened.size();
    }

    /** One tick while the mob walks: opens the doors ahead (if allowed), closes the ones left behind. */
    public void tick(Path path) {
        World world = mob.getWorld();
        long now = world.getGameTime();
        closePassed(now, false);

        if (!mob.canOpenDoors() || path == null || path.isDone()) return;
        int last = Math.min(path.getNodeCount() - 1, path.getNextNodeIndex() + LOOK_AHEAD_NODES - 1);
        for (int i = path.getNextNodeIndex(); i <= last; i++) {
            Node node = path.getNode(i);
            if (!world.isChunkLoaded(node.x >> 4, node.z >> 4)) continue;
            for (int dy = 0; dy <= 1; dy++) {
                Block block = world.getBlockAt(node.x, node.y + dy, node.z);
                if (!isHandDoor(block) || isOpen(block) || horizontalDistance(block) > OPEN_RANGE) continue;
                open(block, now);
            }
        }
    }

    /** Closes the doors left behind while the navigation is not moving (arrived, failed...). */
    public void tickIdle() {
        if (opened.isEmpty()) return;
        closePassed(mob.getWorld().getGameTime(), true);
    }

    /** Closes every door this mob opened (navigation destroyed), except the one it is standing in. */
    public void closeAll() {
        for (Iterator<Opened> it = opened.iterator(); it.hasNext(); ) {
            Opened o = it.next();
            if (!occupied(o.lower)) {
                setOpen(o.lower, false);
                it.remove();
            }
        }
    }

    private void closePassed(long now, boolean idle) {
        for (Iterator<Opened> it = opened.iterator(); it.hasNext(); ) {
            Opened o = it.next();
            if (!isHandDoor(o.lower) || !isOpen(o.lower)) { // closed or broken by someone else
                it.remove();
                continue;
            }
            double distance = horizontalDistance(o.lower);
            if (distance < REACH_RANGE) o.reached = true;
            boolean passed = o.reached && distance > CLOSE_RANGE;
            boolean forgotten = now - o.since > GIVE_UP_TICKS && distance > CLOSE_RANGE;
            if ((passed || forgotten || idle && distance > CLOSE_RANGE) && !occupied(o.lower)) {
                setOpen(o.lower, false);
                it.remove();
            }
        }
    }

    private boolean occupied(Block lower) {
        return horizontalDistance(lower) < OCCUPIED_RANGE
                && mob.getY() > lower.getY() - 1.0 && mob.getY() < lower.getY() + 2.0;
    }

    private double horizontalDistance(Block block) {
        double dx = mob.getX() - (block.getX() + 0.5);
        double dz = mob.getZ() - (block.getZ() + 0.5);
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static boolean isOpen(Block block) {
        return block.getBlockData() instanceof Door door && door.isOpen();
    }

    private void open(Block block, long now) {
        Door door = (Door) block.getBlockData();
        Block lower = door.getHalf() == Door.Half.BOTTOM ? block : block.getRelative(BlockFace.DOWN);
        if (!setOpen(lower, true)) return;
        for (Opened o : opened) {
            if (o.lower.equals(lower)) return;
        }
        opened.add(new Opened(lower, now));
    }

    /** Sets both halves of the door; false when {@code lower} is no longer a door. */
    private boolean setOpen(Block lower, boolean open) {
        Block upper = lower.getRelative(BlockFace.UP);
        boolean changed = false;
        for (Block half : new Block[] { lower, upper }) {
            if (half.getBlockData() instanceof Door door && door.isOpen() != open) {
                door.setOpen(open);
                half.setBlockData(door, false);
                changed = true;
            }
        }
        if (changed) {
            Location at = lower.getLocation().add(0.5, 0.5, 0.5);
            lower.getWorld().playSound(at, soundOf(lower.getType(), open), SoundCategory.BLOCKS, 1.0F,
                    0.9F + (float) Math.random() * 0.1F);
        }
        return lower.getBlockData() instanceof Door;
    }

    private static String soundOf(Material material, boolean open) {
        String name = material.name();
        String family = name.contains("COPPER") ? "copper_door"
                : name.startsWith("CRIMSON") || name.startsWith("WARPED") ? "nether_wood_door"
                : name.startsWith("BAMBOO") ? "bamboo_wood_door"
                : name.startsWith("CHERRY") ? "cherry_wood_door"
                : "wooden_door";
        return "block." + family + (open ? ".open" : ".close");
    }
}
