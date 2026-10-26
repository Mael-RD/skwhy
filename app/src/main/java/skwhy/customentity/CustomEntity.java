package skwhy.customentity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import io.github.retrooper.packetevents.util.SpigotReflectionUtil;

/**
 * Une instance d'entité custom. Réelle : une entité interaction du serveur qui porte l'id (le visuel est envoyé par packet
 * aux joueurs qui la suivent). Fausse : aucune entité sur le serveur, une interaction et un visuel envoyés par packet.
 */
public final class CustomEntity {

    private String definitionId;
    private final UUID realUuid;
    private final int visualId = SpigotReflectionUtil.generateEntityId();
    private final UUID visualUuid = UUID.randomUUID();
    private final Set<UUID> viewers = ConcurrentHashMap.newKeySet();

    // Entité fausse : l'interaction qui reçoit les clics et la position
    private final int hitboxId;
    private final UUID hitboxUuid;
    private Location location;

    // Dernière position envoyée du visuel (entité réelle suivie)
    private double sentX, sentY, sentZ;
    private float sentYaw, sentPitch;
    private boolean sentValid;
    private boolean destroyed;
    /** Yaw de la tête du visuel (l'IA le fait varier seul), {@code NaN} = celui de la position. */
    private float headYaw = Float.NaN;
    private float sentHead = Float.NaN;

    // Surcharges propres à cette entité (chemin YAML → String, Double ou Boolean) et la définition qui en résulte
    private final Map<String, Object> overrides = new LinkedHashMap<>();
    private CustomEntityDef cachedBase;
    private CustomEntityDef cachedEffective;
    /** IA refusée (type inconnu ou pas un mob) : on ne réessaie pas tant que l'IA demandée ne change pas. */
    private String failedAi;

    CustomEntity(String definitionId, Entity real) {
        this.definitionId = definitionId;
        this.realUuid = real.getUniqueId();
        this.hitboxId = real.getEntityId();
        this.hitboxUuid = real.getUniqueId();
    }

    CustomEntity(String definitionId, Location location) {
        this.definitionId = definitionId;
        this.realUuid = null;
        this.hitboxId = SpigotReflectionUtil.generateEntityId();
        this.hitboxUuid = UUID.randomUUID();
        this.location = location.clone();
    }

    public String definitionId() { return definitionId; }
    void definitionId(String id) { this.definitionId = id; }

    public boolean isReal() { return realUuid != null; }
    public boolean isFake() { return realUuid == null; }
    public UUID realUuid() { return realUuid; }
    public boolean isDestroyed() { return destroyed; }
    void markDestroyed() { destroyed = true; }

    public int visualId() { return visualId; }
    public UUID visualUuid() { return visualUuid; }
    public int hitboxId() { return hitboxId; }
    public UUID hitboxUuid() { return hitboxUuid; }

    /** Surcharges de cette entité (copie). */
    public Map<String, Object> overrides() { return new LinkedHashMap<>(overrides); }
    void overrides(Map<String, Object> values) {
        overrides.clear();
        overrides.putAll(values);
        cachedEffective = null;
    }
    boolean putOverride(String path, Object value) {
        cachedEffective = null;
        return !java.util.Objects.equals(overrides.put(path, value), value);
    }
    boolean removeOverride(String path) {
        cachedEffective = null;
        return overrides.remove(path) != null;
    }
    boolean clearOverrides() {
        cachedEffective = null;
        boolean had = !overrides.isEmpty();
        overrides.clear();
        return had;
    }

    /** Définition de l'entité : celle du fichier, avec ses surcharges. */
    CustomEntityDef effective(CustomEntityDef base) {
        if (overrides.isEmpty()) return base;
        if (cachedEffective == null || cachedBase != base) {
            List<String> errors = new ArrayList<>();
            cachedEffective = base.withOverrides(overrides, errors);
            cachedBase = base;
            for (String error : errors) skwhy.SkWhy.getInstance().getLogger().warning("[CustomEntity] surcharge ignorée : " + error);
        }
        return cachedEffective;
    }

    float headYaw() { return headYaw; }
    void headYaw(float yaw) { headYaw = yaw; }
    /** Yaw de la tête à envoyer pour une entité placée à ce yaw. */
    float headYawOr(float bodyYaw) { return Float.isNaN(headYaw) ? bodyYaw : headYaw; }
    String failedAi() { return failedAi; }
    void failedAi(String ai) { failedAi = ai; }

    /** Joueurs à qui le visuel est actuellement envoyé. */
    public Set<UUID> viewers() { return viewers; }

    /** Position d'une entité fausse (copie), {@code null} pour une entité réelle. */
    public Location fakeLocation() { return location == null ? null : location.clone(); }
    void fakeLocation(Location l) { this.location = l.clone(); }

    boolean sameAsSent(Location l) {
        return sentValid && Float.compare(sentHead, headYawOr(l.getYaw())) == 0 && sentX == l.getX() && sentY == l.getY() && sentZ == l.getZ() && sentYaw == l.getYaw() && sentPitch == l.getPitch();
    }

    void markSent(Location l) {
        sentX = l.getX();
        sentY = l.getY();
        sentZ = l.getZ();
        sentYaw = l.getYaw();
        sentPitch = l.getPitch();
        sentHead = headYawOr(l.getYaw());
        sentValid = true;
    }

    boolean isViewedBy(Player p) { return viewers.contains(p.getUniqueId()); }

    @Override
    public boolean equals(Object o) {
        return o instanceof CustomEntity other && other.visualId == visualId;
    }

    @Override
    public int hashCode() { return Integer.hashCode(visualId); }

    @Override
    public String toString() {
        return (isReal() ? "real" : "fake") + " custom entity " + definitionId;
    }
}
