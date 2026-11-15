package skwhy.quest.npc;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import skwhy.SkWhy;
import skwhy.data.DisplayGroupData;
import skwhy.data.Quat4;
import skwhy.data.TextDisplayData;
import skwhy.data.Vec3;
import skwhy.modules.QuestElements.events.QuestIndicatorUpdateEvent;
import skwhy.quest.QuestRegistry;
import skwhy.quest.QuestSettings;
import skwhy.quest.model.NpcEntityRef;
import skwhy.quest.model.QuestIndicator;
import skwhy.quest.model.QuestNpc;
import skwhy.quest.script.ScriptContext;
import skwhy.quest.script.ScriptEngine;

/**
 * Marqueurs au-dessus des NPC ({@code ?} nouvelle quête, {@code !} étape de quête...). Une fausse display (paquets, via
 * {@link DisplayGroupData#setAttachedEntity}) montée sur le NPC par type de marqueur, dont les seuls spectateurs sont les
 * joueurs concernés : elle suit le NPC sans téléportation et garde une rotation 0/0 quelle que soit celle du NPC.
 * Le calcul se fait quand l'état d'un joueur change (et une fois par seconde en filet de sécurité), jamais à chaque tick ;
 * seule l'animation de rebond tourne toutes les 3 ticks (celle de l'ancien script).
 */
public final class IndicatorService {

    /** Période de l'animation (ticks) = durée d'interpolation, comme dans l'ancien script. */
    private static final int ANIMATION_PERIOD = 3;

    /** Un marqueur monté sur un NPC : groupe de display et paramètres de l'animation. */
    private static final class Marker {
        final java.util.function.BooleanSupplier valid;
        final DisplayGroupData group;
        final TextDisplayData display;
        final float baseY, scaleX, scaleY;

        Marker(java.util.function.BooleanSupplier valid, DisplayGroupData group, TextDisplayData display, float baseY, float scaleX, float scaleY) {
            this.valid = valid;
            this.group = group;
            this.display = display;
            this.baseY = baseY;
            this.scaleX = scaleX;
            this.scaleY = scaleY;
        }
    }

    /** Marqueurs existants : {@code uuid de l'entité:id du marqueur}. */
    private static final Map<String, Marker> MARKERS = new ConcurrentHashMap<>();
    /** Marqueur actuellement montré : joueur → (entité de NPC → marqueur). */
    private static final Map<UUID, Map<UUID, QuestIndicator>> SHOWN = new ConcurrentHashMap<>();
    private static final Set<UUID> DIRTY = ConcurrentHashMap.newKeySet();
    /** Marqueurs forcés par script : joueur → (id de NPC → marqueur). */
    private static final Map<UUID, Map<String, QuestIndicator>> PINNED = new ConcurrentHashMap<>();
    private static BukkitTask task;
    private static BukkitTask animation;
    private static int step;

    private IndicatorService() {}

    public static void start() {
        task = Bukkit.getScheduler().runTaskTimer(SkWhy.getInstance(), IndicatorService::tick, 20L, 20L);
        animation = Bukkit.getScheduler().runTaskTimer(SkWhy.getInstance(), IndicatorService::animate, ANIMATION_PERIOD, ANIMATION_PERIOD);
    }

    public static void shutdown() {
        if (task != null) task.cancel();
        if (animation != null) animation.cancel();
        MARKERS.values().forEach(m -> m.group.delete());
        MARKERS.clear();
        SHOWN.clear();
        DIRTY.clear();
        PINNED.clear();
    }

    /** Force un marqueur sur un NPC pour un joueur ({@code NONE} ne le retire pas : il cache le marqueur ; {@code null} annule le forçage). */
    public static void pin(UUID player, String npcId, QuestIndicator indicator) {
        Map<String, QuestIndicator> pins = PINNED.computeIfAbsent(player, k -> new ConcurrentHashMap<>());
        if (indicator == null) pins.remove(npcId); else pins.put(npcId, indicator);
        invalidate(player);
    }

    /** Demande un recalcul des marqueurs de ce joueur au prochain tick. */
    public static void invalidate(UUID player) {
        if (!QuestSettings.indicatorsEnabled()) return;
        if (DIRTY.add(player)) {
            Bukkit.getScheduler().runTask(SkWhy.getInstance(), () -> {
                DIRTY.remove(player);
                Player online = Bukkit.getPlayer(player);
                if (online != null) refresh(online);
            });
        }
    }

    private static void tick() {
        if (!QuestSettings.indicatorsEnabled()) return;
        for (Player player : Bukkit.getOnlinePlayers()) refresh(player);
        // Filet de sécurité : si le client a perdu le NPC puis l'a revu, il a aussi perdu ses passagers
        for (Marker marker : MARKERS.values()) marker.group.mount();
    }

    /**
     * Rebond de l'ancien script ({@code quête_compass_display}) : à chaque pas l'angle avance de 15°, la translation verticale
     * vaut {@code 0.2 sin²(angle)} et l'échelle s'étire de {@code 1 + 0.15 (0.5 + 0.5 sin(2 angle - 15°))²} en hauteur
     * (compensée par {@code 1/√} en largeur), interpolées sur 3 ticks.
     */
    private static void animate() {
        step++;
        double angle = Math.toRadians(15.0 * step);
        double stretch = 1 + 0.15 * Math.pow(0.5 + 0.5 * Math.sin(2 * angle - Math.toRadians(15)), 2);
        float lift = (float) (0.2 * Math.pow(Math.sin(angle), 2));
        for (Map.Entry<String, Marker> entry : MARKERS.entrySet()) {
            Marker marker = entry.getValue();
            if (!marker.valid.getAsBoolean()) {
                marker.group.delete();
                MARKERS.remove(entry.getKey());
                continue;
            }
            if (marker.group.getViewers().isEmpty()) continue;
            marker.display.setScale((float) (marker.scaleX / Math.sqrt(stretch)), (float) (marker.scaleY * stretch), 1f);
            marker.display.setTranslation(0f, marker.baseY + lift, 0f);
            marker.display.setInterpolationStart(0);
            marker.display.setInterpolationDuration(ANIMATION_PERIOD);
            marker.group.updateMetadata();
        }
    }

    public static void forget(UUID player) {
        PINNED.remove(player);
        SHOWN.remove(player);
        for (Map.Entry<String, Marker> entry : MARKERS.entrySet()) {
            for (Player viewer : entry.getValue().group.getViewers()) {
                if (viewer.getUniqueId().equals(player)) removeViewer(entry.getKey(), viewer);
            }
        }
    }

    /** Recalcule et applique les marqueurs de tous les NPC proches du joueur. */
    public static void refresh(Player player) {
        double range = QuestSettings.indicatorRange();
        Map<UUID, QuestIndicator> shown = SHOWN.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>());
        Set<UUID> seen = new HashSet<>();
        for (NpcEntityRef ref : QuestRegistry.entityRefs()) {
            NpcService.Body body = NpcService.body(ref);
            if (body == null || !player.getWorld().equals(body.location().getWorld())) continue;
            if (body.location().distanceSquared(player.getLocation()) > range * range) continue;
            if (!NpcService.shouldSee(player, ref)) continue;
            QuestNpc npc = QuestRegistry.npc(ref.npcId());
            if (npc == null) continue;
            seen.add(body.key());

            QuestIndicator computed = compute(player, npc, body.entity());
            QuestIndicator past = shown.getOrDefault(body.key(), QuestIndicator.NONE);
            if (computed == past) continue;
            QuestIndicatorUpdateEvent event = new QuestIndicatorUpdateEvent(player, npc, past, computed);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) continue;
            apply(player, body, event.getIndicator() == null ? QuestIndicator.NONE : event.getIndicator(), shown);
        }
        // Les NPC qui sortent de la portée perdent leur marqueur
        for (UUID entity : new HashSet<>(shown.keySet())) {
            if (!seen.contains(entity)) {
                hideAll(player, entity);
                shown.remove(entity);
            }
        }
    }

    /** Marqueur que ce NPC afficherait à ce joueur (les marqueurs forcés par {@link #pin} sont prioritaires). */
    public static QuestIndicator compute(Player player, QuestNpc npc, Entity entity) {
        Map<String, QuestIndicator> pins = PINNED.get(player.getUniqueId());
        if (pins != null && pins.containsKey(npc.id())) return pins.get(npc.id());
        if (!"scripté".equals(npc.type()) && !"scripte".equals(npc.type()) && !"script".equals(npc.type())) return QuestIndicator.NONE;
        int legacy = ScriptEngine.point(ScriptContext.forNpc(player, npc, entity));
        return QuestIndicator.fromLegacy(legacy);
    }

    private static void apply(Player player, NpcService.Body body, QuestIndicator indicator, Map<UUID, QuestIndicator> shown) {
        hideAll(player, body.key());
        if (indicator == QuestIndicator.NONE) {
            shown.remove(body.key());
            return;
        }
        Marker marker = marker(body, indicator);
        if (marker != null) marker.group.addViewer(player);
        shown.put(body.key(), indicator);
    }

    private static void hideAll(Player player, UUID entity) {
        for (QuestIndicator indicator : QuestIndicator.values()) removeViewer(entity + ":" + indicator.id(), player);
    }

    /** Retire un spectateur ; un marqueur sans spectateur est supprimé (recréé à la demande, l'animation reste synchronisée). */
    private static void removeViewer(String key, Player player) {
        Marker marker = MARKERS.get(key);
        if (marker == null) return;
        marker.group.removeViewer(player);
        if (marker.group.getViewers().isEmpty()) {
            marker.group.delete();
            MARKERS.remove(key);
        }
    }

    private static Marker marker(NpcService.Body body, QuestIndicator indicator) {
        String key = body.key() + ":" + indicator.id();
        Marker existing = MARKERS.get(key);
        if (existing != null) return existing;

        // Mêmes tailles que l'ancien script ; la display est posée au sommet de la hitbox (point d'attache des passagers)
        float scaleX = indicator == QuestIndicator.INFO ? 3f : 4f;
        float scaleY = switch (indicator) {
            case NEW, MAIN, SECONDARY -> 5f;
            case INFO -> 3f;
            default -> 4f;
        };
        float baseY = indicator == QuestIndicator.INFO ? 0f : -0.1f;

        TextDisplayData display = new TextDisplayData(
            new Vec3(scaleX, scaleY, 1f), new Vec3(0f, baseY, 0f), new Quat4(0f, 0f, 0f, 1f), new Quat4(0f, 0f, 0f, 1f),
            -1, 0f, 1f, 1f, 1 /* vertical */, 0, ANIMATION_PERIOD,
            indicator.text(), Color.fromARGB(0, 0, 0, 0), 0 /* centré */, 200, false);
        display.setBrightness(15);

        // entité réelle : la display lui est montée ; entité custom fausse : montée sur son interaction (paquets seulement)
        DisplayGroupData group = body.entity() != null ? new DisplayGroupData(body.entity()) : new DisplayGroupData(body.location(), body.attachId());
        // La rotation du groupe vient sinon de celle du NPC au moment de la création : toujours 0/0
        group.setYawPitch(0f, 0f);
        group.addDisplay(display);

        java.util.function.BooleanSupplier valid = body.entity() != null ? body.entity()::isValid : () -> {
            skwhy.quest.model.NpcEntityRef still = skwhy.quest.QuestRegistry.refOf(body.key());
            return still != null && CustomNpcBodies.isFake(still);
        };
        Marker marker = new Marker(valid, group, display, baseY, scaleX, scaleY);
        MARKERS.put(key, marker);
        return marker;
    }
}
