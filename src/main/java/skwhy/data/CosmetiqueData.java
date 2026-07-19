package skwhy.data;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import java.util.List;
import java.util.ArrayList;
import org.joml.Quaternionf;
import org.bukkit.Location;

import skwhy.BodyTracker;
import skwhy.data.Tail.TailNode;
import skwhy.FutureRotationTracker;

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
    private float scale;
    private boolean ocilator = false;

    public CosmetiqueData(Entity entity, boolean selfHats, boolean selfBack, boolean selfTail) {
        this.entity = entity;
        this.hats = new ArrayList<CosmetiqueHat>();
        this.viewers = new ArrayList<Player>();
        this.selfHats = selfHats;
        this.selfBack = selfBack;
        this.selfTail = selfTail;
        this.scale = 1.0f;
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
        float yaw, futureYaw, futurePitch;
        if (entity instanceof Player p) {
            yaw = BodyTracker.getCustomBodyYaw(p)+180;
            float[] predicted = FutureRotationTracker.getPredictedRotation(p);
            futureYaw = predicted[0];
            futurePitch = predicted[1];
        } else {
            yaw = entity.getLocation().getYaw()+180;
            futureYaw = yaw;
            futurePitch = entity.getLocation().getPitch();
        }
        for (CosmetiqueHat hat : hats) {
            hat.update(futureYaw, futurePitch);
        }
        float smoothedYaw = smoothYaw(yaw, 0.5f);  // 0.5f = 50% vers la cible par frame
        ocilator = !ocilator;
        if ("wings".equals(type) && back != null && back2 != null) {
            if (ocilator) {
                back.setRotation(new Quat4(calculateWingRotation()));
                back2.setRotation(back.getRotation().clone(true, false, false));
                if (back != null) {
                    back.updateMetadata();
                }
                if (back2 != null) {
                    back2.updateMetadata();
                }
            }
            // Utiliser un yaw lisse pour eviter les saccades lors de changements brusques
            back.setYawPitch(smoothedYaw, 0);
            back2.setYawPitch(smoothedYaw, 0);
        }
        Location location = entity.getLocation();
        location.setYaw(futureYaw);
        if (tail != null) {
            tail.nextFrame(location);
            tail.setYawPitch(yaw+180, 0);
        }
    }

    public void mount() {
        update();
    }

    public void delete() {
        for (CosmetiqueHat hat : hats) {
            hat.delete();
        }
        if (back != null) back.delete();
        if (back2 != null) back2.delete();
        if (tail != null) tail.delete();
        hats.clear();
        back = null;
        back2 = null;
        tail = null;
        entity = null;
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
        if (selfHats == this.selfHats) return; // Pas de changement
        this.selfHats = selfHats;
        if (entity instanceof Player p) {
            if (selfHats) for (CosmetiqueHat hat : hats) hat.data.addViewer(p);
            else for (CosmetiqueHat hat : hats) hat.data.removeViewer(p);
        }
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
        private void update(float futureYaw, float futurePitch) {
            if (verticalRotation) {
                data.setRotation(new Quat4(new Quaternionf().rotationXYZ((float) Math.toRadians(-futurePitch), 0f, 0f)));
                data.updateMetadata();
            }
            if (horizontalRotation) {
                data.setYawPitch(futureYaw+180, 0);
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
        if (selfBack == this.selfBack) return;
        this.selfBack = selfBack;
        if (entity instanceof Player p && back != null) {
            if (selfBack) {
                back.addViewer(p);
                if (back2 != null) back2.addViewer(p);
            } else {
                back.removeViewer(p);
                if (back2 != null) back2.removeViewer(p);
            }
        }
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
        back.setInterpolationDuration(3);
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
        if ("wings".equals(type)) {
            this.back2 = back.clone(true, false, false);
        } else {
            this.back2 = null;
        }
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
        this.time += 0.1f;

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
        if (selfTail == this.selfTail) return;
        this.selfTail = selfTail;
        if (entity instanceof Player p && tail != null) {
            if (selfTail) tail.addViewer(p);
            else tail.removeViewer(p);
        }
    }
    public void setTail(TailNode tail) {
        removeTail();
      
        this.tail = tail.getTailFromNode();
        this.tail.setAttachedEntity(entity);
        this.tail.setInterpolationDuration(2);
        this.tail.setTeleportationDuration(2);
        if (entity instanceof Player p) {
            this.tail.setYawPitch(BodyTracker.getCustomBodyYaw(p), 0);
        } else {
            this.tail.setYawPitch(entity.getLocation().getYaw(), 0);
        }
        List<Player> finalViewers = new ArrayList<>(viewers != null ? viewers : List.of());
        if (entity instanceof Player p && selfHats) finalViewers.add(p);
        this.tail.setViewers(finalViewers);
    }
    public void removeTail() {
        if (tail != null) {
            tail.delete();
            tail = null;
        }
    }

    // ─────── Paramètres de la Queue ───────

    public Tail getTailParameters() {
        return tail;
    }

    // Ressort
    public float getTailRigidity() {
        return tail != null ? tail.getRigidity() : 0f;
    }
    public void setTailRigidity(float value) {
        if (tail != null) tail.setRigidity(value);
    }

    public float getTailDamping() {
        return tail != null ? tail.getDamping() : 0f;
    }
    public void setTailDamping(float value) {
        if (tail != null) tail.setDamping(value);
    }

    public float getTailVelocitySmoothing() {
        return tail != null ? tail.getVelocitySmoothing() : 0f;
    }
    public void setTailVelocitySmoothing(float value) {
        if (tail != null) tail.setVelocitySmoothing(value);
    }

    // Déflexion vélocité
    public float getTailVelocityInfluenceForward() {
        return tail != null ? tail.getVelocityInfluenceForward() : 0f;
    }
    public void setTailVelocityInfluenceForward(float value) {
        if (tail != null) tail.setVelocityInfluenceForward(value);
    }

    public float getTailVelocityInfluenceLateral() {
        return tail != null ? tail.getVelocityInfluenceLateral() : 0f;
    }
    public void setTailVelocityInfluenceLateral(float value) {
        if (tail != null) tail.setVelocityInfluenceLateral(value);
    }

    public float getTailVelocityInfluenceVertical() {
        return tail != null ? tail.getVelocityInfluenceVertical() : 0f;
    }
    public void setTailVelocityInfluenceVertical(float value) {
        if (tail != null) tail.setVelocityInfluenceVertical(value);
    }

    public float getTailVelocityInfluenceYaw() {
        return tail != null ? tail.getVelocityInfluenceYaw() : 0f;
    }
    public void setTailVelocityInfluenceYaw(float value) {
        if (tail != null) tail.setVelocityInfluenceYaw(value);
    }

    public float getTailMaxDeflectionAngle() {
        return tail != null ? tail.getMaxDeflectionAngle() : 0f;
    }
    public void setTailMaxDeflectionAngle(float value) {
        if (tail != null) tail.setMaxDeflectionAngle(value);
    }

    public float getTailDepthDeflectionFactor() {
        return tail != null ? tail.getDepthDeflectionFactor() : 0f;
    }
    public void setTailDepthDeflectionFactor(float value) {
        if (tail != null) tail.setDepthDeflectionFactor(value);
    }

    // Impulsions
    public float getTailImpulseInfluenceForward() {
        return tail != null ? tail.getImpulseInfluenceForward() : 0f;
    }
    public void setTailImpulseInfluenceForward(float value) {
        if (tail != null) tail.setImpulseInfluenceForward(value);
    }

    public float getTailImpulseInfluenceLateral() {
        return tail != null ? tail.getImpulseInfluenceLateral() : 0f;
    }
    public void setTailImpulseInfluenceLateral(float value) {
        if (tail != null) tail.setImpulseInfluenceLateral(value);
    }

    public float getTailImpulseInfluenceVertical() {
        return tail != null ? tail.getImpulseInfluenceVertical() : 0f;
    }
    public void setTailImpulseInfluenceVertical(float value) {
        if (tail != null) tail.setImpulseInfluenceVertical(value);
    }

    // Ondulation
    public float getTailUndulationAmplitudeX() {
        return tail != null ? tail.getUndulationAmplitudeX() : 0f;
    }
    public void setTailUndulationAmplitudeX(float value) {
        if (tail != null) tail.setUndulationAmplitudeX(value);
    }

    public float getTailUndulationAmplitudeY() {
        return tail != null ? tail.getUndulationAmplitudeY() : 0f;
    }
    public void setTailUndulationAmplitudeY(float value) {
        if (tail != null) tail.setUndulationAmplitudeY(value);
    }

    public float getTailUndulationAmplitudeZ() {
        return tail != null ? tail.getUndulationAmplitudeZ() : 0f;
    }
    public void setTailUndulationAmplitudeZ(float value) {
        if (tail != null) tail.setUndulationAmplitudeZ(value);
    }

    public float getTailUndulationFrequency() {
        return tail != null ? tail.getUndulationFrequency() : 0f;
    }
    public void setTailUndulationFrequency(float value) {
        if (tail != null) tail.setUndulationFrequency(value);
    }

    public float getTailUndulationPropagation() {
        return tail != null ? tail.getUndulationPropagation() : 0f;
    }
    public void setTailUndulationPropagation(float value) {
        if (tail != null) tail.setUndulationPropagation(value);
    }

    // Bruit aléatoire
    public float getTailRandomAmplitude() {
        return tail != null ? tail.getRandomAmplitude() : 0f;
    }
    public void setTailRandomAmplitude(float value) {
        if (tail != null) tail.setRandomAmplitude(value);
    }

    public float getTailRandomFrequency() {
        return tail != null ? tail.getRandomFrequency() : 0f;
    }
    public void setTailRandomFrequency(float value) {
        if (tail != null) tail.setRandomFrequency(value);
    }

    public void setTailRestRotation(List<Quaternionf> rotations) {
        if (tail != null) tail.setRestRotation(rotations);
    }

    // ─────── SCALE ───────

    public float getScale() {
        return scale;
    }

    public void setScale(float value) {
        this.scale = value;
        // Appliquer le scale à tous les groupes du cosmétique
        for (CosmetiqueHat hat : hats) {
            hat.data.setScale(value);
        }
        if (back != null) back.setScale(value);
        if (back2 != null) back2.setScale(value);
        if (tail != null) {
            tail.setScale(value);
        }
    }

    // utilitaires
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Cosmetique[");
        
        // Hats
        sb.append("hats: ").append(hats.size()).append(" (");
        for (int i = 0; i < hats.size(); i++) {
            CosmetiqueHat hat = hats.get(i);
            sb.append(hat.data.getDisplays().size()).append(" displays");
            if (i < hats.size() - 1) sb.append(", ");
        }
        sb.append(")");
        
        // Back
        sb.append(", back: ");
        if (back != null) {
            sb.append(back.getDisplays().size()).append(" displays, ")
              .append(back.getViewers().size()).append(" viewers");
        } else {
            sb.append("null");
        }
        
        // Tail
        sb.append(", tail: ");
        if (tail != null) {
            int tailDisplayCount = tail.getDisplayCount();
            sb.append(tailDisplayCount).append(" displays, ")
              .append(tail.getViewers().size()).append(" viewers");
        } else {
            sb.append("null");
        }
        
        sb.append(", viewers: ").append(viewers.size()).append("]");
        return sb.toString();
    }

    public String serialize() {
        return toString();
    }
}
