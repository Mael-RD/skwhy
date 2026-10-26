package skwhy.modules.QuestElements.events;

import org.bukkit.OfflinePlayer;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Cancellable;
import org.bukkit.entity.Entity;
import org.bukkit.event.inventory.ClickType;
import skwhy.quest.model.QuestNpc;

public class QuestNpcInteractEvent extends QuestPlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private final QuestNpc npc;
    private final Entity entity;
    private final ClickType clickType;

    private boolean cancelled;

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    public QuestNpcInteractEvent(OfflinePlayer player, QuestNpc npc, Entity entity, ClickType clickType) {
        super(player);
        this.npc = npc;
        this.entity = entity;
        this.clickType = clickType;
    }

    public QuestNpc getNpc() { return npc; }

    public Entity getEntity() { return entity; }

    public ClickType getClickType() { return clickType; }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
