package skwhy.modules.QuestElements.events;

import org.bukkit.OfflinePlayer;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Cancellable;
import skwhy.quest.model.QuestNpc;

public class QuestNpcPositionChangeEvent extends QuestPlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private final QuestNpc npc;
    private final int pastPosition;
    private final int position;

    private boolean cancelled;

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    public QuestNpcPositionChangeEvent(OfflinePlayer player, QuestNpc npc, int pastPosition, int position) {
        super(player);
        this.npc = npc;
        this.pastPosition = pastPosition;
        this.position = position;
    }

    public QuestNpc getNpc() { return npc; }

    public int getPastPosition() { return pastPosition; }

    public int getPosition() { return position; }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
