package skwhy.quest.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import skwhy.quest.editor.UnlockAnalyzer.Assumption;
import skwhy.quest.editor.UnlockAnalyzer.Tri;
import skwhy.quest.editor.UnlockAnalyzer.Unlocker;
import skwhy.quest.model.Quest;
import skwhy.quest.model.QuestNpc;

/** Quels NPC font avancer une quête, et depuis quelle étape leur action est accessible. */
class UnlockAnalyzerTest {

    private static Map<String, Object> json(String json) {
        return EditorSupportTest.tree(json);
    }

    private static Quest quest(int steps) {
        StringBuilder sb = new StringBuilder("{\"name\":\"Q\",\"steps\":[");
        for (int i = 0; i < steps; i++) sb.append(i > 0 ? "," : "").append("{\"text\":\"s").append(i).append("\"}");
        return Quest.parse("q", json(sb.append("]}").toString()));
    }

    private static final String STEP_IS_0 = "\"at0\":{\"condition\":\"compare\",\"operator\":\"equal\",\"value1\":{\"type\":\"quest\",\"quest\":\"q\"},\"value2\":{\"type\":\"value\",\"value\":0}}";

    private static QuestNpc npc(String id, String script) {
        return QuestNpc.parse(id, json("{\"script\":{" + script + "}}"));
    }

    private static Map<String, Unlocker> byNode(List<Unlocker> list) {
        Map<String, Unlocker> out = new LinkedHashMap<>();
        for (Unlocker u : list) out.put(u.node(), u);
        return out;
    }

    @Test
    void anActionWithoutConditionIsAccessibleFromAnywhere() {
        QuestNpc n = npc("a", "\"main\":{\"action\":\"list\",\"liste\":[\"go\"]},\"go\":{\"action\":\"quest\",\"mode\":\"step\",\"quest\":\"q\",\"step\":2}");
        List<Unlocker> u = UnlockAnalyzer.unlockers(quest(3), List.of(n));
        assertEquals(1, u.size());
        assertEquals("a", u.get(0).npcId());
        assertEquals(2, u.get(0).toStep());
        assertNull(u.get(0).fromSteps(), "n'importe quelle étape");
    }

    @Test
    void aStepConditionRestrictsTheSourceSteps() {
        QuestNpc n = npc("a", "\"main\":{\"action\":\"list\",\"liste\":[\"go\"]},\"go\":{\"action\":\"quest\",\"mode\":\"step\",\"quest\":\"q\",\"step\":1,\"condition\":\"at0\"}," + STEP_IS_0);
        Unlocker u = UnlockAnalyzer.unlockers(quest(3), List.of(n)).get(0);
        assertEquals(List.of(0), u.fromSteps());
        assertEquals(1, u.toStep());
    }

    @Test
    void notStartedIsStepMinusOne() {
        QuestNpc n = npc("a", "\"main\":{\"action\":\"list\",\"liste\":[\"go\"]},\"go\":{\"action\":\"quest\",\"mode\":\"new\",\"quest\":\"q\",\"condition\":\"c\"},"
            + "\"c\":{\"condition\":\"compare\",\"operator\":\"equal\",\"value1\":{\"type\":\"quest\",\"quest\":\"q\"},\"value2\":{\"type\":\"value\",\"value\":-1}}");
        Unlocker u = UnlockAnalyzer.unlockers(quest(2), List.of(n)).get(0);
        assertEquals("new", u.mode());
        assertEquals(0, u.toStep());
        assertEquals(List.of(-1), u.fromSteps());
    }

    @Test
    void anImpossibleConditionHidesTheNpc() {
        QuestNpc n = npc("a", "\"main\":{\"action\":\"list\",\"liste\":[\"go\"]},\"go\":{\"action\":\"quest\",\"mode\":\"step\",\"quest\":\"q\",\"step\":1,\"condition\":\"c\"},"
            + "\"c\":{\"condition\":\"compare\",\"operator\":\"equal\",\"value1\":{\"type\":\"quest\",\"quest\":\"q\"},\"value2\":{\"type\":\"value\",\"value\":9}}");
        assertTrue(UnlockAnalyzer.unlockers(quest(3), List.of(n)).isEmpty(), "l'étape 9 n'existe pas : jamais accessible");
    }

    @Test
    void unrelatedConditionsAreUnknownAndDoNotBlock() {
        QuestNpc n = npc("a", "\"main\":{\"action\":\"list\",\"liste\":[\"go\"]},\"go\":{\"action\":\"quest\",\"mode\":\"step\",\"quest\":\"q\",\"step\":1,\"condition\":\"lvl\"},"
            + "\"lvl\":{\"condition\":\"compare\",\"operator\":\"superior\",\"value1\":{\"type\":\"level\"},\"value2\":{\"type\":\"value\",\"value\":10}}");
        Unlocker u = UnlockAnalyzer.unlockers(quest(2), List.of(n)).get(0);
        assertNull(u.fromSteps());
    }

    @Test
    void conditionsOfOtherQuestsAreUnknown() {
        QuestNpc n = npc("a", "\"main\":{\"action\":\"list\",\"liste\":[\"go\"]},\"go\":{\"action\":\"quest\",\"mode\":\"step\",\"quest\":\"q\",\"step\":1,\"condition\":\"c\"},"
            + "\"c\":{\"condition\":\"compare\",\"operator\":\"equal\",\"value1\":{\"type\":\"quest\",\"quest\":\"autre\"},\"value2\":{\"type\":\"value\",\"value\":9}}");
        assertNull(UnlockAnalyzer.unlockers(quest(2), List.of(n)).get(0).fromSteps());
    }

    @Test
    void combinedConditionsReverseAndBetween() {
        String script = "\"main\":{\"action\":\"list\",\"liste\":[\"all\",\"any\",\"not\",\"btw\"]},"
            + "\"all\":{\"action\":\"quest\",\"mode\":\"step\",\"quest\":\"q\",\"step\":3,\"condition\":\"andc\"},"
            + "\"any\":{\"action\":\"quest\",\"mode\":\"step\",\"quest\":\"q\",\"step\":3,\"condition\":\"orc\"},"
            + "\"not\":{\"action\":\"quest\",\"mode\":\"step\",\"quest\":\"q\",\"step\":3,\"condition\":\"notc\"},"
            + "\"btw\":{\"action\":\"quest\",\"mode\":\"step\",\"quest\":\"q\",\"step\":3,\"condition\":\"btwc\"},"
            + STEP_IS_0 + ","
            + "\"ge1\":{\"condition\":\"compare\",\"operator\":\"superior\",\"value1\":{\"type\":\"quest\",\"quest\":\"q\"},\"value2\":{\"type\":\"value\",\"value\":1}},"
            + "\"lvl\":{\"condition\":\"compare\",\"operator\":\"superior\",\"value1\":{\"type\":\"level\"},\"value2\":{\"type\":\"value\",\"value\":10}},"
            + "\"andc\":{\"condition\":\"list\",\"mode\":\"all\",\"liste\":[\"ge1\",\"lvl\"]},"
            + "\"orc\":{\"condition\":\"list\",\"mode\":\"single\",\"liste\":[\"at0\",\"ge1\"]},"
            + "\"notc\":{\"condition\":\"compare\",\"reverse\":true,\"operator\":\"superior_strict\",\"value1\":{\"type\":\"quest\",\"quest\":\"q\"},\"value2\":{\"type\":\"value\",\"value\":1}},"
            + "\"btwc\":{\"condition\":\"between\",\"value1\":{\"type\":\"value\",\"value\":1},\"value2\":{\"type\":\"quest\",\"quest\":\"q\"},\"value3\":{\"type\":\"value\",\"value\":2}}";
        Map<String, Unlocker> u = byNode(UnlockAnalyzer.unlockers(quest(4), List.of(npc("a", script))));
        assertEquals(List.of(1, 2, 3), u.get("all").fromSteps(), "étape >= 1 ET niveau inconnu");
        assertEquals(List.of(0, 1, 2, 3), u.get("any").fromSteps(), "étape = 0 OU étape >= 1 : tout sauf « pas commencée »");
    }

    @Test
    void orOfStepConditionsListsTheMatchingSteps() {
        String script = "\"main\":{\"action\":\"list\",\"liste\":[\"go\"]},\"go\":{\"action\":\"quest\",\"mode\":\"step\",\"quest\":\"q\",\"step\":3,\"condition\":\"orc\"},"
            + STEP_IS_0 + ",\"ge2\":{\"condition\":\"compare\",\"operator\":\"superior\",\"value1\":{\"type\":\"quest\",\"quest\":\"q\"},\"value2\":{\"type\":\"value\",\"value\":2}},"
            + "\"orc\":{\"condition\":\"list\",\"mode\":\"single\",\"liste\":[\"at0\",\"ge2\"]}";
        Unlocker u = UnlockAnalyzer.unlockers(quest(4), List.of(npc("a", script))).get(0);
        assertEquals(List.of(0, 2, 3), u.fromSteps());
    }

    @Test
    void reverseAndBetweenAreEvaluated() {
        String script = "\"main\":{\"action\":\"list\",\"liste\":[\"n\",\"b\"]},"
            + "\"n\":{\"action\":\"quest\",\"mode\":\"step\",\"quest\":\"q\",\"step\":3,\"condition\":\"notc\"},"
            + "\"b\":{\"action\":\"quest\",\"mode\":\"step\",\"quest\":\"q\",\"step\":3,\"condition\":\"btwc\"},"
            + "\"notc\":{\"condition\":\"compare\",\"reverse\":true,\"operator\":\"superior_strict\",\"value1\":{\"type\":\"quest\",\"quest\":\"q\"},\"value2\":{\"type\":\"value\",\"value\":1}},"
            + "\"btwc\":{\"condition\":\"between\",\"value1\":{\"type\":\"value\",\"value\":1},\"value2\":{\"type\":\"quest\",\"quest\":\"q\"},\"value3\":{\"type\":\"value\",\"value\":2}}";
        Map<String, Unlocker> u = byNode(UnlockAnalyzer.unlockers(quest(4), List.of(npc("a", script))));
        assertEquals(List.of(-1, 0, 1), u.get("n").fromSteps(), "NON (étape > 1)");
        assertEquals(List.of(1, 2), u.get("b").fromSteps(), "1 <= étape <= 2");
    }

    @Test
    void singleListStopsAtTheFirstSureChild() {
        String script = "\"main\":{\"action\":\"list\",\"mode\":\"single\",\"liste\":[\"first\",\"second\"]},"
            + "\"first\":{\"action\":\"message\",\"text\":\"x\"},"
            + "\"second\":{\"action\":\"quest\",\"mode\":\"step\",\"quest\":\"q\",\"step\":1}";
        assertTrue(UnlockAnalyzer.unlockers(quest(2), List.of(npc("a", script))).isEmpty(), "le premier est toujours joué : le second jamais");
        String unsure = script.replace("\"first\":{\"action\":\"message\"", "\"first\":{\"condition\":\"lvl\",\"action\":\"message\"")
            + ",\"lvl\":{\"condition\":\"compare\",\"operator\":\"superior\",\"value1\":{\"type\":\"level\"},\"value2\":{\"type\":\"value\",\"value\":3}}";
        assertEquals(1, UnlockAnalyzer.unlockers(quest(2), List.of(npc("a", unsure))).size(), "si le premier est incertain le second reste possible");
    }

    @Test
    void dialogueOnlyPlaysTheFirstTextWhoseConditionIsTrue() {
        String script = "\"main\":{\"action\":\"list\",\"liste\":[\"talk\"]},\"talk\":{\"action\":\"dialogue\",\"dialogues\":{\"start\":{\"texts\":["
            + "{\"text\":\"always\"},{\"text\":\"never reached\",\"action\":\"hidden\"}]}}},"
            + "\"hidden\":{\"action\":\"quest\",\"mode\":\"step\",\"quest\":\"q\",\"step\":1}";
        assertTrue(UnlockAnalyzer.unlockers(quest(2), List.of(npc("a", script))).isEmpty());
    }

    @Test
    void actionsReachedThroughDialogueAnswersAreFound() {
        String script = "\"main\":{\"action\":\"list\",\"liste\":[\"talk\"]},\"talk\":{\"action\":\"dialogue\",\"dialogues\":{\"start\":{\"texts\":["
            + "{\"text\":\"hi\",\"answers\":[{\"text\":\"ok\",\"action\":\"go\"},{\"text\":\"hidden\",\"condition\":\"false\",\"action\":\"no\"}],\"action\":\"txt\"}]}}},"
            + "\"go\":{\"action\":\"quest\",\"mode\":\"substep\",\"quest\":\"q\",\"step\":1,\"substep\":0},"
            + "\"no\":{\"action\":\"quest\",\"mode\":\"step\",\"quest\":\"q\",\"step\":0},"
            + "\"txt\":{\"action\":\"quest\",\"mode\":\"new\",\"quest\":\"q\"}";
        Map<String, Unlocker> u = byNode(UnlockAnalyzer.unlockers(quest(2), List.of(npc("a", script))));
        assertEquals(Set.of("go", "txt"), u.keySet(), "la réponse dont la condition est fausse n'est pas accessible");
        assertEquals("substep", u.get("go").mode());
        assertEquals(0, u.get("go").substep());
    }

    @Test
    void unreachableNodesAreIgnored() {
        QuestNpc n = npc("a", "\"main\":{\"action\":\"message\",\"text\":\"x\"},\"orphan\":{\"action\":\"quest\",\"mode\":\"step\",\"quest\":\"q\",\"step\":1}");
        assertTrue(UnlockAnalyzer.unlockers(quest(2), List.of(n)).isEmpty());
    }

    @Test
    void otherQuestsAndModesAreIgnored() {
        QuestNpc n = npc("a", "\"main\":{\"action\":\"list\",\"liste\":[\"x\",\"y\"]},"
            + "\"x\":{\"action\":\"quest\",\"mode\":\"step\",\"quest\":\"autre\",\"step\":1},\"y\":{\"action\":\"quest\",\"mode\":\"variable\",\"quest\":\"q\",\"variable\":\"v\",\"value\":1}");
        assertTrue(UnlockAnalyzer.unlockers(quest(2), List.of(n)).isEmpty());
    }

    @Test
    void cyclesDoNotLoopForever() {
        QuestNpc n = npc("a", "\"main\":{\"action\":\"list\",\"liste\":[\"l2\"]},\"l2\":{\"action\":\"list\",\"liste\":[\"main\",\"go\"]},\"go\":{\"action\":\"quest\",\"mode\":\"step\",\"quest\":\"q\",\"step\":1}");
        assertEquals(1, UnlockAnalyzer.unlockers(quest(2), List.of(n)).size());
    }

    @Test
    void severalNpcsAreSortedAndListed() {
        String go = "\"main\":{\"action\":\"list\",\"liste\":[\"go\"]},\"go\":{\"action\":\"quest\",\"mode\":\"step\",\"quest\":\"q\",\"step\":1}";
        Collection<QuestNpc> npcs = List.of(npc("zeta", go), npc("alpha", go), npc("none", "\"main\":{\"action\":\"message\",\"text\":\"x\"}"));
        List<String> ids = UnlockAnalyzer.unlockers(quest(2), npcs).stream().map(Unlocker::npcId).collect(Collectors.toList());
        assertEquals(List.of("alpha", "zeta"), ids);
    }

    @Test
    void conditionEvaluationIsThreeValued() {
        QuestNpc n = npc("a", STEP_IS_0 + ",\"lvl\":{\"condition\":\"compare\",\"operator\":\"superior\",\"value1\":{\"type\":\"level\"},\"value2\":{\"type\":\"value\",\"value\":1}}"
            + ",\"custom\":{\"condition\":\"skt_custom\"}");
        assertEquals(Tri.TRUE, UnlockAnalyzer.evaluate(n.script(), "at0", new Assumption("q", 0), 0));
        assertEquals(Tri.FALSE, UnlockAnalyzer.evaluate(n.script(), "at0", new Assumption("q", 1), 0));
        assertEquals(Tri.UNKNOWN, UnlockAnalyzer.evaluate(n.script(), "lvl", new Assumption("q", 0), 0));
        assertEquals(Tri.UNKNOWN, UnlockAnalyzer.evaluate(n.script(), "custom", new Assumption("q", 0), 0));
        assertEquals(Tri.UNKNOWN, UnlockAnalyzer.evaluate(n.script(), "missing", new Assumption("q", 0), 0));
        assertEquals(Tri.TRUE, UnlockAnalyzer.evaluate(n.script(), "true", new Assumption("q", 0), 0));
        assertEquals(Tri.FALSE, UnlockAnalyzer.evaluate(n.script(), "false", new Assumption("q", 0), 0));
        assertEquals(Tri.TRUE, UnlockAnalyzer.evaluate(n.script(), null, new Assumption("q", 0), 0));
    }

    @Test
    void reachableReturnsTheActionIds() {
        QuestNpc n = npc("a", "\"main\":{\"action\":\"list\",\"liste\":[\"x\"]},\"x\":{\"action\":\"message\",\"text\":\"m\"},\"y\":{\"action\":\"message\",\"text\":\"n\"}");
        Set<String> reach = UnlockAnalyzer.reachable(n.script(), new Assumption("q", 0));
        assertEquals(Set.of("main", "x"), reach);
    }
}
