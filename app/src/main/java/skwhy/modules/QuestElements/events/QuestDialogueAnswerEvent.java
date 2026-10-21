package skwhy.modules.QuestElements.events;

import org.bukkit.OfflinePlayer;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Cancellable;
import skwhy.quest.model.QuestNpc;

public class QuestDialogueAnswerEvent extends QuestPlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private final QuestNpc npc;
    private final String answer;

    private boolean cancelled;

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    public QuestDialogueAnswerEvent(OfflinePlayer player, QuestNpc npc, String answer) {
        super(player);
        this.npc = npc;
        this.answer = answer;
    }

    public QuestNpc getNpc() { return npc; }

    public String getAnswer() { return answer; }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
