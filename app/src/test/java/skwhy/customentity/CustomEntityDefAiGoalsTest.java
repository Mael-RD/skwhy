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

/** Paramètre {@code ai_goals} (IA sans buts ni cibles) et metadata de variantes (since 1.6.1). */
class CustomEntityDefAiGoalsTest {

    private static CustomEntityDef parse(Map<String, Object> tree) {
        List<String> errors = new ArrayList<>();
        CustomEntityDef def = CustomEntityDef.parse("skt", tree, errors);
        assertTrue(errors.isEmpty(), errors.toString());
        return def;
    }

    @Test
    void goalsAreOnByDefault() {
        Map<String, Object> tree = new LinkedHashMap<>();
        tree.put("type", "zombie");
        tree.put("ai", "zombie");
        CustomEntityDef def = parse(tree);
        assertTrue(def.aiGoals());
        assertEquals(true, def.value("ai_goals"));
    }

    @Test
    void aiGoalsFalseIsRead() {
        Map<String, Object> tree = new LinkedHashMap<>();
        tree.put("type", "zombie");
        tree.put("ai", "zombie");
        tree.put("ai_goals", false);
        CustomEntityDef def = parse(tree);
        assertFalse(def.aiGoals());
        assertEquals(false, def.value("ai_goals"));
    }

    @Test
    void aiGoalsIsABooleanParameter() {
        assertEquals("boolean", CustomEntityDef.kindOf("ai_goals"));
        assertTrue(CustomEntityDef.fixedPaths().contains("ai_goals"));
        assertNull(CustomEntityDef.checkOverride("ai_goals", true));
        assertNotNull(CustomEntityDef.checkOverride("ai_goals", "non"));
    }

    @Test
    void overrideTurnsGoalsOffAndResetRestoresThem() {
        Map<String, Object> tree = new LinkedHashMap<>();
        tree.put("type", "zombie");
        tree.put("ai", "zombie");
        CustomEntityDef def = parse(tree);
        Map<String, Object> overrides = new LinkedHashMap<>();
        overrides.put("ai_goals", false);
        CustomEntityDef off = def.withOverrides(overrides, new ArrayList<>());
        assertFalse(off.aiGoals());
        overrides.put("ai_goals", null);
        assertTrue(def.withOverrides(overrides, new ArrayList<>()).aiGoals());
    }

    @Test
    void variantMetadataTypesAreAccepted() {
        for (String type : List.of("cat_variant", "wolf_variant", "frog_variant")) {
            assertNull(CustomEntityDef.checkOverride("metadata.20", type + ":calico"), type);
        }
        assertNotNull(CustomEntityDef.checkOverride("metadata.20", "pig_variant:warm"));
    }

    @Test
    void variantMetadataFromYaml() {
        Map<String, Object> raw = new LinkedHashMap<>();
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("type", "cat_variant");
        entry.put("value", "calico");
        raw.put("20", entry);
        Map<String, Object> tree = new LinkedHashMap<>();
        tree.put("type", "cat");
        tree.put("metadata", raw);
        CustomEntityDef def = parse(tree);
        assertEquals(1, def.metadata().size());
        assertEquals("cat_variant:calico", def.value("metadata.20"));
    }
}
