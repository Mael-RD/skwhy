package skwhy.quest.script;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import skwhy.quest.model.Quest;
import skwhy.quest.model.QuestNpc;
import skwhy.quest.model.ScriptSet;

/**
 * Contexte d'exécution d'un script : le joueur, le NPC (et son entité) quand le script en vient, et l'ensemble de
 * nœuds dans lequel les ids d'actions et de conditions sont résolus.
 */
public record ScriptContext(OfflinePlayer player, ScriptSet scripts, QuestNpc npc, Entity entity, Quest quest) {

    public static ScriptContext forNpc(OfflinePlayer player, QuestNpc npc, Entity entity) {
        return new ScriptContext(player, npc.script(), npc, entity, null);
    }

    public static ScriptContext forQuest(OfflinePlayer player, Quest quest) {
        return new ScriptContext(player, quest.script(), null, null, quest);
    }

    /** Le joueur s'il est connecté, sinon {@code null}. */
    public Player online() { return player.getPlayer(); }

    /** Position du NPC pour ce joueur (1 par défaut). */
    public int npcPosition() {
        if (npc == null) return 1;
        skwhy.quest.model.NpcEntityRef ref = entity == null ? null : skwhy.quest.QuestRegistry.refOf(entity.getUniqueId());
        if (ref != null) return ref.position();
        return skwhy.quest.data.QuestStore.get(player.getUniqueId()).npcPosition(npc.id());
    }
}
