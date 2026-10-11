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
import skwhy.util.ConfigMerger;
import skwhy.chunkpath.longnav.LongNavigation;
import skwhy.modules.FakeDisplayModule;
import skwhy.modules.NavigationModule;
import skwhy.modules.RandomStuffModule;
import skwhy.modules.APIModule;
import skwhy.modules.ChunkPathModule;


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
        if (isModuleEnabled("modules.chunk_path")) {
            skriptAddon.loadModules(new ChunkPathModule());
            Bukkit.getScheduler().runTaskTimer(this, LongNavigation::tickAll, 0L, 1L);
        } else {
            getLogger().info("Module ChunkPath désactivé dans la config.");
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
        ChunkPathModule.shutdown();
        getLogger().info("SkWhy désactivé.");
    }

    public static SkWhy getInstance() { return instance; }
    public static SkriptAddon getSkriptAddon() { return skriptAddon; }

    public boolean isModuleEnabled(String path) {
        return getConfig().getBoolean(path, true);
    }

    private void mergeConfigWithDefaults() {
        InputStream defaultStream = getResource("config.yml");
        if (defaultStream == null) return;

        File configFile = new File(getDataFolder(), "config.yml");
        List<String> defaultLines;
        List<String> currentLines;
        try {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(defaultStream, StandardCharsets.UTF_8))) {
                defaultLines = reader.lines().collect(Collectors.toList());
            }
            currentLines = Files.readAllLines(configFile.toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            getLogger().severe("Impossible de lire les configs : " + e.getMessage());
            return;
        }

        ConfigMerger.Result merged = ConfigMerger.merge(currentLines, defaultLines);
        if (merged.addedKeys().isEmpty()) return;

        try {
            Files.write(configFile.toPath(), merged.lines(), StandardCharsets.UTF_8);
            getLogger().info(merged.addedKeys().size() + " clé(s) manquante(s) ajoutée(s) à la config : " + merged.addedKeys());
            reloadConfig();
        } catch (IOException e) {
            getLogger().severe("Impossible d'écrire la config mise à jour : " + e.getMessage());
        }
    }
}
