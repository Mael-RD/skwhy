package skwhy;

import skwhy.data.DisplayGroupData;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;

import java.util.ArrayList;


import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;

public class EntityRemove implements Listener {

    @EventHandler
    public void onEntityRemove(EntityRemoveFromWorldEvent event) {
        DisplayGroupData.clearEntityData(event.getEntity());
    }

    @EventHandler
    public void onEntityMount(EntityMountEvent event) {
        Entity passenger = event.getEntity(); // Celui qui s'assoit
        Entity vehicle = event.getMount();    // La monture
        Bukkit.getLogger().info("EntityMountEvent: " + passenger + " monte sur " + vehicle);
        Bukkit.getScheduler().runTask(JavaPlugin.getProvidingPlugin(getClass()), () -> {
            DisplayGroupData.addOtherMount(vehicle, passenger.getEntityId(), new ArrayList<>(vehicle.getTrackedBy()));
        });
    }

    @EventHandler
    public void onEntityDismount(EntityDismountEvent event) {
        Entity passenger = event.getEntity();
        Entity vehicle = event.getDismounted();
        
        Bukkit.getScheduler().runTask(JavaPlugin.getProvidingPlugin(getClass()), () -> {
            DisplayGroupData.removeOtherMount(vehicle, passenger.getEntityId(), new ArrayList<>(vehicle.getTrackedBy()));
        });
    }

}