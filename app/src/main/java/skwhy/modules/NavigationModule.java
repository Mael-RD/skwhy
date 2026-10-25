package skwhy.modules;

import org.skriptlang.skript.addon.AddonModule;
import org.skriptlang.skript.addon.SkriptAddon;


import skwhy.modules.NavigationElements.conditions.*;
import skwhy.modules.NavigationElements.effects.*;
import skwhy.modules.NavigationElements.expressions.*;
import skwhy.modules.NavigationElements.types.*;

import org.bukkit.Bukkit;

public class NavigationModule implements AddonModule {

    @Override
    public String name() {
        return "FakePathFindingModule";
    }

    @Override
    public boolean canLoad(SkriptAddon addon) {
        return skwhy.util.PacketEventsSupport.available("FakePathFindingModule");
    }

    @Override
    public void init(SkriptAddon addon) {
        NavigationSkriptType.register();
    }

    @Override
    public void load(SkriptAddon addon) {
        NavigationHasRealEntity.register(addon);

        DestroyNavigation.register(addon);

        NavigationCreate.register(addon);
        NavigationEntity.register(addon);
        NavigationLocation.register(addon);
        NavigationNumber.register(addon);
        NavigationType.register(addon);
        NavigationVector.register(addon);

        try {
            Class.forName("com.github.retrooper.packetevents.PacketEvents");
            NavigationPlayers.register(addon);
        } catch (ClassNotFoundException e) {
            Bukkit.getLogger().info("[SkWhy] PacketEvents absent — FakePathFindingPlayers non chargé.");
        }
    }
}
