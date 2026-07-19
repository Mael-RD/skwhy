package skwhy.modules.EveryBlockChange;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.craftbukkit.block.data.CraftBlockData;

public class BlockChangeDispatcher {

    public static void dispatch(LevelChunk chunk, BlockPos pos, BlockState oldState, BlockState newState) {
        if (oldState.equals(newState)) return;

        World world = chunk.getLevel().getWorld();
        Location location = new Location(world, pos.getX(), pos.getY(), pos.getZ());
        BlockData previousData = CraftBlockData.fromData(oldState);
        BlockData currentData = CraftBlockData.fromData(newState);

        if (Bukkit.isPrimaryThread()) {
            callEvent(location, previousData, currentData);
        } else {
            Bukkit.getScheduler().runTask(getPlugin(), () -> callEvent(location, previousData, currentData));
        }
    }

    private static void callEvent(Location location, BlockData previousData, BlockData currentData) {
        BlockChangeEvent event = new BlockChangeEvent(location, previousData, currentData);
        Bukkit.getPluginManager().callEvent(event);
    }

    private static org.bukkit.plugin.Plugin getPlugin() {
        return Bukkit.getPluginManager().getPlugin("SkWhy"); // adapte au nom réel de ton plugin
    }
}