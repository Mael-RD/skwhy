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
import skwhy.quest.npc.CustomNpcBodies;
import skwhy.quest.npc.NpcPlaces;
import skwhy.quest.npc.NpcService;
import skwhy.quest.npc.NpcTag;

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
        if (QuestSettings.legacyImportEnabled() && QuestSettings.legacyMigrate()) {
            migrateLegacy(new File(QuestSettings.legacyFolder(), "quests"), QuestSettings.questFolder());
            migrateLegacy(new File(QuestSettings.legacyFolder(), "npcs"), QuestSettings.npcFolder());
        }
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

    /**
     * Un seul format : chaque ancien fichier {@code .nbt} qui n'a pas encore de YAML est converti en YAML (le YAML d'un même identifiant
     * prime toujours et laisse son {@code .nbt} intact), puis le {@code .nbt} est renommé en {@code .nbt.migrated} (jamais supprimé).
     */
    private static void migrateLegacy(File legacy, File target) {
        File[] files = legacy.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".nbt"));
        if (files == null) return;
        int done = 0;
        for (File f : files) {
            String id = f.getName().substring(0, f.getName().length() - ".nbt".length());
            if (new File(target, id + ".yml").exists()) continue;
            Map<String, Object> tree = NbtTree.read(f);
            if (tree == null || !YamlTree.write(new File(target, id + ".yml"), tree)) {
                SkWhy.getInstance().getLogger().warning("[Quest] Migration impossible : " + f.getPath());
                continue;
            }
            File moved = new File(f.getParentFile(), f.getName() + ".migrated");
            if (!f.renameTo(moved)) SkWhy.getInstance().getLogger().warning("[Quest] YAML écrit mais " + f.getName() + " n'a pas pu être renommé.");
            done++;
        }
        if (done > 0) SkWhy.getInstance().getLogger().info("[Quest] " + done + " ancien(s) fichier(s) NBT de " + legacy.getName() + " converti(s) en YAML dans " + target.getName() + " (anciens fichiers renommés en .nbt.migrated).");
    }

    private static void loadQuestFiles() {
        if (QuestSettings.legacyImportEnabled()) {
            for (Map.Entry<String, Map<String, Object>> e : readFolder(new File(QuestSettings.legacyFolder(), "quests")).entrySet()) {
                QUESTS.put(e.getKey(), Quest.parse(e.getKey(), e.getValue()));
            }
        }
        for (Map.Entry<String, Map<String, Object>> e : readFolder(QuestSettings.questFolder()).entrySet()) {
            QUESTS.put(e.getKey(), Quest.parse(e.getKey(), e.getValue()));
        }
    }

    private static void loadNpcFiles() {
        if (QuestSettings.legacyImportEnabled()) {
            for (Map.Entry<String, Map<String, Object>> e : readFolder(new File(QuestSettings.legacyFolder(), "npcs")).entrySet()) {
                NPCS.put(e.getKey(), QuestNpc.parse(e.getKey(), e.getValue()));
            }
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

    /**
     * Le NPC que représente cette entité : son tag NBT ({@link NpcTag}) d'abord, puis une liaison en mémoire, puis la clé
     * {@code entity} des fichiers de NPC (ancien format, par UUID).
     */
    public static NpcEntityRef refOf(org.bukkit.entity.Entity entity) {
        if (entity == null) return null;
        NpcEntityRef tagged = NpcTag.read(entity);
        if (tagged != null) {
            BOUND.put(entity.getUniqueId().toString(), tagged);
            return tagged;
        }
        return refOf(entity.getUniqueId());
    }

    /** Comme {@link #refOf(org.bukkit.entity.Entity)} pour un UUID : liaisons déjà vues (entités chargées, entités custom fausses) et fichiers. */
    public static NpcEntityRef refOf(java.util.UUID entity) {
        if (entity == null) return null;
        NpcEntityRef bound = BOUND.get(entity.toString());
        return bound != null ? bound : ENTITY_INDEX.get(entity.toString());
    }

    /** Les entités de tous les NPC : celles des fichiers (sauf si elles ont été re-liées) puis les entités liées (tag, script, entités custom). */
    public static java.util.Collection<NpcEntityRef> entityRefs() {
        CustomNpcBodies.purge();
        java.util.List<NpcEntityRef> out = new ArrayList<>(BOUND.values());
        for (Map.Entry<String, NpcEntityRef> e : ENTITY_INDEX.entrySet()) {
            if (!BOUND.containsKey(e.getKey())) out.add(e.getValue());
        }
        return out;
    }

    /** Retient une liaison en mémoire (entité custom fausse, ou entité réelle qui vient de se charger). */
    public static void bindEntity(java.util.UUID entity, NpcEntityRef ref) {
        BOUND.put(entity.toString(), ref);
    }

    public static void unbindEntity(java.util.UUID entity) {
        BOUND.remove(entity.toString());
    }

    // ── Liaison d'entités réelles (tag NBT) et positions enregistrées ───────

    /**
     * Lie une entité réelle à un NPC à une position : écrit le tag dans son NBT et enregistre l'endroit où elle se tient comme position
     * de ce NPC (fichier {@code quest-npc-positions.yml}).
     */
    public static NpcEntityRef bind(org.bukkit.entity.Entity entity, String npcId, int position) {
        NpcTag.write(entity, npcId, position);
        NpcEntityRef ref = NpcTag.read(entity);
        BOUND.put(entity.getUniqueId().toString(), ref);
        NpcPlaces.set(npcId, position, NpcPlaces.Place.of(entity.getLocation()));
        NpcService.refreshAll();
        return ref;
    }

    /** Retire le lien d'une entité réelle ; @return {@code true} si elle était liée par tag ou en mémoire */
    public static boolean unbind(org.bukkit.entity.Entity entity) {
        boolean had = NpcTag.clear(entity);
        had |= BOUND.remove(entity.getUniqueId().toString()) != null;
        NpcService.release(entity);
        return had;
    }

    /** Une entité chargée vient d'apparaître : si elle porte un tag, elle rejoint les liaisons connues. */
    public static NpcEntityRef onLoad(org.bukkit.entity.Entity entity) {
        NpcEntityRef tagged = NpcTag.read(entity);
        if (tagged != null) BOUND.put(entity.getUniqueId().toString(), tagged);
        return tagged;
    }

    /** Une entité réelle quitte le monde (déchargement) : on l'oublie, son tag la retrouvera à son retour. */
    public static void onUnload(org.bukkit.entity.Entity entity) {
        BOUND.remove(entity.getUniqueId().toString());
    }

    /** L'entité réelle chargée qui représente ce NPC à cette position (tag), ou {@code null}. */
    public static NpcEntityRef loaded(String npcId, int position) {
        for (NpcEntityRef ref : BOUND.values()) {
            if (ref.npcId().equals(npcId) && ref.position() == position && !CustomNpcBodies.isFake(ref)) return ref;
        }
        return null;
    }

    /**
     * Où se tient ce NPC à cette position : l'endroit enregistré ({@link NpcPlaces}) s'il y en a un, sinon celui du fichier du NPC
     * (ancien format). L'UUID est celui d'une entité de cette position si l'une est chargée.
     */
    public static NpcEntityRef entityAt(QuestNpc npc, int position) {
        NpcEntityRef legacy = npc.entityAt(position);
        NpcPlaces.Place place = NpcPlaces.get(npc.id(), position);
        if (place == null) return legacy;
        NpcEntityRef loaded = loaded(npc.id(), position);
        String uuid = loaded != null ? loaded.entityUuid() : legacy != null ? legacy.entityUuid() : "";
        return new NpcEntityRef(npc.id(), uuid, position, place.world(), place.x(), place.y(), place.z());
    }

    /** Toutes les positions d'un NPC (fichier + enregistrées), chacune avec son endroit, croissantes. */
    public static List<NpcEntityRef> places(QuestNpc npc) {
        java.util.TreeSet<Integer> positions = new java.util.TreeSet<>(npc.positions());
        positions.addAll(NpcPlaces.positions(npc.id()));
        List<NpcEntityRef> out = new ArrayList<>();
        for (int position : positions) {
            NpcEntityRef ref = entityAt(npc, position);
            if (ref != null) out.add(ref);
        }
        return out;
    }
}
