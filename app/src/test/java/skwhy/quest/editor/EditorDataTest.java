package skwhy.quest.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import skwhy.customitem.CustomItemDef;
import skwhy.quest.model.Quest;
import skwhy.quest.model.QuestNpc;

/** Catalogue par monde, définitions envoyées au navigateur et cache. */
class EditorDataTest {

    /** Registre modifiable (comme QuestRegistry : recharger remplace l'objet). */
    static final class Registry implements EditorData.Source {
        final Map<String, Quest> quests = new LinkedHashMap<>();
        final Map<String, QuestNpc> npcs = new LinkedHashMap<>();
        final List<CustomItemDef> items = new java.util.ArrayList<>();
        @Override public Collection<Quest> quests() { return quests.values(); }
        @Override public Collection<QuestNpc> npcs() { return npcs.values(); }
        @Override public Quest quest(String id) { return quests.get(id); }
        @Override public QuestNpc npc(String id) { return npcs.get(id); }
        @Override public Collection<CustomItemDef> customItems() { return items; }
        void quest(String id, String json) { quests.put(id, Quest.parse(id, EditorSupportTest.tree(json))); }
        void npc(String id, String json) { npcs.put(id, QuestNpc.parse(id, EditorSupportTest.tree(json))); }
    }

    private static Registry registry() {
        Registry r = new Registry();
        r.quest("zelda", "{\"name\":\"<red>Zelda\",\"catégorie\":\"quete_zelda\",\"steps\":[{\"text\":\"a\"}]}");
        r.quest("ville", "{\"name\":\"Ville\",\"steps\":[]}");
        r.quest("alpha", "{\"name\":\"&aAlpha\",\"category\":\"quete_zelda\",\"steps\":[{\"text\":\"a\"},{\"text\":\"b\"}]}");
        r.npc("maire", "{\"name\":\"Maire\",\"entity\":{\"u2\":{\"position\":2,\"location\":{\"world\":\"event\"}},\"u1\":{\"position\":1,\"location\":{\"world\":\"villes\"}}},"
            + "\"script\":{\"main\":{\"action\":\"list\",\"liste\":[\"go\"]},\"go\":{\"action\":\"quest\",\"mode\":\"step\",\"quest\":\"zelda\",\"step\":0}}}");
        r.npc("fantome", "{\"type\":\"scripté\"}");
        return r;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parse(String json) {
        return (Map<String, Object>) EditorJson.parse(json);
    }

    @Test
    void questsAreGroupedByTheirCategoryAndNpcsByTheirFirstPosition() {
        EditorData data = new EditorData(registry());
        List<Map<String, Object>> worlds = (List<Map<String, Object>>) (Object) parse(data.catalogJson()).get("worlds");
        assertEquals(List.of("", "quete_zelda", "villes"), worlds.stream().map(w -> (String) w.get("name")).toList(), "ordre alphabétique, '' = sans position");
        Map<String, Object> zelda = worlds.get(1);
        List<Map<String, Object>> qs = (List<Map<String, Object>>) zelda.get("quests");
        assertEquals(List.of("alpha", "zelda"), qs.stream().map(q -> (String) q.get("id")).toList(), "triées par nom sans couleurs");
        assertEquals(2, qs.get(0).get("steps"));
        assertEquals("villes", EditorData.npcWorld(registry().npc("maire")), "la position 1 passe avant la 2");
        assertEquals("", EditorData.npcWorld(registry().npc("fantome")));
        Map<String, Object> villes = worlds.get(2);
        assertEquals(List.of("ville"), ((List<Map<String, Object>>) villes.get("quests")).stream().map(q -> (String) q.get("id")).toList(), "catégorie par défaut : villes");
        assertEquals(List.of("maire"), ((List<Map<String, Object>>) villes.get("npcs")).stream().map(n -> (String) n.get("id")).toList());
    }

    @Test
    void plainRemovesAllColorSyntaxes() {
        assertEquals("Zelda 1 x", EditorData.plain("&aZelda §l1 <#ff8800>x</#ff8800>"));
        assertEquals("a<b", EditorData.plain("a<b"));
        assertEquals("", EditorData.plain(null));
    }

    @Test
    void questJsonCarriesTheRawTreeTheVersionAndTheNpcsThatAdvanceIt() {
        EditorData data = new EditorData(registry());
        Map<String, Object> q = parse(data.entityJson("quest", "zelda"));
        assertEquals("quest", q.get("kind"));
        assertNull(q.get("unlockers"), "l'analyse des NPC est une requête à part");
        assertEquals("zelda", q.get("id"));
        assertEquals(EditorJson.version(registry().quest("zelda").raw()), q.get("version"));
        assertEquals("<red>Zelda", ((Map<?, ?>) q.get("raw")).get("name"));
        Map<String, Object> u = parse(data.unlockersJson("zelda"));
        assertEquals("zelda", u.get("id"));
        List<Map<String, Object>> unlockers = (List<Map<String, Object>>) u.get("unlockers");
        assertEquals(1, unlockers.size());
        assertEquals("maire", unlockers.get(0).get("npc"));
        assertEquals("Maire", unlockers.get(0).get("npcName"));
        assertEquals("go", unlockers.get(0).get("node"));
        assertEquals("step", unlockers.get(0).get("mode"));
        assertNull(unlockers.get(0).get("fromSteps"), "sans condition : depuis n'importe quelle étape");
    }

    @Test
    void npcJsonListsTheQuestsItDrives() {
        EditorData data = new EditorData(registry());
        Map<String, Object> n = parse(data.entityJson("npc", "maire"));
        assertEquals("villes", n.get("world"));
        List<Map<String, Object>> drives = (List<Map<String, Object>>) n.get("drives");
        assertEquals(1, drives.size());
        assertEquals("zelda", drives.get(0).get("quest"));
        assertTrue(((String) drives.get(0).get("questName")).contains("Zelda"));
    }

    @Test
    void unknownEntitiesAreNull() {
        EditorData data = new EditorData(registry());
        assertNull(data.entityJson("quest", "nope"));
        assertNull(data.entityJson("npc", "nope"));
        assertNull(data.entityJson("other", "zelda"));
        assertNull(data.unlockersJson("nope"));
        assertNull(data.version("quest", "nope"));
        assertNull(data.raw("monster", "x"));
        assertNotNull(data.raw("quest", "zelda"));
    }

    @Test
    void theCacheIsUsedUntilTheRegistryObjectChanges() {
        Registry r = registry();
        EditorData data = new EditorData(r);
        String first = data.entityJson("quest", "zelda");
        String firstU = data.unlockersJson("zelda");
        int after = data.computations();
        assertSame(first, data.entityJson("quest", "zelda"), "même texte : rien n'est recalculé");
        assertSame(firstU, data.unlockersJson("zelda"));
        assertEquals(after, data.computations());

        // un autre NPC change : seule l'analyse des NPC d'une quête est refaite, pas la quête
        r.npc("garde", "{\"script\":{\"main\":{\"action\":\"list\",\"liste\":[\"go\"]},\"go\":{\"action\":\"quest\",\"mode\":\"new\",\"quest\":\"zelda\"}}}");
        assertSame(first, data.entityJson("quest", "zelda"), "la quête ne dépend pas des NPC");
        String second = data.unlockersJson("zelda");
        assertEquals(after + 1, data.computations());
        assertTrue(second.contains("garde"));

        // la quête elle-même est rechargée (nouvel objet, même contenu) : recalcul des deux
        int before = data.computations();
        r.quest("zelda", "{\"name\":\"Zelda 2\",\"catégorie\":\"quete_zelda\",\"steps\":[{\"text\":\"a\"}]}");
        assertTrue(data.entityJson("quest", "zelda").contains("Zelda 2"));
        data.unlockersJson("zelda");
        assertEquals(before + 2, data.computations());

        // une autre quête ne touche pas aux autres entrées
        int c = data.computations();
        data.entityJson("quest", "alpha");
        r.quest("alpha", "{\"name\":\"A2\",\"steps\":[]}");
        data.entityJson("quest", "zelda");
        assertEquals(c + 1, data.computations(), "seule « alpha » était nouvelle ; « zelda » reste en cache");
    }

    @Test
    void theNpcCacheIgnoresOtherEntities() {
        Registry r = registry();
        EditorData data = new EditorData(r);
        data.entityJson("npc", "maire");
        int c = data.computations();
        r.quest("autre", "{\"name\":\"x\",\"steps\":[]}");
        r.npc("nouveau", "{\"name\":\"n\"}");
        data.entityJson("npc", "maire");
        assertEquals(c, data.computations(), "la fiche d'un NPC ne dépend pas des autres");
    }

    @Test
    void theCatalogIsCachedAndRefreshedWhenSomethingIsAddedOrReplaced() {
        Registry r = registry();
        EditorData data = new EditorData(r);
        String a = data.catalogJson();
        int c = data.computations();
        assertSame(a, data.catalogJson());
        assertEquals(c, data.computations());
        r.quest("neuve", "{\"name\":\"Neuve\",\"catégorie\":\"autre\",\"steps\":[]}");
        assertTrue(data.catalogJson().contains("neuve"));
        assertEquals(c + 1, data.computations());
        r.npcs.remove("fantome");
        assertTrue(!data.catalogJson().contains("fantome"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void theCatalogListsCustomItemsAndRefreshesWhenTheyChange() {
        Registry r = registry();
        EditorData data = new EditorData(r);
        assertEquals(List.of(), parse(data.catalogJson()).get("items"), "sans module d'items : liste vide");
        Map<String, Object> tree = new LinkedHashMap<>();
        tree.put("material_id", "paper");
        tree.put("name", "<gold>Billet");
        r.items.add(CustomItemDef.parse("billet", tree, new java.util.ArrayList<>()));
        r.items.add(CustomItemDef.parse("anneau", Map.of("material_id", "gold_nugget"), new java.util.ArrayList<>()));
        List<Map<String, Object>> items = (List<Map<String, Object>>) parse(data.catalogJson()).get("items");
        assertEquals(List.of("anneau", "billet"), items.stream().map(i -> (String) i.get("id")).toList(), "triés par id");
        assertEquals("paper", items.get(1).get("material"));
        assertEquals("<gold>Billet", items.get(1).get("name"));
        assertEquals("", items.get(0).get("name"));
    }
}
