package skwhy.quest.editor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Compte les différences entre deux arbres de données (valeur par valeur) pour résumer des modifications. */
public final class TreeDiff {

    private TreeDiff() {}

    /** Quantité de modifications : valeurs ajoutées, retirées et changées. */
    public record Stat(int added, int removed, int modified) {
        public int total() { return added + removed + modified; }

        public Stat plus(Stat other) {
            return new Stat(added + other.added, removed + other.removed, modified + other.modified);
        }
    }

    public static Stat diff(Object before, Object after) {
        int[] counts = new int[3];
        walk(EditorJson.toElement(before), EditorJson.toElement(after), counts);
        return new Stat(counts[0], counts[1], counts[2]);
    }

    /** Clés de premier niveau dont le contenu diffère (dans l'ordre alphabétique). */
    public static List<String> changedSections(Map<String, Object> before, Map<String, Object> after) {
        JsonObject a = EditorJson.toElement(before).getAsJsonObject();
        JsonObject b = EditorJson.toElement(after).getAsJsonObject();
        Set<String> keys = new TreeSet<>(a.keySet());
        keys.addAll(b.keySet());
        List<String> out = new ArrayList<>();
        for (String key : keys) {
            JsonElement x = a.get(key);
            JsonElement y = b.get(key);
            if (x == null ? y != null : !x.equals(y)) out.add(key);
        }
        return out;
    }

    private static void walk(JsonElement a, JsonElement b, int[] c) {
        if (a == null && b == null) return;
        if (a == null) { c[0] += leaves(b); return; }
        if (b == null) { c[1] += leaves(a); return; }
        if (a.isJsonObject() && b.isJsonObject()) {
            JsonObject x = a.getAsJsonObject();
            JsonObject y = b.getAsJsonObject();
            for (String key : x.keySet()) walk(x.get(key), y.get(key), c);
            for (String key : y.keySet()) if (!x.has(key)) c[0] += leaves(y.get(key));
        } else if (a.isJsonArray() && b.isJsonArray()) {
            JsonArray x = a.getAsJsonArray();
            JsonArray y = b.getAsJsonArray();
            int common = Math.min(x.size(), y.size());
            for (int i = 0; i < common; i++) walk(x.get(i), y.get(i), c);
            for (int i = common; i < x.size(); i++) c[1] += leaves(x.get(i));
            for (int i = common; i < y.size(); i++) c[0] += leaves(y.get(i));
        } else if (!a.equals(b)) {
            c[2]++;
        }
    }

    private static int leaves(JsonElement e) {
        if (e.isJsonObject()) {
            int n = 0;
            for (String key : e.getAsJsonObject().keySet()) n += leaves(e.getAsJsonObject().get(key));
            return Math.max(1, n);
        }
        if (e.isJsonArray()) {
            int n = 0;
            for (JsonElement x : e.getAsJsonArray()) n += leaves(x);
            return Math.max(1, n);
        }
        return 1;
    }
}
