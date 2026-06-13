package skwhy.modules;

import org.skriptlang.skript.addon.AddonModule;
import org.skriptlang.skript.addon.SkriptAddon;

import com.github.retrooper.packetevents.PacketEvents;

import org.bukkit.Bukkit;

import skwhy.modules.FakePathFindingElements.effects.*;
import skwhy.modules.FakePathFindingElements.expressions.*;
import skwhy.modules.FakePathFindingElements.conditions.*;
import skwhy.modules.FakePathFindingElements.types.*;

public class FakePathFindingModule implements AddonModule {

    @Override
    public String name() {
        return "FakePathFindingModule";
    }

    @Override
    public boolean canLoad(SkriptAddon addon) {
        if (PacketEvents.getAPI() == null) {
            return false;
        }
        return true;
    }

    @Override
    public void init(SkriptAddon addon) {
        FakePathFindingClass.register();
    }

    @Override
    public void load(SkriptAddon addon) {
        RealEntity.register(addon);

        DestroyPathFinding.register(addon);
        UpdateHitbox.register(addon);

        FakePathFindingCreate.register(addon);
        FakePathFindingEntity.register(addon);
        FakePathFindingLocation.register(addon);
        FakePathFindingNumber.register(addon);
        FakePathFindingType.register(addon);
        FakePathFindingVector.register(addon);

        try {
            Class.forName("com.github.retrooper.packetevents.PacketEvents");
            FakePathFindingPlayers.register(addon);
        } catch (ClassNotFoundException e) {
            Bukkit.getLogger().info("[SkWhy] PacketEvents absent — FakePathFindingPlayers non chargé.");
        }
    }
}
