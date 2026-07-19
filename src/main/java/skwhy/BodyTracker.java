package skwhy; // Ajuste le package si besoin

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BodyTracker implements Listener {

    // L'équivalent de ta variable globale {-body::%player%}
    private static final Map<UUID, Float> bodyYaws = new ConcurrentHashMap<>();

    // Méthode pour lire la valeur depuis n'importe où (notamment ton Expression)
    public static float getCustomBodyYaw(Player player) {
        return bodyYaws.getOrDefault(player.getUniqueId(), player.getLocation().getYaw());
    }

    // Nettoyage quand le joueur se déconnecte
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        bodyYaws.remove(event.getPlayer().getUniqueId());
    }

    // Équivalent de "on player rotate" et "on player move" combinés
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        
        // Récupère l'ancienne valeur
        float bodyYaw = bodyYaws.getOrDefault(uuid, to.getYaw());

        // --- 1. LOGIQUE DE ROTATION ---
        if (from.getYaw() != to.getYaw()) {
            float change = to.getYaw();
            float diff = normalize(change - bodyYaw);
            
            if (diff > 50) {
                bodyYaw = mod(change - 50, 360);
            } else if (diff < -50) {
                bodyYaw = mod(change + 50, 360);
            }
        }

        // --- 2. LOGIQUE DE MOUVEMENT ---
        if (from.getX() != to.getX() || from.getZ() != to.getZ()) {
            if (player.getVehicle() == null) {
                Vector vector = to.toVector().subtract(from.toVector());
                vector.setY(0);
                double length = vector.length();

                if (length > 0.08) {
                    float playerYaw = to.getYaw();
                    // Calcul du yaw du vecteur mathématiquement
                    float vecYaw = (float) Math.toDegrees(Math.atan2(-vector.getX(), vector.getZ()));
                    
                    float direction = mod(playerYaw - vecYaw, 360);
                    float changeTarget = playerYaw;

                    if (direction >= 230 && direction <= 340) {
                        changeTarget = mod(playerYaw + 50, 360);
                    } else if (direction >= 200 && direction <= 230) {
                        changeTarget = mod(playerYaw - 50, 360);
                    } else if (direction >= 110 && direction <= 160) {
                        changeTarget = mod(playerYaw + 50, 360);
                    } else if (direction >= 20 && direction <= 110) {
                        changeTarget = mod(playerYaw - 50, 360);
                    }

                    float change = normalize(changeTarget - bodyYaw);
                    float speedMod = change < 0 ? -1100f : 1100f;
                    float change2 = speedMod * (float)(length - 0.06);

                    if (Math.abs(change) < Math.abs(change2)) {
                        change2 = change;
                    }

                    bodyYaw = mod(bodyYaw + change2, 360);
                }
            }
        }

        // Sauvegarde de la nouvelle valeur
        bodyYaws.put(uuid, bodyYaw);
    }

    // --- Fonctions Mathématiques Utilitaires (Équivalentes à Skript) ---
    
    // Le modulo de Java (%) peut renvoyer du négatif, cette fonction force le positif (comme Skript)
    private float mod(float a, float b) {
        return ((a % b) + b) % b;
    }

    // Équivalent de: mod((angle + 180), 360) - 180
    private float normalize(float angle) {
        return mod(angle + 180, 360) - 180;
    }

    // Détecte le swing du bras et définit le yaw du corps au yaw du joueur
    @EventHandler
    public void onPlayerArmSwing(PlayerAnimationEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        
        // Définit le yaw du corps au yaw du joueur lors du swing du bras
        bodyYaws.put(uuid, player.getLocation().getYaw());
    }
}