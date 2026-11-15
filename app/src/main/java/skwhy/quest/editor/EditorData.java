package skwhy.quest.editor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import skwhy.quest.model.NpcEntityRef;
import skwhy.customitem.CustomItemDef;
import skwhy.quest.model.Quest;
import skwhy.quest.model.QuestNpc;
import skwhy.quest.model.ScriptNode;

/**
 * Ce que l'éditeur envoie au navigateur : le catalogue (quêtes et NPC par monde) et la définition d'une quête ou d'un NPC
 * avec les NPC qui la font avancer. Tout est mis en cache : une entrée n'est recalculée que si la quête ou le NPC a été
 * rechargé (l'objet du registre change), ou, pour une quête, si un NPC a changé.
 */
public final class EditorData {

    /** Source des définitions (le registre du module de quêtes en production, des objets fixes dans les tests). */
    public interface Source {
        Collection<Quest> quests();
        Collection<QuestNpc> npcs();
        Quest quest(String id);
        QuestNpc npc(String id);

        /** Items custom connus (vide si le module custom_item n'est pas chargé). */
        default Collection<CustomItemDef> customItems() { return List.of(); }
    }

    private record Cached(Object source, long stamp, String json) {}

    private final Source source;
    private final Map<String, Cached> entities = new ConcurrentHashMap<>();
    private volatile Cached catalog;
    private volatile int computations;

    public EditorData(Source source) {
        this.source = source;
    }

    /** Nombre de calculs réellement faits (les tests vérifient que le cache sert). */
    public int computations() {
        return computations;
    }

    // ── Catalogue ───────────────────────────────────────────────────────────

    public String catalogJson() {
        long stamp = (stamp(source.quests()) * 31 + stamp(source.npcs())) * 31 + stamp(source.customItems()) * 31 + skwhy.quest.QuestSettings.pathfindingRules().hashCode();
        Cached c = catalog;
        if (c != null && c.stamp == stamp) return c.json;
        computations++;
        String json = EditorJson.write(buildCatalog());
        catalog = new Cached(null, stamp, json);
        return json;
    }

    private Map<String, Object> buildCatalog() {
        Map<String, List<Map<String, Object>>> quests = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        Map<String, List<Map<String, Object>>> npcs = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Quest q : source.quests()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", q.id());
            m.put("name", q.name());
            m.put("type", q.type());
            m.put("steps", q.steps().size());
            quests.computeIfAbsent(q.category(), k -> new ArrayList<>()).add(m);
        }
        for (QuestNpc n : source.npcs()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", n.id());
            m.put("name", n.displayName());
            m.put("type", n.type() == null ? "" : n.type());
            npcs.computeIfAbsent(npcWorld(n), k -> new ArrayList<>()).add(m);
        }
        TreeMap<String, Boolean> names = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        quests.keySet().forEach(k -> names.put(k, true));
        npcs.keySet().forEach(k -> names.put(k, true));
        List<Object> worlds = new ArrayList<>();
        for (String world : names.keySet()) {
            Map<String, Object> w = new LinkedHashMap<>();
            w.put("name", world);
            w.put("quests", sorted(quests.getOrDefault(world, List.of())));
            w.put("npcs", sorted(npcs.getOrDefault(world, List.of())));
            worlds.add(w);
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("worlds", worlds);
        List<Object> items = new ArrayList<>();
        for (CustomItemDef def : source.customItems()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", def.id());
            m.put("name", def.tree().get("name") instanceof String text ? text : "");
            m.put("material", def.materialId());
            items.add(m);
        }
        items.sort(Comparator.comparing(o -> String.valueOf(((Map<?, ?>) o).get("id"))));
        root.put("items", items);
        root.put("pathRules", new ArrayList<>(skwhy.quest.QuestSettings.pathfindingRules()));
        return root;
    }

    private static List<Map<String, Object>> sorted(List<Map<String, Object>> list) {
        List<Map<String, Object>> out = new ArrayList<>(list);
        out.sort(Comparator.comparing((Map<String, Object> m) -> plain(String.valueOf(m.get("name"))).toLowerCase(Locale.ROOT))
            .thenComparing(m -> String.valueOf(m.get("id"))));
        return out;
    }

    /** Monde d'un NPC : celui de sa première position (la plus petite), {@code ""} s'il n'en a aucune. */
    public static String npcWorld(QuestNpc npc) {
        NpcEntityRef best = null;
        for (NpcEntityRef r : skwhy.quest.QuestRegistry.places(npc)) {
            if (r.world() == null || r.world().isBlank()) continue;
            if (best == null || r.position() < best.position()) best = r;
        }
        return best == null ? "" : best.world();
    }

    /** Texte sans codes de couleur ({@code &a}, {@code §a}, {@code <red>}, {@code <#ff8800>}). */
    public static String plain(String text) {
        if (text == null) return "";
        return text.replaceAll("[&§][0-9a-fk-orA-FK-OR]", "").replaceAll("</?[a-zA-Z_#:0-9]+(?::[^>]*)?>", "");
    }

    // ── Quêtes et NPC ───────────────────────────────────────────────────────

    /** @return le JSON de la quête ou du NPC, ou {@code null} s'il n'existe pas */
    public String entityJson(String kind, String id) {
        if ("quest".equals(kind)) {
            Quest q = source.quest(id);
            if (q == null) return null;
            return cached("quest:" + id, q, 0, () -> header("quest", q.id(), q.raw()));
        }
        if ("npc".equals(kind)) {
            QuestNpc n = source.npc(id);
            if (n == null) return null;
            return cached("npc:" + id, n, 0, () -> npcJson(n));
        }
        return null;
    }

    /** Version (empreinte) actuelle du fichier, pour le contrôle de version ; {@code null} si absent. */
    public String version(String kind, String id) {
        Map<String, Object> raw = raw(kind, id);
        return raw == null ? null : EditorJson.version(raw);
    }

    public Map<String, Object> raw(String kind, String id) {
        if ("quest".equals(kind)) {
            Quest q = source.quest(id);
            return q == null ? null : q.raw();
        }
        if ("npc".equals(kind)) {
            QuestNpc n = source.npc(id);
            return n == null ? null : n.raw();
        }
        return null;
    }

    private String cached(String key, Object object, long stamp, Supplier<Map<String, Object>> build) {
        Cached c = entities.get(key);
        if (c != null && c.source == object && c.stamp == stamp) return c.json;
        computations++;
        String json = EditorJson.write(build.get());
        entities.put(key, new Cached(object, stamp, json));
        return json;
    }

    /**
     * Les NPC qui font avancer une quête : analyse de tous les scripts de NPC, donc demandée à part (le navigateur affiche la quête
     * d'abord). Recalculée seulement si la quête ou un NPC a été rechargé.
     *
     * @return {@code null} si la quête n'existe pas
     */
    public String unlockersJson(String id) {
        Quest quest = source.quest(id);
        if (quest == null) return null;
        return cached("unlockers:" + id, quest, stamp(source.npcs()), () -> {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("id", id);
            out.put("unlockers", unlockers(quest));
            return out;
        });
    }

    private List<Object> unlockers(Quest quest) {
        List<Object> unlockers = new ArrayList<>();
        for (UnlockAnalyzer.Unlocker u : UnlockAnalyzer.unlockers(quest, source.npcs())) {
            QuestNpc npc = source.npc(u.npcId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("npc", u.npcId());
            m.put("npcName", npc == null ? u.npcId() : npc.displayName());
            m.put("node", u.node());
            m.put("mode", u.mode());
            m.put("toStep", u.toStep());
            m.put("substep", u.substep());
            m.put("fromSteps", u.fromSteps());
            unlockers.add(m);
        }
        return unlockers;
    }

    private Map<String, Object> npcJson(QuestNpc npc) {
        Map<String, Object> out = header("npc", npc.id(), npc.raw());
        out.put("world", npcWorld(npc));
        List<Object> drives = new ArrayList<>();
        for (ScriptNode node : npc.script().all().values()) {
            if (!"quest".equals(node.action()) || node.str("quest") == null) continue;
            Quest q = source.quest(node.str("quest"));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("quest", node.str("quest"));
            m.put("questName", q == null ? node.str("quest") : q.name());
            m.put("node", node.id());
            m.put("mode", node.str("mode", ""));
            m.put("step", node.integer("step", -1));
            m.put("substep", node.integer("substep", -1));
            drives.add(m);
        }
        out.put("drives", drives);
        return out;
    }

    private static Map<String, Object> header(String kind, String id, Map<String, Object> raw) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("kind", kind);
        out.put("id", id);
        out.put("version", EditorJson.version(raw));
        out.put("raw", raw);
        return out;
    }

    /** Empreinte d'un ensemble d'objets du registre : change dès qu'un objet est remplacé, ajouté ou retiré. */
    private static long stamp(Collection<?> objects) {
        long h = objects.size();
        for (Object o : objects) h += System.identityHashCode(o) * 2654435761L;
        return h;
    }

    public void clear() {
        entities.clear();
        catalog = null;
    }
}
