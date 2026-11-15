package skwhy.quest.editor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** Les modifications envoyées par le navigateur en une fois (plusieurs quêtes et NPC possibles), en attente de la réponse du joueur. */
public final class ChangeSet {

    /** Une quête ou un NPC modifié. {@code base} est la version du fichier quand l'éditeur l'a ouvert. */
    public record Entry(String kind, String id, String base, Map<String, Object> tree, TreeDiff.Stat stat, List<String> sections) {
        public boolean isNpc() { return "npc".equals(kind); }
    }

    final String id;
    final UUID player;
    final String sessionId;
    final String targetKind;
    final String targetId;
    final long createdAt;
    final List<Entry> entries;
    /** Passe à {@code true} à la première réponse (valider / annuler) : les autres clics sont ignorés. */
    final AtomicBoolean answered = new AtomicBoolean();

    public ChangeSet(String id, UUID player, String sessionId, String targetKind, String targetId, long createdAt, List<Entry> entries) {
        this.id = id;
        this.player = player;
        this.sessionId = sessionId;
        this.targetKind = targetKind;
        this.targetId = targetId;
        this.createdAt = createdAt;
        this.entries = List.copyOf(entries);
    }

    public String id() { return id; }
    public UUID player() { return player; }
    public String targetKind() { return targetKind; }
    public String targetId() { return targetId; }
    public List<Entry> entries() { return entries; }

    public TreeDiff.Stat total() {
        TreeDiff.Stat t = new TreeDiff.Stat(0, 0, 0);
        for (Entry e : entries) t = t.plus(e.stat());
        return t;
    }

    public long count(boolean npc) {
        return entries.stream().filter(e -> e.isNpc() == npc).count();
    }

    // ── Fichier d'attente (joueur absent) ───────────────────────────────────

    public String toJson() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("id", id);
        root.put("player", player.toString());
        root.put("session", sessionId);
        root.put("target_kind", targetKind);
        root.put("target_id", targetId);
        root.put("created_at", createdAt);
        List<Object> list = new ArrayList<>();
        for (Entry e : entries) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("kind", e.kind());
            m.put("id", e.id());
            m.put("base", e.base());
            m.put("tree", e.tree());
            m.put("added", e.stat().added());
            m.put("removed", e.stat().removed());
            m.put("modified", e.stat().modified());
            m.put("sections", e.sections());
            list.add(m);
        }
        root.put("entries", list);
        return EditorJson.write(root);
    }

    @SuppressWarnings("unchecked")
    public static ChangeSet fromJson(String json) {
        Map<String, Object> root = (Map<String, Object>) EditorJson.parse(json);
        List<Entry> entries = new ArrayList<>();
        for (Object o : (List<Object>) root.get("entries")) {
            Map<String, Object> m = (Map<String, Object>) o;
            List<String> sections = new ArrayList<>();
            if (m.get("sections") instanceof List<?> l) for (Object s : l) sections.add(String.valueOf(s));
            entries.add(new Entry(String.valueOf(m.get("kind")), String.valueOf(m.get("id")), String.valueOf(m.get("base")),
                (Map<String, Object>) m.get("tree"),
                new TreeDiff.Stat(num(m.get("added")), num(m.get("removed")), num(m.get("modified"))), sections));
        }
        return new ChangeSet(String.valueOf(root.get("id")), UUID.fromString(String.valueOf(root.get("player"))),
            String.valueOf(root.get("session")), String.valueOf(root.get("target_kind")), String.valueOf(root.get("target_id")),
            ((Number) root.get("created_at")).longValue(), entries);
    }

    private static int num(Object o) {
        return o instanceof Number n ? n.intValue() : 0;
    }
}
