package skwhy.modules.CustomEntityElements.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.ClickType;

import skwhy.customentity.CustomEntity;

/** Un joueur clique (clic droit ou clic gauche) sur une entité custom, réelle ou fausse, sur son visuel ou sur son interaction. */
public class CustomEntityInteractEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final CustomEntity entity;
    private final ClickType clickType;

    public CustomEntityInteractEvent(Player player, CustomEntity entity, ClickType clickType) {
        this.player = player;
        this.entity = entity;
        this.clickType = clickType;
    }

    public Player getPlayer() { return player; }
    public CustomEntity getEntity() { return entity; }
    public ClickType getClickType() { return clickType; }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
