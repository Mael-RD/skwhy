package skwhy.quest.data;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import skwhy.quest.NbtTree;
import skwhy.quest.QuestSettings;
import skwhy.quest.Tree;
import skwhy.quest.model.QuestStatus;

/**
 * Accès en lecture seule aux anciens fichiers joueur ({@code <uuid>.dat}) : métiers, options de genre... et import
 * de l'ancienne progression de quêtes.
 */
public final class LegacyPlayerData {

    private LegacyPlayerData() {}

    /** Lit le fichier du joueur (toujours à jour, sans cache) ; {@code null} s'il n'existe pas. */
    public static Map<String, Object> read(UUID uuid) {
        return NbtTree.read(new File(QuestSettings.legacyPlayerFolder(), uuid + ".dat"));
    }

    /** Valeur d'un chemin {@code a;b;c} du fichier joueur historique. */
    public static Object path(UUID uuid, String path) {
        Map<String, Object> root = read(uuid);
        return root == null ? null : Tree.path(root, path);
    }

    /** {@code true} si le joueur a choisi le genre féminin dans ses options. */
    public static boolean feminine(UUID uuid) {
        return Tree.toBool(path(uuid, "options;genre"), false);
    }

    /** Importe la progression de quête de l'ancien fichier, ou {@code null} s'il n'y en a pas. */
    public static PlayerQuestData importQuests(UUID uuid) {
        Map<String, Object> root = read(uuid);
        if (root == null) return null;
        Map<String, Object> quest = Tree.map(root, "quest");
        if (quest.isEmpty()) return null;

        PlayerQuestData data = new PlayerQuestData(uuid);
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
                if (v instanceof Byte b) v = (int) b;
                if (v instanceof List<?>) continue;
                p.variables.put(ve.getKey(), v);
            }
            p.rewardClaimed = Tree.toBool(q.get("récompense"), false);
            data.quests.put(e.getKey(), p);
        }
        data.markDirty();
        return data;
    }
}
