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
import net.kyori.adventure.text.format.NamedTextColor;
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

    private static final String[] PALETTE = {"#ffaa00", "#00ff00", "#ff0000", "#00ffff", "#ffff00", "#8800ff", "#ffbbcc", "#3333ff",
        "#777777", "#886543", "#333333", "#ff6600", "#00aa88", "#aa00ff", "#aaff00", "#ff0088", "#0088ff", "#996600", "#00ff99", "#cc0000", "#4444aa"};
    private static final String[] LETTERS = {"S", "SO", "O", "NO", "N", "NE", "E", "SE"};
    private static final int CELLS = 96;
    private static final double CELL_DEGREES = 360.0 / CELLS;
    private static final int HALF_WINDOW = 12;
    private static final TextColor DECOR = TextColor.color(0xddddcc);

    private static final Map<UUID, BossBar> BARS = new ConcurrentHashMap<>();
    private static final Map<UUID, List<Target>> TARGETS = new ConcurrentHashMap<>();
    private static final Set<UUID> DIRTY = ConcurrentHashMap.newKeySet();
    private static BukkitTask task;

    private CompassService() {}

    public static void start() {
        task = Bukkit.getScheduler().runTaskTimer(SkWhy.getInstance(), CompassService::tick, 2L, 2L);
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
            BossBar bar = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.BLUE, BossBar.Overlay.NOTCHED_6);
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
            e.getValue().color(inZone[0] ? BossBar.Color.GREEN : BossBar.Color.BLUE);
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
                NpcEntityRef ref = npc == null ? null : npc.entityAt(data.npcPosition(npc.id()));
                if (ref != null) out.add(new Target(QuestDestination.Kind.POINT, ref.world(), ref.x(), ref.y() + 1, ref.z(), 0, color));
            } else {
                out.add(new Target(d.kind(), d.world(), d.x(), d.y(), d.z(), d.radius(), color));
            }
        }
    }

    // ── Rendu ───────────────────────────────────────────────────────────────

    private static Component render(Player player, List<Target> targets, boolean[] inZone) {
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
                arrowUp[cell] = at.getPitch() >= targetPitch + 10;
                arrowDown[cell] = at.getPitch() <= targetPitch - 10;
            }
        }
        int center = cell(at.getYaw());
        Component out = Component.empty();
        for (int i = -HALF_WINDOW; i <= HALF_WINDOW; i++) {
            int cell = Math.floorMod(center + i, CELLS);
            Target t = cells[cell];
            if (t != null && t.kind() == QuestDestination.Kind.FARM) {
                out = out.append(Component.text("◯", NamedTextColor.GREEN));
            } else if (t != null) {
                String symbol = arrowUp[cell] ? "⮝" : arrowDown[cell] ? "⮟" : "❖";
                out = out.append(Component.text(symbol, TextColor.fromHexString(PALETTE[t.color() % PALETTE.length])));
            } else {
                out = out.append(Component.text(decoration(cell), DECOR));
            }
        }
        return out;
    }

    private static int cell(double yaw) {
        double normalized = ((yaw % 360) + 360) % 360;
        return (int) Math.floor(normalized / CELL_DEGREES) % CELLS;
    }

    private static String decoration(int cell) {
        int r = cell % 12;
        if (r == 0) return LETTERS[cell / 12];
        if (r == 6) return "◈";
        if (r == 3 || r == 9) return "•";
        return "·";
    }
}
