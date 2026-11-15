package skwhy.customitem;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class PlaceholdersTest {

    @Test
    void replacesInNestedValuesButNotKeys() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("{value}", "{value}");
        data.put("n", 3);
        Map<String, Object> tree = new LinkedHashMap<>();
        tree.put("name", "Billet {value}");
        tree.put("lore", List.of("a {value}", "b"));
        tree.put("data", data);
        Map<String, Object> out = Placeholders.apply(tree, Map.of("value", "50"));
        assertEquals("Billet 50", out.get("name"));
        assertEquals(List.of("a 50", "b"), out.get("lore"));
        @SuppressWarnings("unchecked")
        Map<String, Object> outData = (Map<String, Object>) out.get("data");
        assertEquals("50", outData.get("{value}"));
        assertEquals(3, outData.get("n"));
        assertEquals("Billet {value}", tree.get("name"), "l'arbre d'origine n'est pas modifie");
    }

    @Test
    void unknownPlaceholderIsKept() {
        assertEquals("x {autre}", Placeholders.replace("x {autre}", Map.of("value", "1")));
    }

    @Test
    void loreMarkerPlacesItemLore() {
        List<String> item = List.of("i1", "i2");
        assertEquals(List.of("i1", "i2", "n1"), Placeholders.mergeLore(item, List.of("n1")));
        assertEquals(List.of("n0", "i1", "i2", "n1"), Placeholders.mergeLore(item, List.of("n0", "[lore]", "n1")));
        assertEquals(List.of("i1", "i2"), Placeholders.mergeLore(item, List.of("[lore]")));
    }
}
