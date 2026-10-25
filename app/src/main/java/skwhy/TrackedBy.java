package skwhy;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * Entités suivies (visibles) par un joueur. Calculé à la demande : l'ancien scan de toutes les entités de chaque monde,
 * à chaque tick, pour chaque joueur, coûtait cher alors que le résultat n'est lu que par l'expression
 * {@code entities tracked by}.
 */
public final class TrackedBy {

    private TrackedBy() {}

    /**
     * Récupère toutes les entités du monde du joueur qu'il suit.
     *
     * @param player Le joueur
     * @return Une liste des entités que le joueur track, ou une liste vide
     */
    public static List<Entity> getTrackedEntities(Player player) {
        List<Entity> trackedEntities = new ArrayList<>();
        if (player == null) return trackedEntities;
        for (Entity entity : player.getWorld().getEntities()) {
            if (entity.getTrackedBy().contains(player)) trackedEntities.add(entity);
        }
        return trackedEntities;
    }
}
