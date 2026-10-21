package skwhy.modules.QuestElements.events;

import org.bukkit.OfflinePlayer;
import org.bukkit.event.HandlerList;
import skwhy.quest.model.Quest;

public class QuestFinishEvent extends QuestPlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();
    private final Quest quest;

    public QuestFinishEvent(OfflinePlayer player, Quest quest) {
        super(player);
        this.quest = quest;
    }

    public Quest getQuest() { return quest; }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
