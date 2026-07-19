package skwhy.modules.EveryBlockChange;

import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import edu.umd.cs.findbugs.annotations.NonNull;

public class BlockChangeEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Location location;
    private final BlockData previousData;
    private final BlockData newData;
    private boolean cancelled = false;

    public BlockChangeEvent(Location location, BlockData previousData, BlockData newData) {
        this.location = location;
        this.previousData = previousData;
        this.newData = newData;
    }

    @NonNull
    public Location getLocation() { return location; }
    @NonNull
    public BlockData getPreviousData() { return previousData; }
    @NonNull
    public BlockData getNewData() { return newData; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}