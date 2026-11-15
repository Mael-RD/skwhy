package skwhy.customitem;

import java.io.File;

import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;

import skwhy.SkWhy;

/** Point d'accès au registre des items custom (créé au chargement du module). */
public final class CustomItemManager {

    private static CustomItemManager instance;

    private final CustomItemRegistry registry;
    private final CustomItemListener listener = new CustomItemListener();

    private CustomItemManager(SkWhy plugin) {
        File folder = new File(plugin.getDataFolder(), plugin.getConfig().getString("custom_item.folder", "custom-items"));
        this.registry = new CustomItemRegistry(folder, plugin.getLogger());
    }

    public static void init(SkWhy plugin) {
        instance = new CustomItemManager(plugin);
        instance.registry.reload();
        Bukkit.getPluginManager().registerEvents(instance.listener, plugin);
    }

    public static void shutdown() {
        if (instance != null) HandlerList.unregisterAll(instance.listener);
        instance = null;
    }

    /** @return le gestionnaire, ou {@code null} si le module n'est pas chargé */
    public static CustomItemManager get() { return instance; }

    public static boolean isLoaded() { return instance != null; }

    public CustomItemRegistry registry() { return registry; }
}
