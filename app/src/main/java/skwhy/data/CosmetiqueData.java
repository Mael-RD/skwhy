package skwhy.data;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import java.util.List;
import java.util.ArrayList;
import org.joml.Quaternionf;
import org.bukkit.Location;

import skwhy.BodyTracker;
import skwhy.data.Tail.TailNode;

public class CosmetiqueData {
    private List<CosmetiqueHat> hats;
    private List<Player> viewers;
    private DisplayGroupData back;
    private DisplayGroupData back2;
    private Tail tail;
    private String type;
    private Entity entity;
    private boolean selfHats;
    private boolean selfBack;
    private boolean selfTail;

    public CosmetiqueData(Entity entity, boolean selfHats, boolean selfBack, boolean selfTail) {
        this.entity = entity;
        this.hats = new ArrayList<CosmetiqueHat>();
        this.selfHats = selfHats;
        this.selfBack = selfBack;
        this.selfTail = selfTail;
    }

    public void addViewer(Player player) {
        if (!viewers.contains(player)) {
            viewers.add(player);
            for (CosmetiqueHat hat : hats) {
                hat.data.addViewer(player);
            }
            if (back != null) back.addViewer(player);
            if (back2 != null) back2.addViewer(player);
            if (tail != null) tail.addViewer(player);
        }
    }
    public void removeViewer(Player player) {
        if (viewers.contains(player)) {
            viewers.remove(player);
            for (CosmetiqueHat hat : hats) {
                hat.data.removeViewer(player);
            }
            if (back != null) back.removeViewer(player);
            if (back2 != null) back2.removeViewer(player);
            if (tail != null) tail.removeViewer(player);
        }
    }

    public List<Player> getViewers() {
        return new ArrayList<>(viewers);
    }

    public void clearViewers() {
        viewers.clear();
        for (CosmetiqueHat hat : hats) {
            hat.data.clearViewers();
        }
        if (back != null) back.clearViewers();
        if (back2 != null) back2.clearViewers();
        if (tail != null) tail.clearViewers();
    }
    public void setViewers(List<Player> viewers) {
        this.viewers = new ArrayList<>(viewers);
        for (CosmetiqueHat hat : hats) {
            hat.data.setViewers(viewers);
        }
        if (back != null) back.setViewers(viewers);
        if (back2 != null) back2.setViewers(viewers);
        if (tail != null) tail.setViewers(viewers);
    }

    public void update() {
        for (CosmetiqueHat hat : hats) {
            hat.update();
        }
        float yaw;
        if (entity instanceof Player p) {
            yaw = BodyTracker.getCustomBodyYaw(p)+180;
        } else {
            yaw = entity.getLocation().getYaw()+180;
        }
        if (type.equals("wings") && back != null && back2 != null) {
            back.setRotation(new Quat4(calculateWingRotation()));
            back2.setRotation(back.getRotation().clone(true, false, false));
            // Utiliser un yaw lisse pour eviter les saccades lors de changements brusques
            float smoothedYaw = smoothYaw(yaw, 0.5f);  // 0.5f = 50% vers la cible par frame
            back.setYawPitch(smoothedYaw, 0);
            back2.setYawPitch(smoothedYaw, 0);
        }
        if (back != null) {
            back.updateMetadata();
        }
        if (back2 != null) {
            back2.updateMetadata();
        }
        Location location = entity.getLocation();
        location.setYaw(yaw);
        if (tail != null) tail.nextFrame(entity.getLocation());
    }

    public void delete() {
        for (CosmetiqueHat hat : hats) {
            hat.delete();
        }
        if (back != null) back.delete();
        if (back2 != null) back2.delete();
        if (tail != null) tail.delete();
        if (entity != null) entity.remove();
        hats.clear();
        back = null;
        back2 = null;
        tail = null;
        viewers.clear();
    }

    // ─────── CHAPEAUX ───────

    public List<String> getHats() {
        return hats.stream().map(CosmetiqueHat::getSlot).toList();
    }
    public boolean getSelfHats() {
        return selfHats;
    }
    public void setSelfHats(boolean selfHats) {
        this.selfHats = selfHats;
    }
    public void setHat(DisplayGroupData data, String slot, boolean verticalRotation, boolean horizontalRotation) {
        removeHat(slot);
        this.hats.add(new CosmetiqueHat(data, slot, verticalRotation, horizontalRotation));
    }
    public void removeHat(String slot) {
        for (CosmetiqueHat hat : hats) {
            if (hat.getSlot().equals(slot)) {
                hat.delete();
                hats.remove(hat);
                break;
            }
        }
    }
    public void removeHats() {
        for (CosmetiqueHat hat : hats) {
            hat.delete();
            hats.remove(hat);
            break;
        }
    }

    private class CosmetiqueHat {
        private DisplayGroupData data;
        private String slot;
        private boolean verticalRotation;
        private boolean horizontalRotation;

        private CosmetiqueHat(DisplayGroupData data, String slot, boolean verticalRotation, boolean horizontalRotation) {
            data.setCenter(new Vec3(0f, -0.4f, 0f));
            data.setInterpolationDuration(2);
            data.setTeleportationDuration(2);
            data.setAttachedEntity(entity);
            List<Player> finalViewers = new ArrayList<>(viewers != null ? viewers : List.of());
            if (entity instanceof Player p && selfHats) finalViewers.add(p);
            data.setViewers(finalViewers);
            data.setYawPitch(entity.getLocation().getYaw(), 0);
            this.data = data;
            this.slot = slot;
            this.verticalRotation = verticalRotation;
            this.horizontalRotation = horizontalRotation;
        }
        private String getSlot() {
            return slot;
        }
        private void update() {
            if (verticalRotation) {
                data.setRotation(new Quat4(new Quaternionf().rotationXYZ((float) Math.toRadians(entity.getLocation().getPitch()), 0f, 0f)));
                data.updateMetadata();
            }
            if (horizontalRotation) {
                data.setYawPitch(entity.getLocation().getYaw(), 0);
                data.sendRotation();
            }
        }
        private void delete() {
            data.delete();
        }
    }


    // ─────── DOS ───────

    public boolean getSelfBack() {
        return selfBack;
    }
    public void setSelfBack(boolean selfBack) {
        this.selfBack = selfBack;
    }
    public void removeBack() {
        if (back != null) {
            back.delete();
            back = null;
        }
        if (back2 != null) {
            back2.delete();
            back2 = null;
        }
    }

    public void setBack(DisplayGroupData back, String type) {
        removeBack();
        back.setCenter(new Vec3(0f, -0.5f, 0.15f));
        back.setInterpolationDuration(2);
        back.setTeleportationDuration(2);
        if (entity instanceof Player p) {
            back.setYawPitch(BodyTracker.getCustomBodyYaw(p), 0);
        } else {
            back.setYawPitch(entity.getLocation().getYaw(), 0);
        }
        back.setAttachedEntity(entity);
        this.type = type;
        List<Player> finalViewers = new ArrayList<>(viewers != null ? viewers : List.of());
        if (entity instanceof Player p && selfBack) finalViewers.add(p);
        back.setViewers(finalViewers);
        this.back = back;
        if (type.equals("wings")) {
            Bukkit.getLogger().info("Setting wings with back group " + back.serialize());
            this.back2 = back.clone(true, false, false);
        } else {
            this.back2 = null;
        }
        Bukkit.getLogger().info("Debug 6");
    }

    private float time = 0f;
    private float lastSmoothedYaw = 0f;

    /**
     * Lisse le yaw pour eviter les saccades lors de changements brusques.
     * Utilise une interpolation exponentielle avec gestion des discontinuites (-180/180).
     *
     * @param targetYaw Le yaw brut du joueur
     * @param smoothingFactor Facteur de lissage [0-1]. Plus proche de 1 = plus lisse
     * @return Le yaw lisse
     */
    private float smoothYaw(float targetYaw, float smoothingFactor) {
        // Normaliser les yaws a [-180, 180]
        targetYaw = normalizeDegrees(targetYaw);
        float lastYaw = normalizeDegrees(lastSmoothedYaw);
        
        // Calculer la difference minimale (en tenant compte de la discontinuite)
        float diff = targetYaw - lastYaw;
        
        // Si la difference est > 180, prendre le chemin court
        if (diff > 180f) diff -= 360f;
        if (diff < -180f) diff += 360f;
        
        // Interpolation exponentielle
        float smoothedYaw = lastYaw + diff * smoothingFactor;
        lastSmoothedYaw = normalizeDegrees(smoothedYaw);
        
        return lastSmoothedYaw;
    }

    /**
     * Normalise un angle en degres a la plage [-180, 180].
     */
    private float normalizeDegrees(float degrees) {
        degrees = degrees % 360f;
        if (degrees > 180f) degrees -= 360f;
        if (degrees < -180f) degrees += 360f;
        return degrees;
    }

    private Quaternionf calculateWingRotation() {
        this.time += 0.05f;

        // --- 1. HARMONIQUES ADDITIVES (Respiration naturelle et asynchrone) ---
        // Axe Y (Yaw) : Ouverture / Fermeture de l'aile
        float yawAnim = (float) (
            Math.sin(time * 1.0)  * 7.0    // Respiration de base (amplitude 5°)
            + Math.sin(time * 2.37) * 3.0    // Désynchronisation (amplitude 2°)
            + Math.sin(time * 5.13) * 1
        );

        // Axe Z (Roll) : Haussement / Abaissement de l'aile
        // On déphase (+1.0) pour éviter que l'aile monte et s'ouvre en même temps (effet robot)
        float rollAnim = (float) (
            Math.sin(time * 1.2 + 1.0) * 6.0
            + Math.sin(time * 2.81)      * 3.0
        );

        // --- 2. LE TWITCH OCCASIONNEL (Petit coup sec aléatoire) ---
        // La puissance 50 isole un pic ultra-rapide qui pop environ toutes les 10 secondes
        double twitchWave = Math.sin(time * 0.6);
        float twitch = 0f;
        if (twitchWave > 0) {
            twitch = (float) Math.pow(twitchWave, 50) * 18.0f; // Extension subite de 18 degrés
        }

        // --- 3. POSITION DE REPOS DE L'AILE (À ajuster selon ton modèle) ---
        float basePitch = 10f; 
        float baseYaw   = 35f; 
        float baseRoll  = -10f;

        // Application des animations et conversion globale en Radians
        float finalPitch = (float) Math.toRadians(basePitch);
        float finalYaw   = (float) Math.toRadians(baseYaw + yawAnim + twitch);
        float finalRoll  = (float) Math.toRadians(baseRoll + rollAnim);

        // --- 4. QUATERNION FINAL ---
        // Recréé un quaternion propre à partir des axes XYZ
        return new Quaternionf().rotationXYZ(finalPitch, finalYaw, finalRoll);
    }


    // ─────── QUEUES ───────


    public TailNode getTail() {
        return tail.getRoot();
    }
    public boolean getSelfTail() {
        return selfTail;
    }
    public void setSelfTail(boolean selfTail) {
        this.selfTail = selfTail;
    }
    public void setTail(TailNode tail) {
        this.tail = tail.getTailFromNode();
        this.tail.setViewers(viewers != null ? viewers : List.of());
    }
    public void removeTail() {
        if (tail != null) {
            tail.delete();
            tail = null;
        }
    }

    // utilitaires
    public String serialize() {
        return "cosmetique:" + this.hashCode();
    }
}
