package skwhy.modules;

import org.skriptlang.skript.addon.AddonModule;
import org.skriptlang.skript.addon.SkriptAddon;

import skwhy.SkWhy;
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
        LongNavigationNumber.register(addon);
        LongNavigationStatus.register(addon);

        try {
            Class.forName("com.github.retrooper.packetevents.PacketEvents");
            LongNavigationPlayers.register(addon);
        } catch (ClassNotFoundException e) {
            SkWhy.getInstance().getLogger().info("[ChunkPath] PacketEvents absent — LongNavigationPlayers non chargé.");
        }
    }

    /**
     * À appeler depuis onDisable() du plugin principal.
     */
    public static void shutdown() {
        ChunkPathManager.shutdown();
    }
}
