package skwhy.modules.QuestElements.events;

import org.bukkit.OfflinePlayer;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

import skwhy.quest.model.QuestIndicator;
import skwhy.quest.model.QuestNpc;

public class QuestIndicatorUpdateEvent extends QuestPlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final QuestNpc npc;
    private final QuestIndicator pastIndicator;
    private QuestIndicator indicator;
    private boolean cancelled;

    public QuestIndicatorUpdateEvent(OfflinePlayer player, QuestNpc npc, QuestIndicator pastIndicator, QuestIndicator indicator) {
        super(player);
        this.npc = npc;
        this.pastIndicator = pastIndicator;
        this.indicator = indicator;
    }

    public QuestNpc getNpc() { return npc; }

    public QuestIndicator getPastIndicator() { return pastIndicator; }

    /** Marqueur qui sera affiché ; modifiable pour le remplacer. */
    public QuestIndicator getIndicator() { return indicator; }

    public void setIndicator(QuestIndicator indicator) { this.indicator = indicator; }

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
