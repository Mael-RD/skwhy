package skwhy;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FutureRotationTracker implements Listener {

    // Petite classe interne pour stocker les données par joueur (très léger)
    private static class RotState {
        float lastYaw = 0f;
        float lastPitch = 0f;
        float deltaYaw = 0f;
        float deltaPitch = 0f;
    }

    private static final Map<UUID, RotState> states = new ConcurrentHashMap<>();

    /**
     * Lance la boucle d'analyse à appeler dans ton onEnable()
     */
    public static void startTracking(Plugin plugin) {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    RotState state = states.computeIfAbsent(player.getUniqueId(), k -> new RotState());
                    Location loc = player.getLocation();
                    
                    float currentYaw = loc.getYaw();
                    float currentPitch = loc.getPitch();

                    // Calcul de la différence (delta) avec le tick précédent
                    // On normalise le yaw pour éviter le bug du passage de 179° à -179°
                    state.deltaYaw = normalizeAngle(currentYaw - state.lastYaw);
                    state.deltaPitch = currentPitch - state.lastPitch;

                    // Mise à jour pour le prochain tick
                    state.lastYaw = currentYaw;
                    state.lastPitch = currentPitch;
                }
            }
        }.runTaskTimer(plugin, 0L, 1L); // S'exécute tous les ticks
    }

    /**
     * Calcule la prédiction pour le tick suivant.
     * @return float[] { predictedYaw, predictedPitch }
     */
    public static float[] getPredictedRotation(Player player) {
        Location loc = player.getLocation();
        RotState state = states.get(player.getUniqueId());
        
        // Sécurité si le joueur vient de se connecter
        if (state == null) {
            return new float[]{ loc.getYaw(), loc.getPitch() };
        }

        // Prédiction : Position actuelle + Vélocité angulaire
        float predYaw = normalizeAngle(loc.getYaw() + 2*state.deltaYaw);
        
        // Le pitch dans Minecraft est strictement bloqué entre -90 (haut) et 90 (bas)
        float predPitch = Math.max(-90.0f, Math.min(90.0f, loc.getPitch() + 2*state.deltaPitch));

        return new float[]{ predYaw, predPitch };
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        states.remove(event.getPlayer().getUniqueId()); // Nettoyage de la RAM
    }

    /**
     * Utilitaire pour garder les angles de Yaw entre -180 et 180 (Format Bukkit)
     */
    private static float normalizeAngle(float angle) {
        angle = angle % 360.0f;
        if (angle >= 180.0f) {
            angle -= 360.0f;
        }
        if (angle < -180.0f) {
            angle += 360.0f;
        }
        return angle;
    }
}