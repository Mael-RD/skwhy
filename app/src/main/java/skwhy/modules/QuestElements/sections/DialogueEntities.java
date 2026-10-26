package skwhy.modules.QuestElements.sections;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Entity;

import skwhy.quest.QuestRegistry;
import skwhy.quest.model.NpcEntityRef;
import skwhy.quest.model.QuestNpc;
import skwhy.quest.npc.NpcService;

/** Retrouve l'entité d'un NPC, utile pour la distance maximale des réponses. */
final class DialogueEntities {

    private DialogueEntities() {}

    /** L'entité du NPC à la position que voit le joueur, sinon n'importe quelle entité chargée du NPC. */
    static Entity of(QuestNpc npc, OfflinePlayer player) {
        int position = NpcService.position(player, npc);
        Entity fallback = null;
        for (NpcEntityRef ref : QuestRegistry.entityRefs()) {
            if (!ref.npcId().equals(npc.id())) continue;
            Entity entity = NpcService.entity(ref);
            if (entity == null) continue;
            if (ref.position() == position) return entity;
            if (fallback == null) fallback = entity;
        }
        return fallback;
    }
}
