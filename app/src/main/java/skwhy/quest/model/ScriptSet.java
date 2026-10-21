package skwhy.quest.model;

import java.util.LinkedHashMap;
import java.util.Map;

import skwhy.quest.Tree;

/** Ensemble des nœuds de script d'une quête ou d'un NPC. */
public final class ScriptSet {

    private final Map<String, ScriptNode> nodes = new LinkedHashMap<>();

    public ScriptSet(Map<String, Object> script) {
        for (Map.Entry<String, Object> e : script.entrySet()) {
            if (e.getValue() instanceof Map<?, ?>) nodes.put(e.getKey(), new ScriptNode(e.getKey(), Tree.map(e.getValue())));
        }
    }

    public ScriptNode get(String id) { return id == null ? null : nodes.get(id); }

    public boolean has(String id) { return id != null && nodes.containsKey(id); }

    public Map<String, ScriptNode> all() { return nodes; }
}
