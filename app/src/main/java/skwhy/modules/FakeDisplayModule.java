package skwhy.modules;

import org.skriptlang.skript.addon.AddonModule;
import org.skriptlang.skript.addon.SkriptAddon;

import com.github.retrooper.packetevents.PacketEvents;

import skwhy.modules.FakeDisplayElements.types.Display;
import skwhy.modules.FakeDisplayElements.types.DisplayGroup;
import skwhy.modules.FakeDisplayElements.sections.CreateBlockSection;
import skwhy.modules.FakeDisplayElements.sections.CreateItemSection;
import skwhy.modules.FakeDisplayElements.sections.CreateTextSection;
import skwhy.modules.FakeDisplayElements.expressions.DisplayScale;
import skwhy.modules.FakeDisplayElements.expressions.DisplayNumber;
import skwhy.modules.FakeDisplayElements.expressions.DisplayBoolean;
import skwhy.modules.FakeDisplayElements.expressions.DisplayId;
import skwhy.modules.FakeDisplayElements.expressions.CreateDisplayGroup;
import skwhy.modules.FakeDisplayElements.expressions.DisplayBlockData;
import skwhy.modules.FakeDisplayElements.expressions.DisplayItem;
import skwhy.modules.FakeDisplayElements.expressions.DisplayText;
import skwhy.modules.FakeDisplayElements.expressions.DisplayFromReal;
import skwhy.modules.FakeDisplayElements.expressions.EntityTrackers;

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
        CreateBlockSection.register(addon);
        CreateItemSection.register(addon);
        CreateTextSection.register(addon);
        DisplayScale.register(addon);
        DisplayNumber.register(addon);
        DisplayBoolean.register(addon);
        DisplayBlockData.register(addon);
        DisplayItem.register(addon);
        DisplayText.register(addon);
        DisplayId.register(addon);
        CreateDisplayGroup.register(addon);
        EntityTrackers.register(addon);
        DisplayFromReal.register(addon);
    }
}
