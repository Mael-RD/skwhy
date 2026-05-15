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
    protected Vector3f scale = new Vector3f(1f, 1f, 1f);
    protected Vector3f translation = new Vector3f(0f, 0f, 0f);
    protected Quaternion4f leftRotation = new Quaternion4f(0f, 0f, 0f, 1f);
    protected Quaternion4f rightRotation = new Quaternion4f(0f, 0f, 0f, 1f);
    protected int   glowColor     = -1;   // -1 = pas de glow
    protected float shadowRadius  = 0f;
    protected float shadowStrength = 1f;
    protected float viewRange     = 128f;
    protected int   billboardMode = 0;    // 0=fixed, 1=vertical, 2=horizontal, 3=center
    protected int   interpolationStart = 0;
    protected int   interpolationDuration = 0;

    // ── Cache du packet compilé ───────────────────────────────────────────────
    protected List<EntityData<?>> cachedData = new ArrayList<>();

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
    public CompiledDisplayPacket getSpawnPacket(@Nullable Location location) {

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

        return new CompiledDisplayPacket(entityId, spawnPacket, metadataPacket);
    }

    /**
     * Retourne un packet de mise à jour basé sur le cache des données modifiées.
     * Si cachedData n'est pas vide, compile un packet metadata et réinitialise le cache.
     *
     * @return Le packet metadata compilé, ou null si cachedData est vide.
     */
    @Nullable
    public WrapperPlayServerEntityMetadata getUpdatePacket() {
        if (!cachedData.isEmpty()) {
            WrapperPlayServerEntityMetadata metadataPacket = new WrapperPlayServerEntityMetadata(
                entityId,
                cachedData
            );
            cachedData = new ArrayList<>();
            return metadataPacket;
        }
        return null;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Construction de la metadata commune (display entity, indices wiki.vg)
    // ─────────────────────────────────────────────────────────────────────────

    private List<EntityData<?>> buildMetadata() {
        List<EntityData<?>> data = new ArrayList<>();

        // Index 8  – interpolation start delta (0 = immédiat)
        data.add(new EntityData<>(8, EntityDataTypes.INT, interpolationStart));

        // Index 9  – interpolation duration
        data.add(new EntityData<>(9, EntityDataTypes.INT, interpolationDuration));

        // Index 11 – translation offset
        data.add(new EntityData<>(11, EntityDataTypes.VECTOR3F, translation));

        // Index 12 – scale
        data.add(new EntityData<>(12, EntityDataTypes.VECTOR3F, scale));

        // Index 13 – left rotation (quaternion)
        data.add(new EntityData<>(13, EntityDataTypes.QUATERNION, leftRotation));

        // Index 14 – right rotation (quaternion)
        data.add(new EntityData<>(14, EntityDataTypes.QUATERNION, rightRotation));

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

    /**
     * Retire l'EntityData avec l'index spécifié du cache s'il est présent.
     * Évite la duplication lors de la mise à jour d'une propriété.
     */
    protected void cachedDataRemove(int index) {
        cachedData.removeIf(data -> data.getIndex() == index);
    }

    public int getEntityId() { return entityId; }
    public UUID getEntityUUID() { return entityUUID; }

    // ── Getters / Setters (chaque setter invalide le cache) ───────────────────

    public Vector3f getScale() { return scale; }
    public void setScale(Vector3f v) {
        scale = v;
        cachedDataRemove(12);
        cachedData.add(new EntityData<>(12, EntityDataTypes.VECTOR3F, scale));
    }
    public void setScale(float x, float y, float z) {
        scale = new Vector3f(x, y, z);
        cachedDataRemove(12);
        cachedData.add(new EntityData<>(12, EntityDataTypes.VECTOR3F, scale));
    }

    public Vector3f getTranslation() { return translation; }
    public void setTranslation(Vector3f v) {
        translation = v;
        cachedDataRemove(11);
        cachedData.add(new EntityData<>(11, EntityDataTypes.VECTOR3F, translation));
    }
    public void setTranslation(float x, float y, float z) {
        translation = new Vector3f(x, y, z);
        cachedDataRemove(11);
        cachedData.add(new EntityData<>(11, EntityDataTypes.VECTOR3F, translation));
    }

    public Quaternion4f getLeftRotation() { return leftRotation; }
    public void setLeftRotation(Quaternion4f v) {
        leftRotation = v;
        cachedDataRemove(13);
        cachedData.add(new EntityData<>(13, EntityDataTypes.QUATERNION, leftRotation));
    }
    public void setLeftRotation(float x, float y, float z, float w) {
        leftRotation = new Quaternion4f(x, y, z, w);
        cachedDataRemove(13);
        cachedData.add(new EntityData<>(13, EntityDataTypes.QUATERNION, leftRotation));
    }

    public Quaternion4f getRightRotation() { return rightRotation; }
    public void setRightRotation(Quaternion4f v) {
        rightRotation = v;
        cachedDataRemove(14);
        cachedData.add(new EntityData<>(14, EntityDataTypes.QUATERNION, rightRotation));
    }
    public void setRightRotation(float x, float y, float z, float w) {
        rightRotation = new Quaternion4f(x, y, z, w);
        cachedDataRemove(14);
        cachedData.add(new EntityData<>(14, EntityDataTypes.QUATERNION, rightRotation));
    }

    public int getGlowColor() { return glowColor; }
    public void setGlowColor(int rgb) {
        glowColor = rgb;
        cachedDataRemove(22);
        cachedData.add(new EntityData<>(22, EntityDataTypes.INT, glowColor));
    }

    public float getShadowRadius() { return shadowRadius; }
    public void setShadowRadius(float v) {
        shadowRadius = v;
        cachedDataRemove(18);
        cachedData.add(new EntityData<>(18, EntityDataTypes.FLOAT, shadowRadius));
    }

    public float getShadowStrength() { return shadowStrength; }
    public void setShadowStrength(float v) {
        shadowStrength = v;
        cachedDataRemove(19);
        cachedData.add(new EntityData<>(19, EntityDataTypes.FLOAT, shadowStrength));
    }

    public float getViewRange() { return viewRange; }
    public void setViewRange(float v) {
        viewRange = v;
        cachedDataRemove(17);
        cachedData.add(new EntityData<>(17, EntityDataTypes.FLOAT, viewRange));
    }

    public int getBillboardMode() { return billboardMode; }
    public void setBillboardMode(int v) {
        billboardMode = v;
        cachedDataRemove(15);
        cachedData.add(new EntityData<>(15, EntityDataTypes.BYTE, (byte) billboardMode));
    }

    public int getInterpolationStart() { return interpolationStart; }
    public void setInterpolationStart(int v) {
        interpolationStart = v;
        cachedDataRemove(8);
        cachedData.add(new EntityData<>(8, EntityDataTypes.INT, interpolationStart));
    }

    public int getInterpolationDuration() { return interpolationDuration; }
    public void setInterpolationDuration(int v) {
        interpolationDuration = v;
        cachedDataRemove(9);
        cachedData.add(new EntityData<>(9, EntityDataTypes.INT, interpolationDuration));
    }
}