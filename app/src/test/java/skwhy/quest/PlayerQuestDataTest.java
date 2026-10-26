package skwhy.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import skwhy.quest.data.PlayerQuestData;
import skwhy.quest.data.QuestProgress;
import skwhy.quest.model.QuestStatus;

class PlayerQuestDataTest {

    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void progressCountsNumbersAndFinishedObjectives() {
        QuestProgress p = new QuestProgress();
        assertEquals(QuestStatus.AVAILABLE, p.status);
        assertEquals(-1, p.step);
        assertEquals(0, p.count(0, 10));
        assertFalse(p.isDone(0));
        p.progress.put(0, 4);
        assertEquals(4, p.count(0, 10));
        p.progress.put(0, Boolean.TRUE);
        assertEquals(10, p.count(0, 10));
        assertTrue(p.isDone(0));
        p.progress.put(1, Boolean.FALSE);
        assertEquals(0, p.count(1, 10));
        assertFalse(p.isDone(1));
        p.progress.put(2, "texte");
        assertEquals(0, p.count(2, 10));
    }

    @Test
    void newPlayerDataIsEmptyAndClean() {
        PlayerQuestData d = new PlayerQuestData(ID);
        assertEquals(ID, d.uuid);
        assertEquals(QuestStatus.UNKNOWN, d.status("rien"));
        assertEquals(1, d.npcPosition("pnj"));
        assertFalse(d.isDirty());
        d.markDirty();
        assertTrue(d.isDirty());
        assertEquals("all", d.filterState);
        assertEquals("all", d.filterType);
        assertFalse(d.compass);
    }

    @Test
    void emptyTreeGivesDefaults() {
        PlayerQuestData d = PlayerQuestData.fromTree(ID, new LinkedHashMap<>());
        assertNull(d.selected);
        assertTrue(d.quests.isEmpty());
        assertEquals("all", d.filterState);
        assertFalse(d.compass);
    }

    @Test
    void serializedTreeCarriesTheVersion() {
        Map<String, Object> tree = new PlayerQuestData(ID).toTree();
        assertEquals(1, tree.get("version"));
        assertFalse(tree.containsKey("selected"));
    }

    @Test
    void unknownStatusFallsBackToAvailable() {
        Map<String, Object> quest = new LinkedHashMap<>();
        quest.put("status", "n'importe quoi");
        quest.put("step", 2);
        Map<String, Object> quests = new LinkedHashMap<>();
        quests.put("q", quest);
        Map<String, Object> tree = new LinkedHashMap<>();
        tree.put("quests", quests);
        PlayerQuestData d = PlayerQuestData.fromTree(ID, tree);
        assertEquals(QuestStatus.AVAILABLE, d.status("q"));
        assertEquals(2, d.quests.get("q").step);
        assertEquals(-1, PlayerQuestData.fromTree(ID, Map.of("quests", Map.of("r", Map.of()))).quests.get("r").step);
    }

    @Test
    void malformedProgressKeysAreIgnoredAndNumbersBecomeInts() {
        Map<String, Object> progress = new LinkedHashMap<>();
        progress.put("0", 3L);
        progress.put("1", Boolean.TRUE);
        progress.put("abc", 5);
        Map<String, Object> quest = new LinkedHashMap<>();
        quest.put("progress", progress);
        PlayerQuestData d = PlayerQuestData.fromTree(ID, Map.of("quests", Map.of("q", quest)));
        QuestProgress p = d.quests.get("q");
        assertEquals(2, p.progress.size());
        assertEquals(3, p.progress.get(0));
        assertEquals(Boolean.TRUE, p.progress.get(1));
    }

    @Test
    void everythingRoundTripsThroughATree() {
        PlayerQuestData d = new PlayerQuestData(ID);
        d.selected = "mine";
        d.categories.addAll(List.of("principale", "annexe"));
        d.metNpcs.add("forgeron");
        d.knownNames.add("Forgeron");
        d.npcPositions.put("forgeron", 3);
        d.dialogues.put("forgeron", "salut");
        d.compass = true;
        d.filterState = "done";
        d.filterType = "kill";
        QuestProgress p = new QuestProgress();
        p.status = QuestStatus.STARTED;
        p.step = 1;
        p.progress.put(0, 7);
        p.progress.put(1, Boolean.TRUE);
        p.variables.put("or", 12);
        p.rewardClaimed = true;
        d.quests.put("mine", p);

        PlayerQuestData back = PlayerQuestData.fromTree(ID, d.toTree());
        assertEquals("mine", back.selected);
        assertEquals(List.of("principale", "annexe"), back.categories);
        assertEquals(3, back.npcPosition("forgeron"));
        assertEquals("salut", back.dialogues.get("forgeron"));
        assertTrue(back.compass);
        assertEquals("done", back.filterState);
        assertEquals("kill", back.filterType);
        QuestProgress q = back.quests.get("mine");
        assertEquals(QuestStatus.STARTED, q.status);
        assertEquals(1, q.step);
        assertEquals(7, q.progress.get(0));
        assertTrue(q.isDone(1));
        assertEquals(12, q.variables.get("or"));
        assertTrue(q.rewardClaimed);
        assertEquals(d.toTree(), back.toTree());
    }
}
