package skwhy.data;

import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.Bukkit;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Classe pour gérer un groupe de display entities avec une position ou une attache fixe.
 */
public class DisplayGroupData {
    private static final Map<Integer, List<DisplayGroupData>> entityGroupMount = new HashMap<>();
    private static final Map<Integer, List<Integer>> entityOtherMount = new HashMap<>();
    
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
        setAttachedId(attachedEntity.getEntityId());
        this.yaw = attachedEntity.getLocation().getYaw();
        this.pitch = attachedEntity.getLocation().getPitch();
    }
    public DisplayGroupData(Location location, Integer attachedId) {
        this(location);
        setAttachedId(attachedId);
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
        setAttachedId(attachedId);
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
        if (attachedId != null) {
            removeDisplayGroupMount(attachedId);
        }
        this.attachedId = null;
        sendMovePacket();
    }

    public Location getLocation() {
        if (attachedEntity != null) {
            if (attachedEntity.isValid()) {
                Location location = (attachedEntity instanceof LivingEntity living) ? living.getEyeLocation().clone() : attachedEntity.getLocation().clone();
                location.setYaw(yaw);
                location.setPitch(pitch);
                return location;
            }
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
                    if (attachedId != null) finalMount(viewers, attachedId);
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
        
        if (attachedId != null) {
            finalMount(List.of(player), attachedId);
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
                finalMount(players, attachedId);
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
            finalMount(viewers, attachedId);
        }
    }
    
    public static void addOtherMount(int vehicleId, int passengerId, List<Player> targetPlayers) {
        entityOtherMount.computeIfAbsent(vehicleId, k -> new ArrayList<>()).add(passengerId);
        finalMount(targetPlayers, vehicleId);
    }

    public static void addOtherMount(Entity vehicle, int passengerId, List<Player> targetPlayers) {
        entityOtherMount.computeIfAbsent(vehicle.getEntityId(), k -> new ArrayList<>()).add(passengerId);
        finalMount(targetPlayers, vehicle);
    }

    public static void removeOtherMount(int vehicleId, int passengerId, List<Player> targetPlayers) {
        List<Integer> passengers = entityOtherMount.get(vehicleId);
        if (passengers != null) {
            passengers.remove(Integer.valueOf(passengerId));
            if (passengers.isEmpty()) {
                entityOtherMount.remove(vehicleId);
            }
            finalMount(targetPlayers, vehicleId);
        }
    }

    public static void removeOtherMount(Entity vehicle, int passengerId, List<Player> targetPlayers) {
        List<Integer> passengers = entityOtherMount.get(vehicle.getEntityId());
        if (passengers != null) {
            passengers.remove(Integer.valueOf(passengerId));
            if (passengers.isEmpty()) {
                entityOtherMount.remove(vehicle.getEntityId());
            }
            finalMount(targetPlayers, vehicle);
        }
    }

    private void addDisplayGroupMount(Integer entityId) {
        entityGroupMount.computeIfAbsent(entityId, k -> new ArrayList<>()).add(this);
        finalMount(viewers, entityId);
    }

    private void removeDisplayGroupMount(Integer entityId) {
        if (entityId == null) return;
        List<DisplayGroupData> list = entityGroupMount.get(entityId);
        
        if (list != null) {
            list.remove(this);
            
            if (list.isEmpty()) {
                entityGroupMount.remove(entityId);
            }
            finalMount(viewers, entityId);
        }
    }

    /**
     * Attache le groupe à une entité. La position sera celle de l'entité.
     */
    public void setAttachedEntity(Entity entity) {
        if (entity == null) return;
        this.attachedEntity = entity;
        this.location = null;
        setAttachedId(entity.getEntityId());
        sendMovePacket();
        finalMount(viewers, entity);
    }

    private void setAttachedId(Integer entityId) {
        if (entityId == null) return;
        if (attachedId != null) {
            removeDisplayGroupMount(attachedId);
        }
        this.attachedId = entityId;
        addDisplayGroupMount(attachedId);
    }

    /**
     * Attache le groupe à une entité via son ID.
     */
    public void setAttachedId(Location location, Integer entityId) {
        if (entityId == null) return;
        this.attachedEntity = null;
        this.location = location.clone();
        setAttachedId(entityId);
        addDisplayGroupMount(attachedId);
        sendMovePacket();
        finalMount(viewers, attachedId);
    }


    private static void finalMount(List<Player> targetPlayers, Entity vehicle) {
        if (vehicle == null) return;
        finalMount(targetPlayers, vehicle.getEntityId(), vehicle.getPassengers().stream()
            .map(Entity::getEntityId)
            .collect(Collectors.toList())
);
    }

    private static void finalMount(List<Player> targetPlayers, int vehicleId) {
        finalMount(targetPlayers, vehicleId, new ArrayList<>());
    }

    private static void finalMount(List<Player> targetPlayers, int vehicleId, List<Integer> passengerIds) {
        Bukkit.getLogger().info("FinalMount: Véhicule ID " + vehicleId + " avec passagers " + passengerIds + " pour joueurs " + targetPlayers.stream().map(Player::getName).toList());
        for (List<DisplayGroupData> groupList : entityGroupMount.values()) {
            for (DisplayGroupData group : groupList) {
                for (DisplayData display : group.getDisplays()) {
                    passengerIds.add(display.getEntityId());
                }
            }
        }
        for (List<Integer> groupList : entityOtherMount.values()) {
            passengerIds.addAll(groupList);
        }

        WrapperPlayServerSetPassengers passengerPacket = new WrapperPlayServerSetPassengers(
            vehicleId,
            passengerIds.stream().mapToInt(Integer::intValue).toArray()
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


    public void delete() {
        sendDestroyPacket(displays, viewers);
        displays.clear();
        viewers.clear();
        if (attachedId != null) {
            removeDisplayGroupMount(attachedId);
        }
        attachedEntity = null;
        location = null;
        attachedId = null;
    }

// ─────────────────────────────────────────────────────────────────────────
    // ── Méthodes d'Affichage et Debug ──
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Affichage NORMAL : Format compact, idéal pour les logs standards ou l'affichage en jeu.
     */
    @Override
    public String toString() {
        String target = "Statique";
        if (attachedEntity != null) {
            target = "Attaché à l'Entité (Type: " + attachedEntity.getType().name() + ", ID: " + attachedId + ")";
        } else if (attachedId != null) {
            target = "Attaché à l'ID Entité: " + attachedId;
        }

        Location loc = getLocation();
        String coords;
        
        // Sécurité maximale : on vérifie que loc ET loc.getWorld() ne soient pas null
        if (loc != null && loc.getWorld() != null) {
            coords = String.format("%.2f, %.2f, %.2f dans %s", loc.getX(), loc.getY(), loc.getZ(), loc.getWorld().getName());
        } else if (loc != null) {
            coords = String.format("%.2f, %.2f, %.2f (Monde non chargé/indéfini)", loc.getX(), loc.getY(), loc.getZ());
        } else {
            coords = "Inconnue ou non initialisée";
        }

        return "DisplayGroup[Target: " + target + 
               "; Pos: [" + coords + "]" +
               "; Yaw/Pitch: " + String.format("%.1f°/%.1f°", yaw, pitch) +
               "; Displays: " + displays.size() + 
               "; Viewers: " + viewers.size() + "]";
    }
    
    public String serialize() {
        return String.format(
            "DisplayGroup[Target: %s; Pos: %s; Yaw/Pitch: %.1f/%.1f; Displays: %d; Viewers: %d]",
            (attachedEntity != null) ? "Entité (ID: " + attachedId + ")" : (attachedId != null ? "ID Entité: " + attachedId : "Statique"),
            (getLocation() != null) ? String.format("%.2f, %.2f, %.2f", getLocation().getX(), getLocation().getY(), getLocation().getZ()) : "Inconnue",
            yaw, pitch, displays.size(), viewers.size()
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ── Miroir et Clonage du Groupe ──
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Applique un effet miroir sur toutes les displays contenues dans ce groupe 
     * selon les axes spécifiés (X, Y, Z).
     *
     * @param x true pour appliquer un miroir sur l'axe X
     * @param y true pour appliquer un miroir sur l'axe Y
     * @param z true pour appliquer un miroir sur l'axe Z
     */
    public void mirror(boolean x, boolean y, boolean z) {
        if (!x && !y && !z) return;

        globalTransformation.mirror(x, y, z);
        // On répercute le miroir sur chacune des displays du groupe
        for (DisplayData display : displays) {
            display.mirror(x, y, z);
        }

        // Si le groupe est actif (des joueurs le regardent), on rafraîchit la metadata visuelle
        if (!viewers.isEmpty()) {
            updateMetadata();
        }
    }

    /**
     * Crée une copie indépendante (clone) de ce groupe de displays.
     * Chaque display du groupe d'origine est clonée et inversée selon les axes demandés.
     * Le nouveau groupe hérite de la position ou de l'ancrage de l'original, mais pas de ses viewers.
     *
     * @param mirrorX true pour appliquer un miroir sur l'axe X au moment du clonage
     * @param mirrorY true pour appliquer un miroir sur l'axe Y au moment du clonage
     * @param mirrorZ true pour appliquer un miroir sur l'axe Z au moment du clonage
     * @return Une nouvelle instance de DisplayGroupData clonée et transformée
     */
    public DisplayGroupData clone(boolean mirrorX, boolean mirrorY, boolean mirrorZ) {
        DisplayGroupData clonedGroup;

        // 1. Initialisation du clone selon le type d'ancrage d'origine
        if (this.attachedEntity != null) {
            clonedGroup = new DisplayGroupData(this.attachedEntity);
        } else if (this.attachedId != null) {
            clonedGroup = new DisplayGroupData(this.location, this.attachedId);
        } else {
            clonedGroup = new DisplayGroupData(this.location);
        }

        // 2. Duplication des attributs d'orientation
        clonedGroup.yaw = this.yaw;
        clonedGroup.pitch = this.pitch;

        // 3. Duplication de la matrice de transformation globale
        clonedGroup.globalTransformation.setScale(this.getScale());
        clonedGroup.globalTransformation.setTranslation(this.getTranslation());
        clonedGroup.globalTransformation.setCentreRotation(this.getCenter());
        clonedGroup.globalTransformation.setRotation(this.getRotation());

        // 4. Clonage individuel et application du miroir sur chaque display
        for (DisplayData originalDisplay : this.displays) {
            // Utilise la méthode polymorphique d'instance écrite sur DisplayData
            DisplayData clonedDisplay = originalDisplay.clone(mirrorX, mirrorY, mirrorZ);
            
            // On l'ajoute au nouveau groupe (l'attachement à la GlobalTransformation du clone se fait ici)
            clonedGroup.addDisplay(clonedDisplay);
        }
        clonedGroup.sendSpawnPacket(viewers);
        return clonedGroup;
    }
    
    public DisplayGroupData clone() {
        return clone(false, false, false);
    }

    // Cette méthode peut être appelée lors du départ d'un joueur pour nettoyer les références
    // et éviter les fuites de mémoire. Elle doit être appelée depuis un listener de PlayerQuitEvent.
    // Exemple d'utilisation : DisplayGroupData.clearEntityData(event.getEntity());
    public static void clearEntityData(Entity entity) {
        entityGroupMount.remove(entity.getEntityId());
        entityOtherMount.remove(entity.getEntityId());
    }

}