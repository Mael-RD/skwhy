package skwhy.quest.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

/** JSON, comptage des différences, clefs (expiration, invalidation) et fichier d'attente : logique de l'éditeur sans serveur. */
class EditorSupportTest {

    @SuppressWarnings("unchecked")
    static Map<String, Object> tree(String json) {
        return (Map<String, Object>) EditorJson.parse(json);
    }

    // ── JSON ────────────────────────────────────────────────────────────────

    @Test
    void integersStayIntegersAndOrderIsKept() {
        Map<String, Object> t = tree("{\"b\":1,\"a\":2.5,\"c\":[1,2,{\"x\":true}],\"d\":\"é\",\"big\":12345678901}");
        assertEquals(List.of("b", "a", "c", "d", "big"), new ArrayList<>(t.keySet()));
        assertEquals(Integer.valueOf(1), t.get("b"));
        assertEquals(Double.valueOf(2.5), t.get("a"));
        assertEquals(Long.valueOf(12345678901L), t.get("big"));
        assertEquals("{\"b\":1,\"a\":2.5,\"c\":[1,2,{\"x\":true}],\"d\":\"é\",\"big\":12345678901}", EditorJson.write(t));
    }

    @Test
    void bytesAreWrittenAsNumbers() {
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("flag", (byte) 1);
        t.put("name", "a<b>&c");
        assertEquals("{\"flag\":1,\"name\":\"a<b>&c\"}", EditorJson.write(t));
    }

    @Test
    void versionChangesWithTheContentOnly() {
        Map<String, Object> a = tree("{\"x\":1,\"y\":[1,2]}");
        Map<String, Object> same = tree("{\"x\":1,\"y\":[1,2]}");
        Map<String, Object> other = tree("{\"x\":1,\"y\":[1,3]}");
        assertEquals(EditorJson.version(a), EditorJson.version(same));
        assertNotEquals(EditorJson.version(a), EditorJson.version(other));
        assertEquals(16, EditorJson.version(a).length());
    }

    // ── Différences ─────────────────────────────────────────────────────────

    @Test
    void diffCountsAddedRemovedAndModifiedValues() {
        Map<String, Object> before = tree("{\"name\":\"a\",\"n\":1,\"steps\":[{\"t\":\"x\"},{\"t\":\"y\"}],\"gone\":{\"p\":1,\"q\":2}}");
        Map<String, Object> after = tree("{\"name\":\"b\",\"n\":1,\"steps\":[{\"t\":\"x\"},{\"t\":\"y\"},{\"t\":\"z\",\"u\":1}],\"new\":true}");
        TreeDiff.Stat s = TreeDiff.diff(before, after);
        assertEquals(1, s.modified(), "name");
        assertEquals(2, s.removed(), "gone.p et gone.q");
        assertEquals(3, s.added(), "steps[2].t, steps[2].u, new");
        assertEquals(6, s.total());
        assertEquals(List.of("gone", "name", "new", "steps"), TreeDiff.changedSections(before, after));
    }

    @Test
    void identicalTreesHaveNoDifference() {
        Map<String, Object> t = tree("{\"a\":[1,{\"b\":2}]}");
        assertEquals(0, TreeDiff.diff(t, tree("{\"a\":[1,{\"b\":2}]}")).total());
        assertTrue(TreeDiff.changedSections(t, tree("{\"a\":[1,{\"b\":2}]}")).isEmpty());
    }

    @Test
    void anEmptyAddedContainerCountsOnce() {
        assertEquals(1, TreeDiff.diff(tree("{}"), tree("{\"a\":{}}")).added());
        assertEquals(1, TreeDiff.diff(tree("{\"a\":[]}"), tree("{}")).removed());
    }

    @Test
    void aNumberChangeIsOneModification() {
        assertEquals(new TreeDiff.Stat(0, 0, 1), TreeDiff.diff(tree("{\"x\":1}"), tree("{\"x\":2}")));
        assertEquals(new TreeDiff.Stat(0, 0, 0), TreeDiff.diff(tree("{\"x\":1}"), tree("{\"x\":1}")));
    }

    // ── Clefs ───────────────────────────────────────────────────────────────

    @Test
    void keysExpireAfterTheirDelay() {
        AtomicLong now = new AtomicLong(1_000);
        EditorSessions sessions = new EditorSessions(now::get);
        EditorSessions.Issued issued = sessions.issue("quest", "q", UUID.randomUUID(), "Joueur", 7_200_000L);
        assertNotNull(sessions.find(issued.key()));
        now.addAndGet(7_199_999L);
        assertNotNull(sessions.find(issued.key()), "encore valide 1 ms avant la fin");
        now.addAndGet(1L);
        assertNull(sessions.find(issued.key()), "expirée au bout de 2 h");
        assertEquals(0, sessions.size(), "et oubliée");
    }

    @Test
    void invalidKeysAllGiveNull() {
        EditorSessions sessions = new EditorSessions();
        sessions.issue("quest", "q", UUID.randomUUID(), "J", 60_000L);
        for (String key : new String[] {null, "", "abc", "x".repeat(43), "x".repeat(500), "../../etc/passwd", "a b c d e f g h i j k l"}) {
            assertNull(sessions.find(key), String.valueOf(key));
        }
    }

    @Test
    void keysAreUniqueLongAndNotStoredInClear() {
        EditorSessions sessions = new EditorSessions();
        UUID player = UUID.randomUUID();
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < 200; i++) keys.add(sessions.issue("npc", "n", player, "J", 60_000L).key());
        assertEquals(200, keys.stream().distinct().count());
        for (String k : keys) {
            assertTrue(k.length() >= 43, k);
            assertTrue(k.matches("[A-Za-z0-9_-]+"), k);
        }
        for (EditorSession s : sessions.all()) assertFalse(keys.contains(s.keyHash), "seule l'empreinte est gardée");
    }

    @Test
    void invalidateAndRenewal() {
        AtomicLong now = new AtomicLong();
        EditorSessions sessions = new EditorSessions(now::get);
        UUID player = UUID.randomUUID();
        EditorSessions.Issued first = sessions.issue("quest", "q", player, "J", 7_200_000L);
        EditorSession s = first.session();
        assertEquals(EditorSession.State.OPEN, s.state());
        // envoyée puis validée : la clef ne reste valable que le délai de renouvellement
        s.state = EditorSession.State.APPLIED;
        s.expiresAt = now.get() + 300_000L;
        assertNotNull(sessions.find(first.key()));
        EditorSessions.Issued renewed = sessions.issue(s.kind(), s.targetId(), s.player(), "J", 7_200_000L);
        s.renewedKey = renewed.key();
        assertNotEquals(first.key(), renewed.key());
        assertEquals(renewed.key(), sessions.find(first.key()).renewedKey(), "l'ancienne page apprend la nouvelle clef");
        now.addAndGet(300_000L);
        assertNull(sessions.find(first.key()), "5 minutes après : fermée");
        assertNotNull(sessions.find(renewed.key()), "la nouvelle reste valable 2 h");
        sessions.invalidate(sessions.find(renewed.key()));
        assertNull(sessions.find(renewed.key()), "« ne pas renouveler » ferme tout de suite");
    }

    @Test
    void findByIdAndPurge() {
        AtomicLong now = new AtomicLong();
        EditorSessions sessions = new EditorSessions(now::get);
        EditorSession a = sessions.issue("quest", "a", UUID.randomUUID(), "J", 1_000L).session();
        EditorSession b = sessions.issue("quest", "b", UUID.randomUUID(), "J", 5_000L).session();
        assertEquals(a, sessions.findById(a.id()));
        now.set(2_000L);
        assertNull(sessions.findById(a.id()));
        sessions.purge();
        assertEquals(1, sessions.size());
        assertEquals(b, sessions.findById(b.id()));
    }

    // ── Fichier d'attente ───────────────────────────────────────────────────

    @Test
    void aChangeSetSurvivesTheWaitingFile() {
        Map<String, Object> t = tree("{\"name\":\"N\",\"steps\":[{\"text\":\"é\\\"x\"}],\"p\":3}");
        ChangeSet.Entry e = new ChangeSet.Entry("quest", "q1", "abcd", t, new TreeDiff.Stat(1, 2, 3), List.of("steps", "p"));
        ChangeSet.Entry n = new ChangeSet.Entry("npc", "n1", "ef01", tree("{\"name\":\"M\"}"), new TreeDiff.Stat(0, 0, 1), List.of("name"));
        UUID player = UUID.randomUUID();
        ChangeSet cs = new ChangeSet("id-1", player, "sess", "quest", "q1", 42L, List.of(e, n));

        ChangeSet back = ChangeSet.fromJson(cs.toJson());
        assertEquals("id-1", back.id());
        assertEquals(player, back.player());
        assertEquals("quest", back.targetKind());
        assertEquals("q1", back.targetId());
        assertEquals(2, back.entries().size());
        assertEquals(t, back.entries().get(0).tree());
        assertEquals("abcd", back.entries().get(0).base());
        assertEquals(new TreeDiff.Stat(1, 2, 3), back.entries().get(0).stat());
        assertEquals(List.of("steps", "p"), back.entries().get(0).sections());
        assertTrue(back.entries().get(1).isNpc());
        assertEquals(new TreeDiff.Stat(1, 2, 4), back.total());
        assertEquals(1, back.count(false));
        assertEquals(1, back.count(true));
    }

    @Test
    void onlyTheFirstAnswerCounts() {
        ChangeSet cs = new ChangeSet("i", UUID.randomUUID(), "s", "quest", "q", 0L, List.of());
        assertTrue(cs.answered.compareAndSet(false, true));
        assertFalse(cs.answered.compareAndSet(false, true));
    }
}
