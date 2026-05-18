package skwhy;

import skwhy.data.DisplayGroupData;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;


import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;

public class EntityRemove implements Listener {

    @EventHandler
    public void onEntityRemove(EntityRemoveFromWorldEvent event) {
        DisplayGroupData.clearEntityData(event.getEntity());
    }

    @EventHandler
    public void onEntityMount(EntityMountEvent event) {
        Entity vehicle = event.getMount();
        
        List<Player> playersToUpdate = new ArrayList<>(vehicle.getTrackedBy());
        
        if (vehicle instanceof Player player) {
            if (!playersToUpdate.contains(player)) {
                playersToUpdate.add(player);
            }
        }

        Bukkit.getScheduler().runTaskLater(JavaPlugin.getProvidingPlugin(getClass()), () -> {
            DisplayGroupData.finalMount(playersToUpdate, vehicle);
        }, 1L);
    }

    @EventHandler
    public void onEntityDismount(EntityDismountEvent event) {
        Entity vehicle = event.getDismounted();
        
        List<Player> playersToUpdate = new ArrayList<>(vehicle.getTrackedBy());
        
        if (vehicle instanceof Player player) {
            if (!playersToUpdate.contains(player)) {
                playersToUpdate.add(player);
            }
        }

        Bukkit.getScheduler().runTaskLater(JavaPlugin.getProvidingPlugin(getClass()), () -> {
            DisplayGroupData.finalMount(playersToUpdate, vehicle);
        }, 1L);
    }

}