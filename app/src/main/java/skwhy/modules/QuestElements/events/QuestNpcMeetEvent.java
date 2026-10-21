package skwhy.modules.QuestElements.events;

import org.bukkit.OfflinePlayer;
import org.bukkit.event.HandlerList;
import skwhy.quest.model.QuestNpc;

public class QuestNpcMeetEvent extends QuestPlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();
    private final QuestNpc npc;

    public QuestNpcMeetEvent(OfflinePlayer player, QuestNpc npc) {
        super(player);
        this.npc = npc;
    }

    public QuestNpc getNpc() { return npc; }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
