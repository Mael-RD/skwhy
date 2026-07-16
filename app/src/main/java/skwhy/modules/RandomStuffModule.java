package skwhy.modules;

import org.skriptlang.skript.addon.AddonModule;
import org.skriptlang.skript.addon.SkriptAddon;

import com.github.retrooper.packetevents.PacketEvents;

import skwhy.modules.RandomStuff.expressions.*;
import skwhy.modules.RandomStuff.effects.*;

public class RandomStuffModule implements AddonModule {

    @Override
    public String name() {
        return "RandomStuffModule";
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
    }

    // load() : sections, effets, conditions, expressions
    @Override
    public void load(SkriptAddon addon) {
        BodyYaw.register(addon);
        EntityId.register(addon);
        EntityTrackers.register(addon);
        FutureDirection.register(addon);
        GenerateId.register(addon);
        SplitRegex.register(addon);
        TrackedEntities.register(addon);

        SecretHide.register(addon);
        SecretReveal.register(addon);
        SecretDestroy.register(addon);
    }
}
