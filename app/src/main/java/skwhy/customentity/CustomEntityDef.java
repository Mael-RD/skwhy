package skwhy.customentity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Définition d'une entité custom, lue dans un fichier YAML du dossier {@code custom-entities}. Elle décrit tout ce que
 * le plugin affiche par packet : l'entité réelle ou fausse ne porte que l'id de cette définition.
 */
public final class CustomEntityDef {

    /** Une valeur brute de metadata (index, type, valeur) pour les cas que les clés ci-dessous ne couvrent pas. */
    public record RawMetadata(int index, String type, Object value) {}

    private static final List<String> SLOTS = List.of("main_hand", "off_hand", "head", "chest", "legs", "feet", "body");
    private static final List<String> RAW_TYPES = List.of("byte", "int", "long", "float", "string", "boolean");

    private final String id;
    private final String type;
    /** Arbre YAML d'origine (copie), pour recalculer la définition avec des surcharges. */
    private Map<String, Object> source = new LinkedHashMap<>();
    private String name;
    private String ai;
    private boolean nameVisible = true;
    private double scale = 1.0;
    private boolean glowing;
    private boolean invisible;
    private boolean silent = true;
    private boolean onFire;
    private double hitboxWidth = 0.6;
    private double hitboxHeight = 1.8;
    private String skinValue;
    private String skinSignature;
    private final Map<String, String> equipment = new LinkedHashMap<>();
    private final List<RawMetadata> metadata = new ArrayList<>();

    private CustomEntityDef(String id, String type) {
        this.id = id;
        this.type = type;
    }

    public String id() { return id; }
    /** Type d'entité du visuel, sans namespace ({@code wolf}, {@code mannequin}...). */
    public String type() { return type; }
    /** Nom au format Skript ({@code <green>}, {@code &a}...), ou {@code null}. */
    public String name() { return name; }
    /** Type du mob dont l'IA vanilla pilote l'entité (sans namespace), ou {@code null} pour une entité immobile. */
    public String ai() { return ai; }
    public boolean nameVisible() { return nameVisible; }
    public double scale() { return scale; }
    public boolean glowing() { return glowing; }
    public boolean invisible() { return invisible; }
    public boolean silent() { return silent; }
    public boolean onFire() { return onFire; }
    public double hitboxWidth() { return hitboxWidth; }
    public double hitboxHeight() { return hitboxHeight; }
    public String skinValue() { return skinValue; }
    public String skinSignature() { return skinSignature; }
    /** Emplacement ({@code main_hand}, {@code head}...) → nom de matériau. */
    public Map<String, String> equipment() { return equipment; }
    public List<RawMetadata> metadata() { return metadata; }

    /**
     * Construit une définition à partir d'un arbre YAML.
     *
     * @param errors reçoit un message par problème rencontré (une valeur invalide est ignorée, un type manquant rejette la définition)
     * @return la définition, ou {@code null} si elle n'a pas de {@code type}
     */
    public static CustomEntityDef parse(String id, Map<String, Object> tree, List<String> errors) {
        String type = string(tree.get("type"));
        if (type == null || type.isBlank()) {
            errors.add(id + " : 'type' manquant");
            return null;
        }
        type = type.trim().toLowerCase(Locale.ROOT);
        if (type.startsWith("minecraft:")) type = type.substring("minecraft:".length());
        CustomEntityDef def = new CustomEntityDef(id, type);
        def.source = deepCopy(tree);

        def.name = string(tree.get("name"));
        String ai = string(tree.get("ai"));
        if (ai != null && !ai.isBlank()) {
            ai = ai.trim().toLowerCase(Locale.ROOT);
            def.ai = ai.startsWith("minecraft:") ? ai.substring("minecraft:".length()) : ai;
        }
        def.nameVisible = bool(tree.get("name_visible"), true);
        def.scale = number(tree.get("scale"), 1.0);
        if (def.scale <= 0 || def.scale > 16) {
            errors.add(id + " : 'scale' doit être entre 0 (exclu) et 16, valeur " + def.scale + " remplacée par 1");
            def.scale = 1.0;
        }
        def.glowing = bool(tree.get("glowing"), false);
        def.invisible = bool(tree.get("invisible"), false);
        def.silent = bool(tree.get("silent"), true);
        def.onFire = bool(tree.get("on_fire"), false);

        if (tree.get("hitbox") instanceof Map<?, ?> hitbox) {
            def.hitboxWidth = number(hitbox.get("width"), def.hitboxWidth);
            def.hitboxHeight = number(hitbox.get("height"), def.hitboxHeight);
            if (def.hitboxWidth <= 0 || def.hitboxHeight <= 0) {
                errors.add(id + " : 'hitbox' doit avoir une largeur et une hauteur positives, valeurs par défaut utilisées");
                def.hitboxWidth = 0.6;
                def.hitboxHeight = 1.8;
            }
        }

        if (tree.get("skin") instanceof Map<?, ?> skin) {
            def.skinValue = string(skin.get("value"));
            def.skinSignature = string(skin.get("signature"));
            if (def.skinValue == null || def.skinValue.isBlank()) {
                errors.add(id + " : 'skin.value' manquant, skin ignoré");
                def.skinValue = null;
                def.skinSignature = null;
            }
        }

        if (tree.get("equipment") instanceof Map<?, ?> equipment) {
            for (Map.Entry<?, ?> e : equipment.entrySet()) {
                String slot = String.valueOf(e.getKey()).toLowerCase(Locale.ROOT);
                String item = string(e.getValue());
                if (!SLOTS.contains(slot)) {
                    errors.add(id + " : emplacement d'équipement inconnu '" + slot + "' (attendus : " + String.join(", ", SLOTS) + ")");
                } else if (item != null && !item.isBlank()) {
                    def.equipment.put(slot, item.trim());
                }
            }
        }

        if (tree.get("metadata") instanceof Map<?, ?> raw) {
            for (Map.Entry<?, ?> e : raw.entrySet()) {
                RawMetadata meta = rawMetadata(id, String.valueOf(e.getKey()), e.getValue(), errors);
                if (meta != null) def.metadata.add(meta);
            }
        }
        return def;
    }

    /** Nature d'un paramètre : {@code "number"}, {@code "boolean"}, {@code "text"}, ou {@code null} si le chemin n'existe pas. */
    public static String kindOf(String path) {
        return switch (path) {
            case "scale", "hitbox.width", "hitbox.height" -> "number";
            case "name_visible", "glowing", "invisible", "silent", "on_fire" -> "boolean";
            case "type", "name", "ai", "skin.value", "skin.signature" -> "text";
            default -> {
                if (path.startsWith("equipment.") && SLOTS.contains(path.substring("equipment.".length()))) yield "text";
                if (path.startsWith("metadata.") && isIndex(path.substring("metadata.".length()))) yield "text";
                yield null;
            }
        };
    }

    private static boolean isIndex(String text) {
        try {
            int index = Integer.parseInt(text);
            return index >= 0 && index <= 254;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** Chemins de paramètres fixes (hors {@code equipment.*} et {@code metadata.*}), pour les messages d'erreur. */
    public static List<String> fixedPaths() {
        return List.of("type", "ai", "name", "name_visible", "scale", "glowing", "invisible", "silent", "on_fire",
            "hitbox.width", "hitbox.height", "skin.value", "skin.signature");
    }

    public static List<String> equipmentSlots() { return SLOTS; }

    /**
     * Vérifie qu'une surcharge est acceptable (chemin connu, valeur du bon type, metadata de la forme {@code type:valeur}).
     *
     * @return un message d'erreur, ou {@code null} si elle est valide
     */
    public static String checkOverride(String path, Object value) {
        String kind = kindOf(path);
        if (kind == null) return "paramètre inconnu '" + path + "'";
        switch (kind) {
            case "number" -> {
                if (!(value instanceof Number n)) return "'" + path + "' attend un nombre";
                if (path.equals("scale") && (n.doubleValue() <= 0 || n.doubleValue() > 16)) return "'scale' doit être entre 0 (exclu) et 16";
                if (path.startsWith("hitbox.") && n.doubleValue() <= 0) return "'" + path + "' doit être positif";
            }
            case "boolean" -> {
                if (!(value instanceof Boolean)) return "'" + path + "' attend un booléen";
            }
            default -> {
                if (!(value instanceof String)) return "'" + path + "' attend un texte";
                if (path.startsWith("metadata.")) {
                    try {
                        rawMetadataTree((String) value);
                    } catch (IllegalArgumentException e) {
                        return e.getMessage();
                    }
                }
            }
        }
        return null;
    }

    /**
     * Copie de cette définition où les surcharges remplacent les paramètres du fichier (un chemin à {@code null} reprend sa valeur
     * par défaut). Une surcharge refusée est ignorée et signalée dans {@code errors}.
     */
    public CustomEntityDef withOverrides(Map<String, Object> overrides, List<String> errors) {
        if (overrides.isEmpty()) return this;
        Map<String, Object> tree = deepCopy(source);
        for (Map.Entry<String, Object> e : overrides.entrySet()) {
            String problem = e.getValue() == null ? null : checkOverride(e.getKey(), e.getValue());
            if (problem != null) {
                errors.add(id + " : " + problem);
            } else if (e.getValue() == null) {
                removePath(tree, e.getKey());
            } else {
                setPath(tree, e.getKey(), e.getValue());
            }
        }
        CustomEntityDef result = parse(id, tree, errors);
        return result == null ? this : result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> deepCopy(Map<?, ?> map) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : map.entrySet()) {
            Object value = e.getValue() instanceof Map<?, ?> nested ? deepCopy(nested) : e.getValue();
            copy.put(String.valueOf(e.getKey()), value);
        }
        return copy;
    }

    /**
     * Valeur d'un paramètre par son chemin YAML ({@code scale}, {@code hitbox.width}, {@code skin.value}, {@code equipment.head},
     * {@code metadata.15}...). Une {@code String}, un {@code Double} ou un {@code Boolean} ; {@code null} si le paramètre n'est pas renseigné.
     * Une metadata s'écrit {@code type:valeur} (ex. {@code byte:1}).
     */
    public Object value(String path) {
        if (path.startsWith("equipment.")) return equipment.get(path.substring("equipment.".length()));
        if (path.startsWith("metadata.")) {
            for (RawMetadata raw : metadata) {
                if (String.valueOf(raw.index()).equals(path.substring("metadata.".length()))) return raw.type() + ":" + stringOf(raw.value());
            }
            return null;
        }
        return switch (path) {
            case "type" -> type;
            case "name" -> name;
            case "ai" -> ai;
            case "name_visible" -> nameVisible;
            case "scale" -> scale;
            case "glowing" -> glowing;
            case "invisible" -> invisible;
            case "silent" -> silent;
            case "on_fire" -> onFire;
            case "hitbox.width" -> hitboxWidth;
            case "hitbox.height" -> hitboxHeight;
            case "skin.value" -> skinValue;
            case "skin.signature" -> skinSignature;
            default -> null;
        };
    }

    private static String stringOf(Object value) {
        if (value instanceof Double d && d == Math.rint(d) && !Double.isInfinite(d)) return String.valueOf(d.longValue());
        return String.valueOf(value);
    }

    /**
     * Écrit un paramètre dans l'arbre YAML d'une définition, en créant les sections intermédiaires ({@code hitbox}, {@code skin}...).
     * Une metadata se donne sous la forme {@code type:valeur}.
     *
     * @throws IllegalArgumentException si la valeur d'une metadata n'a pas la forme attendue
     */
    @SuppressWarnings("unchecked")
    public static void setPath(Map<String, Object> body, String path, Object value) {
        Object stored = path.startsWith("metadata.") ? rawMetadataTree(String.valueOf(value)) : value;
        String[] parts = path.split("\\.", 2);
        if (parts.length == 1) {
            body.put(path, stored);
            return;
        }
        Object section = body.get(parts[0]);
        Map<String, Object> map;
        if (section instanceof Map<?, ?> existing) {
            map = (Map<String, Object>) existing;
        } else {
            map = new LinkedHashMap<>();
            body.put(parts[0], map);
        }
        map.put(parts[1], stored);
    }

    /** Retire un paramètre (il reprend sa valeur par défaut) et la section parente si elle devient vide. */
    public static void removePath(Map<String, Object> body, String path) {
        if (path.equals("skin.value")) path = "skin"; // une signature seule n'a pas de sens
        String[] parts = path.split("\\.", 2);
        if (parts.length == 1) {
            body.remove(path);
            return;
        }
        if (body.get(parts[0]) instanceof Map<?, ?> section) {
            section.remove(parts[1]);
            if (section.isEmpty()) body.remove(parts[0]);
        }
    }

    private static Map<String, Object> rawMetadataTree(String text) {
        int colon = text.indexOf(':');
        if (colon <= 0) throw new IllegalArgumentException("metadata attendue sous la forme type:valeur, reçu '" + text + "'");
        String type = text.substring(0, colon).trim().toLowerCase(Locale.ROOT);
        String raw = text.substring(colon + 1).trim();
        if (!RAW_TYPES.contains(type)) throw new IllegalArgumentException("type de metadata inconnu '" + type + "'");
        Object value;
        try {
            value = switch (type) {
                case "byte", "int", "long" -> Long.parseLong(raw);
                case "float" -> Double.parseDouble(raw);
                case "boolean" -> Boolean.parseBoolean(raw);
                default -> raw;
            };
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("valeur '" + raw + "' invalide pour une metadata " + type);
        }
        Map<String, Object> tree = new LinkedHashMap<>();
        tree.put("type", type);
        tree.put("value", value);
        return tree;
    }

    private static RawMetadata rawMetadata(String id, String key, Object value, List<String> errors) {
        int index;
        try {
            index = Integer.parseInt(key.trim());
        } catch (NumberFormatException e) {
            errors.add(id + " : l'index de metadata '" + key + "' n'est pas un entier");
            return null;
        }
        if (index < 0 || index > 254 || !(value instanceof Map<?, ?> map)) {
            errors.add(id + " : metadata '" + key + "' invalide (index 0-254 et clés 'type' et 'value' attendus)");
            return null;
        }
        String type = string(map.get("type"));
        if (type == null || !RAW_TYPES.contains(type.toLowerCase(Locale.ROOT)) || map.get("value") == null) {
            errors.add(id + " : metadata '" + key + "' : 'type' doit être " + String.join(", ", RAW_TYPES) + " et 'value' renseignée");
            return null;
        }
        return new RawMetadata(index, type.toLowerCase(Locale.ROOT), map.get("value"));
    }

    private static String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static boolean bool(Object value, boolean fallback) {
        if (value instanceof Boolean b) return b;
        if (value instanceof String s) return s.equalsIgnoreCase("true") || (!s.equalsIgnoreCase("false") && fallback);
        return fallback;
    }

    private static double number(Object value, double fallback) {
        if (value instanceof Number n) return n.doubleValue();
        if (value instanceof String s) {
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException e) {
                return fallback;
            }
        }
        return fallback;
    }
}
