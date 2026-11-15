package skwhy.quest.npc;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityHeadLook;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRotation;

import ch.njol.skript.variables.Variables;
import skwhy.SkWhy;
import skwhy.chunkpath.block.PathCostRules;
import skwhy.chunkpath.longnav.LongNavigation;
import skwhy.chunkpath.longnav.NavigationGuard;
import skwhy.modules.LongNavigationElements.types.PathCostRuleData;
import skwhy.modules.RandomStuff.effects.SecretHide;
import skwhy.quest.QuestRegistry;
import skwhy.quest.QuestSettings;
import skwhy.quest.model.NpcEntityRef;
import skwhy.quest.model.QuestNpc;

/**
 * Déplace un NPC pour un joueur avec le long pathfinding. L'entité réelle du NPC (celle de la position de départ) est
 * pilotée par une {@link LongNavigation} virtuelle : ses déplacements ne sont envoyés qu'à ce joueur en packets, l'entité
 * ne bouge pas côté serveur (les autres joueurs la voient toujours à sa place). À l'arrivée, la position du NPC change
 * pour le joueur : l'entité de départ est supprimée chez lui et celle d'arrivée affichée. Pendant la marche l'entité est masquée en secret
 * ({@link SecretHide} : le client la garde mais le serveur ne lui envoie plus rien, donc plus de mise à jour qui la remettrait à sa place) ;
 * le client retire les entités des chunks qu'il décharge : elle lui est renvoyée ({@link EntitySnapshot}) quand elle entre dans la zone affichée. Si le trajet ne peut pas être
 * calculé, ou si le joueur ne voit plus le NPC (déconnexion, éloignement, autre monde), le NPC est placé tout de suite à
 * son arrivée.
 */
public final class NpcMover {

    public enum Mode {
        /** Le NPC apparaît à l'arrivée. */
        INSTANT,
        /** Le NPC marche à vitesse constante. */
        CONSTANT,
        /** Le NPC marche, accélère s'il est derrière le joueur et attend s'il est trop loin devant. */
        WAIT;

        public static Mode parse(String text) {
            return switch (text == null ? "" : text.toLowerCase(Locale.ROOT)) {
                case "constant", "constent", "walk" -> CONSTANT;
                // « follow » (ancien mode supprimé) se comporte comme « wait »
                case "wait", "follow" -> WAIT;
                default -> INSTANT;
            };
        }
    }

    public static final class Options {
        /** Niveau de vitesse de 0 (escargot) à 4 (rapide). */
        public int speed = 2;
        public List<String> onStop = List.of();
        public List<String> onContinue = List.of();
        /** Variable Skript des règles de coût ({@code pathfinding::rules::villes} = {@code {pathfinding::rules::villes::*}}). */
        public String rules = "";
        /**
         * Destination fixe (coordonnées) au lieu d'une position du NPC, {@code null} = une position. Le monde peut être {@code null}
         * (= celui du NPC). Un déplacement vers une position est toujours personnel (packets pour le joueur) ; vers une destination
         * fixe il peut être {@link #real}.
         */
        public Location destination;
        /** Destination fixe seulement : l'entité du NPC marche vraiment (tous les joueurs la voient) au lieu de ne marcher que pour le joueur. */
        public boolean real;
    }

    /** Blocs par tick pour chaque niveau de vitesse. */
    private static final double[] SPEEDS = {0.22, 0.235, 0.267, 0.30, 0.33};
    private static final int FALLBACK_TICKS = 12000;
    private static final int TRACK_LIMIT = 4000;
    private static final double RESUME_RATIO = 0.6;
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
        if (options.destination != null) return startFixed(player, npc, mode, options, done);
        NpcEntityRef from = QuestRegistry.entityAt(npc, NpcService.position(player, npc));
        NpcEntityRef to = QuestRegistry.entityAt(npc, position);
        if (mode == Mode.INSTANT || from == null || to == null || from == to) {
            NpcService.setPosition(player, npc, position);
            return false;
        }
        Entity source = NpcService.entity(from);
        if (source == null) {
            NpcService.setPosition(player, npc, position);
            return false;
        }
        PathCostRules rules = loadRules(options.rules);
        if (rules == null) {
            SkWhy.getInstance().getLogger().warning("[Quest] Déplacement de '" + npc.id() + "' : règles de coût introuvables, " + rulesProblem(options.rules) + " : le NPC est téléporté.");
            NpcService.setPosition(player, npc, position);
            return false;
        }

        Location end = new Location(source.getWorld(), to.x(), to.y(), to.z());
        double base = SPEEDS[Math.max(0, Math.min(SPEEDS.length - 1, options.speed))];
        LongNavigation nav;
        try {
            nav = LongNavigation.virtualOf(source, (float) base, rules, List.of(player));
        } catch (NavigationGuard.Refused e) {
            SkWhy.getInstance().getLogger().severe("[Quest] Long navigation refusée pour '" + npc.id() + "' : " + e.getMessage());
            NpcService.setPosition(player, npc, position);
            return false;
        }
        nav.setEnd(end);
        if (nav.getStatus() == LongNavigation.Status.FAILED_NO_PATH) { // pas de route : téléportation
            SkWhy.getInstance().getLogger().warning("[Quest] Aucune route pour déplacer '" + npc.id() + "' : le NPC est téléporté.");
            nav.unregister();
            NpcService.setPosition(player, npc, position);
            return false;
        }

        List<double[]> points = new ArrayList<>();
        points.add(new double[]{source.getX(), source.getY(), source.getZ()});
        for (Location block : nav.pathBlocks(TRACK_LIMIT)) points.add(new double[]{block.getX(), block.getY(), block.getZ()});
        points.add(new double[]{end.getX() + 0.5, end.getY(), end.getZ() + 0.5});

        nav.setPauseTicks(0); // une long navigation est en pause par défaut
        Walk walk = new Walk(player, npc, position, mode, options, source, nav, new NpcWalkMath.Track(points), base, done, false);
        walk.begin();
        walk.runTaskTimer(SkWhy.getInstance(), 1L, 1L);
        return true;
    }

    /**
     * Déplacement vers une destination fixe : l'entité du NPC pour le joueur (celle de sa position courante) marche jusqu'aux
     * coordonnées. {@code real} = l'entité marche vraiment (tous les joueurs la voient, sa position enregistrée est mise à jour à
     * l'arrivée) ; sinon elle ne marche que pour le joueur (packets) et le serveur ne la déplace pas.
     */
    private static boolean startFixed(Player player, QuestNpc npc, Mode mode, Options options, Runnable done) {
        int position = NpcService.position(player, npc);
        NpcEntityRef from = QuestRegistry.entityAt(npc, position);
        Entity source = from == null ? null : NpcService.entity(from);
        if (source == null) {
            SkWhy.getInstance().getLogger().warning("[Quest] Déplacement de '" + npc.id() + "' vers une destination fixe : aucune entité chargée à la position " + position + ".");
            return false;
        }
        Location end = options.destination.clone();
        if (end.getWorld() == null) end.setWorld(source.getWorld());
        boolean real = options.real;
        if (mode == Mode.INSTANT) {
            arriveFixed(player, npc, position, source, end, real);
            return false;
        }
        PathCostRules rules = loadRules(options.rules);
        if (rules == null) {
            SkWhy.getInstance().getLogger().warning("[Quest] Déplacement de '" + npc.id() + "' : règles de coût introuvables, " + rulesProblem(options.rules) + " : le NPC est téléporté.");
            arriveFixed(player, npc, position, source, end, real);
            return false;
        }
        double base = SPEEDS[Math.max(0, Math.min(SPEEDS.length - 1, options.speed))];
        LongNavigation nav;
        try {
            nav = real ? new LongNavigation(source, (float) base, rules) : LongNavigation.virtualOf(source, (float) base, rules, List.of(player));
        } catch (NavigationGuard.Refused e) {
            SkWhy.getInstance().getLogger().severe("[Quest] Long navigation refusée pour '" + npc.id() + "' : " + e.getMessage());
            arriveFixed(player, npc, position, source, end, real);
            return false;
        }
        nav.setEnd(end);
        if (nav.getStatus() == LongNavigation.Status.FAILED_NO_PATH) {
            SkWhy.getInstance().getLogger().warning("[Quest] Aucune route pour déplacer '" + npc.id() + "' : le NPC est téléporté.");
            nav.unregister();
            arriveFixed(player, npc, position, source, end, real);
            return false;
        }
        List<double[]> points = new ArrayList<>();
        points.add(new double[]{source.getX(), source.getY(), source.getZ()});
        for (Location block : nav.pathBlocks(TRACK_LIMIT)) points.add(new double[]{block.getX(), block.getY(), block.getZ()});
        points.add(new double[]{end.getX(), end.getY(), end.getZ()});
        nav.setPauseTicks(0);
        Walk walk = new Walk(player, npc, position, mode, options, source, nav, new NpcWalkMath.Track(points), base, done, true);
        walk.end = end;
        walk.real = real;
        if (!real) walk.begin();
        walk.runTaskTimer(SkWhy.getInstance(), 1L, 1L);
        return true;
    }

    /** Place le NPC à sa destination fixe : réellement (téléportation + position enregistrée) ou pour le joueur seul (packet). */
    private static void arriveFixed(Player player, QuestNpc npc, int position, Entity source, Location end, boolean real) {
        if (real) {
            source.teleport(end);
            NpcPlaces.set(npc.id(), position, NpcPlaces.Place.of(end));
            return;
        }
        if (!player.isOnline()) return;
        // pour le joueur seul : le serveur ne doit plus remettre l'entité à sa place (masquage secret, sans suppression côté client)
        if (player.canSee(source)) SecretHide.hide(SkWhy.getInstance(), player, source);
        if (chunkSent(player, end)) EntitySnapshot.of(source).spawnFor(player, end);
    }

    /** Le client du joueur a-t-il le chunk de cet endroit (donc affiche-t-il les entités qui s'y trouvent) ? */
    static boolean chunkSent(Player player, Location at) {
        return at.getWorld() != null && at.getWorld().equals(player.getWorld())
            && player.isChunkSent(org.bukkit.Chunk.getChunkKey(at.getBlockX() >> 4, at.getBlockZ() >> 4));
    }

    private static String rulesProblem(String variable) {
        String name = normalize(variable);
        if (name.isEmpty()) return "aucune variable de règles de coût n'est indiquée (clé 'rules')";
        if (!isAllowed(name)) return "la variable '" + name + "' n'est pas dans la config quests.pathfinding_rules (ajoutez-la puis /skwhy reload)";
        return "la variable Skript {" + name + "::*} est vide ou ne contient aucune règle de coût (script de chargement pas encore exécuté ?)";
    }

    /**
     * Règles de coût d'une variable Skript de la liste de la config ({@code quests.pathfinding_rules}). Les entrées de la
     * config s'écrivent {@code a::b} ou {@code {a::b::*}} ; seules celles-ci sont acceptées (menu déroulant de l'éditeur).
     */
    static PathCostRules loadRules(String variable) {
        String name = normalize(variable);
        if (name.isEmpty() || !isAllowed(name)) return null;
        List<PathCostRuleData> found = new ArrayList<>();
        collect(Variables.getVariable(name + "::*", null, false), found);
        return PathCostRuleData.buildRules(found.toArray(new PathCostRuleData[0]));
    }

    /** La variable (déjà normalisée) figure-t-elle dans {@code quests.pathfinding_rules} ? */
    private static boolean isAllowed(String name) {
        for (String entry : QuestSettings.pathfindingRules()) if (normalize(entry).equalsIgnoreCase(name)) return true;
        return false;
    }

    /** {@code {a::b::*}}, {@code a::b::*} et {@code a::b} désignent la même liste. */
    public static String normalize(String variable) {
        String name = variable == null ? "" : variable.trim();
        if (name.startsWith("{") && name.endsWith("}")) name = name.substring(1, name.length() - 1).trim();
        if (name.endsWith("::*")) name = name.substring(0, name.length() - 3);
        return name;
    }

    private static void collect(Object value, List<PathCostRuleData> into) {
        if (value instanceof PathCostRuleData data) into.add(data);
        else if (value instanceof Map<?, ?> map) for (Object each : map.values()) collect(each, into);
    }

    private static final class Walk extends BukkitRunnable {
        private final Player player;
        private final QuestNpc npc;
        private final int position;
        private Mode mode;
        private final Options options;
        private final Entity source;
        private final EntitySnapshot snapshot;
        /** Le client avait-il le chunk où l'entité marche au dernier tick (sinon il n'affiche pas l'entité). */
        private boolean wasSent;
        private final LongNavigation nav;
        private final NpcWalkMath.Track track;
        private final Runnable done;
        private final double base;
        private int ticks;
        private boolean waiting;
        private boolean finished;
        /** Destination fixe : pas de changement de position du NPC à l'arrivée. */
        private final boolean fixed;
        private Location end;
        private boolean real;

        Walk(Player player, QuestNpc npc, int position, Mode mode, Options options, Entity source, LongNavigation nav,
             NpcWalkMath.Track track, double base, Runnable done, boolean fixed) {
            this.fixed = fixed;
            this.player = player;
            this.npc = npc;
            this.position = position;
            this.mode = mode;
            this.options = options;
            this.source = source;
            this.snapshot = EntitySnapshot.of(source);
            this.nav = nav;
            this.track = track;
            this.base = base;
            this.done = done;
        }

        @Override
        public void run() {
            if (finished) return;
            ticks++;
            LongNavigation.Status status = nav.getStatus();
            // arrivée, route introuvable, ou plus personne ne le voit (le joueur est parti) : terminé tout de suite
            if (!nav.isRegistered() || status == LongNavigation.Status.ARRIVED || status == LongNavigation.Status.FAILED_NO_PATH
                || status == LongNavigation.Status.ABANDONED || !player.isOnline()) {
                finish();
                return;
            }
            if (ticks > FALLBACK_TICKS && mode != Mode.CONSTANT) {
                mode = Mode.CONSTANT;
                say(options.onContinue);
            }
            if (!real) keepClientInSync();
            if (mode != Mode.WAIT) {
                nav.setSpeed((float) base);
                return;
            }

            Location npcAt = nav.getCurrentLocation();
            Location playerAt = player.getLocation();
            double[] npcOnTrack = track.project(npcAt.getX(), npcAt.getY(), npcAt.getZ());
            double[] playerOnTrack = track.project(playerAt.getX(), playerAt.getY(), playerAt.getZ());
            double gap = npcOnTrack[0] - playerOnTrack[0];
            double scale = QuestSettings.waitStopDistance();
            boolean wait = NpcWalkMath.shouldWait(waiting, gap, playerOnTrack[1], scale, RESUME_RATIO);
            if (wait) {
                if (!waiting) {
                    waiting = true;
                    nav.setPauseTicks(LongNavigation.PAUSED_INDEFINITELY);
                    say(options.onStop.isEmpty() ? List.of(STOP_LINES.get(RANDOM.nextInt(STOP_LINES.size()))) : options.onStop);
                }
                face(npcAt, playerAt);
                return;
            }
            if (waiting) {
                waiting = false;
                nav.setPauseTicks(0);
                say(options.onContinue);
            }
            nav.setSpeed((float) (base * NpcWalkMath.speedFactor(gap, scale, QuestSettings.npcMaxSpeedFactor())));
        }

        private void face(Location from, Location target) {
            double dx = target.getX() - from.getX();
            double dz = target.getZ() - from.getZ();
            if (Math.abs(dx) < 1e-6 && Math.abs(dz) < 1e-6) return;
            float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            PacketEvents.getAPI().getPlayerManager().sendPacket(player, new WrapperPlayServerEntityRotation(source.getEntityId(), yaw, 0F, true));
            PacketEvents.getAPI().getPlayerManager().sendPacket(player, new WrapperPlayServerEntityHeadLook(source.getEntityId(), yaw));
        }

        /**
         * Entité masquée en secret : le client la garde sans plus rien recevoir du serveur (ni mise à jour qui la remettrait à sa place,
         * ni suppression), seuls les paquets de la navigation la bougent. Mais le client retire les entités des chunks qu'il décharge :
         * quand l'entité entre dans la zone qu'il affiche, elle lui est renvoyée (même id, à sa position de marche).
         */
        private void begin() {
            boolean tracked = source.getTrackedBy().contains(player);
            SecretHide.hide(SkWhy.getInstance(), player, source);
            Location at = nav.getCurrentLocation();
            wasSent = chunkSent(player, at);
            if (!tracked && wasSent) snapshot.spawnFor(player, at); // le client ne l'avait pas (hors de portée de suivi)
        }

        private void keepClientInSync() {
            Location at = nav.getCurrentLocation();
            boolean sent = chunkSent(player, at);
            if (sent && !wasSent) snapshot.spawnFor(player, at);
            wasSent = sent;
        }

        private void say(List<String> lines) {
            if (lines.isEmpty() || !player.isOnline()) return;
            DialogueService.say(player, npc, lines.get(RANDOM.nextInt(lines.size())), null, null);
        }

        private void finish() {
            if (finished) return;
            finished = true;
            cancel();
            nav.unregister();
            if (fixed) arriveFixed(player, npc, position, source, end, real); // le NPC est exactement sur sa destination (la marche s'arrête à ~1 bloc)
            else {
                // l'entité de départ (masquée en secret, donc toujours affichée par le client) disparaît, puis celle d'arrivée apparaît
                if (player.isOnline()) PacketEvents.getAPI().getPlayerManager().sendPacket(player, new WrapperPlayServerDestroyEntities(source.getEntityId()));
                NpcService.setPosition(player, npc, position);
            }
            if (done != null) done.run();
        }
    }
}
