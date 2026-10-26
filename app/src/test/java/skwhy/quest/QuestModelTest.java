package skwhy.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import skwhy.quest.data.PlayerQuestData;
import skwhy.quest.data.QuestProgress;
import skwhy.quest.model.ObjectiveType;
import skwhy.quest.model.Quest;
import skwhy.quest.model.QuestDestination;
import skwhy.quest.model.QuestIndicator;
import skwhy.quest.model.QuestNpc;
import skwhy.quest.model.QuestObjective;
import skwhy.quest.model.QuestStatus;
import skwhy.quest.model.QuestStep;
import skwhy.quest.model.ScriptNode;

class QuestModelTest {

    private static Map<String, Object> questTree() {
        Map<String, Object> objective1 = new LinkedHashMap<>();
        objective1.put("type", "action");
        objective1.put("action", "break.stone");
        objective1.put("count", 5);
        objective1.put("text", "Casse 5 pierres");
        objective1.put("destinations", List.of(Map.of("npc", "maire"), Map.of("type", "farm", "world", "villes", "x", 10.0, "z", 20.0, "radius", 30.0)));
        Map<String, Object> objective2 = new LinkedHashMap<>();
        objective2.put("type", "special");
        objective2.put("action", "jobs");
        objective2.put("jobs", "chasseur");
        Map<String, Object> step0 = new LinkedHashMap<>();
        step0.put("text", "Première étape");
        step0.put("next", 2);
        step0.put("objectives", List.of(objective1, objective2));
        Map<String, Object> step1 = new LinkedHashMap<>();
        step1.put("text", "Seconde");
        step1.put("condition", "gate");
        Map<String, Object> script = new LinkedHashMap<>();
        script.put("gate", Map.of("condition", "compare", "operator", "equal"));
        script.put("reward", Map.of("action", "give"));
        Map<String, Object> tree = new LinkedHashMap<>();
        tree.put("name", "&6Chasse");
        tree.put("type", "secondaire");
        tree.put("catégorie", "quete_zelda");
        tree.put("priority", 12);
        tree.put("variables", List.of("kills", "timer"));
        tree.put("steps", List.of(step0, step1));
        tree.put("script", script);
        return tree;
    }

    @Test
    void questIsParsedFromATree() {
        Quest quest = Quest.parse("chasse", questTree());
        assertEquals("chasse", quest.id());
        assertEquals("&6Chasse", quest.name());
        assertEquals("secondaire", quest.type());
        assertEquals("quete_zelda", quest.category());
        assertEquals(12, quest.priority());
        assertTrue(quest.hasVariable("kills"));
        assertFalse(quest.hasVariable("other"));
        assertEquals(2, quest.steps().size());
        assertNull(quest.step(2));
        assertNull(quest.step(-1));
        assertTrue(quest.script().has("reward"));
    }

    @Test
    void stepsDefaultToTheFollowingStep() {
        Quest quest = Quest.parse("chasse", questTree());
        assertEquals(2, quest.step(0).next());
        assertEquals(2, quest.step(1).next());
        assertEquals("gate", quest.step(1).condition());
        assertTrue(quest.step(1).objectives().isEmpty());
    }

    @Test
    void objectivesKnowTheirTypeAndOwner() {
        Quest quest = Quest.parse("chasse", questTree());
        QuestStep step = quest.step(0);
        QuestObjective action = step.objectives().get(0);
        assertEquals(ObjectiveType.ACTION, action.type());
        assertEquals("break.stone", action.key());
        assertEquals(5, action.count());
        assertEquals(0, action.step());
        assertEquals(quest, action.quest());
        QuestObjective special = step.objectives().get(1);
        assertEquals(ObjectiveType.SPECIAL, special.type());
        assertEquals("jobs", special.special());
        assertEquals(1, special.count());
    }

    @Test
    void destinationsAreNpcOrFarmOrPoint() {
        Quest quest = Quest.parse("chasse", questTree());
        List<QuestDestination> destinations = quest.step(0).objectives().get(0).destinations();
        assertEquals(QuestDestination.Kind.NPC, destinations.get(0).kind());
        assertEquals("maire", destinations.get(0).npc());
        assertEquals(QuestDestination.Kind.FARM, destinations.get(1).kind());
        assertEquals(30.0, destinations.get(1).radius());
        QuestDestination point = QuestDestination.parse(Map.of("type", "objective", "world", "w", "x", 1.0, "y", 2.0, "z", 3.0));
        assertEquals(QuestDestination.Kind.POINT, point.kind());
        assertEquals(2.0, point.y());
    }

    @Test
    void scriptNodesTellActionsFromConditions() {
        Quest quest = Quest.parse("chasse", questTree());
        ScriptNode gate = quest.script().get("gate");
        assertTrue(gate.isCondition());
        assertEquals("compare", gate.conditionType());
        ScriptNode reward = quest.script().get("reward");
        assertFalse(reward.isCondition());
        assertEquals("give", reward.action());
        assertNull(reward.guard());
        ScriptNode guarded = new ScriptNode("a", Map.of("action", "give", "condition", "gate"));
        assertEquals("gate", guarded.guard());
        assertEquals("list", new ScriptNode("b", Map.of()).action());
    }

    @Test
    void npcKeepsItsEntitiesByPosition() {
        Map<String, Object> entity1 = Map.of("position", 1, "location", Map.of("world", "villes", "x", 1.0, "y", 64.0, "z", 2.0));
        Map<String, Object> entity2 = Map.of("position", 2, "location", Map.of("world", "villes", "x", 50.0, "y", 70.0, "z", 60.0));
        Map<String, Object> tree = new LinkedHashMap<>();
        tree.put("name", "Perceval");
        tree.put("type", "scripté");
        tree.put("name_visible", (byte) 0);
        tree.put("rencontre", "Enchanté");
        tree.put("entity", Map.of("aaaa", entity1, "bbbb", entity2));
        QuestNpc npc = QuestNpc.parse("maire", tree);
        assertEquals("Perceval", npc.displayName());
        assertFalse(npc.nameVisible());
        assertEquals("Enchanté", npc.meetMessage());
        assertEquals(List.of(1, 2), npc.positions());
        assertEquals("aaaa", npc.entityAt(1).entityUuid());
        assertEquals(60.0, npc.entityAt(2).z());
        assertNull(npc.entityAt(3));
        assertEquals("maire", QuestNpc.parse("maire", null).displayName());
    }

    @Test
    void statusAndIndicatorAcceptTheirLegacyNames() {
        assertEquals(QuestStatus.AVAILABLE, QuestStatus.parse("disponible"));
        assertEquals(QuestStatus.STARTED, QuestStatus.parse("commencée"));
        assertEquals(QuestStatus.FINISHED, QuestStatus.parse("terminée"));
        assertEquals(QuestStatus.FINISHED, QuestStatus.parse("Finished"));
        assertNull(QuestStatus.parse("???"));
        assertEquals(QuestIndicator.MAIN, QuestIndicator.fromLegacy(1));
        assertEquals(QuestIndicator.NONE, QuestIndicator.fromLegacy(-1));
        assertEquals(QuestIndicator.REGION, QuestIndicator.parse("contrée"));
    }

    @Test
    void playerDataRoundTripsThroughATree() {
        PlayerQuestData data = new PlayerQuestData(java.util.UUID.randomUUID());
        data.selected = "chasse";
        data.categories.add("villes");
        data.metNpcs.add("maire");
        data.knownNames.add("maire");
        data.npcPositions.put("maire", 3);
        data.dialogues.put("action1", "suite");
        data.compass = true;
        data.filterState = "start";
        QuestProgress progress = new QuestProgress();
        progress.status = QuestStatus.STARTED;
        progress.step = 2;
        progress.progress.put(0, Boolean.TRUE);
        progress.progress.put(1, 4);
        progress.variables.put("kills", 7);
        progress.variables.put("note", "ok");
        progress.rewardClaimed = true;
        data.quests.put("chasse", progress);

        PlayerQuestData copy = PlayerQuestData.fromTree(data.uuid, data.toTree());
        assertEquals("chasse", copy.selected);
        assertEquals(List.of("villes"), copy.categories);
        assertTrue(copy.metNpcs.contains("maire"));
        assertTrue(copy.knownNames.contains("maire"));
        assertEquals(3, copy.npcPosition("maire"));
        assertEquals(1, copy.npcPosition("inconnu"));
        assertEquals("suite", copy.dialogues.get("action1"));
        assertTrue(copy.compass);
        assertEquals("start", copy.filterState);
        QuestProgress p = copy.quests.get("chasse");
        assertNotNull(p);
        assertEquals(QuestStatus.STARTED, p.status);
        assertEquals(2, p.step);
        assertTrue(p.isDone(0));
        assertEquals(4, p.count(1, 10));
        assertEquals(10, p.count(0, 10));
        assertEquals(7, p.variables.get("kills"));
        assertEquals("ok", p.variables.get("note"));
        assertTrue(p.rewardClaimed);
        assertEquals(QuestStatus.UNKNOWN, copy.status("autre"));
    }
}
