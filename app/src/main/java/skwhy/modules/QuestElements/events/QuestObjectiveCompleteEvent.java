package skwhy.modules.QuestElements.events;

import org.bukkit.OfflinePlayer;
import org.bukkit.event.HandlerList;
import skwhy.quest.model.Quest;
import skwhy.quest.model.QuestObjective;

public class QuestObjectiveCompleteEvent extends QuestPlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();
    private final Quest quest;
    private final QuestObjective objective;

    public QuestObjectiveCompleteEvent(OfflinePlayer player, Quest quest, QuestObjective objective) {
        super(player);
        this.quest = quest;
        this.objective = objective;
    }

    public Quest getQuest() { return quest; }

    public QuestObjective getObjective() { return objective; }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
