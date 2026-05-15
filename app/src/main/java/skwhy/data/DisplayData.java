package skwhy.data;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityType;
import com.github.retrooper.packetevents.util.Quaternion4f;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;

import io.github.retrooper.packetevents.util.SpigotReflectionUtil;
import org.bukkit.Location;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public abstract class DisplayData {

    // ── IDs uniques pour les fake entities ────────────────────────────────────

    private final int entityId = SpigotReflectionUtil.generateEntityId();
    private final UUID entityUUID = UUID.randomUUID();

    // ── Propriétés visuelles uniquement (plus de position) ────────────────────
    protected float scaleX = 1f, scaleY = 1f, scaleZ = 1f;
    protected int   glowColor     = -1;   // -1 = pas de glow
    protected float shadowRadius  = 0f;
    protected float shadowStrength = 1f;
    protected float viewRange     = 128f;
    protected int   billboardMode = 0;    // 0=fixed, 1=vertical, 2=horizontal, 3=center

    // ── Cache du packet compilé ───────────────────────────────────────────────
    private CompiledDisplayPacket cachedPacket    = null;
    private boolean               packetDirty     = true;

    // ─────────────────────────────────────────────────────────────────────────
    // Packet compilé : contient spawn + metadata, prêt à envoyer
    // ─────────────────────────────────────────────────────────────────────────

    public static final class CompiledDisplayPacket {

        private final WrapperPlayServerSpawnEntity   spawnPacket;
        private final WrapperPlayServerEntityMetadata metadataPacket;
        private final int entityId;

        private CompiledDisplayPacket(int entityId,
                                      WrapperPlayServerSpawnEntity spawn,
                                      WrapperPlayServerEntityMetadata metadata) {
            this.entityId       = entityId;
            this.spawnPacket    = spawn;
            this.metadataPacket = metadata;
        }

        /** Envoie spawn + metadata à un joueur. */
        public void send(org.bukkit.entity.Player player) {
            var user = PacketEvents.getAPI().getPlayerManager().getUser(player);
            if (user == null) return;
            user.sendPacket(spawnPacket);
            user.sendPacket(metadataPacket);
        }

        /** Envoie à plusieurs joueurs d'un coup (boucle, PacketEvents ne supporte pas le broadcast natif). */
        public void sendToAll(List<org.bukkit.entity.Player> players) {
            for (var player : players) send(player);
        }

        public int getEntityId() { return entityId; }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Méthode principale
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Retourne le packet compilé de création de l'entité display.
     *
     * @param location Si non-null, recrée entièrement le packet avec cette position.
     *                 Si null, retourne le packet déjà en cache (doit avoir été construit au moins une fois).
     * @return Le packet compilé, ou null si location est null et qu'aucun packet n'a encore été créé.
     */
    @Nullable
    public CompiledDisplayPacket getPacket(@Nullable Location location) {

        // ── Cas 1 : pas de localisation → retourne le cache tel quel ──────────
        if (location == null || cachedPacket == null || packetDirty) {
            return cachedPacket; // null si jamais construit, c'est au caller de vérifier
        }

        // ── Cas 2 : localisation fournie → recréer le packet complet ──────────

        // Spawn packet (position + entity type)
        WrapperPlayServerSpawnEntity spawnPacket = new WrapperPlayServerSpawnEntity(
            entityId,
            Optional.of(entityUUID),
            getEntityType(),
            new Vector3d(location.getX(), location.getY(), location.getZ()),
            location.getPitch(),
            location.getYaw(),
            location.getYaw(),
            0,
            Optional.empty()
        );

        // Metadata packet (propriétés visuelles)
        WrapperPlayServerEntityMetadata metadataPacket = new WrapperPlayServerEntityMetadata(
            entityId, 
            buildMetadata()
        );

        cachedPacket = new CompiledDisplayPacket(entityId, spawnPacket, metadataPacket);
        packetDirty  = false;

        return cachedPacket;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Construction de la metadata commune (display entity, indices wiki.vg)
    // ─────────────────────────────────────────────────────────────────────────

    private List<EntityData<?>> buildMetadata() {
        List<EntityData<?>> data = new ArrayList<>();

        // Index 8  – interpolation start delta (0 = immédiat)
        data.add(new EntityData<>(8, EntityDataTypes.INT, 0));

        // Index 9  – interpolation duration
        data.add(new EntityData<>(9, EntityDataTypes.INT, 0));

        // Index 11 – translation offset (toujours 0,0,0 ici, la vraie position est dans spawn)
        data.add(new EntityData<>(11, EntityDataTypes.VECTOR3F, new Vector3f(0f, 0f, 0f)));

        // Index 12 – scale
        data.add(new EntityData<>(12, EntityDataTypes.VECTOR3F, new Vector3f(scaleX, scaleY, scaleZ)));

        // Index 13 – left rotation (quaternion identité)
        data.add(new EntityData<>(13, EntityDataTypes.QUATERNION, new Quaternion4f(0f, 0f, 0f, 1f)));

        // Index 14 – right rotation (quaternion identité)
        data.add(new EntityData<>(14, EntityDataTypes.QUATERNION, new Quaternion4f(0f, 0f, 0f, 1f)));

        // Index 15 – billboard mode (byte)
        data.add(new EntityData<>(15, EntityDataTypes.BYTE, (byte) billboardMode));

        // Index 16 – brightness override (-1 = utilise la valeur du monde)
        data.add(new EntityData<>(16, EntityDataTypes.INT, -1));

        // Index 17 – view range
        data.add(new EntityData<>(17, EntityDataTypes.FLOAT, viewRange));

        // Index 18 – shadow radius
        data.add(new EntityData<>(18, EntityDataTypes.FLOAT, shadowRadius));

        // Index 19 – shadow strength
        data.add(new EntityData<>(19, EntityDataTypes.FLOAT, shadowStrength));

        // Index 22 – glow color override (-1 = aucun)
        data.add(new EntityData<>(22, EntityDataTypes.INT, glowColor));

        // Indices spécifiques au sous-type (block/item/text)
        data.addAll(buildSpecificMetadata());

        return data;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Méthodes abstraites à implémenter dans les sous-classes
    // ─────────────────────────────────────────────────────────────────────────

    /** Retourne les EntityData spécifiques au type (index 23+). */
    protected abstract List<EntityData<?>> buildSpecificMetadata();

    /** Retourne le type d'entité PacketEvents (BLOCK_DISPLAY, ITEM_DISPLAY, TEXT_DISPLAY). */
    public abstract EntityType getEntityType();

    /** Retourne le type sous forme de String (pour sérialisation Skript). */
    public abstract String getDisplayType();

    /** Sérialise les données en String. */
    public abstract String serialize();

    // ─────────────────────────────────────────────────────────────────────────
    // Invalidation du cache quand une propriété change
    // ─────────────────────────────────────────────────────────────────────────

    protected void markDirty() { this.packetDirty = true; }
    public boolean isPacketDirty() { return packetDirty; }
    public int getEntityId() { return entityId; }
    public UUID getEntityUUID() { return entityUUID; }

    // ── Getters / Setters (chaque setter invalide le cache) ───────────────────

    public float getScaleX() { return scaleX; }
    public void setScaleX(float v) { scaleX = v; markDirty(); }

    public float getScaleY() { return scaleY; }
    public void setScaleY(float v) { scaleY = v; markDirty(); }

    public float getScaleZ() { return scaleZ; }
    public void setScaleZ(float v) { scaleZ = v; markDirty(); }

    public void setScale(float x, float y, float z) { scaleX=x; scaleY=y; scaleZ=z; markDirty(); }

    public int getGlowColor() { return glowColor; }
    public void setGlowColor(int rgb) { glowColor = rgb; markDirty(); }

    public float getShadowRadius() { return shadowRadius; }
    public void setShadowRadius(float v) { shadowRadius = v; markDirty(); }

    public float getShadowStrength() { return shadowStrength; }
    public void setShadowStrength(float v) { shadowStrength = v; markDirty(); }

    public float getViewRange() { return viewRange; }
    public void setViewRange(float v) { viewRange = v; markDirty(); }

    public int getBillboardMode() { return billboardMode; }
    public void setBillboardMode(int v) { billboardMode = v; markDirty(); }
}