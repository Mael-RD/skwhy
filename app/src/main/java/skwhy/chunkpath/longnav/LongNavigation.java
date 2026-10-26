package skwhy.chunkpath.longnav;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityHeadLook;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRelativeMove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRotation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;

import ch.njol.skript.Skript;

import skwhy.SkWhy;
import skwhy.chunkpath.ChunkGateService;
import skwhy.chunkpath.block.PathCostRules;
import skwhy.chunkpath.gate.Gate;
import skwhy.chunkpath.graph.ChunkNavMesh;
import skwhy.chunkpath.move.LongMob;
import skwhy.chunkpath.move.navigation.LongGroundPathNavigation;
import skwhy.chunkpath.move.navigation.PathNavigation;
import skwhy.chunkpath.move.pathcalculator.LongPathFinder;
import skwhy.chunkpath.move.pathcalculator.Node;
import skwhy.chunkpath.move.pathcalculator.Path;
import skwhy.chunkpath.pathfinding.GatePathfinder;
import skwhy.modules.LongNavigationElements.ChunkPathManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Long-distance, cost-rule aware pathfinding session — the long-distance counterpart to
 * {@link skwhy.pathfinder.Navigation}, but a fully independent movement/pathfinding engine
 * ({@link LongMob} + {@code skwhy.chunkpath.move}) and its own path-search strategy:
 *
 * <ol>
 *   <li>Once both {@link #getStart()} and {@link #getEnd()} are known, the coarse route is computed
 *       once via {@link GatePathfinder#findPath} (the chunk-gate graph, cost-rule weighted). A
 *       {@code null} result (missing registered chunks along the way) moves this navigation to
 *       {@link Status#FAILED_NO_PATH} — {@code GatePathfinder} itself already logs the console
 *       warning explaining why, so no duplicate warning is logged here.</li>
 *   <li>The entity is then walked waypoint by waypoint along that route (each gate's representative
 *       block, then the final destination), each leg computed live by
 *       {@link LongGroundPathNavigation} — a ground A* search weighted by the same
 *       {@link PathCostRules} as the coarse graph — instead of walking straight through every gate,
 *       so the real terrain (not the simplified gate graph) decides the actual footpath.</li>
 *   <li>When the next waypoint's chunk isn't loaded, this navigation never forces a chunk load: it
 *       waits for a duration derived from the already-known gate-to-gate weighted distance for that
 *       leg (converted to ticks via speed), re-checking periodically. If the chunk that loads during
 *       the wait is the *direct next step*, following resumes as if nothing happened. Otherwise, once
 *       the wait exceeds that estimate, the entity is teleported to the first loaded gate found
 *       scanning forward along the remaining route, or straight to the destination if nothing ahead
 *       is loaded — see {@link #tickWaiting()}.</li>
 * </ol>
 */
public class LongNavigation {

    // =========================================================================
    // Enums
    // =========================================================================

    public enum Status {
        /** Created, but start and/or end location aren't both known yet. */
        IDLE,
        /** Route known, actively walking toward the current waypoint. */
        FOLLOWING,
        /** The local pathfinder failed for a reason other than an unloaded chunk; retried periodically. */
        STUCK,
        /** The next waypoint's chunk isn't loaded; waiting per the class-level strategy. */
        WAITING_FOR_CHUNK,
        /** Reached the final destination. */
        ARRIVED,
        /** {@link GatePathfinder} could not find a route (missing registered chunks along the way). */
        FAILED_NO_PATH
    }

    // =========================================================================
    // Constantes
    // =========================================================================

    public static final int PAUSED_INDEFINITELY = -1;

    /** Journalise (console) chaque changement d'état et chaque échec de tronçon ; activé par {@code /cpdebug verbose}. */
    public static volatile boolean verbose = false;

    private static final float MAX_LEG_PATH_LENGTH = 48F;
    private static final int ESTIMATE_COST_PER_BLOCK = 5; // durée estimée seulement, sans règle par défaut
    private static final long WAIT_RECHECK_INTERVAL_TICKS = 20L; // "revérifier régulièrement"
    private static final long STUCK_RETRY_INTERVAL_TICKS = 20L;
    private static final long NO_PROGRESS_TICKS = 100L; // 5 s without getting closer to the waypoint
    private static final double PROGRESS_EPSILON = 0.3; // blocks
    private static final double ARRIVAL_TOLERANCE = 3.0; // blocks, accepted as 'arrived' when the last steps cannot be walked
    private static final int MAX_LEG_SEARCHES_PER_TICK = 3; // A* budget shared by all navigations
    private static final long ABSOLUTE_SYNC_INTERVAL_TICKS = 100L;
    private static final double REACHED_DISTANCE = 1.0; // blocks, horizontally, around a waypoint's block centre

    // reprise après une pause : l'entité a pu être déplacée pendant ce temps
    private static final double RESUME_SNAP_DISTANCE = 1.5;    // blocks: still on the path, just continue
    private static final double RESUME_REJOIN_MAX = 16.0;      // blocks: short A* to the path, beyond that the route is recomputed
    private static final double RESUME_FAR_FROM_WAYPOINT = MAX_LEG_PATH_LENGTH * 0.75; // blocks, when no leg path is being walked
    private static final double REJOIN_ARRIVED_DISTANCE = 3.0; // blocks: rejoin path done close enough to resume the old one

    private static final int PREVIEW_PROBE_ID = -1;      // id of the throw-away mobs the preview searches legs with
    private static final int PREVIEW_MESH_CACHE = 16;    // chunk graphs kept between two calls
    private static final int MAX_REQUESTED_CHUNKS = 25;  // chunks asked for in one go when the walk waits for terrain

    // =========================================================================
    // Registre statique
    // =========================================================================

    private static int legSearchBudget;
    private static long tickCounter;
    private static final List<LongNavigation> REGISTRY = Collections.synchronizedList(new ArrayList<>());

    public static List<LongNavigation> getRegistry() { return Collections.unmodifiableList(REGISTRY); }

    @SuppressWarnings("null")
    public static void tickAll() {
        REGISTRY.removeIf(nav -> {
            boolean gone = nav.isRealEntity() && !nav.getEntity().isValid();
            if (gone) {
                nav.debug("entity no longer valid (dead, removed or its chunk unloaded): navigation dropped");
                nav.releaseEntity();
            }
            return gone;
        });
        legSearchBudget = MAX_LEG_SEARCHES_PER_TICK;
        List<LongNavigation> navs = new ArrayList<>(REGISTRY);
        // start from a different navigation each tick so the search budget never starves the last ones
        int offset = navs.isEmpty() ? 0 : (int) (tickCounter++ % navs.size());
        for (int i = 0; i < navs.size(); i++) {
            LongNavigation nav = navs.get((i + offset) % navs.size());
            try {
                nav.tick();
            } catch (RuntimeException e) {
                // une navigation défaillante ne doit ni bloquer les autres ni se relancer à l'infini
                SkWhy.getInstance().getLogger().log(java.util.logging.Level.WARNING,
                        "[ChunkPath] Erreur pendant le tick d'une long navigation (id " + nav.getEntityId() + ") : "
                                + e, e);
                nav.status = Status.STUCK;
                nav.lastStuckRetryTick = nav.world.getGameTime();
            }
        }
    }

    /**
     * The mob moves the real entity itself (position written every tick, own gravity and collisions):
     * the vanilla AI and gravity would fight it, so both are switched off while this navigation lives.
     */
    private void takeOverEntity(Entity entity) {
        if (previousGravity != null) return; // already controlled
        previousGravity = entity.hasGravity();
        entity.setGravity(false);
        if (entity instanceof org.bukkit.entity.Mob living) {
            previousAware = living.isAware();
            living.setAware(false);
        }
    }

    private void releaseEntity() {
        Entity entity = mob.getEntity();
        if (entity == null || !entity.isValid() || previousGravity == null) return;
        entity.setGravity(previousGravity);
        if (entity instanceof org.bukkit.entity.Mob living && previousAware != null) {
            living.setAware(previousAware);
        }
        previousGravity = null;
    }

    private void debug(String message) {
        if (verbose) {
            SkWhy.getInstance().getLogger().info("[ChunkPath/debug] nav#" + mob.getId() + " " + message);
        }
    }

    private void setStatus(Status newStatus, String why) {
        if (mob.isRealEntity()) {
            // vanilla behaviour only while the navigation is not actively driving the entity (a paused one is not)
            if (newStatus == Status.IDLE || newStatus == Status.ARRIVED || newStatus == Status.FAILED_NO_PATH) {
                releaseEntity();
            } else if (!paused) {
                takeOverEntity(mob.getEntity());
            }
        }
        if (newStatus != status) {
            debug(status + " -> " + newStatus + " (" + why + ") pos=" + fmt(mob.getLocation())
                    + " waypoint#" + waypointIndex + "=" + fmt(currentWaypointLocation()));
        }
        status = newStatus;
    }

    /** Blocks at the mob's feet and around the node it is heading to (debug of a stalled walk). */
    private String describeSurroundings() {
        Location here = mob.getLocation();
        StringBuilder sb = new StringBuilder("mob ").append(fmt(here)).append(" column[");
        appendColumn(sb, here.getBlockX(), here.getBlockY(), here.getBlockZ());
        Path path = mob.getNavigation().getPath();
        if (path != null && !path.isDone()) {
            Node next = path.getNextNode();
            sb.append("] next-node ").append(next.x).append(',').append(next.y).append(',').append(next.z).append(':')
                    .append(next.type).append(" column[");
            appendColumn(sb, next.x, next.y, next.z);
        }
        return sb.append(']').toString();
    }

    private void appendColumn(StringBuilder sb, int x, int y, int z) {
        for (int dy = -1; dy <= 2; dy++) {
            sb.append(dy == -1 ? "" : " ").append(world.getBlockAt(x, y + dy, z).getType());
        }
    }

    private static String describePath(Path path) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < path.getNodeCount() && i < 40; i++) {
            Node n = path.getNode(i);
            sb.append(i == path.getNextNodeIndex() ? " >" : " ").append(n.x).append(',').append(n.y).append(',').append(n.z)
                    .append(':').append(n.type);
        }
        return sb.toString();
    }

    private static String fmt(Location l) {
        return l == null ? "null" : String.format(java.util.Locale.ROOT, "%.1f,%.1f,%.1f", l.getX(), l.getY(), l.getZ());
    }

    /** Restitue toutes les entités réelles (arrêt du plugin) et vide le registre. */
    public static void shutdownAll() {
        for (LongNavigation nav : new ArrayList<>(REGISTRY)) nav.unregister();
    }

    private void register()        { REGISTRY.add(this); }
    public  void unregister() {
        REGISTRY.remove(this);
        mob.getDoors().closeAll();
        releaseEntity();
    }
    public  boolean isRegistered() { return REGISTRY.contains(this); }

    // =========================================================================
    // Champs d'instance
    // =========================================================================

    private final LongMob mob;
    private final World world;
    private List<Player> players;
    private Location startLocation;
    private Location endLocation;
    private int pauseTicks = PAUSED_INDEFINITELY;

    private Status status = Status.IDLE;
    private List<Gate> route;
    private RouteWaypoints waypoints; // concrete cells to walk through, derived from the gates of the route
    private int waypointIndex;
    private int progressWaypoint = -1;
    private double bestDistance;
    private long lastProgressTick;
    private int stuckLevel;
    private float lastSentYaw = Float.NaN;
    private long lastAbsoluteSyncTick;
    private Boolean previousGravity;
    private Boolean previousAware;
    private int legWaypoint = -1; // waypoint for which the mob's current leg path was computed
    private int cutWaypoint = -1; // crossing the current leg heads for without having decided it (see CROSSING_DECISION_RANGE): the leg is cut near it
    private int cutBase = -1;     // waypoint the walk heads for again once that leg is cut
    private boolean skippedWaypoint; // the current stretch of the route already skipped a waypoint it could not reach
    private boolean legPartial;   // that leg path only gets as close as it can to the waypoint
    private boolean paused = true; // true while the navigation does not tick (pause), until the next active tick
    private int forcedTeleports;
    private PathCostRules customCostRules; // null = aucune règle : pas de route, pas de marche
    private Path rejoinResume; // leg path to walk again once the short detour back onto it is done
    private int rejoinIndex;

    private long waitStartTick;
    private long lastWaitCheckTick;
    private long expectedWaitTicks;
    private long lastStuckRetryTick;

    private boolean forceChunkLoading; // false: never asks for a chunk to be loaded, true: loads the chunks the walk needs
    private boolean routeInvalid;      // the rules changed since the route was computed
    private final PathPreview preview = new PathPreview(new PathPreview.LegSolver() {
        @Override public void restart() { previewWaypoints = null; }
        @Override public PathPreview.Leg leg(int[] from, int target) { return previewLeg(from, target); }
        @Override public PathPreview.Leg back(int[] from, int[] to) { return previewBack(from, to); }
        @Override public boolean canRefine(int[] from) { return isSurroundingLoaded(from[0], from[2]); }
    });
    /** Copy of the waypoints the preview chooses the crossings on, as the walk does on its own (null = to be copied from the walk's). */
    private RouteWaypoints previewWaypoints;
    /** Chunk graphs read from the region files for the preview (cleared whenever the rules change). */
    private final Map<Long, ChunkNavMesh> previewMeshes = new LinkedHashMap<>(16, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, ChunkNavMesh> eldest) {
            return size() > PREVIEW_MESH_CACHE;
        }
    };

    // =========================================================================
    // Constructeurs
    // =========================================================================

    /** Entité virtuelle (sans objet Bukkit Entity), sans règle de coût (elle ne marchera pas tant qu'on ne lui en donne pas). */
    public LongNavigation(int entityId, Vector hitbox, Location location, float speed, List<Player> players) {
        this(entityId, hitbox, location, speed, players, null);
    }

    /** Entité virtuelle ; {@code customRules} {@code null} = aucune règle (pas de marche). */
    public LongNavigation(int entityId, Vector hitbox, Location location, float speed, List<Player> players,
                          PathCostRules customRules) {
        this.world = location.getWorld();
        this.customCostRules = customRules;
        this.mob = new LongMob(this, entityId, hitbox, location, speed, customRules);
        this.startLocation = location.clone();
        this.players = new ArrayList<>(players);
        register();
    }

    /** Entité Bukkit réelle, sans règle de coût (elle ne marchera pas tant qu'on ne lui en donne pas). */
    public LongNavigation(Entity entity, float speed) {
        this(entity, speed, null);
    }

    /** Entité Bukkit réelle ; {@code customRules} {@code null} = aucune règle (pas de marche). */
    public LongNavigation(Entity entity, float speed, PathCostRules customRules) {
        this.world = entity.getWorld();
        this.customCostRules = customRules;
        this.mob = new LongMob(this, entity, speed, customRules);
        this.startLocation = entity.getLocation();
        this.players = new ArrayList<>();
        register();
    }

    /** Règles de coût utilisées par cette navigation, ou {@code null} si aucune n'a été donnée (pas de règle par défaut). */
    public PathCostRules getCostRules() {
        return mob.getCostRules();
    }

    /**
     * Change les règles de coût de la marche ({@code null} = aucune règle : la navigation ne bouge plus tant
     * qu'on ne lui en donne pas). Le chemin de la jambe en cours est recalculé au prochain tick.
     */
    public void setCostRules(PathCostRules rules) {
        this.customCostRules = rules;
        mob.setCostRules(rules);
        legWaypoint = -1;
        rejoinResume = null;
        routeInvalid = true;
        invalidatePreview();
    }

    // =========================================================================
    // Tick principal
    // =========================================================================

    public void tick() {
        if (isObserver()) return; // a player is never moved: this navigation only answers questions (see pathBlocks)
        if (pauseTicks == PAUSED_INDEFINITELY) { paused = true; return; }
        if (pauseTicks > 0) { pauseTicks--; paused = true; return; }
        if (status == Status.IDLE || status == Status.ARRIVED || status == Status.FAILED_NO_PATH) {
            mob.getDoors().tickIdle();
            return;
        }
        if (paused) {
            paused = false;
            onResume();
        }

        if (status == Status.WAITING_FOR_CHUNK) {
            tickWaiting();
        } else {
            tickFollowing();
        }

        mob.tick();
        syncVirtualEntity();
        traceTick();

        if (verbose && world.getGameTime() % 20 == 0) {
            Vector d = mob.getDeltaMovement();
            debug(String.format(java.util.Locale.ROOT, "tick %s pos=%s delta=%.3f,%.3f,%.3f ground=%b hcol=%b %s legWp=%d wp=%d",
                    status, fmt(mob.getLocation()), d.getX(), d.getY(), d.getZ(), mob.onGround(),
                    mob.horizontalCollision, mob.getMoveControl().debugState(), legWaypoint, waypointIndex));
        }
    }

    /**
     * Virtual entities only exist on the viewers' clients: besides the relative move packets sent by
     * the mob, the body/head rotation is pushed when it changes, and an absolute position every few
     * seconds so rounding of the relative packets never lets the client drift.
     */
    private void syncVirtualEntity() {
        if (mob.isRealEntity() || players.isEmpty()) return;
        Location loc = mob.getLocation();
        long now = world.getGameTime();
        boolean rotated = Float.isNaN(lastSentYaw) || Math.abs(loc.getYaw() - lastSentYaw) >= 1.0F;
        if (rotated) {
            lastSentYaw = loc.getYaw();
            WrapperPlayServerEntityRotation rotation = new WrapperPlayServerEntityRotation(
                    mob.getId(), loc.getYaw(), loc.getPitch(), mob.onGround());
            WrapperPlayServerEntityHeadLook head = new WrapperPlayServerEntityHeadLook(mob.getId(), loc.getYaw());
            for (Player p : players) {
                PacketEvents.getAPI().getPlayerManager().sendPacket(p, rotation);
                PacketEvents.getAPI().getPlayerManager().sendPacket(p, head);
            }
        }
        if (now - lastAbsoluteSyncTick >= ABSOLUTE_SYNC_INTERVAL_TICKS) {
            lastAbsoluteSyncTick = now;
            sendTeleportPacket(loc);
        }
    }

    private java.io.PrintWriter traceOut;

    /** {@code /cpdebug set trace on}: one CSV line per 2 ticks (tick, x, y, z, status, waypoint index, leg waypoint). */
    private void traceTick() {
        if (!skwhy.chunkpath.debug.ChunkPathTuning.trace) {
            if (traceOut != null) { traceOut.close(); traceOut = null; }
            return;
        }
        long now = world.getGameTime();
        if (now % 2 != 0) return;
        try {
            if (traceOut == null) {
                java.io.File dir = new java.io.File(SkWhy.getInstance().getDataFolder(), "traces");
                dir.mkdirs();
                traceOut = new java.io.PrintWriter(new java.io.FileWriter(new java.io.File(dir, "nav-" + mob.getId() + "-" + System.currentTimeMillis() + ".csv")), true);
                traceOut.println("tick,x,y,z,status,waypoint,leg,forcedTp");
            }
            Location l = mob.getLocation();
            traceOut.println(now + "," + String.format(java.util.Locale.ROOT, "%.2f,%.2f,%.2f", l.getX(), l.getY(), l.getZ())
                    + "," + status + "," + waypointIndex + "," + legWaypoint + "," + forcedTeleports);
        } catch (java.io.IOException e) {
            skwhy.chunkpath.debug.ChunkPathTuning.trace = false;
        }
    }

    /**
     * Once the terrain around the next chunk border is loaded, chooses its crossing cell again with the real floor costs
     * (see {@link RouteWaypoints#refine}).
     *
     * @return true when the waypoint moved
     */
    private boolean refineCrossing(Location waypoint) {
        return refineCrossing(waypointIndex);
    }

    /**
     * {@link #refineCrossing(Location)} for waypoint {@code index}, from where the mob stands. A crossing further than
     * {@link #CROSSING_DECISION_RANGE} is not chosen yet: the walk decides it once it gets there (see {@link #isUndecidedFar}).
     */
    private boolean refineCrossing(int index) {
        if (waypoints == null || index >= waypoints.size() || mob.getCostRules() == null) return false;
        PathCostRules rules = mob.getCostRules();
        Location here = mob.getLocation();
        if (here.distance(waypointLocation(index)) > CROSSING_DECISION_RANGE) return false;
        boolean changed = waypoints.refine(index,
                new int[] { here.getBlockX(), here.getBlockY(), here.getBlockZ() },
                new int[] { endLocation.getBlockX(), endLocation.getBlockY(), endLocation.getBlockZ() },
                (x, y, z) -> {
                    if (!world.isChunkLoaded(x >> 4, z >> 4)) return Integer.MIN_VALUE;
                    org.bukkit.Material below = world.getType(x, y - 1, z);
                    return below.isSolid() && !skwhy.chunkpath.block.BlockClassifier.noCollisionBlocks().contains(below) ? rules.costOf(below) : Integer.MIN_VALUE;
                });
        if (changed) {
            if (verbose) {
                int[] a = waypoints.cell(index), b = waypoints.cell(index + 1);
                debug("crossing #" + index + " chosen again with the floor costs: " + a[0] + "," + a[1] + "," + a[2] + " -> " + b[0] + "," + b[1] + "," + b[2]);
            }
            legWaypoint = -1;
        }
        return changed;
    }

    /**
     * Distance (blocks) within which the walk decides where it crosses a chunk border. Further away the crossing is only a direction:
     * a leg aiming at it is cut once it is this close (cutWaypoint), and the walk searches again from there, with the crossing chosen
     * from that point (real floor costs, the line from where it stands) instead of from wherever the leg started.
     */
    static final double CROSSING_DECISION_RANGE = 16.0;

    /** True when waypoint {@code index} is a border crossing nobody has decided yet and {@code here} is too far to decide it. */
    private boolean isUndecidedFar(int index, Location here) {
        return waypoints != null && index < waypoints.size() && waypoints.isCrossing(index) && !waypoints.isSettled(index)
                && here.distance(waypointLocation(index)) > CROSSING_DECISION_RANGE;
    }

    /**
     * {@code nav.moveTo} toward waypoint {@code index}. When that waypoint is the exit cell of a border crossing, the search
     * may end on any exit cell of the crossing (see {@link RouteWaypoints#acceptsExit}) and the waypoint becomes the cell it
     * ended on: no sideways step toward one precise block of the border.
     */
    private boolean moveToWaypoint(PathNavigation nav, int index, Location target) {
        boolean tolerant = waypoints != null && index < waypoints.size() && waypoints.isCrossing(index);
        boolean decides = tolerant && !isUndecidedFar(index, mob.getLocation()); // a far crossing is decided later, when the leg is cut
        nav.setAlsoAccept(tolerant ? (x, y, z) -> waypoints.acceptsExit(index, x, y, z) : null);
        try {
            boolean moving = nav.moveTo(target, skwhy.chunkpath.debug.ChunkPathTuning.legLength);
            Path path = nav.getPath();
            Node end = moving && path != null && path.canReach() ? path.getEndNode() : null;
            if (decides && end != null && waypoints.snapExit(index, end.x, end.y, end.z)) {
                if (verbose) debug("crossing #" + index + " follows the path: " + end.x + "," + end.y + "," + end.z);
                progressWaypoint = -1;
            }
            return moving;
        } finally {
            nav.setAlsoAccept(null);
        }
    }

    /**
     * The gates only give the direction: before walking to the next waypoint, try the farthest waypoint (or
     * the destination) within reach. The shortcut is kept when the A* really gets there and not by a detour.
     */
    private boolean tryShortcut(PathNavigation nav) {
        int ahead = skwhy.chunkpath.debug.ChunkPathTuning.lookahead;
        if (ahead <= 0) return false;
        return tryShortcut(nav, waypointIndex + ahead, waypointIndex + 1, skwhy.chunkpath.debug.ChunkPathTuning.lookaheadRatio);
    }

    /**
     * Aims past the border: the leg heads for the waypoint after the crossing (the next exit, or the destination) so the
     * path itself decides where it crosses, instead of bending toward one block of the border. Looser than the shortcut
     * (the way across the border is what is being improved) but still bounded.
     */
    private boolean tryThroughLeg(PathNavigation nav) {
        if (waypoints == null || !waypoints.isCrossing(waypointIndex)) return false;
        return tryShortcut(nav, waypointIndex + 2, waypointIndex + 2, THROUGH_LEG_RATIO);
    }

    private static final double THROUGH_LEG_RATIO = 2.0;

    /** Tries the waypoints {@code maxJ} down to {@code minJ} (the destination counts as the one after the last). */
    private boolean tryShortcut(PathNavigation nav, int maxJ, int minJ, double ratio) {
        if (waypoints == null || waypointIndex >= waypoints.size()) return false;
        Location here = mob.getLocation();
        double range = skwhy.chunkpath.debug.ChunkPathTuning.lookaheadRange;
        int tries = 0;
        for (int j = Math.min(waypoints.size(), maxJ); j >= minJ && j > waypointIndex && tries < 3; j--) {
            // never aim at an entry cell: it is one precise block past the border (a stair can start next to it), and
            // standing in the chunk is enough to be past it
            if (j < waypoints.size() && waypoints.isEntry(j)) continue;
            if (j < waypoints.size()) refineCrossing(j); // only once close enough to decide it
            Location target = j == waypoints.size() ? endLocation : waypointLocation(j);
            double straight = here.distance(target);
            if (straight > range || !isChunkLoaded(target)) continue;
            tries++;
            if (!moveToWaypoint(nav, j, target) || !nav.getPath().canReach()) { nav.stop(); continue; }
            Path path = nav.getPath();
            double walked = 0.0;
            for (int i = 1; i < path.getNodeCount(); i++) {
                Node a = path.getNode(i - 1), b = path.getNode(i);
                walked += Math.sqrt((a.x - b.x) * (double) (a.x - b.x) + (a.z - b.z) * (double) (a.z - b.z));
            }
            double chain = 0.0;
            Location previous = here;
            for (int i = waypointIndex; i <= j; i++) {
                Location next = i == waypoints.size() ? endLocation : waypointLocation(i);
                chain += Math.sqrt(Math.pow(previous.getX() - next.getX(), 2) + Math.pow(previous.getZ() - next.getZ(), 2));
                previous = next;
            }
            if (walked > chain * ratio + 2.0) { nav.stop(); continue; }
            if (verbose) debug("shortcut from waypoint#" + waypointIndex + " to waypoint#" + j + " (" + Math.round(walked) + " blocks, gates " + Math.round(chain) + ")");
            boolean undecided = isUndecidedFar(j, here);
            cutBase = waypointIndex;
            cutWaypoint = undecided ? j : -1;
            waypointIndex = j;
            legWaypoint = j;
            legPartial = false;
            setStatus(Status.FOLLOWING, "shortcut leg found (" + path.getNodeCount() + " nodes)");
            return true;
        }
        return false;
    }

    private void tickFollowing() {
        long now = world.getGameTime();
        if (status == Status.STUCK && now - lastStuckRetryTick < STUCK_RETRY_INTERVAL_TICKS) {
            return; // avoid hammering the A* search every tick against an unreachable target
        }

        Location waypoint = currentWaypointLocation();
        if (waypoint == null) {
            setStatus(Status.ARRIVED, "no more waypoint");
            mob.getNavigation().stop();
            return;
        }
        if (hasReached(waypoint)) {
            advanceWaypoint();
            return;
        }

        PathNavigation nav = mob.getNavigation();
        if (rejoinResume != null) {
            if (!nav.isDone()) {
                // walking the short detour back onto the path: not a lack of progress
                progressWaypoint = -1;
                setStatus(Status.FOLLOWING, "rejoining the path");
                return;
            }
            finishRejoin(nav);
        }

        if (noProgressFor(waypoint, now)) {
            return;
        }

        if (legWaypoint == waypointIndex && !nav.isDone()) {
            if (cutWaypoint != waypointIndex || mob.getLocation().distance(waypoint) > CROSSING_DECISION_RANGE) {
                setStatus(Status.FOLLOWING, "leg in progress");
                return; // the leg path is being walked by the mob (ticked right after)
            }
            // close enough to the crossing it was heading for to decide it: a new leg from here (a new start, like the preview's)
            if (verbose) debug("leg cut near crossing #" + cutWaypoint + ": searched again from here");
            nav.stop();
            waypointIndex = cutBase;
            legWaypoint = -1;
            cutWaypoint = -1;
            progressWaypoint = -1;
            waypoint = currentWaypointLocation();
        }
        if (legWaypoint == waypointIndex && legPartial && isCloseTo(waypoint)) {
            // the waypoint (a gate block recorded earlier) cannot be stood on any more — a wall or a door built
            // since — and the closest reachable spot has been reached: carry on from here
            debug("leg ended " + String.format(java.util.Locale.ROOT, "%.1f", mob.getLocation().distance(waypoint))
                    + " blocks from waypoint#" + waypointIndex + " (not reachable): next waypoint");
            advanceWaypoint();
            return;
        }

        if (legSearchBudget <= 0) {
            return; // too many A* searches this tick already, this navigation gets its turn on a later one
        }
        legSearchBudget--;
        if (refineCrossing(waypoint)) {
            waypoint = currentWaypointLocation();
        }
        if (tryShortcut(nav) || tryThroughLeg(nav)) return;
        boolean moving = moveToWaypoint(nav, waypointIndex, waypoint);
        if (!moving) {
            LongGroundPathNavigation.FailureReason reason =
                    ((LongGroundPathNavigation) nav).lastFailureReason();
            if (reason == LongGroundPathNavigation.FailureReason.UNLOADED_CHUNK) {
                enterWaitingState();
            } else {
                if (verbose) debug("leg failed, path=" + nav.getPath() + " target=" + nav.getTargetPos());
                setStatus(Status.STUCK, "leg path failed: " + reason);
                lastStuckRetryTick = now;
            }
            return;
        }

        legWaypoint = waypointIndex;
        cutBase = waypointIndex;
        cutWaypoint = isUndecidedFar(waypointIndex, mob.getLocation()) ? waypointIndex : -1;
        legPartial = !nav.getPath().canReach();
        if (verbose) debug("leg to waypoint#" + waypointIndex + " " + (legPartial ? "PARTIAL " : "") + describePath(nav.getPath()));
        setStatus(Status.FOLLOWING, "leg path found (" + nav.getPath().getNodeCount() + " nodes)");
    }

    /**
     * Watches the distance to the current waypoint. The coarse route (chunk block model) and the live
     * collisions can disagree (an open trapdoor, a fence post...), leaving the mob pushing against an
     * obstacle forever. No progress for {@link #NO_PROGRESS_TICKS}: first the leg path is recomputed
     * from where the mob stands; if that does not help either, it is moved on to the waypoint, which
     * the chunk analysis knows to be standable.
     *
     * @return true when the tick has been consumed by a recovery
     */
    private boolean noProgressFor(Location waypoint, long now) {
        if (progressWaypoint != waypointIndex) {
            progressWaypoint = waypointIndex;
            bestDistance = Double.MAX_VALUE;
            lastProgressTick = now;
            stuckLevel = 0;
        }
        double distance = mob.getLocation().distance(waypoint);
        if (distance < bestDistance - PROGRESS_EPSILON) {
            bestDistance = distance;
            lastProgressTick = now;
            return false;
        }
        if (now - lastProgressTick < NO_PROGRESS_TICKS) return false;

        lastProgressTick = now;
        stuckLevel++;
        if (stuckLevel == 1) {
            debug("no progress toward waypoint#" + waypointIndex + " for " + NO_PROGRESS_TICKS + " ticks: recomputing the leg");
            if (verbose) debug("stuck surroundings: " + describeSurroundings());
            mob.getNavigation().stop();
            legWaypoint = -1;
            return false;
        }
        if (isCloseTo(waypoint)) {
            debug("no progress but within " + ARRIVAL_TOLERANCE + " blocks of waypoint#" + waypointIndex + ": counted as reached");
            advanceWaypoint();
            return true;
        }
        if (waypoints != null && waypointIndex < waypoints.size() && !skippedWaypoint) {
            // maybe that gate block is the problem (blocked since it was recorded): try to walk to the next waypoint instead
            debug("still no progress toward waypoint#" + waypointIndex + ": skipping it");
            skippedWaypoint = true;
            mob.getNavigation().stop();
            waypointIndex++;
            legWaypoint = -1;
            rejoinResume = null;
            skippedWaypoint = false;
            return true;
        }
        debug("still no progress toward waypoint#" + waypointIndex + ": moving the mob to the waypoint");
        teleportMob(waypoint.clone().add(0.5, 0.0, 0.5), "no progress toward waypoint#" + waypointIndex);
        mob.getNavigation().stop();
        stuckLevel = 0;
        advanceWaypoint();
        return true;
    }

    /** Within {@link #ARRIVAL_TOLERANCE} blocks of the waypoint, whatever stands between. */
    private boolean isCloseTo(Location waypoint) {
        Location current = mob.getLocation();
        double dx = current.getX() - (waypoint.getBlockX() + 0.5);
        double dz = current.getZ() - (waypoint.getBlockZ() + 0.5);
        double dy = current.getY() - waypoint.getBlockY();
        return dx * dx + dz * dz <= ARRIVAL_TOLERANCE * ARRIVAL_TOLERANCE && dy > -2.0 && dy < 2.5;
    }

    /** Close enough to a waypoint (gate block or destination) to consider it reached. */
    private boolean hasReached(Location waypoint) {
        Location current = mob.getLocation();
        double nearDx = current.getX() - (waypoint.getBlockX() + 0.5);
        double nearDz = current.getZ() - (waypoint.getBlockZ() + 0.5);
        double nearDy = current.getY() - waypoint.getBlockY();
        if (nearDx * nearDx + nearDz * nearDz < REACHED_DISTANCE * REACHED_DISTANCE && nearDy > -1.5 && nearDy < 2.0) {
            return true; // already standing on the gate block: no leg to walk (the A* has nothing to search)
        }
        if (skwhy.chunkpath.debug.ChunkPathTuning.switchMode >= 1 && waypoints != null && waypointIndex < waypoints.size()) {
            // a gate is only a direction: the mob heads to the gate of its chunk that leads to the next chunk
            // (the exit cell guides it around the obstacles inside the chunk) and switches to the following
            // gate as soon as it stands in the next chunk, without touching the gate block. A gate followed
            // by another one in the same chunk (entry -> exit) is done once the mob is in that chunk.
            int[] cell = waypoints.cell(waypointIndex);
            int[] ref = cell;
            if (waypointIndex + 1 < waypoints.size()) {
                int[] next = waypoints.cell(waypointIndex + 1);
                if (skwhy.chunkpath.debug.ChunkPathTuning.switchMode == 2
                        || (next[0] >> 4) != (cell[0] >> 4) || (next[2] >> 4) != (cell[2] >> 4)) ref = next;
            }
            return (current.getBlockX() >> 4) == (ref[0] >> 4) && (current.getBlockZ() >> 4) == (ref[2] >> 4);
        }
        double dx = current.getX() - (waypoint.getBlockX() + 0.5);
        double dz = current.getZ() - (waypoint.getBlockZ() + 0.5);
        double dy = current.getY() - waypoint.getBlockY();
        return dx * dx + dz * dz < REACHED_DISTANCE * REACHED_DISTANCE && dy > -1.5 && dy < 2.0;
    }

    // =========================================================================
    // Attente / rattrapage chunk non chargé
    // =========================================================================

    private void enterWaitingState() {
        setStatus(Status.WAITING_FOR_CHUNK, "chunks around the next waypoint are not loaded");
        mob.getNavigation().stop(); // stand still, never walk on into unloaded terrain
        waitStartTick = world.getGameTime();
        lastWaitCheckTick = waitStartTick;
        expectedWaitTicks = estimatedTicksForCurrentLeg();
        requestChunks(mob.getLocation(), currentWaypointLocation());
    }

    private void tickWaiting() {
        long now = world.getGameTime();
        if (now - lastWaitCheckTick < WAIT_RECHECK_INTERVAL_TICKS) return;
        lastWaitCheckTick = now;
        requestChunks(mob.getLocation(), currentWaypointLocation());

        Location immediateTarget = currentWaypointLocation();
        if (immediateTarget != null && isChunkLoaded(immediateTarget)
                && mob.getNavigation().moveTo(immediateTarget, MAX_LEG_PATH_LENGTH)) {
            // The direct continuation loaded during the wait: resume as if nothing happened.
            legWaypoint = waypointIndex;
            legPartial = !mob.getNavigation().getPath().canReach();
            setStatus(Status.FOLLOWING, "chunks loaded, leg path found");
            return;
        }

        long elapsed = now - waitStartTick;
        if (elapsed <= expectedWaitTicks) return; // still within the estimated travel time, keep waiting

        if (waypoints != null) {
            for (int i = waypointIndex; i < waypoints.size(); i++) {
                int[] cell = waypoints.cell(i);
                if (world.isChunkLoaded(cell[0] >> 4, cell[2] >> 4)) {
                    teleportMob(waypointLocation(i), "waited " + elapsed + " ticks for the chunks (expected " + expectedWaitTicks + ")");
                    waypointIndex = i;
                    legWaypoint = -1;
                    setStatus(Status.FOLLOWING, "state machine");
                    return;
                }
            }
        }

        // Nothing loaded anywhere ahead: give up on a realistic walk and arrive directly.
        teleportMob(endLocation, "no loaded chunk ahead after " + elapsed + " ticks");
        setStatus(Status.ARRIVED, "state machine");
    }

    private boolean isChunkLoaded(Location loc) {
        return world.isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
    }

    /** Last-resort catch-up; counted in {@link #getForcedTeleports()} because a healthy walk never needs one. */
    private void teleportMob(Location location, String reason) {
        forcedTeleports++;
        SkWhy.getInstance().getLogger().warning("[ChunkPath] nav#" + mob.getId() + " téléportation forcée vers " + fmt(location)
                + " depuis " + fmt(mob.getLocation()) + " : " + reason);
        mob.setLocation(location);
    }

    // =========================================================================
    // Reprise après pause
    // =========================================================================

    /**
     * The navigation was paused (or has just been given its first route): the entity may have been
     * moved meanwhile (vanilla AI, pushed, teleported by a script...). Where it stands is checked
     * against the path it was following: still on it, it just carries on; a short way off, a small
     * A* brings it back onto the path ({@link #rejoinLeg}); too far, the route is recomputed.
     */
    private void onResume() {
        long now = world.getGameTime();
        mob.setDeltaMovement(0.0, 0.0, 0.0);
        if (mob.isRealEntity()) takeOverEntity(mob.getEntity());
        progressWaypoint = -1;
        lastStuckRetryTick = now - STUCK_RETRY_INTERVAL_TICKS;
        if (status == Status.WAITING_FOR_CHUNK) { // the pause is not time spent waiting for the chunks
            waitStartTick = now;
            lastWaitCheckTick = now;
        }
        PathNavigation nav = mob.getNavigation();
        nav.onResume();
        rejoinResume = null;

        Location waypoint = currentWaypointLocation();
        if (waypoints == null || waypoint == null) return;
        Location here = mob.getLocation();
        Path leg = nav.getPath();
        if (legWaypoint == waypointIndex && leg != null && !leg.isDone()) {
            rejoinLeg(leg, here);
        } else {
            legWaypoint = -1; // the next tick computes a fresh leg from where the mob really is
            if (here.getWorld() != world || here.distance(waypoint) > RESUME_FAR_FROM_WAYPOINT) {
                debug("resumed " + fmt(here) + " is far from waypoint#" + waypointIndex + ": route recomputed");
                recomputeRoute();
            }
        }
    }

    private void rejoinLeg(Path leg, Location here) {
        PathNavigation nav = mob.getNavigation();
        int best = -1;
        double bestSquared = Double.MAX_VALUE;
        for (int i = Math.max(0, leg.getNextNodeIndex() - 1); i < leg.getNodeCount(); i++) {
            Node node = leg.getNode(i);
            double dx = here.getX() - (node.x + 0.5);
            double dy = here.getY() - node.y;
            double dz = here.getZ() - (node.z + 0.5);
            double squared = dx * dx + dy * dy + dz * dz;
            if (squared < bestSquared) {
                bestSquared = squared;
                best = i;
            }
        }
        double distance = Math.sqrt(bestSquared);
        if (best < 0 || distance > RESUME_REJOIN_MAX) {
            debug("resumed " + fmt(here) + " is " + String.format(java.util.Locale.ROOT, "%.1f", distance)
                    + " blocks from the leg path: route recomputed");
            nav.stop();
            recomputeRoute();
            return;
        }
        if (distance <= RESUME_SNAP_DISTANCE) {
            leg.setNextNodeIndex(distance > 0.7 ? best : best + 1);
            return;
        }
        Node target = leg.getNode(best);
        Location targetLocation = new Location(world, target.x, target.y, target.z);
        if (nav.moveTo(targetLocation, (float) (distance * 2.0 + 8.0))) {
            debug("resumed " + String.format(java.util.Locale.ROOT, "%.1f", distance) + " blocks off the leg path: rejoining at node#" + best);
            rejoinResume = leg;
            rejoinIndex = best;
        } else {
            debug("no short path back onto the leg path: leg recomputed");
            nav.stop();
            legWaypoint = -1;
        }
    }

    private void finishRejoin(PathNavigation nav) {
        Path resumed = rejoinResume;
        rejoinResume = null;
        Node node = resumed.getNode(rejoinIndex);
        Location here = mob.getLocation();
        double dx = here.getX() - (node.x + 0.5);
        double dz = here.getZ() - (node.z + 0.5);
        if (Math.sqrt(dx * dx + dz * dz) <= REJOIN_ARRIVED_DISTANCE && Math.abs(here.getY() - node.y) < 2.0) {
            nav.resumePath(resumed, rejoinIndex + 1);
            debug("back on the leg path at node#" + rejoinIndex);
        } else {
            debug("rejoin ended away from the leg path: leg recomputed");
            legWaypoint = -1;
        }
    }

    /**
     * Estimated travel time (in ticks) for the leg the navigation is currently stuck on, derived
     * from the already-known weighted distance for that leg — consecutive gates in {@link #route}
     * carry their cost in {@link Gate#internalDistances} when in the same chunk; a cross-chunk hop,
     * or any leg without a precomputed distance (the first/last leg to/from an exact point), falls
     * back to straight-line block distance times the world's default per-block cost, matching the
     * constant {@link GatePathfinder} itself uses for border crossings.
     */
    private long estimatedTicksForCurrentLeg() {
        Location from = mob.getLocation();
        Location to = currentWaypointLocation();
        if (to == null) return 0L;

        double weightedDistance = null != waypoints && waypointIndex > 0 && waypointIndex < waypoints.size()
                ? sameChunkDistanceOrFallback(waypoints.owner(waypointIndex - 1), waypoints.owner(waypointIndex), from, to)
                : straightLineWeightedDistance(from, to);

        float speed = mob.getSpeed();
        if (speed <= 0F) return 0L;
        return (long) Math.ceil(weightedDistance / speed);
    }

    private double sameChunkDistanceOrFallback(Gate previous, Gate current, Location from, Location to) {
        if (previous.chunkX == current.chunkX && previous.chunkZ == current.chunkZ) {
            Integer known = previous.id == current.id ? Integer.valueOf(0) : previous.internalDistances.get(current.id);
            if (known != null) return known;
        }
        return straightLineWeightedDistance(from, to);
    }

    private double straightLineWeightedDistance(Location from, Location to) {
        // ce n'est qu'une estimation de durée : la règle par défaut des règles de la navigation, sinon un coût standard
        PathCostRules costRules = mob.getCostRules();
        int perBlock = costRules != null && costRules.defaultCost() >= 0 ? costRules.defaultCost() : ESTIMATE_COST_PER_BLOCK;
        return from.distance(to) * perBlock;
    }

    // =========================================================================
    // Route / waypoints
    // =========================================================================

    private void recomputeRoute() {
        routeInvalid = false;
        invalidatePreview();
        if (startLocation == null || endLocation == null) {
            setStatus(Status.IDLE, "state machine");
            route = null;
            waypoints = null;
            return;
        }
        if (mob.getCostRules() == null) {
            Skript.warning("[ChunkPath] Aucune règle de coût sur cette long navigation (pas de règle par défaut) : "
                    + "ajoutez-en avec 'using rules' ou 'path cost rules of long navigation'. La navigation ne fait rien.");
            setStatus(Status.FAILED_NO_PATH, "state machine");
            route = null;
            waypoints = null;
            return;
        }
        if (startLocation.getWorld() == null || !startLocation.getWorld().equals(endLocation.getWorld())) {
            Skript.warning("[ChunkPath] La position de départ et d'arrivée d'une long navigation doivent être dans le même monde.");
            setStatus(Status.FAILED_NO_PATH, "state machine");
            route = null;
            waypoints = null;
            return;
        }

        // the route starts where the mob really is (retargeting a moving navigation must not send it
        // back to its original start); the start location only matters until the mob has left it
        Location from = startLocation;
        Location here = mob.getLocation();
        if (here.getWorld() != null && here.getWorld().equals(world) && here.distanceSquared(startLocation) > 4.0) {
            from = here;
        }
        ChunkGateService service = ChunkPathManager.serviceFor(world);
        route = GatePathfinder.findPath(service, mob.getCostRules(),
                from.getBlockX(), from.getBlockY(), from.getBlockZ(),
                endLocation.getBlockX(), endLocation.getBlockY(), endLocation.getBlockZ());

        if (route == null) {
            waypoints = null;
            setStatus(Status.FAILED_NO_PATH, "state machine");
            return;
        }
        waypoints = RouteWaypoints.build(route, from.getBlockX(), from.getBlockY(), from.getBlockZ(),
                new int[] { endLocation.getBlockX(), endLocation.getBlockY(), endLocation.getBlockZ() });
        if (verbose) {
            StringBuilder sb = new StringBuilder("route of ").append(route.size()).append(" gates, waypoints:");
            for (int i = 0; i < waypoints.size(); i++) {
                int[] c = waypoints.cell(i);
                sb.append(' ').append(c[0]).append(',').append(c[1]).append(',').append(c[2]);
            }
            debug(sb.toString());
        }

        waypointIndex = 0;
        legWaypoint = -1;
        rejoinResume = null;
        skippedWaypoint = false;
        setStatus(Status.FOLLOWING, "state machine");
    }

    private void advanceWaypoint() {
        if (waypoints == null || waypointIndex >= waypoints.size()) {
            setStatus(Status.ARRIVED, "final destination reached");
            mob.getNavigation().stop();
        } else {
            waypointIndex++;
            legWaypoint = -1;
            rejoinResume = null;
        }
    }

    private Location currentWaypointLocation() {
        if (waypoints == null) return null;
        if (waypointIndex < waypoints.size()) {
            return waypointLocation(waypointIndex);
        }
        if (waypointIndex == waypoints.size()) {
            return endLocation;
        }
        return null;
    }

    private Location waypointLocation(int index) {
        int[] cell = waypoints.cell(index);
        return new Location(world, cell[0], cell[1], cell[2]);
    }

    // =========================================================================
    // Chargement des chunks / aperçu du chemin
    // =========================================================================

    /** Whether the chunks the walk (or the path preview) needs are loaded when they are not. False by default. */
    public boolean isForceChunkLoading() { return forceChunkLoading; }

    /**
     * Off, no chunk is ever loaded for this navigation: a walk waits for the terrain (see {@link #tickWaiting}) and the cells
     * past an unloaded chunk are left out of {@link #pathBlocks}. On, a walk that waits asks for the chunks it needs (loaded
     * asynchronously, never generated) and {@link #pathBlocks} reads the unloaded ones from the region files, without loading them.
     */
    public void setForceChunkLoading(boolean force) {
        if (force == forceChunkLoading) return;
        forceChunkLoading = force;
        invalidatePreview();
    }

    /** True for a player: it is never driven (no teleport, no gravity change), the navigation only answers {@link #pathBlocks}. */
    public boolean isObserver() { return mob.getEntity() instanceof Player; }

    /**
     * Asks for the chunks between two places to be loaded (asynchronously, never generated), nearest first, when
     * {@link #setForceChunkLoading} is on.
     */
    private void requestChunks(Location from, Location to) {
        if (!forceChunkLoading || from == null || to == null) return;
        double length = from.distance(to);
        int samples = (int) Math.min(64, Math.ceil(length / 8.0)) + 1;
        java.util.Set<Long> wanted = new java.util.LinkedHashSet<>();
        for (int pass = 0; pass < 2; pass++) {
            int reach = pass == 0 ? 0 : 1;
            for (int i = 0; i <= samples; i++) {
                double t = (double) i / samples;
                int cx = (int) Math.floor(from.getX() + (to.getX() - from.getX()) * t) >> 4;
                int cz = (int) Math.floor(from.getZ() + (to.getZ() - from.getZ()) * t) >> 4;
                for (int dx = -reach; dx <= reach; dx++) {
                    for (int dz = -reach; dz <= reach; dz++) {
                        wanted.add(((long) (cx + dx) << 32) ^ ((cz + dz) & 0xFFFFFFFFL));
                    }
                }
            }
        }
        int asked = 0;
        for (long key : wanted) {
            int cx = (int) (key >> 32), cz = (int) key;
            if (world.isChunkLoaded(cx, cz)) continue;
            if (++asked > MAX_REQUESTED_CHUNKS) return;
            world.getChunkAtAsync(cx, cz, false);
        }
    }

    private void invalidatePreview() {
        preview.invalidate();
        previewWaypoints = null;
        previewMeshes.clear();
    }

    /**
     * The cells (feet position, block centre) the walk goes through from where the entity is now, nearest first; at most
     * {@code limit} of them ({@link Integer#MAX_VALUE} = all). Nothing moves: this is how a player, never driven, gets its path.
     *
     * <p>Only what is missing is computed: the route (gates) is kept while the entity stays in the chunks it goes through, the
     * cells already passed are dropped, a position a little off the path is brought back onto it by one small search, and
     * asking for more cells only searches the legs after those already known (see {@link PathPreview}). A leg in a loaded chunk
     * is searched like a real walk would; one in an unloaded chunk is read from the region file when
     * {@link #isForceChunkLoading} is on (the chunk is not loaded), and otherwise ends the list.
     */
    public List<Location> pathBlocks(int limit) {
        List<Location> out = new ArrayList<>();
        if (limit <= 0 || endLocation == null) return out;
        Location here = mob.getLocation();
        if (here.getWorld() != world) return out;
        if (routeInvalid || (route == null && status != Status.FAILED_NO_PATH)) recomputeRoute();
        if (route == null || waypoints == null) return out;

        int first = previewTargetIndex(here);
        if (first < 0) { // out of the chunks of the route: a new route from where the entity is
            recomputeRoute();
            if (route == null || waypoints == null) return out;
            first = Math.max(0, previewTargetIndex(here));
        }
        List<int[]> ahead = preview.next(new int[] { here.getBlockX(), here.getBlockY(), here.getBlockZ() },
                limit, waypoints.size() + 1, first);
        if (isObserver()) setStatus(ahead.isEmpty() && preview.isComplete() ? Status.ARRIVED : Status.FOLLOWING, "path blocks asked");
        for (int[] cell : ahead) out.add(new Location(world, cell[0] + 0.5, cell[1], cell[2] + 0.5));
        return out;
    }

    /**
     * Index of the first waypoint (the destination is {@code waypoints.size()}) still ahead of a position in the chunks of the route, so the gates
     * already found are kept when the entity changed chunk: in a chunk, it heads for the exit cell (the entry cell is behind
     * it); in the last chunk, for the destination. -1 when the route does not go through the chunk of the position.
     */
    private int previewTargetIndex(Location here) {
        int cx = here.getBlockX() >> 4, cz = here.getBlockZ() >> 4;
        for (int i = 0; i < waypoints.size(); i++) {
            int[] cell = waypoints.cell(i);
            if ((cell[0] >> 4) == cx && (cell[2] >> 4) == cz) return waypoints.isEntry(i) ? i + 1 : i;
        }
        if ((endLocation.getBlockX() >> 4) == cx && (endLocation.getBlockZ() >> 4) == cz) return waypoints.size();
        return -1;
    }

    // -------------------------------------------------------------------------
    // Aperçu : rejoue les décisions de la marche (voir tickFollowing) depuis une position virtuelle.
    // Rien de ce qui suit ne touche à la marche : les recherches se font avec des mobs sondes jetables et les
    // passages de bordure sont rechoisis sur une copie des waypoints.
    // -------------------------------------------------------------------------

    private RouteWaypoints previewWaypoints() {
        if (previewWaypoints == null) previewWaypoints = waypoints.copy();
        return previewWaypoints;
    }

    private int[] destinationCell() {
        return new int[] { endLocation.getBlockX(), endLocation.getBlockY(), endLocation.getBlockZ() };
    }

    /** Waypoint {@code index} as a cell; {@code wps.size()} is the destination. */
    private int[] previewCell(RouteWaypoints wps, int index) {
        return index < wps.size() ? wps.cell(index) : destinationCell();
    }

    /**
     * {@link PathPreview.LegSolver#leg}: what the walk does standing on {@code from} and heading for waypoint {@code index}.
     * A waypoint already reached is left at once, the crossing is chosen again with the real floor costs, a shortcut is tried
     * (and the crossing's tolerant exit), then the plain leg; the cells stop where the walk would switch to the next waypoint.
     */
    private PathPreview.Leg previewLeg(int[] from, int index) {
        RouteWaypoints wps = previewWaypoints();
        int[] destination = destinationCell();
        int switchMode = skwhy.chunkpath.debug.ChunkPathTuning.switchMode;
        while (index <= wps.size() && wps.isReached(from, index, destination, switchMode)) index++;
        if (index > wps.size()) return new PathPreview.Leg(new ArrayList<>(List.of(from)), index);
        PathCostRules rules = mob.getCostRules();
        if (rules == null) return null;

        if (index < wps.size() && !previewUndecidedFar(wps, index, from)) { // a far crossing is decided once the walk is close (leg cut)
            wps.refine(index, from.clone(), destination, previewFloorCost(rules));
        }
        if (isSurroundingLoaded(from[0], from[2])) {
            PathPreview.Leg live = liveLeg(wps, from, index, rules);
            if (verbose) {
                debug("path blocks leg from " + from[0] + "," + from[1] + "," + from[2] + " toward waypoint#" + index + ": live search "
                        + (live != null ? live.cells().size() + " cells, next waypoint#" + live.next() : "failed"));
            }
            if (live != null) return live;
        }
        PathPreview.Leg read = fallbackLeg(wps, from, index, rules);
        if (verbose) {
            debug("path blocks leg from " + from[0] + "," + from[1] + "," + from[2] + " toward waypoint#" + index
                    + (forceChunkLoading ? ", file read: " + (read != null ? read.cells().size() + " cells" : "failed") : ", not read (force chunk loading is off)"));
        }
        return read;
    }

    /** {@link #isUndecidedFar} for the preview: standing on {@code from}, on the preview's copy of the waypoints. */
    private boolean previewUndecidedFar(RouteWaypoints wps, int index, int[] from) {
        if (index >= wps.size() || !wps.isCrossing(index) || wps.isSettled(index)) return false;
        return previewDistance(from, wps.cell(index)) > CROSSING_DECISION_RANGE;
    }

    private static double previewDistance(int[] a, int[] b) {
        double dx = a[0] - b[0], dy = a[1] - b[1], dz = a[2] - b[2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Floor cost of a cell for {@link RouteWaypoints#refine}, as the walk reads it (see {@link #refineCrossing}). */
    private RouteWaypoints.CellCost previewFloorCost(PathCostRules rules) {
        return (x, y, z) -> {
            if (!world.isChunkLoaded(x >> 4, z >> 4)) return Integer.MIN_VALUE;
            org.bukkit.Material below = world.getType(x, y - 1, z);
            return below.isSolid() && !skwhy.chunkpath.block.BlockClassifier.noCollisionBlocks().contains(below) ? rules.costOf(below) : Integer.MIN_VALUE;
        };
    }

    /** A throw-away mob standing on {@code from}, with the settings of the real one, whose searches never load a chunk. */
    private LongMob previewProbe(int[] from, PathCostRules rules) {
        LongMob probe = new LongMob(this, PREVIEW_PROBE_ID, mob.getHitbox(),
                new Location(world, from[0] + 0.5, from[1], from[2] + 0.5), (float) mob.getSpeed(), rules);
        probe.setCanOpenDoors(mob.canOpenDoors());
        probe.setCostSmoothing(mob.costSmoothing());
        probe.setOnGround(true);
        probe.getNavigation().getNodeEvaluator().setNeverLoadChunks(true);
        return probe;
    }

    /** The real walk's search ({@link #tickFollowing}) in loaded terrain; null when no path can be walked (now). */
    private PathPreview.Leg liveLeg(RouteWaypoints wps, int[] from, int index, PathCostRules rules) {
        PathNavigation nav = previewProbe(from, rules).getNavigation();
        int ahead = skwhy.chunkpath.debug.ChunkPathTuning.lookahead;
        int j = ahead > 0
                ? previewShortcut(nav, wps, from, index, index + ahead, index + 1, skwhy.chunkpath.debug.ChunkPathTuning.lookaheadRatio)
                : -1;
        if (j < 0 && wps.isCrossing(index)) j = previewShortcut(nav, wps, from, index, index + 2, index + 2, THROUGH_LEG_RATIO);
        if (j < 0) {
            if (!previewMoveTo(nav, wps, from, index, previewCell(wps, index))) return null;
            j = index;
        }
        Path path = nav.getPath();
        int[] target = previewCell(wps, j);
        Node end = path.getEndNode();
        if (!path.canReach()) {
            // the walk carries on from the closest spot when it is near enough, and is stuck otherwise
            int dx = end.x - target[0], dz = end.z - target[2], dy = end.y - target[1];
            if (dx * dx + dz * dz > ARRIVAL_TOLERANCE * ARRIVAL_TOLERANCE || dy <= -2 || dy >= 3) return null;
        }
        PathPreview.Leg walked = pathCells(path, from);
        List<int[]> cells = walked.cells();
        double[] costs = walked.costs();

        // the leg heads for a crossing nobody has decided and that is still far: the walk is cut once it is close enough, and the
        // next leg starts from there (the crossing is then chosen again from that point), like the real walk does
        if (previewUndecidedFar(wps, j, from)) {
            for (int k = 1; k < cells.size(); k++) {
                if (previewDistance(cells.get(k), target) <= CROSSING_DECISION_RANGE) {
                    cells.subList(k + 1, cells.size()).clear();
                    costs = java.util.Arrays.copyOf(costs, cells.size());
                    return new PathPreview.Leg(cells, index, costs);
                }
            }
        }

        // the walk switches to the next waypoint as soon as it stands where waypoint j is considered reached
        int[] destination = destinationCell();
        int switchMode = skwhy.chunkpath.debug.ChunkPathTuning.switchMode;
        int next = j + 1;
        for (int k = 1; k < cells.size(); k++) {
            if (wps.isReached(cells.get(k), j, destination, switchMode)) {
                if (k < cells.size() - 1) {
                    cells.subList(k + 1, cells.size()).clear();
                    costs = java.util.Arrays.copyOf(costs, cells.size());
                    next = j;
                }
                break;
            }
        }
        return new PathPreview.Leg(cells, next, costs);
    }

    /** The cells a path goes through, from {@code from}, with the cost of the walk up to each (the search's own cost of the nodes). */
    private static PathPreview.Leg pathCells(Path path, int[] from) {
        List<int[]> nodes = new ArrayList<>(path.getNodeCount() + 1);
        double[] g = new double[path.getNodeCount() + 1];
        int count = 0;
        int[] first = null;
        for (int i = 0; i < path.getNodeCount(); i++) {
            Node node = path.getNode(i);
            if (first == null && (node.x != from[0] || node.y != from[1] || node.z != from[2])) {
                nodes.add(from); // the path starts on a node other than the cell: the walk starts on the cell, at no cost
                g[count++] = node.g;
            }
            first = from;
            nodes.add(new int[] { node.x, node.y, node.z });
            g[count++] = node.g;
        }
        return PathPreview.expand(nodes, java.util.Arrays.copyOf(g, count));
    }

    /**
     * {@link #moveToWaypoint} on a probe: toward waypoint {@code index}; for the exit cell of a crossing the search may end on any
     * exit cell of it, and the crossing becomes the cell it ended on.
     */
    private boolean previewMoveTo(PathNavigation nav, RouteWaypoints wps, int[] from, int index, int[] target) {
        boolean tolerant = index < wps.size() && wps.isCrossing(index);
        boolean decides = tolerant && !previewUndecidedFar(wps, index, from);
        nav.setAlsoAccept(tolerant ? (x, y, z) -> wps.acceptsExit(index, x, y, z) : null);
        try {
            boolean moving = nav.moveTo(new Location(world, target[0], target[1], target[2]),
                    skwhy.chunkpath.debug.ChunkPathTuning.legLength);
            Path path = nav.getPath();
            Node end = moving && path != null && path.canReach() ? path.getEndNode() : null;
            if (decides && end != null) wps.snapExit(index, end.x, end.y, end.z);
            return moving;
        } finally {
            nav.setAlsoAccept(null);
        }
    }

    /** {@link #tryShortcut} on a probe: the waypoints {@code maxJ} down to {@code minJ}; the one reached (its path is on {@code nav}) or -1. */
    private int previewShortcut(PathNavigation nav, RouteWaypoints wps, int[] from, int index, int maxJ, int minJ, double ratio) {
        double range = skwhy.chunkpath.debug.ChunkPathTuning.lookaheadRange;
        int size = wps.size();
        int tries = 0;
        for (int j = Math.min(size, maxJ); j >= minJ && j > index && tries < 3; j--) {
            if (j < size && wps.isEntry(j)) continue; // never aim at an entry cell
            if (j < size && !previewUndecidedFar(wps, j, from)) wps.refine(j, from.clone(), destinationCell(), previewFloorCost(mob.getCostRules()));
            int[] target = previewCell(wps, j);
            double dx = from[0] + 0.5 - target[0], dy = from[1] - target[1], dz = from[2] + 0.5 - target[2];
            if (Math.sqrt(dx * dx + dy * dy + dz * dz) > range || !isSurroundingLoaded(target[0], target[2])) continue;
            tries++;
            if (!previewMoveTo(nav, wps, from, j, target) || !nav.getPath().canReach()) { nav.stop(); continue; }
            Path path = nav.getPath();
            double walked = 0.0;
            for (int i = 1; i < path.getNodeCount(); i++) {
                Node a = path.getNode(i - 1), b = path.getNode(i);
                walked += Math.sqrt((a.x - b.x) * (double) (a.x - b.x) + (a.z - b.z) * (double) (a.z - b.z));
            }
            double chain = 0.0;
            double previousX = from[0] + 0.5, previousZ = from[2] + 0.5;
            for (int i = index; i <= j; i++) {
                int[] next = previewCell(wps, i);
                chain += Math.sqrt(Math.pow(previousX - next[0], 2) + Math.pow(previousZ - next[2], 2));
                previousX = next[0];
                previousZ = next[2];
            }
            if (walked > chain * ratio + 2.0) { nav.stop(); continue; }
            return j;
        }
        return -1;
    }

    /** What is left of the walk when the terrain is not loaded: one step across a border, or a leg read from the region file. */
    private PathPreview.Leg fallbackLeg(RouteWaypoints wps, int[] from, int index, PathCostRules rules) {
        int[] to = previewCell(wps, index);
        if (Math.abs(from[0] - to[0]) + Math.abs(from[2] - to[2]) == 1 && Math.abs(from[1] - to[1]) <= 1) {
            return new PathPreview.Leg(new ArrayList<>(List.of(from, to)), index + 1); // e.g. across a chunk border (exact, not provisional)
        }
        int cx = from[0] >> 4, cz = from[2] >> 4;
        if (!forceChunkLoading || cx != (to[0] >> 4) || cz != (to[2] >> 4)) return null;
        List<int[]> read = fileLeg(from, to, cx, cz, rules);
        return read == null ? null : new PathPreview.Leg(read, index + 1).asProvisional();
    }

    /** {@link PathPreview.LegSolver#back}: the small search that brings a position off the path back onto it (see {@link #rejoinLeg}). */
    private PathPreview.Leg previewBack(int[] from, int[] to) {
        PathCostRules rules = mob.getCostRules();
        if (rules == null) return null;
        int cx = from[0] >> 4, cz = from[2] >> 4;
        if (isSurroundingLoaded(from[0], from[2]) && isSurroundingLoaded(to[0], to[2])) {
            PathNavigation nav = previewProbe(from, rules).getNavigation();
            double distance = Math.sqrt(Math.pow(from[0] - to[0], 2) + Math.pow(from[1] - to[1], 2) + Math.pow(from[2] - to[2], 2));
            if (nav.moveTo(new Location(world, to[0], to[1], to[2]), (float) (distance * 2.0 + 8.0))) {
                Path path = nav.getPath();
                Node end = path.getEndNode();
                double dx = end.x - to[0], dz = end.z - to[2];
                if (Math.sqrt(dx * dx + dz * dz) <= REJOIN_ARRIVED_DISTANCE && Math.abs(end.y - to[1]) < 2) {
                    PathPreview.Leg walked = pathCells(path, from);
                    List<int[]> cells = walked.cells();
                    double[] costs = walked.costs();
                    if (end.x != to[0] || end.y != to[1] || end.z != to[2]) { // ends next to the cell: one more step onto it
                        cells.add(to);
                        costs = java.util.Arrays.copyOf(costs, cells.size());
                        costs[cells.size() - 1] = costs[cells.size() - 2] + 1.0;
                    }
                    return new PathPreview.Leg(cells, 0, costs);
                }
            }
        }
        if (!forceChunkLoading || cx != (to[0] >> 4) || cz != (to[2] >> 4)) return null;
        List<int[]> read = fileLeg(from, to, cx, cz, rules);
        return read == null ? null : new PathPreview.Leg(read, 0).asProvisional();
    }

    /** True when the chunk of the column and the chunks of the blocks next to it are loaded (nothing read there loads a chunk). */
    private boolean isSurroundingLoaded(int x, int z) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (!world.isChunkLoaded((x + dx) >> 4, (z + dz) >> 4)) return false;
            }
        }
        return true;
    }

    /** The walk inside an unloaded chunk, from the chunk graph read from the region file (the chunk is not loaded). */
    private List<int[]> fileLeg(int[] from, int[] to, int cx, int cz, PathCostRules rules) {
        ChunkNavMesh mesh = previewMesh(cx, cz, rules);
        if (mesh == null) return null;
        int a = meshNode(mesh, from), b = meshNode(mesh, to);
        if (a < 0 || b < 0) return null;
        int[] ids = mesh.pathBetween(a, b);
        if (ids == null) return null;
        List<int[]> cells = new ArrayList<>(ids.length + 1);
        cells.add(from);
        for (int id : ids) {
            int[] cell = { (cx << 4) + mesh.x(id), mesh.y(id), (cz << 4) + mesh.z(id) };
            if (cells.size() == 1 && cell[0] == from[0] && cell[1] == from[1] && cell[2] == from[2]) continue;
            cells.add(cell);
        }
        return cells;
    }

    private static int meshNode(ChunkNavMesh mesh, int[] cell) {
        for (int dy : new int[] { 0, -1, 1, -2, 2 }) {
            int node = mesh.nodeAt(cell[0] & 15, cell[1] + dy, cell[2] & 15);
            if (node >= 0) return node;
        }
        return -1;
    }

    private ChunkNavMesh previewMesh(int cx, int cz, PathCostRules rules) {
        long key = ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
        ChunkNavMesh mesh = previewMeshes.get(key);
        if (mesh == null) {
            try {
                mesh = ChunkPathManager.serviceFor(world).analyzer().buildMesh(cx, cz, rules);
            } catch (RuntimeException e) {
                debug("path blocks: chunk " + cx + "," + cz + " cannot be read from its region file: " + e.getMessage());
                return null;
            }
            previewMeshes.put(key, mesh);
        }
        return mesh;
    }

    // =========================================================================
    // Getters / Setters publics
    // =========================================================================

    public LongMob getMob() { return mob; }

    public int getEntityId() { return mob.getId(); }

    public Vector getHitbox()     { return mob.getHitbox(); }
    public void   setHitbox(Vector hitbox) {
        mob.setHitbox(hitbox);
        invalidatePreview();
    }

    public Location getCurrentLocation() { return mob.getLocation(); }
    /** Moves the entity; a player is never moved by a navigation (it only observes), so nothing happens for one. */
    public void setCurrentLocation(Location location) {
        if (isObserver()) return;
        mob.setLocation(location);
    }

    public Location getStart() { return startLocation != null ? startLocation.clone() : null; }

    public void setStart(Location start) {
        this.startLocation = start != null ? start.clone() : null;
        recomputeRoute();
    }

    public Location getEnd() { return endLocation != null ? endLocation.clone() : null; }

    public void setEnd(Location end) {
        this.endLocation = end != null ? end.clone() : null;
        recomputeRoute();
    }

    public double getSpeed()          { return mob.getSpeed(); }
    public void   setSpeed(float s)   { mob.setSpeed(s); }

    public List<Player> getPlayers()       { return Collections.unmodifiableList(players); }
    public void setPlayers(List<Player> p) { players.clear(); players.addAll(p); }
    public void addPlayer(Player p)        { if (!players.contains(p)) players.add(p); }
    public void removePlayer(Player p)     { players.remove(p); }

    public int  getPauseTicks()      { return pauseTicks; }

    /** A paused navigation leaves its entity alone (vanilla AI and gravity back); {@link #onResume} takes it over again. */
    public void setPauseTicks(int t) {
        this.pauseTicks = t;
        if (t != 0) {
            releaseEntity();
        }
    }

    public boolean canOpenDoors() { return mob.canOpenDoors(); }

    /** Whether wooden doors on the way may be opened; off, the entity walks around them (the leg is recomputed). */
    public void setCanOpenDoors(boolean canOpen) {
        if (canOpen == mob.canOpenDoors()) return;
        mob.setCanOpenDoors(canOpen);
        rejoinResume = null;
        legWaypoint = -1;
        mob.getNavigation().stop();
        invalidatePreview();
    }

    /** See {@link LongMob#costSmoothing()}. */
    public int getCostSmoothing() { return mob.costSmoothing(); }

    /** Size in blocks under which a more expensive patch is ignored; negative = default. The current leg is recomputed. */
    public void setCostSmoothing(int blocks) {
        mob.setCostSmoothing(blocks);
        rejoinResume = null;
        legWaypoint = -1;
        mob.getNavigation().stop();
        invalidatePreview();
    }

    /** Number of times the walk had to be cut short by moving the entity (should stay 0 on a healthy walk). */
    public int getForcedTeleports() { return forcedTeleports; }

    public Entity  getEntity()    { return mob.getEntity(); }
    public boolean isRealEntity() { return mob.isRealEntity(); }

    public Status getStatus() { return status; }

    /** Envoie un packet de téléportation aux joueurs abonnés (entité fictive uniquement). */
    public void sendTeleportPacket(Location loc) {
        WrapperPlayServerEntityTeleport packet = new WrapperPlayServerEntityTeleport(
                mob.getId(),
                new com.github.retrooper.packetevents.protocol.world.Location(
                        loc.getX(), loc.getY(), loc.getZ(),
                        loc.getYaw(), loc.getPitch()),
                mob.onGround());
        for (Player p : players) {
            PacketEvents.getAPI().getPlayerManager().sendPacket(p, packet);
        }
    }

    /** Envoie un packet de mouvement relatif aux joueurs abonnés (voir {@link skwhy.pathfinder.Navigation#sendMovePacket}). */
    public void sendMovePacket(Vector movement) {
        short dx = (short) Math.round(movement.getX() * 4096);
        short dy = (short) Math.round(movement.getY() * 4096);
        short dz = (short) Math.round(movement.getZ() * 4096);
        WrapperPlayServerEntityRelativeMove packet = new WrapperPlayServerEntityRelativeMove(mob.getId(), dx, dy, dz, mob.onGround());
        for (Player p : players) {
            PacketEvents.getAPI().getPlayerManager().sendPacket(p, packet);
        }
    }

    // =========================================================================
    // Debug / Affichage
    // =========================================================================

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append("\n╔════════════════════════════════════════════\n");
        sb.append("║ LONG NAVIGATION INFO\n");
        sb.append("╠════════════════════════════════════════════\n");

        sb.append("║ [État]\n");
        if (pauseTicks == PAUSED_INDEFINITELY) {
            sb.append("║ Pause: PAUSE INDÉFINIE (Arrêté)\n");
        } else if (pauseTicks > 0) {
            sb.append("║ Pause: EN PAUSE (Reste ").append(pauseTicks).append(" ticks)\n");
        } else {
            sb.append("║ Pause: ACTIF\n");
        }
        sb.append("║ Statut: ").append(status).append("\n");

        sb.append("║\n║ [Positions]\n");
        appendLoc(sb, "Départ      ", startLocation);
        appendLoc(sb, "Arrivée     ", endLocation);
        appendLoc(sb, "Position act", mob.getLocation());

        sb.append("║\n║ [Route de portes]\n");
        if (route == null) {
            sb.append("║ Route: aucune (jamais calculée ou échec)\n");
        } else {
            sb.append("║ Portes dans la route: ").append(route.size()).append(" (").append(waypoints.size())
                    .append(" points de passage)\n");
            sb.append("║ Index du point visé: ").append(waypointIndex)
                    .append(waypointIndex >= waypoints.size() ? " (destination finale)\n" : "\n");
            if (waypointIndex < waypoints.size()) {
                Gate g = waypoints.owner(waypointIndex);
                int[] c = waypoints.cell(waypointIndex);
                sb.append("║ Point visé: ").append(c[0]).append(",").append(c[1]).append(",").append(c[2])
                        .append(" (porte chunk(").append(g.chunkX).append(",").append(g.chunkZ)
                        .append(") id=").append(g.id).append(" bord=").append(g.edge).append(")\n");
            }
        }

        if (status == Status.WAITING_FOR_CHUNK) {
            sb.append("║\n║ [Attente chunk non chargé]\n");
            long now = world.getGameTime();
            sb.append("║ Attente depuis: ").append(now - waitStartTick).append(" / ").append(expectedWaitTicks).append(" ticks\n");
        }

        sb.append("║\n║ [Réseau / Joueurs]\n");
        sb.append("║ Joueurs ciblés par les packets: ").append(players.isEmpty() ? "Aucun (ou géré par Bukkit)" : String.valueOf(players.size())).append("\n");

        sb.append("║\n");
        sb.append(mob.toString());
        sb.append(mob.getNavigation().toString());

        return sb.toString();
    }

    private static void appendLoc(StringBuilder sb, String label, Location loc) {
        if (loc == null) {
            sb.append("║ ").append(label).append(": Aucune\n");
        } else {
            sb.append(String.format("║ %s: X:%.2f | Y:%.2f | Z:%.2f (%s)\n",
                    label, loc.getX(), loc.getY(), loc.getZ(), loc.getWorld() != null ? loc.getWorld().getName() : "?"));
        }
    }
}
