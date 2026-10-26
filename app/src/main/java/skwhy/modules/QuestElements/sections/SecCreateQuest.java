package skwhy.modules.QuestElements.sections;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import ch.njol.skript.Skript;
import ch.njol.skript.config.Node;
import ch.njol.skript.config.SectionNode;
import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.Section;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.TriggerItem;
import ch.njol.util.Kleenean;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;
import skwhy.quest.QuestRegistry;
import skwhy.quest.model.Quest;

@Name("[Quest] Create Quest")
@Description("Defines a quest from a script instead of a file. Run it when your scripts load ('on load'); a quest file with the same id " +
    "loaded later (or by 'reload quests') replaces it. Keys: name (color tags allowed), type (principale, secondaire or contrée), category, priority, " +
    "variables (a list of texts), icon (an item id such as iron_sword) and head (the URL of a head texture), then 'step <n>:' sections (numbered from 1) with: " +
    "text, next (the number of the following step; the next section by default) and 'objective <n>:' sections with: type (external, action, item or special), " +
    "text, count, action (the action key of an 'action' objective, or jobs, quest, variable, advancement or rank for a 'special' one), item, and any other key of the " +
    "objective (jobs, list, variable, advancement, rank...). A step or an objective may have 'destination:' lines: 'npc <id>', 'point <world> <x> <y> <z>' or " +
    "'farm <world> <x> <z> <radius>'. A 'reward:' section lists what is given when the reward is claimed, one line each: " +
    "give: \"item\" <count>, message: \"text\", command: \"text\" (with {player}), money: <n>, experience: <n> and sound: \"name\". " +
    "A quest without step finishes as soon as it is unlocked. Numbers are not quoted, texts are.")
@Examples({
    "on load:",
    "\tcreate quest \"demo\":",
    "\t\tname: \"<gold>Démo\"",
    "\t\ttype: secondaire",
    "\t\tvariables: \"kills\"",
    "\t\tstep 1:",
    "\t\t\ttext: \"Casse des pierres\"",
    "\t\t\tobjective 1:",
    "\t\t\t\ttype: action",
    "\t\t\t\taction: \"break.stone\"",
    "\t\t\t\tcount: 5",
    "\t\t\t\ttext: \"Casse 5 pierres\"",
    "\t\t\t\tdestination: point \"world\" 0 70 0",
    "\t\treward:",
    "\t\t\tgive: \"diamond\" 3",
    "\t\t\tmessage: \"Bravo !\""
})
@Since("1.6.0")
public class SecCreateQuest extends Section {

    private Expression<String> idExpr;
    private Map<String, Object> tree;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, SkriptParser.ParseResult pr,
                        @Nullable SectionNode sectionNode, List<TriggerItem> triggerItems) {
        if (sectionNode == null) {
            Skript.error("'create quest' needs a section (add ':' at the end of the line).");
            return false;
        }
        idExpr = (Expression<String>) exprs[0];
        tree = new LinkedHashMap<>();
        return parseQuest(sectionNode, tree);
    }

    @Override
    protected @Nullable TriggerItem walk(Event event) {
        String id = idExpr.getSingle(event);
        if (id != null && !id.isBlank()) QuestRegistry.register(Quest.parse(id, deepCopy(tree)));
        return walk(event, false);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> deepCopy(Map<String, Object> source) {
        return (Map<String, Object>) skwhy.quest.Tree.deepCopy(source);
    }

    // ── Lecture de la section ───────────────────────────────────────────────

    private static boolean parseQuest(SectionNode node, Map<String, Object> out) {
        TreeMap<Integer, Map<String, Object>> steps = new TreeMap<>();
        List<Object> reward = new ArrayList<>();
        Map<String, Object> script = new LinkedHashMap<>();
        for (Node child : node) {
            String key = child.getKey() == null ? "" : child.getKey().trim();
            if (key.isEmpty() || key.startsWith("#")) continue;
            if (child instanceof SectionNode section) {
                if (key.startsWith("step ")) {
                    Integer number = number(key.substring(5));
                    if (number == null || number < 1) return fail("Invalid step number: '" + key + "' (steps are numbered from 1).");
                    Map<String, Object> step = new LinkedHashMap<>();
                    if (!parseStep(section, step, number)) return false;
                    steps.put(number, step);
                } else if (key.equals("reward")) {
                    if (!parseReward(section, script, reward)) return false;
                } else if (key.equals("icon")) {
                    return fail("'icon' is a single line: icon: <item id>.");
                } else {
                    return fail("Unknown section '" + key + "' in 'create quest'.");
                }
                continue;
            }
            int colon = key.indexOf(':');
            if (colon <= 0) return fail("Expected 'key: value' in 'create quest', found: " + key);
            String name = key.substring(0, colon).trim().toLowerCase();
            String value = key.substring(colon + 1).trim();
            switch (name) {
                case "name", "type", "category", "catégorie" -> out.put(name.equals("category") ? "catégorie" : name, text(value));
                case "priority" -> out.put("priority", value(value));
                case "variables" -> out.put("variables", list(value));
                case "icon" -> {
                    Map<String, Object> icon = skwhy.quest.Tree.map(out, "item").isEmpty() ? new LinkedHashMap<>() : new LinkedHashMap<>(skwhy.quest.Tree.map(out, "item"));
                    icon.put("id", text(value).contains(":") ? text(value) : "minecraft:" + text(value));
                    out.put("item", icon);
                }
                case "head" -> {
                    Map<String, Object> icon = new LinkedHashMap<>(skwhy.quest.Tree.map(out, "item"));
                    icon.putIfAbsent("id", "minecraft:player_head");
                    icon.put("head", text(value));
                    out.put("item", icon);
                }
                default -> { return fail("Unknown key '" + name + "' in 'create quest'."); }
            }
        }
        List<Object> list = new ArrayList<>();
        int expected = 1;
        for (Map.Entry<Integer, Map<String, Object>> e : steps.entrySet()) {
            if (e.getKey() != expected) return fail("Steps must be numbered 1, 2, 3... without gaps: step " + expected + " is missing.");
            list.add(e.getValue());
            expected++;
        }
        out.put("steps", list);
        if (!reward.isEmpty()) {
            Map<String, Object> main = new LinkedHashMap<>();
            main.put("action", "list");
            main.put("mode", "list");
            main.put("liste", reward);
            script.put("reward", main);
        }
        if (!script.isEmpty()) out.put("script", script);
        return true;
    }

    private static boolean parseStep(SectionNode node, Map<String, Object> out, int number) {
        TreeMap<Integer, Map<String, Object>> objectives = new TreeMap<>();
        List<Object> destinations = new ArrayList<>();
        for (Node child : node) {
            String key = child.getKey() == null ? "" : child.getKey().trim();
            if (key.isEmpty() || key.startsWith("#")) continue;
            if (child instanceof SectionNode section) {
                if (!key.startsWith("objective ")) return fail("Unknown section '" + key + "' in step " + number + ".");
                Integer n = number(key.substring(10));
                if (n == null || n < 1) return fail("Invalid objective number: '" + key + "' (objectives are numbered from 1).");
                Map<String, Object> objective = new LinkedHashMap<>();
                if (!parseObjective(section, objective)) return false;
                objectives.put(n, objective);
                continue;
            }
            int colon = key.indexOf(':');
            if (colon <= 0) return fail("Expected 'key: value' in step " + number + ", found: " + key);
            String name = key.substring(0, colon).trim().toLowerCase();
            String value = key.substring(colon + 1).trim();
            switch (name) {
                case "text" -> out.put("text", text(value));
                case "next" -> {
                    Integer n = number(value);
                    if (n == null || n < 1) return fail("'next' must be a step number (from 1) in step " + number + ".");
                    out.put("next", n - 1);
                }
                case "destination" -> {
                    Map<String, Object> d = destination(value);
                    if (d == null) return fail("Invalid destination '" + value + "'.");
                    destinations.add(d);
                }
                default -> { return fail("Unknown key '" + name + "' in step " + number + "."); }
            }
        }
        if (!destinations.isEmpty()) out.put("destinations", destinations);
        if (!objectives.isEmpty()) {
            List<Object> list = new ArrayList<>();
            int expected = 1;
            for (Map.Entry<Integer, Map<String, Object>> e : objectives.entrySet()) {
                if (e.getKey() != expected) return fail("Objectives must be numbered 1, 2, 3... without gaps in step " + number + ".");
                list.add(e.getValue());
                expected++;
            }
            out.put("objectives", list);
        }
        return true;
    }

    private static boolean parseObjective(SectionNode node, Map<String, Object> out) {
        List<Object> destinations = new ArrayList<>();
        for (Node child : node) {
            String key = child.getKey() == null ? "" : child.getKey().trim();
            if (key.isEmpty() || key.startsWith("#")) continue;
            if (child instanceof SectionNode) return fail("An objective cannot contain sections.");
            int colon = key.indexOf(':');
            if (colon <= 0) return fail("Expected 'key: value' in an objective, found: " + key);
            String name = key.substring(0, colon).trim().toLowerCase();
            String value = key.substring(colon + 1).trim();
            if (name.equals("destination")) {
                Map<String, Object> d = destination(value);
                if (d == null) return fail("Invalid destination '" + value + "'.");
                destinations.add(d);
            } else if (name.equals("type")) {
                String type = text(value).toLowerCase();
                out.put("type", type.equals("external") || type.equals("externe") ? "externe" : type);
            } else if (name.equals("list")) {
                out.put("list", list(value));
            } else {
                out.put(name, value(value));
            }
        }
        out.putIfAbsent("type", "externe");
        if (!destinations.isEmpty()) out.put("destinations", destinations);
        return true;
    }

    private static boolean parseReward(SectionNode node, Map<String, Object> script, List<Object> reward) {
        int index = 0;
        for (Node child : node) {
            String key = child.getKey() == null ? "" : child.getKey().trim();
            if (key.isEmpty() || key.startsWith("#")) continue;
            if (child instanceof SectionNode) return fail("The reward cannot contain sections.");
            int colon = key.indexOf(':');
            if (colon <= 0) return fail("Expected 'key: value' in the reward, found: " + key);
            String name = key.substring(0, colon).trim().toLowerCase();
            String value = key.substring(colon + 1).trim();
            Map<String, Object> action = new LinkedHashMap<>();
            switch (name) {
                case "give" -> {
                    List<String> parts = tokens(value);
                    if (parts.isEmpty()) return fail("'give' needs an item id.");
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("item", text(parts.get(0)));
                    item.put("count", parts.size() > 1 ? value(parts.get(1)) : 1);
                    List<Object> items = new ArrayList<>();
                    items.add(item);
                    action.put("action", "give");
                    action.put("items", items);
                }
                case "message" -> { action.put("action", "message"); action.put("text", text(value)); }
                case "command" -> { action.put("action", "command"); action.put("command", text(value)); }
                case "money" -> { action.put("action", "money"); action.put("operator", "add"); action.put("value", value(value)); }
                case "experience" -> {
                    action.put("action", "experience"); action.put("mode", false); action.put("operator", "add"); action.put("value", value(value));
                }
                case "sound" -> { action.put("action", "sound"); action.put("sound", text(value)); }
                default -> { return fail("Unknown reward line '" + name + "'."); }
            }
            String id = "reward_" + (++index);
            script.put(id, action);
            reward.add(id);
        }
        return true;
    }

    // ── Valeurs ─────────────────────────────────────────────────────────────

    private static boolean fail(String message) {
        Skript.error(message);
        return false;
    }

    private static Integer number(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String text(String raw) {
        String t = raw.trim();
        if (t.length() >= 2 && t.startsWith("\"") && t.endsWith("\"")) return t.substring(1, t.length() - 1).replace("\"\"", "\"");
        return t;
    }

    /** Un nombre, un booléen, ou un texte (guillemets facultatifs). */
    private static Object value(String raw) {
        String t = raw.trim();
        if (t.startsWith("\"")) return text(t);
        try {
            return Integer.parseInt(t);
        } catch (NumberFormatException ignored) { }
        try {
            return Double.parseDouble(t);
        } catch (NumberFormatException ignored) { }
        if (t.equalsIgnoreCase("true")) return true;
        if (t.equalsIgnoreCase("false")) return false;
        return t;
    }

    /** Découpe en tenant compte des guillemets : {@code "a b" 3} donne {@code "a b"} et {@code 3}. */
    private static List<String> tokens(String raw) {
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '"') quoted = !quoted;
            if (!quoted && Character.isWhitespace(c)) {
                if (current.length() > 0) { out.add(current.toString()); current.setLength(0); }
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) out.add(current.toString());
        return out;
    }

    private static List<Object> list(String raw) {
        List<Object> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '"') quoted = !quoted;
            if (c == ',' && !quoted) {
                if (!current.toString().isBlank()) out.add(text(current.toString()));
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        if (!current.toString().isBlank()) out.add(text(current.toString()));
        return out;
    }

    private static Map<String, Object> destination(String raw) {
        List<String> parts = tokens(raw);
        if (parts.isEmpty()) return null;
        Map<String, Object> out = new LinkedHashMap<>();
        switch (parts.get(0).toLowerCase()) {
            case "npc" -> {
                if (parts.size() < 2) return null;
                out.put("npc", text(parts.get(1)));
            }
            case "point" -> {
                if (parts.size() < 5) return null;
                out.put("type", "objective");
                out.put("world", text(parts.get(1)));
                out.put("x", value(parts.get(2)));
                out.put("y", value(parts.get(3)));
                out.put("z", value(parts.get(4)));
            }
            case "farm" -> {
                if (parts.size() < 5) return null;
                out.put("type", "farm");
                out.put("world", text(parts.get(1)));
                out.put("x", value(parts.get(2)));
                out.put("z", value(parts.get(3)));
                out.put("radius", value(parts.get(4)));
            }
            default -> {
                return null;
            }
        }
        return out;
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "create quest " + idExpr.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.SECTION,
            SyntaxInfo.builder(SecCreateQuest.class)
                .addPattern("create quest %string%")
                .build()
        );
    }
}
