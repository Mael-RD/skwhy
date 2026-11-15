package skwhy.quest.npc;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import com.mojang.datafixers.util.Pair;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Ce qu'il faut pour réafficher une entité réelle chez un joueur qui l'a perdue (packets d'apparition avec l'id réel de
 * l'entité, à la position voulue) : type, métadonnées (nom, skin du mannequin, pose...), équipement et attributs.
 * Sert à une long navigation virtuelle : le client ne suit l'entité que près de sa position serveur, qui ne change pas.
 */
public final class EntitySnapshot {

    private final net.minecraft.world.entity.Entity nms;
    private final int id;

    private EntitySnapshot(net.minecraft.world.entity.Entity nms, int id) {
        this.nms = nms;
        this.id = id;
    }

    public static EntitySnapshot of(Entity entity) {
        return new EntitySnapshot(((CraftEntity) entity).getHandle(), entity.getEntityId());
    }

    /** Envoie l'apparition de l'entité à {@code at} (remplace celle que le client a peut-être encore). */
    public boolean spawnFor(Player viewer, Location at) {
        try {
            var target = ((CraftPlayer) viewer).getHandle();
            send(target, new ClientboundAddEntityPacket(id, nms.getUUID(), at.getX(), at.getY(), at.getZ(),
                at.getPitch(), at.getYaw(), nms.getType(), 0, Vec3.ZERO, at.getYaw()));
            List<SynchedEntityData.DataValue<?>> values = nms.getEntityData().getNonDefaultValues();
            if (values != null && !values.isEmpty()) send(target, new ClientboundSetEntityDataPacket(id, values));
            if (nms instanceof LivingEntity living) {
                List<Pair<EquipmentSlot, ItemStack>> equipment = new ArrayList<>();
                for (EquipmentSlot slot : EquipmentSlot.values()) {
                    ItemStack stack = living.getItemBySlot(slot);
                    if (!stack.isEmpty()) equipment.add(Pair.of(slot, stack.copy()));
                }
                if (!equipment.isEmpty()) send(target, new ClientboundSetEquipmentPacket(id, equipment));
                send(target, new ClientboundUpdateAttributesPacket(id, living.getAttributes().getSyncableAttributes()));
            }
            return true;
        } catch (RuntimeException | LinkageError e) {
            skwhy.SkWhy.getInstance().getLogger().warning("[Quest] Réaffichage du NPC impossible : " + e);
            return false;
        }
    }

    private static void send(net.minecraft.server.level.ServerPlayer player, Packet<?> packet) {
        player.connection.send(packet);
    }
}
