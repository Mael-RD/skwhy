package skwhy;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Classe qui track quelles entités sont trackées (visibles) par chaque joueur.
 *
 * <p>Mise à jour chaque tick : scanne toutes les entités et maintient une Map
 * Player -> Set d'entités qu'il track.
 *
 * <p>À appeler dans onEnable() du plugin : {@code TrackedBy.startTracking(plugin);}
 */
public class TrackedBy implements Listener {

    // Map UUID du joueur -> Set d'UUID des entités trackées
    private static final Map<UUID, Set<UUID>> playerTrackedEntities = new ConcurrentHashMap<>();

    /**
     * Lance le tracking des entités par tick.
     */
    public static void startTracking(Plugin plugin) {
        // Enregistrer le listener (pour PlayerQuitEvent)
        Bukkit.getPluginManager().registerEvents(new TrackedBy(), plugin);

        // Task qui s'exécute chaque tick pour mettre à jour le tracking
        new BukkitRunnable() {
            @Override
            public void run() {
                updateTrackedEntities();
            }
        }.runTaskTimer(plugin, 0L, 1L); // Chaque tick
    }

    /**
     * Met à jour la Map en scanant toutes les entités et leurs trackers.
     */
    private static void updateTrackedEntities() {
        // Effacer l'ancien état
        playerTrackedEntities.clear();

        // Pour chaque joueur en ligne
        for (Player player : Bukkit.getOnlinePlayers()) {
            Set<UUID> trackedEntities = new HashSet<>();
            playerTrackedEntities.put(player.getUniqueId(), trackedEntities);

            // Scaner toutes les entités du monde du joueur
            for (Entity entity : player.getWorld().getEntities()) {
                // Si le joueur track cette entité, l'ajouter à la liste
                if (entity.getTrackedBy().contains(player)) {
                    trackedEntities.add(entity.getUniqueId());
                }
            }
        }
    }

    /**
     * Récupère toutes les entités trackées par un joueur.
     *
     * @param player Le joueur
     * @return Une liste des entités que le joueur track, ou une liste vide
     */
    public static List<Entity> getTrackedEntities(Player player) {
        if (player == null) return new ArrayList<>();

        Set<UUID> trackedUUIDs = playerTrackedEntities.get(player.getUniqueId());
        if (trackedUUIDs == null || trackedUUIDs.isEmpty()) return new ArrayList<>();

        List<Entity> trackedEntities = new ArrayList<>();
        for (UUID uuid : trackedUUIDs) {
            Entity entity = Bukkit.getEntity(uuid);
            if (entity != null && entity.getWorld().equals(player.getWorld())) {
                trackedEntities.add(entity);
            }
        }
        return trackedEntities;
    }

    /**
     * Nettoie les données du joueur quand il se déconnecte.
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        playerTrackedEntities.remove(event.getPlayer().getUniqueId());
    }
}
