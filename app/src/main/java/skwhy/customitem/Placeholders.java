package skwhy.customitem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Remplacement de {@code {clé}} dans les textes d'une définition, et fusion des lignes de lore ({@code [lore]}). */
public final class Placeholders {

    private Placeholders() {}

    /** Copie de l'arbre dont chaque texte (valeurs, pas les clés) a ses {@code {clé}} remplacés. */
    public static Map<String, Object> apply(Map<String, Object> tree, Map<String, String> values) {
        if (values.isEmpty()) return tree;
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : tree.entrySet()) out.put(e.getKey(), applyValue(e.getValue(), values));
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Object applyValue(Object value, Map<String, String> values) {
        if (value instanceof String s) return replace(s, values);
        if (value instanceof Map<?, ?> m) return apply((Map<String, Object>) m, values);
        if (value instanceof List<?> l) {
            List<Object> out = new ArrayList<>();
            for (Object o : l) out.add(applyValue(o, values));
            return out;
        }
        return value;
    }

    public static String replace(String text, Map<String, String> values) {
        for (Map.Entry<String, String> e : values.entrySet()) text = text.replace("{" + e.getKey() + "}", e.getValue());
        return text;
    }

    /**
     * Lore d'un item avec des lignes ajoutées : les lignes avant {@code [lore]} passent avant le lore de l'item, celles après
     * viennent après ; sans {@code [lore]}, tout vient après.
     */
    public static List<String> mergeLore(List<String> itemLore, List<String> lines) {
        List<String> before = new ArrayList<>();
        List<String> after = new ArrayList<>();
        boolean seen = !lines.contains("[lore]");
        for (String line : lines) {
            if (line.equals("[lore]")) seen = true;
            else if (seen) after.add(line);
            else before.add(line);
        }
        List<String> out = new ArrayList<>(before);
        out.addAll(itemLore);
        out.addAll(after);
        return out;
    }
}
