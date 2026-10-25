package skwhy.quest.script;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;

import ch.njol.skript.variables.Variables;
import skwhy.quest.QuestEngine;
import skwhy.quest.QuestRegistry;
import skwhy.quest.QuestSettings;
import skwhy.quest.Tree;
import skwhy.quest.data.LegacyPlayerData;
import skwhy.quest.data.PlayerQuestData;
import skwhy.quest.data.QuestProgress;
import skwhy.quest.data.QuestStore;
import skwhy.quest.model.Quest;
import skwhy.quest.model.QuestObjective;
import skwhy.quest.model.QuestStep;
import skwhy.quest.model.ScriptNode;

/** Évaluation des conditions des scripts : {@code compare}, {@code between}, {@code list} et les types de valeurs. */
public final class Conditions {

    private static final int MAX_DEPTH = 64;
    /** Profondeur d'évaluation en cours (tout se passe sur le thread principal). */
    private static int depth;

    private Conditions() {}

    /** Évalue le nœud de condition {@code id} (ou les littéraux {@code true} / {@code false}). */
    public static boolean eval(ScriptContext ctx, String id) {
        if (depth >= MAX_DEPTH) {
            skwhy.SkWhy.getInstance().getLogger().warning("[Quest] Conditions imbriquées sur plus de " + MAX_DEPTH
                + " niveaux (boucle probable) autour de '" + id + "' : évaluée à faux.");
            return false;
        }
        depth++;
        try {
            return evalNode(ctx, id);
        } finally {
            depth--;
        }
    }

    private static boolean evalNode(ScriptContext ctx, String id) {
        if (id == null || id.equals("true")) return true;
        if (id.equals("false")) return false;
        // Une condition « inline » enregistrée dynamiquement par le staff / un script
        if (CustomHandlers.hasCondition(id)) return CustomHandlers.condition(id, ctx);
        ScriptNode node = ctx.scripts().get(id);
        if (node == null) return false;
        boolean reverse = node.bool("reverse", false);
        switch (node.conditionType()) {
            case "list" -> {
                boolean all = "all".equals(node.str("mode", "single"));
                List<String> list = Tree.strings(node.data(), "liste");
                boolean result = all;
                for (String child : list) {
                    boolean value = eval(ctx, child);
                    if (all && !value) { result = false; break; }
                    if (!all && value) { result = true; break; }
                }
                if (!all && list.isEmpty()) result = false;
                return result != reverse;
            }
            case "between" -> {
                Object a = value(ctx, Tree.map(node.data(), "value1"));
                Object b = value(ctx, Tree.map(node.data(), "value2"));
                Object c = value(ctx, Tree.map(node.data(), "value3"));
                boolean result = number(b) >= number(a) && number(b) <= number(c);
                return result != reverse;
            }
            case "compare" -> {
                Object a = value(ctx, Tree.map(node.data(), "value1"));
                Object b = value(ctx, Tree.map(node.data(), "value2"));
                boolean result = switch (node.str("operator", "equal")) {
                    case "equal" -> equal(a, b);
                    case "superior_strict" -> number(a) > number(b);
                    case "inferior_strict" -> number(a) < number(b);
                    case "superior" -> number(a) >= number(b);
                    case "inferior" -> number(a) <= number(b);
                    default -> false;
                };
                return result != reverse;
            }
            default -> {
                if (CustomHandlers.hasCondition(node.conditionType())) {
                    return CustomHandlers.condition(node.conditionType(), ctx, node) != reverse;
                }
                return false;
            }
        }
    }

    private static boolean equal(Object a, Object b) {
        if (a == null || b == null) return a == b;
        if (a instanceof String || b instanceof String) {
            if (a instanceof Number || b instanceof Number || a instanceof Boolean || b instanceof Boolean) {
                return number(a) == number(b) && !Double.isNaN(number(a));
            }
            return String.valueOf(a).equals(String.valueOf(b));
        }
        return number(a) == number(b);
    }

    private static double number(Object v) {
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof Boolean b) return b ? 1 : 0;
        if (v instanceof String s) {
            try { return Double.parseDouble(s.trim()); } catch (NumberFormatException ignored) { return Double.NaN; }
        }
        return 0;
    }

    /** Valeur d'une opérande de condition ({@code value1}, {@code value2}, {@code value3}). */
    @SuppressWarnings("deprecation") // metadata Paper, API volontairement utilisée
    public static Object value(ScriptContext ctx, Map<String, Object> v) {
        OfflinePlayer player = ctx.player();
        UUID uuid = player.getUniqueId();
        String type = Tree.str(v, "type", "value");
        switch (type) {
            case "value": {
                Object raw = v.get("value");
                return raw instanceof Byte b ? (Object) (int) b : raw;
            }
            case "level": {
                Player online = player.getPlayer();
                return online == null ? 0 : online.getLevel();
            }
            case "rank":
                return rank(player);
            case "variable": {
                String name = Tree.str(v, "variable", "").replace("{p}", uuid.toString());
                return Variables.getVariable(name, null, false);
            }
            case "player_nbt": {
                Object raw = LegacyPlayerData.path(uuid, Tree.str(v, "path", ""));
                return raw instanceof Byte b ? (Object) (int) b : raw;
            }
            case "player_metadata": {
                Player online = player.getPlayer();
                if (online == null) return null;
                for (MetadataValue m : online.getMetadata(Tree.str(v, "path", ""))) return m.value();
                return null;
            }
            case "npc_nbt":
                return npcValue(ctx, Tree.str(v, "path", ""));
            case "now":
                return System.currentTimeMillis() / 1000L;
            case "item": {
                Player online = player.getPlayer();
                return online == null ? 0 : ItemResolver.count(online, Tree.str(v, "item", ""));
            }
            case "quest":
                return questValue(uuid, v);
            case "position": {
                String npc = Tree.str(v, "npc");
                if (npc != null) return QuestStore.get(uuid).npcPosition(npc);
                return ctx.npcPosition();
            }
            default:
                return null;
        }
    }

    private static Object npcValue(ScriptContext ctx, String path) {
        if (ctx.npc() == null) return 0;
        if (path.equals("position")) return ctx.npcPosition();
        if (path.equals("identifiant") || path.equals("id")) return ctx.npc().id();
        Object raw = Tree.path(ctx.npc().raw(), path);
        return raw == null ? 0 : raw;
    }

    private static Object questValue(UUID uuid, Map<String, Object> v) {
        String questId = Tree.str(v, "quest", "");
        PlayerQuestData data = QuestStore.get(uuid);
        QuestProgress p = data.quests.get(questId);
        if (v.containsKey("step") && v.containsKey("substep")) {
            int step = Tree.integer(v, "step", 0);
            int sub = Tree.integer(v, "substep", 0);
            if (p == null) return 0;
            if (p.step == step) {
                Quest quest = QuestRegistry.quest(questId);
                QuestStep def = quest == null ? null : quest.step(step);
                int max = def != null && sub < def.objectives().size() ? def.objectives().get(sub).count() : 1;
                return p.count(sub, max);
            }
            if (step < p.step) {
                Quest quest = QuestRegistry.quest(questId);
                QuestStep def = quest == null ? null : quest.step(step);
                QuestObjective o = def != null && sub < def.objectives().size() ? def.objectives().get(sub) : null;
                return o == null ? 1 : o.count();
            }
            return 0;
        }
        if (v.containsKey("variable")) {
            Object value = p == null ? null : p.variables.get(Tree.str(v, "variable", ""));
            return value == null ? 0 : value;
        }
        return p == null ? -1 : p.step;
    }

    /** Index (1-based) du plus haut rang du joueur dans la liste configurée, 0 s'il n'en a aucun. */
    public static int rank(OfflinePlayer player) {
        Player online = player.getPlayer();
        if (online == null) return 0;
        List<String> ranks = QuestSettings.ranks();
        String prefix = QuestSettings.groupPermissionPrefix();
        int best = 0;
        for (int i = 0; i < ranks.size(); i++) {
            if (online.hasPermission(prefix + ranks.get(i))) best = i + 1;
        }
        return best;
    }

    /** Valeur numérique d'une variable de quête (0 si elle n'existe pas). */
    public static double variableNumber(OfflinePlayer player, String quest, String variable) {
        Object v = QuestEngine.variable(player, quest, variable);
        return v instanceof Number n ? n.doubleValue() : 0;
    }
}
