package skwhy.modules.QuestElements.events;

import org.bukkit.OfflinePlayer;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Cancellable;
import skwhy.quest.model.QuestNpc;

public class QuestDialogueStartEvent extends QuestPlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private final QuestNpc npc;
    private final String dialogue;

    private boolean cancelled;

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    public QuestDialogueStartEvent(OfflinePlayer player, QuestNpc npc, String dialogue) {
        super(player);
        this.npc = npc;
        this.dialogue = dialogue;
    }

    public QuestNpc getNpc() { return npc; }

    public String getDialogue() { return dialogue; }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
