package skwhy.quest.npc;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import skwhy.SkWhy;
import skwhy.modules.QuestElements.events.QuestIndicatorUpdateEvent;
import skwhy.quest.QuestRegistry;
import skwhy.quest.QuestSettings;
import skwhy.quest.model.NpcEntityRef;
import skwhy.quest.model.QuestIndicator;
import skwhy.quest.model.QuestNpc;
import skwhy.quest.script.ScriptContext;
import skwhy.quest.script.ScriptEngine;
import skwhy.util.TextComponents;

/**
 * Marqueurs au-dessus des NPC ({@code ?} nouvelle quête, {@code !} étape de quête...). Un texte flottant par NPC et par
 * type de marqueur, invisible par défaut puis montré aux seuls joueurs concernés. Le calcul se fait quand l'état d'un
 * joueur change (et une fois par seconde en filet de sécurité), jamais à chaque tick.
 */
public final class IndicatorService {

    private static final Map<String, TextDisplay> DISPLAYS = new ConcurrentHashMap<>();
    /** Marqueur actuellement montré : joueur → (entité de NPC → marqueur). */
    private static final Map<UUID, Map<UUID, QuestIndicator>> SHOWN = new ConcurrentHashMap<>();
    private static final Set<UUID> DIRTY = ConcurrentHashMap.newKeySet();
    /** Marqueurs forcés par script : joueur → (id de NPC → marqueur). */
    private static final Map<UUID, Map<String, QuestIndicator>> PINNED = new ConcurrentHashMap<>();
    private static BukkitTask task;

    private IndicatorService() {}

    public static void start() {
        task = Bukkit.getScheduler().runTaskTimer(SkWhy.getInstance(), IndicatorService::tick, 20L, 20L);
    }

    public static void shutdown() {
        if (task != null) task.cancel();
        DISPLAYS.values().forEach(d -> { if (d.isValid()) d.remove(); });
        DISPLAYS.clear();
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
    }

    public static void forget(UUID player) {
        PINNED.remove(player);
        Map<UUID, QuestIndicator> shown = SHOWN.remove(player);
        Player online = Bukkit.getPlayer(player);
        if (shown == null || online == null) return;
        for (UUID entity : shown.keySet()) hideAll(online, entity);
    }

    /** Recalcule et applique les marqueurs de tous les NPC proches du joueur. */
    public static void refresh(Player player) {
        double range = QuestSettings.indicatorRange();
        Map<UUID, QuestIndicator> shown = SHOWN.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>());
        Set<UUID> seen = new HashSet<>();
        for (NpcEntityRef ref : QuestRegistry.entityRefs()) {
            Entity entity = NpcService.entity(ref);
            if (entity == null || !entity.getWorld().equals(player.getWorld())) continue;
            if (entity.getLocation().distanceSquared(player.getLocation()) > range * range) continue;
            if (!NpcService.shouldSee(player, ref)) continue;
            QuestNpc npc = QuestRegistry.npc(ref.npcId());
            if (npc == null) continue;
            seen.add(entity.getUniqueId());

            QuestIndicator computed = compute(player, npc, entity);
            QuestIndicator past = shown.getOrDefault(entity.getUniqueId(), QuestIndicator.NONE);
            if (computed == past) continue;
            QuestIndicatorUpdateEvent event = new QuestIndicatorUpdateEvent(player, npc, past, computed);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) continue;
            apply(player, entity, event.getIndicator() == null ? QuestIndicator.NONE : event.getIndicator(), shown);
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

    private static void apply(Player player, Entity entity, QuestIndicator indicator, Map<UUID, QuestIndicator> shown) {
        hideAll(player, entity.getUniqueId());
        if (indicator == QuestIndicator.NONE) {
            shown.remove(entity.getUniqueId());
            return;
        }
        TextDisplay display = display(entity, indicator);
        if (display != null) player.showEntity(SkWhy.getInstance(), display);
        shown.put(entity.getUniqueId(), indicator);
    }

    private static void hideAll(Player player, UUID entity) {
        for (QuestIndicator indicator : QuestIndicator.values()) {
            TextDisplay display = DISPLAYS.get(entity + ":" + indicator.id());
            if (display != null && display.isValid()) player.hideEntity(SkWhy.getInstance(), display);
        }
    }

    private static TextDisplay display(Entity entity, QuestIndicator indicator) {
        String key = entity.getUniqueId() + ":" + indicator.id();
        TextDisplay existing = DISPLAYS.get(key);
        Location at = entity.getLocation().add(0, entity.getHeight() + 0.2, 0);
        if (existing != null && existing.isValid()) {
            if (existing.getLocation().distanceSquared(at) > 0.04) existing.teleport(at);
            return existing;
        }
        TextDisplay display = entity.getWorld().spawn(at, TextDisplay.class, d -> {
            d.setVisibleByDefault(false);
            d.setPersistent(false);
            d.setBillboard(Display.Billboard.VERTICAL);
            d.setSeeThrough(false);
            d.setBackgroundColor(org.bukkit.Color.fromARGB(0, 0, 0, 0));
            d.setBrightness(new Display.Brightness(15, 15));
            d.setAlignment(TextDisplay.TextAlignment.CENTER);
            d.text(TextComponents.fromSkript(indicator.text()));
            d.setTransformation(new Transformation(new Vector3f(0, 0.2f, 0), new Quaternionf(), new Vector3f(3f, 3f, 3f), new Quaternionf()));
        });
        DISPLAYS.put(key, display);
        return display;
    }
}
