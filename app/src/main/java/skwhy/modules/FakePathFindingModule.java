package skwhy.modules;

import org.skriptlang.skript.addon.AddonModule;
import org.skriptlang.skript.addon.SkriptAddon;

import com.github.retrooper.packetevents.PacketEvents;

import skwhy.modules.FakePathFindingElements.expressions.FakePathFindingCreate;
import skwhy.modules.FakePathFindingElements.expressions.FakePathFindingLocation;
import skwhy.modules.FakePathFindingElements.expressions.FakePathFindingNumber;
import skwhy.modules.FakePathFindingElements.expressions.FakePathFindingPlayers;
import skwhy.modules.FakePathFindingElements.expressions.FakePathFindingType;
import skwhy.modules.FakePathFindingElements.expressions.FakePathFindingVector;
import skwhy.modules.FakePathFindingElements.types.FakePathFindingClass;

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
        FakePathFindingCreate.register(addon);
        FakePathFindingNumber.register(addon);
        FakePathFindingLocation.register(addon);
        FakePathFindingVector.register(addon);
        FakePathFindingType.register(addon);
        FakePathFindingPlayers.register(addon);
    }
}
