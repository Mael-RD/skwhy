package skwhy.quest.model;

import java.util.Map;

import skwhy.quest.Tree;

/** Un nœud d'un script (action ou condition), identifié par son id dans le {@code script} d'une quête ou d'un NPC. */
public record ScriptNode(String id, Map<String, Object> data) {

    /** Type d'action ({@code list} quand le nœud n'en déclare pas). */
    public String action() { return Tree.str(data, "action", "list"); }

    public boolean isCondition() { return data.containsKey("condition") && !data.containsKey("action"); }

    public String conditionType() { return Tree.str(data, "condition", ""); }

    public boolean has(String key) { return data.containsKey(key); }

    public String str(String key) { return Tree.str(data, key); }

    public String str(String key, String def) { return Tree.str(data, key, def); }

    public int integer(String key, int def) { return Tree.integer(data, key, def); }

    public double dbl(String key, double def) { return Tree.dbl(data, key, def); }

    public boolean bool(String key, boolean def) { return Tree.bool(data, key, def); }

    /** Id du nœud de condition qui garde ce nœud, ou {@code null}. */
    public String guard() {
        Object c = data.get("condition");
        return c instanceof String s && !isCondition() ? s : null;
    }
}
