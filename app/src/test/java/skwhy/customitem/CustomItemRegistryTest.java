package skwhy.customitem;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CustomItemRegistryTest {

    private static Map<String, Object> tree(String material) {
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("material_id", material);
        return t;
    }

    @Test
    void idsAreNormalized() {
        assertEquals("joyau_de_préservation", CustomItemIds.normalize("  Joyau de  préservation "));
        assertTrue(CustomItemIds.isValid("billet"));
        assertFalse(CustomItemIds.isValid(""));
        assertFalse(CustomItemIds.isValid("a/b"));
        assertFalse(CustomItemIds.isValid("a;b"));
        assertFalse(CustomItemIds.isValid(".hidden"));
    }

    @Test
    void parseRequiresMaterialAndStripsNamespace() {
        List<String> errors = new ArrayList<>();
        CustomItemDef def = CustomItemDef.parse("x", tree("minecraft:Stone"), errors);
        assertNotNull(def);
        assertEquals("stone", def.materialId());
        assertTrue(errors.isEmpty());
        assertNull(CustomItemDef.parse("x", new LinkedHashMap<>(), errors));
        assertEquals(1, errors.size());
    }

    @Test
    void twoLayerFileIsRejectedWithExplanation() {
        Map<String, Object> inner = tree("stone");
        Map<String, Object> outer = new LinkedHashMap<>();
        outer.put("billet", inner);
        List<String> errors = new ArrayList<>();
        assertNull(CustomItemDef.parse("billet", outer, errors));
        assertTrue(errors.get(0).contains("nom du fichier"));
    }

    @Test
    void badShapesAreReported() {
        Map<String, Object> t = tree("stone");
        t.put("lore", "pas une liste");
        t.put("data", "pas une table");
        List<String> errors = new ArrayList<>();
        CustomItemDef def = CustomItemDef.parse("x", t, errors);
        assertNotNull(def);
        assertEquals(2, errors.size());
        assertFalse(def.tree().containsKey("lore"));
    }

    @Test
    void saveBumpsVersionOnlyWhenContentChanges(@TempDir Path dir) {
        CustomItemRegistry registry = new CustomItemRegistry(dir.toFile(), Logger.getAnonymousLogger());
        assertEquals(0, registry.save("Mon Item", tree("stone"), false).version());
        assertEquals(0, registry.save("mon item", tree("stone"), false).version());
        assertEquals(1, registry.save("mon_item", tree("dirt"), false).version());
        assertEquals(2, registry.save("mon_item", tree("dirt"), true).version());
        assertTrue(new File(dir.toFile(), "mon_item.yml").isFile());
        assertEquals("dirt", registry.get("MON ITEM").materialId());
    }

    @Test
    void reloadReadsOneFilePerItemAndIgnoresSubfolders(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("a.yml"), "material_id: stone\nname: \"<red>A\"\n");
        Files.writeString(dir.resolve("broken.yml"), "name: sans materiau\n");
        Files.createDirectory(dir.resolve("sous"));
        Files.writeString(dir.resolve("sous").resolve("b.yml"), "material_id: dirt\n");
        CustomItemRegistry registry = new CustomItemRegistry(dir.toFile(), Logger.getAnonymousLogger());
        assertEquals(1, registry.reload());
        assertEquals(List.of("a"), registry.ids());
        assertEquals("<red>A", registry.get("a").tree().get("name"));
    }

    @Test
    void removeDeletesTheFile(@TempDir Path dir) {
        CustomItemRegistry registry = new CustomItemRegistry(dir.toFile(), Logger.getAnonymousLogger());
        registry.save("x", tree("stone"), false);
        assertTrue(registry.remove("x"));
        assertFalse(registry.fileOf("x").exists());
        assertFalse(registry.remove("x"));
    }
}
