package skwhy.quest.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import skwhy.quest.Tree;

public final class QuestStep {

    private final int index;
    private final String text;
    private final int next;
    private final String condition;
    private final List<QuestObjective> objectives = new ArrayList<>();
    private final List<QuestDestination> destinations = new ArrayList<>();

    QuestStep(Quest quest, int index, Map<String, Object> data) {
        this.index = index;
        this.text = Tree.str(data, "text", "");
        this.next = Tree.integer(data, "next", index + 1);
        this.condition = Tree.str(data, "condition");
        int i = 0;
        for (Map<String, Object> o : Tree.maps(data, "objectives")) {
            QuestObjective obj = new QuestObjective(i++, o);
            obj.attach(quest, index);
            objectives.add(obj);
        }
        for (Map<String, Object> d : Tree.maps(data, "destinations")) destinations.add(QuestDestination.parse(d));
    }

    public int index() { return index; }
    public String text() { return text; }
    /** Index (0-based) de l'étape suivante ; une valeur hors tableau termine la quête. */
    public int next() { return next; }
    /** Id d'un nœud de condition du script de la quête, ou {@code null}. */
    public String condition() { return condition; }
    public List<QuestObjective> objectives() { return objectives; }
    public List<QuestDestination> destinations() { return destinations; }
}
