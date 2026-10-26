package skwhy.customentity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class CustomEntityDefTest {

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    @Test
    void minimalDefinitionUsesDefaults() {
        List<String> errors = new ArrayList<>();
        CustomEntityDef def = CustomEntityDef.parse("wolf", map("type", "Minecraft:Wolf"), errors);
        assertNotNull(def);
        assertTrue(errors.isEmpty());
        assertEquals("wolf", def.type());
        assertNull(def.name());
        assertTrue(def.nameVisible());
        assertEquals(1.0, def.scale());
        assertTrue(def.silent());
        assertFalse(def.glowing());
        assertEquals(0.6, def.hitboxWidth());
        assertEquals(1.8, def.hitboxHeight());
        assertTrue(def.equipment().isEmpty());
        assertTrue(def.metadata().isEmpty());
    }

    @Test
    void missingTypeRejectsTheDefinition() {
        List<String> errors = new ArrayList<>();
        assertNull(CustomEntityDef.parse("x", map("name", "a"), errors));
        assertEquals(1, errors.size());
        assertNull(CustomEntityDef.parse("x", map("type", "  "), new ArrayList<>()));
    }

    @Test
    void fullDefinition() {
        List<String> errors = new ArrayList<>();
        CustomEntityDef def = CustomEntityDef.parse("m", map(
            "type", "mannequin", "name", "<gold>Marchand", "name_visible", "false", "scale", 2, "glowing", true,
            "invisible", true, "silent", false, "on_fire", true,
            "hitbox", map("width", 1.0, "height", 2.5),
            "skin", map("value", "abc", "signature", "def"),
            "equipment", map("head", "iron_helmet", "main_hand", "stick"),
            "metadata", map("15", map("type", "byte", "value", 1))), errors);
        assertNotNull(def);
        assertTrue(errors.isEmpty(), errors.toString());
        assertEquals("<gold>Marchand", def.name());
        assertFalse(def.nameVisible());
        assertEquals(2.0, def.scale());
        assertTrue(def.glowing() && def.invisible() && def.onFire());
        assertFalse(def.silent());
        assertEquals(1.0, def.hitboxWidth());
        assertEquals(2.5, def.hitboxHeight());
        assertEquals("abc", def.skinValue());
        assertEquals("def", def.skinSignature());
        assertEquals("iron_helmet", def.equipment().get("head"));
        assertEquals(1, def.metadata().size());
        assertEquals(15, def.metadata().get(0).index());
        assertEquals("byte", def.metadata().get(0).type());
    }

    @Test
    void invalidValuesAreIgnoredWithAnError() {
        List<String> errors = new ArrayList<>();
        CustomEntityDef def = CustomEntityDef.parse("bad", map(
            "type", "wolf", "scale", 0, "hitbox", map("width", -1, "height", 2),
            "skin", map("signature", "x"),
            "equipment", map("tail", "stick"),
            "metadata", map("abc", map("type", "byte", "value", 1), "300", map("type", "byte", "value", 1),
                "5", map("type", "color", "value", 1), "6", "texte")), errors);
        assertNotNull(def);
        assertEquals(1.0, def.scale());
        assertEquals(0.6, def.hitboxWidth());
        assertEquals(1.8, def.hitboxHeight());
        assertNull(def.skinValue());
        assertTrue(def.equipment().isEmpty());
        assertTrue(def.metadata().isEmpty());
        assertEquals(8, errors.size(), errors.toString());
    }

    @Test
    void valueReadsEveryParameterWithDefaults() {
        CustomEntityDef def = CustomEntityDef.parse("v", map("type", "cow", "equipment", map("head", "stick"),
            "metadata", map("15", map("type", "byte", "value", 1)), "skin", map("value", "abc")), new ArrayList<>());
        assertEquals("cow", def.value("type"));
        assertNull(def.value("name"));
        assertEquals(1.0, def.value("scale"));
        assertEquals(true, def.value("name_visible"));
        assertEquals(true, def.value("silent"));
        assertEquals(false, def.value("glowing"));
        assertEquals(0.6, def.value("hitbox.width"));
        assertEquals("stick", def.value("equipment.head"));
        assertNull(def.value("equipment.feet"));
        assertEquals("byte:1", def.value("metadata.15"));
        assertNull(def.value("metadata.16"));
        assertEquals("abc", def.value("skin.value"));
        assertNull(def.value("skin.signature"));
        assertNull(def.value("inconnu"));
    }

    @Test
    void setAndRemovePathBuildSections() {
        Map<String, Object> body = map("type", "cow");
        CustomEntityDef.setPath(body, "scale", 2.0);
        CustomEntityDef.setPath(body, "hitbox.width", 1.5);
        CustomEntityDef.setPath(body, "equipment.head", "stick");
        CustomEntityDef.setPath(body, "metadata.15", "byte:1");
        CustomEntityDef.setPath(body, "metadata.16", "boolean:true");
        CustomEntityDef.setPath(body, "metadata.17", "string:a:b");
        List<String> errors = new ArrayList<>();
        CustomEntityDef def = CustomEntityDef.parse("s", body, errors);
        assertTrue(errors.isEmpty(), errors.toString());
        assertEquals(2.0, def.scale());
        assertEquals(1.5, def.hitboxWidth());
        assertEquals("stick", def.equipment().get("head"));
        assertEquals("byte:1", def.value("metadata.15"));
        assertEquals("boolean:true", def.value("metadata.16"));
        assertEquals("string:a:b", def.value("metadata.17"));

        CustomEntityDef.removePath(body, "hitbox.width");
        assertFalse(body.containsKey("hitbox"), "la section vide est retirée");
        CustomEntityDef.setPath(body, "skin.value", "a");
        CustomEntityDef.setPath(body, "skin.signature", "b");
        CustomEntityDef.removePath(body, "skin.value");
        assertFalse(body.containsKey("skin"), "retirer la valeur du skin retire tout le skin");
        CustomEntityDef.removePath(body, "scale");
        CustomEntityDef.removePath(body, "inconnu.x");
        assertEquals(1.0, CustomEntityDef.parse("s", body, new ArrayList<>()).scale());
    }

    @Test
    void invalidMetadataTextIsRejected() {
        Map<String, Object> body = map("type", "cow");
        for (String bad : new String[]{ "1", ":1", "color:1", "byte:abc", "float:" }) {
            org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> CustomEntityDef.setPath(body, "metadata.5", bad), bad);
        }
        assertTrue(body.size() == 1);
    }

    @Test
    void numbersAndBooleansAcceptStrings() {
        CustomEntityDef def = CustomEntityDef.parse("s", map("type", "cow", "scale", "1.25", "glowing", "true"), new ArrayList<>());
        assertEquals(1.25, def.scale());
        assertTrue(def.glowing());
    }
}
