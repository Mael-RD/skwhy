package skwhy.pathfinder;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.bukkit.Tag;

import java.util.*;

/**
 * Navigation — portage Bukkit de PathNavigation vanilla.
 *
 * Utilise les NodeEvaluator déjà présents dans le package (WalkNodeEvaluator,
 * FlyNodeEvaluator, SwimNodeEvaluator, AmphibiousNodeEvaluator) exactement comme
 * GroundPathNavigation / FlyingPathNavigation / WaterBoundPathNavigation le font
 * côté vanilla, mais sans la couche net.minecraft.
 *
 * Expose la même interface publique que FakePathFinding.
 */
public class Navigation {

    // =========================================================================
    // Enum public
    // =========================================================================

    public enum PathfindingType {
        WALK, WALK_WATER, SWIM, FLY, FLY_GROUND, CLIMB, NONE
    }

    // =========================================================================
    // Constantes
    // =========================================================================

    public static final int PAUSED_INDEFINITELY   = -1;
    private static final int    PATH_RECALC_INTERVAL = 10;
    private static final double ARRIVAL_THRESHOLD    = 0.35;
    private static final double GRAVITY_ACCEL        = 0.08;
    private static final double MAX_FALL_SPEED       = 3.92;
    private static final double JUMP_IMPULSE         = 0.46;
    private static final double JUMP_MIN_DY          = 0.1;
    private static final double JUMP_MAX_DY          = 1.25;

    // =========================================================================
    // Registre statique
    // =========================================================================

    private static final List<Navigation> REGISTRY = Collections.synchronizedList(new ArrayList<>());

    public static List<Navigation> getRegistry() { return Collections.unmodifiableList(REGISTRY); }

    public static void tickAll() {
        REGISTRY.removeIf(nav -> nav.isRealEntity() && !nav.entity.isValid());
        new ArrayList<>(REGISTRY).forEach(Navigation::tick);
    }

    private void register()        { REGISTRY.add(this); }
    public  void unregister()      { REGISTRY.remove(this); }
    public  boolean isRegistered() { return REGISTRY.contains(this); }

    // =========================================================================
    // Champs d'instance
    // =========================================================================

    private final int          entityId;
    private final Entity       entity;
    private Vector             hitbox;
    private Location           location;
    private double             speed;
    private List<Player>       players;
    private PathfindingType    pathfindingType;
    private boolean            allowBreaching;
    private Location           destination;
    private int                pauseTicks = PAUSED_INDEFINITELY;

    // État du chemin courant
    private final Deque<Location> path = new ArrayDeque<>();
    private int      ticksSinceRecalc = 0;
    private Location lastPathDest     = null;
    private double   lastSpeed;
    private boolean  usingPartialPath = false;

    // Physique verticale (pour entités non-réelles)
    private double  verticalVelocity = 0.0;
    private boolean onGround         = false;

    // NodeEvaluators — instanciés à la demande, réutilisés ensuite
    // Ils font partie du même package ; leurs noms correspondent aux classes
    // vanilla portées en Bukkit (WalkNodeEvaluator, FlyNodeEvaluator, etc.)
    private NodeEvaluator walkEvaluator;
    private NodeEvaluator flyEvaluator;
    private NodeEvaluator swimEvaluator;

    // =========================================================================
    // Constructeurs
    // =========================================================================

    /** Entité virtuelle (sans objet Bukkit Entity). */
    public Navigation(int entityId, Vector hitbox, Location location, double speed,
                      List<Player> players, PathfindingType pathfindingType) {
        this.entityId        = entityId;
        this.hitbox          = hitbox.clone();
        this.location        = location.clone();
        this.speed           = speed;
        this.players         = new ArrayList<>(players);
        this.pathfindingType = pathfindingType;
        this.lastSpeed       = speed;
        this.entity          = null;
        register();
    }

    /** Entité Bukkit réelle. */
    public Navigation(Entity entity, double speed, List<Player> players,
                      PathfindingType pathfindingType) {
        this.entityId        = entity.getEntityId();
        this.hitbox          = new Vector(entity.getWidth(), entity.getHeight(), entity.getWidth());
        this.location        = entity.getLocation().clone();
        this.speed           = speed;
        this.players         = new ArrayList<>(players);
        this.pathfindingType = pathfindingType;
        this.lastSpeed       = speed;
        this.entity          = entity;
        // Un seul navigateur par entité réelle
        REGISTRY.removeIf(n -> n.isRealEntity() && n.entity.getEntityId() == entity.getEntityId());
        register();
    }

    // =========================================================================
    // Tick principal  (logique de PathNavigation#tick + followThePath)
    // =========================================================================

    public void tick() {
        if (pauseTicks == PAUSED_INDEFINITELY) return;
        if (pauseTicks > 0) { pauseTicks--; return; }
        if (destination == null) { applyGravity(); return; }

        // Arrivée à destination
        if (getLocation().distanceSquared(destination) <= ARRIVAL_THRESHOLD * ARRIVAL_THRESHOLD) {
            path.clear(); destination = null; usingPartialPath = false;
            applyGravity(); return;
        }

        // Recalcul du chemin si nécessaire (logique de PathNavigation#recomputePath)
        boolean needsRecalc = path.isEmpty()
                || ticksSinceRecalc >= PATH_RECALC_INTERVAL
                || !isSameBlock(lastPathDest, destination)
                || lastSpeed != speed;

        if (needsRecalc) { recalculatePath(); ticksSinceRecalc = 0; lastSpeed = speed; }
        else             { ticksSinceRecalc++; }

        if (path.isEmpty()) {
            if (usingPartialPath) { destination = null; usingPartialPath = false; }
            applyGravity(); return;
        }

        // ── followThePath : avancer vers le prochain waypoint ────────────────
        Location next = path.peek();
        if (getLocation().distanceSquared(next) <= ARRIVAL_THRESHOLD * ARRIVAL_THRESHOLD) {
            path.poll();
            if (path.isEmpty()) {
                if (usingPartialPath) { destination = null; usingPartialPath = false; }
                applyGravity(); return;
            }
            next = path.peek();
        }

        Location prev   = getLocation().clone();
        Vector   horiz  = new Vector(next.getX() - prev.getX(), 0, next.getZ() - prev.getZ());
        double   hDist  = horiz.length();
        Location newLoc = prev.clone();

        // Déplacement horizontal (speedModifier vanilla → speed ici)
        if (hDist > 0.001) {
            if (hDist <= speed) { newLoc.setX(next.getX()); newLoc.setZ(next.getZ()); }
            else { horiz.normalize().multiply(speed); newLoc.add(horiz.getX(), 0, horiz.getZ()); }
        }

        newLoc = resolveHorizontalCollision(prev, newLoc);

        // ── Vertical : step / jump / gravité  (logique de MoveControl vanilla) ──
        double   dy      = next.getY() - getLocation().getY();
        boolean  stepped = false;

        // Step-up (montée douce ≤ 0.5 bloc, comme l'attribut step_height vanilla)
        if (onGround && verticalVelocity <= 0) {
            World sw = newLoc.getWorld();
            if (sw != null) {
                double scanFrom  = prev.getY() + 0.6;
                double newFloor  = findFloorYAabb(sw, newLoc.getX(), scanFrom, newLoc.getZ(), scanFrom);
                double stepDy    = newFloor - prev.getY();
                if (stepDy > 1e-4 && stepDy <= 0.5) {
                    double ceilY = findCeilingYAabb(sw, newLoc.getX(), newFloor, newLoc.getZ());
                    if (newFloor + entityHeight() <= ceilY) {
                        newLoc.setY(newFloor); verticalVelocity = 0; onGround = true; stepped = true;
                    }
                }
            }
        }

        if (!stepped && shouldJump(dy, prev, next)) verticalVelocity = JUMP_IMPULSE;
        if (!stepped) newLoc = applyGravityToLocation(newLoc);

        // Orientation (yaw) vers la direction de déplacement
        double ddx = newLoc.getX() - prev.getX();
        double ddz = newLoc.getZ() - prev.getZ();
        if (Math.abs(ddx) > 0.001 || Math.abs(ddz) > 0.001)
            newLoc.setYaw((float) Math.toDegrees(Math.atan2(-ddx, ddz)));

        applyNewLocation(newLoc);
    }

    // =========================================================================
    // Recalcul du chemin  (logique de PathNavigation#createPath + PathFinder)
    // =========================================================================

    private void recalculatePath() {
        path.clear(); lastPathDest = destination.clone(); usingPartialPath = false;

        World world = getLocation().getWorld();
        if (world == null) return;

        // Choisit le NodeEvaluator approprié, identique au choix fait par
        // GroundPathNavigation / FlyingPathNavigation / WaterBoundPathNavigation
        NodeEvaluator evaluator = getEvaluator(allowBreaching);
        evaluator.prepare(world, this);

        // getStart : position courante de l'entité (cf. getTempMobPos vanilla)
        Node start = evaluator.getStart();
        // getGoal  : position cible (cf. PathFinder#findPath → NodeEvaluator#getGoal)
        Node goal  = evaluator.getTarget(
                (int) Math.floor(destination.getX()),
                (int) Math.floor(destination.getY()),
                (int) Math.floor(destination.getZ()));

        List<Node> found = aStar(evaluator, start, goal);
        if (found == null || found.isEmpty()) return;

        Node last = found.get(found.size() - 1);
        usingPartialPath = !(last.x == goal.x && last.y == goal.y && last.z == goal.z);

        for (Node n : found) {
            double surfY = surfaceYForNode(world, n);
            path.add(new Location(world, n.x + 0.5, surfY, n.z + 0.5));
        }
        if (!usingPartialPath) path.add(destination.clone());
    }

    // =========================================================================
    // A* (calqué sur PathFinder#findPath vanilla, adapté sans net.minecraft)
    // =========================================================================

    /** Min-heap sur Node#f. */
    private static final class NodeHeap {
        private final List<Node> h = new ArrayList<>();
        boolean isEmpty()               { return h.isEmpty(); }
        boolean contains(Node n)    { return n.heapIdx >= 0; }

        void insert(Node n)         { n.heapIdx = h.size(); h.add(n); siftUp(n.heapIdx); }
        Node poll() {
            Node min = h.get(0), last = h.remove(h.size() - 1);
            min.heapIdx = -1;
            if (!h.isEmpty()) { h.set(0, last); last.heapIdx = 0; siftDown(0); }
            return min;
        }
        void changeCost(Node n)     { siftUp(n.heapIdx); }

        private void siftUp(int i) {
            Node n = h.get(i);
            while (i > 0) {
                int p = (i - 1) >> 1;
                if (n.f >= h.get(p).f) break;
                swap(i, p); i = p;
            }
        }
        private void siftDown(int i) {
            int s = h.size();
            while (true) {
                int l = (i << 1) + 1, r = l + 1, b = i;
                if (l < s && h.get(l).f < h.get(b).f) b = l;
                if (r < s && h.get(r).f < h.get(b).f) b = r;
                if (b == i) break;
                swap(i, b); i = b;
            }
        }
        private void swap(int a, int b) {
            Node na = h.get(a), nb = h.get(b);
            h.set(a, nb); h.set(b, na); na.heapIdx = b; nb.heapIdx = a;
        }
    }

    private static final int MAX_NODES = 200;

    private List<Node> aStar(NodeEvaluator evaluator, Node start, Node goal) {
        NodeHeap open = new NodeHeap();
        Map<Long, Node> nodeMap = new HashMap<>();

        start.g = 0; start.h = start.heuristic(goal); start.f = start.h;
        open.insert(start); nodeMap.put(nodeKey(start), start);

        Node best = start; float bestH = start.h; int explored = 0;
        Node[] buf = new Node[32];

        while (!open.isEmpty() && explored < MAX_NODES) {
            Node cur = open.poll();
            explored++; cur.closed = true;

            if (cur.h < bestH) { bestH = cur.h; best = cur; }
            if (cur.x == goal.x && cur.y == goal.y && cur.z == goal.z)
                return reconstructPath(cur);

            // getNeighbors : méthode du NodeEvaluator du package
            int nCount = evaluator.getNeighbors(buf, cur);
            for (int i = 0; i < nCount; i++) {
                Node nb = buf[i];
                if (nb == null || nb.closed) continue;

                float move = (float) Math.sqrt(
                    (nb.x - cur.x) * (double)(nb.x - cur.x) +
                    (nb.y - cur.y) * (double)(nb.y - cur.y) +
                    (nb.z - cur.z) * (double)(nb.z - cur.z)) + nb.costMalus;
                float tg = cur.g + move;

                long key = nodeKey(nb);
                Node ex = nodeMap.get(key);
                if (ex != null) {
                    if (tg < ex.g) {
                        ex.g = tg; ex.f = tg + ex.h; ex.cameFrom = cur;
                        if (open.contains(ex)) open.changeCost(ex); else open.insert(ex);
                    }
                } else {
                    nb.g = tg; nb.h = nb.heuristic(goal); nb.f = nb.g + nb.h; nb.cameFrom = cur;
                    open.insert(nb); nodeMap.put(key, nb);
                }
            }
        }
        return best != start ? reconstructPath(best) : Collections.emptyList();
    }

    private List<Node> reconstructPath(Node end) {
        LinkedList<Node> result = new LinkedList<>();
        for (Node n = end; n != null; n = n.cameFrom) result.addFirst(n);
        if (!result.isEmpty()) result.removeFirst(); // retire le nœud de départ
        return result;
    }

    private long nodeKey(Node n) {
        return ((long)(n.x + 524288) << 40) | ((long)(n.y + 2048) << 20) | (n.z + 524288);
    }

    // =========================================================================
    // Physique verticale  (identique à FakePathFinding, inchangée)
    // =========================================================================

    private boolean shouldJump(double dy, Location from, Location to) {
        if (!onGround || verticalVelocity > 0.0) return false;
        if (dy <= JUMP_MIN_DY || dy > JUMP_MAX_DY) return false;
        World world = from.getWorld();
        if (world == null) return false;

        double hw = halfWidth(), eh = entityHeight();
        double dxR = to.getX() - from.getX(), dzR = to.getZ() - from.getZ();
        double hLen = Math.sqrt(dxR * dxR + dzR * dzR);
        if (hLen < 0.001) return false;
        double ndx = dxR / hLen, ndz = dzR / hLen;

        int yBase = (int) Math.floor(from.getY());
        int head  = (int) Math.floor(from.getY() + eh - 1e-7);
        int fbx   = (int) Math.floor(from.getX()), fbz = (int) Math.floor(from.getZ());

        // Pas de saut si un bloc bloque déjà la tête
        if (isSolidForMovement(world, fbx, head,     fbz)) return false;
        if (isSolidForMovement(world, fbx, head + 1, fbz)) return false;

        // Un obstacle sur la trajectoire justifie le saut
        for (double dist : new double[]{ hw + 0.1, hLen * 0.5, hLen }) {
            if (dist > hLen + 0.01) break;
            int bx = (int) Math.floor(from.getX() + ndx * dist);
            int bz = (int) Math.floor(from.getZ() + ndz * dist);
            for (int by = yBase; by <= head; by++)
                if (isSolidForMovement(world, bx, by, bz)
                        && blockTopY(world, bx, by, bz) > from.getY() + 0.6) return true;
        }
        int dbx = (int) Math.floor(to.getX()), dbz = (int) Math.floor(to.getZ());
        if (isSolidForMovement(world, dbx, yBase,     dbz) && blockTopY(world, dbx, yBase,     dbz) > from.getY() + 0.6) return true;
        if (isSolidForMovement(world, dbx, yBase + 1, dbz) && blockTopY(world, dbx, yBase + 1, dbz) > from.getY() + 0.6) return true;
        return false;
    }

    private Location applyGravityToLocation(Location loc) {
        World world = loc.getWorld();
        if (world == null) return loc;

        boolean flying   = pathfindingType == PathfindingType.FLY || pathfindingType == PathfindingType.FLY_GROUND;
        boolean swimming = pathfindingType == PathfindingType.SWIM || pathfindingType == PathfindingType.WALK_WATER;

        if (flying) { verticalVelocity = 0; onGround = false; return loc; }
        if (swimming && world.getBlockAt(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()).getType() == Material.WATER) {
            verticalVelocity = 0; onGround = false; return loc;
        }

        double prev = verticalVelocity;
        verticalVelocity = Math.max(verticalVelocity - GRAVITY_ACCEL, -MAX_FALL_SPEED);
        double newY = loc.getY() + verticalVelocity;

        // Collision plafond pendant la montée
        if (prev > 0) {
            double ceil = findCeilingYAabb(world, loc.getX(), newY, loc.getZ());
            if (newY + entityHeight() > ceil) {
                newY = ceil - entityHeight(); verticalVelocity = 0; onGround = false;
                loc.setY(newY); return loc;
            }
        }

        double searchStart = Math.max(loc.getY(), newY) + 0.1;
        double floorY = findFloorYAabb(world, loc.getX(), searchStart, loc.getZ(), loc.getY() + 1e-4);

        if (verticalVelocity <= 0 && newY <= floorY) {
            newY = floorY; verticalVelocity = 0; onGround = true;
        } else {
            onGround = false;
            double ceil = findCeilingYAabb(world, loc.getX(), newY, loc.getZ());
            if (newY + entityHeight() > ceil) { newY = ceil - entityHeight(); verticalVelocity = 0; }
        }
        loc.setY(newY); return loc;
    }

    private Location resolveHorizontalCollision(Location from, Location to) {
        World world = from.getWorld();
        if (world == null) return to;
        double hw = halfWidth(), eh = entityHeight(), baseY = from.getY();

        java.util.function.BiPredicate<Double, Double> overlaps = (cx, cz) -> {
            int x0 = (int) Math.floor(cx - hw), x1 = (int) Math.floor(cx + hw - 1e-7);
            int y0 = (int) Math.floor(baseY + 1e-4), y1 = (int) Math.floor(baseY + eh - 1e-7);
            int z0 = (int) Math.floor(cz - hw), z1 = (int) Math.floor(cz + hw - 1e-7);
            for (int bx = x0; bx <= x1; bx++)
                for (int by = y0; by <= y1; by++)
                    for (int bz = z0; bz <= z1; bz++)
                        if (isSolidForMovement(world, bx, by, bz) && blockTopY(world, bx, by, bz) > baseY + 0.6)
                            return true;
            return false;
        };

        double rx = (Math.abs(to.getX() - from.getX()) > 1e-6 && overlaps.test(to.getX(), from.getZ())) ? from.getX() : to.getX();
        double rz = (Math.abs(to.getZ() - from.getZ()) > 1e-6 && overlaps.test(rx, to.getZ()))          ? from.getZ() : to.getZ();
        Location result = to.clone(); result.setX(rx); result.setZ(rz); return result;
    }

    private void applyGravity() {
        if (isRealEntity()) return;
        Location loc = getLocation(), adj = applyGravityToLocation(loc.clone());
        if (adj.getY() != loc.getY()) applyNewLocation(adj);
    }

    private double findFloorYAabb(World world, double cx, double searchStartY, double cz, double maxAllowedY) {
        double hw = halfWidth();
        int x0 = (int) Math.floor(cx - hw + 1e-7), x1 = (int) Math.floor(cx + hw - 1e-7);
        int z0 = (int) Math.floor(cz - hw + 1e-7), z1 = (int) Math.floor(cz + hw - 1e-7);
        double best = world.getMinHeight() - 1;
        for (int bx = x0; bx <= x1; bx++) for (int bz = z0; bz <= z1; bz++) {
            for (int by = (int) Math.floor(searchStartY); by >= world.getMinHeight(); by--) {
                double top = blockTopY(world, bx, by, bz);
                if (top > Double.NEGATIVE_INFINITY && top <= maxAllowedY + 1e-4) { if (top > best) best = top; break; }
            }
        }
        return best < world.getMinHeight() ? world.getMinHeight() : best;
    }

    private double findCeilingYAabb(World world, double cx, double footY, double cz) {
        double hw = halfWidth(), eh = entityHeight();
        int x0 = (int) Math.floor(cx - hw + 1e-7), x1 = (int) Math.floor(cx + hw - 1e-7);
        int z0 = (int) Math.floor(cz - hw + 1e-7), z1 = (int) Math.floor(cz + hw - 1e-7);
        int searchMax = (int) Math.ceil(footY + eh) + 5;
        double best = searchMax;
        for (int bx = x0; bx <= x1; bx++) for (int bz = z0; bz <= z1; bz++) {
            for (int by = (int) Math.ceil(footY + eh - 1e-7); by <= searchMax; by++)
                if (isSolidForMovement(world, bx, by, bz)) { if (by < best) best = by; break; }
        }
        return best;
    }

    // =========================================================================
    // Utilitaires blocs
    // =========================================================================

    /** Délègue au NodeEvaluator walk pour ne pas dupliquer la logique de solidité. */
    private boolean isSolidForMovement(World world, int bx, int by, int bz) {
        return WalkNodeEvaluator.isLandPathfindable(world.getBlockAt(bx, by, bz));
    }

    private double blockTopY(World world, int bx, int by, int bz) {
        org.bukkit.block.Block b = world.getBlockAt(bx, by, bz);
        Material mat = b.getType();
        if (org.bukkit.Tag.WOOL_CARPETS.isTagged(mat) || mat == Material.MOSS_CARPET) return by + 0.0625;
        if (!WalkNodeEvaluator.isLandPathfindable(b)) return Double.NEGATIVE_INFINITY;
        if (Tag.FENCES.isTagged(mat) || Tag.WALLS.isTagged(mat)) return by + 1.5;
        org.bukkit.block.data.BlockData data = b.getBlockData();
        if (data instanceof org.bukkit.block.data.type.Slab slab)
            return slab.getType() == org.bukkit.block.data.type.Slab.Type.BOTTOM ? by + 0.5 : by + 1.0;
        return by + 1.0;
    }

    private double surfaceYForNode(World world, Node n) {
        boolean flying   = pathfindingType == PathfindingType.FLY || pathfindingType == PathfindingType.FLY_GROUND;
        boolean swimming = pathfindingType == PathfindingType.SWIM;
        if (flying || swimming) return n.y + 0.5;
        org.bukkit.block.Block self = world.getBlockAt(n.x, n.y, n.z);
        if (self.getBlockData() instanceof org.bukkit.block.data.type.Slab slab
                && slab.getType() == org.bukkit.block.data.type.Slab.Type.BOTTOM) return n.y + 0.5;
        return n.y;
    }

    private double halfWidth()    { return hitbox != null ? hitbox.getX() / 2.0 : 0.3; }
    private double entityHeight() { return hitbox != null ? hitbox.getY() : 1.8; }

    // =========================================================================
    // Sélection du NodeEvaluator  (logique des sous-classes vanilla)
    // ─ GroundPathNavigation   → WalkNodeEvaluator
    // ─ FlyingPathNavigation   → FlyNodeEvaluator
    // ─ WaterBoundPathNavigation → SwimNodeEvaluator
    // ─ WALK_WATER / CLIMB     → WalkNodeEvaluator (fallback)
    // =========================================================================

    private NodeEvaluator getEvaluator(boolean allowBreaching) {
        return switch (pathfindingType) {
            case FLY, FLY_GROUND -> getFlyEvaluator();
            case SWIM            -> getSwimEvaluator(allowBreaching);
            default              -> getWalkEvaluator();
        };
    }

    // Les NodeEvaluator sont ceux du package ; ils sont instanciés ici
    // exactement comme PathNavigation#createPathFinder les instancie vanilla.
    private NodeEvaluator getWalkEvaluator() {
        if (walkEvaluator == null) walkEvaluator = new WalkNodeEvaluator();
        return walkEvaluator;
    }
    private NodeEvaluator getFlyEvaluator() {
        if (flyEvaluator == null) flyEvaluator = new FlyNodeEvaluator();
        return flyEvaluator;
    }
    private NodeEvaluator getSwimEvaluator(boolean allowBreaching) {
        if (swimEvaluator == null) swimEvaluator = new SwimNodeEvaluator(allowBreaching);
        return swimEvaluator;
    }

    // =========================================================================
    // Déplacement de l'entité
    // =========================================================================

    private void applyNewLocation(Location loc) {
        if (isRealEntity()) entity.teleport(loc); else this.location = loc.clone();
    }

    private boolean isSameBlock(Location a, Location b) {
        if (a == null || b == null) return false;
        return (int) a.getX() == (int) b.getX()
            && (int) a.getY() == (int) b.getY()
            && (int) a.getZ() == (int) b.getZ();
    }

    // =========================================================================
    // Getters / Setters publics  (interface identique à FakePathFinding)
    // =========================================================================

    public int     getEntityId()   { return entityId; }

    public Vector  getHitbox()     { return hitbox.clone(); }
    public void    setHitbox(Vector hitbox) { this.hitbox = hitbox.clone(); }
    public double  getMinX()       { return location.getX() - (isRealEntity() ? entity.getWidth() : hitbox.getX()) / 2.0; }
    public double  getMinY()       { return location.getY(); }
    public double  getMinZ()       { return location.getZ() - (isRealEntity() ? entity.getWidth() : hitbox.getZ()) / 2.0; }
    public double  getMaxX()       { return location.getX() + (isRealEntity() ? entity.getWidth() : hitbox.getX()) / 2.0; }
    public double  getMaxY()       { return location.getY() + (isRealEntity() ? entity.getHeight() : hitbox.getY()); }
    public double  getMaxZ()       { return location.getZ() + (isRealEntity() ? entity.getWidth() : hitbox.getZ()) / 2.0; }

    public Location getLocation()  {
        return isRealEntity() ? entity.getLocation().clone() : location.clone();
    }
    public void setLocation(Location location) {
        if (isRealEntity()) entity.teleport(location); else this.location = location.clone();
        ticksSinceRecalc = PATH_RECALC_INTERVAL;
    }

    public double   getSpeed()        { return speed; }
    public void     setSpeed(double s) { this.speed = s; }

    public PathfindingType getPathfindingType()           { return pathfindingType; }
    public void setPathfindingType(PathfindingType type)  { this.pathfindingType = type; path.clear(); }

    public List<Player> getPlayers()                { return Collections.unmodifiableList(players); }
    public void setPlayers(List<Player> p)          { players.clear(); players.addAll(p); }
    public void addPlayer(Player p)                 { if (!players.contains(p)) players.add(p); }
    public void removePlayer(Player p)              { players.remove(p); }

    public Location getDestination() { return destination != null ? destination.clone() : null; }
    public void setDestination(Location dest) {
        this.destination = dest != null ? dest.clone() : null;
        this.usingPartialPath = false; path.clear(); ticksSinceRecalc = PATH_RECALC_INTERVAL;
    }

    public int  getPauseTicks()     { return pauseTicks; }
    public void setPauseTicks(int t){ this.pauseTicks = t; }

    public Entity   getEntity()     { return entity; }
    public boolean  isRealEntity()  { return entity != null; }

    public boolean isInWater() {
        Block block = getLocation().getBlock();
        if (block.getType() == org.bukkit.Material.WATER) {
            return true;
        }
        BlockData data = block.getBlockData();
        return data instanceof Waterlogged wl && wl.isWaterlogged();
    }

    public boolean isOnGround() {
        return (isRealEntity()) ? entity.isOnGround() : onGround;
    }
}