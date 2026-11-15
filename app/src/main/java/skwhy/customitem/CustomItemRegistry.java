package skwhy.customitem;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;

import skwhy.quest.YamlTree;

/**
 * Définitions d'items custom : un fichier {@code <id>.yml} par item, directement dans le dossier (pas de sous-dossier, pas
 * de regroupement). Un fichier invalide ne bloque pas les autres.
 */
public final class CustomItemRegistry {

    private final Logger logger;
    private final File folder;
    private volatile Map<String, CustomItemDef> definitions = new TreeMap<>();

    public CustomItemRegistry(File folder, Logger logger) {
        this.folder = folder;
        this.logger = logger;
    }

    public File folder() { return folder; }

    /** @param id un id quelconque (espaces, majuscules) : il est normalisé */
    public CustomItemDef get(String id) {
        return id == null ? null : definitions.get(CustomItemIds.normalize(id));
    }

    public boolean has(String id) {
        return get(id) != null;
    }

    public List<String> ids() {
        return new ArrayList<>(definitions.keySet());
    }

    public File fileOf(String id) {
        return new File(folder, CustomItemIds.normalize(id) + ".yml");
    }

    /**
     * Écrit l'item {@code id}. La version vaut celle du fichier existant, +1 si le contenu a changé (0 pour un nouvel item) ;
     * {@code force} incrémente même sans changement.
     *
     * @return la définition enregistrée, ou {@code null} si l'id ou le contenu est invalide
     */
    public synchronized CustomItemDef save(String id, Map<String, Object> tree, boolean force) {
        id = CustomItemIds.normalize(id);
        List<String> errors = new ArrayList<>();
        CustomItemDef def = CustomItemDef.parse(id, tree, errors);
        if (def == null) {
            for (String error : errors) logger.warning("[CustomItem] " + error);
            return null;
        }
        CustomItemDef old = definitions.get(id);
        int version = old == null ? def.version() : old.version() + (force || !old.sameContent(def.tree()) ? 1 : 0);
        def = def.withVersion(version);
        if (!YamlTree.write(fileOf(id), def.tree())) return null;
        Map<String, CustomItemDef> copy = new TreeMap<>(definitions);
        copy.put(id, def);
        definitions = copy;
        return def;
    }

    /** Supprime le fichier de l'item. {@code false} si l'id est inconnu. */
    public synchronized boolean remove(String id) {
        id = CustomItemIds.normalize(id);
        if (!definitions.containsKey(id)) return false;
        File file = fileOf(id);
        if (file.exists() && !file.delete()) return false;
        Map<String, CustomItemDef> copy = new TreeMap<>(definitions);
        copy.remove(id);
        definitions = copy;
        return true;
    }

    /** Relit le dossier (non récursif). Retourne le nombre de définitions chargées. */
    public synchronized int reload() {
        folder.mkdirs();
        Map<String, CustomItemDef> loaded = new TreeMap<>();
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));
        if (files != null) {
            Arrays.sort(files, Comparator.comparing(File::getName));
            for (File file : files) load(file, loaded);
        }
        definitions = loaded;
        logger.info("[CustomItem] " + loaded.size() + " item(s) custom chargé(s) depuis " + folder.getName() + ".");
        return loaded.size();
    }

    private void load(File file, Map<String, CustomItemDef> into) {
        String name = file.getName();
        String base = name.substring(0, name.lastIndexOf('.'));
        String id = CustomItemIds.normalize(base);
        if (!base.equals(id)) {
            logger.warning("[CustomItem] " + name + " : le nom du fichier doit être l'id normalisé ('" + id + ".yml'), id lu : " + id);
        }
        Map<String, Object> tree = YamlTree.read(file);
        if (tree == null) {
            logger.warning("[CustomItem] " + name + " : YAML illisible ou invalide, fichier ignoré.");
            return;
        }
        List<String> errors = new ArrayList<>();
        CustomItemDef def = CustomItemDef.parse(id, tree, errors);
        for (String error : errors) logger.warning("[CustomItem] " + name + " : " + error);
        if (def == null) return;
        if (into.containsKey(id)) {
            logger.warning("[CustomItem] " + name + " : id '" + id + "' déjà défini par un autre fichier, ignoré.");
            return;
        }
        into.put(id, def);
    }
}
