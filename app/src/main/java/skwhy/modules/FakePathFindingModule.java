package skwhy.modules;

import org.skriptlang.skript.addon.AddonModule;
import org.skriptlang.skript.addon.SkriptAddon;

import com.github.retrooper.packetevents.PacketEvents;

import skwhy.modules.FakePathFindingElements.FakePathFindingCreate;
import skwhy.modules.FakePathFindingElements.FakePathFindingLocation;
import skwhy.modules.FakePathFindingElements.FakePathFindingNumber;
import skwhy.modules.FakePathFindingElements.FakePathFindingPlayers;
import skwhy.modules.FakePathFindingElements.FakePathFindingType;
import skwhy.modules.FakePathFindingElements.FakePathFindingVector;

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
        // Aucun type spécifique à enregistrer pour le moment.
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
