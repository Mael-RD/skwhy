package skwhy.quest;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import skwhy.SkWhy;
import skwhy.quest.model.NpcEntityRef;
import skwhy.quest.model.Quest;
import skwhy.quest.model.QuestNpc;

/**
 * Définitions chargées en mémoire : quêtes, NPC et index des entités de NPC. Les fichiers YAML du dossier de SkWhy
 * priment sur les anciens fichiers NBT du même identifiant.
 */
public final class QuestRegistry {

    private static final Map<String, Quest> QUESTS = new ConcurrentHashMap<>();
    private static final Map<String, QuestNpc> NPCS = new ConcurrentHashMap<>();
    private static final Map<String, NpcEntityRef> ENTITY_INDEX = new ConcurrentHashMap<>();
    /** Entités liées à un NPC par un script : elles survivent au rechargement des fichiers. */
    private static final Map<String, NpcEntityRef> BOUND = new ConcurrentHashMap<>();
    private static volatile List<Quest> sorted = List.of();

    private QuestRegistry() {}

    // ── Chargement ──────────────────────────────────────────────────────────

    public static synchronized void reloadAll() {
        QUESTS.clear();
        NPCS.clear();
        loadQuestFiles();
        loadNpcFiles();
        rebuildIndexes();
    }

    public static synchronized boolean reloadQuest(String id) {
        Map<String, Object> tree = readDefinition(QuestSettings.questFolder(), new File(QuestSettings.legacyFolder(), "quests"), id);
        if (tree == null) {
            QUESTS.remove(id);
            rebuildIndexes();
            return false;
        }
        QUESTS.put(id, Quest.parse(id, tree));
        rebuildIndexes();
        return true;
    }

    public static synchronized boolean reloadNpc(String id) {
        Map<String, Object> tree = readDefinition(QuestSettings.npcFolder(), new File(QuestSettings.legacyFolder(), "npcs"), id);
        if (tree == null) {
            NPCS.remove(id);
            rebuildIndexes();
            return false;
        }
        NPCS.put(id, QuestNpc.parse(id, tree));
        rebuildIndexes();
        return true;
    }

    private static void loadQuestFiles() {
        for (Map.Entry<String, Map<String, Object>> e : readFolder(new File(QuestSettings.legacyFolder(), "quests")).entrySet()) {
            QUESTS.put(e.getKey(), Quest.parse(e.getKey(), e.getValue()));
        }
        for (Map.Entry<String, Map<String, Object>> e : readFolder(QuestSettings.questFolder()).entrySet()) {
            QUESTS.put(e.getKey(), Quest.parse(e.getKey(), e.getValue()));
        }
    }

    private static void loadNpcFiles() {
        for (Map.Entry<String, Map<String, Object>> e : readFolder(new File(QuestSettings.legacyFolder(), "npcs")).entrySet()) {
            NPCS.put(e.getKey(), QuestNpc.parse(e.getKey(), e.getValue()));
        }
        for (Map.Entry<String, Map<String, Object>> e : readFolder(QuestSettings.npcFolder()).entrySet()) {
            NPCS.put(e.getKey(), QuestNpc.parse(e.getKey(), e.getValue()));
        }
    }

    private static Map<String, Map<String, Object>> readFolder(File folder) {
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        File[] files = folder.listFiles();
        if (files == null) return out;
        for (File f : files) {
            String name = f.getName();
            int dot = name.lastIndexOf('.');
            if (dot <= 0) continue;
            String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
            String id = name.substring(0, dot);
            Map<String, Object> tree = switch (ext) {
                case "yml", "yaml" -> YamlTree.read(f);
                case "nbt" -> NbtTree.read(f);
                default -> null;
            };
            if (tree == null) {
                if (ext.equals("yml") || ext.equals("yaml") || ext.equals("nbt")) {
                    SkWhy.getInstance().getLogger().warning("[Quest] Fichier illisible : " + f.getPath());
                }
                continue;
            }
            // Un fichier .nbt ne remplace pas un .yml du même id présent dans le même dossier
            if (ext.equals("nbt") && out.containsKey(id)) continue;
            out.put(id, tree);
        }
        return out;
    }

    private static Map<String, Object> readDefinition(File folder, File legacy, String id) {
        Map<String, Object> tree = YamlTree.read(new File(folder, id + ".yml"));
        if (tree == null) tree = NbtTree.read(new File(legacy, id + ".nbt"));
        return tree;
    }

    // ── Enregistrement par code (section « create quest ») ──────────────────

    public static synchronized Quest register(Quest quest) {
        QUESTS.put(quest.id(), quest);
        rebuildIndexes();
        return quest;
    }

    public static synchronized QuestNpc register(QuestNpc npc) {
        NPCS.put(npc.id(), npc);
        rebuildIndexes();
        return npc;
    }

    private static void rebuildIndexes() {
        List<Quest> list = new ArrayList<>(QUESTS.values());
        list.sort(Comparator.comparingInt(Quest::priority).thenComparing(Quest::id));
        sorted = List.copyOf(list);
        ENTITY_INDEX.clear();
        for (QuestNpc npc : NPCS.values()) {
            for (NpcEntityRef ref : npc.entities()) ENTITY_INDEX.put(ref.entityUuid(), ref);
        }
    }

    // ── Accès ───────────────────────────────────────────────────────────────

    public static Quest quest(String id) {
        return id == null ? null : QUESTS.get(id);
    }

    public static QuestNpc npc(String id) {
        return id == null ? null : NPCS.get(id);
    }

    /** Quêtes triées par priorité croissante. */
    public static List<Quest> quests() {
        return sorted;
    }

    public static java.util.Collection<QuestNpc> npcs() {
        return NPCS.values();
    }

    public static NpcEntityRef refOf(java.util.UUID entity) {
        if (entity == null) return null;
        NpcEntityRef bound = BOUND.get(entity.toString());
        return bound != null ? bound : ENTITY_INDEX.get(entity.toString());
    }

    /** Les entités de tous les NPC : celles des fichiers (sauf si un script les a re-liées) puis celles liées par script. */
    public static java.util.Collection<NpcEntityRef> entityRefs() {
        java.util.List<NpcEntityRef> out = new ArrayList<>(BOUND.values());
        for (Map.Entry<String, NpcEntityRef> e : ENTITY_INDEX.entrySet()) {
            if (!BOUND.containsKey(e.getKey())) out.add(e.getValue());
        }
        return out;
    }

    /** Lie une entité à un NPC à l'exécution (sans toucher aux fichiers). */
    public static void bindEntity(java.util.UUID entity, NpcEntityRef ref) {
        BOUND.put(entity.toString(), ref);
    }

    public static void unbindEntity(java.util.UUID entity) {
        BOUND.remove(entity.toString());
    }
}
