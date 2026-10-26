package skwhy.quest.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import skwhy.quest.Tree;

/** Définition d'un NPC (fichier {@code quest-npcs/<id>.yml} ou {@code npcs/<id>.nbt}). */
public final class QuestNpc {

    private final String id;
    private final Map<String, Object> raw;
    private final String name;
    private final String hover;
    private final String type;
    private final boolean nameVisible;
    private final String meetMessage;
    private final List<String> texts;
    private final List<String> attackTexts;
    private final Map<String, Object> trade;
    private final Map<String, Object> trades;
    private final ScriptSet script;
    private final List<NpcEntityRef> entities = new ArrayList<>();

    private QuestNpc(String id, Map<String, Object> raw) {
        this.id = id;
        this.raw = raw;
        this.name = Tree.str(raw, "name");
        this.hover = Tree.str(raw, "hover");
        this.type = Tree.str(raw, "type");
        this.nameVisible = Tree.bool(raw, "name_visible", false);
        this.meetMessage = Tree.str(raw, "rencontre", Tree.str(raw, "meet"));
        this.texts = Tree.strings(raw, "text");
        this.attackTexts = Tree.strings(raw, "attaque");
        this.trade = Tree.map(raw, "trade");
        this.trades = Tree.map(raw, "trades");
        this.script = new ScriptSet(Tree.map(raw, "script"));
        Map<String, Object> ents = Tree.map(raw, "entity");
        for (Map.Entry<String, Object> e : ents.entrySet()) {
            Map<String, Object> d = Tree.map(e.getValue());
            Map<String, Object> loc = Tree.map(d, "location");
            entities.add(new NpcEntityRef(id, e.getKey(), Tree.integer(d, "position", 1), Tree.str(loc, "world"),
                Tree.dbl(loc, "x", 0), Tree.dbl(loc, "y", 0), Tree.dbl(loc, "z", 0)));
        }
    }

    public static QuestNpc parse(String id, Map<String, Object> raw) {
        return new QuestNpc(id, raw == null ? new LinkedHashMap<>() : raw);
    }

    public String id() { return id; }
    public Map<String, Object> raw() { return raw; }
    /** Nom affiché (couleurs possibles) ou {@code null} : l'id est alors utilisé. */
    public String name() { return name; }
    public String hover() { return hover; }
    /** {@code discour}, {@code marchand}, {@code scripté} ou {@code null}. */
    public String type() { return type; }
    public boolean nameVisible() { return nameVisible; }
    public String meetMessage() { return meetMessage; }
    public List<String> texts() { return texts; }
    public List<String> attackTexts() { return attackTexts; }
    public Map<String, Object> trade() { return trade; }
    public Map<String, Object> trades() { return trades; }
    public ScriptSet script() { return script; }
    public List<NpcEntityRef> entities() { return entities; }

    public NpcEntityRef entityAt(int position) {
        for (NpcEntityRef r : entities) if (r.position() == position) return r;
        return null;
    }

    public List<Integer> positions() {
        List<Integer> out = new ArrayList<>();
        for (NpcEntityRef r : entities) if (!out.contains(r.position())) out.add(r.position());
        out.sort(Integer::compare);
        return out;
    }

    public String displayName() { return name != null ? name : id; }

    @Override
    public String toString() { return "quest npc " + id; }
}
