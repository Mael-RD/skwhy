package skwhy;

import ch.njol.skript.Skript;
import me.tofaa.entitylib.APIConfig;
import me.tofaa.entitylib.EntityLib;
import me.tofaa.entitylib.spigot.SpigotEntityLibPlatform;

import org.bukkit.plugin.java.JavaPlugin;
import org.skriptlang.skript.addon.SkriptAddon;

import com.github.retrooper.packetevents.PacketEvents;

import skwhy.modules.FakeDisplayModule;
import skwhy.modules.RandomStuffModule;
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

        if (PacketEvents.getAPI() != null) {
            SpigotEntityLibPlatform platform = new SpigotEntityLibPlatform(this);
            APIConfig config = new APIConfig(PacketEvents.getAPI())
                .usePlatformLogger();
            EntityLib.init(platform, config);
        }

        skriptAddon = Skript.instance().registerAddon(SkWhy.class, "SkWhy");
        
        skriptAddon.localizer().setSourceDirectories(
            "lang",
            null
        );
        // Chargement des modules
        skriptAddon.loadModules(new FakeDisplayModule());
        skriptAddon.loadModules(new RandomStuffModule());

        getLogger().info("SkWhy activé avec succès !");
    }

    @Override
    public void onDisable() {
        getLogger().info("SkWhy désactivé.");
    }

    public static SkWhy getInstance() { return instance; }
    public static SkriptAddon getSkriptAddon() { return skriptAddon; }
}
