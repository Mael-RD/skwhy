package skwhy;

import ch.njol.skript.Skript;

import java.io.BufferedReader;
import java.io.File;
import java.nio.file.Files;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.skriptlang.skript.addon.SkriptAddon;

import skwhy.pathfinder.Navigation;
import skwhy.modules.FakeDisplayModule;
import skwhy.modules.NavigationModule;
import skwhy.modules.RandomStuffModule;
import skwhy.modules.APIModule;


// import com.github.retrooper.packetevents.PacketEvents;

public class SkWhy extends JavaPlugin {

    private static SkWhy instance;
    private static SkriptAddon skriptAddon;

    @Override
    public void onEnable() {
        instance = this;
        
        // Vérification que Skript est bien chargé
        if (Skript.instance() == null) {
            getLogger().severe("Skript n'est pas chargé ! Désactivation.");
            setEnabled(false);
            return;
        }

        
        instance = this;
        saveDefaultConfig();
        mergeConfigWithDefaults();


        getLogger().info("Addon chargé avec succès !");


        skriptAddon = Skript.instance().registerAddon(SkWhy.class, "SkWhy");
        
        skriptAddon.localizer().setSourceDirectories(
            "lang",
            null
        );
        // Chargement des modules
        if (isModuleEnabled("modules.fake_display")) {
            skriptAddon.loadModules(new FakeDisplayModule());
        } else {
            getLogger().info("Module FakeDisplay désactivé dans la config.");
        }
        if (isModuleEnabled("modules.fake_pathfinding")) {
            skriptAddon.loadModules(new NavigationModule());
            Bukkit.getScheduler().runTaskTimer(this, Navigation::tickAll, 0L, 1L);
        } else {
            getLogger().info("Module FakePathFinding désactivé dans la config.");
        }
        if (isModuleEnabled("modules.random_stuff")) {
            skriptAddon.loadModules(new RandomStuffModule());
        } else {
            getLogger().info("Module RandomStuff désactivé dans la config.");
        }
        if (isModuleEnabled("modules.api")) {
            skriptAddon.loadModules(new APIModule());
        } else {
            getLogger().info("Module API REST désactivé dans la config.");
        }
        getServer().getPluginManager().registerEvents(new EntityRemove(), this);
        getServer().getPluginManager().registerEvents(new BodyTracker(), this);
        getServer().getPluginManager().registerEvents(new FutureRotationTracker(), this);
        getServer().getPluginManager().registerEvents(new TrackedBy(), this);
        FutureRotationTracker.startTracking(this);
        TrackedBy.startTracking(this);

        getLogger().info("SkWhy activé avec succès !");
    }

    @Override
    public void onDisable() {
        APIModule.shutdown();
        getLogger().info("SkWhy désactivé.");
    }

    public static SkWhy getInstance() { return instance; }
    public static SkriptAddon getSkriptAddon() { return skriptAddon; }

    public boolean isModuleEnabled(String path) {
        return getConfig().getBoolean(path, true);
    }

    private void mergeConfigWithDefaults() {
    // Lire la config par défaut (dans le jar)
    InputStream defaultStream = getResource("config.yml");
    if (defaultStream == null) return;

    List<String> defaultLines;
    List<String> currentLines;

    try {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(defaultStream, StandardCharsets.UTF_8))) {
            defaultLines = reader.lines().collect(Collectors.toList());
        }

        File configFile = new File(getDataFolder(), "config.yml");
        currentLines = Files.readAllLines(configFile.toPath(), StandardCharsets.UTF_8);
    } catch (IOException e) {
        getLogger().severe("Impossible de lire les configs : " + e.getMessage());
        return;
    }

    // Construire l'ensemble des clés présentes dans la config actuelle
    // Format : "bloc.sous-bloc.cle" reconstitué depuis l'indentation
    Set<String> existingKeys = extractKeys(currentLines);

    // Parcourir la config par défaut, détecter les clés absentes
    // et accumuler les lignes à ajouter (commentaires inclus)
    List<String> pendingBuffer = new ArrayList<>(); // commentaires en attente
    Map<String, List<String>> toInsert = new LinkedHashMap<>();
    // clé = clé YAML complète absente, valeur = buffer commentaires + la ligne elle-même

    Deque<String> blockStack = new ArrayDeque<>(); // pile des blocs parents

    for (String line : defaultLines) {
        String trimmed = line.stripLeading();

        // Ligne vide ou commentaire → on l'accumule dans le buffer en attente
        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
            pendingBuffer.add(line);
            continue;
        }

        // Calculer l'indentation
        int indent = line.length() - trimmed.length();

        // Mettre à jour la pile de blocs selon l'indentation
        // (on dépile jusqu'au niveau parent correspondant)
        while (blockStack.size() > indent / 2) {
            blockStack.pollLast();
        }

        // Extraire le nom de la clé sur cette ligne
        String keyPart = trimmed.contains(":") ? trimmed.substring(0, trimmed.indexOf(':')).trim() : null;
        if (keyPart == null) {
            pendingBuffer.clear();
            continue;
        }

        // Clé complète = pile + clé courante
        String fullKey = blockStack.isEmpty()
                ? keyPart
                : String.join(".", blockStack) + "." + keyPart;

        // Si la valeur est vide (bloc parent), empiler
        String afterColon = trimmed.contains(":") ? trimmed.substring(trimmed.indexOf(':') + 1).trim() : "";
        if (afterColon.isEmpty()) {
            blockStack.addLast(keyPart);
        }

        if (!existingKeys.contains(fullKey)) {
            // Clé absente : on mémorise le buffer de commentaires + la ligne
            List<String> block = new ArrayList<>(pendingBuffer);
            block.add(line);
            toInsert.put(fullKey, block);
        }

        pendingBuffer.clear();
    }

    if (toInsert.isEmpty()) return; // rien à faire

    // Injecter les lignes manquantes dans la config actuelle
    List<String> result = injectMissingLines(currentLines, defaultLines, toInsert);

    // Réécrire le fichier
    File configFile = new File(getDataFolder(), "config.yml");
    try {
        Files.write(configFile.toPath(), result, StandardCharsets.UTF_8);
        getLogger().info(toInsert.size() + " clé(s) manquante(s) ajoutée(s) à la config.");
        reloadConfig();
    } catch (IOException e) {
        getLogger().severe("Impossible d'écrire la config mise à jour : " + e.getMessage());
    }
}

    /**
     * Extrait toutes les clés complètes présentes dans un fichier YAML (texte brut).
     * Exemple : "database.host", "server.port", etc.
     */
    private Set<String> extractKeys(List<String> lines) {
        Set<String> keys = new HashSet<>();
        Deque<String> stack = new ArrayDeque<>();

        for (String line : lines) {
            String trimmed = line.stripLeading();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;

            int indent = line.length() - trimmed.length();

            while (stack.size() > indent / 2) {
                stack.pollLast();
            }

            if (!trimmed.contains(":")) continue;
            String keyPart = trimmed.substring(0, trimmed.indexOf(':')).trim();

            String fullKey = stack.isEmpty() ? keyPart : String.join(".", stack) + "." + keyPart;
            keys.add(fullKey);

            String afterColon = trimmed.substring(trimmed.indexOf(':') + 1).trim();
            if (afterColon.isEmpty()) {
                stack.addLast(keyPart);
            }
        }

        return keys;
    }

    /**
     * Insère les lignes manquantes dans la config actuelle au bon endroit.
     * "Au bon endroit" = après le dernier frère connu dans le même bloc,
     * ou à la fin du bloc parent si aucun frère n'existe encore.
     */
    private List<String> injectMissingLines(
            List<String> currentLines,
            List<String> defaultLines,
            Map<String, List<String>> toInsert) {

        List<String> result = new ArrayList<>(currentLines);

        for (Map.Entry<String, List<String>> entry : toInsert.entrySet()) {
            String missingKey  = entry.getKey();
            List<String> block = entry.getValue();

            // Trouver la ligne correspondante dans la config par défaut
            // pour connaître son indentation et son bloc parent
            String parentKey = missingKey.contains(".")
                    ? missingKey.substring(0, missingKey.lastIndexOf('.'))
                    : null;

            int insertAt = findInsertionPoint(result, defaultLines, missingKey, parentKey);
            result.addAll(insertAt, block);
        }

        return result;
    }

    /**
     * Cherche la position d'insertion dans `current` pour une clé manquante.
     *
     * Stratégie :
     *  1. Trouver dans `defaultLines` le prédécesseur immédiat de la clé manquante
     *     (la ligne juste avant dans le même bloc, en ignorant commentaires/vides).
     *  2. Chercher ce prédécesseur dans `current` → insérer juste après.
     *  3. Sinon, trouver la ligne du bloc parent dans `current` → insérer après elle.
     *  4. Sinon, ajouter à la fin.
     */
    private int findInsertionPoint(
            List<String> current,
            List<String> defaultLines,
            String missingKey,
            String parentKey) {

        // ── 1. Trouver le prédécesseur dans defaultLines ──
        String predecessorKeyPart = null;
        {
            Deque<String> stack    = new ArrayDeque<>();
            String        prevKey  = null;

            for (String line : defaultLines) {
                String trimmed = line.stripLeading();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                if (!trimmed.contains(":")) continue;

                int indent    = line.length() - trimmed.length();
                while (stack.size() > indent / 2) stack.pollLast();

                String keyPart = trimmed.substring(0, trimmed.indexOf(':')).trim();
                String fullKey = stack.isEmpty() ? keyPart : String.join(".", stack) + "." + keyPart;

                if (fullKey.equals(missingKey)) {
                    predecessorKeyPart = prevKey;
                    break;
                }

                // Même niveau de bloc que la clé cherchée → candidat prédécesseur
                String currentParent = fullKey.contains(".")
                        ? fullKey.substring(0, fullKey.lastIndexOf('.'))
                        : null;
                boolean sameBlock = (parentKey == null && currentParent == null)
                        || (parentKey != null && parentKey.equals(currentParent));

                if (sameBlock) prevKey = keyPart;

                String afterColon = trimmed.substring(trimmed.indexOf(':') + 1).trim();
                if (afterColon.isEmpty()) stack.addLast(keyPart);
            }
        }

        // ── 2. Chercher le prédécesseur dans current ──
        if (predecessorKeyPart != null) {
            for (int i = current.size() - 1; i >= 0; i--) {
                String trimmed = current.get(i).stripLeading();
                if (trimmed.startsWith(predecessorKeyPart + ":")) {
                    // Avancer jusqu'à la fin du bloc de ce prédécesseur
                    int indent = current.get(i).length() - trimmed.length();
                    int j = i + 1;
                    while (j < current.size()) {
                        String t = current.get(j).stripLeading();
                        if (!t.isEmpty() && !t.startsWith("#")) {
                            int nextIndent = current.get(j).length() - t.length();
                            if (nextIndent <= indent) break;
                        }
                        j++;
                    }
                    return j;
                }
            }
        }

        // ── 3. Chercher le bloc parent dans current ──
        if (parentKey != null) {
            String parentKeyPart = parentKey.contains(".")
                    ? parentKey.substring(parentKey.lastIndexOf('.') + 1)
                    : parentKey;

            for (int i = 0; i < current.size(); i++) {
                String trimmed = current.get(i).stripLeading();
                if (trimmed.startsWith(parentKeyPart + ":")) {
                    // Fin du bloc parent
                    int indent = current.get(i).length() - trimmed.length();
                    int j = i + 1;
                    while (j < current.size()) {
                        String t = current.get(j).stripLeading();
                        if (!t.isEmpty() && !t.startsWith("#")) {
                            int nextIndent = current.get(j).length() - t.length();
                            if (nextIndent <= indent) break;
                        }
                        j++;
                    }
                    return j;
                }
            }
        }

        // ── 4. Fallback : fin de fichier ──
        return current.size();
    }
}
