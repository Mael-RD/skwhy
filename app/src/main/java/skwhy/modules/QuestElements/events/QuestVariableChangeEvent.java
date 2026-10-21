package skwhy.modules.QuestElements.events;

import org.bukkit.OfflinePlayer;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Cancellable;
import skwhy.quest.model.Quest;

public class QuestVariableChangeEvent extends QuestPlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private final Quest quest;
    private final String name;
    private final Object pastValue;
    private final Object value;

    private boolean cancelled;

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    public QuestVariableChangeEvent(OfflinePlayer player, Quest quest, String name, Object pastValue, Object value) {
        super(player);
        this.quest = quest;
        this.name = name;
        this.pastValue = pastValue;
        this.value = value;
    }

    public Quest getQuest() { return quest; }

    public String getName() { return name; }

    public Object getPastValue() { return pastValue; }

    public Object getValue() { return value; }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
