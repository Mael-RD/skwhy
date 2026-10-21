package skwhy.modules.QuestElements.events;

import java.util.Map;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Entity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

import skwhy.quest.model.QuestNpc;

/**
 * Événement interne dans lequel s'exécutent les sections {@code register quest action} et
 * {@code register quest condition}. Pour une condition, annuler l'événement la rend fausse.
 */
public class QuestScriptEvent extends QuestPlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final QuestNpc npc;
    private final Entity entity;
    private final Map<String, Object> arguments;
    private boolean cancelled;

    public QuestScriptEvent(OfflinePlayer player, QuestNpc npc, Entity entity, Map<String, Object> arguments) {
        super(player);
        this.npc = npc;
        this.entity = entity;
        this.arguments = arguments;
    }

    public QuestNpc getNpc() { return npc; }

    public Entity getEntity() { return entity; }

    /** Les paramètres du nœud de script qui a appelé ce type d'action ou de condition. */
    public Map<String, Object> getArguments() { return arguments; }

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
