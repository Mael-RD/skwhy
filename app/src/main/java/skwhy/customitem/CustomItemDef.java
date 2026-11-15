package skwhy.customitem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Définition d'un item custom : le contenu d'un fichier {@code custom-items/<id>.yml}. Il n'y a pas de clé de premier niveau
 * pour l'id (c'est le nom du fichier). Clés connues : {@code material_id} (obligatoire), {@code name}, {@code lore},
 * {@code data} (custom_data), {@code stack}, {@code unbreakable}, {@code glint}, {@code model}, {@code color}, {@code attributes},
 * {@code enchantments}, {@code potion}, {@code tooltip_display}, {@code equippable}, {@code consumable}, {@code use_cooldown},
 * {@code fireworks}, {@code swing_animation}, {@code version}. Tout composant sans clé dédiée est conservé tel quel dans
 * {@code components} (clé de composant → valeur), donc aucun NBT n'est perdu.
 */
public final class CustomItemDef {

    private final String id;
    private final String materialId;
    private final Map<String, Object> tree;

    private CustomItemDef(String id, String materialId, Map<String, Object> tree) {
        this.id = id;
        this.materialId = materialId;
        this.tree = tree;
    }

    public String id() { return id; }

    /** Matériau, sans {@code minecraft:} pour un item vanilla ({@code stone}). */
    public String materialId() { return materialId; }

    /** Numéro de version, incrémenté à chaque modification enregistrée (0 si absent). */
    public int version() {
        return tree.get("version") instanceof Number n ? n.intValue() : 0;
    }

    /** Copie de l'arbre YAML. */
    public Map<String, Object> tree() {
        return deepCopy(tree);
    }

    /**
     * @param errors reçoit un message par problème
     * @return la définition, ou {@code null} si {@code material_id} manque ou si le fichier est de la forme « clé = id » (deux couches)
     */
    public static CustomItemDef parse(String id, Map<String, Object> tree, List<String> errors) {
        if (!CustomItemIds.isValid(id)) {
            errors.add("id invalide '" + id + "'");
            return null;
        }
        Object material = tree.get("material_id");
        if (!(material instanceof String text) || text.isBlank()) {
            if (tree.size() == 1 && tree.values().iterator().next() instanceof Map<?, ?>) {
                errors.add(id + " : l'id est le nom du fichier, pas une clé du contenu ('material_id' attendu au premier niveau)");
            } else {
                errors.add(id + " : 'material_id' manquant");
            }
            return null;
        }
        Map<String, Object> copy = deepCopy(tree);
        String normalized = text.trim().toLowerCase(java.util.Locale.ROOT);
        if (normalized.startsWith("minecraft:")) normalized = normalized.substring("minecraft:".length());
        copy.put("material_id", normalized);
        for (String key : List.of("name", "stack", "model")) {
            Object v = copy.get(key);
            if (v != null && v instanceof Map<?, ?>) errors.add(id + " : '" + key + "' doit être une valeur simple");
        }
        if (copy.containsKey("lore") && !(copy.get("lore") instanceof List<?>)) {
            errors.add(id + " : 'lore' doit être une liste de lignes");
            copy.remove("lore");
        }
        if (copy.containsKey("components") && !(copy.get("components") instanceof Map<?, ?>)) {
            errors.add(id + " : 'components' doit être une table");
            copy.remove("components");
        }
        if (copy.containsKey("data") && !(copy.get("data") instanceof Map<?, ?>)) {
            errors.add(id + " : 'data' doit être une table");
            copy.remove("data");
        }
        return new CustomItemDef(id, normalized, copy);
    }

    /** Même contenu, hors {@code version}. */
    public boolean sameContent(Map<String, Object> other) {
        Map<String, Object> a = deepCopy(tree);
        Map<String, Object> b = deepCopy(other);
        a.remove("version");
        b.remove("version");
        return Objects.equals(a, b);
    }

    public CustomItemDef withVersion(int version) {
        Map<String, Object> copy = deepCopy(tree);
        copy.put("version", version);
        return new CustomItemDef(id, materialId, copy);
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> deepCopy(Map<String, Object> source) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : source.entrySet()) out.put(e.getKey(), copyValue(e.getValue()));
        return out;
    }

    private static Object copyValue(Object value) {
        if (value instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) out.put(String.valueOf(e.getKey()), copyValue(e.getValue()));
            return out;
        }
        if (value instanceof List<?> l) {
            List<Object> out = new ArrayList<>();
            for (Object o : l) out.add(copyValue(o));
            return out;
        }
        return value;
    }
}
