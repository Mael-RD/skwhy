package skwhy.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
