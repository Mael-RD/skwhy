package skwhy.quest;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** Lecture et écriture d'un arbre de données au format YAML. */
public final class YamlTree {

    private YamlTree() {}

    public static Map<String, Object> read(File file) {
        if (file == null || !file.isFile()) return null;
        try {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(file);
            return toMap(yaml);
        } catch (IOException | org.bukkit.configuration.InvalidConfigurationException e) {
            return null;
        }
    }

    public static boolean write(File file, Map<String, Object> tree) {
        try {
            File parent = file.getParentFile();
            if (parent != null) parent.mkdirs();
            YamlConfiguration yaml = new YamlConfiguration();
            for (Map.Entry<String, Object> e : tree.entrySet()) yaml.set(escape(e.getKey()), toYaml(e.getValue()));
            yaml.save(file);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** Les clés contenant un point sont des chemins pour Bukkit : on les remplace pour pouvoir les relire. */
    private static String escape(String key) {
        return key.replace(".", "․");
    }

    private static String unescape(String key) {
        return key.replace("․", ".");
    }

    private static Object toYaml(Object value) {
        if (value instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) out.put(escape(String.valueOf(e.getKey())), toYaml(e.getValue()));
            return out;
        }
        if (value instanceof List<?> l) {
            List<Object> out = new ArrayList<>();
            for (Object o : l) out.add(toYaml(o));
            return out;
        }
        return value;
    }

    private static Map<String, Object> toMap(ConfigurationSection section) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) out.put(unescape(key), convert(section.get(key)));
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Object convert(Object value) {
        if (value instanceof ConfigurationSection s) return toMap(s);
        if (value instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : ((Map<Object, Object>) m).entrySet()) out.put(unescape(String.valueOf(e.getKey())), convert(e.getValue()));
            return out;
        }
        if (value instanceof List<?> l) {
            List<Object> out = new ArrayList<>();
            for (Object o : l) out.add(convert(o));
            return out;
        }
        return value;
    }
}
