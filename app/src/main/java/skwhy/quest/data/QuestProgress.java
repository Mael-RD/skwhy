package skwhy.quest.data;

import java.util.LinkedHashMap;
import java.util.Map;

import skwhy.quest.model.QuestStatus;

/**
 * Progression d'un joueur dans une quête. {@code step} est 0-based ({@code -1} : aucune étape). Pour chaque objectif de
 * l'étape courante, {@code progress} contient un {@link Integer} (compteur) ou {@link Boolean#TRUE} (objectif terminé).
 */
public final class QuestProgress {

    public QuestStatus status = QuestStatus.AVAILABLE;
    public int step = -1;
    public final Map<Integer, Object> progress = new LinkedHashMap<>();
    public final Map<String, Object> variables = new LinkedHashMap<>();
    public boolean rewardClaimed;

    /** Valeur du compteur de l'objectif (le total demandé quand il est terminé). */
    public int count(int objective, int max) {
        Object v = progress.get(objective);
        if (v instanceof Boolean b && b) return max;
        if (v instanceof Number n) return n.intValue();
        return 0;
    }

    public boolean isDone(int objective) {
        return progress.get(objective) instanceof Boolean b && b;
    }
}
