package skwhy;

import ch.njol.skript.Skript;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.skriptlang.skript.addon.SkriptAddon;

import skwhy.data.FakePathFinding;
import skwhy.modules.FakeDisplayModule;
import skwhy.modules.FakePathFindingModule;
import skwhy.modules.RandomStuffModule;
import skwhy.modules.VoiceModule;


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

        // if (PacketEvents.getAPI() != null) {
        //     SpigotEntityLibPlatform platform = new SpigotEntityLibPlatform(this);
        //     APIConfig config = new APIConfig(PacketEvents.getAPI())
        //         .usePlatformLogger();
        //     EntityLib.init(platform, config);
        // }


        
        instance = this;
        saveDefaultConfig();


        getLogger().info("[VoiceSkript] Addon chargé avec succès !");


        skriptAddon = Skript.instance().registerAddon(SkWhy.class, "SkWhy");
        
        skriptAddon.localizer().setSourceDirectories(
            "lang",
            null
        );
        // Chargement des modules
        if (isModuleEnabled("modules.fake_display")) {
            skriptAddon.loadModules(new FakeDisplayModule());
        } else {
            getLogger().info("[VoiceSkript] Module FakeDisplay désactivé dans la config.");
        }
        if (isModuleEnabled("modules.fake_pathfinding")) {
            skriptAddon.loadModules(new FakePathFindingModule());
            Bukkit.getScheduler().runTaskTimer(this, FakePathFinding::tickAll, 0L, 1L);
        } else {
            getLogger().info("[VoiceSkript] Module FakePathFinding désactivé dans la config.");
        }
        if (isModuleEnabled("modules.random_stuff")) {
            skriptAddon.loadModules(new RandomStuffModule());
        } else {
            getLogger().info("[VoiceSkript] Module RandomStuff désactivé dans la config.");
        }
        if (isModuleEnabled("modules.voice")) {
            skriptAddon.loadModules(new VoiceModule());
        } else {
            getLogger().info("[VoiceSkript] Module Voice désactivé dans la config.");
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
        getLogger().info("SkWhy désactivé.");
    }

    public static SkWhy getInstance() { return instance; }
    public static SkriptAddon getSkriptAddon() { return skriptAddon; }

    private boolean isModuleEnabled(String path) {
        return getConfig().getBoolean(path, true);
    }
}
