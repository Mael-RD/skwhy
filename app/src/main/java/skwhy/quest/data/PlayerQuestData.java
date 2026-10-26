package skwhy.quest.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import skwhy.quest.Tree;
import skwhy.quest.model.QuestStatus;

/** Toutes les données de quête d'un joueur. Accédées depuis le thread principal du serveur. */
public final class PlayerQuestData {

    public final UUID uuid;
    public final Map<String, QuestProgress> quests = new LinkedHashMap<>();
    public String selected;
    public final List<String> categories = new ArrayList<>();
    public final Set<String> metNpcs = new LinkedHashSet<>();
    public final Set<String> knownNames = new LinkedHashSet<>();
    public final Map<String, Integer> npcPositions = new LinkedHashMap<>();
    public final Map<String, String> dialogues = new LinkedHashMap<>();
    public boolean compass;
    public String filterState = "all";
    public String filterType = "all";

    boolean dirty;

    /** Index des objectifs actifs, reconstruit par le moteur à chaque changement d'étape. */
    public transient ActiveIndex index = new ActiveIndex();

    public PlayerQuestData(UUID uuid) {
        this.uuid = uuid;
    }

    public void markDirty() { dirty = true; }

    public boolean isDirty() { return dirty; }

    public QuestStatus status(String quest) {
        QuestProgress p = quests.get(quest);
        return p == null ? QuestStatus.UNKNOWN : p.status;
    }

    public int npcPosition(String npc) {
        return npcPositions.getOrDefault(npc, 1);
    }

    // ── Sérialisation ───────────────────────────────────────────────────────

    public Map<String, Object> toTree() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("version", 1);
        if (selected != null) out.put("selected", selected);
        out.put("categories", new ArrayList<>(categories));
        out.put("met-npcs", new ArrayList<>(metNpcs));
        out.put("known-names", new ArrayList<>(knownNames));
        out.put("npc-positions", new LinkedHashMap<String, Object>(npcPositions));
        out.put("dialogues", new LinkedHashMap<String, Object>(dialogues));
        out.put("compass", compass);
        Map<String, Object> filter = new LinkedHashMap<>();
        filter.put("state", filterState);
        filter.put("type", filterType);
        out.put("filter", filter);
        Map<String, Object> qs = new LinkedHashMap<>();
        for (Map.Entry<String, QuestProgress> e : quests.entrySet()) {
            QuestProgress p = e.getValue();
            Map<String, Object> q = new LinkedHashMap<>();
            q.put("status", p.status.id());
            q.put("step", p.step);
            Map<String, Object> prog = new LinkedHashMap<>();
            for (Map.Entry<Integer, Object> pe : p.progress.entrySet()) prog.put(String.valueOf(pe.getKey()), pe.getValue());
            q.put("progress", prog);
            q.put("variables", new LinkedHashMap<String, Object>(p.variables));
            q.put("reward", p.rewardClaimed);
            qs.put(e.getKey(), q);
        }
        out.put("quests", qs);
        return out;
    }

    public static PlayerQuestData fromTree(UUID uuid, Map<String, Object> tree) {
        PlayerQuestData d = new PlayerQuestData(uuid);
        d.selected = Tree.str(tree, "selected");
        d.categories.addAll(Tree.strings(tree, "categories"));
        d.metNpcs.addAll(Tree.strings(tree, "met-npcs"));
        d.knownNames.addAll(Tree.strings(tree, "known-names"));
        for (Map.Entry<String, Object> e : Tree.map(tree, "npc-positions").entrySet()) {
            if (e.getValue() instanceof Number n) d.npcPositions.put(e.getKey(), n.intValue());
        }
        for (Map.Entry<String, Object> e : Tree.map(tree, "dialogues").entrySet()) d.dialogues.put(e.getKey(), String.valueOf(e.getValue()));
        d.compass = Tree.bool(tree, "compass", false);
        Map<String, Object> filter = Tree.map(tree, "filter");
        d.filterState = Tree.str(filter, "state", "all");
        d.filterType = Tree.str(filter, "type", "all");
        for (Map.Entry<String, Object> e : Tree.map(tree, "quests").entrySet()) {
            Map<String, Object> q = Tree.map(e.getValue());
            QuestProgress p = new QuestProgress();
            QuestStatus status = QuestStatus.parse(Tree.str(q, "status"));
            p.status = status == null || status == QuestStatus.UNKNOWN ? QuestStatus.AVAILABLE : status;
            p.step = Tree.integer(q, "step", -1);
            for (Map.Entry<String, Object> pe : Tree.map(q, "progress").entrySet()) {
                try {
                    int idx = Integer.parseInt(pe.getKey());
                    Object v = pe.getValue();
                    p.progress.put(idx, v instanceof Boolean ? v : (v instanceof Number n ? (Object) n.intValue() : v));
                } catch (NumberFormatException ignored) { }
            }
            p.variables.putAll(Tree.map(q, "variables"));
            p.rewardClaimed = Tree.bool(q, "reward", false);
            d.quests.put(e.getKey(), p);
        }
        return d;
    }

    /** Copie des index pour éviter les collisions entre threads lors des sauvegardes. */
    public Map<String, Object> snapshot() {
        return toTree();
    }

    /** Index des objectifs de l'étape courante de chaque quête commencée. */
    public static final class ActiveIndex {
        public final Map<String, List<skwhy.quest.model.QuestObjective>> actions = new HashMap<>();
        public final List<skwhy.quest.model.QuestObjective> items = new ArrayList<>();
        public final Map<String, List<skwhy.quest.model.QuestObjective>> specials = new HashMap<>();
    }
}
