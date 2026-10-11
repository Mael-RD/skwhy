package skwhy.modules.LongNavigationElements;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import skwhy.chunkpath.ChunkGateService;
import skwhy.chunkpath.ChunkPathAnalyzer;
import skwhy.chunkpath.block.PathCostRules;
import skwhy.chunkpath.gate.ChunkGateStore;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fait le pont entre les mondes Bukkit et le système d'analyse chunk-à-chunk
 * (agnostique de Bukkit) de {@code skwhy.chunkpath} : une {@link ChunkGateService} par
 * monde, toutes partageant la même {@link ChunkGateStore} (une base de données par
 * monde, dans le dossier configuré par {@code chunkpath.database_folder}).
 */
public final class ChunkPathManager {

    public static final String DEFAULT_WORLD_FOLDER = "world/dimensions/minecraft";

    private static ChunkGateStore store;
    private static Path worldFolder;
    private static final Map<String, ChunkGateService> services = new ConcurrentHashMap<>();

    private ChunkPathManager() {
    }

    public static synchronized void init(JavaPlugin plugin) {
        if (store != null) return;
        String databaseFolder = plugin.getConfig().getString(
                "chunkpath.database_folder", ChunkGateStore.DEFAULT_DATABASE_FOLDER);
        store = new ChunkGateStore(plugin.getDataFolder().toPath(), databaseFolder);
        String worldFolderName = plugin.getConfig().getString("chunkpath.world_folder", DEFAULT_WORLD_FOLDER);
        worldFolder = Bukkit.getWorldContainer().toPath().resolve(
                worldFolderName == null || worldFolderName.isBlank() ? DEFAULT_WORLD_FOLDER : worldFolderName);
    }

    /** Ferme les bases de données ouvertes (à appeler depuis l'arrêt du plugin). */
    public static synchronized void shutdown() {
        services.clear();
        if (store != null) {
            store.close();
            store = null;
        }
    }

    public static ChunkGateService serviceFor(World world) {
        return services.computeIfAbsent(world.getName(), name -> {
            ChunkPathAnalyzer analyzer = new ChunkPathAnalyzer(regionFolderOf(world), PathCostRules.defaults());
            return new ChunkGateService(analyzer, store, name);
        });
    }

    private static Path regionFolderOf(World world) {
        String dimension = switch (world.getEnvironment()) {
            case NETHER -> "the_nether";
            case THE_END -> "the_end";
            default -> "overworld";
        };
        return worldFolder.resolve(dimension).resolve("region");
    }
}
