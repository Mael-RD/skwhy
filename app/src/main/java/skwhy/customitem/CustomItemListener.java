package skwhy.customitem;

import java.util.function.Function;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;
import org.bukkit.inventory.ItemStack;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import skwhy.customitem.events.CustomItemEvent;

/** Transforme les événements Bukkit qui concernent un item custom en événements {@link CustomItemEvent} que Skript écoute. */
public final class CustomItemListener implements Listener {

    private static String idOf(ItemStack item) {
        return item == null || item.getType().isAir() ? null : CustomItemIdentity.idOf(item);
    }

    private static void fire(ItemStack item, Function<String, CustomItemEvent> factory, Function<CustomItemEvent, CustomItemEvent> setup) {
        String id = idOf(item);
        if (id == null) return;
        Bukkit.getPluginManager().callEvent(setup.apply(factory.apply(id)));
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() == Action.PHYSICAL) return;
        ItemStack item = e.getItem();
        boolean left = e.getAction().isLeftClick();
        Player player = e.getPlayer();
        ClickType click = left ? (player.isSneaking() ? ClickType.SHIFT_LEFT : ClickType.LEFT) : (player.isSneaking() ? ClickType.SHIFT_RIGHT : ClickType.RIGHT);
        fire(item, id -> new CustomItemEvent.Click(player, item, id, e), c -> c.withClick(click).withBlock(e.getClickedBlock()));
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        ItemStack item = idOf(e.getCurrentItem()) != null ? e.getCurrentItem() : e.getCursor();
        fire(item, id -> new CustomItemEvent.InventoryClick(player, item, id, e), c -> c.withClick(e.getClick()));
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        ItemStack item = e.getItemDrop().getItemStack();
        fire(item, id -> new CustomItemEvent.Drop(e.getPlayer(), item, id, e), c -> c);
    }

    @EventHandler
    public void onPickup(PlayerAttemptPickupItemEvent e) {
        ItemStack item = e.getItem().getItemStack();
        fire(item, id -> new CustomItemEvent.Pickup(e.getPlayer(), item, id, e), c -> c);
    }

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent e) {
        fire(e.getItem(), id -> new CustomItemEvent.Consume(e.getPlayer(), e.getItem(), id, e), c -> c);
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        ItemStack item = e.getItemInHand();
        fire(item, id -> new CustomItemEvent.Place(e.getPlayer(), item, id, e), c -> c.withBlock(e.getBlockPlaced()));
    }

    @EventHandler
    public void onHeld(PlayerItemHeldEvent e) {
        Player player = e.getPlayer();
        ItemStack held = player.getInventory().getItem(e.getNewSlot());
        ItemStack stowed = player.getInventory().getItem(e.getPreviousSlot());
        fire(held, id -> new CustomItemEvent.Held(player, held, id, e), c -> c);
        fire(stowed, id -> new CustomItemEvent.Unheld(player, stowed, id, e), c -> c);
    }

    @EventHandler
    public void onArmor(PlayerArmorChangeEvent e) {
        Player player = e.getPlayer();
        ItemStack equipped = e.getNewItem();
        ItemStack removed = e.getOldItem();
        fire(equipped, id -> new CustomItemEvent.Equip(player, equipped, id, null), c -> c);
        fire(removed, id -> new CustomItemEvent.Unequip(player, removed, id, null), c -> c);
    }
}
