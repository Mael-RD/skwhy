package skwhy.customitem.events;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

/**
 * Un joueur fait quelque chose avec un item custom (clic, lâcher, ramassage...). Un événement par action (sous-classes ci-dessous),
 * tous avec le joueur, l'item et son id ; annuler l'événement annule l'événement Bukkit d'origine.
 */
public abstract class CustomItemEvent extends Event implements Cancellable {

    private final Player player;
    private final ItemStack item;
    private final String id;
    private final Event original;
    private ClickType clickType;
    private Block block;
    private boolean cancelled;

    protected CustomItemEvent(Player player, ItemStack item, String id, Event original) {
        this.player = player;
        this.item = item;
        this.id = id;
        this.original = original;
        this.cancelled = original instanceof Cancellable c && c.isCancelled();
    }

    public Player getPlayer() { return player; }
    public ItemStack getItem() { return item; }
    /** Id de l'item custom (normalisé). */
    public String getId() { return id; }
    /** Clic gauche ou droit (avec {@code SHIFT_}), {@code null} pour les événements sans clic. */
    public ClickType getClickType() { return clickType; }
    public Block getBlock() { return block; }

    public CustomItemEvent withClick(ClickType clickType) {
        this.clickType = clickType;
        return this;
    }

    public CustomItemEvent withBlock(Block block) {
        this.block = block;
        return this;
    }

    @Override public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
        if (original instanceof Cancellable c) c.setCancelled(cancel);
    }

    /** Clic gauche ou droit sur un bloc ou dans l'air avec l'item en main. */
    public static final class Click extends CustomItemEvent {
        private static final HandlerList HANDLERS = new HandlerList();
        public Click(Player player, ItemStack item, String id, Event original) { super(player, item, id, original); }
        @Override public HandlerList getHandlers() { return HANDLERS; }
        public static HandlerList getHandlerList() { return HANDLERS; }
    }

    /** Clic dans un inventaire sur l'item ou avec l'item sur le curseur. */
    public static final class InventoryClick extends CustomItemEvent {
        private static final HandlerList HANDLERS = new HandlerList();
        public InventoryClick(Player player, ItemStack item, String id, Event original) { super(player, item, id, original); }
        @Override public HandlerList getHandlers() { return HANDLERS; }
        public static HandlerList getHandlerList() { return HANDLERS; }
    }

    public static final class Drop extends CustomItemEvent {
        private static final HandlerList HANDLERS = new HandlerList();
        public Drop(Player player, ItemStack item, String id, Event original) { super(player, item, id, original); }
        @Override public HandlerList getHandlers() { return HANDLERS; }
        public static HandlerList getHandlerList() { return HANDLERS; }
    }

    public static final class Pickup extends CustomItemEvent {
        private static final HandlerList HANDLERS = new HandlerList();
        public Pickup(Player player, ItemStack item, String id, Event original) { super(player, item, id, original); }
        @Override public HandlerList getHandlers() { return HANDLERS; }
        public static HandlerList getHandlerList() { return HANDLERS; }
    }

    public static final class Consume extends CustomItemEvent {
        private static final HandlerList HANDLERS = new HandlerList();
        public Consume(Player player, ItemStack item, String id, Event original) { super(player, item, id, original); }
        @Override public HandlerList getHandlers() { return HANDLERS; }
        public static HandlerList getHandlerList() { return HANDLERS; }
    }

    public static final class Place extends CustomItemEvent {
        private static final HandlerList HANDLERS = new HandlerList();
        public Place(Player player, ItemStack item, String id, Event original) { super(player, item, id, original); }
        @Override public HandlerList getHandlers() { return HANDLERS; }
        public static HandlerList getHandlerList() { return HANDLERS; }
    }

    /** Le joueur prend l'item en main (changement de slot de la barre rapide). */
    public static final class Held extends CustomItemEvent {
        private static final HandlerList HANDLERS = new HandlerList();
        public Held(Player player, ItemStack item, String id, Event original) { super(player, item, id, original); }
        @Override public HandlerList getHandlers() { return HANDLERS; }
        public static HandlerList getHandlerList() { return HANDLERS; }
    }

    /** Le joueur range l'item (il change de slot de la barre rapide). */
    public static final class Unheld extends CustomItemEvent {
        private static final HandlerList HANDLERS = new HandlerList();
        public Unheld(Player player, ItemStack item, String id, Event original) { super(player, item, id, original); }
        @Override public HandlerList getHandlers() { return HANDLERS; }
        public static HandlerList getHandlerList() { return HANDLERS; }
    }

    public static final class Equip extends CustomItemEvent {
        private static final HandlerList HANDLERS = new HandlerList();
        public Equip(Player player, ItemStack item, String id, Event original) { super(player, item, id, original); }
        @Override public HandlerList getHandlers() { return HANDLERS; }
        public static HandlerList getHandlerList() { return HANDLERS; }
    }

    public static final class Unequip extends CustomItemEvent {
        private static final HandlerList HANDLERS = new HandlerList();
        public Unequip(Player player, ItemStack item, String id, Event original) { super(player, item, id, original); }
        @Override public HandlerList getHandlers() { return HANDLERS; }
        public static HandlerList getHandlerList() { return HANDLERS; }
    }
}
