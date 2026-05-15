package skwhy.data;

import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.Location;

import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRotation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPassengers;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityHeadLook;

import java.util.ArrayList;
import java.util.List;

/**
 * Classe pour gérer un groupe de display entities avec une position ou une attache fixe.
 */
public class DisplayGroupData {
    
    private final List<Player> viewers;
    private final List<DisplayData> displays;
    private final GlobalTransformation globalTransformation;
    
    // Position du groupe : soit une location statique, soit une entité mobile
    private Location location;
    private Entity attachedEntity;
    private float yaw, pitch;
    private Integer attachedId;
    
    private DisplayGroupData() {
        this.viewers = new ArrayList<>();
        this.displays = new ArrayList<>();
        this.globalTransformation = new GlobalTransformation();
    }

    public DisplayGroupData(Location location) {
        this();
        this.location = location.clone();
        this.yaw = location.getYaw();
        this.pitch = location.getPitch();
    }
    public DisplayGroupData(Entity attachedEntity) {
        this();
        this.attachedEntity = attachedEntity;
        this.attachedId = attachedEntity.getEntityId();
        this.yaw = attachedEntity.getLocation().getYaw();
        this.pitch = attachedEntity.getLocation().getPitch();
    }
    public DisplayGroupData(Location location, Integer attachedId) {
        this(location);
        this.attachedId = attachedId;
    }
    public DisplayGroupData(List<DisplayData> displays, Location location) {
        this(location);
        addDisplay(displays);
    }
    public DisplayGroupData(List<DisplayData> displays, Entity attachedEntity) {
        this(attachedEntity);
        addDisplay(displays);
    }
    public DisplayGroupData(List<DisplayData> displays, Location location, Integer attachedId) {
        this(displays, location);
        this.attachedId = attachedId;
    }
    public DisplayGroupData(List<DisplayData> displays, List<Player> viewers, Location location) {
        this(displays,location);
        this.viewers.addAll(viewers);
    }
    public DisplayGroupData(List<DisplayData> displays, List<Player> viewers, Entity attachedEntity) {
        this(displays,attachedEntity);
        this.viewers.addAll(viewers);
    }
    public DisplayGroupData(List<DisplayData> displays, List<Player> viewers, Location location, Integer attachedId) {
        this(displays, location, attachedId);
        this.viewers.addAll(viewers);
    }

    // ── Gestion de la Position et de l'Attache ──

    /**
     * Définit une position statique pour le groupe et retire toute attache à une entité.
     */
    public void setLocation(Location location) {
        if (location == null) return;
        this.location = location;
        this.attachedEntity = null;
        this.attachedId = null;
        sendMovePacket();
    }

    /**
     * Attache le groupe à une entité. La position sera celle de l'entité.
     */
    public void setAttachedEntity(Entity entity) {
        if (entity == null) return;
        this.attachedEntity = entity;
        this.location = null;
        this.attachedId = entity.getEntityId();
        sendMovePacket();
        mount();
    }

    /**
     * Attache le groupe à une entité via son ID.
     */
    public void setAttachedId(Location location, Integer entityId) {
        if (entityId == null) return;
        this.attachedEntity = null;
        this.location = location.clone();
        this.attachedId = entityId;
        sendMovePacket();
        mount();
    }

    public Location getLocation() {
        if (attachedEntity != null) {
            if (attachedEntity.isValid()) return (attachedEntity instanceof LivingEntity living) ? living.getEyeLocation().clone() : attachedEntity.getLocation().clone();
            else {
                sendDestroyPacket(displays, viewers);
                return null;
            }
        } else if (location != null) {
            return location.clone();
        } else {
            sendDestroyPacket(displays, viewers);
            return null;
        }
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public void setYawPitch(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
        if (attachedEntity == null){
            this.location.setYaw(yaw);
            this.location.setPitch(pitch);
            sendMovePacket();
        } else {
            sendRotation();
        }
    }

    public Entity getAttachedEntity() {
        return attachedEntity;
    }

    public Integer getAttachedId() {
        return attachedId;
    }

    // ── Gestion des Displays ──

    public List<DisplayData> getDisplays() {
        return new ArrayList<>(displays);
    }
    
    public void addDisplay(DisplayData display) {
        if (display != null && !displays.contains(display)) {
            if (display.setGlobalTransformation(globalTransformation)) {
                displays.add(display);
                // Si des joueurs regardent déjà, on fait apparaître la nouvelle display
                Location currentPos = getLocation();
                if (currentPos != null && !viewers.isEmpty()) {
                    DisplayData.CompiledDisplayPacket packet = display.getSpawnPacket(currentPos);
                    if (packet != null) packet.sendToAll(viewers);
                    if (attachedId != null) mountOnEntity(attachedId, viewers);
                }
            }
        }
    }
    
    public void addDisplay(List<DisplayData> displays) {
        for (DisplayData display : displays) addDisplay(display);
    }

    public void removeDisplay(DisplayData display) {
        if (display != null && displays.contains(display)) {
            sendDestroyPacket(List.of(display), viewers);
            displays.remove(display);
        }
    }

    public void removeDisplay(List<DisplayData> displaysToRemove) {
        if (displaysToRemove == null || displaysToRemove.isEmpty()) return;
        sendDestroyPacket(displaysToRemove, viewers);
        displays.removeAll(displaysToRemove);
    }

    public void clearDisplays() {
        sendDestroyPacket(displays, viewers);
        displays.clear();
    }

    // ── Gestion des Viewers ──

    public List<Player> getViewers() {
        return new ArrayList<>(viewers);
    }

    /**
     * Ajoute un ou plusieurs joueurs aux viewers en utilisant la position enregistrée.
     */
    public void addViewer(Player player) {
        if (player == null || viewers.contains(player)) return;
        
        Location spawnLoc = getLocation();
        if (spawnLoc == null) return;

        viewers.add(player);
        
        // 1. Spawn des entités
        sendSpawnPacket(List.of(player));
        
        // 2. Montage automatique si une entité est définie
        if (attachedId != null) {
            mountOnEntity(attachedId, List.of(player));
        }
    }

    public void addViewer(List<Player> players) {
        if (players == null) return;
        for (Player p : players) addViewer(p);
    }

    public void removeViewer(Player player) {
        if (player != null && viewers.contains(player)) {
            sendDestroyPacket(displays, List.of(player));
            viewers.remove(player);
        }
    }
    
    public void removeViewer(List<Player> players) {
        if (players == null) return;
        sendDestroyPacket(displays, players);
        viewers.removeAll(players);
    }

    public void clearViewers() {
        sendDestroyPacket(displays, viewers);
        viewers.clear();
    }

    // ── Envoi des Paquets (Utilise l'état interne) ──

    public void sendSpawnPacket(List<Player> players) {
        if (players.isEmpty() || displays.isEmpty()) return;
        for (DisplayData display : displays) {
            Location spawnLoc = getLocation();
            if (spawnLoc == null) return;
            DisplayData.CompiledDisplayPacket packet = display.getSpawnPacket(spawnLoc);
            if (packet == null) return;
            packet.sendToAll(players);
            if (attachedId != null) {
                mountOnEntity(attachedId, players);
            }
        }
    }

    public void sendDestroyPacket(List<DisplayData> displaysToRemove, List<Player> targetPlayers) {
        if (displaysToRemove.isEmpty() || targetPlayers.isEmpty()) return;
        
        int[] ids = displaysToRemove.stream().mapToInt(DisplayData::getEntityId).toArray();
        WrapperPlayServerDestroyEntities destroyPacket = new WrapperPlayServerDestroyEntities(ids);

        for (Player player : targetPlayers) {
            var user = PacketEvents.getAPI().getPlayerManager().getUser(player);
            if (user != null) user.sendPacket(destroyPacket);
        }
    }

    /**
     * Téléporte toutes les displays du groupe à la position actuelle (getPosition()).
     * Utile pour rafraîchir la position ou déplacer un groupe statique.
     */
    private void sendMovePacket() {
        Location loc = getLocation();
        if (loc == null || viewers.isEmpty() || displays.isEmpty()) return;

        for (DisplayData display : displays) {
            // Création du paquet de téléportation pour l'entité
            WrapperPlayServerEntityTeleport teleportPacket = new WrapperPlayServerEntityTeleport(
                display.getEntityId(),
                new Vector3d(loc.getX(), loc.getY(), loc.getZ()),
                loc.getYaw(),
                loc.getPitch(),
                false // onGround
            );

            // Envoi à tous les viewers
            for (Player player : viewers) {
                var user = PacketEvents.getAPI().getPlayerManager().getUser(player);
                if (user != null) {
                    user.sendPacket(teleportPacket);
                }
            }
        }
    }

    /**
     * Envoie uniquement la rotation (yaw/pitch) à tous les viewers.
     * Les displays montées sur une entité sont ignorées — elles héritent
     * de la rotation de leur monture et ne doivent pas recevoir de packet
     * individuel qui pourrait entrer en conflit.
     */
    public void sendRotation() {
        if (viewers.isEmpty() || displays.isEmpty()) return;

        // Si le groupe entier est monté sur une entité, sa rotation
        // est dictée par la monture → on n'envoie rien
        if (attachedId != null) return;

        for (DisplayData display : displays) {
            WrapperPlayServerEntityRotation rotationPacket =
                new WrapperPlayServerEntityRotation(
                    display.getEntityId(),
                    yaw,
                    pitch,
                    false // onGround
                );

            // On envoie aussi le head yaw pour que la rotation soit cohérente
            WrapperPlayServerEntityHeadLook headLookPacket =
                new WrapperPlayServerEntityHeadLook(
                    display.getEntityId(),
                    yaw
                );

            for (Player player : viewers) {
                var user = PacketEvents.getAPI().getPlayerManager().getUser(player);
                if (user == null) continue;
                user.sendPacket(rotationPacket);
                user.sendPacket(headLookPacket);
            }
        }
    }

    public void updateMetadata() {
        if (viewers.isEmpty() || displays.isEmpty()) return;

        for (DisplayData display : displays) {
            WrapperPlayServerEntityMetadata updatePacket = display.getUpdatePacket();
            if (updatePacket != null) {
                for (Player player : viewers) {
                    var user = PacketEvents.getAPI().getPlayerManager().getUser(player);
                    if (user != null) user.sendPacket(updatePacket);
                }
            }
        }
    }

    // ── Montage (Mount) ──

    /**
     * Force le montage des displays sur l'entité enregistrée pour tous les viewers.
     */
    public void mount() {
        if (attachedId != null && !viewers.isEmpty()) {
            mountOnEntity(attachedId, viewers);
        }
    }

    private void mountOnEntity(int targetEntityId, List<Player> targetPlayers) {
        if (displays.isEmpty() || targetPlayers.isEmpty()) return;

        WrapperPlayServerSetPassengers passengerPacket = new WrapperPlayServerSetPassengers(
            targetEntityId,
            displays.stream().mapToInt(DisplayData::getEntityId).toArray()
        );

        for (Player player : targetPlayers) {
            var user = PacketEvents.getAPI().getPlayerManager().getUser(player);
            if (user != null) user.sendPacket(passengerPacket);
        }
    }

    // ── Transformation Globale ──

    public GlobalTransformation getGlobalTransformation() {
        return globalTransformation;
    }

    public void setRotation(Quat4 rotation) {
        if (rotation != null) {
            globalTransformation.setRotation(rotation);
            for (DisplayData display : displays) {
                display.updateGlobalTransformation(false, true, false);
            }
        }
    }
    public void setTranslation(Vec3 translation) {
        if (translation != null) {
            globalTransformation.setTranslation(translation);
            for (DisplayData display : displays) {
                display.updateGlobalTransformation(true, false, false);
            }
        }
    }

    public void setCenter(Vec3 centreRotation) {
        if (centreRotation != null) {
            globalTransformation.setCentreRotation(centreRotation);
            for (DisplayData display : displays) {
                display.updateGlobalTransformation(true, false, false);
            }
        }
    }

    public void setScale(float scale) {
        globalTransformation.setScale(scale);
        for (DisplayData display : displays) {
            display.updateGlobalTransformation(false, false, true);
        }
    }

    public Vec3 getTranslation() {
        return globalTransformation.getTranslation();
    }

    public Vec3 getCenter() {
        return globalTransformation.getCentreRotation();
    }

    public Quat4 getRotation() {
        return globalTransformation.getRotation();
    }

    public float getScale() {
        return globalTransformation.getScale();
    }

}