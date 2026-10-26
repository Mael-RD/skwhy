package skwhy.quest.npc;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.scheduler.BukkitRunnable;

import skwhy.SkWhy;
import skwhy.quest.QuestSettings;
import skwhy.quest.model.NpcEntityRef;
import skwhy.quest.model.QuestNpc;

/**
 * Déplace un NPC pour un joueur. Le déplacement est joué par un double du NPC visible uniquement par ce joueur ; les
 * deux entités réelles (départ et arrivée) sont masquées pendant le trajet, puis l'arrivée est affichée.
 */
public final class NpcMover {

    public enum Mode {
        INSTANT, CONSTANT, WAIT, FOLLOW;

        public static Mode parse(String text) {
            return switch (text == null ? "" : text.toLowerCase(Locale.ROOT)) {
                case "constant", "constent" -> CONSTANT;
                case "wait" -> WAIT;
                case "follow" -> FOLLOW;
                default -> INSTANT;
            };
        }
    }

    public static final class Options {
        /** Niveau de vitesse de 0 (escargot) à 4 (rapide). */
        public int speed = 2;
        public List<String> onStop = List.of();
        public List<String> onContinue = List.of();
        /** Trajet enregistré : {@code x, y, z} répétés ; vide : ligne droite jusqu'à la destination. */
        public double[] path = new double[0];
    }

    /** Blocs par tick pour chaque niveau de vitesse. */
    private static final double[] SPEEDS = {0.22, 0.235, 0.267, 0.30, 0.33};
    private static final int FALLBACK_TICKS = 12000;
    private static final Random RANDOM = new Random();

    private static final List<String> STOP_LINES = List.of(
        "Je t'attends ici.", "Je m'arrête là, viens quand tu es prêt.", "Je fais une pause ici, viens me retrouver dès que possible.",
        "Je reste ici pour le moment, rejoins-moi quand tu peux.");

    private NpcMover() {}

    /**
     * Change la position du NPC pour le joueur.
     *
     * @return {@code true} si le déplacement est asynchrone ({@code done} sera appelé à l'arrivée)
     */
    public static boolean start(Player player, QuestNpc npc, int position, Mode mode, Options options, Runnable done) {
        NpcEntityRef from = npc.entityAt(NpcService.position(player, npc));
        NpcEntityRef to = npc.entityAt(position);
        if (mode == Mode.INSTANT || from == null || to == null || from == to) {
            NpcService.setPosition(player, npc, position);
            return false;
        }
        Entity source = NpcService.entity(from);
        World world = source == null ? null : source.getWorld();
        if (source == null || world == null) {
            NpcService.setPosition(player, npc, position);
            return false;
        }

        List<Location> points = new ArrayList<>();
        points.add(source.getLocation());
        double[] path = options.path;
        for (int i = 0; i + 2 < path.length; i += 3) points.add(new Location(world, path[i], path[i + 1], path[i + 2]));
        Location end = new Location(world, to.x(), to.y(), to.z());
        if (path.length < 3) points.add(end);

        UUID playerId = player.getUniqueId();
        NpcService.suspend(playerId, npc.id());
        Entity clone = spawnClone(player, source, points.get(0));
        if (clone == null) {
            NpcService.resume(playerId, npc.id());
            NpcService.setPosition(player, npc, position);
            return false;
        }
        new Walk(player, npc, position, mode, options, points, clone, done).runTaskTimer(SkWhy.getInstance(), 1L, 1L);
        return true;
    }

    private static Entity spawnClone(Player viewer, Entity source, Location at) {
        try {
            Entity clone = source.copy(at);
            clone.setVisibleByDefault(false);
            clone.setPersistent(false);
            if (!clone.spawnAt(at, CreatureSpawnEvent.SpawnReason.CUSTOM)) return null;
            viewer.showEntity(SkWhy.getInstance(), clone);
            return clone;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static final class Walk extends BukkitRunnable {
        private final Player player;
        private final QuestNpc npc;
        private final int position;
        private final Mode initialMode;
        private Mode mode;
        private final Options options;
        private final List<Location> points;
        private final Entity clone;
        private final Runnable done;
        private int segment;
        private int ticks;
        private boolean waiting;
        private final double speed;

        Walk(Player player, QuestNpc npc, int position, Mode mode, Options options, List<Location> points, Entity clone, Runnable done) {
            this.player = player;
            this.npc = npc;
            this.position = position;
            this.initialMode = mode;
            this.mode = mode;
            this.options = options;
            this.points = points;
            this.clone = clone;
            this.done = done;
            this.speed = SPEEDS[Math.max(0, Math.min(SPEEDS.length - 1, options.speed))];
        }

        @Override
        public void run() {
            ticks++;
            if (!clone.isValid()) {
                finish(false);
                return;
            }
            if (!player.isOnline() || !player.getWorld().equals(clone.getWorld())) {
                finish(true);
                return;
            }
            if (ticks > FALLBACK_TICKS && mode != Mode.CONSTANT) {
                mode = Mode.CONSTANT;
                say(options.onContinue);
            }

            double distance = player.getLocation().distance(clone.getLocation());
            double step = speed;
            if (mode == Mode.WAIT || mode == Mode.FOLLOW) {
                double stop = QuestSettings.waitStopDistance();
                if (!waiting && distance > stop) {
                    waiting = true;
                    say(options.onStop.isEmpty() ? List.of(STOP_LINES.get(RANDOM.nextInt(STOP_LINES.size()))) : options.onStop);
                } else if (waiting && distance < stop * 0.6) {
                    waiting = false;
                    say(options.onContinue);
                }
                if (waiting) {
                    face(player.getLocation());
                    return;
                }
                if (mode == Mode.FOLLOW) step = speed * Math.max(0.3, Math.min(1.4, 1.6 - distance / 10.0));
            }
            advance(step);
        }

        private void advance(double remaining) {
            Location current = clone.getLocation();
            while (remaining > 0 && segment + 1 < points.size()) {
                Location target = points.get(segment + 1);
                double d = current.distance(target);
                if (d <= remaining) {
                    current = target.clone();
                    remaining -= d;
                    segment++;
                } else {
                    Location direction = target.clone().subtract(current);
                    current.add(direction.toVector().normalize().multiply(remaining));
                    current.setDirection(direction.toVector());
                    remaining = 0;
                }
            }
            if (segment + 1 >= points.size()) {
                finish(true);
                return;
            }
            Location now = clone.getLocation();
            current.setYaw(yawTo(now, current));
            clone.teleport(current);
        }

        private void face(Location target) {
            Location now = clone.getLocation();
            now.setYaw(yawTo(now, target));
            clone.teleport(now);
        }

        private float yawTo(Location from, Location to) {
            double dx = to.getX() - from.getX();
            double dz = to.getZ() - from.getZ();
            if (Math.abs(dx) < 1e-6 && Math.abs(dz) < 1e-6) return from.getYaw();
            return (float) Math.toDegrees(Math.atan2(-dx, dz));
        }

        private void say(List<String> lines) {
            if (lines.isEmpty() || !player.isOnline()) return;
            DialogueService.say(player, npc, lines.get(RANDOM.nextInt(lines.size())), null, null);
        }

        private void finish(boolean arrived) {
            cancel();
            if (clone.isValid()) clone.remove();
            NpcService.resume(player.getUniqueId(), npc.id());
            if (arrived || initialMode == Mode.INSTANT) NpcService.setPosition(player, npc, position);
            else NpcService.refreshVisibility(player);
            if (done != null) done.run();
        }
    }
}
