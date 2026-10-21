package skwhy.modules.QuestElements.events;

import org.bukkit.OfflinePlayer;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Cancellable;
import skwhy.quest.model.Quest;
import skwhy.quest.model.QuestObjective;

public class QuestObjectiveProgressEvent extends QuestPlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private final Quest quest;
    private final QuestObjective objective;
    private final int pastProgress;
    private final int progress;

    private boolean cancelled;

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    public QuestObjectiveProgressEvent(OfflinePlayer player, Quest quest, QuestObjective objective, int pastProgress, int progress) {
        super(player);
        this.quest = quest;
        this.objective = objective;
        this.pastProgress = pastProgress;
        this.progress = progress;
    }

    public Quest getQuest() { return quest; }

    public QuestObjective getObjective() { return objective; }

    public int getPastProgress() { return pastProgress; }

    public int getProgress() { return progress; }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
