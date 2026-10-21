package skwhy.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class TreeTest {

    private static Map<String, Object> sample() {
        Map<String, Object> jobs = new LinkedHashMap<>();
        Map<String, Object> chasseur = new LinkedHashMap<>();
        chasseur.put("actif", (byte) 1);
        chasseur.put("level", 7);
        jobs.put("chasseur", chasseur);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("jobs", jobs);
        root.put("steps", List.of(Map.of("text", "a"), Map.of("text", "b")));
        root.put("name", "Quête");
        root.put("count", "12");
        return root;
    }

    @Test
    void pathFollowsSemicolonSeparatedKeys() {
        assertEquals(7, Tree.path(sample(), "jobs;chasseur;level"));
        assertNull(Tree.path(sample(), "jobs;mineur;level"));
        assertNull(Tree.path(sample(), "jobs;chasseur;level;x"));
    }

    @Test
    void pathSupportsListIndexes() {
        assertEquals("b", Tree.path(sample(), "steps[1];text"));
        assertNull(Tree.path(sample(), "steps[2];text"));
        assertNull(Tree.path(sample(), "steps[x];text"));
    }

    @Test
    void nbtBytesAreBooleans() {
        assertTrue(Tree.toBool(Tree.path(sample(), "jobs;chasseur;actif"), false));
        assertFalse(Tree.toBool((byte) 0, true));
        assertTrue(Tree.toBool("TRUE", false));
        assertFalse(Tree.toBool("nope", false));
        assertTrue(Tree.toBool(null, true));
    }

    @Test
    void numbersAreReadFromTextToo() {
        assertEquals(12, Tree.integer(sample(), "count", 0));
        assertEquals(0, Tree.integer(sample(), "name", 0));
        assertEquals(12.0, Tree.dbl(sample(), "count", 0));
    }

    @Test
    void missingValuesFallBackToDefaults() {
        assertEquals("def", Tree.str(sample(), "missing", "def"));
        assertTrue(Tree.map(sample(), "missing").isEmpty());
        assertTrue(Tree.list(sample(), "missing").isEmpty());
        assertTrue(Tree.strings(sample(), "missing").isEmpty());
    }

    @Test
    void deepCopyIsIndependent() {
        Map<String, Object> source = sample();
        @SuppressWarnings("unchecked")
        Map<String, Object> copy = (Map<String, Object>) Tree.deepCopy(source);
        Tree.map(copy, "jobs").clear();
        assertFalse(Tree.map(source, "jobs").isEmpty());
    }
}
