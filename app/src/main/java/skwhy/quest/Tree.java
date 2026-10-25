package skwhy.quest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Accès typé et tolérant à un arbre de données (Map / List / String / Number / Boolean) venant d'un fichier YAML ou
 * d'un fichier NBT. Les booléens NBT sont des octets (0 / 1), d'où la tolérance de {@link #bool}.
 */
public final class Tree {

    private Tree() {}

    @SuppressWarnings("unchecked")
    public static Map<String, Object> map(Object value) {
        if (value instanceof Map<?, ?> m) return (Map<String, Object>) m;
        return Collections.emptyMap();
    }

    public static Map<String, Object> map(Map<String, Object> parent, String key) {
        return map(parent.get(key));
    }

    public static List<Object> list(Object value) {
        if (value instanceof List<?> l) return new ArrayList<>(l);
        return Collections.emptyList();
    }

    public static List<Object> list(Map<String, Object> parent, String key) {
        return list(parent.get(key));
    }

    public static List<Map<String, Object>> maps(Map<String, Object> parent, String key) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object o : list(parent, key)) if (o instanceof Map<?, ?>) out.add(map(o));
        return out;
    }

    public static List<String> strings(Map<String, Object> parent, String key) {
        List<String> out = new ArrayList<>();
        Object v = parent.get(key);
        if (v instanceof List<?> l) {
            for (Object o : l) if (o != null) out.add(String.valueOf(o));
        } else if (v instanceof String s) {
            out.add(s);
        }
        return out;
    }

    public static String str(Map<String, Object> parent, String key) {
        return str(parent, key, null);
    }

    public static String str(Map<String, Object> parent, String key, String def) {
        Object v = parent.get(key);
        return v == null ? def : String.valueOf(v);
    }

    public static int integer(Map<String, Object> parent, String key, int def) {
        Object v = parent.get(key);
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s) {
            try { return Integer.parseInt(s.trim()); } catch (NumberFormatException ignored) { }
        }
        return def;
    }

    public static double dbl(Map<String, Object> parent, String key, double def) {
        Object v = parent.get(key);
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof String s) {
            try { return Double.parseDouble(s.trim()); } catch (NumberFormatException ignored) { }
        }
        return def;
    }

    public static boolean bool(Map<String, Object> parent, String key, boolean def) {
        Object v = parent.get(key);
        return toBool(v, def);
    }

    public static boolean toBool(Object v, boolean def) {
        if (v instanceof Boolean b) return b;
        if (v instanceof Number n) return n.doubleValue() != 0;
        if (v instanceof String s) {
            if (s.equalsIgnoreCase("true")) return true;
            if (s.equalsIgnoreCase("false")) return false;
        }
        return def;
    }

    /** Lit un chemin séparé par des « ; » (ex. {@code jobs;chasseur;actif}) ; les index {@code name[3]} sont gérés. */
    public static Object path(Object root, String path) {
        Object current = root;
        for (String part : path.split(";")) {
            if (current == null) return null;
            String key = part;
            int index = -1;
            int bracket = part.indexOf('[');
            if (bracket > 0 && part.endsWith("]")) {
                key = part.substring(0, bracket);
                try { index = Integer.parseInt(part.substring(bracket + 1, part.length() - 1)); }
                catch (NumberFormatException e) { return null; }
            }
            if (current instanceof Map<?, ?> m) current = m.get(key);
            else return null;
            if (index >= 0) {
                if (current instanceof List<?> l && index < l.size()) current = l.get(index);
                else return null;
            }
        }
        return current;
    }

    /** Copie profonde modifiable. */
    public static Object deepCopy(Object value) {
        if (value instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) out.put(String.valueOf(e.getKey()), deepCopy(e.getValue()));
            return out;
        }
        if (value instanceof List<?> l) {
            List<Object> out = new ArrayList<>();
            for (Object o : l) out.add(deepCopy(o));
            return out;
        }
        return value;
    }
}
