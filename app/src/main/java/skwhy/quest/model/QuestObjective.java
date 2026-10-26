package skwhy.quest.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import skwhy.quest.Tree;

/** Objectif d'une étape. Les index sont 0-based en interne ; les syntaxes Skript les exposent en 1-based. */
public final class QuestObjective {

    private final int index;
    private final ObjectiveType type;
    private final int count;
    private final String text;
    private final String key;
    private final String special;
    private final Map<String, Object> data;
    private final List<QuestDestination> destinations = new ArrayList<>();
    private Quest quest;
    private int step;

    QuestObjective(int index, Map<String, Object> data) {
        this.index = index;
        this.data = data;
        this.type = ObjectiveType.parse(Tree.str(data, "type"));
        this.count = Math.max(1, Tree.integer(data, "count", 1));
        this.text = Tree.str(data, "text", "");
        switch (type) {
            case ACTION -> { key = Tree.str(data, "action", ""); special = null; }
            case ITEM -> { key = Tree.str(data, "item", ""); special = null; }
            case SPECIAL -> { key = null; special = Tree.str(data, "action", ""); }
            default -> { key = null; special = null; }
        }
        for (Map<String, Object> d : Tree.maps(data, "destinations")) destinations.add(QuestDestination.parse(d));
    }

    void attach(Quest quest, int step) {
        this.quest = quest;
        this.step = step;
    }

    public int index() { return index; }
    public ObjectiveType type() { return type; }
    public int count() { return count; }
    public String text() { return text; }
    /** Clé d'action ({@code break.stone}) ou identifiant d'item, selon le type. */
    public String key() { return key; }
    /** Sous-type d'un objectif spécial ({@code jobs}, {@code quest}, {@code variable}, {@code advancement}, {@code rank}). */
    public String special() { return special; }
    public Map<String, Object> data() { return data; }
    public List<QuestDestination> destinations() { return destinations; }
    public Quest quest() { return quest; }
    public int step() { return step; }

    @Override
    public String toString() {
        return "objective " + (index + 1) + " of step " + (step + 1) + " of quest " + (quest == null ? "?" : quest.id());
    }
}
