package skwhy.chunkpath.debug;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import skwhy.SkWhy;
import skwhy.chunkpath.ChunkGateService;
import skwhy.chunkpath.Direction;
import skwhy.chunkpath.gate.ChunkGateData;
import skwhy.chunkpath.gate.Gate;
import skwhy.data.ItemDisplayData;
import skwhy.data.DisplayData;
import skwhy.data.GlobalTransformation;
import skwhy.data.TextDisplayData;
import skwhy.modules.LongNavigationElements.ChunkPathManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Debug TEMPORAIRE ({@code /cpdebug gates [on|off|refresh]}) : affiche pour le chunk du joueur et les 8 chunks
 * autour les portes du long pathfinding, leurs liens et les distances calculées.
 * <ul>
 *   <li>marqueur (block display, couleur = bord) + étiquette (text display) par porte, tout en packets ;</li>
 *   <li>particules : blocs membres de chaque porte (couleur du bord), liens de voisinage (cyan, entre chunks),
 *       distances internes (orange, entre portes d'un même chunk) avec un text display « coût » au milieu.</li>
 * </ul>
 * Seules les données déjà connues (chunks enregistrés) sont lues : aucune analyse n'est lancée.
 * À supprimer une fois le debug terminé (voir {@link ChunkPathDebugCommand}).
 */
public final class GateDebugOverlay {

    private static final int RADIUS = 1;
    private static final int MAX_DISPLAYS = 700;
    private static final int MAX_PARTICLES_PER_TICK = 6000;
    private static final double LINE_STEP = 0.12;

    private static final Map<UUID, State> STATES = new HashMap<>();
    private static BukkitTask task;

    private GateDebugOverlay() {
    }

    private static final class State {
        World world;
        int chunkX = Integer.MIN_VALUE, chunkZ = Integer.MIN_VALUE;
        final List<DisplayData> displays = new ArrayList<>();
        final List<double[]> points = new ArrayList<>(); // x, y, z, rgb
        int gates, internal, links;
    }

    public static boolean toggle(Player player, boolean enable) {
        if (!enable) {
            State s = STATES.remove(player.getUniqueId());
            if (s != null) clear(player, s);
            if (STATES.isEmpty() && task != null) {
                task.cancel();
                task = null;
            }
            return false;
        }
        STATES.computeIfAbsent(player.getUniqueId(), k -> new State());
        if (task == null) task = Bukkit.getScheduler().runTaskTimer(SkWhy.getInstance(), GateDebugOverlay::tick, 1L, 5L);
        return true;
    }

    public static boolean isOn(Player player) {
        return STATES.containsKey(player.getUniqueId());
    }

    public static void refresh(Player player) {
        State s = STATES.get(player.getUniqueId());
        if (s != null) s.chunkX = Integer.MIN_VALUE;
    }

    public static String summary(Player player) {
        State s = STATES.get(player.getUniqueId());
        return s == null ? "debug des portes désactivé"
                : s.gates + " portes, " + s.internal + " distances internes, " + s.links + " liens, "
                + s.displays.size() + " displays, " + s.points.size() + " points de particules";
    }

    public static void shutdown() {
        for (UUID id : new ArrayList<>(STATES.keySet())) {
            Player p = Bukkit.getPlayer(id);
            State s = STATES.remove(id);
            if (p != null && s != null) clear(p, s);
        }
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private static void tick() {
        Iterator<Map.Entry<UUID, State>> it = STATES.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null || !p.isOnline()) {
                it.remove();
                continue;
            }
            State s = e.getValue();
            Location l = p.getLocation();
            int cx = l.getBlockX() >> 4, cz = l.getBlockZ() >> 4;
            if (cx != s.chunkX || cz != s.chunkZ || s.world != l.getWorld()) {
                clear(p, s);
                s.world = l.getWorld();
                s.chunkX = cx;
                s.chunkZ = cz;
                try {
                    build(p, s);
                } catch (Exception ex) {
                    p.sendMessage("debug portes : erreur " + ex);
                    ex.printStackTrace();
                }
            }
            drawParticles(p, s);
        }
        if (STATES.isEmpty() && task != null) {
            task.cancel();
            task = null;
        }
    }

    // ── Construction ─────────────────────────────────────────────────────────

    private static void build(Player player, State s) {
        ChunkGateService service = ChunkPathManager.serviceFor(s.world);
        s.gates = s.internal = s.links = 0;

        Map<String, Gate> known = new HashMap<>();
        List<ChunkGateData> chunks = new ArrayList<>();
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                ChunkGateData data = service.getIfKnown(s.chunkX + dx, s.chunkZ + dz);
                if (data == null) continue;
                chunks.add(data);
                for (Gate g : data.gates()) known.put(g.globalId(), g);
            }
        }

        List<DisplayData> toSend = new ArrayList<>();
        Set<String> drawnLinks = new HashSet<>();
        for (ChunkGateData data : chunks) {
            for (Gate g : data.gates()) {
                s.gates++;
                Color edgeColor = colorOf(g.edge);
                for (int[] m : g.memberBlocksWorld) {
                    s.points.add(new double[]{m[0] + 0.5, m[1] + 0.5, m[2] + 0.5, edgeColor.asRGB()});
                }
                double[] a = center(g);
                toSend.add(marker(a, itemOf(g.edge)));
                toSend.add(label(a[0], a[1] + 1.3, a[2],
                        "<white>G" + g.id + " <gray>" + g.edge.name().charAt(0) + " c(" + g.chunkX + "," + g.chunkZ + ")"
                                + "<newline><dark_gray>" + g.memberBlocksWorld.size() + " blocs, y" + minY(g) + ".." + maxY(g)
                                + "<newline><dark_gray>" + g.neighborLinks.size() + " lien(s)"));

                // distances internes (symétriques : on ne trace qu'une fois)
                for (var d : g.internalDistances.entrySet()) {
                    if (d.getKey() <= g.id) continue;
                    Gate other = data.byId(d.getKey());
                    if (other == null) continue;
                    double[] b = center(other);
                    s.internal++;
                    line(s, a, b, Color.ORANGE);
                    toSend.add(label((a[0] + b[0]) / 2, (a[1] + b[1]) / 2 + 0.4, (a[2] + b[2]) / 2,
                            "<gold>" + d.getValue()));
                }

                // liens de voisinage
                for (String id : g.neighborLinks) {
                    Gate other = known.get(id);
                    if (other == null) continue;
                    String key = g.globalId().compareTo(id) < 0 ? g.globalId() + "|" + id : id + "|" + g.globalId();
                    if (!drawnLinks.add(key)) continue;
                    double[] b = center(other);
                    s.links++;
                    line(s, a, b, Color.AQUA);
                }
            }
        }

        if (toSend.size() > MAX_DISPLAYS) {
            player.sendMessage("debug portes : " + toSend.size() + " displays, tronqué à " + MAX_DISPLAYS);
            toSend = toSend.subList(0, MAX_DISPLAYS);
        }
        for (DisplayData d : toSend) {
            DisplayData.CompiledDisplayPacket packet = d.getSpawnPacket(new Location(s.world, positions.get(d)[0], positions.get(d)[1], positions.get(d)[2]));
            if (packet != null) packet.send(player);
            s.displays.add(d);
        }
        positions.clear();
        player.sendMessage("debug portes : " + summary(player));
    }

    /** Position de spawn de chaque display construit (le spawn packet est créé à l'envoi). */
    private static final Map<DisplayData, double[]> positions = new java.util.IdentityHashMap<>();

    private static DisplayData marker(double[] at, String item) {
        ItemDisplayData d = new ItemDisplayData();
        d.setGlobalTransformation(new GlobalTransformation());
        d.setItemStack(item);
        d.setDisplayMode(8); // fixed : le centre de l'item est à la position du display
        d.setScale(0.4f, 0.4f, 0.4f);
        d.setViewRange(1f);
        d.setGlowColor(0xFFFFFF);
        positions.put(d, at);
        return d;
    }

    private static DisplayData label(double x, double y, double z, String text) {
        TextDisplayData d = new TextDisplayData();
        d.setGlobalTransformation(new GlobalTransformation());
        d.setText(text);
        d.setBillboardMode(3);
        d.setSeeThrough(true);
        d.setBackgroundColor(Color.fromARGB(150, 0, 0, 0));
        d.setScale(0.6f, 0.6f, 0.6f);
        d.setViewRange(0.6f);
        positions.put(d, new double[]{x, y, z});
        return d;
    }

    // ── Rendu ────────────────────────────────────────────────────────────────

    private static void line(State s, double[] a, double[] b, Color color) {
        double dx = b[0] - a[0], dy = b[1] - a[1], dz = b[2] - a[2];
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int steps = Math.max(1, (int) (len / LINE_STEP));
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            s.points.add(new double[]{a[0] + dx * t, a[1] + dy * t, a[2] + dz * t, color.asRGB()});
        }
    }

    private static void drawParticles(Player player, State s) {
        int n = s.points.size();
        if (n == 0) return;
        int stride = Math.max(1, (n + MAX_PARTICLES_PER_TICK - 1) / MAX_PARTICLES_PER_TICK);
        int offset = (int) (Bukkit.getCurrentTick() / 5 % stride);
        for (int i = offset; i < n; i += stride) {
            double[] p = s.points.get(i);
            player.spawnParticle(Particle.DUST, p[0], p[1], p[2], 1, 0, 0, 0, 0,
                    new Particle.DustOptions(Color.fromRGB((int) p[3]), 1.1f));
        }
    }

    private static void clear(Player player, State s) {
        if (!s.displays.isEmpty()) {
            int[] ids = s.displays.stream().mapToInt(DisplayData::getEntityId).toArray();
            var user = PacketEvents.getAPI().getPlayerManager().getUser(player);
            if (user != null) user.sendPacket(new WrapperPlayServerDestroyEntities(ids));
        }
        s.displays.clear();
        s.points.clear();
    }

    // ── Utilitaires ──────────────────────────────────────────────────────────

    private static double[] center(Gate g) {
        int[] r = g.representativeWorld;
        return new double[]{r[0] + 0.5, r[1] + 0.5, r[2] + 0.5};
    }

    private static int minY(Gate g) {
        return g.memberBlocksWorld.stream().mapToInt(m -> m[1]).min().orElse(0);
    }

    private static int maxY(Gate g) {
        return g.memberBlocksWorld.stream().mapToInt(m -> m[1]).max().orElse(0);
    }

    private static Color colorOf(Direction d) {
        return switch (d) {
            case NORTH -> Color.RED;
            case SOUTH -> Color.BLUE;
            case WEST -> Color.LIME;
            case EAST -> Color.YELLOW;
        };
    }

    private static String itemOf(Direction d) {
        return switch (d) {
            case NORTH -> "RED_CONCRETE";
            case SOUTH -> "BLUE_CONCRETE";
            case WEST -> "LIME_CONCRETE";
            case EAST -> "YELLOW_CONCRETE";
        };
    }
}
