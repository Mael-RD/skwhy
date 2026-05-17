package skwhy.modules;

import org.skriptlang.skript.addon.AddonModule;
import org.skriptlang.skript.addon.SkriptAddon;

import com.github.retrooper.packetevents.PacketEvents;

import skwhy.modules.FakeDisplayElements.types.*;

import skwhy.modules.FakeDisplayElements.effects.*;

import skwhy.modules.FakeDisplayElements.expressions.*;

import skwhy.modules.FakeDisplayElements.sections.*;

public class FakeDisplayModule implements AddonModule {

    @Override
    public String name() {
        return "FakeDisplayModule";
    }

    @Override
    public boolean canLoad(SkriptAddon addon) {
        
        if (PacketEvents.getAPI() == null) {
            return false;
        }
        return true;
    }

    // init() : types en premier, avant toute syntaxe
    @Override
    public void init(SkriptAddon addon) {

        Display.register();
        DisplayGroup.register();
    }

    // load() : sections, effets, conditions, expressions
    @Override
    public void load(SkriptAddon addon) {
        DestroyGroup.register(addon);
        MountGroup.register(addon);
        SendUpdate.register(addon);
        SetGroupDirection.register(addon);

        DisplayBlockData.register(addon);
        DisplayBoolean.register(addon);
        DisplayId.register(addon);
        DisplayItem.register(addon);
        DisplayNumber.register(addon);
        DisplayRotation.register(addon);
        DisplayScale.register(addon);
        DisplayFromReal.register(addon);
        DisplayText.register(addon);

        GroupCreate.register(addon);
        GroupDisplays.register(addon);
        GroupLocation.register(addon);  
        GroupPlayers.register(addon);
        GroupTransformation.register(addon);

        CreateBlockSection.register(addon);
        CreateItemSection.register(addon);
        CreateTextSection.register(addon);
    }
}
