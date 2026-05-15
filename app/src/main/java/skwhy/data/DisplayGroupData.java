package skwhy.data;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import java.util.*;

/**
 * Classe pour gérer un groupe de display entities.
 * Permet de contrôler plusieurs displays en même temps avec une position et une monture communes.
 */
public class DisplayGroupData {
    
    private List<Player> viewers; // Joueurs pouvant voir ce groupe
    private List<DisplayData> displays; // Liste des displays dans ce groupe
    private Entity mountedEntity; // Entité sur laquelle le groupe est monté (null = libre)
    private String world;
    private double x, y, z;
    private float yaw, pitch;
    
    /**
     * Constructeur du groupe de displays.
     */
    public DisplayGroupData() {
        this.viewers = new ArrayList<>();
        this.displays = new ArrayList<>();
        this.mountedEntity = null;
        this.world = "world";
        this.x = 0;
        this.y = 0;
        this.z = 0;
        this.yaw = 0;
        this.pitch = 0;
    }
    
    // ── Getters et Setters pour la liste des viewers ──
    public List<Player> getViewers() {
        return new ArrayList<>(viewers);
    }
    
    public boolean hasViewer(Player player) {
        return viewers.contains(player);
    }
    
    // ── Getters et Setters pour la liste des displays ──
    public List<DisplayData> getDisplays() {
        return new ArrayList<>(displays);
    }
    
    /**
     * Ajoute une display au groupe.
     * La display sera liée à ce groupe et utilisera ses paramètres.
     */
    public void addDisplay(DisplayData display) {
        if (display != null && !displays.contains(display)) {
            displays.add(display);
        }
    }
    
    /**
     * Retire une display du groupe.
     * La display redevient indépendante.
     */
    public void removeDisplay(DisplayData display) {
        if (display != null && displays.contains(display)) {
            displays.remove(display);
        }
    }
    
    /**
     * Ajoute un joueur à la liste des viewers du groupe.
     * Ajoute également le joueur aux viewers de toutes les displays du groupe.
     */
    public void addViewer(Player player) {
        if (player != null && !viewers.contains(player)) {
            viewers.add(player);
        }
    }
    
    /**
     * Retire un joueur de la liste des viewers du groupe.
     * Retire également le joueur des viewers de toutes les displays du groupe.
     */
    public void removeViewer(Player player) {
        if (player != null && viewers.contains(player)) {
            viewers.remove(player);
        }
    }
    
    // ── Getters et Setters pour la position et la monture ──
    public String getWorld() {
        return world;
    }
    
    public void setWorld(String world) {
        this.world = world;
    }
    
    public double getX() {
        if (mountedEntity != null && mountedEntity.isValid()) {
            return mountedEntity.getLocation().getX();
        }
        return x;
    }
    
    public void setX(double x) {
        this.x = x;
        this.mountedEntity = null;
    }
    
    public double getY() {
        if (mountedEntity != null && mountedEntity.isValid()) {
            return mountedEntity.getLocation().getY();
        }
        return y;
    }
    
    public void setY(double y) {
        this.y = y;
        this.mountedEntity = null;
    }
    
    public double getZ() {
        if (mountedEntity != null && mountedEntity.isValid()) {
            return mountedEntity.getLocation().getZ();
        }
        return z;
    }
    
    public void setZ(double z) {
        this.z = z;
        this.mountedEntity = null;
    }
    
    public void setLocation(String world, double x, double y, double z) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.mountedEntity = null;
    }
    
    public float getYaw() {
        return yaw;
    }
    
    public void setYaw(float yaw) {
        this.yaw = yaw;
    }
    
    public float getPitch() {
        return pitch;
    }
    
    public void setPitch(float pitch) {
        this.pitch = pitch;
    }
    
    public Entity getMountedEntity() {
        return mountedEntity;
    }
    
    public void setMountedEntity(Entity entity) {
        this.mountedEntity = entity;
    }
    
    /**
     * Met à jour tous les displays du groupe avec les nouvelles valeurs.
     * Les arguments doivent être des paires clé-valeur.
     */
    public void updateGroup(Object... args) {
        // Mettre à jour les paramètres du groupe
        for (int i = 0; i < args.length - 1; i += 2) {
            String key = String.valueOf(args[i]);
            Object value = args[i + 1];
            
            switch (key.toLowerCase()) {
                case "world" -> setWorld(String.valueOf(value));
                case "x" -> setX(((Number) value).doubleValue());
                case "y" -> setY(((Number) value).doubleValue());
                case "z" -> setZ(((Number) value).doubleValue());
                case "yaw" -> setYaw(((Number) value).floatValue());
                case "pitch" -> setPitch(((Number) value).floatValue());
                case "entity" -> setMountedEntity((Entity) value);
            }
        }
        
    }
}
