package skwhy.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

class ConfigMergerTest {

    private static List<String> l(String text) {
        return List.of(text.split("\n", -1));
    }

    private static String merge(String current, String defaults) {
        return String.join("\n", ConfigMerger.merge(l(current), l(defaults)).lines());
    }

    private static List<String> added(String current, String defaults) {
        return ConfigMerger.merge(l(current), l(defaults)).addedKeys();
    }

    @Test
    void nothingMissingLeavesFileUntouched() {
        String c = "a: 1\n# hi\nb:\n  c: 2\n";
        assertEquals(c, merge(c, c));
        assertTrue(added(c, c).isEmpty());
    }

    @Test
    void missingRootKeyGoesAfterItsPredecessor() {
        String d = "a: 1\nb: 2\nc: 3\n";
        assertEquals("a: 1\nb: 2\nc: 3\n", merge("a: 1\nc: 3\n", d));
        assertEquals(List.of("b"), added("a: 1\nc: 3\n", d));
    }

    @Test
    void missingKeyAfterBlockPredecessorGoesAfterTheWholeBlock() {
        String d = "a:\n  x: 1\n  y: 2\nb: 3\n";
        assertEquals("a:\n  x: 1\n  y: 2\nb: 3\n", merge("a:\n  x: 1\n  y: 2\n", d));
    }

    @Test
    void missingChildGoesInsideItsParentNotAtEndOfFile() {
        String d = "m:\n  a: 1\n  b: 2\n  c: 3\nz: 9\n";
        String c = "m:\n  a: 1\n  c: 3\nz: 9\n";
        assertEquals(d, merge(c, d));
    }

    @Test
    void firstChildMissingIsInsertedBeforeNextSibling() {
        String d = "m:\n  a: 1\n  b: 2\nz: 9\n";
        assertEquals(d, merge("m:\n  b: 2\nz: 9\n", d));
    }

    @Test
    void sameKeyNameInDifferentBlocksDoesNotConfuse() {
        String d = "t:\n  enabled: true\n  x: 1\nb:\n  enabled: true\n  y: 2\n";
        String c = "t:\n  enabled: true\nb:\n  enabled: false\n";
        String r = merge(c, d);
        assertEquals("t:\n  enabled: true\n  x: 1\nb:\n  enabled: false\n  y: 2\n", r);
    }

    @Test
    void wholeMissingBlockIsInsertedOnceWithoutDuplicates() {
        String d = "a: 1\n\n# Bloc\nb:\n  # c\n  c: 1\n  d: 2\ne: 5\n";
        String c = "a: 1\ne: 5\n";
        assertEquals(d, merge(c, d));
        assertEquals(List.of("b"), added(c, d));
    }

    @Test
    void insertionDoesNotSwallowCommentsOfNextKey() {
        String d = "a: 1\nb: 2\n# about c\nc: 3\n";
        String c = "a: 1\n# about c\nc: 3\n";
        assertEquals(d, merge(c, d));
    }

    @Test
    void idempotent() {
        String d = "m:\n  a: 1\n  b: 2\n# n\nn:\n  - x\n  - y\nq: 1\n";
        String once = merge("q: 1\n", d);
        assertEquals(once, merge(once, d));
        assertEquals(d, once);
    }

    @Test
    void userValuesAndFourSpaceIndentationAreKept() {
        String d = "m:\n  a: 1\n  b: 2\n";
        String c = "m:\n    a: 99\n";
        assertEquals("m:\n    a: 99\n    b: 2\n", merge(c, d));
    }

    @Test
    void listsAreTreatedAsPartOfTheirKey() {
        String d = "l:\n  - a\n  - b\nk: 1\n";
        assertEquals("l:\n- a\nk: 1\n", merge("l:\n- a\nk: 1\n", d));
        assertEquals("l:\n  - a\nk: 1\n", merge("l:\n  - a\nk: 1\n", d));
        assertEquals("l:\n  - a\n  - b\nk: 1\n", merge("l:\n  - a\n  - b\n", d));
    }

    @Test
    void valuesContainingColonsAndQuotedKeys() {
        String d = "url: \"http://x:80\"\n\"q\": 1\nr: 2\n";
        assertEquals(d, merge("url: \"http://x:80\"\nr: 2\n", d));
    }

    @Test
    void emptyCurrentFileGetsEverything() {
        String d = "# h\na: 1\nb:\n  c: 2\n";
        assertEquals(d.strip(), merge("", d).strip());
    }

    @Test
    void realDefaultConfigAgainstDegradedVersions() throws IOException {
        String d;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("config.yml")) {
            d = new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        }
        // L'ancienne config (avant chunkpath / chunk_path / request) doit retrouver exactement les defaults
        String old = "modules:\n  fake_display: true\n  fake_pathfinding: true\n  random_stuff: true\n  api: true\n\n"
                + "api_rest:\n  port: 8080\n  key: \"abc\"\n  use_https: true\n";
        String r = merge(old, d);
        String again = merge(r, d);
        assertEquals(r, again);
        assertTrue(r.contains("port: 8080") && r.contains("key: \"abc\""));
        assertEquals(1, count(r, "chunk_path:"));
        assertEquals(1, count(r, "request:"));
        assertEquals(1, count(r, "chunkpath:"));
        assertEquals(1, count(r, "certificate:"));
        // chunk_path et request restent dans `modules`
        assertTrue(r.indexOf("chunk_path:") < r.indexOf("api_rest:"));
        // certificate / private_key restent dans api_rest, avant chunkpath
        assertTrue(r.indexOf("certificate:") > r.indexOf("api_rest:") && r.indexOf("certificate:") < r.indexOf("chunkpath:"));

        // fichier vide de cles : tout revient, une seule fois
        String r2 = merge("modules:\n", d);
        assertEquals(merge(r2, d), r2);
        assertEquals(1, count(r2, "port:"));
        // supprimer au hasard chaque ligne de cle : une seule ligne ajoutee, en double jamais
        List<String> dl = l(d);
        for (int i = 0; i < dl.size(); i++) {
            if (!dl.get(i).contains(": ") || dl.get(i).strip().startsWith("#")) continue; // un commentaire n'est pas une cle
            List<String> cur = new java.util.ArrayList<>(dl);
            cur.remove(i);
            List<String> res = ConfigMerger.merge(cur, dl).lines();
            assertEquals(dl.size(), res.size(), "ligne " + i + " : " + dl.get(i));
            assertEquals(dl.stream().sorted().toList(), res.stream().sorted().toList(), "ligne " + i);
            if (!dl.get(i - 1).strip().startsWith("#") && !dl.get(i - 1).isBlank()) {
                assertEquals(dl, res, "position de la ligne " + i + " : " + dl.get(i));
            }
        }
    }

    private static int count(String s, String sub) {
        int n = 0;
        for (int i = s.indexOf(sub); i >= 0; i = s.indexOf(sub, i + 1)) n++;
        return n;
    }
}
