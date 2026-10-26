package skwhy.quest.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import skwhy.quest.Tree;

/** Définition d'une quête chargée depuis un fichier YAML ou NBT. */
public final class Quest {

    private final String id;
    private final Map<String, Object> raw;
    private final String name;
    private final String type;
    private final String category;
    private final int priority;
    private final Map<String, Object> icon;
    private final List<String> variables;
    private final List<QuestStep> steps = new ArrayList<>();
    private final ScriptSet script;

    private Quest(String id, Map<String, Object> raw) {
        this.id = id;
        this.raw = raw;
        this.name = Tree.str(raw, "name", id);
        this.type = Tree.str(raw, "type", "principale");
        this.category = Tree.str(raw, "catégorie", Tree.str(raw, "category", "villes"));
        this.priority = Tree.integer(raw, "priority", 0);
        this.icon = Tree.map(raw, "item");
        this.variables = Tree.strings(raw, "variables");
        int i = 0;
        for (Map<String, Object> s : Tree.maps(raw, "steps")) steps.add(new QuestStep(this, i++, s));
        this.script = new ScriptSet(Tree.map(raw, "script"));
    }

    public static Quest parse(String id, Map<String, Object> raw) {
        return new Quest(id, raw == null ? new LinkedHashMap<>() : raw);
    }

    public String id() { return id; }
    public Map<String, Object> raw() { return raw; }
    public String name() { return name; }
    /** {@code principale}, {@code secondaire} ou {@code contrée}. */
    public String type() { return type; }
    public String category() { return category; }
    public int priority() { return priority; }
    public Map<String, Object> icon() { return icon; }
    public List<String> variables() { return variables; }
    public List<QuestStep> steps() { return steps; }
    public ScriptSet script() { return script; }

    public QuestStep step(int index) {
        return index >= 0 && index < steps.size() ? steps.get(index) : null;
    }

    public boolean hasVariable(String name) { return variables.contains(name); }

    @Override
    public String toString() { return "quest " + id; }
}
