package skwhy.data;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRelativeMoveAndRotation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * CustomEntity — entité virtuelle gérée par packet, sans entité Bukkit réelle.
 *
 * Dépendances requises :
 *   - PacketEvents  (com.github.retrooper:packetevents-bukkit)
 *   - Paper / Spigot API
 *
 * Intégration dans votre plugin :
 *   - Appelez entity.tick() depuis un BukkitRunnable ou un listener TickEvent
 *     à chaque tick serveur (20 tps).
 */
public class FakePathFinding {

    // -------------------------------------------------------------------------
    // Enums
    // -------------------------------------------------------------------------

    /**
     * Types de pathfinding disponibles, calqués sur les comportements vanilla.
     */
    public enum PathfindingType {
        /** Marche au sol, évite l'eau (zombie, squelette…) */
        WALK,
        /** Marche au sol, traverse l'eau (noyé, gardien…) */
        WALK_WATER,
        /** Nage uniquement dans l'eau (poisson, squid…) */
        SWIM,
        /** Vole librement en 3D (chauve-souris, fantôme…) */
        FLY,
        /** Vole mais reste au sol si possible (abeille, feu follet…) */
        FLY_GROUND,
        /** Grimpe les surfaces verticales (araignée…) */
        CLIMB,
        /** Aucun déplacement autonome */
        NONE
    }

    // -------------------------------------------------------------------------
    // Constantes
    // -------------------------------------------------------------------------

    /** Valeur sentinelle : l'entité ne fait rien tant que pauseTicks == PAUSED_INDEFINITELY */
    public static final int PAUSED_INDEFINITELY = -1;

    /** Rayon autour de la destination considéré comme « arrivé » */
    private static final double ARRIVAL_THRESHOLD = 0.35;

    /** Nombre de ticks entre deux recalculs de chemin complets */
    private static final int PATH_RECALC_INTERVAL = 10;

    /** Coût de franchissement d'un bloc d'eau pour les entités non-aquatiques */
    private static final double WATER_COST = 8.0;

    // -------------------------------------------------------------------------
    // Champs définis par le constructeur
    // -------------------------------------------------------------------------

    private final int entityId;
    private Vector hitbox;           // largeur, hauteur, profondeur
    private Location location;
    private double speed;            // blocs/tick
    private final List<Player> players;
    private PathfindingType pathfindingType;

    // -------------------------------------------------------------------------
    // Champs optionnels (non définis par le constructeur)
    // -------------------------------------------------------------------------

    /** Destination du pathfinding. null = aucune destination. */
    private Location destination;

    /**
     * Temps de pause en ticks.
     *  -1  → PAUSED_INDEFINITELY : l'entité attend indéfiniment
     *   0  → pas de pause, l'entité bouge normalement
     *  > 0 → décompte à chaque tick, puis l'entité reprend
     */
    private int pauseTicks = PAUSED_INDEFINITELY;

    // -------------------------------------------------------------------------
    // État interne du pathfinding (non exposé)
    // -------------------------------------------------------------------------

    /** Chemin calculé : liste de positions à atteindre dans l'ordre */
    private final Deque<Location> path = new ArrayDeque<>();

    /** Ticks depuis le dernier recalcul complet */
    private int ticksSinceRecalc = 0;

    /** Destination utilisée pour calculer le chemin actuel */
    private Location lastPathDestination = null;

    /** Vitesse utilisée lors du dernier recalcul (pour détecter les changements) */
    private double lastSpeed;

    // -------------------------------------------------------------------------
    // Constructeur
    // -------------------------------------------------------------------------

    /**
     * @param entityId       ID de l'entité (doit être unique et réservé côté serveur)
     * @param hitbox         Dimensions de la hitbox (x=largeur, y=hauteur, z=profondeur)
     * @param location       Position initiale
     * @param speed          Vitesse en blocs/tick (ex: 0.2 pour un zombie)
     * @param players        Liste des joueurs qui reçoivent les packets
     * @param pathfindingType Type de navigation
     */
    public FakePathFinding(int entityId,
                        Vector hitbox,
                        Location location,
                        double speed,
                        List<Player> players,
                        PathfindingType pathfindingType) {
        this.entityId      = entityId;
        this.hitbox        = hitbox.clone();
        this.location      = location.clone();
        this.speed         = speed;
        this.players       = new ArrayList<>(players);
        this.pathfindingType = pathfindingType;
        this.lastSpeed     = speed;
    }

    // -------------------------------------------------------------------------
    // Tick principal — à appeler chaque tick serveur
    // -------------------------------------------------------------------------

    /**
     * Méthode principale à invoquer à chaque tick (20x/s).
     * Gère la pause, le pathfinding et l'envoi des packets.
     */
    public void tick() {

        // 1. Pause indéfinie → on ne fait rien du tout
        if (pauseTicks == PAUSED_INDEFINITELY) return;

        // 2. Décompte de pause
        if (pauseTicks > 0) {
            pauseTicks--;
            return;
        }

        // 3. Pas de destination → immobile
        if (destination == null) return;

        // 4. Déjà arrivé ?
        if (location.distanceSquared(destination) <= ARRIVAL_THRESHOLD * ARRIVAL_THRESHOLD) {
            path.clear();
            return;
        }

        // 5. Recalcul de chemin si nécessaire
        boolean speedChanged = lastSpeed != speed;
        boolean needsRecalc  = path.isEmpty()
                || ticksSinceRecalc >= PATH_RECALC_INTERVAL
                || !isSameBlock(lastPathDestination, destination)
                || speedChanged;

        if (needsRecalc) {
            recalculatePath();
            ticksSinceRecalc = 0;
            lastSpeed        = speed;
        } else {
            ticksSinceRecalc++;
        }

        // 6. Avancer le long du chemin
        if (path.isEmpty()) return;

        Location next = path.peek();

        // Si le prochain waypoint est atteint, on passe au suivant
        if (location.distanceSquared(next) <= ARRIVAL_THRESHOLD * ARRIVAL_THRESHOLD) {
            path.poll();
            if (path.isEmpty()) return;
            next = path.peek();
        }

        // Calcul du vecteur de déplacement
        Vector direction = next.toVector().subtract(location.toVector());
        double dist = direction.length();

        Location previousLocation = location.clone();

        if (dist <= speed) {
            // On atteint le waypoint ce tick
            location = next.clone();
        } else {
            direction.normalize().multiply(speed);
            location.add(direction);
        }

        // Ajustement vertical selon le type de pathfinding
        applyGravityOrFlight();

        // 7. Envoi du packet de déplacement
        sendMovePacket(previousLocation, location);
    }

    // -------------------------------------------------------------------------
    // Pathfinding — A* adapté vanilla
    // -------------------------------------------------------------------------

    /**
     * Recalcule le chemin complet depuis {@code location} vers {@code destination}
     * via A* adapté au type de navigation de l'entité.
     */
    private void recalculatePath() {
        path.clear();
        lastPathDestination = destination.clone();

        World world = location.getWorld();
        if (world == null) return;

        BlockPos start = BlockPos.of(location);
        BlockPos end   = BlockPos.of(destination);

        List<BlockPos> found = aStar(world, start, end);

        if (found != null) {
            for (BlockPos bp : found) {
                // Centre du bloc + offset vertical selon type
                double yOffset = getYOffset(world, bp);
                path.add(new Location(world,
                        bp.x + 0.5,
                        bp.y + yOffset,
                        bp.z + 0.5));
            }
            // Ajouter la position exacte de la destination en dernier
            path.add(destination.clone());
        }
    }

    /**
     * Algorithme A* standard, optimisé :
     *  - Heuristique Octile (meilleure que Manhattan en 3D)
     *  - Voisins générés selon le type de pathfinding
     *  - Limite de nœuds pour éviter le freeze sur les longs chemins
     */
    private List<BlockPos> aStar(World world, BlockPos start, BlockPos end) {
        final int MAX_NODES = 1024;

        Map<BlockPos, BlockPos> cameFrom = new HashMap<>();
        Map<BlockPos, Double>   gScore   = new HashMap<>();
        Map<BlockPos, Double>   fScore   = new HashMap<>();

        PriorityQueue<BlockPos> open = new PriorityQueue<>(
                Comparator.comparingDouble(n -> fScore.getOrDefault(n, Double.MAX_VALUE))
        );

        gScore.put(start, 0.0);
        fScore.put(start, heuristic(start, end));
        open.add(start);

        int explored = 0;

        while (!open.isEmpty() && explored < MAX_NODES) {
            BlockPos current = open.poll();
            explored++;

            if (current.equals(end)) {
                return reconstructPath(cameFrom, current);
            }

            for (BlockPos neighbor : getNeighbors(world, current)) {
                double tentativeG = gScore.getOrDefault(current, Double.MAX_VALUE)
                        + moveCost(world, current, neighbor);

                if (tentativeG < gScore.getOrDefault(neighbor, Double.MAX_VALUE)) {
                    cameFrom.put(neighbor, current);
                    gScore.put(neighbor, tentativeG);
                    fScore.put(neighbor, tentativeG + heuristic(neighbor, end));
                    open.remove(neighbor);
                    open.add(neighbor);
                }
            }
        }

        // Chemin non trouvé : retourner le meilleur nœud partiel
        return bestPartialPath(cameFrom, gScore, end);
    }

    /** Heuristique Octile 3D */
    private double heuristic(BlockPos a, BlockPos b) {
        double dx = Math.abs(a.x - b.x);
        double dy = Math.abs(a.y - b.y);
        double dz = Math.abs(a.z - b.z);
        double dMax = Math.max(dx, Math.max(dy, dz));
        double dMin = Math.min(dx, Math.min(dy, dz));
        double dMid = dx + dy + dz - dMax - dMin;
        return dMax + (Math.sqrt(3) - 2) * dMin + (Math.sqrt(2) - 1) * dMid;
    }

    /** Coût de déplacement entre deux nœuds voisins */
    private double moveCost(World world, BlockPos from, BlockPos to) {
        double base = from.distanceTo(to); // 1.0 ou ~1.41 en diagonal
        Block b = world.getBlockAt(to.x, to.y, to.z);

        if (isWater(b) && pathfindingType != PathfindingType.SWIM
                       && pathfindingType != PathfindingType.WALK_WATER) {
            base += WATER_COST;
        }
        return base;
    }

    /** Voisins accessibles selon le type de pathfinding */
    private List<BlockPos> getNeighbors(World world, BlockPos pos) {
        List<BlockPos> neighbors = new ArrayList<>();

        switch (pathfindingType) {
            case FLY:
                // 6 directions + diagonales 3D
                for (int dx = -1; dx <= 1; dx++)
                    for (int dy = -1; dy <= 1; dy++)
                        for (int dz = -1; dz <= 1; dz++) {
                            if (dx == 0 && dy == 0 && dz == 0) continue;
                            BlockPos n = new BlockPos(pos.x + dx, pos.y + dy, pos.z + dz);
                            if (isPassable(world, n)) neighbors.add(n);
                        }
                break;

            case FLY_GROUND:
                // Horizontal + léger vol, préfère rester proche du sol
                for (int dx = -1; dx <= 1; dx++)
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dz == 0) continue;
                        for (int dy = -1; dy <= 1; dy++) {
                            BlockPos n = new BlockPos(pos.x + dx, pos.y + dy, pos.z + dz);
                            if (isPassable(world, n)) { neighbors.add(n); break; }
                        }
                    }
                break;

            case SWIM:
                // Se déplace uniquement dans l'eau
                int[][] swimDirs = {{1,0,0},{-1,0,0},{0,0,1},{0,0,-1},{0,1,0},{0,-1,0}};
                for (int[] d : swimDirs) {
                    BlockPos n = new BlockPos(pos.x + d[0], pos.y + d[1], pos.z + d[2]);
                    Block b = world.getBlockAt(n.x, n.y, n.z);
                    if (isWater(b)) neighbors.add(n);
                }
                break;

            case CLIMB:
                // Horizontal + vertical sur surfaces solides
                addWalkNeighbors(world, pos, neighbors);
                // Montée verticale (grimper)
                BlockPos up = new BlockPos(pos.x, pos.y + 1, pos.z);
                if (isPassable(world, up)) neighbors.add(up);
                break;

            case WALK:
            case WALK_WATER:
            default:
                addWalkNeighbors(world, pos, neighbors);
                break;
        }

        return neighbors;
    }

    /** Voisins de marche standard (4 directions + saut d'1 bloc + descente) */
    private void addWalkNeighbors(World world, BlockPos pos, List<BlockPos> neighbors) {
        int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}};
        for (int[] d : dirs) {
            int nx = pos.x + d[0];
            int nz = pos.z + d[1];

            // Même niveau
            BlockPos flat = new BlockPos(nx, pos.y, nz);
            if (canWalkTo(world, flat)) { neighbors.add(flat); continue; }

            // Saut : +1 en hauteur
            BlockPos jump = new BlockPos(nx, pos.y + 1, nz);
            if (canWalkTo(world, jump)) { neighbors.add(jump); continue; }

            // Descente : -1 en hauteur
            BlockPos drop = new BlockPos(nx, pos.y - 1, nz);
            if (canWalkTo(world, drop)) neighbors.add(drop);
        }
    }

    /** Un nœud de marche est valide si le sol est solide et l'espace au-dessus libre */
    private boolean canWalkTo(World world, BlockPos pos) {
        Block floor = world.getBlockAt(pos.x, pos.y - 1, pos.z);
        Block body  = world.getBlockAt(pos.x, pos.y,     pos.z);
        Block head  = world.getBlockAt(pos.x, pos.y + 1, pos.z);
        return floor.getType().isSolid()
                && !body.getType().isSolid()
                && !head.getType().isSolid();
    }

    private boolean isPassable(World world, BlockPos pos) {
        return !world.getBlockAt(pos.x, pos.y, pos.z).getType().isSolid();
    }

    private boolean isWater(Block b) {
        return b.getType() == Material.WATER;
    }

    private double getYOffset(World world, BlockPos bp) {
        return switch (pathfindingType) {
            case FLY, SWIM -> 0.5;
            default -> 1.0; // pied de l'entité au-dessus du sol
        };
    }

    private void applyGravityOrFlight() {
        if (pathfindingType == PathfindingType.WALK
                || pathfindingType == PathfindingType.WALK_WATER) {
            // Coller au sol : descendre jusqu'au premier bloc solide
            World world = location.getWorld();
            if (world != null) {
                Block below = world.getBlockAt(
                        (int) Math.floor(location.getX()),
                        (int) Math.floor(location.getY()) - 1,
                        (int) Math.floor(location.getZ()));
                if (!below.getType().isSolid()) {
                    location.subtract(0, speed, 0); // gravité simplifiée
                }
            }
        }
    }

    private List<BlockPos> reconstructPath(Map<BlockPos, BlockPos> cameFrom, BlockPos current) {
        LinkedList<BlockPos> total = new LinkedList<>();
        while (cameFrom.containsKey(current)) {
            total.addFirst(current);
            current = cameFrom.get(current);
        }
        return total;
    }

    /** Retourne le chemin partiel vers le nœud le plus proche de la destination */
    private List<BlockPos> bestPartialPath(Map<BlockPos, BlockPos> cameFrom,
                                           Map<BlockPos, Double> gScore,
                                           BlockPos end) {
        if (cameFrom.isEmpty()) return Collections.emptyList();
        BlockPos best = cameFrom.keySet().stream()
                .min(Comparator.comparingDouble(n -> heuristic(n, end)))
                .orElse(null);
        return best != null ? reconstructPath(cameFrom, best) : Collections.emptyList();
    }

    private boolean isSameBlock(Location a, Location b) {
        if (a == null || b == null) return false;
        return (int) a.getX() == (int) b.getX()
                && (int) a.getY() == (int) b.getY()
                && (int) a.getZ() == (int) b.getZ();
    }

    // -------------------------------------------------------------------------
    // Packets PacketEvents
    // -------------------------------------------------------------------------

    /**
     * Envoie un packet de déplacement relatif ou téléportation à tous les joueurs
     * de la liste, via PacketEvents.
     */
    private void sendMovePacket(Location from, Location to) {
        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();

        // PacketEvents encode les deltas en 1/4096ème de bloc (short)
        short pDx = (short) (dx * 4096);
        short pDy = (short) (dy * 4096);
        short pDz = (short) (dz * 4096);

        boolean tooFar = Math.abs(dx) > 8 || Math.abs(dy) > 8 || Math.abs(dz) > 8;

        for (Player player : players) {
            if (!player.isOnline()) continue;

            if (tooFar) {
                // Téléportation absolue si déplacement trop grand
                WrapperPlayServerEntityTeleport teleport = new WrapperPlayServerEntityTeleport(
                        entityId,
                        new com.github.retrooper.packetevents.protocol.world.Location(
                                to.getX(), to.getY(), to.getZ(),
                                to.getYaw(), to.getPitch()),
                        false
                );
                PacketEvents.getAPI().getPlayerManager()
                        .sendPacket(player, teleport);
            } else {
                float yaw   = to.getYaw();
                float pitch = to.getPitch();

                WrapperPlayServerEntityRelativeMoveAndRotation move =
                        new WrapperPlayServerEntityRelativeMoveAndRotation(
                                entityId, pDx, pDy, pDz,
                                yaw, pitch, false
                        );
                PacketEvents.getAPI().getPlayerManager()
                        .sendPacket(player, move);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Getters / Setters — constructeur
    // -------------------------------------------------------------------------

    public int getEntityId() { return entityId; }

    // --- Hitbox ---
    public Vector getHitbox() { return hitbox.clone(); }
    public void setHitbox(Vector hitbox) { this.hitbox = hitbox.clone(); }

    // --- Location ---
    public Location getLocation() { return location.clone(); }
    public void setLocation(Location location) {
        this.location = location.clone();
        // Forcer un recalcul de chemin dès le prochain tick
        ticksSinceRecalc = PATH_RECALC_INTERVAL;
    }

    // --- Speed ---
    public double getSpeed() { return speed; }
    public void setSpeed(double speed) { this.speed = speed; }

    // --- PathfindingType ---
    public PathfindingType getPathfindingType() { return pathfindingType; }
    public void setPathfindingType(PathfindingType type) {
        this.pathfindingType = type;
        path.clear(); // invalider le chemin actuel
    }

    // --- Players ---
    public List<Player> getPlayers() { return Collections.unmodifiableList(players); }
    public void setPlayers(List<Player> players) {
        this.players.clear();
        this.players.addAll(players);
    }
    public void addPlayer(Player player) {
        if (!players.contains(player)) players.add(player);
    }
    public void removePlayer(Player player) { players.remove(player); }

    // -------------------------------------------------------------------------
    // Getters / Setters — champs optionnels
    // -------------------------------------------------------------------------

    // --- Destination ---
    public Location getDestination() { return destination != null ? destination.clone() : null; }
    public void setDestination(Location destination) {
        this.destination = destination != null ? destination.clone() : null;
        path.clear(); // forcer un recalcul immédiat
        ticksSinceRecalc = PATH_RECALC_INTERVAL;
    }

    // --- PauseTicks ---
    /**
     * @return Le nombre de ticks de pause restants,
     *         ou {@link #PAUSED_INDEFINITELY} si l'entité est en pause indéfinie.
     */
    public int getPauseTicks() { return pauseTicks; }

    /**
     * Définit le temps de pause.
     * @param ticks {@link #PAUSED_INDEFINITELY} pour une pause indéfinie,
     *              0 pour reprendre immédiatement, ou un nombre positif de ticks.
     */
    public void setPauseTicks(int ticks) { this.pauseTicks = ticks; }

    // -------------------------------------------------------------------------
    // Classe utilitaire BlockPos
    // -------------------------------------------------------------------------

    /** Coordonnées entières d'un bloc, immuables. */
    private static final class BlockPos {
        final int x, y, z;

        BlockPos(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }

        static BlockPos of(Location loc) {
            return new BlockPos(
                    (int) Math.floor(loc.getX()),
                    (int) Math.floor(loc.getY()),
                    (int) Math.floor(loc.getZ())
            );
        }

        double distanceTo(BlockPos other) {
            int dx = this.x - other.x;
            int dy = this.y - other.y;
            int dz = this.z - other.z;
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof BlockPos)) return false;
            BlockPos b = (BlockPos) o;
            return x == b.x && y == b.y && z == b.z;
        }

        @Override
        public int hashCode() { return Objects.hash(x, y, z); }

        @Override
        public String toString() { return "(" + x + "," + y + "," + z + ")"; }
    }
}
