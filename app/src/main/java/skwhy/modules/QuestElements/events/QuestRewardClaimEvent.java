package skwhy.modules.QuestElements.events;

import org.bukkit.OfflinePlayer;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Cancellable;
import skwhy.quest.model.Quest;

public class QuestRewardClaimEvent extends QuestPlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private final Quest quest;

    private boolean cancelled;

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    public QuestRewardClaimEvent(OfflinePlayer player, Quest quest) {
        super(player);
        this.quest = quest;
    }

    public Quest getQuest() { return quest; }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
