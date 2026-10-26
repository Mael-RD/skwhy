package skwhy.quest;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.inventory.EquipmentSlot;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;

import io.papermc.paper.event.player.PlayerInventorySlotChangeEvent;
import io.papermc.paper.event.player.PlayerPurchaseEvent;
import io.papermc.paper.event.player.PlayerTrackEntityEvent;
import skwhy.SkWhy;
import skwhy.quest.data.QuestStore;
import skwhy.quest.model.NpcEntityRef;
import skwhy.quest.npc.CompassService;
import skwhy.quest.npc.DialogueService;
import skwhy.quest.npc.IndicatorService;
import skwhy.quest.npc.NpcService;
import skwhy.quest.npc.TradeService;

/** Écouteurs Bukkit du module de quêtes. */
public final class QuestListener implements Listener {

    /** Joueurs dont l'inventaire a changé ce tick : un seul recomptage par tick. */
    private final Set<UUID> pendingItems = ConcurrentHashMap.newKeySet();

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        QuestStore.get(player.getUniqueId());
        Bukkit.getScheduler().runTaskLater(SkWhy.getInstance(), () -> {
            if (!player.isOnline()) return;
            NpcService.refreshVisibility(player);
            CompassService.join(player);
            IndicatorService.invalidate(player.getUniqueId());
            QuestEngine.recheckSpecials(player, null);
        }, 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        CompassService.quit(id);
        IndicatorService.forget(id);
        NpcService.forget(id);
        DialogueService.clear(id);
        TradeService.onClose(event.getPlayer());
        QuestStore.unload(id);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        QuestEngine.refresh(event.getPlayer());
        NpcService.refreshVisibility(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (NpcService.refOf(event.getRightClicked()) == null) return;
        event.setCancelled(true);
        NpcService.interact(event.getPlayer(), event.getRightClicked(), ClickType.RIGHT);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (NpcService.refOf(event.getEntity()) == null) return;
        event.setCancelled(true);
        NpcService.interact(player, event.getEntity(), ClickType.LEFT);
    }

    @EventHandler(ignoreCancelled = true)
    public void onTrack(PlayerTrackEntityEvent event) {
        NpcEntityRef ref = NpcService.refOf(event.getEntity());
        if (ref != null && !NpcService.shouldSee(event.getPlayer(), ref)) event.setCancelled(true);
    }

    @EventHandler
    public void onEntityAdd(EntityAddToWorldEvent event) {
        if (NpcService.refOf(event.getEntity()) != null) {
            NpcService.refreshVisibility(event.getEntity());
            for (Player player : Bukkit.getOnlinePlayers()) IndicatorService.invalidate(player.getUniqueId());
        }
    }

    @EventHandler
    public void onInventorySlot(PlayerInventorySlotChangeEvent event) {
        Player player = event.getPlayer();
        var data = QuestStore.getIfLoaded(player.getUniqueId());
        if (data == null || data.index.items.isEmpty()) return;
        if (pendingItems.add(player.getUniqueId())) {
            Bukkit.getScheduler().runTask(SkWhy.getInstance(), () -> {
                pendingItems.remove(player.getUniqueId());
                if (player.isOnline()) QuestEngine.recheckItems(player);
            });
        }
    }

    @EventHandler
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        QuestEngine.recheckSpecials(event.getPlayer(), "advancement");
    }

    @EventHandler(ignoreCancelled = true)
    public void onPurchase(PlayerPurchaseEvent event) {
        TradeService.onPurchase(event);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player && event.getInventory() instanceof org.bukkit.inventory.MerchantInventory) {
            TradeService.onClose(player);
        }
    }

    /** Sauvegarde les données quand SkWhy est désactivé (l'addon principal n'a pas de crochet pour ce module). */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginDisable(PluginDisableEvent event) {
        if (event.getPlugin() == SkWhy.getInstance()) QuestManager.shutdown();
    }
}
