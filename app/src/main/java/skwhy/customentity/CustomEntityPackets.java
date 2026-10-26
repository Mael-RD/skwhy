package skwhy.customentity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.attribute.Attributes;
import com.github.retrooper.packetevents.protocol.component.builtin.item.ItemProfile;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityType;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.player.Equipment;
import com.github.retrooper.packetevents.protocol.player.EquipmentSlot;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEquipment;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityHeadLook;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateAttributes;

import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import skwhy.SkWhy;
import skwhy.util.TextComponents;

/** Fabrique les packets qui montrent, déplacent et retirent une entité custom à un joueur. */
public final class CustomEntityPackets {

    /** Index de metadata du profil (skin) d'un mannequin. */
    static final int MANNEQUIN_PROFILE_INDEX = 17;

    private CustomEntityPackets() {}

    /** Packets de création du visuel (et de l'interaction pour une entité fausse). */
    public static List<PacketWrapper<?>> show(CustomEntity entity, CustomEntityDef def, Location at) {
        List<PacketWrapper<?>> packets = new ArrayList<>();
        if (entity.isFake()) {
            packets.add(spawn(entity.hitboxId(), entity.hitboxUuid(), EntityTypes.INTERACTION, at));
            packets.add(new WrapperPlayServerEntityMetadata(entity.hitboxId(), List.of(
                new EntityData<>(8, EntityDataTypes.FLOAT, (float) def.hitboxWidth()),
                new EntityData<>(9, EntityDataTypes.FLOAT, (float) def.hitboxHeight()),
                new EntityData<>(10, EntityDataTypes.BOOLEAN, true))));
        }
        EntityType type = EntityTypes.getByName("minecraft:" + def.type());
        if (type == null) type = EntityTypes.ARMOR_STAND;
        packets.add(spawn(entity.visualId(), entity.visualUuid(), type, at));
        packets.add(new WrapperPlayServerEntityMetadata(entity.visualId(), metadata(def)));
        packets.add(new WrapperPlayServerEntityHeadLook(entity.visualId(), entity.headYawOr(at.getYaw())));
        if (def.scale() != 1.0) {
            packets.add(new WrapperPlayServerUpdateAttributes(entity.visualId(), List.of(
                new WrapperPlayServerUpdateAttributes.Property(Attributes.SCALE, def.scale(), List.of()))));
        }
        List<Equipment> equipment = equipment(def);
        if (!equipment.isEmpty()) packets.add(new WrapperPlayServerEntityEquipment(entity.visualId(), equipment));
        return packets;
    }

    /** Packets qui placent le visuel (et l'interaction d'une entité fausse) à une position. */
    public static List<PacketWrapper<?>> move(CustomEntity entity, Location at) {
        List<PacketWrapper<?>> packets = new ArrayList<>();
        if (entity.isFake()) packets.add(teleport(entity.hitboxId(), at));
        packets.add(teleport(entity.visualId(), at));
        packets.add(new WrapperPlayServerEntityHeadLook(entity.visualId(), entity.headYawOr(at.getYaw())));
        return packets;
    }

    public static PacketWrapper<?> destroy(CustomEntity entity) {
        return entity.isFake()
            ? new WrapperPlayServerDestroyEntities(entity.visualId(), entity.hitboxId())
            : new WrapperPlayServerDestroyEntities(entity.visualId());
    }

    public static void send(Player player, List<? extends PacketWrapper<?>> packets) {
        var user = PacketEvents.getAPI().getPlayerManager().getUser(player);
        if (user == null) return;
        for (PacketWrapper<?> packet : packets) user.sendPacket(packet);
    }

    public static void send(Player player, PacketWrapper<?> packet) {
        send(player, List.of(packet));
    }

    private static PacketWrapper<?> spawn(int id, UUID uuid, EntityType type, Location at) {
        return new WrapperPlayServerSpawnEntity(id, Optional.of(uuid), type,
            new Vector3d(at.getX(), at.getY(), at.getZ()), at.getPitch(), at.getYaw(), at.getYaw(), 0, Optional.empty());
    }

    private static PacketWrapper<?> teleport(int id, Location at) {
        return new WrapperPlayServerEntityTeleport(id, new Vector3d(at.getX(), at.getY(), at.getZ()), at.getYaw(), at.getPitch(), false);
    }

    /** Metadata du visuel : drapeaux, nom, silence, skin de mannequin puis metadata brute (qui l'emporte). */
    static List<EntityData<?>> metadata(CustomEntityDef def) {
        List<EntityData<?>> data = new ArrayList<>();
        byte flags = 0;
        if (def.onFire()) flags |= 0x01;
        if (def.invisible()) flags |= 0x20;
        if (def.glowing()) flags |= 0x40;
        if (flags != 0) data.add(new EntityData<>(0, EntityDataTypes.BYTE, flags));
        if (def.name() != null) {
            data.add(new EntityData<>(2, EntityDataTypes.OPTIONAL_ADV_COMPONENT, Optional.of(TextComponents.fromSkript(def.name()))));
            data.add(new EntityData<>(3, EntityDataTypes.BOOLEAN, def.nameVisible()));
        }
        if (def.silent()) data.add(new EntityData<>(4, EntityDataTypes.BOOLEAN, true));
        if (def.skinValue() != null) {
            ItemProfile profile = new ItemProfile("Mannequin", UUID.randomUUID(),
                List.of(new ItemProfile.Property("textures", def.skinValue(), def.skinSignature())));
            data.add(new EntityData<>(MANNEQUIN_PROFILE_INDEX, EntityDataTypes.RESOLVABLE_PROFILE, profile));
        }
        for (CustomEntityDef.RawMetadata raw : def.metadata()) {
            EntityData<?> value = raw(raw);
            if (value == null) continue;
            data.removeIf(d -> d.getIndex() == raw.index());
            data.add(value);
        }
        return data;
    }

    private static EntityData<?> raw(CustomEntityDef.RawMetadata raw) {
        try {
            String text = String.valueOf(raw.value());
            return switch (raw.type()) {
                case "byte" -> new EntityData<>(raw.index(), EntityDataTypes.BYTE, ((Number) number(raw.value())).byteValue());
                case "int" -> new EntityData<>(raw.index(), EntityDataTypes.INT, ((Number) number(raw.value())).intValue());
                case "long" -> new EntityData<>(raw.index(), EntityDataTypes.LONG, ((Number) number(raw.value())).longValue());
                case "float" -> new EntityData<>(raw.index(), EntityDataTypes.FLOAT, ((Number) number(raw.value())).floatValue());
                case "boolean" -> new EntityData<>(raw.index(), EntityDataTypes.BOOLEAN, Boolean.parseBoolean(text));
                case "string" -> new EntityData<>(raw.index(), EntityDataTypes.STRING, text);
                default -> null;
            };
        } catch (NumberFormatException | ClassCastException e) {
            SkWhy.getInstance().getLogger().warning("[CustomEntity] metadata " + raw.index() + " ignorée : valeur '" + raw.value() + "' invalide pour le type " + raw.type());
            return null;
        }
    }

    private static Number number(Object value) {
        if (value instanceof Number n) return n;
        return Double.parseDouble(String.valueOf(value).trim());
    }

    private static List<Equipment> equipment(CustomEntityDef def) {
        List<Equipment> list = new ArrayList<>();
        def.equipment().forEach((slot, item) -> {
            Material material = Material.matchMaterial(item.toLowerCase(Locale.ROOT));
            EquipmentSlot target = switch (slot) {
                case "main_hand" -> EquipmentSlot.MAIN_HAND;
                case "off_hand" -> EquipmentSlot.OFF_HAND;
                case "head" -> EquipmentSlot.HELMET;
                case "chest" -> EquipmentSlot.CHEST_PLATE;
                case "legs" -> EquipmentSlot.LEGGINGS;
                case "feet" -> EquipmentSlot.BOOTS;
                case "body" -> EquipmentSlot.BODY;
                default -> null;
            };
            if (material == null || !material.isItem() || target == null) {
                SkWhy.getInstance().getLogger().warning("[CustomEntity] " + def.id() + " : équipement '" + item + "' ignoré (matériau inconnu)");
                return;
            }
            list.add(new Equipment(target, SpigotConversionUtil.fromBukkitItemStack(new ItemStack(material))));
        });
        return list;
    }
}
