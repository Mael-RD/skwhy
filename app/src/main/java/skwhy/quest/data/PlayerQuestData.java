package skwhy.quest.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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

    /** Appelé à chaque modification (branché par {@link QuestStore}, absent dans les tests). */
    static java.util.function.Consumer<PlayerQuestData> onDirty;

    boolean dirty;
    /** Empreinte du NBT au dernier échange (lecture ou écriture) : si elle change, quelqu'un d'autre l'a modifié. */
    long stamp = Long.MIN_VALUE;

    /** Index des objectifs actifs, reconstruit par le moteur à chaque changement d'étape. */
    public transient ActiveIndex index = new ActiveIndex();

    public PlayerQuestData(UUID uuid) {
        this.uuid = uuid;
    }

    /** Marque les données modifiées : elles sont écrites tout de suite dans le NBT du joueur (au plus tard à la fin du tick). */
    public void markDirty() {
        dirty = true;
        if (onDirty != null) onDirty.accept(this);
    }

    public boolean isDirty() { return dirty; }

    public QuestStatus status(String quest) {
        QuestProgress p = quests.get(quest);
        return p == null ? QuestStatus.UNKNOWN : p.status;
    }

    public int npcPosition(String npc) {
        return npcPositions.getOrDefault(npc, 1);
    }

    /** Remplace tout le contenu par celui de {@code other} (relecture après une modification externe du NBT). */
    void replaceWith(PlayerQuestData other) {
        quests.clear();
        quests.putAll(other.quests);
        selected = other.selected;
        categories.clear();
        categories.addAll(other.categories);
        metNpcs.clear();
        metNpcs.addAll(other.metNpcs);
        knownNames.clear();
        knownNames.addAll(other.knownNames);
        npcPositions.clear();
        npcPositions.putAll(other.npcPositions);
        dialogues.clear();
        dialogues.putAll(other.dialogues);
        compass = other.compass;
        filterState = other.filterState;
        filterType = other.filterType;
    }

    /** Index des objectifs de l'étape courante de chaque quête commencée. */
    public static final class ActiveIndex {
        public final Map<String, List<skwhy.quest.model.QuestObjective>> actions = new HashMap<>();
        public final List<skwhy.quest.model.QuestObjective> items = new ArrayList<>();
        public final Map<String, List<skwhy.quest.model.QuestObjective>> specials = new HashMap<>();
    }
}
