package skwhy.quest.data;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import skwhy.quest.QuestSettings;
import skwhy.quest.Tree;
import skwhy.quest.model.QuestStatus;

/**
 * Accès en lecture seule aux anciens fichiers joueur ({@code <uuid>.dat}) : métiers, options de genre... et import
 * de l'ancienne progression de quêtes.
 */
public final class LegacyPlayerData {

    private LegacyPlayerData() {}

    /** Valeur d'un chemin {@code a;b;c} du NBT custom du joueur (toujours à jour, sans cache). */
    public static Object path(UUID uuid, String path) {
        return PlayerNbt.path(uuid, path);
    }

    /** {@code true} si le joueur a choisi le genre féminin dans ses options. */
    public static boolean feminine(UUID uuid) {
        return Tree.toBool(path(uuid, "options;genre"), false);
    }

    /** Lit la progression de quête du NBT custom du joueur, ou {@code null} s'il n'y en a pas. */
    @SuppressWarnings("unchecked")
    public static PlayerQuestData importQuests(UUID uuid) {
        Object quest = path(uuid, "quest");
        if (!(quest instanceof Map<?, ?> map) || map.isEmpty()) return null;
        return parse(uuid, (Map<String, Object>) map);
    }

    /** Construit les données d'un joueur depuis l'arbre {@code quest} de son NBT. */
    public static PlayerQuestData parse(UUID uuid, Map<String, Object> quest) {

        PlayerQuestData data = new PlayerQuestData(uuid);
        data.compass = Tree.toBool(quest.get("boussole"), QuestSettings.compassAuto());
        data.selected = Tree.str(quest, "sélectionnée");
        data.categories.addAll(Tree.strings(quest, "catégorie"));
        data.metNpcs.addAll(Tree.strings(quest, "npcs"));
        data.knownNames.addAll(Tree.strings(quest, "npc_name"));
        for (Map.Entry<String, Object> e : Tree.map(quest, "npcs_pos").entrySet()) {
            if (e.getValue() instanceof Number n) data.npcPositions.put(e.getKey(), n.intValue());
        }
        for (Map.Entry<String, Object> e : Tree.map(quest, "dialogues").entrySet()) {
            data.dialogues.put(e.getKey(), String.valueOf(e.getValue()));
        }
        Map<String, Object> filter = Tree.map(quest, "filtre");
        data.filterState = Tree.str(filter, "etat", "all");
        data.filterType = Tree.str(filter, "type", "all");

        for (Map.Entry<String, Object> e : Tree.map(quest, "liste").entrySet()) {
            Map<String, Object> q = Tree.map(e.getValue());
            // Une entrée sans statut ne contient que des variables écrites par un script : la quête n'est pas débloquée
            if (!q.containsKey("statut")) continue;
            QuestProgress p = new QuestProgress();
            QuestStatus status = QuestStatus.parse(Tree.str(q, "statut"));
            p.status = status == null || status == QuestStatus.UNKNOWN ? QuestStatus.AVAILABLE : status;
            p.step = Tree.integer(q, "step", -1);
            for (Map.Entry<String, Object> pe : Tree.map(q, "progress").entrySet()) {
                try {
                    int idx = Integer.parseInt(pe.getKey());
                    Object v = pe.getValue();
                    // Un booléen NBT est un octet ; un compteur est un entier
                    if (v instanceof Byte b) p.progress.put(idx, b != 0);
                    else if (v instanceof Number n) p.progress.put(idx, n.intValue());
                } catch (NumberFormatException ignored) { }
            }
            for (Map.Entry<String, Object> ve : Tree.map(q, "variables").entrySet()) {
                Object v = ve.getValue();
                if (v instanceof Byte b) v = b != 0;
                if (v instanceof List<?> || v instanceof Map<?, ?>) continue;
                p.variables.put(ve.getKey(), v);
            }
            p.rewardClaimed = Tree.toBool(q.get("récompense"), false);
            data.quests.put(e.getKey(), p);
        }
        return data;
    }
}
