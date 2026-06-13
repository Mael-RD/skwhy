package skwhy.data;

import ch.njol.skript.Skript;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRelativeMoveAndRotation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Openable;
import org.bukkit.block.data.type.Slab;
import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * FakePathFinding — entité virtuelle (ou réelle) guidée par un A* calqué sur
 * l'algorithme vanilla de Minecraft.
 */
public class FakePathFinding {

    // =========================================================================
    // Enums publics
    // =========================================================================

    public enum PathfindingType {
        WALK, WALK_WATER, SWIM, FLY, FLY_GROUND, CLIMB, NONE
    }

    // =========================================================================
    // PathType
    // =========================================================================

    private enum PathType {
        BLOCKED(Float.MAX_VALUE), OPEN(0f), WALKABLE(0f), WALKABLE_DOOR(0f),
        TRAPDOOR(0f), FENCE(Float.MAX_VALUE), LAVA(Float.MAX_VALUE),
        WATER(8f), WATER_BORDER(8f), DOOR_WOOD_CLOSED(Float.MAX_VALUE),
        DOOR_IRON_CLOSED(Float.MAX_VALUE), DOOR_OPEN(0f), STICKY_HONEY(8f),
        POWDER_SNOW(Float.MAX_VALUE), DAMAGE_FIRE(16f), DAMAGE_CACTUS(16f),
        DAMAGE_OTHER(16f);

        final float malus;
        PathType(float malus) { this.malus = malus; }
    }

    // =========================================================================
    // Node
    // =========================================================================

    private static final class Node {
        final int x, y, z;
        float g, h, f;
        Node cameFrom;
        boolean closed;
        int heapIdx = -1;
        PathType type = PathType.OPEN;

        Node(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }

        float heuristic(Node target) {
            float dx = Math.abs(this.x - target.x);
            float dy = Math.abs(this.y - target.y);
            float dz = Math.abs(this.z - target.z);
            float max = Math.max(dx, Math.max(dy, dz));
            float min = Math.min(dx, Math.min(dy, dz));
            float mid = dx + dy + dz - max - min;
            return max + (float)(Math.sqrt(3) - 2) * min + (float)(Math.sqrt(2) - 1) * mid;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Node n)) return false;
            return x == n.x && y == n.y && z == n.z;
        }

        @Override
        public int hashCode() { return Objects.hash(x, y, z); }
    }

    // =========================================================================
    // BinaryHeap min-heap
    // =========================================================================

    private static final class BinaryHeap {
        private final List<Node> heap = new ArrayList<>();

        boolean isEmpty() { return heap.isEmpty(); }

        void insert(Node node) {
            node.heapIdx = heap.size();
            heap.add(node);
            siftUp(node.heapIdx);
        }

        Node poll() {
            Node min = heap.get(0);
            Node last = heap.remove(heap.size() - 1);
            min.heapIdx = -1;
            if (!heap.isEmpty()) {
                heap.set(0, last);
                last.heapIdx = 0;
                siftDown(0);
            }
            return min;
        }

        void changeCost(Node node) { siftUp(node.heapIdx); }

        boolean contains(Node node) { return node.heapIdx >= 0; }

        private void siftUp(int i) {
            Node node = heap.get(i);
            while (i > 0) {
                int parent = (i - 1) >> 1;
                Node p = heap.get(parent);
                if (node.f >= p.f) break;
                swap(i, parent);
                i = parent;
            }
        }

        private void siftDown(int i) {
            int size = heap.size();
            while (true) {
                int left = (i << 1) + 1;
                int right = left + 1;
                int best = i;
                if (left < size && heap.get(left).f < heap.get(best).f) best = left;
                if (right < size && heap.get(right).f < heap.get(best).f) best = right;
                if (best == i) break;
                swap(i, best);
                i = best;
            }
        }

        private void swap(int a, int b) {
            Node na = heap.get(a);
            Node nb = heap.get(b);
            heap.set(a, nb);
            heap.set(b, na);
            na.heapIdx = b;
            nb.heapIdx = a;
        }
    }

    // =========================================================================
    // NodeEvaluator abstrait
    // =========================================================================

    private abstract class NodeEvaluator {
        protected World world;

        void prepare(World world) { this.world = world; }

        abstract Node getStart(double x, double y, double z);
        abstract Node getGoal(int x, int y, int z);
        abstract int getNeighbors(Node[] buffer, Node node);

        Node makeNode(int x, int y, int z) { return new Node(x, y, z); }

        double halfWidth() { return hitbox != null ? hitbox.getX() / 2.0 : 0.3; }
        double entityHeight() { return hitbox != null ? hitbox.getY() : 1.8; }

        /** Correction : Utilisation de getBlockTopY pour s'assurer que la hitbox de l'entité ne heurte pas la dalle / le carpet */
        boolean hasEnoughSpace(double cx, double y, double cz) {
            double hw = halfWidth();
            double eh = entityHeight();
            int x0 = (int) Math.floor(cx - hw);
            int x1 = (int) Math.floor(cx + hw - 1e-7);
            int y0 = (int) Math.floor(y + 1e-4);
            int y1 = (int) Math.floor(y + eh - 1e-7);
            int z0 = (int) Math.floor(cz - hw);
            int z1 = (int) Math.floor(cz + hw - 1e-7);

            for (int bx = x0; bx <= x1; bx++) {
                for (int by = y0; by <= y1; by++) {
                    for (int bz = z0; bz <= z1; bz++) {
                        Block b = world.getBlockAt(bx, by, bz);
                        if (isSolidForMovement(b)) {
                            double blockTop = getBlockTopY(b, by);
                            if (blockTop > y + 1e-4) return false;
                        }
                    }
                }
            }
            return true;
        }

        double findWalkableY(int nx, int startY, int nz, int dropMax) {
            for (int dy = 0; dy <= dropMax; dy++) {
                int by = startY - dy;
                if (by < world.getMinHeight()) return Double.NaN;
                double surfY = blockSurfaceY(nx, by, nz);
                if (!Double.isNaN(surfY) && hasEnoughSpace(nx + 0.5, surfY, nz + 0.5))
                    return surfY;
                if (dy > 0 && isSolidForMovement(world.getBlockAt(nx, by, nz))) return Double.NaN;
            }
            return Double.NaN;
        }

        /** Correction : Logique repensée pour utiliser de manière exacte la fonction getBlockTopY */
        double blockSurfaceY(int bx, int by, int bz) {
            Block self = world.getBlockAt(bx, by, bz);
            if (isSolidForMovement(self)) {
                if (self.getBlockData() instanceof Slab slab && slab.getType() == Slab.Type.BOTTOM) return by + 0.5;
                return Double.NaN;
            }
            if (Tag.WOOL_CARPETS.isTagged(self.getType()) || self.getType() == Material.MOSS_CARPET) {
                return by + 0.0625;
            }

            Block floor = world.getBlockAt(bx, by - 1, bz);
            double floorTop = getBlockTopY(floor, by - 1);
            if (floorTop > Double.NEGATIVE_INFINITY) return floorTop;

            return Double.NaN;
        }

        PathType getPathType(int bx, int by, int bz) {
            Block b = world.getBlockAt(bx, by, bz);
            Material mat = b.getType();

            if (mat == Material.AIR || mat == Material.CAVE_AIR || mat == Material.VOID_AIR) return PathType.OPEN;
            if (mat == Material.WATER) return PathType.WATER;
            if (mat == Material.LAVA) return PathType.LAVA;
            if (mat == Material.POWDER_SNOW) return PathType.POWDER_SNOW;
            if (mat == Material.FIRE || mat == Material.SOUL_FIRE) return PathType.DAMAGE_FIRE;
            if (mat == Material.CACTUS || mat == Material.SWEET_BERRY_BUSH) return PathType.DAMAGE_CACTUS;
            if (mat == Material.HONEY_BLOCK) return PathType.STICKY_HONEY;

            if (isWoodenDoor(mat)) {
                if (b.getBlockData() instanceof Openable op && op.isOpen()) return PathType.DOOR_OPEN;
                return PathType.DOOR_WOOD_CLOSED;
            }
            if (mat == Material.IRON_DOOR) {
                if (b.getBlockData() instanceof Openable op && op.isOpen()) return PathType.DOOR_OPEN;
                return PathType.DOOR_IRON_CLOSED;
            }
            if (isTrapdoor(mat)) return PathType.TRAPDOOR;
            if (isFence(mat)) return PathType.FENCE;

            if (isSolidForMovement(b)) return PathType.BLOCKED;
            if (isNonSolidPassable(mat)) return PathType.OPEN;

            return PathType.WALKABLE;
        }

        boolean isSolidForMovement(Block b) {
            Material mat = b.getType();
            if (mat.isAir() || mat == Material.WATER || mat == Material.LAVA) return false;
            if (Tag.FLOWERS.isTagged(mat) || Tag.CROPS.isTagged(mat) || Tag.WOOL_CARPETS.isTagged(mat)) return false;
            switch (mat) {
                case MOSS_CARPET, TALL_GRASS, SHORT_GRASS, FERN, DEAD_BUSH, VINE,
                     SEAGRASS, TALL_SEAGRASS, TORCH, WALL_TORCH, SOUL_TORCH, SOUL_WALL_TORCH,
                     REDSTONE_WIRE, REPEATER, COMPARATOR, LEVER, TRIPWIRE, STRING,
                     RAIL, POWERED_RAIL, DETECTOR_RAIL, ACTIVATOR_RAIL, SNOW, SUGAR_CANE:
                    return false;
                default: break;
            }
            if ((isWoodenDoor(mat) || mat == Material.IRON_DOOR || isTrapdoor(mat))) {
                if (b.getBlockData() instanceof Openable op && op.isOpen()) return false;
            }
            return mat.isSolid();
        }

        boolean isClimbable(Block b) {
            Material mat = b.getType();
            return mat == Material.LADDER || mat == Material.VINE
                || mat == Material.WEEPING_VINES || mat == Material.WEEPING_VINES_PLANT
                || mat == Material.TWISTING_VINES || mat == Material.TWISTING_VINES_PLANT
                || mat == Material.CAVE_VINES || mat == Material.CAVE_VINES_PLANT
                || mat == Material.SCAFFOLDING;
        }

        boolean isNonSolidPassable(Material mat) {
            if (Tag.FLOWERS.isTagged(mat) || Tag.CROPS.isTagged(mat)) return true;
            switch (mat) {
                case TALL_GRASS, SHORT_GRASS, FERN, DEAD_BUSH, VINE, SEAGRASS, TALL_SEAGRASS,
                     TORCH, WALL_TORCH, SOUL_TORCH, SOUL_WALL_TORCH,
                     REDSTONE_WIRE, REPEATER, COMPARATOR, LEVER, TRIPWIRE, STRING,
                     RAIL, POWERED_RAIL, DETECTOR_RAIL, ACTIVATOR_RAIL, SNOW, SUGAR_CANE:
                    return true;
                default: return false;
            }
        }

        boolean isWater(Block b) { return b.getType() == Material.WATER; }
        boolean isWoodenDoor(Material mat) { return Tag.WOODEN_DOORS.isTagged(mat); }
        boolean isTrapdoor(Material mat) { return Tag.TRAPDOORS.isTagged(mat); }
        boolean isFence(Material mat) { return Tag.FENCES.isTagged(mat) || Tag.FENCE_GATES.isTagged(mat) || Tag.WALLS.isTagged(mat); }
    }

    // =========================================================================
    // WalkNodeEvaluator
    // =========================================================================

    private final class WalkNodeEvaluator extends NodeEvaluator {

        private static final double JUMP_HEIGHT = 1.125;
        private static final int MAX_DROP = 4;

        @Override
        Node getStart(double x, double y, double z) {
            int bx = (int) Math.floor(x);
            int by = (int) Math.floor(y);
            int bz = (int) Math.floor(z);
            if (isWater(world.getBlockAt(bx, by, bz))) return makeNode(bx, by, bz);
            int floor = (int) Math.floor(findFloorY(x, y, z));
            return makeNode(bx, floor, bz);
        }

        @Override
        Node getGoal(int x, int y, int z) { return makeNode(x, y, z); }

        @Override
        int getNeighbors(Node[] buf, Node node) {
            int count = 0;
            int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1},{1,1},{1,-1},{-1,1},{-1,-1}};

            for (int[] d : dirs) {
                int nx = node.x + d[0];
                int nz = node.z + d[1];

                if (d[0] != 0 && d[1] != 0) {
                    boolean xWalkable = canWalkOrJumpTo(node.x + d[0], node.y, node.z, node);
                    boolean zWalkable = canWalkOrJumpTo(node.x, node.y, node.z + d[1], node);
                    if (!xWalkable || !zWalkable) continue;
                }

                Node flat = evaluateWalkNode(nx, node.y, nz);
                if (flat != null) { buf[count++] = flat; continue; }

                if (hasClearanceAbove(node.x, node.y, node.z)) {
                    Node jump = evaluateWalkNode(nx, node.y + 1, nz);
                    // Vérification du couloir de transit lors d'une montée :
                    // l'entité doit pouvoir passer à la hauteur intermédiaire (pied courant → pied+1)
                    // dans la colonne de départ ET dans la colonne d'arrivée.
                    if (jump != null && hasTransitClearance(node.x, node.y, node.z, nx, jump.y, nz)) {
                        buf[count++] = jump; continue;
                    }
                }

                for (int drop = 1; drop <= MAX_DROP; drop++) {
                    int checkY = node.y - drop;
                    Node dropped = evaluateWalkNode(nx, checkY, nz);
                    if (dropped != null) {
                        // Vérification du couloir de transit lors d'une descente :
                        // l'entité doit pouvoir traverser les blocs intermédiaires côté source.
                        if (hasTransitClearance(node.x, node.y, node.z, nx, dropped.y, nz)) {
                            buf[count++] = dropped;
                        }
                        break;
                    }
                    if (isSolidForMovement(world.getBlockAt(nx, checkY, nz))) break;
                }
            }
            return count;
        }

        private boolean canWalkOrJumpTo(int nx, int nodeY, int nz, Node node) {
            if (evaluateWalkNode(nx, nodeY, nz) != null) return true;
            if (hasClearanceAbove(node.x, nodeY, node.z)) {
                if (evaluateWalkNode(nx, nodeY + 1, nz) != null) return true;
            }
            return false;
        }

        private Node evaluateWalkNode(int nx, int startY, int nz) {
            double footY = findWalkableY(nx, startY, nz, 1);
            if (Double.isNaN(footY)) return null;

            int footBlock = (int) Math.floor(footY);
            int spaceBlock = (int) Math.floor(footY + 0.6);

            PathType pt = getPathType(nx, spaceBlock, nz);
            if (pt == PathType.BLOCKED || pt == PathType.FENCE) return null;
            if (pt == PathType.LAVA || pt == PathType.POWDER_SNOW || pt == PathType.DOOR_IRON_CLOSED) return null;

            Node n = makeNode(nx, footBlock, nz);
            n.type = pt;
            return n;
        }

        private boolean hasClearanceAbove(int bx, int by, int bz) {
            double eh = entityHeight();
            // Le premier bloc occupé par la tête d'une entité debout sur le sol `by` est
            // floor(by + eh - 1e-7). Ex : eh=1.8, by=64 → tête touche bloc 65.
            // Pour pouvoir monter d'1 bloc, la tête irait jusqu'à (by+1)+eh = by+2.8 → bloc 66.
            // On vérifie donc les blocs [by + floor(eh - 1e-7) .. by + floor(eh - 1e-7) + 1].
            int headBlock = by + (int) Math.floor(eh - 1e-7); // bloc de tête actuel (ex: 65)
            int jumpHead  = headBlock + 1;                     // bloc de tête après le saut  (ex: 66)
            for (int y = headBlock; y <= jumpHead; y++) {
                if (isSolidForMovement(world.getBlockAt(bx, y, bz))) return false;
            }
            return true;
        }

        /**
         * Vérifie que le "couloir" de transit entre le nœud courant (srcX, srcY, srcZ)
         * et le nœud cible (dstX, dstY, dstZ) est libre sur toute la hauteur nécessaire.
         *
         * Pour une montée (dstY > srcY) : l'entité part du sol srcY et doit franchir
         * les blocs à hauteur srcY..dstY dans la colonne source ET dans la colonne destination.
         * Pour une descente (dstY < srcY) : l'entité part du sol srcY et doit traverser
         * les blocs à hauteur dstY..srcY dans la colonne source (elle tombe librement côté dest).
         */
        /**
         * Vérifie que le couloir de transit entre (srcX,srcY,srcZ) et (dstX,dstY,dstZ) est libre.
         *
         * Règle de calcul des blocs occupés :
         *   Une entité debout sur le sol `y` occupe les blocs de y à floor(y + eh - 1e-7) inclus.
         *   Ex : eh=1.8, sol=64 → blocs 64 et 65 occupés (tête au bloc 65).
         *
         * Montée (dstY = srcY+1) :
         *   - Pendant le saut la tête passe par les blocs srcHead et srcHead+1 dans la colonne SOURCE.
         *   - À destination la tête occupe les blocs jusqu'à dstHead = floor(dstY + eh - 1e-7).
         *   - On vérifie ces blocs dans les deux colonnes.
         *
         * Descente (dstY < srcY) :
         *   - L'entité marche sur srcY jusqu'au bord : la tête est au bloc srcHead.
         *   - On vérifie la colonne SOURCE depuis dstY jusqu'à srcHead (espace de marche + tête).
         */
        private boolean hasTransitClearance(int srcX, int srcY, int srcZ, int dstX, int dstY, int dstZ) {
            double eh = entityHeight();
            // Bloc de tête quand l'entité est debout sur le sol donné
            int srcHead = srcY + (int) Math.floor(eh - 1e-7);
            int dstHead = dstY + (int) Math.floor(eh - 1e-7);

            if (dstY > srcY) {
                // Montée d'1 bloc : pendant le saut la tête monte de srcHead jusqu'à dstHead.
                // Vérifier colonne SOURCE (de srcHead à srcHead+1) et colonne DEST (de dstY à dstHead).
                for (int by = srcHead; by <= srcHead + 1; by++) {
                    if (isSolidForMovement(world.getBlockAt(srcX, by, srcZ))) return false;
                }
                for (int by = dstY; by <= dstHead; by++) {
                    if (isSolidForMovement(world.getBlockAt(dstX, by, dstZ))) return false;
                }
            } else if (dstY < srcY) {
                // Descente : l'entité approche du bord depuis srcY, tête au bloc srcHead.
                // La colonne source doit être libre de dstY jusqu'à srcHead.
                for (int by = dstY; by <= srcHead; by++) {
                    if (isSolidForMovement(world.getBlockAt(srcX, by, srcZ))) return false;
                }
            }
            return true;
        }

        private double findFloorY(double x, double y, double z) {
            int bx = (int) Math.floor(x);
            int bz = (int) Math.floor(z);
            for (int by = (int) Math.floor(y); by >= world.getMinHeight(); by--) {
                double s = blockSurfaceY(bx, by, bz);
                if (!Double.isNaN(s)) return s;
            }
            return world.getMinHeight();
        }
    }

    // =========================================================================
    // FlyNodeEvaluator
    // =========================================================================

    private final class FlyNodeEvaluator extends NodeEvaluator {
        @Override Node getStart(double x, double y, double z) { return makeNode((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)); }
        @Override Node getGoal(int x, int y, int z) { return makeNode(x, y, z); }
        @Override
        int getNeighbors(Node[] buf, Node node) {
            int count = 0;
            boolean groundHug = pathfindingType == PathfindingType.FLY_GROUND;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        if (groundHug && dy > 0) continue;
                        int nx = node.x + dx, ny = node.y + dy, nz = node.z + dz;
                        Block b = world.getBlockAt(nx, ny, nz);
                        if (isSolidForMovement(b) || !hasEnoughSpace(nx + 0.5, ny, nz + 0.5)) continue;
                        Node n = makeNode(nx, ny, nz);
                        n.type = getPathType(nx, ny, nz);
                        buf[count++] = n;
                    }
                }
            }
            if (groundHug && count == 0) {
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    int nx = node.x + dx; int ny = node.y + 1; int nz = node.z + dz;
                    Block b = world.getBlockAt(nx, ny, nz);
                    if (!isSolidForMovement(b) && hasEnoughSpace(nx + 0.5, ny, nz + 0.5)) {
                        Node n = makeNode(nx, ny, nz);
                        n.type = getPathType(nx, ny, nz);
                        buf[count++] = n;
                    }
                }
            }
            return count;
        }
    }

    // =========================================================================
    // SwimNodeEvaluator
    // =========================================================================

    private final class SwimNodeEvaluator extends NodeEvaluator {
        @Override Node getStart(double x, double y, double z) { return makeNode((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)); }
        @Override Node getGoal(int x, int y, int z) { return makeNode(x, y, z); }
        @Override
        int getNeighbors(Node[] buf, Node node) {
            int count = 0;
            int[][] dirs = {{1,0,0},{-1,0,0},{0,0,1},{0,0,-1},{0,1,0},{0,-1,0}};
            for (int[] d : dirs) {
                int nx = node.x + d[0], ny = node.y + d[1], nz = node.z + d[2];
                Block b = world.getBlockAt(nx, ny, nz);
                if (!isWater(b) || isSolidForMovement(b)) continue;
                Node n = makeNode(nx, ny, nz);
                n.type = PathType.WATER;
                buf[count++] = n;
            }
            return count;
        }
    }

    // =========================================================================
    // Constantes
    // =========================================================================

    public static final int PAUSED_INDEFINITELY = -1;
    private static final double ARRIVAL_THRESHOLD    = 0.35;
    private static final int    PATH_RECALC_INTERVAL = 10;
    private static final int    MAX_NODES = 200;
    private static final double GRAVITY_ACCEL  = 0.08;
    private static final double MAX_FALL_SPEED = 3.92;
    private static final double JUMP_IMPULSE     = 0.46;
    private static final double JUMP_MIN_DY      = 0.1;
    private static final double JUMP_MAX_DY      = 1.25;

    // =========================================================================
    // PacketEvents & Registre
    // =========================================================================

    private static final boolean PACKET_EVENTS_AVAILABLE;
    static {
        boolean available = false;
        try { Class.forName("com.github.retrooper.packetevents.PacketEvents"); available = true; } catch (ClassNotFoundException ignored) {}
        PACKET_EVENTS_AVAILABLE = available;
    }

    private static final List<FakePathFinding> REGISTRY = Collections.synchronizedList(new ArrayList<>());
    public static List<FakePathFinding> getRegistry() { return Collections.unmodifiableList(REGISTRY); }
    public static void tickAll() {
        REGISTRY.removeIf(fake -> fake.isRealEntity() && !fake.entity.isValid());
        List<FakePathFinding> snapshot = new ArrayList<>(REGISTRY);
        for (FakePathFinding fake : snapshot) fake.tick();
    }
    private void register()    { REGISTRY.add(this); }
    public  void unregister()  { REGISTRY.remove(this); }
    public  boolean isRegistered() { return REGISTRY.contains(this); }

    // =========================================================================
    // Champs d'instance
    // =========================================================================

    private final int entityId;
    private final Entity entity;
    private Vector hitbox;
    private Location location;
    private double speed;
    private final List<Player> players;
    private PathfindingType pathfindingType;
    private Location destination;
    private int pauseTicks = PAUSED_INDEFINITELY;
    private final Deque<Location> path = new ArrayDeque<>();
    private int ticksSinceRecalc = 0;
    private Location lastPathDest = null;
    private double lastSpeed;
    private boolean usingPartialPath = false;
    private NodeEvaluator walkEvaluator, flyEvaluator, swimEvaluator;
    private double verticalVelocity = 0.0;
    private boolean onGround = false;

    // =========================================================================
    // Constructeurs
    // =========================================================================

    public FakePathFinding(int entityId, Vector hitbox, Location location, double speed, List<Player> players, PathfindingType pathfindingType) {
        if (!PACKET_EVENTS_AVAILABLE) throw new UnsupportedOperationException("[SkWhy] PacketEvents is required for virtual entities.");
        this.entityId = entityId; this.hitbox = hitbox.clone(); this.location = location.clone();
        this.speed = speed; this.players = new ArrayList<>(players); this.pathfindingType = pathfindingType;
        this.lastSpeed = speed; this.entity = null; register();
    }

    public FakePathFinding(Entity entity, double speed, List<Player> players, PathfindingType pathfindingType) {
        this.entityId = entity.getEntityId();
        this.hitbox = new Vector(entity.getWidth(), entity.getHeight(), entity.getWidth());
        this.location = entity.getLocation().clone(); this.speed = speed;
        this.players = new ArrayList<>(players); this.pathfindingType = pathfindingType;
        this.lastSpeed = speed; this.entity = entity;
        REGISTRY.removeIf(e -> e.isRealEntity() && e.entity.getEntityId() == entity.getEntityId());
        register();
    }

    // =========================================================================
    // Tick principal
    // =========================================================================

    public void tick() {
        if (pauseTicks == PAUSED_INDEFINITELY) return;
        if (pauseTicks > 0) { pauseTicks--; return; }
        if (destination == null) { applyGravity(); return; }

        if (getLocation().distanceSquared(destination) <= ARRIVAL_THRESHOLD * ARRIVAL_THRESHOLD) {
            path.clear(); destination = null; usingPartialPath = false; applyGravity(); return;
        }

        boolean needsRecalc = path.isEmpty() || ticksSinceRecalc >= PATH_RECALC_INTERVAL
            || !isSameBlock(lastPathDest, destination) || lastSpeed != speed;

        if (needsRecalc) { recalculatePath(); ticksSinceRecalc = 0; lastSpeed = speed; } 
        else { ticksSinceRecalc++; }

        if (path.isEmpty()) {
            if (usingPartialPath) { destination = null; usingPartialPath = false; }
            applyGravity(); return;
        }

        Location next = path.peek();
        if (getLocation().distanceSquared(next) <= ARRIVAL_THRESHOLD * ARRIVAL_THRESHOLD) {
            path.poll();
            if (path.isEmpty()) {
                if (usingPartialPath) { destination = null; usingPartialPath = false; }
                applyGravity(); return;
            }
            next = path.peek();
        }

        Location prev = getLocation().clone();
        Vector horiz = new Vector(next.getX() - prev.getX(), 0, next.getZ() - prev.getZ());
        double hDist = horiz.length();
        Location newLoc = prev.clone();
        if (hDist > 0.001) {
            if (hDist <= speed) { newLoc.setX(next.getX()); newLoc.setZ(next.getZ()); }
            else { horiz.normalize().multiply(speed); newLoc.add(horiz.getX(), 0, horiz.getZ()); }
        }

        newLoc = resolveHorizontalCollision(prev, newLoc);

        double dy = next.getY() - getLocation().getY();
        boolean stepped = false;

        if (onGround && verticalVelocity <= 0) {
            World stepWorld = newLoc.getWorld();
            if (stepWorld != null) {
                double scanFrom = prev.getY() + 0.6;
                double newFloor = findFloorYAabb(stepWorld, newLoc.getX(), scanFrom, newLoc.getZ(), scanFrom);
                double stepDy   = newFloor - prev.getY();
                if (stepDy > 1e-4 && stepDy <= 0.5) {
                    double ceilY = findCeilingYAabb(stepWorld, newLoc.getX(), newFloor, newLoc.getZ());
                    if (newFloor + getEntityHeight() <= ceilY) {
                        newLoc.setY(newFloor);
                        verticalVelocity = 0;
                        onGround = true;
                        stepped = true;
                    }
                }
            }
        }

        if (!stepped && shouldJump(dy, prev, next)) verticalVelocity = JUMP_IMPULSE;
        if (!stepped) newLoc = applyGravityToLocation(newLoc);

        double ddx = newLoc.getX() - prev.getX();
        double ddz = newLoc.getZ() - prev.getZ();
        if (Math.abs(ddx) > 0.001 || Math.abs(ddz) > 0.001) {
            newLoc.setYaw((float) Math.toDegrees(Math.atan2(-ddx, ddz)));
            newLoc.setPitch(0f);
        }

        applyNewLocation(newLoc);
        sendMovePacket(prev, getLocation());
    }

    // =========================================================================
    // Physique
    // =========================================================================

    /** Correction : Ajout du check getBlockTopY pour empêcher les sauts infinis causés par des dalles lointaines */
    private boolean shouldJump(double dy, Location from, Location to) {
        if (!onGround || verticalVelocity > 0.0) return false;
        if (dy <= JUMP_MIN_DY || dy > JUMP_MAX_DY) return false;

        World world = from.getWorld();
        if (world == null) return false;

        double hw = hitbox != null ? hitbox.getX() / 2.0 : 0.3;
        double eh = hitbox != null ? hitbox.getY() : 1.8;

        double dxRaw = to.getX() - from.getX();
        double dzRaw = to.getZ() - from.getZ();
        double hLen  = Math.sqrt(dxRaw * dxRaw + dzRaw * dzRaw);
        if (hLen < 0.001) return false;
        double ndx = dxRaw / hLen;
        double ndz = dzRaw / hLen;

        NodeEvaluator ev = getWalkEvaluator();
        int yBase = (int) Math.floor(from.getY());
        // Bloc de tête actuel (floor(from.getY() + eh - 1e-7)). Ex : sol=64, eh=1.8 → bloc 65.
        int headBlock = (int) Math.floor(from.getY() + eh - 1e-7);

        // ── Vérification du plafond AVANT d'autoriser le saut ──────────────────────────
        // Pendant le saut, la tête monte d'1 bloc supplémentaire (headBlock → headBlock+1).
        // Si l'un ou l'autre est solide dans la colonne de départ, on ne peut pas sauter.
        int fromBx = (int) Math.floor(from.getX());
        int fromBz = (int) Math.floor(from.getZ());
        if (ev.isSolidForMovement(world.getBlockAt(fromBx, headBlock,     fromBz))) return false;
        if (ev.isSolidForMovement(world.getBlockAt(fromBx, headBlock + 1, fromBz))) return false;

        // ── Vérification qu'un obstacle horizontal justifie le saut ────────────────────
        // On sonde quelques points sur la trajectoire horizontale ; s'il y a un bloc solide
        // dont le dessus dépasse le sol courant de plus de 0.6 (step height), on doit sauter.
        double[] probeDistances = { hw + 0.1, hLen * 0.5, hLen };
        for (double dist : probeDistances) {
            if (dist > hLen + 0.01) break;
            double probeX = from.getX() + ndx * dist;
            double probeZ = from.getZ() + ndz * dist;
            int bx = (int) Math.floor(probeX);
            int bz = (int) Math.floor(probeZ);
            for (int by = yBase; by <= headBlock; by++) {
                Block b = world.getBlockAt(bx, by, bz);
                if (ev.isSolidForMovement(b) && getBlockTopY(b, by) > from.getY() + 0.6) return true;
            }
        }

        int destBx = (int) Math.floor(to.getX());
        int destBz = (int) Math.floor(to.getZ());
        Block b1 = world.getBlockAt(destBx, yBase, destBz);
        if (ev.isSolidForMovement(b1) && getBlockTopY(b1, yBase) > from.getY() + 0.6) return true;
        Block b2 = world.getBlockAt(destBx, yBase + 1, destBz);
        if (ev.isSolidForMovement(b2) && getBlockTopY(b2, yBase + 1) > from.getY() + 0.6) return true;

        return false;
    }

    private Location applyGravityToLocation(Location loc) {
        World world = loc.getWorld();
        if (world == null) return loc;

        boolean flying = pathfindingType == PathfindingType.FLY || pathfindingType == PathfindingType.FLY_GROUND;
        boolean swimming = pathfindingType == PathfindingType.SWIM || pathfindingType == PathfindingType.WALK_WATER;

        if (flying) { verticalVelocity = 0; onGround = false; return loc; }
        if (swimming) {
            Block b = world.getBlockAt(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
            if (b.getType() == Material.WATER) { verticalVelocity = 0; onGround = false; return loc; }
        }

        double prevVelocity = verticalVelocity;
        verticalVelocity = Math.max(verticalVelocity - GRAVITY_ACCEL, -MAX_FALL_SPEED);
        double newY = loc.getY() + verticalVelocity;

        // Collision plafond pendant la montée (saut) : si l'entité monte, on vérifie
        // immédiatement si la tête heurte quelque chose avant d'appliquer le déplacement.
        if (prevVelocity > 0) {
            double ceilY = findCeilingYAabb(world, loc.getX(), newY, loc.getZ());
            if (newY + getEntityHeight() > ceilY) {
                newY = ceilY - getEntityHeight();
                verticalVelocity = 0; // annule l'élan vers le haut
                onGround = false;
                loc.setY(newY);
                return loc;
            }
        }

        // Commence à chercher un tout petit peu au dessus de l'ancien Y, cela évite de rater
        // le tapis quand la gravité nous fait passer en dessous de Y.0625 et permet une vraie collision par vitesse
        double searchStart = Math.max(loc.getY(), newY) + 0.1;
        double maxAllowedY = loc.getY() + 1e-4; 
        double floorY = findFloorYAabb(world, loc.getX(), searchStart, loc.getZ(), maxAllowedY);

        if (verticalVelocity <= 0 && newY <= floorY) {
            newY = floorY;
            verticalVelocity = 0;
            onGround = true;
        } else {
            onGround = false;
            double ceilY = findCeilingYAabb(world, loc.getX(), newY, loc.getZ());
            if (newY + getEntityHeight() > ceilY) { newY = ceilY - getEntityHeight(); verticalVelocity = 0; }
        }

        loc.setY(newY);
        return loc;
    }

    private Location resolveHorizontalCollision(Location from, Location to) {
        World world = from.getWorld();
        if (world == null) return to;

        double hw = hitbox != null ? hitbox.getX() / 2.0 : 0.3;
        double eh = hitbox != null ? hitbox.getY() : 1.8;
        NodeEvaluator ev = getWalkEvaluator();
        double fromX = from.getX(), fromZ = from.getZ(), baseY = from.getY();
        double toX = to.getX(), toZ = to.getZ();

        java.util.function.BiPredicate<Double,Double> overlapsBlock = (cx, cz) -> {
            int x0 = (int) Math.floor(cx - hw), x1 = (int) Math.floor(cx + hw - 1e-7);
            int y0 = (int) Math.floor(baseY + 1e-4), y1 = (int) Math.floor(baseY + eh - 1e-7);
            int z0 = (int) Math.floor(cz - hw), z1 = (int) Math.floor(cz + hw - 1e-7);
            for (int bx = x0; bx <= x1; bx++)
                for (int by = y0; by <= y1; by++)
                    for (int bz = z0; bz <= z1; bz++) {
                        Block blk = world.getBlockAt(bx, by, bz);
                        if (!ev.isSolidForMovement(blk)) continue;
                        double top = getBlockTopY(blk, by);
                        if (top <= baseY + 0.6) continue;
                        return true;
                    }
            return false;
        };

        double resolvedX = toX;
        if (Math.abs(toX - fromX) > 1e-6 && overlapsBlock.test(toX, fromZ)) resolvedX = fromX;
        double resolvedZ = toZ;
        if (Math.abs(toZ - fromZ) > 1e-6 && overlapsBlock.test(resolvedX, toZ)) resolvedZ = fromZ;

        Location result = to.clone();
        result.setX(resolvedX);
        result.setZ(resolvedZ);
        return result;
    }

    private void applyGravity() {
        if (isRealEntity()) return;
        Location loc = getLocation();
        Location adj = applyGravityToLocation(loc.clone());
        if (adj.getY() != loc.getY()) { applyNewLocation(adj); sendMovePacket(loc, getLocation()); }
    }

    /** Correction : scan depuis searchStartY pour ne pas rater les entités fines tombées via la gravité */
    private double findFloorYAabb(World world, double cx, double searchStartY, double cz, double maxAllowedY) {
        double hw = hitbox != null ? hitbox.getX() / 2.0 : 0.3;
        int x0 = (int) Math.floor(cx - hw + 1e-7), x1 = (int) Math.floor(cx + hw - 1e-7);
        int z0 = (int) Math.floor(cz - hw + 1e-7), z1 = (int) Math.floor(cz + hw - 1e-7);
        double best = world.getMinHeight() - 1;

        for (int bx = x0; bx <= x1; bx++) {
            for (int bz = z0; bz <= z1; bz++) {
                for (int by = (int) Math.floor(searchStartY); by >= world.getMinHeight(); by--) {
                    Block b = world.getBlockAt(bx, by, bz);
                    double top = getBlockTopY(b, by);
                    if (top > Double.NEGATIVE_INFINITY && top <= maxAllowedY + 1e-4) {
                        if (top > best) best = top;
                        break; 
                    }
                }
            }
        }
        return best < world.getMinHeight() ? world.getMinHeight() : best;
    }

    private double findCeilingYAabb(World world, double cx, double footY, double cz) {
        double hw = hitbox != null ? hitbox.getX() / 2.0 : 0.3;
        double eh = hitbox != null ? hitbox.getY() : 1.8;
        int x0 = (int) Math.floor(cx - hw + 1e-7), x1 = (int) Math.floor(cx + hw - 1e-7);
        int z0 = (int) Math.floor(cz - hw + 1e-7), z1 = (int) Math.floor(cz + hw - 1e-7);
        int searchMax = (int) Math.ceil(footY + eh) + 5;
        double best = searchMax;
        NodeEvaluator ev = getWalkEvaluator();

        for (int bx = x0; bx <= x1; bx++) {
            for (int bz = z0; bz <= z1; bz++) {
                for (int by = (int) Math.ceil(footY + eh - 1e-7); by <= searchMax; by++) {
                    if (ev.isSolidForMovement(world.getBlockAt(bx, by, bz))) {
                        if (by < best) best = by;
                        break;
                    }
                }
            }
        }
        return best;
    }

    private double getBlockTopY(Block b, int by) {
        Material mat = b.getType();
        if (Tag.WOOL_CARPETS.isTagged(mat) || mat == Material.MOSS_CARPET) return by + 0.0625;
        NodeEvaluator ev = getWalkEvaluator();
        if (!ev.isSolidForMovement(b) && !ev.isClimbable(b)) return Double.NEGATIVE_INFINITY;
        if (ev.isFence(mat)) return by + 1.5;
        BlockData data = b.getBlockData();
        if (data instanceof Slab slab) return slab.getType() == Slab.Type.BOTTOM ? by + 0.5 : by + 1.0;
        return by + 1.0;
    }

    private double getEntityHeight() { return hitbox != null ? hitbox.getY() : 1.8; }

    // =========================================================================
    // A* // =========================================================================

    private void recalculatePath() {
        path.clear(); lastPathDest = destination.clone(); usingPartialPath = false;
        World world = getLocation().getWorld();
        if (world == null) return;

        NodeEvaluator evaluator = getEvaluator();
        evaluator.prepare(world);

        Location loc = getLocation();
        Node start = evaluator.getStart(loc.getX(), loc.getY(), loc.getZ());
        Node goal  = evaluator.getGoal((int) Math.floor(destination.getX()), (int) Math.floor(destination.getY()), (int) Math.floor(destination.getZ()));

        List<Node> found = aStar(evaluator, start, goal, world);
        if (found == null || found.isEmpty()) return;

        Node last = found.get(found.size() - 1);
        usingPartialPath = !(last.x == goal.x && last.y == goal.y && last.z == goal.z);

        for (Node n : found) {
            double surfY = surfaceYForNode(world, n);
            path.add(new Location(world, n.x + 0.5, surfY, n.z + 0.5));
        }
        if (!usingPartialPath) path.add(destination.clone());
    }

    private List<Node> aStar(NodeEvaluator evaluator, Node start, Node goal, World world) {
        BinaryHeap open = new BinaryHeap(); Set<Node> closed = new HashSet<>();
        Map<Long, Node> nodeMap = new HashMap<>();

        start.g = 0; start.h = start.heuristic(goal); start.f = start.h;
        open.insert(start); nodeMap.put(nodeKey(start), start);

        Node bestNode = start; float bestH = start.h; int explored = 0;
        Node[] neighborBuf = new Node[32];

        while (!open.isEmpty() && explored < MAX_NODES) {
            Node current = open.poll();
            explored++; current.closed = true; closed.add(current);

            if (current.h < bestH) { bestH = current.h; bestNode = current; }
            if (current.x == goal.x && current.y == goal.y && current.z == goal.z) return reconstructPath(current);

            int nCount = evaluator.getNeighbors(neighborBuf, current);
            for (int i = 0; i < nCount; i++) {
                Node nb = neighborBuf[i];
                if (nb == null || nb.closed) continue;

                float moveCost = (float) Math.sqrt(
                    (nb.x - current.x)*(nb.x - current.x) + (nb.y - current.y)*(nb.y - current.y) + (nb.z - current.z)*(nb.z - current.z)
                ) + nb.type.malus;
                float tentativeG = current.g + moveCost;

                long key = nodeKey(nb); Node existing = nodeMap.get(key);
                if (existing != null) {
                    if (tentativeG < existing.g) {
                        existing.g = tentativeG; existing.f = tentativeG + existing.h; existing.cameFrom = current;
                        if (open.contains(existing)) open.changeCost(existing); else open.insert(existing);
                    }
                } else {
                    nb.g = tentativeG; nb.h = nb.heuristic(goal); nb.f = nb.g + nb.h; nb.cameFrom = current;
                    open.insert(nb); nodeMap.put(key, nb);
                }
            }
        }
        if (bestNode != start) return reconstructPath(bestNode);
        return Collections.emptyList();
    }

    private List<Node> reconstructPath(Node end) {
        LinkedList<Node> path = new LinkedList<>(); Node cur = end;
        while (cur != null) { path.addFirst(cur); cur = cur.cameFrom; }
        if (!path.isEmpty()) path.removeFirst();
        return path;
    }

    private long nodeKey(Node n) { return ((long)(n.x + 524288) << 40) | ((long)(n.y + 2048) << 20) | (n.z + 524288); }

    private double surfaceYForNode(World world, Node n) {
        boolean flying = pathfindingType == PathfindingType.FLY || pathfindingType == PathfindingType.FLY_GROUND;
        boolean swimming = pathfindingType == PathfindingType.SWIM;
        if (flying || swimming) return n.y + 0.5;
        Block self = world.getBlockAt(n.x, n.y, n.z);
        BlockData selfData = self.getBlockData();
        if (selfData instanceof Slab slab && slab.getType() == Slab.Type.BOTTOM) return n.y + 0.5;
        return n.y;
    }

    private NodeEvaluator getEvaluator() {
        switch (pathfindingType) {
            case FLY, FLY_GROUND -> { return getFlyEvaluator(); }
            case SWIM             -> { return getSwimEvaluator(); }
            default               -> { return getWalkEvaluator(); }
        }
    }
    private NodeEvaluator getWalkEvaluator() { if (walkEvaluator == null) walkEvaluator = new WalkNodeEvaluator(); return walkEvaluator; }
    private NodeEvaluator getFlyEvaluator() { if (flyEvaluator == null) flyEvaluator = new FlyNodeEvaluator(); return flyEvaluator; }
    private NodeEvaluator getSwimEvaluator() { if (swimEvaluator == null) swimEvaluator = new SwimNodeEvaluator(); return swimEvaluator; }

    // =========================================================================
    // Commit position & Packets
    // =========================================================================

    private void applyNewLocation(Location loc) {
        if (isRealEntity()) entity.teleport(loc); else this.location = loc.clone();
    }

    private void sendMovePacket(Location from, Location to) {
        if (isRealEntity() || !PACKET_EVENTS_AVAILABLE || players.isEmpty()) return;

        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();
        
        boolean tooFar = Math.abs(dx) > 8 || Math.abs(dy) > 8 || Math.abs(dz) > 8;

        for (Player player : players) {
            if (!player.isOnline()) continue;
            
            if (tooFar) {
                PacketEvents.getAPI().getPlayerManager().sendPacket(player,
                    new WrapperPlayServerEntityTeleport(entityId, 
                        new com.github.retrooper.packetevents.protocol.world.Location(
                            to.getX(), to.getY(), to.getZ(), to.getYaw(), to.getPitch()
                        ), false));
            } else {
                // Remplacement ici : On donne directement dx, dy, dz (PacketEvents fera le * 4096 lui-même)
                PacketEvents.getAPI().getPlayerManager().sendPacket(player,
                    new WrapperPlayServerEntityRelativeMoveAndRotation(
                        entityId, 
                        dx, dy, dz, 
                        to.getYaw(), to.getPitch(), 
                        false
                    ));
            }
        }
    }
    
    private boolean isSameBlock(Location a, Location b) {
        if (a == null || b == null) return false;
        return (int) a.getX() == (int) b.getX() && (int) a.getY() == (int) b.getY() && (int) a.getZ() == (int) b.getZ();
    }

    // =========================================================================
    // Getters / Setters
    // =========================================================================

    private void checkRegistered(String method) { if (!isRegistered()) Skript.warning("[SkWhy] Calling '" + method + "' on an unregistered FakePathFinding instance."); }
    private void checkPacketEvents(String method) { if (!PACKET_EVENTS_AVAILABLE) throw new UnsupportedOperationException("[SkWhy] '" + method + "' requires PacketEvents."); }

    public int getEntityId() { checkRegistered("entity id"); checkPacketEvents("entity id"); return entityId; }
    public Vector getHitbox() { checkRegistered("hitbox"); return hitbox.clone(); }
    public void setHitbox(Vector hitbox) {
        checkRegistered("set hitbox"); checkPacketEvents("set hitbox");
        if (isRealEntity()) { Skript.warning("[SkWhy] Cannot manually set the hitbox of a real entity."); return; }
        this.hitbox = hitbox.clone();
    }
    public void updateHitbox() {
        checkRegistered("update hitbox");
        if (!isRealEntity()) { Skript.warning("[SkWhy] Cannot update hitbox on a numeric-ID fake entity."); return; }
        this.hitbox = new Vector(entity.getWidth(), entity.getHeight(), entity.getWidth());
    }

    public Location getLocation() { checkRegistered("location"); return isRealEntity() ? entity.getLocation().clone() : location.clone(); }
    public void setLocation(Location location) {
        checkRegistered("set location");
        if (isRealEntity()) entity.teleport(location); else { checkPacketEvents("set location"); this.location = location.clone(); }
        ticksSinceRecalc = PATH_RECALC_INTERVAL;
    }

    public double getSpeed() { checkRegistered("speed"); return speed; }
    public void setSpeed(double s) { checkRegistered("set speed"); this.speed = s; }
    public PathfindingType getPathfindingType() { checkRegistered("pathfinding type"); return pathfindingType; }
    public void setPathfindingType(PathfindingType type) { checkRegistered("set pathfinding type"); this.pathfindingType = type; path.clear(); }

    public List<Player> getPlayers() { checkRegistered("players"); checkPacketEvents("players"); return Collections.unmodifiableList(players); }
    public void setPlayers(List<Player> p) { checkRegistered("set players"); checkPacketEvents("set players"); players.clear(); players.addAll(p); }
    public void addPlayer(Player p) { checkRegistered("add player"); checkPacketEvents("add player"); if (!players.contains(p)) players.add(p); }
    public void removePlayer(Player p) { checkRegistered("remove player"); checkPacketEvents("remove player"); players.remove(p); }

    public Location getDestination() { checkRegistered("destination"); return destination != null ? destination.clone() : null; }
    public void setDestination(Location dest) {
        checkRegistered("set destination"); this.destination = dest != null ? dest.clone() : null;
        this.usingPartialPath = false; path.clear(); ticksSinceRecalc = PATH_RECALC_INTERVAL;
    }

    public int getPauseTicks() { checkRegistered("pause ticks"); return pauseTicks; }
    public void setPauseTicks(int t) { checkRegistered("set pause ticks"); this.pauseTicks = t; }

    public Entity getEntity() {
        checkRegistered("entity");
        if (!isRealEntity()) Skript.warning("[SkWhy] This FakePathFinding was created with a numeric ID, not a real entity.");
        return entity;
    }

    public boolean isRealEntity() { return entity != null; }
}