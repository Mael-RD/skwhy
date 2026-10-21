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
import skwhy.chunkpath.move.LongMob;
import skwhy.chunkpath.move.navigation.LongGroundPathNavigation;
import skwhy.chunkpath.move.navigation.PathNavigation;
import skwhy.chunkpath.pathfinding.GatePathfinder;
import skwhy.modules.LongNavigationElements.ChunkPathManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
    private static final long WAIT_RECHECK_INTERVAL_TICKS = 20L; // "revérifier régulièrement"
    private static final long STUCK_RETRY_INTERVAL_TICKS = 20L;
    private static final long NO_PROGRESS_TICKS = 100L; // 5 s without getting closer to the waypoint
    private static final double PROGRESS_EPSILON = 0.3; // blocks
    private static final double ARRIVAL_TOLERANCE = 3.0; // blocks, accepted as 'arrived' when the last steps cannot be walked
    private static final int MAX_LEG_SEARCHES_PER_TICK = 3; // A* budget shared by all navigations
    private static final long ABSOLUTE_SYNC_INTERVAL_TICKS = 100L;
    private static final double REACHED_DISTANCE = 1.0; // blocks, horizontally, around a waypoint's block centre

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
            // vanilla behaviour only while the navigation is not actively driving the entity
            if (newStatus == Status.IDLE || newStatus == Status.ARRIVED || newStatus == Status.FAILED_NO_PATH) {
                releaseEntity();
            } else {
                takeOverEntity(mob.getEntity());
            }
        }
        if (newStatus != status) {
            debug(status + " -> " + newStatus + " (" + why + ") pos=" + fmt(mob.getLocation())
                    + " waypoint#" + waypointIndex + "=" + fmt(currentWaypointLocation()));
        }
        status = newStatus;
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

    private long waitStartTick;
    private long lastWaitCheckTick;
    private long expectedWaitTicks;
    private long lastStuckRetryTick;

    // =========================================================================
    // Constructeurs
    // =========================================================================

    /** Entité virtuelle (sans objet Bukkit Entity). */
    public LongNavigation(int entityId, Vector hitbox, Location location, float speed, List<Player> players) {
        this.world = location.getWorld();
        PathCostRules costRules = ChunkPathManager.serviceFor(world).analyzer().costRules();
        this.mob = new LongMob(this, entityId, hitbox, location, speed, costRules);
        this.startLocation = location.clone();
        this.players = new ArrayList<>(players);
        register();
    }

    /** Entité Bukkit réelle. */
    public LongNavigation(Entity entity, float speed) {
        this.world = entity.getWorld();
        PathCostRules costRules = ChunkPathManager.serviceFor(world).analyzer().costRules();
        this.mob = new LongMob(this, entity, speed, costRules);
        this.startLocation = entity.getLocation();
        this.players = new ArrayList<>();
        register();
    }

    // =========================================================================
    // Tick principal
    // =========================================================================

    public void tick() {
        if (pauseTicks == PAUSED_INDEFINITELY) return;
        if (pauseTicks > 0) { pauseTicks--; return; }
        if (status == Status.IDLE || status == Status.ARRIVED || status == Status.FAILED_NO_PATH) return;

        if (status == Status.WAITING_FOR_CHUNK) {
            tickWaiting();
        } else {
            tickFollowing();
        }

        mob.tick();
        syncVirtualEntity();

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

        if (noProgressFor(waypoint, now)) {
            return;
        }

        PathNavigation nav = mob.getNavigation();
        if (legWaypoint == waypointIndex && !nav.isDone()) {
            setStatus(Status.FOLLOWING, "leg in progress");
            return; // the leg path is being walked by the mob (ticked right after)
        }

        if (legSearchBudget <= 0) {
            return; // too many A* searches this tick already, this navigation gets its turn on a later one
        }
        legSearchBudget--;
        boolean moving = nav.moveTo(waypoint, MAX_LEG_PATH_LENGTH);
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
            mob.getNavigation().stop();
            legWaypoint = -1;
            return false;
        }
        if (route != null && waypointIndex >= route.size() && mob.getLocation().distance(waypoint) <= ARRIVAL_TOLERANCE) {
            debug("no progress but within " + ARRIVAL_TOLERANCE + " blocks of the destination: arrived");
            advanceWaypoint();
            return true;
        }
        debug("still no progress toward waypoint#" + waypointIndex + ": moving the mob to the waypoint");
        teleportMob(waypoint.clone().add(0.5, 0.0, 0.5));
        mob.getNavigation().stop();
        stuckLevel = 0;
        advanceWaypoint();
        return true;
    }

    /** Close enough to a waypoint (gate block or destination) to consider it reached. */
    private boolean hasReached(Location waypoint) {
        Location current = mob.getLocation();
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
    }

    private void tickWaiting() {
        long now = world.getGameTime();
        if (now - lastWaitCheckTick < WAIT_RECHECK_INTERVAL_TICKS) return;
        lastWaitCheckTick = now;

        Location immediateTarget = currentWaypointLocation();
        if (immediateTarget != null && isChunkLoaded(immediateTarget)
                && mob.getNavigation().moveTo(immediateTarget, MAX_LEG_PATH_LENGTH)) {
            // The direct continuation loaded during the wait: resume as if nothing happened.
            legWaypoint = waypointIndex;
            setStatus(Status.FOLLOWING, "chunks loaded, leg path found");
            return;
        }

        long elapsed = now - waitStartTick;
        if (elapsed <= expectedWaitTicks) return; // still within the estimated travel time, keep waiting

        if (route != null) {
            for (int i = waypointIndex; i < route.size(); i++) {
                Gate gate = route.get(i);
                if (world.isChunkLoaded(gate.chunkX, gate.chunkZ)) {
                    teleportMob(gateLocation(gate));
                    waypointIndex = i;
                    legWaypoint = -1;
                    setStatus(Status.FOLLOWING, "state machine");
                    return;
                }
            }
        }

        // Nothing loaded anywhere ahead: give up on a realistic walk and arrive directly.
        teleportMob(endLocation);
        setStatus(Status.ARRIVED, "state machine");
    }

    private boolean isChunkLoaded(Location loc) {
        return world.isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
    }

    private void teleportMob(Location location) {
        mob.setLocation(location);
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

        PathCostRules costRules = ChunkPathManager.serviceFor(world).analyzer().costRules();
        double weightedDistance = null != route && waypointIndex > 0 && waypointIndex < route.size()
                ? sameChunkDistanceOrFallback(route.get(waypointIndex - 1), route.get(waypointIndex), costRules, from, to)
                : straightLineWeightedDistance(from, to, costRules);

        float speed = mob.getSpeed();
        if (speed <= 0F) return 0L;
        return (long) Math.ceil(weightedDistance / speed);
    }

    private double sameChunkDistanceOrFallback(Gate previous, Gate current, PathCostRules costRules,
                                                Location from, Location to) {
        if (previous.chunkX == current.chunkX && previous.chunkZ == current.chunkZ) {
            Integer known = previous.internalDistances.get(current.id);
            if (known != null) return known;
        }
        return straightLineWeightedDistance(from, to, costRules);
    }

    private double straightLineWeightedDistance(Location from, Location to, PathCostRules costRules) {
        return from.distance(to) * costRules.defaultCost();
    }

    // =========================================================================
    // Route / waypoints
    // =========================================================================

    private void recomputeRoute() {
        if (startLocation == null || endLocation == null) {
            setStatus(Status.IDLE, "state machine");
            route = null;
            return;
        }
        if (startLocation.getWorld() == null || !startLocation.getWorld().equals(endLocation.getWorld())) {
            Skript.warning("[ChunkPath] La position de départ et d'arrivée d'une long navigation doivent être dans le même monde.");
            setStatus(Status.FAILED_NO_PATH, "state machine");
            route = null;
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
        route = GatePathfinder.findPath(service,
                from.getBlockX(), from.getBlockY(), from.getBlockZ(),
                endLocation.getBlockX(), endLocation.getBlockY(), endLocation.getBlockZ());

        if (route == null) {
            setStatus(Status.FAILED_NO_PATH, "state machine");
            return;
        }

        waypointIndex = 0;
        legWaypoint = -1;
        setStatus(Status.FOLLOWING, "state machine");
    }

    private void advanceWaypoint() {
        if (route == null || waypointIndex >= route.size()) {
            setStatus(Status.ARRIVED, "final destination reached");
            mob.getNavigation().stop();
        } else {
            waypointIndex++;
            legWaypoint = -1;
        }
    }

    private Location currentWaypointLocation() {
        if (route == null) return null;
        if (waypointIndex < route.size()) {
            return gateLocation(route.get(waypointIndex));
        }
        if (waypointIndex == route.size()) {
            return endLocation;
        }
        return null;
    }

    private Location gateLocation(Gate gate) {
        int[] r = gate.representativeWorld;
        return new Location(world, r[0], r[1], r[2]);
    }

    // =========================================================================
    // Getters / Setters publics
    // =========================================================================

    public LongMob getMob() { return mob; }

    public int getEntityId() { return mob.getId(); }

    public Vector getHitbox()     { return mob.getHitbox(); }
    public void   setHitbox(Vector hitbox) { mob.setHitbox(hitbox); }

    public Location getCurrentLocation() { return mob.getLocation(); }
    public void setCurrentLocation(Location location) { mob.setLocation(location); }

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
    public void setPauseTicks(int t) { this.pauseTicks = t; }

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
            sb.append("║ Portes dans la route: ").append(route.size()).append("\n");
            sb.append("║ Index de la porte visée: ").append(waypointIndex)
                    .append(waypointIndex >= route.size() ? " (destination finale)\n" : "\n");
            if (waypointIndex < route.size()) {
                Gate g = route.get(waypointIndex);
                sb.append("║ Porte visée: chunk(").append(g.chunkX).append(",").append(g.chunkZ)
                        .append(") id=").append(g.id).append(" bord=").append(g.edge).append("\n");
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
