package skwhy.modules.QuestElements.events;

import org.bukkit.OfflinePlayer;
import org.bukkit.event.HandlerList;
import skwhy.quest.model.Quest;

public class QuestSelectEvent extends QuestPlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();
    private final Quest quest;
    private final Quest pastQuest;

    public QuestSelectEvent(OfflinePlayer player, Quest quest, Quest pastQuest) {
        super(player);
        this.quest = quest;
        this.pastQuest = pastQuest;
    }

    public Quest getQuest() { return quest; }

    public Quest getPastQuest() { return pastQuest; }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
