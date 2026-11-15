package skwhy.quest.npc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import skwhy.SkWhy;
import skwhy.quest.QuestEngine;
import skwhy.quest.QuestRegistry;
import skwhy.quest.data.PlayerQuestData;
import skwhy.quest.data.QuestProgress;
import skwhy.quest.data.QuestStore;
import skwhy.quest.model.NpcEntityRef;
import skwhy.quest.model.Quest;
import skwhy.quest.model.QuestDestination;
import skwhy.quest.model.QuestNpc;
import skwhy.quest.model.QuestObjective;
import skwhy.quest.model.QuestStep;

/**
 * Boussole : une bossbar qui affiche une rose des vents et, pour la quête suivie, les destinations de l'étape
 * courante ({@code ❖}, {@code ⮝}/{@code ⮟} quand la cible est plus haut/plus bas, {@code ◯} pour une zone de recherche).
 * Les destinations ne sont recalculées que lorsque la progression change.
 */
public final class CompassService {

    /** Une cible affichée sur la boussole. */
    public record Target(QuestDestination.Kind kind, String world, double x, double y, double z, double radius, int color) {}

    private static final int CELLS = 96;
    private static final double CELL_DEGREES = 360.0 / CELLS;

    private static final Map<UUID, BossBar> BARS = new ConcurrentHashMap<>();
    private static final Map<UUID, List<Target>> TARGETS = new ConcurrentHashMap<>();
    private static final Set<UUID> DIRTY = ConcurrentHashMap.newKeySet();
    private static BukkitTask task;
    private static CompassStyle style = CompassStyle.load();

    private CompassService() {}

    public static void start() {
        style = CompassStyle.load();
        task = Bukkit.getScheduler().runTaskTimer(SkWhy.getInstance(), CompassService::tick, style.refreshTicks, style.refreshTicks);
    }

    /** Relit {@code quests.compass_display} (après un {@code reload quests}) : la fréquence ne change qu'au redémarrage. */
    public static void reloadStyle() {
        SkWhy.getInstance().reloadConfig();
        style = CompassStyle.load();
        for (UUID id : BARS.keySet()) DIRTY.add(id);
        for (BossBar bar : BARS.values()) bar.overlay(style.overlay);
    }

    public static void shutdown() {
        if (task != null) task.cancel();
        for (Map.Entry<UUID, BossBar> e : BARS.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null) p.hideBossBar(e.getValue());
        }
        BARS.clear();
        TARGETS.clear();
        DIRTY.clear();
    }

    public static boolean isEnabled(UUID player) {
        return QuestStore.get(player).compass;
    }

    public static void setEnabled(Player player, boolean enabled) {
        PlayerQuestData data = QuestStore.get(player.getUniqueId());
        data.compass = enabled;
        data.markDirty();
        if (enabled) show(player); else hide(player);
    }

    private static void show(Player player) {
        BARS.computeIfAbsent(player.getUniqueId(), id -> {
            BossBar bar = BossBar.bossBar(Component.empty(), 1f, style.barColor, style.overlay);
            player.showBossBar(bar);
            return bar;
        });
        DIRTY.add(player.getUniqueId());
    }

    private static void hide(Player player) {
        BossBar bar = BARS.remove(player.getUniqueId());
        if (bar != null) player.hideBossBar(bar);
        TARGETS.remove(player.getUniqueId());
    }

    /** À la connexion : réaffiche la boussole si le joueur l'avait activée. */
    public static void join(Player player) {
        if (QuestStore.get(player.getUniqueId()).compass) show(player);
    }

    public static void quit(UUID player) {
        BARS.remove(player);
        TARGETS.remove(player);
        DIRTY.remove(player);
    }

    /** La progression a changé : les cibles seront recalculées au prochain passage. */
    public static void invalidate(UUID player) {
        DIRTY.add(player);
    }

    /**
     * Couleur de la boussole pour l'objectif {@code number} (1 = premier objectif ; 0 = destinations de l'étape, sans objectif) :
     * la palette {@code quests.compass_display.colors} est parcourue en boucle, donc le 22e objectif reprend la couleur du 1er.
     */
    public static TextColor objectiveColor(int number) {
        return style.color(number);
    }

    /** Cibles actuelles du joueur (recalculées si nécessaire). */
    public static List<Target> targets(Player player) {
        if (DIRTY.remove(player.getUniqueId()) || !TARGETS.containsKey(player.getUniqueId())) {
            TARGETS.put(player.getUniqueId(), compute(player));
        }
        return TARGETS.get(player.getUniqueId());
    }

    private static void tick() {
        for (Map.Entry<UUID, BossBar> e : BARS.entrySet()) {
            Player player = Bukkit.getPlayer(e.getKey());
            if (player == null) continue;
            List<Target> targets = targets(player);
            boolean[] inZone = new boolean[1];
            e.getValue().name(render(player, targets, inZone));
            e.getValue().color(inZone[0] ? style.zoneBarColor : style.barColor);
        }
    }

    // ── Cibles ──────────────────────────────────────────────────────────────

    private static List<Target> compute(Player player) {
        List<Target> out = new ArrayList<>();
        Quest quest = QuestEngine.selected(player);
        if (quest == null) return out;
        PlayerQuestData data = QuestStore.get(player.getUniqueId());
        QuestProgress progress = data.quests.get(quest.id());
        if (progress == null) return out;
        QuestStep step = quest.step(progress.step);
        if (step == null) return out;
        addAll(out, step.destinations(), 0, data);
        for (QuestObjective o : step.objectives()) {
            if (!progress.isDone(o.index())) addAll(out, o.destinations(), o.index() + 1, data);
        }
        return out;
    }

    private static void addAll(List<Target> out, List<QuestDestination> destinations, int color, PlayerQuestData data) {
        for (QuestDestination d : destinations) {
            if (d.kind() == QuestDestination.Kind.NPC) {
                QuestNpc npc = QuestRegistry.npc(d.npc());
                NpcEntityRef ref = npc == null ? null : QuestRegistry.entityAt(npc, data.npcPosition(npc.id()));
                if (ref != null) out.add(new Target(QuestDestination.Kind.POINT, ref.world(), ref.x(), ref.y() + 1, ref.z(), 0, color));
            } else {
                out.add(new Target(d.kind(), d.world(), d.x(), d.y(), d.z(), d.radius(), color));
            }
        }
    }

    // ── Rendu ───────────────────────────────────────────────────────────────

    private static Component render(Player player, List<Target> targets, boolean[] inZone) {
        CompassStyle st = style;
        Location at = player.getLocation();
        Target[] cells = new Target[CELLS];
        boolean[] arrowUp = new boolean[CELLS];
        boolean[] arrowDown = new boolean[CELLS];
        for (Target t : targets) {
            if (!at.getWorld().getName().equals(t.world())) continue;
            double dx = t.x() - at.getX();
            double dz = t.z() - at.getZ();
            int cell = cell(Math.toDegrees(Math.atan2(-dx, dz)));
            if (t.kind() == QuestDestination.Kind.FARM) {
                if (dx * dx + dz * dz <= t.radius() * t.radius()) inZone[0] = true;
                if (cells[cell] == null) cells[cell] = t;
                continue;
            }
            if (cells[cell] == null || cells[cell].kind() == QuestDestination.Kind.FARM) {
                cells[cell] = t;
                double horizontal = Math.sqrt(dx * dx + dz * dz);
                double targetPitch = Math.toDegrees(Math.atan((at.getY() + 1.7 - t.y()) / Math.max(horizontal, 0.0001)));
                arrowUp[cell] = at.getPitch() >= targetPitch + st.pitchMargin;
                arrowDown[cell] = at.getPitch() <= targetPitch - st.pitchMargin;
            }
        }
        int center = cell(at.getYaw());
        Component out = Component.empty();
        // Défilement fin : un quart de case = un espace avant ou après (comme l'ancienne boussole)
        int phase = (int) Math.floor((((at.getYaw() % 360) + 360) % 360 / CELL_DEGREES % 1) * 4);
        if (st.smoothScroll && phase == 0) out = out.append(Component.space());
        for (int i = -st.halfWindow; i <= st.halfWindow; i++) {
            int cell = Math.floorMod(center + i, CELLS);
            Target t = cells[cell];
            if (t != null && t.kind() == QuestDestination.Kind.FARM) {
                out = out.append(st.zone);
            } else if (t != null) {
                Component symbol = arrowUp[cell] ? st.up : arrowDown[cell] ? st.down : st.objective;
                out = out.append(symbol.colorIfAbsent(st.color(t.color())));
            } else {
                out = out.append(decoration(st, cell));
            }
        }
        if (st.smoothScroll && phase == 3) out = out.append(Component.space());
        return st.font == null ? out : out.font(st.font);
    }

    private static int cell(double yaw) {
        double normalized = ((yaw % 360) + 360) % 360;
        return (int) Math.floor(normalized / CELL_DEGREES) % CELLS;
    }

    private static Component decoration(CompassStyle st, int cell) {
        int r = cell % 12;
        if (r == 0) return st.letters[cell / 12];
        if (r == 1) return st.first;
        if (r == 6) return st.half;
        if (r == 3 || r == 9) return st.quarter;
        return st.plain;
    }
}
