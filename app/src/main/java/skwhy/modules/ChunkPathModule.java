package skwhy.modules;

import org.skriptlang.skript.addon.AddonModule;
import org.skriptlang.skript.addon.SkriptAddon;

import ch.njol.skript.lang.util.SimpleEvent;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.skriptlang.skript.bukkit.lang.eventvalue.EventValue;
import org.skriptlang.skript.bukkit.lang.eventvalue.EventValueRegistry;
import org.skriptlang.skript.bukkit.registration.BukkitSyntaxInfos;

import skwhy.SkWhy;
import skwhy.chunkpath.longnav.LongNavigation;
import skwhy.chunkpath.longnav.event.LongNavigationEvent;
import skwhy.modules.LongNavigationElements.ChunkPathManager;
import skwhy.modules.LongNavigationElements.effects.*;
import skwhy.modules.LongNavigationElements.expressions.*;
import skwhy.modules.LongNavigationElements.types.*;

public class ChunkPathModule implements AddonModule {

    @Override
    public String name() {
        return "ChunkPathModule";
    }

    @Override
    public boolean canLoad(SkriptAddon addon) {
        return true;
    }

    @Override
    public void init(SkriptAddon addon) {
        ChunkPathRecord.register();
        PathCostRuleData.register();
        LongNavigationSkriptType.register();
    }

    @Override
    public void load(SkriptAddon addon) {
        ChunkPathManager.init(SkWhy.getInstance());
        org.bukkit.Bukkit.getScheduler().runTask(SkWhy.getInstance(), skwhy.chunkpath.block.BlockClassifier::initCollisionData);
        skwhy.chunkpath.debug.ChunkPathDebugCommand.register();

        RegisterChunkPath.register(addon);
        DeleteChunkPath.register(addon);
        ChunkPathOf.register(addon);
        ChunkPathAllOf.register(addon);

        ChunkPathWorld.register(addon);
        ChunkPathChunk.register(addon);
        ChunkPathNumber.register(addon);
        ChunkPathLocations.register(addon);
        ChunkPathLinkedChunks.register(addon);
        GatePathLocations.register(addon);

        PathCostRuleCreate.register(addon);
        PathCostRuleBlocks.register(addon);
        PathCostRuleTags.register(addon);
        PathCostRuleCost.register(addon);

        DestroyLongNavigation.register(addon);
        LongNavigationCreate.register(addon);
        LongNavigationEntity.register(addon);
        LongNavigationLocation.register(addon);
        LongNavigationDoors.register(addon);
        LongNavigationCostSmoothing.register(addon);
        LongNavigationPathSmoothing.register(addon);
        LongNavigationForceChunks.register(addon);
        LongNavigationBlocks.register(addon);
        LongNavigationRules.register(addon);
        LongNavigationNumber.register(addon);
        LongNavigationStatus.register(addon);
        LongNavigationOf.register(addon);
        registerEvents(addon);

        try {
            Class.forName("com.github.retrooper.packetevents.PacketEvents");
            LongNavigationPlayers.register(addon);
        } catch (ClassNotFoundException e) {
            SkWhy.getInstance().getLogger().info("[ChunkPath] PacketEvents absent — LongNavigationPlayers non chargé.");
        }
    }

    private void registerEvents(SkriptAddon addon) {
        String values = " 'event-longnavigation' is the long navigation, 'event-entity' its entity (not set for a fake entity "
            + "made by id), 'event-players' the players who watched it (virtual navigations) and 'event-location' where the entity "
            + "was. It works for every long navigation, not only NPCs.";
        registerEvent(addon, LongNavigationEvent.Arrive.class, "Long Navigation Arrive", "long navigation arriv(e|al)",
            "Fires when a long navigation reaches its destination." + values,
            "on long navigation arrive:", "	broadcast \"%event-entity% est arrivé\"");
        registerEvent(addon, LongNavigationEvent.Fail.class, "Long Navigation Fail", "long navigation fail[ure]",
            "Fires when no route can be found for a long navigation (a chunk of the way is not registered, the start or the end is not walkable)." + values,
            "on long navigation failure:", "	teleport event-entity to end location of event-longnavigation");
        registerEvent(addon, LongNavigationEvent.Abandon.class, "Long Navigation Abandon", "long navigation abandon[ed]",
            "Fires when the last player that watched a virtual long navigation (made for players: by id, or 'for <players>') is gone: "
                + "offline, in another world or beyond their view distance. The navigation stops at once." + values,
            "on long navigation abandoned:", "	destroy long navigation event-longnavigation");
    }

    private <E extends LongNavigationEvent> void registerEvent(SkriptAddon addon, Class<E> type, String name, String pattern,
                                                                String description, String... examples) {
        this.moduleRegistry(addon).register(
            BukkitSyntaxInfos.Event.KEY,
            BukkitSyntaxInfos.Event.builder(SimpleEvent.class, "[Long Navigation] " + name.substring("Long Navigation ".length()))
                .addEvent(type)
                .addPattern(pattern)
                .addDescription(description)
                .addExamples(examples)
                .addKeywords("long", "navigation", "pathfinding")
                .addSince("1.6.1")
                .build()
        );
        EventValueRegistry reg = addon.registry(EventValueRegistry.class);
        reg.register(EventValue.builder(type, LongNavigation.class).patterns("long navigation", "longnavigation", "navigation")
            .getter(LongNavigationEvent::getNavigation).time(EventValue.Time.NOW).build());
        reg.register(EventValue.simple(type, Entity.class, LongNavigationEvent::getEntity));
        reg.register(EventValue.simple(type, Player[].class, e -> e.getViewers().toArray(new Player[0])));
        reg.register(EventValue.simple(type, Location.class, LongNavigationEvent::getLocation));
    }

    /**
     * À appeler depuis onDisable() du plugin principal.
     */
    public static void shutdown() {
        skwhy.chunkpath.debug.GateDebugOverlay.shutdown();
        skwhy.chunkpath.longnav.LongNavigation.shutdownAll();
        ChunkPathManager.shutdown();
    }
}
