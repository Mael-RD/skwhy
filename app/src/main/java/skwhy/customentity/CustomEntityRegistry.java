package skwhy.customentity;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.logging.Logger;

import skwhy.quest.YamlTree;

/**
 * Enregistrement des définitions d'entités custom : tous les fichiers {@code .yml} du dossier, chaque clé de premier niveau
 * étant l'id d'une entité. Un fichier invalide ne bloque pas les autres.
 */
public final class CustomEntityRegistry {

    private static final String EXAMPLE = """
        # Une entrée par entité custom : la clé est l'id, à écrire dans l'entité réelle (interaction) ou fausse.
        # Seul 'type' est obligatoire. Les couleurs utilisent le format de Skript : <green>, <#ff8800>, &a.
        example_wolf:
          type: wolf                # type d'entité du visuel (minecraft:wolf)
          name: "<aqua>Loup"        # nom affiché
          name_visible: true
          scale: 1.0                # taille du visuel (attribut scale)
          glowing: false
          invisible: false
          silent: true
          on_fire: false
          hitbox:                   # hitbox de l'interaction qui reçoit les clics
            width: 0.6
            height: 0.85
          equipment:                # main_hand, off_hand, head, chest, legs, feet, body
            head: iron_helmet
        example_mannequin:
          type: mannequin
          name: "<gold>Marchand"
          skin:
            value: ""               # texture base64 (à remplir)
            signature: ""
          metadata:                 # metadata brute avancée : index -> type (byte, int, long, float, string, boolean) et valeur
            "15":
              type: byte
              value: 0
        """;

    private final Logger logger;
    private final File folder;
    private volatile Map<String, CustomEntityDef> definitions = new TreeMap<>();
    /** Fichier qui contient chaque définition. */
    private final Map<String, File> files = new HashMap<>();

    public CustomEntityRegistry(File folder, Logger logger) {
        this.folder = folder;
        this.logger = logger;
    }

    public File folder() { return folder; }

    public CustomEntityDef get(String id) {
        return id == null ? null : definitions.get(normalize(id));
    }

    public List<String> ids() {
        return new ArrayList<>(definitions.keySet());
    }

    /**
     * Modifie une définition dans son fichier YAML (les commentaires du fichier ne sont pas conservés). Rien n'est écrit si le
     * résultat n'est pas une définition valide. Retourne {@code false} si l'id est inconnu ou la modification refusée.
     */
    public synchronized boolean edit(String id, Consumer<Map<String, Object>> editor) {
        id = normalize(id);
        File file = files.get(id);
        Map<String, Object> tree = file == null ? null : YamlTree.read(file);
        if (tree == null) return false;
        String key = null;
        for (String k : tree.keySet()) {
            if (normalize(k).equals(id)) key = k;
        }
        if (key == null || !(tree.get(key) instanceof Map<?, ?>)) return false;
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) tree.get(key);
        List<String> errors = new ArrayList<>();
        try {
            editor.accept(body);
        } catch (IllegalArgumentException e) {
            logger.warning("[CustomEntity] " + id + " : " + e.getMessage());
            return false;
        }
        CustomEntityDef def = CustomEntityDef.parse(id, body, errors);
        if (def == null || !errors.isEmpty()) {
            for (String error : errors) logger.warning("[CustomEntity] modification refusée, " + error);
            return false;
        }
        if (!YamlTree.write(file, tree)) return false;
        Map<String, CustomEntityDef> copy = new TreeMap<>(definitions);
        copy.put(id, def);
        definitions = copy;
        return true;
    }

    /** Ajoute une définition minimale ({@code type} seul) dans {@code created.yml}. {@code false} si l'id existe déjà ou est invalide. */
    public synchronized boolean create(String id, String type) {
        id = normalize(id);
        if (id.isEmpty() || type == null || type.isBlank() || definitions.containsKey(id)) return false;
        File file = new File(folder, "created.yml");
        Map<String, Object> tree = file.exists() ? YamlTree.read(file) : new LinkedHashMap<>();
        if (tree == null) return false;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", type);
        CustomEntityDef def = CustomEntityDef.parse(id, body, new ArrayList<>());
        if (def == null) return false;
        tree.put(id, body);
        folder.mkdirs();
        if (!YamlTree.write(file, tree)) return false;
        files.put(id, file);
        Map<String, CustomEntityDef> copy = new TreeMap<>(definitions);
        copy.put(id, def);
        definitions = copy;
        return true;
    }

    /** Supprime une définition de son fichier. {@code false} si l'id est inconnu. */
    public synchronized boolean remove(String id) {
        id = normalize(id);
        File file = files.get(id);
        Map<String, Object> tree = file == null ? null : YamlTree.read(file);
        if (tree == null) return false;
        String target = id;
        tree.keySet().removeIf(k -> normalize(k).equals(target));
        if (!YamlTree.write(file, tree)) return false;
        files.remove(id);
        Map<String, CustomEntityDef> copy = new TreeMap<>(definitions);
        copy.remove(id);
        definitions = copy;
        return true;
    }

    public static String normalize(String id) {
        return id.trim().toLowerCase(Locale.ROOT);
    }

    /** Relit le dossier. Le premier lancement y écrit un fichier d'exemple. Retourne le nombre de définitions chargées. */
    public synchronized int reload() {
        if (!folder.exists()) {
            folder.mkdirs();
            try {
                Files.writeString(new File(folder, "example.yml").toPath(), EXAMPLE, StandardCharsets.UTF_8);
            } catch (IOException e) {
                logger.warning("[CustomEntity] Impossible d'écrire le fichier d'exemple : " + e.getMessage());
            }
        }
        Map<String, CustomEntityDef> loaded = new TreeMap<>();
        Map<String, File> loadedFiles = new HashMap<>();
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));
        if (files != null) {
            Arrays.sort(files, Comparator.comparing(File::getName));
            for (File file : files) load(file, loaded, loadedFiles);
        }
        synchronized (this) {
            this.files.clear();
            this.files.putAll(loadedFiles);
            definitions = loaded;
        }
        logger.info("[CustomEntity] " + loaded.size() + " définition(s) chargée(s) depuis " + folder.getName() + ".");
        return loaded.size();
    }

    private void load(File file, Map<String, CustomEntityDef> into, Map<String, File> intoFiles) {
        Map<String, Object> tree = YamlTree.read(file);
        if (tree == null) {
            logger.warning("[CustomEntity] " + file.getName() + " : YAML illisible ou invalide, fichier ignoré.");
            return;
        }
        for (Map.Entry<String, Object> entry : tree.entrySet()) {
            String id = normalize(entry.getKey());
            if (!(entry.getValue() instanceof Map<?, ?> raw)) {
                logger.warning("[CustomEntity] " + file.getName() + " : '" + entry.getKey() + "' n'est pas une définition, ignorée.");
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) raw;
            List<String> errors = new ArrayList<>();
            CustomEntityDef def = CustomEntityDef.parse(id, body, errors);
            for (String error : errors) logger.warning("[CustomEntity] " + file.getName() + " : " + error);
            if (def == null) continue;
            if (into.containsKey(id)) logger.warning("[CustomEntity] " + file.getName() + " : id '" + id + "' déjà défini, remplacé.");
            into.put(id, def);
            intoFiles.put(id, file);
        }
    }
}
