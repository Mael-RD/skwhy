package skwhy.quest.npc;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import skwhy.SkWhy;
import skwhy.quest.model.NpcEntityRef;

/**
 * Le lien entre une entité réelle et un NPC de quête, écrit dans le NBT de l'entité (PDC {@code skwhy:quest_npc} = id du NPC et
 * {@code skwhy:quest_npc_position}) : il suit l'entité (sauvegarde, rechargement de chunk, changement d'UUID par copie) et ne dépend
 * d'aucune table d'UUID.
 */
public final class NpcTag {

    private NpcTag() {}

    private static NamespacedKey npcKey() { return new NamespacedKey(SkWhy.getInstance(), "quest_npc"); }

    private static NamespacedKey positionKey() { return new NamespacedKey(SkWhy.getInstance(), "quest_npc_position"); }

    /** Le NPC que l'entité représente d'après son tag, ou {@code null}. L'endroit est celui de l'entité à cet instant. */
    public static NpcEntityRef read(Entity entity) {
        if (entity == null) return null;
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        String npc = pdc.get(npcKey(), PersistentDataType.STRING);
        if (npc == null || npc.isEmpty()) return null;
        Integer position = pdc.get(positionKey(), PersistentDataType.INTEGER);
        org.bukkit.Location at = entity.getLocation();
        return new NpcEntityRef(npc, entity.getUniqueId().toString(), position == null ? 1 : position,
            at.getWorld() == null ? "" : at.getWorld().getName(), at.getX(), at.getY(), at.getZ());
    }

    public static void write(Entity entity, String npcId, int position) {
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        pdc.set(npcKey(), PersistentDataType.STRING, npcId);
        pdc.set(positionKey(), PersistentDataType.INTEGER, position);
    }

    /** @return {@code true} si l'entité portait un tag */
    public static boolean clear(Entity entity) {
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        boolean had = pdc.has(npcKey(), PersistentDataType.STRING);
        pdc.remove(npcKey());
        pdc.remove(positionKey());
        return had;
    }
}
