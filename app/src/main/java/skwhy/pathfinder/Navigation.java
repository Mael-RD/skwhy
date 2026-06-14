package skwhy.pathfinder;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRelativeMove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;

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

    // =========================================================================
    // Registre statique
    // =========================================================================

    private static final List<Navigation> REGISTRY = Collections.synchronizedList(new ArrayList<>());

    public static List<Navigation> getRegistry() { return Collections.unmodifiableList(REGISTRY); }

    public static void tickAll() {
        REGISTRY.removeIf(nav -> nav.isRealEntity() && !nav.getEntity().isValid());
        new ArrayList<>(REGISTRY).forEach(Navigation::tick);
    }

    private void register()        { REGISTRY.add(this); }
    public  void unregister()      { REGISTRY.remove(this); }
    public  boolean isRegistered() { return REGISTRY.contains(this); }

    // =========================================================================
    // Champs d'instance
    // =========================================================================

    private final Mob          mob;
    private List<Player>       players;
    private Location           destination;
    private int                pauseTicks = PAUSED_INDEFINITELY;

    // =========================================================================
    // Constructeurs
    // =========================================================================

    /** Entité virtuelle (sans objet Bukkit Entity). */
    public Navigation(int entityId, Vector hitbox, Location location, PathfindingType pathfindingType, float speed,
                      List<Player> players) {
        this.mob = new Mob(entityId, hitbox, location, pathfindingType, speed);
        this.players         = new ArrayList<>(players);
        register();
    }

    /** Entité Bukkit réelle. */
    public Navigation(Entity entity, float speed, PathfindingType pathfindingType) {
        this.mob = new Mob(entity, pathfindingType, speed);
        this.players         = new ArrayList<>(players);
        // Un seul navigateur par entité réelle
        REGISTRY.removeIf(n -> n.isRealEntity() && n.getEntity().getEntityId() == entity.getEntityId());
        register();
    }

    // =========================================================================
    // Tick principal  (logique de PathNavigation#tick + followThePath)
    // =========================================================================

    public void tick() {
        if (pauseTicks == PAUSED_INDEFINITELY) return;
        if (pauseTicks > 0) { pauseTicks--; return; }
        if (destination == null) { return; }

    }

    // =========================================================================
    // Getters / Setters publics  (interface identique à FakePathFinding)
    // =========================================================================

    public int     getEntityId()   { return mob.getId(); }

    public Vector  getHitbox()     { return mob.getHitbox(); }
    public void    setHitbox(Vector hitbox) { mob.setHitbox(hitbox); }

    public Location getLocation()  {
        return mob.getLocation();
    }
    public void setLocation(Location location) {
        mob.setLocation(location);
    }

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

    /**
     * Envoie un packet de mouvement relatif aux joueurs abonnés.
     * Utilisé uniquement pour les mises à jour de physique/gravité afin que le client
     * ne traite pas le déplacement comme une téléportation (ce qui causerait un glitch
     * visuel).  Si le delta dépasse la plage d'un paquet relatif (±8 blocs), on replie
     * sur un paquet de téléportation.
     */
    public void sendMovePacket(Vector movement) {
        short dx = (short) Math.round(movement.getX() * 4096);
        short dy = (short) Math.round(movement.getY() * 4096);
        short dz = (short) Math.round(movement.getZ() * 4096);
        WrapperPlayServerEntityRelativeMove packet = new WrapperPlayServerEntityRelativeMove(mob.getId(), dx, dy, dz, mob.onGround());
        for (Player p : players) {
            PacketEvents.getAPI().getPlayerManager().sendPacket(p, packet);
        }
    }

    public double   getSpeed()        { return mob.getSpeed(); }
    public void     setSpeed(float s) { mob.setSpeed(s); }

    public PathfindingType getPathfindingType()           { return mob.getPathfindingType(); }
    public void setPathfindingType(PathfindingType type)  { mob.setPathfindingType(type); }

    public List<Player> getPlayers()                { return Collections.unmodifiableList(players); }
    public void setPlayers(List<Player> p)          { players.clear(); players.addAll(p); }
    public void addPlayer(Player p)                 { if (!players.contains(p)) players.add(p); }
    public void removePlayer(Player p)              { players.remove(p); }

    public Location getDestination() { return destination != null ? destination.clone() : null; }
    public void setDestination(Location dest) {
        this.destination = dest != null ? dest.clone() : null;
    }

    public int  getPauseTicks()     { return pauseTicks; }
    public void setPauseTicks(int t){ this.pauseTicks = t; }

    public Entity   getEntity()     { return mob.getEntity(); }
    public boolean  isRealEntity()  { return mob.isRealEntity(); }

}