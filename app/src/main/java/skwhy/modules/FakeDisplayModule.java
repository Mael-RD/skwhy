package skwhy.modules;

import org.skriptlang.skript.addon.AddonModule;
import org.skriptlang.skript.addon.SkriptAddon;

import com.github.retrooper.packetevents.PacketEvents;

import skwhy.modules.FakeDisplayElements.types.Display;
import skwhy.modules.FakeDisplayElements.types.DisplayGroup;

import skwhy.modules.FakeDisplayElements.effects.MountGroup;
import skwhy.modules.FakeDisplayElements.effects.SendUpdate;
import skwhy.modules.FakeDisplayElements.effects.SetGroupDirection;

import skwhy.modules.FakeDisplayElements.expressions.DisplayBlockData;
import skwhy.modules.FakeDisplayElements.expressions.DisplayBoolean;
import skwhy.modules.FakeDisplayElements.expressions.DisplayFromReal;
import skwhy.modules.FakeDisplayElements.expressions.DisplayId;
import skwhy.modules.FakeDisplayElements.expressions.DisplayItem;
import skwhy.modules.FakeDisplayElements.expressions.DisplayNumber;
import skwhy.modules.FakeDisplayElements.expressions.DisplayRotation;
import skwhy.modules.FakeDisplayElements.expressions.DisplayScale;
import skwhy.modules.FakeDisplayElements.expressions.DisplayText;
import skwhy.modules.FakeDisplayElements.expressions.EntityTrackers;
import skwhy.modules.FakeDisplayElements.expressions.GroupCreate;
import skwhy.modules.FakeDisplayElements.expressions.GroupDisplays;
import skwhy.modules.FakeDisplayElements.expressions.GroupLocation;
import skwhy.modules.FakeDisplayElements.expressions.GroupPlayers;
import skwhy.modules.FakeDisplayElements.expressions.GroupTransformation;

import skwhy.modules.FakeDisplayElements.sections.CreateBlockSection;
import skwhy.modules.FakeDisplayElements.sections.CreateItemSection;
import skwhy.modules.FakeDisplayElements.sections.CreateTextSection;

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

        EntityTrackers.register(addon);

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
