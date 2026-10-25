package skwhy.customentity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class CustomEntityDefOverridesTest {

    private static CustomEntityDef base() {
        Map<String, Object> tree = new LinkedHashMap<>();
        tree.put("type", "wolf");
        tree.put("name", "<aqua>Loup");
        tree.put("scale", 1.5);
        Map<String, Object> hitbox = new LinkedHashMap<>();
        hitbox.put("width", 0.8);
        hitbox.put("height", 1.2);
        tree.put("hitbox", hitbox);
        Map<String, Object> equipment = new LinkedHashMap<>();
        equipment.put("head", "iron_helmet");
        tree.put("equipment", equipment);
        List<String> errors = new ArrayList<>();
        CustomEntityDef def = CustomEntityDef.parse("skt", tree, errors);
        assertTrue(errors.isEmpty(), errors.toString());
        return def;
    }

    @Test
    void noOverrideKeepsTheSameDefinition() {
        CustomEntityDef def = base();
        assertSame(def, def.withOverrides(Map.of(), new ArrayList<>()));
    }

    @Test
    void overridesReplaceOnlyTheirParameters() {
        CustomEntityDef def = base();
        Map<String, Object> overrides = new LinkedHashMap<>();
        overrides.put("scale", 3.0);
        overrides.put("hitbox.width", 2.0);
        overrides.put("glowing", true);
        overrides.put("ai", "Minecraft:Zombie");
        overrides.put("equipment.chest", "iron_chestplate");
        List<String> errors = new ArrayList<>();
        CustomEntityDef result = def.withOverrides(overrides, errors);
        assertTrue(errors.isEmpty(), errors.toString());
        assertEquals(3.0, result.scale());
        assertEquals(2.0, result.hitboxWidth());
        assertEquals(1.2, result.hitboxHeight());
        assertTrue(result.glowing());
        assertEquals("zombie", result.ai());
        assertEquals("<aqua>Loup", result.name());
        assertEquals("iron_helmet", result.equipment().get("head"));
        assertEquals("iron_chestplate", result.equipment().get("chest"));
        // la définition d'origine n'est pas modifiée
        assertEquals(1.5, def.scale());
        assertNull(def.ai());
    }

    @Test
    void nullOverrideRestoresTheDefault() {
        Map<String, Object> overrides = new LinkedHashMap<>();
        overrides.put("scale", null);
        overrides.put("equipment.head", null);
        CustomEntityDef result = base().withOverrides(overrides, new ArrayList<>());
        assertEquals(1.0, result.scale());
        assertFalse(result.equipment().containsKey("head"));
    }

    @Test
    void metadataOverrideUsesTypeColonValue() {
        CustomEntityDef result = base().withOverrides(Map.of("metadata.15", "byte:1"), new ArrayList<>());
        assertEquals(1, result.metadata().size());
        assertEquals(15, result.metadata().get(0).index());
        assertEquals("byte", result.metadata().get(0).type());
        assertEquals("byte:1", result.value("metadata.15"));
    }

    @Test
    void invalidOverridesAreIgnoredAndReported() {
        Map<String, Object> overrides = new LinkedHashMap<>();
        overrides.put("scale", 99.0);
        overrides.put("nope", "x");
        overrides.put("glowing", "yes");
        overrides.put("metadata.3", "bad");
        List<String> errors = new ArrayList<>();
        CustomEntityDef result = base().withOverrides(overrides, errors);
        assertEquals(4, errors.size(), errors.toString());
        assertEquals(1.5, result.scale());
        assertFalse(result.glowing());
    }

    @Test
    void kinds() {
        assertEquals("number", CustomEntityDef.kindOf("scale"));
        assertEquals("number", CustomEntityDef.kindOf("hitbox.height"));
        assertEquals("boolean", CustomEntityDef.kindOf("on_fire"));
        assertEquals("text", CustomEntityDef.kindOf("ai"));
        assertEquals("text", CustomEntityDef.kindOf("equipment.main_hand"));
        assertEquals("text", CustomEntityDef.kindOf("metadata.254"));
        assertNull(CustomEntityDef.kindOf("equipment.hat"));
        assertNull(CustomEntityDef.kindOf("metadata.255"));
        assertNull(CustomEntityDef.kindOf("metadata.x"));
        assertNull(CustomEntityDef.kindOf("health"));
        assertNotNull(CustomEntityDef.checkOverride("health", 1.0));
        assertNull(CustomEntityDef.checkOverride("scale", 2.0));
        assertNotNull(CustomEntityDef.checkOverride("scale", 0.0));
        assertNotNull(CustomEntityDef.checkOverride("hitbox.width", -1.0));
    }

    @Test
    void aiIsReadFromTheFile() {
        Map<String, Object> tree = new LinkedHashMap<>();
        tree.put("type", "mannequin");
        tree.put("ai", "minecraft:Villager");
        CustomEntityDef def = CustomEntityDef.parse("v", tree, new ArrayList<>());
        assertEquals("villager", def.ai());
        assertEquals("villager", def.value("ai"));
    }
}
